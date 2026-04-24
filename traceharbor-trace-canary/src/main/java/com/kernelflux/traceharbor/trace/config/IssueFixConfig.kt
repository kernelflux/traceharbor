package com.kernelflux.traceharbor.trace.config

/**
 * Singleton — original Java exposed access via `IssueFixConfig.getsInstance()`.
 *
 * We model it as a Kotlin `object` (auto-generated `INSTANCE` field), and add
 * a `@JvmStatic getsInstance()` shim on the companion-style accessor pattern
 * by mapping it to the singleton. Because Kotlin's `object` already plays the
 * singleton role, `getsInstance()` simply returns `this`.
 *
 * Note: the original method name `getsInstance` (with the leading lowercase
 * `s`) is intentionally preserved for ABI compatibility with the existing Java
 * call sites in `ActivityThreadHacker.java`.
 */
object IssueFixConfig {

    /**
     * Backed by the auto-generated `boolean isEnableFixSpApply()` /
     * `void setEnableFixSpApply(boolean)` JavaBean accessors — Kotlin's `is`
     * naming convention preserves the original Java getter name verbatim.
     */
    @JvmStatic
    var isEnableFixSpApply: Boolean = false

    @JvmStatic
    fun getsInstance(): IssueFixConfig = this
}
