package com.kernelflux.traceharbor.trace.listeners

interface IDefaultConfig {

    fun isAppMethodBeatEnable(): Boolean

    fun isFPSEnable(): Boolean

    fun isEvilMethodTraceEnable(): Boolean

    fun isAnrTraceEnable(): Boolean

    fun isIdleHandlerTraceEnable(): Boolean

    fun isTouchEventTraceEnable(): Boolean

    fun isSignalAnrTraceEnable(): Boolean

    fun isDebug(): Boolean

    fun isDevEnv(): Boolean

    fun getLooperPrinterStackStyle(): Int

    fun getAnrTraceFilePath(): String?

    fun getPrintTraceFilePath(): String?

    fun isHistoryMsgRecorderEnable(): Boolean

    fun isDenseMsgTracerEnable(): Boolean
}
