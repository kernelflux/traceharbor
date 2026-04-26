@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.library")
    id("kotlin-android")
}

apply(from = rootProject.file("gradle/TraceHarborNativeDepend.gradle"))

android {
    namespace = (rootProject.extra["androidNamespaces"] as Map<*, *>)[project.path] as String
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    // publishNonDefault true
    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        @Suppress("DEPRECATION")
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        // AGP 8 dropped versionCode/versionName from LibraryDefaultConfig (app-only).

        externalNativeBuild {
            cmake {
                cppFlags += "-std=gnu++11 -frtti -fexceptions"
            }
            ndk {
                //noinspection ChromeOsAbiSupport
                abiFilters += setOf("armeabi-v7a", "arm64-v8a")
            }
        }
    }

    sourceSets["main"].apply {
        jniLibs.srcDirs("libs")
        java.srcDir("src/main/java")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })

    implementation(project(":traceharbor-android-lib"))
    implementation(project(":traceharbor-android-commons"))
    implementation(project(":traceharbor-backtrace"))
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

extra["publishArtifactId"] = "traceharbor-traffic"
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
