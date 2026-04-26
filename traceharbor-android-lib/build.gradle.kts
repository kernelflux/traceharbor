plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = (rootProject.extra["androidNamespaces"] as Map<*, *>)[project.path] as String
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    useLibrary("android.test.base")

    buildFeatures {
        aidl = true
    }

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        @Suppress("DEPRECATION")
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        // AGP 8 dropped versionCode/versionName from LibraryDefaultConfig (app-only).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = rootProject.extra["javaVersion"] as JavaVersion
        targetCompatibility = rootProject.extra["javaVersion"] as JavaVersion
    }

    kotlinOptions {
        jvmTarget = (rootProject.extra["javaVersion"] as JavaVersion).toString()
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    api(libs.androidx.lifecycle.common)
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

extra["publishArtifactId"] = "traceharbor-android-lib"
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
