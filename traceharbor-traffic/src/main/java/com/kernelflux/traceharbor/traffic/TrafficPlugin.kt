package com.kernelflux.traceharbor.traffic

import androidx.annotation.Keep
import com.kernelflux.traceharbor.plugin.Plugin
import com.kernelflux.traceharbor.util.TraceHarborLog
import com.kernelflux.traceharbor.util.TraceHarborUtil
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Plugin entry point for the traffic monitor.
 *
 * Native bridges (`nativeInitTraceHarborTraffic`, `nativeReleaseTraceHarborTraffic`,
 * `nativeGetTrafficInfoMap`, ...) are kept as `external fun` declarations on the
 * `companion object` with `@JvmStatic` so the JNI symbol path
 * `Java_com_kernelflux_traceharbor_traffic_TrafficPlugin_<method>` is byte-for-byte
 * identical to the original Java declaration.
 *
 * The JNI upcall `setFdStackTrace(String)` keeps `@JvmStatic @Keep` so:
 *  - it stays a `private static` method in the bytecode (matches the C side),
 *  - R8 cannot strip it.
 */
class TrafficPlugin(private val trafficConfig: TrafficConfig) : Plugin() {

    override fun start() {
        if (isPluginStarted()) {
            return
        }
        super.start()
        TraceHarborLog.i(TAG, "start")
        val ignoreSoFiles = trafficConfig.getIgnoreSoFiles()
        stackTraceFilterMode = trafficConfig.stackTraceFilterMode
        stackTraceFilterCore = trafficConfig.stackTraceFilterCore.orEmpty()
        nativeInitTraceHarborTraffic(
            trafficConfig.isRxCollectorEnable,
            trafficConfig.isTxCollectorEnable,
            trafficConfig.willDumpStackTrace(),
            trafficConfig.willDumpNativeBackTrace(),
            trafficConfig.willHookAllSoReadWrite(),
            ignoreSoFiles,
        )
    }

    override fun stop() {
        if (isPluginStopped()) {
            return
        }
        super.stop()
        nativeReleaseTraceHarborTraffic()
    }

    fun getTrafficInfoMap(type: Int): HashMap<String, String> {
        return nativeGetTrafficInfoMap(type)
    }

    fun getStackTraceByMd5(md5: String): String? {
        return hashStackTraceMap[md5]
    }

    fun getJavaStackTraceByKey(key: String): String {
        if (!trafficConfig.willDumpStackTrace()) {
            return ""
        }
        val md5 = keyHashMap[key]
        if (md5.isNullOrEmpty()) {
            return ""
        }
        return hashStackTraceMap[md5] ?: ""
    }

    fun getNativeBackTraceByKey(key: String): String {
        if (!trafficConfig.willDumpNativeBackTrace()) {
            return ""
        }
        return nativeGetNativeBackTraceByKey(key)
    }

    fun clearTrafficInfo() {
        keyHashMap.clear()
        hashStackTraceMap.clear()
        nativeClearTrafficInfo()
    }

    companion object {
        private const val TAG = "TrafficPlugin"

        const val TYPE_GET_TRAFFIC_RX = 0
        const val TYPE_GET_TRAFFIC_TX = 1
        // TODO
        // const val TYPE_GET_TRAFFIC_ALL = 2

        @JvmStatic
        private var stackTraceFilterMode: Int = 0

        @JvmStatic
        private var stackTraceFilterCore: String = ""

        @JvmStatic
        private val hashStackTraceMap: MutableMap<String, String> = ConcurrentHashMap()

        @JvmStatic
        private val keyHashMap: MutableMap<String, String> = ConcurrentHashMap()

        init {
            System.loadLibrary("traceharbor-traffic")
        }

        @JvmStatic
        private external fun nativeInitTraceHarborTraffic(
            rxEnable: Boolean,
            txEnable: Boolean,
            dumpStackTrace: Boolean,
            dumpNativeBackTrace: Boolean,
            willHookAllSoReadWrite: Boolean,
            ignoreSoFiles: Array<String>?,
        )

        @JvmStatic
        private external fun nativeGetTrafficInfo(): String

        @JvmStatic
        private external fun nativeGetAllStackTraceTrafficInfo(): String

        @JvmStatic
        private external fun nativeReleaseTraceHarborTraffic()

        @JvmStatic
        private external fun nativeClearTrafficInfo()

        @JvmStatic
        private external fun nativeGetTrafficInfoMap(type: Int): HashMap<String, String>

        @JvmStatic
        private external fun nativeGetNativeBackTraceByKey(key: String): String

        /**
         * Invoked from JNI — must remain a `private static` method on
         * `com.kernelflux.traceharbor.traffic.TrafficPlugin`.
         */
        @JvmStatic
        @Keep
        private fun setFdStackTrace(key: String) {
            val stackTrace = StringBuilder()
            val stackTraceElements = Thread.currentThread().stackTrace
            for (element in stackTraceElements) {
                val stackTraceLine = element.toString()
                val willAppend = when (stackTraceFilterMode) {
                    TrafficConfig.STACK_TRACE_FILTER_MODE_FULL -> true
                    TrafficConfig.STACK_TRACE_FILTER_MODE_STARTS_WITH ->
                        stackTraceLine.startsWith(stackTraceFilterCore)
                    TrafficConfig.STACK_TRACE_FILTER_MODE_PATTERN ->
                        stackTraceLine.matches(Regex(stackTraceFilterCore))
                    else -> false
                }
                if (willAppend) {
                    stackTrace.append(stackTraceLine).append("\n")
                }
            }
            val md5 = TraceHarborUtil.getMD5String(stackTrace.toString())
            if (!hashStackTraceMap.containsKey(md5)) {
                hashStackTraceMap[md5] = stackTrace.toString()
            }
            keyHashMap[key] = md5
        }
    }
}
