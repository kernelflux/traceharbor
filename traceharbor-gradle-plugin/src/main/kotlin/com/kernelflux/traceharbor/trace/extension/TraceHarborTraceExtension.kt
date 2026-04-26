package com.kernelflux.traceharbor.trace.extension

open class TraceHarborTraceExtension {
    var isTransformInjectionForced: Boolean = false
    var baseMethodMapFile: String? = null
    var blackListFile: String? = null
    var customDexTransformName: String? = null
    var isSkipCheckClass: Boolean = true // skip by default
    var isEnable: Boolean = false

    /**
     * Inline equivalent of `-keeppackage` entries. Each value is a package prefix; classes
     * matching the prefix are skipped during instrumentation. Trailing `.**`, `.*`
     * or `.` are accepted for readability and stripped before matching.
     *
     * Combined with `blackListFile` — both sources contribute to the same block set.
     */
    var ignorePackages: MutableList<String> = ArrayList()

    /**
     * Inline equivalent of `-keepclass` entries. Each value is a fully-qualified class name
     * (inner classes via `$`); only that exact class is skipped.
     */
    var ignoreClasses: MutableList<String> = ArrayList()

    /** Convenience for `ignorePackage 'com.acme.foo'` style DSL calls. */
    fun ignorePackage(pkg: String?) {
        if (!pkg.isNullOrEmpty()) {
            ignorePackages.add(pkg)
        }
    }

    /** Convenience for `ignoreClass 'com.acme.foo.Bar'` style DSL calls. */
    fun ignoreClass(cls: String?) {
        if (!cls.isNullOrEmpty()) {
            ignoreClasses.add(cls)
        }
    }
}

