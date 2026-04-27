@file:Suppress("UnstableApiUsage")

plugins {
    id("com.android.library")
    id("kotlin-android")
}

apply(from = rootProject.file("gradle/TraceHarborNativeDepend.gradle"))

val javaVersion = rootProject.extra["javaVersion"] as JavaVersion

android {
    namespace = (rootProject.extra["androidNamespaces"] as Map<*, *>)[project.path] as String
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++11 -frtti -fexceptions"
                arguments += "-Dplatform=android"
            }
            ndk {
                abiFilters += setOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path("../CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlinOptions {
        jvmTarget = javaVersion.toString()
    }

    flavorDimensions += "mode"
    productFlavors {
        create("full") {
            dimension = "mode"
            extra["artifactIdSuffix"] = ""
        }
        create("stub") {
            dimension = "mode"
            extra["artifactIdSuffix"] = "no-op"
        }
    }

    sourceSets.getByName("full") {
        jni.srcDirs("cpp")
    }
    sourceSets.getByName("stub") {
        jniLibs.setSrcDirs(emptyList<String>())
    }

    publishing {
        singleVariant("fullRelease") { }
        singleVariant("stubRelease") { }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(project(":traceharbor-android-lib"))
    implementation(project(":traceharbor-android-commons"))
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

extra["publishArtifactId"] = "traceharbor-sqlite-lint-android-sdk"
extra["publishVersion"]    = version.toString()
extra["publishAdditionalPublications"] = listOf(
    mapOf("name" to "release", "component" to "fullRelease", "artifactId" to "traceharbor-sqlite-lint-android-sdk"),
    mapOf("name" to "noOp",    "component" to "stubRelease", "artifactId" to "traceharbor-sqlite-lint-android-sdk-no-op"),
)
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
