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

import com.kernelflux.traceharbor.plugin.Plugin
import org.json.JSONObject

/**
 * Data struct contains the issues
 *
 * Created by zhangshaowen on 2017/8/1.
 */
open class Issue() {
    var type: Int = 0
    var tag: String? = null
    var key: String? = null
    var content: JSONObject? = null
    var plugin: Plugin? = null

    constructor(type: Int) : this() {
        this.type = type
    }

    constructor(content: JSONObject?) : this() {
        this.content = content
    }

    override fun toString(): String {
        val strContent: String = content?.toString() ?: ""
        return String.format("tag[%s]type[%d];key[%s];content[%s]", tag, type, key, strContent)
    }

    companion object {
        const val ISSUE_REPORT_TYPE: String = "type"
        const val ISSUE_REPORT_TAG: String = "tag"
        const val ISSUE_REPORT_PROCESS: String = "process"
        const val ISSUE_REPORT_TIME: String = "time"
    }
}
