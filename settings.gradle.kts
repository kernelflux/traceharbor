// =============================================================================
// Settings — Kotlin DSL.
//
// Ported from settings.gradle as part of Stage 5 of
// docs/plans/2026-04-23-brand-and-modernize-plan.md.
// =============================================================================
// ---------------------------------------------------------------------------
// pluginManagement: where Gradle resolves `plugins { id "..." }` blocks.
// Order matters — mavenLocal first so the freshly-published TraceHarbor plugin
// marker (`com.kernelflux.traceharbor.plugin.gradle.plugin`) wins.
//
// The TraceHarbor plugin version is loaded from gradle.properties so downstream
// modules can simply write `plugins { id("com.kernelflux.traceharbor.plugin") }`
// without repeating the version string.
//
// Note: pluginManagement {} runs in an isolated context (no settings.rootDir,
// no Settings.file() helpers), so we resolve gradle.properties via java.io.File
// built relative to settingsDir (which IS visible inside pluginManagement {}).
// ---------------------------------------------------------------------------
pluginManagement {
    val thProps = java.util.Properties().apply {
        val gp = java.io.File(settingsDir, "gradle.properties")
        if (gp.exists()) java.io.FileInputStream(gp).use { load(it) }
    }
    val thVersion = "${thProps.getProperty("VERSION_NAME_PREFIX")}${thProps.getProperty("VERSION_NAME_SUFFIX", "")}"

    repositories {
        mavenLocal()
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    plugins {
        id("com.kernelflux.traceharbor.plugin") version thVersion
        id("com.android.application")           version "8.2.2"
        id("com.android.library")               version "8.2.2"
        id("org.jetbrains.kotlin.android")      version "1.8.22"
    }
}

rootProject.name = "TraceHarbor"

apply(from = "$rootDir/gradle/Arguments.gradle.kts")

// ---------------------------------------------------------------------------
// Modules
// ---------------------------------------------------------------------------
include(":traceharbor-gradle-plugin")

include(":traceharbor-android-commons")
include(":traceharbor-android-lib")
include(":traceharbor-commons")
include(":traceharbor-resource-canary:traceharbor-resource-canary-common")
include(":traceharbor-resource-canary:traceharbor-resource-canary-analyzer")
include(":traceharbor-resource-canary:traceharbor-resource-canary-analyzer-cli")
include(":traceharbor-resource-canary:traceharbor-resource-canary-android")
include(":traceharbor-trace-canary")
include(":traceharbor-apk-canary")
include(":traceharbor-io-canary")
include(":traceharbor-sqlite-lint:traceharbor-sqlite-lint-android-sdk")
include(":traceharbor-battery-canary")
include(":traceharbor-arscutil")
include(":traceharbor-opengl-leak")
include(":traceharbor-traffic")

// memory-hook components
include(":traceharbor-backtrace")
include(":traceharbor-hooks")
include(":traceharbor-memguard")
include(":traceharbor-mallctl")
include(":traceharbor-fd")

include(":traceharbor-memory-canary")

// ---------------------------------------------------------------------------
// Sample (uses the plugin from mavenLocal). Auto-skipped during publish flows
// because the plugin marker isn't in mavenLocal yet during a release.
// ---------------------------------------------------------------------------
fun isPublishLikeTask(taskName: String): Boolean {
    val lower = taskName.lowercase()
    return lower.contains("deploytomavencentral") ||
        lower.contains("mavencentralupload") ||
        lower.contains("publishtomavencentral") ||
        lower.contains("publishplugins") ||
        lower.contains("uploadarchives") ||
        lower == "publish" || lower.endsWith(":publish") ||
        lower.startsWith("publish") ||
        lower.contains(":publish")
}

val autoSkipForPublish = gradle.startParameter.taskNames.any { isPublishLikeTask(it) }
val skipSampleByEnv = "1" == (System.getenv("TRACEHARBOR_SKIP_SAMPLE") ?: "")
val skipSampleByProp = "true".equals(
    gradle.startParameter.projectProperties["traceharborSkipSample"]?.toString().orEmpty(),
    ignoreCase = true,
)
val skipSample = skipSampleByEnv || skipSampleByProp || autoSkipForPublish
if (!skipSample) {
    include(":samples:sample-android")
}

// ---------------------------------------------------------------------------
// Optional cxx-static modules (only when -PpublishStaticLinkCXXFlavor is set).
// ---------------------------------------------------------------------------
fun staticLinkCXX(): Boolean =
    gradle.startParameter.projectProperties.containsKey("publishStaticLinkCXXFlavor")

if (staticLinkCXX()) {
    include(":traceharbor-hooks:cxx-static")
    include(":traceharbor-opengl-leak:cxx-static")
    include(":traceharbor-backtrace:cxx-static")
}
