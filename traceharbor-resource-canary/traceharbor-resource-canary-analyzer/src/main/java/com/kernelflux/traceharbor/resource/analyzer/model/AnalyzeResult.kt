package com.kernelflux.traceharbor.resource.analyzer.model

import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable

abstract class AnalyzeResult : Serializable {
    @Throws(JSONException::class)
    abstract fun encodeToJSON(jsonObject: JSONObject)
}

