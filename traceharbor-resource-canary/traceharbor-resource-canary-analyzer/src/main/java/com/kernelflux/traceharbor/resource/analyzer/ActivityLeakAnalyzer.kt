package com.kernelflux.traceharbor.resource.analyzer

import com.kernelflux.traceharbor.resource.analyzer.model.ActivityLeakResult
import com.kernelflux.traceharbor.resource.analyzer.model.ExcludedRefs
import com.kernelflux.traceharbor.resource.analyzer.model.HeapSnapshot
import com.kernelflux.traceharbor.resource.analyzer.model.ReferenceChain
import com.kernelflux.traceharbor.resource.analyzer.utils.AnalyzeUtil
import com.kernelflux.traceharbor.resource.analyzer.utils.ShortestPathFinder
import com.squareup.haha.perflib.ClassInstance
import com.squareup.haha.perflib.ClassObj
import com.squareup.haha.perflib.HahaHelper.asString
import com.squareup.haha.perflib.HahaHelper.classInstanceValues
import com.squareup.haha.perflib.HahaHelper.fieldValue
import com.squareup.haha.perflib.Instance
import com.squareup.haha.perflib.Snapshot

class ActivityLeakAnalyzer(
    private val mRefKey: String,
    private val mExcludedRefs: ExcludedRefs,
) : HeapSnapshotAnalyzer<ActivityLeakResult> {
    override fun analyze(heapSnapshot: HeapSnapshot): ActivityLeakResult {
        return checkForLeak(heapSnapshot, mRefKey)
    }

    private fun checkForLeak(heapSnapshot: HeapSnapshot, refKey: String): ActivityLeakResult {
        val analysisStartNanoTime = System.nanoTime()
        return try {
            val snapshot = heapSnapshot.getSnapshot()
            val leakingRef = findLeakingReference(refKey, snapshot)
            if (leakingRef == null) {
                ActivityLeakResult.noLeak(AnalyzeUtil.since(analysisStartNanoTime))
            } else {
                findLeakTrace(analysisStartNanoTime, snapshot, leakingRef)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            ActivityLeakResult.failure(e, AnalyzeUtil.since(analysisStartNanoTime))
        }
    }

    private fun findLeakingReference(key: String, snapshot: Snapshot): Instance? {
        val infoClass: ClassObj =
            snapshot.findClass(DESTROYED_ACTIVITY_INFO_CLASSNAME)
                ?: throw IllegalStateException(
                    "Unabled to find destroy activity info class with name: $DESTROYED_ACTIVITY_INFO_CLASSNAME",
                )

        val keysFound = ArrayList<String>()
        for (infoInstance in infoClass.instancesList) {
            val values: List<ClassInstance.FieldValue> = classInstanceValues(infoInstance)
            val keyCandidate = asString(fieldValue<Any>(values, ACTIVITY_REFERENCE_KEY_FIELDNAME))
            if (keyCandidate == key) {
                val weakRefObj: Instance? = fieldValue(values, ACTIVITY_REFERENCE_FIELDNAME)
                if (weakRefObj == null) {
                    continue
                }
                val activityRefs = classInstanceValues(weakRefObj)
                return fieldValue(activityRefs, "referent")
            }
            keysFound.add(keyCandidate)
        }
        throw IllegalStateException("Could not find weak reference with key $key in $keysFound")
    }

    private fun findLeakTrace(
        analysisStartNanoTime: Long,
        snapshot: Snapshot,
        leakingRef: Instance,
    ): ActivityLeakResult {
        val pathFinder = ShortestPathFinder(mExcludedRefs)
        val result = pathFinder.findPath(snapshot, leakingRef)
        if (result.referenceChainHead == null) {
            return ActivityLeakResult.noLeak(AnalyzeUtil.since(analysisStartNanoTime))
        }
        val referenceChain: ReferenceChain = result.buildReferenceChain()
        val className = leakingRef.classObj.className
        return if (result.excludingKnown || referenceChain.isEmpty()) {
            ActivityLeakResult.noLeak(AnalyzeUtil.since(analysisStartNanoTime))
        } else {
            ActivityLeakResult.leakDetected(false, className, referenceChain, AnalyzeUtil.since(analysisStartNanoTime))
        }
    }

    companion object {
        private const val DESTROYED_ACTIVITY_INFO_CLASSNAME =
            "com.kernelflux.traceharbor.resource.analyzer.model.DestroyedActivityInfo"
        private const val ACTIVITY_REFERENCE_KEY_FIELDNAME = "mKey"
        private const val ACTIVITY_REFERENCE_FIELDNAME = "mActivityRef"
    }
}

