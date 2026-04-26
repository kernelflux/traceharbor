package com.kernelflux.traceharbor.resource

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextWatcher
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ProgressBar
import android.widget.TextView
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.lang.reflect.Field
import java.util.ArrayList

object ActivityLeakFixer {
    private const val TAG = "TraceHarbor.ActivityLeakFixer"

    private var sGroupAndOutChildren: Pair<ViewGroup, ArrayList<View>>? = null

    @JvmStatic
    fun fixViewLocationHolderLeakApi28(destContext: Context) {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P) {
            return
        }
        try {
            val application = destContext.applicationContext
            if (sGroupAndOutChildren == null) {
                val viewGroup: ViewGroup = FrameLayout(application)
                for (i in 0 until 32) {
                    val childView = View(application)
                    viewGroup.addView(childView)
                }
                sGroupAndOutChildren = Pair(viewGroup, ArrayList())
            }
            sGroupAndOutChildren?.first?.addChildrenForAccessibility(sGroupAndOutChildren?.second)
        } catch (e: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, e, "fixViewLocationHolderLeakApi28 err")
        }
    }

    @JvmStatic
    fun fixInputMethodManagerLeak(destContext: Context?) {
        val startTick = System.currentTimeMillis()
        do {
            if (destContext == null) {
                break
            }
            val imm =
                destContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    ?: break
            val viewFieldNames = arrayOf("mCurRootView", "mServedView", "mNextServedView")
            for (viewFieldName in viewFieldNames) {
                try {
                    val paramField = imm.javaClass.getDeclaredField(viewFieldName)
                    if (!paramField.isAccessible) {
                        paramField.isAccessible = true
                    }
                    val obj = paramField.get(imm)
                    if (obj is View) {
                        val view = obj
                        if (view.context === destContext) {
                            paramField.set(imm, null)
                        } else {
                            TraceHarborLog.i(
                                TAG,
                                "fixInputMethodManagerLeak break, context is not suitable, get_context=${view.context} dest_context=$destContext",
                            )
                            break
                        }
                    }
                } catch (thr: Throwable) {
                    TraceHarborLog.e(
                        TAG,
                        "failed to fix InputMethodManagerLeak, %s",
                        thr.toString()
                    )
                }
            }
        } while (false)

        TraceHarborLog.i(
            TAG,
            "fixInputMethodManagerLeak done, cost: %s ms.",
            System.currentTimeMillis() - startTick
        )
    }

    @JvmField
    var sSupportSplit: Boolean = false

    @JvmStatic
    fun unbindDrawables(ui: Activity?) {
        val startTick = System.currentTimeMillis()
        if (ui != null && ui.window != null && ui.window.peekDecorView() != null) {
            var viewRoot = ui.window.peekDecorView().rootView
            try {
                unbindDrawablesAndRecycle(viewRoot)
                if (Build.VERSION.SDK_INT >= 31 && sSupportSplit) {
                    viewRoot = ui.window.decorView.findViewById(android.R.id.content)
                }
                if (viewRoot is ViewGroup) {
                    viewRoot.removeAllViews()
                }
            } catch (thr: Throwable) {
                TraceHarborLog.w(TAG, "caught unexpected exception when unbind drawables.", thr)
            }
        } else {
            TraceHarborLog.i(TAG, "unbindDrawables, ui or ui's window is null, skip rest works.")
        }
        TraceHarborLog.i(
            TAG,
            "unbindDrawables done, cost: %s ms.",
            System.currentTimeMillis() - startTick
        )
    }

    private fun unbindDrawablesAndRecycle(view: View?) {
        if (view == null || view.context == null) {
            return
        }
        recycleView(view)
        if (view is ImageView) {
            recycleImageView(view)
        }
        if (view is TextView) {
            recycleTextView(view)
        }
        if (view is ProgressBar) {
            recycleProgressBar(view)
        }
        if (view is android.widget.ListView) {
            recycleListView(view)
        }
        if (view is FrameLayout) {
            recycleFrameLayout(view)
        }
        if (view is LinearLayout) {
            recycleLinearLayout(view)
        }
        if (view is ViewGroup) {
            recycleViewGroup(view)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun cleanContextOfView(view: View) {
        try {
            val contextField = View::class.java.getDeclaredField("mContext")
            contextField.isAccessible = true
            contextField.set(view, null)
        } catch (_: Throwable) {
        }
    }

    private fun recycleView(view: View?) {
        if (view == null) {
            return
        }
        val isClickable = view.isClickable
        val isLongClickable = view.isLongClickable

        try {
            view.setOnClickListener(null)
        } catch (_: Throwable) {
        }
        try {
            view.setOnCreateContextMenuListener(null)
        } catch (_: Throwable) {
        }
        try {
            view.onFocusChangeListener = null
        } catch (_: Throwable) {
        }
        try {
            view.setOnKeyListener(null)
        } catch (_: Throwable) {
        }
        try {
            view.setOnLongClickListener(null)
        } catch (_: Throwable) {
        }
        try {
            view.setOnClickListener(null)
        } catch (_: Throwable) {
        }
        try {
            view.setOnTouchListener(null)
        } catch (_: Throwable) {
        }

        if (view.background != null) {
            view.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        try {
                            v.background.callback = null
                            v.setBackgroundDrawable(null)
                        } catch (_: Throwable) {
                        }
                        try {
                            v.destroyDrawingCache()
                        } catch (_: Throwable) {
                        }
                        v.removeOnAttachStateChangeListener(this)
                    }
                },
            )
        }

        view.isClickable = isClickable
        view.isLongClickable = isLongClickable
    }

    private fun recycleImageView(iv: ImageView?) {
        if (iv == null) {
            return
        }
        val drawable = iv.drawable
        drawable?.callback = null
        iv.setImageDrawable(null)
    }

    private fun recycleTextView(tv: TextView) {
        val drawables = tv.compoundDrawables
        for (d in drawables) {
            d?.callback = null
        }
        tv.setCompoundDrawables(null, null, null, null)
        tv.setOnEditorActionListener(null)
        tv.keyListener = null
        tv.movementMethod = null
        if (tv is EditText) {
            fixTextWatcherLeak(tv)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    @Suppress("UNCHECKED_CAST")
    private fun fixTextWatcherLeak(tv: TextView?) {
        if (tv == null) {
            return
        }
        try {
            tv.hint = ""
            val listenersField: Field = TextView::class.java.getDeclaredField("mListeners")
            listenersField.isAccessible = true
            val listeners = listenersField.get(tv)
            if (listeners is ArrayList<*>) {
                (listeners as ArrayList<TextWatcher>).clear()
            }
        } catch (_: Throwable) {
        }
    }

    private fun recycleProgressBar(pb: ProgressBar) {
        val progressDrawable = pb.progressDrawable
        if (progressDrawable != null) {
            pb.progressDrawable = null
            progressDrawable.callback = null
        }
        val indeterminateDrawable = pb.indeterminateDrawable
        if (indeterminateDrawable != null) {
            pb.indeterminateDrawable = null
            indeterminateDrawable.callback = null
        }
    }

    private fun recycleListView(listView: android.widget.ListView) {
        val selector: Drawable? = listView.selector
        selector?.callback = null
        try {
            val adapter: ListAdapter? = listView.adapter
            if (adapter != null) {
                listView.adapter = null
            }
        } catch (_: Throwable) {
        }
        try {
            listView.setOnScrollListener(null)
        } catch (_: Throwable) {
        }
        try {
            listView.onItemClickListener = null
        } catch (_: Throwable) {
        }
        try {
            listView.onItemLongClickListener = null
        } catch (_: Throwable) {
        }
        try {
            listView.onItemSelectedListener = null
        } catch (_: Throwable) {
        }
    }

    private fun recycleFrameLayout(fl: FrameLayout?) {
        if (fl != null) {
            val fg = fl.foreground
            if (fg != null) {
                fg.callback = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    fl.foreground = null
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt", "DiscouragedPrivateApi")
    private fun recycleLinearLayout(ll: LinearLayout?) {
        if (ll == null) {
            return
        }
        if (Build.VERSION_CODES.HONEYCOMB <= Build.VERSION.SDK_INT) {
            var dividerDrawable: Drawable? = null
            if (Build.VERSION_CODES.JELLY_BEAN <= Build.VERSION.SDK_INT) {
                dividerDrawable = ll.dividerDrawable
            } else {
                try {
                    val dividerField = ll.javaClass.getDeclaredField("mDivider")
                    dividerField.isAccessible = true
                    dividerDrawable = dividerField.get(ll) as Drawable?
                } catch (_: Throwable) {
                }
            }
            if (dividerDrawable != null) {
                dividerDrawable.callback = null
                ll.dividerDrawable = null
            }
        }
    }

    private fun recycleViewGroup(vg: ViewGroup) {
        val childCount = vg.childCount
        for (i in 0 until childCount) {
            unbindDrawablesAndRecycle(vg.getChildAt(i))
        }
    }
}

