package io.traceharbor.openglleak;

public interface OpenglReportCallback {

    void onExpProcessSuccess();

    void onExpProcessFail();

    void onHookSuccess();

    void onHookFail();

}
