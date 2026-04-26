package com.kernelflux.traceharbor.resource.analyzer.model

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * If you rename this class or its fields, update analyzer-side constants:
 * ActivityLeakAnalyzer.DESTROYED_ACTIVITY_INFO_CLASSNAME,
 * ActivityLeakAnalyzer.ACTIVITY_REFERENCE_KEY_FIELDNAME and
 * ActivityLeakAnalyzer.ACTIVITY_REFERENCE_FIELDNAME.
 */
class DestroyedActivityInfo(
    @JvmField val mKey: String,
    activity: Activity,
    @JvmField val mActivityName: String,
) {
    @JvmField
    val mActivityRef: WeakReference<Activity> = WeakReference(activity)

    @JvmField
    var mDetectedCount: Int = 0
}

