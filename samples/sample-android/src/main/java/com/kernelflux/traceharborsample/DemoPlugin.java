package com.kernelflux.traceharborsample;

import org.json.JSONException;
import org.json.JSONObject;

import com.kernelflux.traceharbor.plugin.Plugin;
import com.kernelflux.traceharbor.report.Issue;

public class DemoPlugin extends Plugin {
    private static final int ISSUE_TYPE_DEMO = 10001;

    @Override
    public String getTag() {
        return "DemoPlugin";
    }

    public void reportIssue(String action, String detail) {
        JSONObject content = new JSONObject();
        try {
            content.put("action", action);
            content.put("detail", detail);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        Issue issue = new Issue(ISSUE_TYPE_DEMO);
        issue.setTag(getTag());
        issue.setKey(action);
        issue.setContent(content);
        onDetectIssue(issue);
    }
}
