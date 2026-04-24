plugins {
    id("com.android.library")
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

apply(from = project.file("dependencies.gradle"))

group   = rootProject.extra["GROUP"].toString()
version = rootProject.extra["VERSION_NAME"].toString()

extra["publishArtifactId"] = project.property("POM_ARTIFACT_ID").toString()
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
