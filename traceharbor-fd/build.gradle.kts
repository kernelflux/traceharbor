@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.library")
    // kotlin-android applied so this module can host Kotlin sources
    // (kept the JNI bridge as Java; only test stubs are Kotlin so far).
    id("kotlin-android")
}

@Suppress("UNCHECKED_CAST")
val abiFiltersList = (rootProject.extra["ABI_FILTERS"] as List<String>)

android {
    namespace = (rootProject.extra["androidNamespaces"] as Map<*, *>)[project.path] as String
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        @Suppress("DEPRECATION")
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        // AGP 8 dropped versionCode/versionName from LibraryDefaultConfig (app-only).

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += abiFiltersList
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

dependencies {
    implementation(fileTree("libs") { include("*.jar") })

    implementation(libs.androidx.annotation)
    // implementation "com.tencent.stubs:logger:${rootProject.LOGGER_VERSION}"
    implementation(project(":traceharbor-android-lib"))
    implementation(project(":traceharbor-android-commons"))
}

group   = rootProject.extra["GROUP"].toString()
version = rootProject.extra["VERSION_NAME"].toString()

extra["publishArtifactId"] = "traceharbor-fd"
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
