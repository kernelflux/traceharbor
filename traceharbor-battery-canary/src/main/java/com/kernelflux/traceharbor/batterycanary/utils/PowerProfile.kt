package com.kernelflux.traceharbor.batterycanary.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.XmlResourceParser
import android.os.Build
import android.system.Os
import androidx.annotation.Nullable
import androidx.annotation.RestrictTo
import androidx.annotation.StringDef
import androidx.annotation.VisibleForTesting
import com.kernelflux.traceharbor.util.TraceHarborLog
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.Callable
import kotlin.math.roundToInt

/**
 * @see com.android.internal.os.PowerProfile
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@Suppress("MemberVisibilityCanBePrivate")
class PowerProfile constructor(context: Context) {
    @StringDef(
        value = [
            "unknown",
            "framework",
            "custom",
            "test",
        ],
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class ResType

    init {
        // Read the XML file for the given profile (normally only one per device).
        synchronized(sLock) {
            if (sPowerItemMap.isEmpty() && sPowerArrayMap.isEmpty()) {
                try {
                    readPowerValuesCompat(context)
                } catch (e: IOException) {
                    TraceHarborLog.w(TAG, "Failed to read power values: $e")
                }
            }
            initCpuClusters()
        }
    }

    fun smoke(): PowerProfile {
        if (getNumCpuClusters() <= 0) {
            throw IOException("Invalid cpu clusters: " + getNumCpuClusters())
        }
        for (i in 0 until getNumCpuClusters()) {
            if (getNumSpeedStepsInCpuCluster(i) <= 0) {
                throw IOException(
                    "Invalid cpu cluster speed-steps: cluster = " + i +
                        ", steps = " + getNumSpeedStepsInCpuCluster(i),
                )
            }
        }
        val cpuCoreNum = BatteryCanaryUtil.getCpuCoreNum()
        val cpuCoreNumInProfile = this.cpuCoreNum
        if (cpuCoreNum != cpuCoreNumInProfile) {
            throw IOException(
                "Unmatched cpu core num, sys = " + cpuCoreNum +
                    ", profile = " + cpuCoreNumInProfile,
            )
        }
        return this
    }

    val isSupported: Boolean
        get() {
            return try {
                smoke()
                true
            } catch (ignored: IOException) {
                false
            }
        }

    fun getAveragePowerUni(type: String): Double {
        val num = getNumElements(type)
        return if (num > 0) {
            var sum = 0.0
            for (i in 0 until num) {
                sum += getAveragePower(type, i)
            }
            sum / num
        } else {
            getAveragePower(type)
        }
    }

    val cpuCoreNum: Int
        get() {
            var cpuCoreNumInProfile = 0
            for (i in 0 until getNumCpuClusters()) {
                cpuCoreNumInProfile += getNumCoresInCpuCluster(i)
            }
            return cpuCoreNumInProfile
        }

    fun getClusterByCpuNum(cpuCoreNum: Int): Int {
        if (cpuCoreNum < 0) {
            return -1
        }
        var delta = 0
        for (i in mCpuClusters.indices) {
            val cpuCluster = mCpuClusters[i]
            if (cpuCluster.numCpus + delta >= cpuCoreNum + 1) {
                return i
            }
            delta += cpuCluster.numCpus
        }
        return -2
    }

    private fun readPowerValuesCompat(context: Context) {
        var exception: Exception? = null
        try {
            readPowerValuesFromRes(context, "power_profile")
            initCpuClusters()
            smoke()
            mResType = "framework"
        } catch (e: Exception) {
            TraceHarborLog.w(TAG, "read from framework failed: $e")
            clear()
            exception = e
        }

        if (exception != null) {
            val findBlock = object : Callable<File> {
                @Throws(FileNotFoundException::class)
                override fun call(): File {
                    val targetFileName = "/xml/power_profile.xml"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val customDirs = Os.getenv("CUST_POLICY_DIRS")
                        for (dir in customDirs.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }) {
                            val file = File(dir, targetFileName)
                            if (file.exists() && file.canRead()) {
                                TraceHarborLog.i(TAG, "find profile xml: $file")
                                return file
                            }
                        }
                    }
                    throw FileNotFoundException(targetFileName)
                }
            }
            try {
                exception = null
                readPowerValuesFromFilePath(context, findBlock.call())
                initCpuClusters()
                smoke()
                mResType = "custom"
            } catch (e: Exception) {
                TraceHarborLog.w(TAG, "read from custom failed: $e")
                clear()
                exception = e
            }
        }

        if (exception != null) {
            try {
                exception = null
                readPowerValuesFromRes(context, "power_profile_test")
                initCpuClusters()
                smoke()
                mResType = "test"
            } catch (e: Exception) {
                TraceHarborLog.w(TAG, "read from test failed: $e")
                clear()
                exception = e
            }
        }

        if (exception != null) {
            throw IOException("readPowerValuesCompat failed", exception)
        }
    }

    @SuppressLint("DiscouragedApi")
    @VisibleForTesting
    fun readPowerValuesFromRes(context: Context, fileName: String) {
        var parser: XmlResourceParser? = null
        try {
            val id = context.resources.getIdentifier(fileName, "xml", "android")
            parser = context.resources.getXml(id)
            readPowerValuesFromXml(context, parser)
        } catch (e: Exception) {
            throw RuntimeException("Error reading res " + fileName + ": " + e.message, e)
        } finally {
            if (parser != null) {
                try {
                    parser.close()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    @VisibleForTesting
    fun readPowerValuesFromFilePath(context: Context, xmlFile: File) {
        var inputStream: FileInputStream? = null
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            inputStream = FileInputStream(xmlFile)
            parser.setInput(inputStream, null)
            readPowerValuesFromXml(context, parser)
        } catch (e: Exception) {
            throw RuntimeException("Error reading file " + xmlFile + ": " + e.message, e)
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close()
                } catch (ignored: IOException) {
                }
            }
        }
    }

    @VisibleForTesting
    fun clear() {
        sPowerItemMap.clear()
        sPowerArrayMap.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun readPowerValuesFromXml(context: Context, parser: XmlPullParser) {
        var parsingArray = false
        val array = ArrayList<Double>()
        var arrayName: String? = null

        try {
            XmlUtils.beginDocument(parser, TAG_DEVICE)

            while (true) {
                XmlUtils.nextElement(parser)

                val element = parser.name ?: break

                if (parsingArray && element != TAG_ARRAYITEM) {
                    sPowerArrayMap[arrayName] = array.toTypedArray()
                    parsingArray = false
                }
                if (element == TAG_ARRAY) {
                    parsingArray = true
                    array.clear()
                    arrayName = parser.getAttributeValue(null, ATTR_NAME)
                } else if (element == TAG_ITEM || element == TAG_ARRAYITEM) {
                    var name: String? = null
                    if (!parsingArray) {
                        name = parser.getAttributeValue(null, ATTR_NAME)
                    }
                    if (parser.next() == XmlPullParser.TEXT) {
                        val power = parser.text
                        var value = 0.0
                        try {
                            value = java.lang.Double.valueOf(power)
                        } catch (ignored: NumberFormatException) {
                        }
                        if (element == TAG_ITEM) {
                            sPowerItemMap[name] = value
                        } else if (parsingArray) {
                            array.add(value)
                        }
                    }
                }
            }
            if (parsingArray) {
                sPowerArrayMap[arrayName] = array.toTypedArray()
            }
        } catch (e: XmlPullParserException) {
            throw RuntimeException(e)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        val configResIds = intArrayOf(
            context.resources.getIdentifier("config_bluetooth_idle_cur_ma", "integer", "android"),
            context.resources.getIdentifier("config_bluetooth_rx_cur_ma", "integer", "android"),
            context.resources.getIdentifier("config_bluetooth_tx_cur_ma", "integer", "android"),
            context.resources.getIdentifier("config_bluetooth_operating_voltage_mv", "integer", "android"),
        )

        val configResIdKeys = arrayOf(
            POWER_BLUETOOTH_CONTROLLER_IDLE,
            POWER_BLUETOOTH_CONTROLLER_RX,
            POWER_BLUETOOTH_CONTROLLER_TX,
            POWER_BLUETOOTH_CONTROLLER_OPERATING_VOLTAGE,
        )

        for (i in configResIds.indices) {
            val key = configResIdKeys[i]
            if (sPowerItemMap.containsKey(key) && sPowerItemMap[key]!! > 0) {
                continue
            }
            val value = context.resources.getInteger(configResIds[i])
            if (value > 0) {
                sPowerItemMap[key] = value.toDouble()
            }
        }
    }

    private var mCpuClusters: Array<CpuClusterKey> = emptyArray()

    fun initCpuClusters() {
        if (sPowerArrayMap.containsKey(CPU_PER_CLUSTER_CORE_COUNT)) {
            val data = sPowerArrayMap[CPU_PER_CLUSTER_CORE_COUNT]!!
            mCpuClusters = Array(data.size) { cluster ->
                val numCpusInCluster = data[cluster].roundToInt()
                CpuClusterKey(
                    CPU_CORE_SPEED_PREFIX + cluster,
                    CPU_CLUSTER_POWER_COUNT + cluster,
                    CPU_CORE_POWER_PREFIX + cluster,
                    numCpusInCluster,
                )
            }
        } else {
            var numCpus = 1
            if (sPowerItemMap.containsKey(CPU_PER_CLUSTER_CORE_COUNT)) {
                numCpus = Math.round(sPowerItemMap[CPU_PER_CLUSTER_CORE_COUNT]!!).toInt()
            }
            mCpuClusters = arrayOf(
                CpuClusterKey(
                    CPU_CORE_SPEED_PREFIX + 0,
                    CPU_CLUSTER_POWER_COUNT + 0,
                    CPU_CORE_POWER_PREFIX + 0,
                    numCpus,
                ),
            )
        }
    }

    class CpuClusterKey internal constructor(
        internal val freqKey: String,
        internal val clusterPowerKey: String,
        internal val corePowerKey: String,
        internal val numCpus: Int,
    )

    fun getNumCpuClusters(): Int = mCpuClusters.size

    fun getNumCoresInCpuCluster(cluster: Int): Int = mCpuClusters[cluster].numCpus

    fun getNumSpeedStepsInCpuCluster(cluster: Int): Int {
        if (cluster < 0 || cluster >= mCpuClusters.size) {
            return 0
        }
        if (sPowerArrayMap.containsKey(mCpuClusters[cluster].freqKey)) {
            return sPowerArrayMap[mCpuClusters[cluster].freqKey]!!.size
        }
        return 1
    }

    fun getAveragePowerForCpuCluster(cluster: Int): Double {
        if (cluster >= 0 && cluster < mCpuClusters.size) {
            return getAveragePower(mCpuClusters[cluster].clusterPowerKey)
        }
        return 0.0
    }

    fun getAveragePowerForCpuCore(cluster: Int, step: Int): Double {
        if (cluster >= 0 && cluster < mCpuClusters.size) {
            return getAveragePower(mCpuClusters[cluster].corePowerKey, step)
        }
        return 0.0
    }

    fun getNumElements(key: String): Int {
        return if (sPowerItemMap.containsKey(key)) {
            1
        } else if (sPowerArrayMap.containsKey(key)) {
            sPowerArrayMap[key]!!.size
        } else {
            0
        }
    }

    fun getAveragePowerOrDefault(type: String, defaultValue: Double): Double {
        return if (sPowerItemMap.containsKey(type)) {
            sPowerItemMap[type]!!
        } else if (sPowerArrayMap.containsKey(type)) {
            sPowerArrayMap[type]!![0]
        } else {
            defaultValue
        }
    }

    fun getAveragePower(type: String): Double = getAveragePowerOrDefault(type, 0.0)

    fun getAveragePower(type: String, level: Int): Double {
        return if (sPowerItemMap.containsKey(type)) {
            sPowerItemMap[type]!!
        } else if (sPowerArrayMap.containsKey(type)) {
            val values = sPowerArrayMap[type]!!
            if (values.size > level && level >= 0) {
                values[level]
            } else if (level < 0 || values.isEmpty()) {
                0.0
            } else {
                values[values.size - 1]
            }
        } else {
            0.0
        }
    }

    val batteryCapacity: Double
        get() = getAveragePower(POWER_BATTERY_CAPACITY)

    object XmlUtils {
        @JvmStatic
        @Throws(XmlPullParserException::class, IOException::class)
        fun beginDocument(parser: XmlPullParser, firstElementName: String) {
            var type: Int
            do {
                type = parser.next()
            } while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT)

            if (type != XmlPullParser.START_TAG) {
                throw XmlPullParserException("No start tag found")
            }

            if (parser.name != firstElementName) {
                throw XmlPullParserException(
                    "Unexpected start tag: found " + parser.name +
                        ", expected " + firstElementName,
                )
            }
        }

        @JvmStatic
        @Throws(XmlPullParserException::class, IOException::class)
        fun nextElement(parser: XmlPullParser) {
            var type: Int
            do {
                type = parser.next()
            } while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT)
        }
    }

    companion object {
        private const val TAG = "PowerProfile"

        private val sLock = Any()

        @JvmField
        var sInstance: PowerProfile? = null

        @JvmStatic
        fun getInstance(): PowerProfile? = sInstance

        @JvmStatic
        @Throws(IOException::class)
        fun init(context: Context): PowerProfile {
            synchronized(sLock) {
                try {
                    if (sInstance == null) {
                        sInstance = PowerProfile(context).smoke()
                    }
                    return sInstance!!
                } catch (e: Throwable) {
                    throw IOException("Compat err: " + e.message, e)
                }
            }
        }

        const val POWER_CPU_SUSPEND: String = "cpu.suspend"
        const val POWER_CPU_IDLE: String = "cpu.idle"
        const val POWER_CPU_ACTIVE: String = "cpu.active"
        const val POWER_WIFI_SCAN: String = "wifi.scan"
        const val POWER_WIFI_ON: String = "wifi.on"
        const val POWER_WIFI_ACTIVE: String = "wifi.active"
        const val POWER_WIFI_CONTROLLER_IDLE: String = "wifi.controller.idle"
        const val POWER_WIFI_CONTROLLER_RX: String = "wifi.controller.rx"
        const val POWER_WIFI_CONTROLLER_TX: String = "wifi.controller.tx"
        const val POWER_WIFI_CONTROLLER_TX_LEVELS: String = "wifi.controller.tx_levels"
        const val POWER_WIFI_CONTROLLER_OPERATING_VOLTAGE: String = "wifi.controller.voltage"
        const val POWER_BLUETOOTH_CONTROLLER_IDLE: String = "bluetooth.controller.idle"
        const val POWER_BLUETOOTH_CONTROLLER_RX: String = "bluetooth.controller.rx"
        const val POWER_BLUETOOTH_CONTROLLER_TX: String = "bluetooth.controller.tx"
        const val POWER_BLUETOOTH_CONTROLLER_OPERATING_VOLTAGE: String = "bluetooth.controller.voltage"
        const val POWER_MODEM_CONTROLLER_SLEEP: String = "modem.controller.sleep"
        const val POWER_MODEM_CONTROLLER_IDLE: String = "modem.controller.idle"
        const val POWER_MODEM_CONTROLLER_RX: String = "modem.controller.rx"
        const val POWER_MODEM_CONTROLLER_TX: String = "modem.controller.tx"
        const val POWER_MODEM_CONTROLLER_OPERATING_VOLTAGE: String = "modem.controller.voltage"
        const val POWER_GPS_ON: String = "gps.on"
        const val POWER_GPS_SIGNAL_QUALITY_BASED: String = "gps.signalqualitybased"
        const val POWER_GPS_OPERATING_VOLTAGE: String = "gps.voltage"

        @Deprecated("")
        const val POWER_BLUETOOTH_ON: String = "bluetooth.on"

        @Deprecated("")
        const val POWER_BLUETOOTH_ACTIVE: String = "bluetooth.active"

        @Deprecated("")
        const val POWER_BLUETOOTH_AT_CMD: String = "bluetooth.at"

        const val POWER_AMBIENT_DISPLAY: String = "ambient.on"
        const val POWER_SCREEN_ON: String = "screen.on"
        const val POWER_RADIO_ON: String = "radio.on"
        const val POWER_RADIO_SCANNING: String = "radio.scanning"
        const val POWER_RADIO_ACTIVE: String = "radio.active"
        const val POWER_SCREEN_FULL: String = "screen.full"
        const val POWER_AUDIO: String = "audio"
        const val POWER_AUDIO_DSP: String = "dsp.audio"
        const val POWER_VIDEO: String = "video"
        const val POWER_VIDEO_DSP: String = "dsp.video"
        const val POWER_FLASHLIGHT: String = "camera.flashlight"
        const val POWER_MEMORY: String = "memory.bandwidths"
        const val POWER_CAMERA: String = "camera.avg"
        const val POWER_WIFI_BATCHED_SCAN: String = "wifi.batchedscan"
        const val POWER_BATTERY_CAPACITY: String = "battery.capacity"

        @JvmField
        val sPowerItemMap: HashMap<String?, Double> = HashMap()

        @JvmField
        val sPowerArrayMap: HashMap<String?, Array<Double>> = HashMap()

        private const val TAG_DEVICE = "device"
        private const val TAG_ITEM = "item"
        private const val TAG_ARRAY = "array"
        private const val TAG_ARRAYITEM = "value"
        private const val ATTR_NAME = "name"

        private var mResType = "unknown"

        @JvmStatic
        @VisibleForTesting
        fun getPowerItemMap(): HashMap<String?, Double> = sPowerItemMap

        @JvmStatic
        @VisibleForTesting
        fun getPowerArrayMap(): HashMap<String?, Array<Double>> = sPowerArrayMap

        @JvmStatic
        @ResType
        fun getResType(): String = mResType

        private const val CPU_PER_CLUSTER_CORE_COUNT = "cpu.clusters.cores"
        private const val CPU_CLUSTER_POWER_COUNT = "cpu.cluster_power.cluster"
        private const val CPU_CORE_SPEED_PREFIX = "cpu.core_speeds.cluster"
        private const val CPU_CORE_POWER_PREFIX = "cpu.core_power.cluster"
    }
}
