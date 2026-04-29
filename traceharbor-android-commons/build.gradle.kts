@file:Suppress("UnstableApiUsage")

import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.withGroovyBuilder

plugins {
    id("com.android.library")
    id("kotlin-android")
}

apply(from = rootProject.file("gradle/TraceHarborNativeDepend.gradle"))

val enableLog: Boolean = gradle.startParameter.projectProperties.containsKey("EnableLog")

android {
    namespace = (rootProject.extra["androidNamespaces"] as Map<*, *>)[project.path] as String
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int

        ndk {
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        externalNativeBuild {
            cmake {
                targets += setOf("xhook", "semi_dlfcn", "enhance_dlsym")
                arguments += "-DEnableLOG=${if (enableLog) "ON" else "OFF"}"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    sourceSets["main"].apply {
        jniLibs.srcDirs("libs")
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
    "from"("src/main/cpp/libxhook") {
        "include"("**/*.h")
    }
    "from"("src/main/cpp/libsemi_dlfcn") {
        "include"("**/*.h")
    }
    "from"("src/main/cpp/libenhance_dlsym") {
        "include"("**/*.h")
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(project(":traceharbor-android-lib"))
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

extra["publishArtifactId"] = "traceharbor-android-commons"
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
