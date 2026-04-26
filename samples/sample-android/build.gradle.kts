import org.gradle.kotlin.dsl.withGroovyBuilder

plugins {
    id("com.android.application")
    //id("com.kernelflux.traceharbor.plugin")
}

val javaVersion = rootProject.extra["javaVersion"] as JavaVersion

android {
    namespace = "com.kernelflux.traceharborsample"
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        applicationId = "com.kernelflux.traceharborsample"
        minSdk = 21
        @Suppress("DEPRECATION")
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        versionCode = 1
        versionName = rootProject.extra["VERSION_NAME"].toString()

        // traceharbor-sqlite-lint-android-sdk has a flavor dimension `mode` (full/stub) without a
        // default. Pull the `full` variant so SQLiteLint actually loads its native checker.
        missingDimensionStrategy("mode", "full")
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// ---------------------------------------------------------------------------
// `traceHarbor { trace { ... } }` is the DSL contributed by the TraceHarbor
// Gradle plugin (com.kernelflux.traceharbor.plugin). Configured via the
// dynamic `extensions` API + Groovy MOP so KTS doesn't need a generated
// accessor — keeping the plugin loose-typed avoids a circular build-time
// dep on the plugin's own jar. (`withGroovyBuilder` import sits at the top
// of this file, since KTS rejects mid-file imports.)
// ---------------------------------------------------------------------------
//extensions.findByName("traceHarbor")?.withGroovyBuilder {
//    "trace" {
//        "enable"(true)
//        setProperty(
//            "ignorePackages",
//            listOf(
//                "com.kernelflux.traceharborsample.subpkg",
//            ),
//        )
//        setProperty(
//            "ignoreClasses",
//            listOf(
//                "com.kernelflux.traceharborsample.LeakActivity",
//                "com.kernelflux.traceharborsample.LeakStore",
//            ),
//        )
//    }
//}

dependencies {
    implementation(project(":traceharbor-android-lib"))
    implementation(project(":traceharbor-trace-canary"))
    implementation(project(":traceharbor-io-canary"))
    implementation(project(":traceharbor-resource-canary:traceharbor-resource-canary-android"))
    implementation(project(":traceharbor-hooks"))
    implementation(project(":traceharbor-battery-canary"))
    implementation(project(":traceharbor-sqlite-lint:traceharbor-sqlite-lint-android-sdk"))
    implementation(project(":traceharbor-traffic"))

    implementation(libs.androidx.recyclerview)
}
