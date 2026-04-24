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

package com.kernelflux.traceharbor.report

import android.content.Context
import android.content.SharedPreferences
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil

/**
 * Created by zhangshaowen on 2017/8/1.
 *
 * SharedPreferences-backed [IssuePublisher] that remembers reported keys
 * across process restarts and trims entries older than [mExpiredTime].
 *
 * Kept as `open class` so existing Java subclasses (e.g. plugin-specific
 * publishers in resource-canary, trace-canary) keep extending it. Field
 * names retain the `m` Hungarian prefix for binary compatibility with
 * any existing field reflection.
 */
open class FilePublisher(
    context: Context,
    expire: Long,
    tag: String,
    issueDetectListener: OnIssueDetectListener?,
) : IssuePublisher(issueDetectListener) {

    private val mExpiredTime: Long = expire
    private val mEditor: SharedPreferences.Editor
    private val mPublishedMap: HashMap<String, Long> = HashMap()

    /**
     * Exposed as a Kotlin property so existing Kotlin callers like
     * `NativeForkAnalyzeProcessor` can reach `watcher.context`. JVM
     * bytecode still has `Context getContext()`, matching the original
     * Java signature exactly.
     */
    @get:JvmName("getContext")
    val context: Context = context

    init {
        val spName = "TraceHarbor_$tag" + TraceHarborUtil.getProcessName(context)
        val sharedPreferences =
            context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        val current = System.currentTimeMillis()
        mEditor = sharedPreferences.edit()
        // sharedPreferences.getAll() can throw on some OEM shells; guard the
        // HashSet copy exactly like the original Java did.
        val spKeys: HashSet<String>? =
            sharedPreferences.all?.keys?.let { HashSet(it) }
        if (spKeys != null) {
            for (key in spKeys) {
                try {
                    val start = sharedPreferences.getLong(key, 0)
                    val costTime = current - start
                    if (start <= 0 || costTime > mExpiredTime) {
                        mEditor.remove(key)
                    } else {
                        mPublishedMap[key] = start
                    }
                } catch (e: ClassCastException) {
                    TraceHarborLog.printErrStackTrace(
                        TAG,
                        e,
                        "might be polluted - sp: %s, key: %s, value : %s",
                        spName,
                        key,
                        sharedPreferences.all[key],
                    )
                }
            }
        }
        mEditor.apply()
    }

    // The four override methods below are explicitly `public` because Java's
    // FilePublisher widened visibility from `protected` (in IssuePublisher) to
    // `public`. Kotlin inherits the parent's visibility on override unless we
    // say otherwise, so the explicit `public` keyword keeps Java callers like
    // `AutoDumpProcessor.getWatcher().markPublished(name)` compiling.

    open fun markPublished(key: String?, persist: Boolean) {
        if (key == null) return
        if (mPublishedMap.containsKey(key)) return
        val now = System.currentTimeMillis()
        mPublishedMap[key] = now

        if (persist) {
            mEditor.putLong(key, now).apply()
        }
    }

    public override fun markPublished(key: String?) {
        markPublished(key, true)
    }

    public override fun unMarkPublished(key: String?) {
        if (key == null) return
        if (!mPublishedMap.containsKey(key)) return
        mPublishedMap.remove(key)
        mEditor.remove(key).apply()
    }

    public override fun isPublished(key: String?): Boolean {
        if (key == null || !mPublishedMap.containsKey(key)) return false
        val start = mPublishedMap[key] ?: return false
        if (start <= 0 || (System.currentTimeMillis() - start) > mExpiredTime) {
            mEditor.remove(key).apply()
            mPublishedMap.remove(key)
            return false
        }
        return true
    }

    companion object {
        private const val TAG = "TraceHarbor.FilePublisher"
    }
}
