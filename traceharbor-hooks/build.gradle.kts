@file:Suppress("UnstableApiUsage")

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

    // AGP 8 stopped generating BuildConfig by default; HookManager references it.
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        @Suppress("DEPRECATION")
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        // AGP 8 dropped versionCode/versionName from LibraryDefaultConfig (app-only).

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += abiFiltersList
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DEnableLOG=${if (enableLog) "ON" else "OFF"}",
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
    "from"("src/main/cpp/common") {
        "include"("**/*.h")
        "include"("*.h")
    }
    "from"("src/main/cpp/external/fastunwind") {
        "include"("**/*.h")
    }
}

apply(from = project.file("dependencies.gradle"))

group   = rootProject.extra["GROUP"].toString()
version = rootProject.extra["VERSION_NAME"].toString()

extra["publishArtifactId"] = "traceharbor-hooks"
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
