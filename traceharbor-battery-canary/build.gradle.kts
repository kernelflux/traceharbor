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
    testImplementation("junit:junit:4.12")
    testImplementation("org.mockito:mockito-core:2.8.9")
    testImplementation("org.jmockit:jmockit:1.28")
    testImplementation("com.google.code.gson:gson:2.8.6")
    androidTestImplementation("commons-io:commons-io:2.6")
    // androidTestImplementation 'androidx.core:core:1.3.2'
    androidTestImplementation("androidx.annotation:annotation:1.0.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.multidex:multidex-instrumentation:2.0.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.1.0")
    androidTestImplementation("org.mockito:mockito-core:2.8.9")
    androidTestImplementation("org.mockito:mockito-android:2.8.9")

    implementation(project(":traceharbor-android-lib"))
    api(project(":traceharbor-trace-canary"))
    // MMKV 1.2.11 ships 4KB-aligned prebuilt .so files; 1.3.4+ ships 16KB-aligned ones,
    // required by Google Play (Nov 2025) and Android 16 / Pixel 8+ at runtime.
    implementation("com.tencent:mmkv:1.3.14")

    api("androidx.appcompat:appcompat:1.1.0")
    api("androidx.recyclerview:recyclerview:1.1.0")
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

extra["publishArtifactId"] = project.property("POM_ARTIFACT_ID").toString()
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
