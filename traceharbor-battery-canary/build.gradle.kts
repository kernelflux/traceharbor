plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = (rootProject.extra["androidNamespaces"] as Map<*, *>)[project.path] as String
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    // AGP 8 stopped generating BuildConfig by default; this module references it.
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        @Suppress("DEPRECATION")
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        // AGP 8 dropped versionCode/versionName from LibraryDefaultConfig (app-only).

        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // testInstrumentationRunner "com.android.test.runner.MultiDexTestRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(project(":traceharbor-android-lib"))
    api(project(":traceharbor-trace-canary"))
    // MMKV 1.2.11 ships 4KB-aligned prebuilt .so files; 1.3.4+ ships 16KB-aligned ones,
    // required by Google Play (Nov 2025) and Android 16 / Pixel 8+ at runtime.
    implementation(libs.mmkv)

    api(libs.androidx.appcompat)
    api(libs.androidx.recyclerview)
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

extra["publishArtifactId"] = "traceharbor-battery-canary"
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
