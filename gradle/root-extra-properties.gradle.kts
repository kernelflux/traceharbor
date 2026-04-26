import org.gradle.api.JavaVersion

extra["minSdkVersion"] = 21
extra["targetSdkVersion"] = 34
extra["compileSdkVersion"] = 34
extra["buildToolsVersion"] = "34.0.0"
extra["androidNamespaces"] = mapOf(
    ":traceharbor-android-commons" to "com.kernelflux.traceharbor.android.commons",
    ":traceharbor-android-lib" to "com.kernelflux.traceharbor",
    ":traceharbor-trace-canary" to "com.kernelflux.traceharbor.trace",
    ":traceharbor-io-canary" to "com.kernelflux.traceharbor.iocanary",
    ":traceharbor-sqlite-lint:traceharbor-sqlite-lint-android-sdk" to "com.kernelflux.traceharbor.sqlitelint",
    ":traceharbor-battery-canary" to "com.kernelflux.traceharbor.batterycanary",
    ":traceharbor-opengl-leak" to "com.kernelflux.traceharbor.openglleak",
    ":traceharbor-traffic" to "com.kernelflux.traceharbor.traffic",
    ":traceharbor-backtrace" to "com.kernelflux.traceharbor.backtrace",
    ":traceharbor-backtrace:cxx-static" to "com.kernelflux.traceharbor.backtrace.cxxstatic",
    ":traceharbor-hooks" to "com.kernelflux.traceharbor.hook",
    ":traceharbor-hooks:cxx-static" to "com.kernelflux.traceharbor.hook.cxxstatic",
    ":traceharbor-memguard" to "com.kernelflux.traceharbor.memguard",
    ":traceharbor-mallctl" to "com.kernelflux.traceharbor.mallctl",
    ":traceharbor-fd" to "com.kernelflux.traceharbor.fd",
    ":traceharbor-memory-canary" to "com.kernelflux.traceharbor.memory.canary",
    ":traceharbor-opengl-leak:cxx-static" to "com.kernelflux.traceharbor.openglleak.cxxstatic",
    ":traceharbor-resource-canary:traceharbor-resource-canary-android" to "com.kernelflux.traceharbor.resource",
)

extra["VERSION_CODE"] = 1
extra["javaVersion"] = JavaVersion.VERSION_17

extra["GROUP"] = "com.kernelflux.mobile"

extra["VERSION_NAME"] = (project.findProperty("VERSION_NAME_BASE") as String?).orEmpty()

extra["POM_PACKAGING"] = "pom"
extra["POM_NAME"] = "TraceHarbor for Android"
extra["POM_DESCRIPTION"] =
    "TraceHarbor is an Android performance and diagnostics framework derived from Tencent Matrix and modernized for continued community development."

extra["POM_URL"] = "https://github.com/kernelflux/traceharbor"
extra["POM_SCM_URL"] = "https://github.com/kernelflux/traceharbor.git"
extra["POM_SCM_COMMIT"] = ""
extra["POM_ISSUE_URL"] = "https://github.com/kernelflux/traceharbor/issues"

extra["POM_LICENCE_NAME"] = "BSD License"
extra["POM_LICENCE_URL"] = "https://opensource.org/licenses/BSD-3-Clause"
extra["POM_LICENCE_DIST"] = "repo"

extra["POM_DEVELOPER_ID"] = "TraceHarbor Contributors"
extra["POM_DEVELOPER_NAME"] = "TraceHarbor Contributors"

extra["ABI_FILTERS"] = listOf("armeabi-v7a", "arm64-v8a")
extra["LOGGER_VERSION"] = 1.1 // fixme: logger
extra["LIFECYCLE_VERSION"] = "2.3.1"
