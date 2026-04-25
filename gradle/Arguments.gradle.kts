// =============================================================================
// Build-time argument helpers exposed on `gradle.ext.*`.
//
// Ported from gradle/Arguments.gradle as part of Stage 5 of
// docs/plans/2026-04-23-brand-and-modernize-plan.md.
//
// Survivors:
//   gradle.enableLog()     — 9 module build.gradle scripts pass this into
//                             externalNativeBuild.cmake.arguments to flip the
//                             -DEnableLOG / -DQUT_STATISTIC_ENABLE flags.
//
// Removed (dead code, no callers anywhere in the repo as of Stage 5):
//   - gradle.enableStatistic()
//   - gradle.forceArm32()
//   - gradle.gonnaPublish()       (replaced by inline check in settings.gradle.kts)
//   - gradle.staticLinkCXX()      (replaced by inline fn in settings.gradle.kts)
//
// Wrapped as a `groovy.lang.Closure` so the `gradle.enableLog()` call sites
// in the still-Groovy module build.gradle scripts keep working unchanged.
// Once those modules are ported to KTS (Stage 6), the closure wrapper can be
// dropped in favor of a plain Kotlin lambda.
// =============================================================================
import groovy.lang.Closure

val gExtra = (gradle as org.gradle.api.plugins.ExtensionAware).extensions.extraProperties

gExtra.set("enableLog", object : Closure<Boolean>(gradle) {
    @Suppress("unused")
    fun doCall(): Boolean =
        gradle.startParameter.projectProperties.containsKey("EnableLog")
})
