package com.kernelflux.traceharbor.resource.analyzer.utils

import com.kernelflux.traceharbor.resource.analyzer.model.ExcludedRefs
import com.kernelflux.traceharbor.resource.analyzer.model.Exclusion
import com.kernelflux.traceharbor.resource.analyzer.model.ReferenceChain
import com.kernelflux.traceharbor.resource.analyzer.model.ReferenceNode
import com.kernelflux.traceharbor.resource.analyzer.model.ReferenceTraceElement
import com.squareup.haha.perflib.ArrayInstance
import com.squareup.haha.perflib.ClassInstance
import com.squareup.haha.perflib.ClassObj
import com.squareup.haha.perflib.Field
import com.squareup.haha.perflib.HahaHelper.extendsThread
import com.squareup.haha.perflib.HahaHelper.fieldToString
import com.squareup.haha.perflib.HahaHelper.isPrimitiveOrWrapperArray
import com.squareup.haha.perflib.HahaHelper.isPrimitiveWrapper
import com.squareup.haha.perflib.HahaHelper.threadName
import com.squareup.haha.perflib.HahaSpy
import com.squareup.haha.perflib.Instance
import com.squareup.haha.perflib.RootObj
import com.squareup.haha.perflib.RootType
import com.squareup.haha.perflib.Snapshot
import com.squareup.haha.perflib.Type
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedList
import java.util.Queue

/**
 * This class is ported from LeakCanary.
 *
 * Not thread safe.
 *
 * Finds the shortest path from a reference to a gc root, ignoring excluded refs first and then
 * including the ones that are not "always ignorable" as needed if no path is found.
 */
class ShortestPathFinder(
    private val excludedRefs: ExcludedRefs,
) {
    private val toVisitQueue: Queue<ReferenceNode> = LinkedList()
    private val toVisitIfNoPathQueue: Queue<ReferenceNode> = LinkedList()
    private val toVisitSet = HashSet<Instance>()
    private val toVisitIfNoPathSet = HashSet<Instance>()
    private val visitedSet = HashSet<Instance>()
    private var canIgnoreStrings = false

    class Result(
        @JvmField val referenceChainHead: ReferenceNode?,
        @JvmField val excludingKnown: Boolean,
    ) {
        fun buildReferenceChain(): ReferenceChain {
            val elements = ArrayList<ReferenceTraceElement>()
            // We iterate from the leak to the GC root
            var node: ReferenceNode? = ReferenceNode(null, null, referenceChainHead, null, null)
            while (node != null) {
                val element = buildReferenceTraceElement(node)
                if (element != null) {
                    elements.add(0, element)
                }
                node = node.parent
            }
            return ReferenceChain(elements)
        }

        private fun buildReferenceTraceElement(node: ReferenceNode): ReferenceTraceElement? {
            if (node.parent == null) {
                // Ignore any root node.
                return null
            }
            val holder = node.parent.instance!!

            if (holder is RootObj) {
                return null
            }
            val type = node.referenceType
            val referenceName = node.referenceName

            var holderType = ReferenceTraceElement.Holder.OBJECT
            var extra: String? = null
            val fields = describeFields(holder)
            val className = getClassName(holder)

            if (holder is ClassObj) {
                holderType = ReferenceTraceElement.Holder.CLASS
            } else if (holder is ArrayInstance) {
                holderType = ReferenceTraceElement.Holder.ARRAY
            } else {
                val classObj = holder.classObj
                if (extendsThread(classObj)) {
                    holderType = ReferenceTraceElement.Holder.THREAD
                    val threadName = threadName(holder)
                    extra = "(named '$threadName')"
                } else if (className.matches(ANONYMOUS_CLASS_NAME_PATTERN.toRegex())) {
                    val parentClassName = classObj.superClassObj.className
                    if (Any::class.java.name == parentClassName) {
                        holderType = ReferenceTraceElement.Holder.OBJECT
                        try {
                            // This is an anonymous class implementing an interface. The API does not
                            // give access to the interfaces implemented by the class. We check if it's
                            // in the class path and use that instead.
                            val actualClass = Class.forName(classObj.className)
                            val interfaces = actualClass.interfaces
                            if (interfaces.isNotEmpty()) {
                                val implementedInterface = interfaces[0]
                                extra =
                                    "(anonymous implementation of ${implementedInterface.name})"
                            } else {
                                extra = "(anonymous subclass of java.lang.Object)"
                            }
                        } catch (_: ClassNotFoundException) {
                            // Ignored.
                        }
                    } else {
                        holderType = ReferenceTraceElement.Holder.OBJECT
                        // Makes it easier to figure out which anonymous class we're looking at.
                        extra = "(anonymous subclass of $parentClassName)"
                    }
                } else {
                    holderType = ReferenceTraceElement.Holder.OBJECT
                }
            }
            return ReferenceTraceElement(
                referenceName = referenceName,
                type = type,
                holder = holderType,
                className = className,
                extra = extra,
                exclusion = node.exclusion,
                fields = fields,
            )
        }

        private fun describeFields(instance: Instance): List<String> {
            val fields = ArrayList<String>()

            if (instance is ClassObj) {
                for (entry in instance.staticFieldValues.entries) {
                    val field = entry.key
                    val value = entry.value
                    fields.add("static ${field.name} = $value")
                }
            } else if (instance is ArrayInstance) {
                if (instance.arrayType == Type.OBJECT) {
                    val values = instance.values
                    for (i in values.indices) {
                        fields.add("[$i] = ${values[i]}")
                    }
                }
            } else {
                val classObj = instance.classObj
                for (entry in classObj.staticFieldValues.entries) {
                    fields.add("static ${fieldToString(entry)}")
                }
                val classInstance = instance as ClassInstance
                for (field in classInstance.values) {
                    fields.add(fieldToString(field))
                }
            }
            return fields
        }

        private fun getClassName(instance: Instance): String {
            return when (instance) {
                is ClassObj -> instance.className
                is ArrayInstance -> instance.classObj.className
                else -> instance.classObj.className
            }
        }
    }

    fun findPath(snapshot: Snapshot, targetReference: Instance): Result {
        val targetRefList = arrayListOf(targetReference)
        val results = findPath(snapshot, targetRefList)
        return if (results.isEmpty()) {
            Result(null, false)
        } else {
            results[targetReference]!!
        }
    }

    fun findPath(snapshot: Snapshot, targetReferences: Collection<Instance>): Map<Instance, Result> {
        val results = HashMap<Instance, Result>()
        if (targetReferences.isEmpty()) {
            return results
        }

        clearState()
        enqueueGcRoots(snapshot)

        canIgnoreStrings = true
        for (targetReference in targetReferences) {
            if (isString(targetReference)) {
                canIgnoreStrings = false
                break
            }
        }

        val targetRefSet = HashSet<Instance>(targetReferences)
        while (toVisitQueue.isNotEmpty() || toVisitIfNoPathQueue.isNotEmpty()) {
            val node =
                if (toVisitQueue.isNotEmpty()) {
                    toVisitQueue.poll()
                } else {
                    val skippedNode = toVisitIfNoPathQueue.poll()
                    if (skippedNode.exclusion == null) {
                        throw IllegalStateException("Expected node to have an exclusion $skippedNode")
                    }
                    skippedNode
                }
            val instance = node.instance!!

            // Termination
            if (targetRefSet.contains(instance)) {
                results[instance] = Result(node, node.exclusion != null)
                targetRefSet.remove(instance)
                if (targetRefSet.isEmpty()) {
                    break
                }
            }

            if (checkSeen(node)) {
                continue
            }

            when (instance) {
                is RootObj -> visitRootObj(node)
                is ClassObj -> visitClassObj(node)
                is ClassInstance -> visitClassInstance(node)
                is ArrayInstance -> visitArrayInstance(node)
                else -> throw IllegalStateException("Unexpected type for $instance")
            }
        }
        return results
    }

    private fun clearState() {
        toVisitQueue.clear()
        toVisitIfNoPathQueue.clear()
        toVisitSet.clear()
        toVisitIfNoPathSet.clear()
        visitedSet.clear()
    }

    private fun enqueueGcRoots(snapshot: Snapshot) {
        for (rootObj in snapshot.gcRoots) {
            when (rootObj.rootType) {
                RootType.JAVA_LOCAL -> {
                    val thread = HahaSpy.allocatingThread(rootObj)!!
                    val threadName = threadName(thread)
                    val params = excludedRefs.threadNames[threadName]
                    if (params == null || !params.alwaysExclude) {
                        enqueue(params, null, rootObj, null, null)
                    }
                }

                RootType.INTERNED_STRING,
                RootType.DEBUGGER,
                RootType.INVALID_TYPE,
                // An object that is unreachable from any other root, but not a root itself.
                RootType.UNREACHABLE,
                RootType.UNKNOWN,
                // An object that is in a queue, waiting for a finalizer to run.
                RootType.FINALIZING
                -> {
                    // Ignored.
                }

                RootType.SYSTEM_CLASS,
                RootType.VM_INTERNAL,
                // A local variable in native code.
                RootType.NATIVE_LOCAL,
                // A global variable in native code.
                RootType.NATIVE_STATIC,
                // An object that was referenced from an active thread block.
                RootType.THREAD_BLOCK,
                // Everything that called the wait() or notify() methods, or that is synchronized.
                RootType.BUSY_MONITOR,
                RootType.NATIVE_MONITOR,
                RootType.REFERENCE_CLEANUP,
                // Input or output parameters in native code.
                RootType.NATIVE_STACK,
                RootType.JAVA_STATIC
                -> enqueue(null, null, rootObj, null, null)

                else -> throw UnsupportedOperationException("Unknown root type:${rootObj.rootType}")
            }
        }
    }

    private fun checkSeen(node: ReferenceNode): Boolean = !visitedSet.add(node.instance!!)

    private fun visitRootObj(node: ReferenceNode) {
        val rootObj = node.instance as RootObj
        val child = rootObj.referredInstance

        if (rootObj.rootType == RootType.JAVA_LOCAL) {
            val holder = HahaSpy.allocatingThread(rootObj)
            // We switch the parent node with the thread instance that holds the local reference.
            var exclusion: Exclusion? = null
            if (node.exclusion != null) {
                exclusion = node.exclusion
            }
            val parent = ReferenceNode(null, holder, null, null, null)
            enqueue(exclusion, parent, child, "<Java Local>", ReferenceTraceElement.Type.LOCAL)
        } else {
            enqueue(null, node, child, null, null)
        }
    }

    private fun visitClassObj(node: ReferenceNode) {
        val classObj = node.instance as ClassObj
        val ignoredStaticFields = excludedRefs.staticFieldNameByClassName[classObj.className]
        for (entry in classObj.staticFieldValues.entries) {
            val field = entry.key
            if (field.type != Type.OBJECT) {
                continue
            }
            val fieldName = field.name
            if (fieldName == "\$staticOverhead") {
                continue
            }
            val child = entry.value as Instance?
            var visit = true
            if (ignoredStaticFields != null) {
                val params = ignoredStaticFields[fieldName]
                if (params != null) {
                    visit = false
                    if (!params.alwaysExclude) {
                        enqueue(
                            exclusion = params,
                            parent = node,
                            child = child,
                            referenceName = fieldName,
                            referenceType = ReferenceTraceElement.Type.STATIC_FIELD,
                        )
                    }
                }
            }
            if (visit) {
                enqueue(
                    exclusion = null,
                    parent = node,
                    child = child,
                    referenceName = fieldName,
                    referenceType = ReferenceTraceElement.Type.STATIC_FIELD,
                )
            }
        }
    }

    private fun visitClassInstance(node: ReferenceNode) {
        val classInstance = node.instance as ClassInstance
        val ignoredFields = LinkedHashMap<String, Exclusion>()
        var superClassObj: ClassObj? = classInstance.classObj
        var classExclusion: Exclusion? = null
        while (superClassObj != null) {
            val params = excludedRefs.classNames[superClassObj.className]
            if (params != null && (classExclusion == null || !classExclusion.alwaysExclude)) {
                // true overrides null or false.
                classExclusion = params
            }
            val classIgnoredFields = excludedRefs.fieldNameByClassName[superClassObj.className]
            if (classIgnoredFields != null) {
                ignoredFields.putAll(classIgnoredFields)
            }
            superClassObj = superClassObj.superClassObj
        }

        if (classExclusion != null && classExclusion.alwaysExclude) {
            return
        }

        for (fieldValue in classInstance.values) {
            var fieldExclusion = classExclusion
            val field = fieldValue.field
            if (field.type != Type.OBJECT) {
                continue
            }
            val child = fieldValue.value as Instance?
            val fieldName = field.name
            val params = ignoredFields[fieldName]
            // If we found a field exclusion and it's stronger than a class exclusion
            if (params != null &&
                (fieldExclusion == null || (params.alwaysExclude && !fieldExclusion.alwaysExclude))
            ) {
                fieldExclusion = params
            }
            enqueue(
                exclusion = fieldExclusion,
                parent = node,
                child = child,
                referenceName = fieldName,
                referenceType = ReferenceTraceElement.Type.INSTANCE_FIELD,
            )
        }
    }

    private fun visitArrayInstance(node: ReferenceNode) {
        val arrayInstance = node.instance as ArrayInstance
        val arrayType = arrayInstance.arrayType
        if (arrayType == Type.OBJECT) {
            val values = arrayInstance.values
            for (i in values.indices) {
                val child = values[i] as Instance?
                enqueue(
                    exclusion = null,
                    parent = node,
                    child = child,
                    referenceName = "[$i]",
                    referenceType = ReferenceTraceElement.Type.ARRAY_ENTRY,
                )
            }
        }
    }

    private fun enqueue(
        exclusion: Exclusion?,
        parent: ReferenceNode?,
        child: Instance?,
        referenceName: String?,
        referenceType: ReferenceTraceElement.Type?,
    ) {
        if (child == null) {
            return
        }
        if (isPrimitiveOrWrapperArray(child) || isPrimitiveWrapper(child)) {
            return
        }
        // Whether we want to visit now or later, we should skip if this is already to visit.
        if (toVisitSet.contains(child)) {
            return
        }
        val visitNow = exclusion == null
        if (!visitNow && toVisitIfNoPathSet.contains(child)) {
            return
        }
        if (canIgnoreStrings && isString(child)) {
            return
        }
        if (visitedSet.contains(child)) {
            return
        }
        val childNode = ReferenceNode(exclusion, child, parent, referenceName, referenceType)
        if (visitNow) {
            toVisitSet.add(child)
            toVisitQueue.add(childNode)
        } else {
            toVisitIfNoPathSet.add(child)
            toVisitIfNoPathQueue.add(childNode)
        }
    }

    companion object {
        private const val ANONYMOUS_CLASS_NAME_PATTERN = "^.+\\$\\d+$"

        private fun isString(instance: Instance): Boolean {
            return instance.classObj != null && instance.classObj.className == String::class.java.name
        }
    }
}
