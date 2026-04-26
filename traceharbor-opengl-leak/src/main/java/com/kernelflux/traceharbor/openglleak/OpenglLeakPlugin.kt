package com.kernelflux.traceharbor.openglleak

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.kernelflux.traceharbor.openglleak.comm.FuncNameString
import com.kernelflux.traceharbor.openglleak.detector.IOpenglIndexDetector
import com.kernelflux.traceharbor.openglleak.detector.OpenglIndexDetectorService
import com.kernelflux.traceharbor.openglleak.hook.OpenGLHook
import com.kernelflux.traceharbor.openglleak.statistics.resource.ResRecordManager
import com.kernelflux.traceharbor.openglleak.utils.ActivityRecorder
import com.kernelflux.traceharbor.openglleak.utils.EGLHelper
import com.kernelflux.traceharbor.openglleak.utils.GlLeakHandlerThread
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.plugin.PluginListener
import com.kernelflux.traceharbor.util.TraceHarborLog

class OpenglLeakPlugin(
    private val context: Context,
) : Plugin() {
    fun registerReportCallback(callback: OpenglReportCallback?) {
        if (sCallback != null) {
            TraceHarborLog.e(TAG, "OpenglReportCallback register again, May be overwrite !!!")
        }
        sCallback = callback
    }

    override fun init(application: Application, pluginListener: PluginListener) {
        super.init(application, pluginListener)
        ActivityRecorder.getInstance().start(application)
        GlLeakHandlerThread.getInstance().start()
    }

    override fun start() {
        super.start()
        Thread { startImpl() }.start()
    }

    private fun value(map: Map<*, *>, key: String): Int = map[key] as? Int ?: throw NullPointerException("Missing key: $key")

    private fun executeHook(iBinder: IBinder) {
        TraceHarborLog.e(TAG, "onServiceConnected")
        val ipc = IOpenglIndexDetector.Stub.asInterface(iBinder)
        try {
            // 通过实验进程获取 index
            val map = ipc.seekIndex()
            if (map == null) {
                TraceHarborLog.e(TAG, "indexMap null")
                return
            }
            TraceHarborLog.e(TAG, "indexMap:$map")

            // 初始化
            EGLHelper.initOpenGL()
            OpenGLHook.getInstance().init()
            TraceHarborLog.e(TAG, "init env")

            val hookResult =
                value(map, FuncNameString.GL_GEN_TEXTURES) *
                    value(map, FuncNameString.GL_DELETE_TEXTURES) *
                    value(map, FuncNameString.GL_GEN_BUFFERS) *
                    value(map, FuncNameString.GL_DELETE_BUFFERS) *
                    value(map, FuncNameString.GL_GEN_FRAMEBUFFERS) *
                    value(map, FuncNameString.GL_DELETE_FRAMEBUFFERS) *
                    value(map, FuncNameString.GL_GEN_RENDERBUFFERS) *
                    value(map, FuncNameString.GL_DELETE_RENDERBUFFERS) *
                    value(map, FuncNameString.GL_TEX_IMAGE_2D) *
                    value(map, FuncNameString.GL_BIND_TEXTURE) *
                    value(map, FuncNameString.GL_BIND_BUFFER) *
                    value(map, FuncNameString.GL_BIND_FRAMEBUFFER) *
                    value(map, FuncNameString.GL_BIND_RENDERBUFFER) *
                    value(map, FuncNameString.GL_TEX_IMAGE_3D) *
                    value(map, FuncNameString.GL_RENDER_BUFFER_STORAGE) *
                    value(map, FuncNameString.GL_BUFFER_DATA)
            TraceHarborLog.e(TAG, "hookResult = $hookResult")
            if (hookResult == 0) {
                sCallback?.onHookFail()
            } else {
                sCallback?.onHookSuccess()
            }

            // hook
            OpenGLHook.hookEgl() // hook eglCreateContext/eglDestroyContext first
            OpenGLHook.getInstance().hook(FuncNameString.GL_GEN_TEXTURES, value(map, FuncNameString.GL_GEN_TEXTURES))
            OpenGLHook.getInstance().hook(FuncNameString.GL_DELETE_TEXTURES, value(map, FuncNameString.GL_DELETE_TEXTURES))
            OpenGLHook.getInstance().hook(FuncNameString.GL_GEN_BUFFERS, value(map, FuncNameString.GL_GEN_BUFFERS))
            OpenGLHook.getInstance().hook(FuncNameString.GL_DELETE_BUFFERS, value(map, FuncNameString.GL_DELETE_BUFFERS))
            OpenGLHook.getInstance().hook(FuncNameString.GL_GEN_FRAMEBUFFERS, value(map, FuncNameString.GL_GEN_FRAMEBUFFERS))
            OpenGLHook.getInstance().hook(FuncNameString.GL_DELETE_FRAMEBUFFERS, value(map, FuncNameString.GL_DELETE_FRAMEBUFFERS))
            OpenGLHook.getInstance().hook(FuncNameString.GL_GEN_RENDERBUFFERS, value(map, FuncNameString.GL_GEN_RENDERBUFFERS))
            OpenGLHook.getInstance().hook(FuncNameString.GL_DELETE_RENDERBUFFERS, value(map, FuncNameString.GL_DELETE_RENDERBUFFERS))
            OpenGLHook.getInstance().hook(FuncNameString.GL_TEX_IMAGE_2D, value(map, FuncNameString.GL_TEX_IMAGE_2D))
            OpenGLHook.getInstance().hook(FuncNameString.GL_TEX_IMAGE_3D, value(map, FuncNameString.GL_TEX_IMAGE_3D))
            OpenGLHook.getInstance().hook(FuncNameString.GL_BIND_TEXTURE, value(map, FuncNameString.GL_BIND_TEXTURE))
            OpenGLHook.getInstance().hook(FuncNameString.GL_BIND_BUFFER, value(map, FuncNameString.GL_BIND_BUFFER))
//            OpenGLHook.getInstance().hook(FuncNameString.GL_BIND_FRAMEBUFFER, value(map, FuncNameString.GL_BIND_FRAMEBUFFER))
            OpenGLHook.getInstance().hook(FuncNameString.GL_BIND_RENDERBUFFER, value(map, FuncNameString.GL_BIND_RENDERBUFFER))
            OpenGLHook.getInstance().hook(FuncNameString.GL_BUFFER_DATA, value(map, FuncNameString.GL_BUFFER_DATA))
            OpenGLHook.getInstance().hook(FuncNameString.GL_RENDER_BUFFER_STORAGE, value(map, FuncNameString.GL_RENDER_BUFFER_STORAGE))
            TraceHarborLog.e(TAG, "hook finish")
        } catch (e: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, e, "")
        } finally {
            // 销毁实验进程
            try {
                TraceHarborLog.i(TAG, "destory test process")
                ipc.destory()
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
        }
    }

    private fun startImpl() {
        val service = Intent(context, OpenglIndexDetectorService::class.java)
        var result = false
        try {
            result =
                context.bindService(
                    service,
                    object : ServiceConnection {
                        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                            Thread { executeHook(iBinder) }.start()
                        }

                        override fun onServiceDisconnected(componentName: ComponentName) {
                            context.unbindService(this)
                        }
                    },
                    Context.BIND_AUTO_CREATE,
                )
        } catch (e: Exception) {
            TraceHarborLog.d(TAG, "bindService error = ${e.cause}")
        }

        TraceHarborLog.d(TAG, "bindService result = $result")
        if (result) {
            sCallback?.onExpProcessSuccess()
        } else {
            sCallback?.onExpProcessFail()
        }
    }

    override fun stop() {
        super.stop()
    }

    override fun destroy() {
        super.destroy()
    }

    override val tag: String
        get() = "OpenglLeak"

    fun setNativeStackDump(open: Boolean) {
        OpenGLHook.getInstance().setNativeStackDump(open)
    }

    fun setJavaStackDump(open: Boolean) {
        OpenGLHook.getInstance().setJavaStackDump(open)
    }

    fun clear() {
        ResRecordManager.getInstance().clear()
    }

    companion object {
        private const val TAG = "traceharbor.OpenglLeakPlugin"

        @JvmField
        var sCallback: OpenglReportCallback? = null
    }
}

