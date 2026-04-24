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

package com.kernelflux.traceharbor.iocanary.detect;

import com.kernelflux.traceharbor.iocanary.config.SharePluginInfo;
import com.kernelflux.traceharbor.iocanary.util.IOCanaryUtil;
import com.kernelflux.traceharbor.report.Issue;
import com.kernelflux.traceharbor.report.IssuePublisher;
import com.kernelflux.traceharbor.util.TraceHarborLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Created by zhangshaowen on 17/7/5.
 */

public class IOCloseLeakDetector extends IssuePublisher implements InvocationHandler {
    private static final String TAG = "TraceHarbor.CloseGuardInvocationHandler";

    private final Object originalReporter;

    public IOCloseLeakDetector(OnIssueDetectListener issueListener, Object originalReporter) {
        super(issueListener);
        this.originalReporter = originalReporter;
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        TraceHarborLog.i(TAG, "invoke method: %s", method.getName());
        if (method.getName().equals("report")) {
            if (args.length != 2) {
                TraceHarborLog.e(TAG, "closeGuard report should has 2 params, current: %d", args.length);
                return null;
            }
            if (!(args[1] instanceof Throwable)) {
                TraceHarborLog.e(TAG, "closeGuard report args 1 should be throwable, current: %s", args[1]);
                return null;
            }
            Throwable throwable = (Throwable) args[1];

            String stackKey = IOCanaryUtil.getThrowableStack(throwable);
            if (isPublished(stackKey)) {
                TraceHarborLog.d(TAG, "close leak issue already published; key:%s", stackKey);
            } else {
                Issue ioIssue = new Issue(SharePluginInfo.IssueType.ISSUE_IO_CLOSABLE_LEAK);
                ioIssue.setKey(stackKey);
                JSONObject content = new JSONObject();
                try {
                    content.put(SharePluginInfo.ISSUE_FILE_STACK, stackKey);
                } catch (JSONException e) {
//                e.printStackTrace();
                    TraceHarborLog.e(TAG, "json content error: %s", e);
                }
                ioIssue.setContent(content);
                publishIssue(ioIssue);
                TraceHarborLog.i(TAG, "close leak issue publish, key:%s", stackKey);
                markPublished(stackKey);
            }


            return null;
        }
        return method.invoke(originalReporter, args);
    }
}
