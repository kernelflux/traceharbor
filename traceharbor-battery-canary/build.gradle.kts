plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = (rootProject.extra["androidNamespaces"] as Map<*, *>)[project.path] as String
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        multiDexEnabled = true
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
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(project(":traceharbor-android-lib"))
    api(project(":traceharbor-trace-canary"))
    api(libs.mmkv)
    api(libs.androidx.appcompat)
    api(libs.androidx.recyclerview)
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

extra["publishArtifactId"] = "traceharbor-battery-canary"
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
