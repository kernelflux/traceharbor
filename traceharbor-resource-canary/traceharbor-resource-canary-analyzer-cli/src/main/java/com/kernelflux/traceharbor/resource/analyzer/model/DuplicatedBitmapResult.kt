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

package com.kernelflux.traceharbor.resource.analyzer.model

import com.kernelflux.traceharbor.resource.common.utils.DigestUtil
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.util.Collections

/**
 * Created by tangyinsheng on 2017/6/6.
 */
class DuplicatedBitmapResult private constructor(
    duplicatedBitmapEntries: Collection<DuplicatedBitmapEntry>,
    private val mAnalyzeDurationMs: Long,
    private val mFailure: Throwable?,
) : AnalyzeResult() {

    private val mDuplicatedBitmapEntries: List<DuplicatedBitmapEntry> =
        Collections.unmodifiableList(duplicatedBitmapEntries.toList())

    fun getDuplicatedBitmapEntries(): List<DuplicatedBitmapEntry> {
        return mDuplicatedBitmapEntries
    }

    @Throws(JSONException::class)
    override fun encodeToJSON(jsonObject: JSONObject) {
        val duplicatedBitmapEntriesJSONArr = JSONArray()
        for (entry in mDuplicatedBitmapEntries) {
            duplicatedBitmapEntriesJSONArr.put(entry.toJSONObject())
        }
        jsonObject.put("targetFound", mDuplicatedBitmapEntries.isNotEmpty())
            .put("analyzeDurationMs", mAnalyzeDurationMs)
            .put("mFailure", mFailure.toString())
            .put("duplicatedBitmapEntries", duplicatedBitmapEntriesJSONArr)
    }

    class DuplicatedBitmapEntry(
        width: Int,
        height: Int,
        private val mBuffer: ByteArray,
        referenceChains: Collection<ReferenceChain>,
    ) : Serializable {
        private val mBufferHash: String = DigestUtil.getMD5String(mBuffer)
        private val mWidth: Int = width
        private val mHeight: Int = height
        private val mReferenceChains: List<ReferenceChain> =
            Collections.unmodifiableList(referenceChains.toList())

        fun getBufferHash(): String {
            return mBufferHash
        }

        fun getWidth(): Int {
            return mWidth
        }

        fun getHeight(): Int {
            return mHeight
        }

        fun getBuffer(): ByteArray {
            return mBuffer
        }

        fun getBufferSize(): Int {
            return mBuffer.size
        }

        @Throws(JSONException::class)
        fun toJSONObject(): JSONObject {
            val result = JSONObject()
            val referenceChainsJson = JSONArray()
            for (referenceChain in mReferenceChains) {
                val referenceChainJson = JSONArray()
                for (element in referenceChain.elements) {
                    referenceChainJson.put(element)
                }
                referenceChainsJson.put(referenceChainJson)
            }
            result.put("bufferHash", mBufferHash)
            result.put("width", mWidth)
            result.put("height", mHeight)
            result.put("bufferSize", getBufferSize())
            result.put("referenceChains", referenceChainsJson)
            return result
        }
    }

    companion object {
        @JvmStatic
        fun noDuplicatedBitmap(analyzeDurationMs: Long): DuplicatedBitmapResult {
            return DuplicatedBitmapResult(
                emptyList(),
                analyzeDurationMs,
                null,
            )
        }

        @JvmStatic
        fun failure(failure: Throwable, analyzeDurationMs: Long): DuplicatedBitmapResult {
            return DuplicatedBitmapResult(
                emptyList(),
                analyzeDurationMs,
                failure,
            )
        }

        @JvmStatic
        fun duplicatedBitmapDetected(
            duplicatedBitmapEntries: Collection<DuplicatedBitmapEntry>,
            analyzeDurationMs: Long,
        ): DuplicatedBitmapResult {
            return DuplicatedBitmapResult(duplicatedBitmapEntries, analyzeDurationMs, null)
        }
    }
}
