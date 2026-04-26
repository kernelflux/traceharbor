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
        @Suppress("DEPRECATION")
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        // AGP 8 dropped versionCode/versionName from LibraryDefaultConfig (app-only).

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

    // KTS quirk: per-flavor sourceSets only exist after productFlavors are
    // declared. Original Groovy script relied on dynamic creation; KTS needs
    // explicit ordering.
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
    testImplementation("junit:junit:4.12")
    androidTestImplementation("androidx.annotation:annotation:1.0.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.1")

    implementation(project(":traceharbor-android-lib"))
    implementation(project(":traceharbor-android-commons"))
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

val pomArtifactId: String = project.property("POM_ARTIFACT_ID").toString()

extra["publishArtifactId"] = pomArtifactId
extra["publishVersion"]    = version.toString()
extra["publishAdditionalPublications"] = listOf(
    mapOf("name" to "release", "component" to "fullRelease", "artifactId" to pomArtifactId),
    mapOf("name" to "noOp",    "component" to "stubRelease", "artifactId" to "$pomArtifactId-no-op"),
)
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
