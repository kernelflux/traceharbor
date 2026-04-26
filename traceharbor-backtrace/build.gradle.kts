import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.withGroovyBuilder

plugins {
    id("com.android.library")
    id("kotlin-android")
}

apply(from = rootProject.file("gradle/TraceHarborNativeDepend.gradle"))

@Suppress("UNCHECKED_CAST")
val abiFiltersList = (rootProject.extra["ABI_FILTERS"] as List<String>)

val enableLog: Boolean = gradle.startParameter.projectProperties.containsKey("EnableLog")

android {
    namespace = (rootProject.extra["androidNamespaces"] as Map<*, *>)[project.path] as String
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["MIN_SDK_VERSION_FOR_HOOK"] as Int
        @Suppress("DEPRECATION")
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        // AGP 8 dropped versionCode/versionName from LibraryDefaultConfig (app-only).

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += abiFiltersList
        }

        externalNativeBuild {
            cmake {
                targets += setOf("traceharbor-backtrace", "unwindstack")
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DEnableLOG=${if (enableLog) "ON" else "OFF"}",
                    "-DQUT_STATISTIC_ENABLE=${if (enableLog) "ON" else "OFF"}",
                )
            }
            // exportHeaders configured below the android { } block.
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
    }
}

val exportHeadersExt = (android.defaultConfig.externalNativeBuild as ExtensionAware)
    .extensions
    .findByName("exportHeaders")
    ?: error("exportHeaders extension was not registered — did TraceHarborNativeDepend.gradle apply correctly?")

exportHeadersExt.withGroovyBuilder {
    "from"("src/main/cpp") {
        "include"("common/*.h")
        "moveToDir"("backtrace")
    }
    "from"("src/main/cpp/libtraceharbor-backtrace/include") {
        "include"("**/*.h")
        "moveToDir"("backtrace")
    }
    "from"("src/main/cpp/external/libunwindstack/include") {
        "include"("**/*.h")
        "moveToDir"("backtrace")
    }
    "from"("src/main/cpp/dexfile/include") {
        "include"("**/*.h")
        "moveToDir"("backtrace")
    }
}

apply(from = project.file("dependencies.gradle"))

group   = rootProject.extra["GROUP"].toString()
version = rootProject.extra["VERSION_NAME"].toString()

extra["publishArtifactId"] = project.property("POM_ARTIFACT_ID").toString()
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
