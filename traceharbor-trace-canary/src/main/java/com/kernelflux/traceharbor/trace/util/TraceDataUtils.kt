package com.kernelflux.traceharbor.trace.util

import android.util.Log
import com.kernelflux.traceharbor.trace.constants.Constants
import com.kernelflux.traceharbor.trace.core.AppMethodBeat
import com.kernelflux.traceharbor.trace.items.MethodItem
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.util.Collections
import java.util.LinkedList

/**
 * Stack-trace post-processing pipeline used by the four tracer classes
 * (StartupTracer, EvilMethodTracer, LooperAnrTracer, plus internal
 * `printTree` debug helper). Converts the raw 64-bit method-event buffer
 * produced by [AppMethodBeat] into a tree of [MethodItem]s, then back
 * into a sorted flat stack ready for upload.
 *
 * Public API kept byte-for-byte:
 *  - All static helpers exposed via `@JvmStatic` on the companion.
 *  - Nested `IStructuredDataFilter` interface preserved verbatim — Java
 *    callers still pass anonymous subclasses.
 *  - Nested `TreeNode` POJO preserved as `class TreeNode` with the same
 *    public surface (constructor + 3 internal helpers).
 */
class TraceDataUtils private constructor() {

    interface IStructuredDataFilter {
        fun isFilter(during: Long, filterCount: Int): Boolean
        fun getFilterMaxCount(): Int
        fun fallback(stack: List<MethodItem>, size: Int)
    }

    /**
     * Stack-tree node. Original Java had `public static final class
     * TreeNode` with package-private mutable fields (`item`, `father`,
     * `children`). Kept identical scope: `internal` for module access.
     */
    class TreeNode internal constructor(
        @JvmField internal var item: MethodItem?,
        @JvmField internal var father: TreeNode?,
    ) {
        @JvmField
        internal val children: LinkedList<TreeNode> = LinkedList()

        internal fun depth(): Int = item?.depth ?: 0

        internal fun add(node: TreeNode) {
            children.addFirst(node)
        }

        internal fun isLeaf(): Boolean = children.isEmpty()
    }

    companion object {
        private const val TAG = "TraceHarbor.TraceDataUtils"

        @JvmStatic
        fun structuredDataToStack(
            buffer: LongArray,
            result: LinkedList<MethodItem>,
            isStrict: Boolean,
            endTime: Long,
        ) {
            var lastInId: Long
            var depth = 0
            val rawData = LinkedList<Long>()
            var isBegin = !isStrict

            for (trueId in buffer) {
                if (0L == trueId) {
                    continue
                }
                if (isStrict) {
                    if (isIn(trueId) && AppMethodBeat.METHOD_ID_DISPATCH == getMethodId(trueId)) {
                        isBegin = true
                    }
                    if (!isBegin) {
                        continue
                    }
                }
                if (isIn(trueId)) {
                    lastInId = getMethodId(trueId).toLong()
                    if (lastInId == AppMethodBeat.METHOD_ID_DISPATCH.toLong()) {
                        depth = 0
                    }
                    depth++
                    rawData.push(trueId)
                } else {
                    val outMethodId = getMethodId(trueId)
                    if (rawData.isNotEmpty()) {
                        var inVal: Long = rawData.pop()
                        depth--
                        var inMethodId: Int = getMethodId(inVal)
                        val tmp = LinkedList<Long>()
                        tmp.add(inVal)
                        while (inMethodId != outMethodId && rawData.isNotEmpty()) {
                            TraceHarborLog.w(
                                TAG,
                                "pop inMethodId[%s] to continue match ouMethodId[%s]",
                                inMethodId,
                                outMethodId,
                            )
                            inVal = rawData.pop()
                            depth--
                            tmp.add(inVal)
                            inMethodId = getMethodId(inVal)
                        }

                        if (inMethodId != outMethodId &&
                            inMethodId == AppMethodBeat.METHOD_ID_DISPATCH
                        ) {
                            TraceHarborLog.e(
                                TAG,
                                "inMethodId[%s] != outMethodId[%s] throw this outMethodId!",
                                inMethodId,
                                outMethodId,
                            )
                            rawData.addAll(tmp)
                            depth += rawData.size
                            continue
                        }

                        val outTime = getTime(trueId)
                        val inTime = getTime(inVal)
                        val during = outTime - inTime
                        if (during < 0) {
                            TraceHarborLog.e(
                                TAG,
                                "[structuredDataToStack] trace during invalid:%d",
                                during,
                            )
                            rawData.clear()
                            result.clear()
                            return
                        }
                        val methodItem = MethodItem(outMethodId, during.toInt(), depth)
                        addMethodItem(result, methodItem)
                    } else {
                        TraceHarborLog.w(
                            TAG,
                            "[structuredDataToStack] method[%s] not found in! ",
                            outMethodId,
                        )
                    }
                }
            }

            while (rawData.isNotEmpty() && isStrict) {
                val trueId = rawData.pop()
                val methodId = getMethodId(trueId)
                val isIn = isIn(trueId)
                val inTime = getTime(trueId) + AppMethodBeat.getDiffTime()
                TraceHarborLog.w(
                    TAG,
                    "[structuredDataToStack] has never out method[%s], isIn:%s, inTime:%s, endTime:%s,rawData size:%s",
                    methodId,
                    isIn,
                    inTime,
                    endTime,
                    rawData.size,
                )
                if (!isIn) {
                    TraceHarborLog.e(
                        TAG,
                        "[structuredDataToStack] why has out Method[%s]? is wrong! ",
                        methodId,
                    )
                    continue
                }
                val methodItem = MethodItem(
                    methodId,
                    (endTime - inTime).toInt(),
                    rawData.size,
                )
                addMethodItem(result, methodItem)
            }
            val root = TreeNode(null, null)
            val count = stackToTree(result, root)
            TraceHarborLog.i(TAG, "stackToTree: count=%s", count)
            result.clear()
            treeToStack(root, result)
        }

        private fun isIn(trueId: Long): Boolean = ((trueId shr 63) and 0x1L) == 1L

        private fun getTime(trueId: Long): Long = trueId and 0x7FFFFFFFFFFL

        private fun getMethodId(trueId: Long): Int = ((trueId shr 43) and 0xFFFFFL).toInt()

        private fun addMethodItem(
            resultStack: LinkedList<MethodItem>,
            item: MethodItem,
        ): Int {
            if (AppMethodBeat.isDev) {
                Log.v(TAG, "method:$item")
            }
            val last: MethodItem? = if (resultStack.isNotEmpty()) resultStack.peek() else null
            return if (last != null && last.methodId == item.methodId &&
                last.depth == item.depth && 0 != item.depth
            ) {
                item.durTime = if (item.durTime == Constants.DEFAULT_ANR) {
                    last.durTime
                } else {
                    item.durTime
                }
                last.mergeMore(item.durTime.toLong())
                last.durTime
            } else {
                resultStack.push(item)
                item.durTime
            }
        }

        private fun treeToStack(root: TreeNode, list: LinkedList<MethodItem>) {
            for (i in 0 until root.children.size) {
                val node: TreeNode? = root.children[i]
                if (node == null) continue
                node.item?.let { list.add(it) }
                if (node.children.isNotEmpty()) {
                    treeToStack(node, list)
                }
            }
        }

        /**
         * Structured the method stack as a tree Data structure.
         */
        @JvmStatic
        fun stackToTree(resultStack: LinkedList<MethodItem>, root: TreeNode): Int {
            var lastNode: TreeNode? = null
            val iterator = resultStack.listIterator(0)
            var count = 0
            while (iterator.hasNext()) {
                val node = TreeNode(iterator.next(), lastNode)
                count++
                if (lastNode == null && node.depth() != 0) {
                    TraceHarborLog.e(
                        TAG,
                        "[stackToTree] begin error! why the first node'depth is not 0!",
                    )
                    return 0
                }
                val depth = node.depth()
                if (lastNode == null || depth == 0) {
                    root.add(node)
                } else if (lastNode.depth() >= depth) {
                    while (lastNode != null && lastNode.depth() > depth) {
                        lastNode = lastNode.father
                    }
                    if (lastNode != null && lastNode.father != null) {
                        node.father = lastNode.father
                        lastNode.father?.add(node)
                    }
                } else {
                    lastNode.add(node)
                }
                lastNode = node
            }
            return count
        }

        @JvmStatic
        fun stackToString(
            stack: LinkedList<MethodItem>,
            reportBuilder: StringBuilder,
            logcatBuilder: StringBuilder,
        ): Long {
            logcatBuilder.append("|*\t\tTraceStack:").append("\n")
            logcatBuilder.append("|*\t\t[id count cost]").append("\n")
            var stackCost: Long = 0 // fix cost
            for (item in stack) {
                reportBuilder.append(item.toString()).append('\n')
                logcatBuilder.append("|*\t\t").append(item.print()).append('\n')
                if (stackCost < item.durTime) {
                    stackCost = item.durTime.toLong()
                }
            }
            return stackCost
        }

        @JvmStatic
        fun countTreeNode(node: TreeNode): Int {
            var count = node.children.size
            for (child in node.children) {
                count += countTreeNode(child)
            }
            return count
        }

        @JvmStatic
        fun printTree(root: TreeNode, print: StringBuilder) {
            print.append("|*   TraceStack: ").append("\n")
            printTree(root, 0, print, "|*        ")
        }

        @JvmStatic
        fun printTree(root: TreeNode, depth: Int, ss: StringBuilder, prefixStr: String) {
            val empty = StringBuilder(prefixStr)
            for (i in 0..depth) {
                empty.append("    ")
            }
            for (i in 0 until root.children.size) {
                val node = root.children[i]
                val item = node.item ?: continue
                ss.append(empty.toString())
                    .append(item.methodId)
                    .append("[")
                    .append(item.durTime)
                    .append("]")
                    .append("\n")
                if (node.children.isNotEmpty()) {
                    printTree(node, depth + 1, ss, prefixStr)
                }
            }
        }

        @JvmStatic
        fun trimStack(
            stack: MutableList<MethodItem>,
            targetCount: Int,
            filter: IStructuredDataFilter,
        ) {
            if (0 > targetCount) {
                stack.clear()
                return
            }

            var filterCount = 1
            var curStackSize = stack.size
            while (curStackSize > targetCount) {
                val iterator = stack.listIterator(stack.size)
                while (iterator.hasPrevious()) {
                    val item = iterator.previous()
                    if (filter.isFilter(item.durTime.toLong(), filterCount)) {
                        iterator.remove()
                        curStackSize--
                        if (curStackSize <= targetCount) {
                            return
                        }
                    }
                }
                curStackSize = stack.size
                filterCount++
                if (filter.getFilterMaxCount() < filterCount) {
                    break
                }
            }
            val size = stack.size
            if (size > targetCount) {
                filter.fallback(stack, size)
            }
        }

        @Deprecated("retained for binary compatibility")
        @JvmStatic
        fun getTreeKey(stack: List<MethodItem>, targetCount: Int): String {
            val ss = StringBuilder()
            val tmp: MutableList<MethodItem> = LinkedList(stack)
            trimStack(
                tmp,
                targetCount,
                object : IStructuredDataFilter {
                    override fun isFilter(during: Long, filterCount: Int): Boolean =
                        during < filterCount * Constants.TIME_UPDATE_CYCLE_MS

                    override fun getFilterMaxCount(): Int = Constants.FILTER_STACK_MAX_COUNT

                    override fun fallback(stack: List<MethodItem>, size: Int) {
                        TraceHarborLog.w(
                            TAG,
                            "[getTreeKey] size:%s targetSize:%s",
                            size,
                            targetCount,
                        )
                        val it = (stack as MutableList<MethodItem>)
                            .listIterator(Math.min(size, targetCount))
                        while (it.hasNext()) {
                            it.next()
                            it.remove()
                        }
                    }
                },
            )
            for (item in tmp) {
                ss.append(item.methodId).append("|")
            }
            return ss.toString()
        }

        @JvmStatic
        fun getTreeKey(stack: List<MethodItem>, stackCost: Long): String {
            val ss = StringBuilder()
            val allLimit = (stackCost * Constants.FILTER_STACK_KEY_ALL_PERCENT).toLong()

            val sortList = LinkedList<MethodItem>()

            for (item in stack) {
                if (item.durTime >= allLimit) {
                    sortList.add(item)
                }
            }

            Collections.sort(sortList) { o1, o2 ->
                Integer.compare((o2.depth + 1) * o2.durTime, (o1.depth + 1) * o1.durTime)
            }

            if (sortList.isEmpty() && stack.isNotEmpty()) {
                val root = stack[0]
                sortList.add(root)
            } else if (sortList.size > 1 &&
                sortList.peek().methodId == AppMethodBeat.METHOD_ID_DISPATCH
            ) {
                sortList.removeFirst()
            }

            for (item in sortList) {
                ss.append(item.methodId).append("|")
                break
            }
            return ss.toString()
        }
    }
}
