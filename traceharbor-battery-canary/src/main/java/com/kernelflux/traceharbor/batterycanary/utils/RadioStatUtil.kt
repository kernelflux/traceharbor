package com.kernelflux.traceharbor.batterycanary.utils

import android.Manifest
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.core.util.Pair
import com.kernelflux.traceharbor.batterycanary.BuildConfig
import com.kernelflux.traceharbor.util.TraceHarborLog

/**
 * @author Kaede
 * @since 2020/12/8
 */
@Suppress("SpellCheckingInspection")
@RestrictTo(RestrictTo.Scope.LIBRARY)
object RadioStatUtil {
    @JvmField
    val MIN_QUERY_INTERVAL: Long = if (BuildConfig.DEBUG) 0L else 2000L

    @JvmField
    var sLastQueryMillis: Long = 0

    @JvmField
    var sLastRef: RadioStat? = null

    private fun checkIfFrequently(): Boolean {
        val currentTimeMillis = System.currentTimeMillis()
        if (currentTimeMillis - sLastQueryMillis < MIN_QUERY_INTERVAL) {
            return true
        }
        sLastQueryMillis = currentTimeMillis
        return false
    }

    @Nullable
    @JvmStatic
    fun getCurrentStat(context: Context): RadioStat? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null
        }

        // if (checkIfFrequently()) {
        //     return sLastRef;
        // }

        return try {
            val network = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager?
            if (network == null) {
                return null
            }
            val stat = RadioStat()
            network.querySummary(NetworkCapabilities.TRANSPORT_WIFI, null, 0, System.currentTimeMillis()).use { stats ->
                while (stats.hasNextBucket()) {
                    val bucket = NetworkStats.Bucket()
                    if (stats.getNextBucket(bucket)) {
                        if (bucket.uid == android.os.Process.myUid()) {
                            stat.wifiRxBytes += bucket.rxBytes
                            stat.wifiTxBytes += bucket.txBytes
                            stat.wifiRxPackets += bucket.rxPackets
                            stat.wifiTxPackets += bucket.txPackets
                        }
                    }
                }
            }
            network.querySummary(NetworkCapabilities.TRANSPORT_CELLULAR, null, 0, System.currentTimeMillis()).use { stats ->
                while (stats.hasNextBucket()) {
                    val bucket = NetworkStats.Bucket()
                    if (stats.getNextBucket(bucket)) {
                        if (bucket.uid == android.os.Process.myUid()) {
                            stat.mobileRxBytes += bucket.rxBytes
                            stat.mobileTxBytes += bucket.txBytes
                            stat.mobileRxPackets += bucket.rxPackets
                            stat.mobileTxPackets += bucket.txPackets
                        }
                    }
                }
            }
            sLastRef = stat
            stat
        } catch (e: Throwable) {
            TraceHarborLog.w(TAG, "querySummary fail: " + e.message)
            sLastRef = null
            null
        }
    }

    @Nullable
    @JvmStatic
    fun getCurrentBps(context: Context): RadioBps? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null
        }
        return try {
            val stat = RadioBps()
            val wifi = getCurrentBps(context, "WIFI")
            stat.wifiRxBps = wifi.first ?: 0
            stat.wifiTxBps = wifi.second ?: 0

            val mobile = getCurrentBps(context, "MOBILE")
            stat.mobileRxBps = mobile.first ?: 0
            stat.mobileTxBps = mobile.second ?: 0
            stat
        } catch (e: Exception) {
            TraceHarborLog.w(TAG, "getBps err: " + e.message)
            null
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun getCurrentBps(context: Context, typeName: String): Pair<Long, Long> {
        var rxBwBps = 0L
        var txBwBps = 0L
        if (context.checkCallingOrSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED) {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (item in manager.allNetworks) {
                val networkInfo: NetworkInfo? = manager.getNetworkInfo(item)
                if (networkInfo != null &&
                    (networkInfo.isConnected || networkInfo.isConnectedOrConnecting) &&
                    networkInfo.typeName.equals(typeName, ignoreCase = true)
                ) {
                    val capabilities = manager.getNetworkCapabilities(item)
                    if (capabilities != null) {
                        rxBwBps = capabilities.linkDownstreamBandwidthKbps * 1024L
                        txBwBps = capabilities.linkUpstreamBandwidthKbps * 1024L
                        if (rxBwBps > 0 || txBwBps > 0) {
                            break
                        }
                    }
                }
            }
        }
        return Pair(rxBwBps, txBwBps)
    }

    class RadioStat {
        @JvmField
        var wifiRxBytes: Long = 0

        @JvmField
        var wifiTxBytes: Long = 0

        @JvmField
        var wifiRxPackets: Long = 0

        @JvmField
        var wifiTxPackets: Long = 0

        @JvmField
        var mobileRxBytes: Long = 0

        @JvmField
        var mobileTxBytes: Long = 0

        @JvmField
        var mobileRxPackets: Long = 0

        @JvmField
        var mobileTxPackets: Long = 0
    }

    class RadioBps {
        @JvmField
        var wifiRxBps: Long = 0

        @JvmField
        var wifiTxBps: Long = 0

        @JvmField
        var mobileRxBps: Long = 0

        @JvmField
        var mobileTxBps: Long = 0
    }

    private const val TAG = "TraceHarbor.battery.ProcStatUtil"
}

