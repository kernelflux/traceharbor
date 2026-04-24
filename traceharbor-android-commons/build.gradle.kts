import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.withGroovyBuilder

plugins {
    id("com.android.library")
}

apply(from = rootProject.file("gradle/TraceHarborNativeDepend.gradle"))

val enableLog: Boolean = gradle.startParameter.projectProperties.containsKey("EnableLog")

android {
    namespace = (rootProject.extra["androidNamespaces"] as Map<*, *>)[project.path] as String
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        minSdk = rootProject.extra["minSdkVersion"] as Int
        @Suppress("DEPRECATION")
        targetSdk = rootProject.extra["targetSdkVersion"] as Int
        // AGP 8 dropped versionCode/versionName from LibraryDefaultConfig (app-only).

        ndk {
            abiFilters += setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        externalNativeBuild {
            cmake {
                targets += setOf("xhook", "semi_dlfcn", "enhance_dlsym")
                arguments += "-DEnableLOG=${if (enableLog) "ON" else "OFF"}"
            }
            // exportHeaders { } is added dynamically by TraceHarborNativeDepend.gradle
            // onto defaultConfig.externalNativeBuildOptions. Configured below the
            // android { } block via the extensions API + withGroovyBuilder.
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    sourceSets["main"].apply {
        jniLibs.srcDirs("libs")
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
    }
}

// ---------------------------------------------------------------------------
// Dynamic `exportHeaders { }` extension installed by TraceHarborNativeDepend.gradle.
// Not visible to KTS as a typed accessor on `externalNativeBuildOptions`, so we
// reach it via ExtensionAware.extensions and drive it with Groovy MOP.
// ---------------------------------------------------------------------------
val exportHeadersExt = (android.defaultConfig.externalNativeBuild as ExtensionAware)
    .extensions
    .findByName("exportHeaders")
    ?: error("exportHeaders extension was not registered — did TraceHarborNativeDepend.gradle apply correctly?")

exportHeadersExt.withGroovyBuilder {
    "from"("src/main/cpp/libxhook") {
        "include"("**/*.h")
    }
    "from"("src/main/cpp/libsemi_dlfcn") {
        "include"("**/*.h")
    }
    "from"("src/main/cpp/libenhance_dlsym") {
        "include"("**/*.h")
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation(project(":traceharbor-android-lib"))
    testImplementation("junit:junit:4.12")

    androidTestImplementation("androidx.annotation:annotation:1.0.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.1")
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

extra["publishArtifactId"] = project.property("POM_ARTIFACT_ID").toString()
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
