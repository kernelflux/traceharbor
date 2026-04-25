package com.kernelflux.traceharbor.trace.util

import android.view.View
import android.view.ViewGroup

/**
 * Pure static utility — wrapped in a `class private constructor()` with a
 * `companion object` so callers keep using `ViewUtil.dumpViewInfo(view)`
 * unchanged. Nested `ViewInfo` POJO uses `@JvmField` to preserve the
 * original `public int` field-access pattern.
 */
class ViewUtil private constructor() {

    class ViewInfo {
        @JvmField
        var mViewCount: Int = 0

        @JvmField
        var mViewDeep: Int = 0

        @JvmField
        var mActivityName: String = ""

        override fun toString(): String =
            "ViewCount:$mViewCount,ViewDeep:$mViewDeep,mActivityName:$mActivityName"
    }

    companion object {
        @JvmStatic
        fun dumpViewInfo(view: View?): ViewInfo {
            val info = ViewInfo()
            traversalViewTree(info, 0, view)
            return info
        }

        @JvmStatic
        private fun traversalViewTree(info: ViewInfo, deep: Int, view: View?) {
            if (view == null) return

            val nextDeep = deep + 1
            if (nextDeep > info.mViewDeep) {
                info.mViewDeep = nextDeep
            }

            if (view !is ViewGroup) return

            val n = view.childCount
            if (n <= 0) return

            for (i in 0 until n) {
                val v = view.getChildAt(i)
                if (v == null || v.visibility == View.GONE) {
                    continue
                }
                info.mViewCount++
                traversalViewTree(info, nextDeep, v)
            }
        }
    }
}
