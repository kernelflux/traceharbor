plugins {
    id("com.android.library")
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

    testImplementation("junit:junit:4.12")
    implementation("androidx.annotation:annotation:1.0.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.1.0")
    // implementation "com.tencent.stubs:logger:${rootProject.LOGGER_VERSION}"
    implementation(project(":traceharbor-android-lib"))
    implementation(project(":traceharbor-android-commons"))
}

group   = rootProject.extra["GROUP"].toString()
version = rootProject.extra["VERSION_NAME"].toString()

extra["publishArtifactId"] = project.property("POM_ARTIFACT_ID").toString()
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
