/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kernelflux.traceharbor.resource.analyzer

import com.kernelflux.traceharbor.resource.analyzer.model.DuplicatedBitmapResult
import com.kernelflux.traceharbor.resource.analyzer.model.DuplicatedBitmapResult.DuplicatedBitmapEntry
import com.kernelflux.traceharbor.resource.analyzer.model.ExcludedBmps
import com.kernelflux.traceharbor.resource.analyzer.model.HeapSnapshot
import com.kernelflux.traceharbor.resource.analyzer.model.ReferenceChain
import com.kernelflux.traceharbor.resource.analyzer.model.ReferenceNode
import com.kernelflux.traceharbor.resource.analyzer.utils.AnalyzeUtil
import com.kernelflux.traceharbor.resource.analyzer.utils.ShortestPathFinder
import com.kernelflux.traceharbor.resource.analyzer.utils.ShortestPathFinder.Result
import com.squareup.haha.perflib.ArrayInstance
import com.squareup.haha.perflib.ClassInstance
import com.squareup.haha.perflib.ClassInstance.FieldValue
import com.squareup.haha.perflib.ClassObj
import com.squareup.haha.perflib.HahaHelper
import com.squareup.haha.perflib.Heap
import com.squareup.haha.perflib.Instance
import com.squareup.haha.perflib.Snapshot
import com.squareup.haha.perflib.StackTrace
import com.squareup.haha.perflib.analysis.ShortestDistanceVisitor
import java.lang.reflect.Field
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet

/**
 * Created by tangyinsheng on 2017/6/6.
 */
class DuplicatedBitmapAnalyzer(
    private val mMinBmpLeakSize: Int,
    private val mExcludedBmps: ExcludedBmps,
) : HeapSnapshotAnalyzer<DuplicatedBitmapResult> {
    private var mMStackField: Field? = null
    private var mMLengthField: Field? = null
    private var mMValueOffsetField: Field? = null

    override fun analyze(heapSnapshot: HeapSnapshot): DuplicatedBitmapResult {
        val analysisStartNanoTime = System.nanoTime()

        return try {
            val snapshot = heapSnapshot.getSnapshot()
            ShortestDistanceVisitor().doVisit(snapshot.gcRoots)
            findDuplicatedBitmap(analysisStartNanoTime, snapshot)
        } catch (e: Throwable) {
            e.printStackTrace()
            DuplicatedBitmapResult.failure(e, AnalyzeUtil.since(analysisStartNanoTime))
        }
    }

    private fun findDuplicatedBitmap(
        analysisStartNanoTime: Long,
        snapshot: Snapshot,
    ): DuplicatedBitmapResult {
        val bitmapClass = snapshot.findClass("android.graphics.Bitmap")
        if (bitmapClass == null) {
            return DuplicatedBitmapResult.noDuplicatedBitmap(AnalyzeUtil.since(analysisStartNanoTime))
        }

        val byteArrayToBitmapMap = HashMap<ArrayInstance, Instance>()
        val byteArrays = HashSet<ArrayInstance>()

        val reachableInstances = ArrayList<Instance>()
        for (heap in snapshot.heaps) {
            if ("default" != heap.name && "app" != heap.name) {
                continue
            }

            val bitmapInstances = bitmapClass.getHeapInstances(heap.id)
            for (bitmapInstance in bitmapInstances) {
                if (bitmapInstance.distanceToGcRoot == Int.MAX_VALUE) {
                    continue
                }
                reachableInstances.add(bitmapInstance)
            }
            for (bitmapInstance in reachableInstances) {
                var buffer: ArrayInstance? =
                    HahaHelper.fieldValue((bitmapInstance as ClassInstance).values, "mBuffer")
                if (buffer != null) {
                    // sizeof(byte) * bufferLength -> bufferSize
                    val bufferSize = buffer.size
                    if (bufferSize < mMinBmpLeakSize) {
                        // Ignore tiny bmp leaks.
                        System.out.println(" + Skiped a bitmap with size: $bufferSize")
                        continue
                    }
                    if (byteArrayToBitmapMap.containsKey(buffer)) {
                        buffer = cloneArrayInstance(buffer)
                        if (buffer == null) {
                            continue
                        }
                    }
                    byteArrayToBitmapMap[buffer!!] = bitmapInstance
                } else {
                    System.out.println(" + Skiped a no-data bitmap")
                }
            }
            byteArrays.addAll(byteArrayToBitmapMap.keys)
        }

        if (byteArrays.size <= 1) {
            return DuplicatedBitmapResult.noDuplicatedBitmap(AnalyzeUtil.since(analysisStartNanoTime))
        }

        val duplicatedBitmapEntries = ArrayList<DuplicatedBitmapEntry>()

        val commonPrefixSets = ArrayList<Set<ArrayInstance>>()
        val reducedPrefixSets = ArrayList<Set<ArrayInstance>>()
        commonPrefixSets.add(byteArrays)

        // Cache the values since instance.getValues() recreates the array on every invocation.
        val cachedValues = HashMap<ArrayInstance, Array<Any?>>()
        for (instance in byteArrays) {
            cachedValues[instance] = instance.values
        }

        var columnIndex = 0
        while (commonPrefixSets.isNotEmpty()) {
            for (commonPrefixArrays in commonPrefixSets) {
                val entryClassifier = HashMap<Any?, MutableSet<ArrayInstance>>(commonPrefixArrays.size)

                for (arrayInstance in commonPrefixArrays) {
                    val element = cachedValues[arrayInstance]!![columnIndex]
                    if (entryClassifier.containsKey(element)) {
                        entryClassifier[element]!!.add(arrayInstance)
                    } else {
                        val instanceSet = HashSet<ArrayInstance>()
                        instanceSet.add(arrayInstance)
                        entryClassifier[element] = instanceSet
                    }
                }

                for (branch in entryClassifier.values) {
                    if (branch.size <= 1) {
                        // Unique branch, ignore it and it won't be counted towards duplication.
                        continue
                    }

                    val terminatedArrays = HashSet<ArrayInstance>()

                    // Move all ArrayInstance that we have hit the end of to the candidate result list.
                    for (instance in branch) {
                        if (HahaHelper.getArrayInstanceLength(instance) == columnIndex + 1) {
                            terminatedArrays.add(instance)
                        }
                    }
                    branch.removeAll(terminatedArrays)

                    // Exact duplicated arrays found.
                    if (terminatedArrays.size > 1) {
                        var rawBuffer: ByteArray? = null
                        var width = 0
                        var height = 0
                        val duplicateBitmaps = ArrayList<Instance>()
                        for (terminatedArray in terminatedArrays) {
                            val bmpInstance = byteArrayToBitmapMap[terminatedArray] ?: continue
                            duplicateBitmaps.add(bmpInstance)
                            if (rawBuffer == null) {
                                val fieldValues = (bmpInstance as ClassInstance).values
                                width = HahaHelper.fieldValue(fieldValues, "mWidth")
                                height = HahaHelper.fieldValue(fieldValues, "mHeight")
                                val byteArraySize = HahaHelper.getArrayInstanceLength(terminatedArray)
                                rawBuffer = HahaHelper.asRawByteArray(terminatedArray, 0, byteArraySize)
                            }
                        }

                        val results: Map<Instance, Result> = ShortestPathFinder(mExcludedBmps)
                            .findPath(snapshot, duplicateBitmaps)
                        val referenceChains = ArrayList<ReferenceChain>()
                        for (result in results.values) {
                            if (result.excludingKnown) {
                                continue
                            }
                            var currRefChainNode: ReferenceNode = result.referenceChainHead ?: continue
                            while (true) {
                                val tempNode = currRefChainNode.parent ?: break
                                val tempNodeInstance = tempNode.instance
                                if (tempNodeInstance == null) {
                                    currRefChainNode = tempNode
                                    continue
                                }
                                val rootHolderHeap: Heap? = tempNodeInstance.heap
                                if (rootHolderHeap != null && "app" != rootHolderHeap.name) {
                                    break
                                } else {
                                    currRefChainNode = tempNode
                                }
                            }
                            val gcRootHolder = currRefChainNode.instance
                            if (gcRootHolder !is ClassObj) {
                                continue
                            }
                            val holderClassName = gcRootHolder.className
                            var isExcluded = false
                            for (patternInfo in mExcludedBmps.mClassNamePatterns) {
                                if (!patternInfo.mForGCRootOnly) {
                                    continue
                                }
                                if (patternInfo.mPattern.matcher(holderClassName).matches()) {
                                    System.out.println(
                                        " + Skipped a bitmap with gc root class: " +
                                            holderClassName + " by pattern: " + patternInfo.mPattern.toString(),
                                    )
                                    isExcluded = true
                                    break
                                }
                            }
                            if (!isExcluded) {
                                referenceChains.add(result.buildReferenceChain())
                            }
                        }
                        if (referenceChains.size > 1) {
                            duplicatedBitmapEntries.add(
                                DuplicatedBitmapEntry(width, height, rawBuffer!!, referenceChains),
                            )
                        }
                    }

                    // If there are ArrayInstances that have identical prefixes and haven't hit the
                    // end, add it back for the next iteration.
                    if (branch.size > 1) {
                        reducedPrefixSets.add(branch)
                    }
                }
            }

            commonPrefixSets.clear()
            commonPrefixSets.addAll(reducedPrefixSets)
            reducedPrefixSets.clear()
            columnIndex++
        }

        return DuplicatedBitmapResult.duplicatedBitmapDetected(
            duplicatedBitmapEntries,
            AnalyzeUtil.since(analysisStartNanoTime),
        )
    }

    private fun cloneArrayInstance(orig: ArrayInstance): ArrayInstance? {
        return try {
            if (mMStackField == null) {
                mMStackField = Instance::class.java.getDeclaredField("mStack")
                mMStackField!!.isAccessible = true
            }
            val stack = mMStackField!!.get(orig) as StackTrace

            if (mMLengthField == null) {
                mMLengthField = ArrayInstance::class.java.getDeclaredField("mLength")
                mMLengthField!!.isAccessible = true
            }
            val length = mMLengthField!!.getInt(orig)

            if (mMValueOffsetField == null) {
                mMValueOffsetField = ArrayInstance::class.java.getDeclaredField("mValuesOffset")
                mMValueOffsetField!!.isAccessible = true
            }
            val valueOffset = mMValueOffsetField!!.getLong(orig)

            val result = ArrayInstance(orig.id, stack, orig.arrayType, length, valueOffset)
            result.heap = orig.heap
            result
        } catch (thr: Throwable) {
            thr.printStackTrace()
            null
        }
    }
}
