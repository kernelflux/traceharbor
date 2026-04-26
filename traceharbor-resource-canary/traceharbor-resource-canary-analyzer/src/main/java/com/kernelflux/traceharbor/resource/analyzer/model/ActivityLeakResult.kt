package com.kernelflux.traceharbor.resource.analyzer.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ActivityLeakResult private constructor(
    @JvmField val mLeakFound: Boolean,
    @JvmField val mExcludedLeak: Boolean,
    @JvmField val mClassName: String?,
    @JvmField val referenceChain: ReferenceChain?,
    @JvmField val mFailure: Throwable?,
    @JvmField val mAnalysisDurationMs: Long,
) : AnalyzeResult() {
    @Throws(JSONException::class)
    override fun encodeToJSON(jsonObject: JSONObject) {
        val leakTraceJSONArray = JSONArray()
        if (referenceChain != null) {
            for (element in referenceChain.elements) {
                leakTraceJSONArray.put(element.toString())
            }
        }
        jsonObject
            .put("leakFound", mLeakFound)
            .put("excludedLeak", mExcludedLeak)
            .put("className", mClassName)
            .put("failure", mFailure.toString())
            .put("analysisDurationMs", mAnalysisDurationMs)
            .put("referenceChain", leakTraceJSONArray)
    }

    override fun toString(): String {
        val sb = StringBuilder("Leak Reference:")
        if (referenceChain != null) {
            for (element in referenceChain.elements) {
                sb.append(element.toCollectableString()).append(";")
            }
        }
        return sb.toString()
    }

    companion object {
        @JvmStatic
        fun noLeak(analysisDurationMs: Long): ActivityLeakResult {
            return ActivityLeakResult(false, false, null, null, null, analysisDurationMs)
        }

        @JvmStatic
        fun leakDetected(
            excludedLeak: Boolean,
            className: String,
            referenceChain: ReferenceChain,
            analysisDurationMs: Long,
        ): ActivityLeakResult {
            return ActivityLeakResult(true, excludedLeak, className, referenceChain, null, analysisDurationMs)
        }

        @JvmStatic
        fun failure(failure: Throwable, analysisDurationMs: Long): ActivityLeakResult {
            return ActivityLeakResult(false, false, null, null, failure, analysisDurationMs)
        }
    }
}

