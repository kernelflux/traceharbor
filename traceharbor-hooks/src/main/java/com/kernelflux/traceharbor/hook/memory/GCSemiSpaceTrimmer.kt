package com.kernelflux.traceharbor.hook.memory

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.kernelflux.traceharbor.hook.HookManager.NativeLibraryLoader
import com.kernelflux.traceharbor.util.TraceHarborLog
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object GCSemiSpaceTrimmer {
    private const val TAG = "TraceHarbor.GCSemiSpaceTrimmer"
    private val NOT_NUM_PATTERN = Pattern.compile("[^0-9]")
    private val DEFAULT_VMSIZE_SAMPLE_INTERVAL = TimeUnit.MINUTES.toMillis(3)

    private var nativeLibLoader: NativeLibraryLoader? = null
    private var criticalVmSizeRatio = 0.0f
    private var vmSizeSampleInterval = DEFAULT_VMSIZE_SAMPLE_INTERVAL
    private var sampleThread: HandlerThread? = null
    private var sampleHandler: Handler? = null
    private var nativeLibLoaded = false
    private var installed = false

    fun setNativeLibraryLoader(loader: NativeLibraryLoader?): GCSemiSpaceTrimmer {
        nativeLibLoader = loader
        return this
    }

    private fun ensureNativeLibLoaded(): Boolean {
        synchronized(this) {
            if (nativeLibLoaded) {
                return true
            }
            try {
                if (nativeLibLoader != null) {
                    nativeLibLoader?.apply {
                        loadLibrary("traceharbor-hookcommon")
                        loadLibrary("traceharbor-memoryhook")
                    }
                } else {
                    System.loadLibrary("traceharbor-hookcommon")
                    System.loadLibrary("traceharbor-memoryhook")
                }
                nativeLibLoaded = true
            } catch (thr: Throwable) {
                TraceHarborLog.printErrStackTrace(TAG, thr, "Fail to load native library.")
                nativeLibLoaded = false
            }
            return nativeLibLoaded
        }
    }

    fun isCompatible(): Boolean {
        synchronized(this) {
            if (!ensureNativeLibLoaded()) {
                return false
            }
            return nativeIsCompatible()
        }
    }

    fun install(
        criticalVmSizeRatio: Float,
        vmsizeSampleInterval: Long,
        vmSampleLooper: Looper?
    ): Boolean {
        synchronized(this) {
            if (installed) {
                TraceHarborLog.e(TAG, "Already installed.")
                return true
            }
            if (!ensureNativeLibLoaded()) {
                TraceHarborLog.e(TAG, "Fail to load native library.")
                return false
            }
            this.criticalVmSizeRatio = criticalVmSizeRatio
            if (vmsizeSampleInterval > 0) {
                vmSizeSampleInterval = vmsizeSampleInterval
            } else if (vmsizeSampleInterval == 0L) {
                vmSizeSampleInterval = DEFAULT_VMSIZE_SAMPLE_INTERVAL
            } else {
                TraceHarborLog.e(
                    TAG,
                    "vmsizeSampleInterval cannot less than zero. (value: $vmsizeSampleInterval)"
                )
                return false
            }
            if (vmSampleLooper != null) {
                sampleHandler = Handler(vmSampleLooper)
            } else {
                sampleThread = HandlerThread("TraceHarbor.GCSST")
                sampleThread!!.start()
                sampleHandler = Handler(sampleThread!!.looper)
            }
            sampleHandler!!.postDelayed(sampleTask, vmSizeSampleInterval)
            TraceHarborLog.i(
                TAG,
                "Installed, critcal_vmsize_ratio: %s, vmsize_sample_interval: %s",
                criticalVmSizeRatio,
                vmsizeSampleInterval
            )
            return true
        }
    }

    private fun readVmSize(): Long {
        var vssSize = -1L
        try {
            BufferedReader(InputStreamReader(FileInputStream("/proc/self/status"))).use { br ->
                var content: String? = br.readLine()
                while (content != null) {
                    val lower = content.lowercase()
                    if (lower.contains("vmsize")) {
                        // current vss size
                        vssSize =
                            NOT_NUM_PATTERN.matcher(lower).replaceAll("").trim().toLong() * 1024
                        break
                    }
                    content = br.readLine()
                }
            }
        } catch (thr: Throwable) {
            TraceHarborLog.printErrStackTrace(TAG, thr, "read proc status failed.")
        }
        return vssSize
    }

    private val sampleTask = object : Runnable {
        override fun run() {
            val vmSize = readVmSize()
            val handler = sampleHandler ?: return
            if (vmSize < 0) {
                TraceHarborLog.e(TAG, "Fail to read vss size, skip checking this time.")
                handler.postDelayed(this, vmSizeSampleInterval)
            } else {
                // (vmsize / 4G > ratio) => (vmsize > ratio * 4G)
                if (vmSize > 4L * 1024 * 1024 * 1024 * criticalVmSizeRatio) {
                    TraceHarborLog.i(
                        TAG,
                        "VmSize usage reaches above critical level, trigger native install. vmsize: %s, critical_ratio: %s",
                        vmSize,
                        criticalVmSizeRatio
                    )
                    val nativeInstallRes = nativeInstall()
                    if (nativeInstallRes) {
                        TraceHarborLog.i(TAG, "nativeInstall triggered successfully.")
                    } else {
                        TraceHarborLog.i(TAG, "Fail to trigger nativeInstall.")
                    }
                } else {
                    TraceHarborLog.i(
                        TAG,
                        "VmSize usage is under critical level, check next time. vmsize: %s, critical_ratio: %s",
                        vmSize,
                        criticalVmSizeRatio
                    )
                    handler.postDelayed(this, vmSizeSampleInterval)
                }
            }
        }
    }

    private external fun nativeIsCompatible(): Boolean
    private external fun nativeInstall(): Boolean
}

