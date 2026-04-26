package com.kernelflux.traceharbor.sqlitelint.behaviour.alert

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.Toolbar
import com.kernelflux.traceharbor.sqlitelint.R

abstract class SQLiteLintBaseActivity : Activity() {
    private var mToolBar: Toolbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onCreateView()
    }

    protected fun onCreateView() {
        setContentView(R.layout.activity_sqlitelint_base)
        val contentLayout = findViewById<FrameLayout>(R.id.content)
        val layoutInflater = LayoutInflater.from(this)
        val layoutId = getLayoutId()
        assert(layoutId != 0)
        layoutInflater.inflate(layoutId, contentLayout)

        if (Build.VERSION.SDK_INT >= 21) {
            mToolBar = findViewById(R.id.toolbar)
            mToolBar?.setNavigationOnClickListener(object : View.OnClickListener {
                override fun onClick(v: View) {
                    onBackBtnClick()
                }
            })
            val drawable: Drawable? = mToolBar?.logo
            drawable?.setVisible(false, true)
        } else {
            Toast.makeText(this, "SQLiteLint toolbar only support in api level >= 21.", Toast.LENGTH_LONG)
        }
    }

    protected fun setTitle(title: String) {
        if (Build.VERSION.SDK_INT >= 21) {
            mToolBar?.title = title
        }
    }

    protected fun onBackBtnClick() {
        finish()
    }

    protected abstract fun getLayoutId(): Int
}
