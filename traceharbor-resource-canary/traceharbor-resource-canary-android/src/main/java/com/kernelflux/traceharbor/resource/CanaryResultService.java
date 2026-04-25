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

package com.kernelflux.traceharbor.resource;

import android.content.Context;
import android.content.Intent;

import com.kernelflux.traceharbor.TraceHarbor;
import com.kernelflux.traceharbor.plugin.Plugin;
import com.kernelflux.traceharbor.report.Issue;
import com.kernelflux.traceharbor.resource.config.SharePluginInfo;
import com.kernelflux.traceharbor.util.TraceHarborLog;

import org.json.JSONObject;

//import com.kernelflux.traceharbor.util.DeviceUtil;

/**
 * Created by tangyinsheng on 2017/7/11.
 *
 */
public class CanaryResultService extends TraceHarborJobIntentService {
    private static final String TAG = "TraceHarbor.CanaryResultService";

    private static final int JOB_ID = 0xFAFBFCFE;
    private static final String ACTION_REPORT_HPROF_RESULT = "com.kernelflux.traceharbor.resource.result.action.REPORT_HPROF_RESULT";
    private static final String EXTRA_PARAM_RESULT_PATH = "RESULT_PATH";
    private static final String EXTRA_PARAM_ACTIVITY = "RESULT_ACTIVITY";

    public static void reportHprofResult(Context context, String resultPath, String activityName) {
        final Intent intent = new Intent(context, CanaryResultService.class);
        intent.setAction(ACTION_REPORT_HPROF_RESULT);
        intent.putExtra(EXTRA_PARAM_RESULT_PATH, resultPath);
        intent.putExtra(EXTRA_PARAM_ACTIVITY, activityName);
        enqueueWork(context, CanaryResultService.class, JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_REPORT_HPROF_RESULT.equals(action)) {
                final String resultPath = intent.getStringExtra(EXTRA_PARAM_RESULT_PATH);
                final String activityName = intent.getStringExtra(EXTRA_PARAM_ACTIVITY);

                if (resultPath != null && !resultPath.isEmpty()
                    && activityName != null && !activityName.isEmpty()) {
                    doReportHprofResult(resultPath, activityName);
                } else {
                    TraceHarborLog.e(TAG, "resultPath or activityName is null or empty, skip reporting.");
                }
            }
        }
    }

    // notice: compatible
    private void doReportHprofResult(String resultPath, String activityName) {
        Issue issue = new Issue(SharePluginInfo.IssueType.LEAK_FOUND);
        final JSONObject resultJson = new JSONObject();
        try {
            resultJson.put(SharePluginInfo.ISSUE_RESULT_PATH, resultPath);
            resultJson.put(SharePluginInfo.ISSUE_ACTIVITY_NAME, activityName);
            issue.setContent(resultJson);
        } catch (Throwable thr) {
            TraceHarborLog.printErrStackTrace(TAG, thr, "unexpected exception, skip reporting.");
        }

        Plugin plugin =  TraceHarbor.with().getPluginByClass(ResourcePlugin.class);
        if (plugin != null) {
            plugin.onDetectIssue(issue);
        }
    }
}
