package com.kernelflux.traceharbor.batterycanary.utils

import android.content.Context
import android.os.BatteryManager
import androidx.annotation.Nullable

/**
 * Since [BatteryManager.BATTERY_PROPERTY_CURRENT_NOW] will return microAmp/millisAmp &
 * positive/negative value in difference devices, here we figure out the unit somehow.
 *
 * @author Kaede
 * @since 9/9/2022
 */
object BatteryCurrencyInspector {
    /**
     * To get a high currency value, you are supposed to call this API within a high cpu-load
     * activity & without charging. (When the device's cpu-load is very low, we can not tell
     * microAmp or millisAmp.)
     */
    @Nullable
    @JvmStatic
    fun isMicroAmpCurr(context: Context, threshold: Int): Boolean? {
        if (BatteryCanaryUtil.isDeviceCharging(context)) {
            // Currency might be very low in charge
            return null
        }
        val valNow = BatteryCanaryUtil.getBatteryCurrencyImmediately(context)
        if (valNow == -1L) {
            return null
        }
        return isMicroAmp(valNow, threshold)
    }

    @Nullable
    @JvmStatic
    fun isMicroAmpCurr(context: Context): Boolean? = isMicroAmpCurr(context, 1000)

    @JvmStatic
    fun isMicroAmp(amp: Long, threshold: Int): Boolean = amp > threshold

    @Nullable
    @JvmStatic
    fun isPositiveInChargeCurr(context: Context): Boolean? {
        if (!BatteryCanaryUtil.isDeviceCharging(context)) {
            return null
        }
        val valNow = BatteryCanaryUtil.getBatteryCurrencyImmediately(context)
        if (valNow == -1L) {
            return null
        }
        return valNow > 0
    }

    @Nullable
    @JvmStatic
    fun isPositiveOutOfChargeCurr(context: Context): Boolean? {
        if (BatteryCanaryUtil.isDeviceCharging(context)) {
            return null
        }
        val valNow = BatteryCanaryUtil.getBatteryCurrencyImmediately(context)
        if (valNow == -1L) {
            return null
        }
        return valNow > 0
    }
}

