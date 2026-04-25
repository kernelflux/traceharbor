package com.kernelflux.traceharbor.traffic

class TrafficConfig {

    var isRxCollectorEnable: Boolean = false
    var isTxCollectorEnable: Boolean = false
    private var dumpStackTraceEnable: Boolean = false
    private var dumpNativeBackTraceEnable: Boolean = false
    var isHookAllSoReadWrite: Boolean = true
    // TODO
    // private var lookupIpAddressEnable: Boolean = false

    var stackTraceFilterMode: Int = STACK_TRACE_FILTER_MODE_FULL
        private set

    var stackTraceFilterCore: String? = null
        private set

    private val ignoreSoList: MutableList<String> = ArrayList()

    constructor()

    constructor(
        rxCollectorEnable: Boolean,
        txCollectorEnable: Boolean,
        dumpStackTraceEnable: Boolean,
    ) {
        this.isRxCollectorEnable = rxCollectorEnable
        this.isTxCollectorEnable = txCollectorEnable
        this.dumpStackTraceEnable = dumpStackTraceEnable
        this.dumpNativeBackTraceEnable = false
    }

    constructor(
        rxCollectorEnable: Boolean,
        txCollectorEnable: Boolean,
        dumpStackTraceEnable: Boolean,
        dumpNativeBackTraceEnable: Boolean,
    ) {
        this.isRxCollectorEnable = rxCollectorEnable
        this.isTxCollectorEnable = txCollectorEnable
        this.dumpStackTraceEnable = dumpStackTraceEnable
        this.dumpNativeBackTraceEnable = dumpNativeBackTraceEnable
    }

    // Java callers used boolean methods named willDumpStackTrace() / willDumpNativeBackTrace() /
    // willHookAllSoReadWrite(); preserve those exact names via @JvmName so JNI callers (and any
    // existing reflective callers) keep linking.
    @JvmName("willDumpStackTrace")
    fun willDumpStackTrace(): Boolean = dumpStackTraceEnable

    fun setDumpStackTraceEnable(value: Boolean) {
        this.dumpStackTraceEnable = value
    }

    @JvmName("willDumpNativeBackTrace")
    fun willDumpNativeBackTrace(): Boolean = dumpNativeBackTraceEnable

    fun setDumpNativeBackTrace(value: Boolean) {
        this.dumpNativeBackTraceEnable = value
    }

    @JvmName("willHookAllSoReadWrite")
    fun willHookAllSoReadWrite(): Boolean = isHookAllSoReadWrite

    fun addIgnoreSoFile(soName: String) {
        ignoreSoList.add(soName)
    }

    fun getIgnoreSoFiles(): Array<String> = ignoreSoList.toTypedArray()

    fun setStackTraceFilterMode(mode: Int, filterCore: String?) {
        this.stackTraceFilterMode = mode
        this.stackTraceFilterCore = filterCore
    }

    companion object {
        const val STACK_TRACE_FILTER_MODE_FULL: Int = 0
        const val STACK_TRACE_FILTER_MODE_STARTS_WITH: Int = 1
        const val STACK_TRACE_FILTER_MODE_PATTERN: Int = 2
    }
}
