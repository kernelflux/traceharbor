package com.kernelflux.traceharbor.resource.dumper

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.Toast
import com.kernelflux.traceharbor.resource.R
import com.kernelflux.traceharbor.resource.analyzer.model.HeapDump
import com.kernelflux.traceharbor.resource.leakcanary.internal.FutureResult
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

class AndroidHeapDumper(
    private val mContext: Context,
    private val mMainHandler: Handler,
) {
    interface HeapDumpHandler {
        fun process(result: HeapDump)
    }

    constructor(context: Context) : this(context, Handler(Looper.getMainLooper()))

    fun dumpHeap(isShowToast: Boolean): File? {
        val hprofFile =
            try {
                HprofFileManager.prepareHprofFile("", false)
            } catch (e: FileNotFoundException) {
                TraceHarborLog.printErrStackTrace(TAG, e, "")
                null
            }

        if (hprofFile == null) {
            TraceHarborLog.w(TAG, "hprof file is null.")
            return null
        }

        val hprofDir = hprofFile.parentFile
        if (hprofDir == null) {
            TraceHarborLog.w(TAG, "hprof file path: %s does not indicate a full path.", hprofFile.absolutePath)
            return null
        }
        if (!hprofDir.canWrite()) {
            TraceHarborLog.w(TAG, "hprof file path: %s cannot be written.", hprofFile.absolutePath)
            return null
        }
        if (hprofDir.freeSpace < (1.5 * 1024 * 1024 * 1024).toLong()) {
            TraceHarborLog.w(TAG, "hprof file path: %s free space not enough", hprofDir.absolutePath)
            return null
        }

        if (isShowToast) {
            val waitingForToast = FutureResult<Toast>()
            showToast(waitingForToast)
            if (!waitingForToast.wait(5, TimeUnit.SECONDS)) {
                TraceHarborLog.w(TAG, "give up dumping heap, waiting for toast too long.")
                return null
            }
            return try {
                Debug.dumpHprofData(hprofFile.absolutePath)
                cancelToast(waitingForToast.get())
                hprofFile
            } catch (e: Exception) {
                TraceHarborLog.printErrStackTrace(TAG, e, "failed to dump heap into file: %s.", hprofFile.absolutePath)
                null
            }
        }

        return try {
            Debug.dumpHprofData(hprofFile.absolutePath)
            hprofFile
        } catch (e: Exception) {
            TraceHarborLog.printErrStackTrace(TAG, e, "failed to dump heap into file: %s.", hprofFile.absolutePath)
            null
        }
    }

    private fun showToast(waitingForToast: FutureResult<Toast>) {
        mMainHandler.post {
            val toast = Toast(mContext)
            toast.duration = Toast.LENGTH_LONG
            toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0)
            val inflater = LayoutInflater.from(mContext)
            toast.view = inflater.inflate(R.layout.resource_canary_toast_wait_for_heapdump, null)
            toast.show()
            Looper.myQueue().addIdleHandler(
                MessageQueue.IdleHandler {
                    waitingForToast.set(toast)
                    false
                },
            )
        }
    }

    private fun cancelToast(toast: Toast) {
        mMainHandler.post { toast.cancel() }
    }

    companion object {
        private const val TAG = "TraceHarbor.AndroidHeapDumper"
    }
}

