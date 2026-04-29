import org.gradle.kotlin.dsl.withGroovyBuilder
import java.util.Properties

plugins {
    id("com.android.application")
    id("com.kernelflux.traceharbor.plugin")
}

val javaVersion = rootProject.extra["javaVersion"] as JavaVersion
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystoreProps.load(keystorePropsFile.inputStream())
}
// helper to read env if property missing
fun propOrEnv(key: String): String? =
    (keystoreProps.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

fun signingFileFromProp(key: String): File? {
    val value = propOrEnv(key) ?: return null
    val propFile = File(value)
    if (propFile.isAbsolute) return propFile

    val direct = keystorePropsFile.parentFile.resolve(value)
    if (direct.exists()) return direct

    val appSubDir = keystorePropsFile.parentFile.resolve("app").resolve(value)
    if (appSubDir.exists()) return appSubDir

    return direct
}


val appPkgName = "com.kernelflux.traceharborsample"
val appVerCode = 1
val appVerName = "1.0.0"

android {
    namespace = appPkgName
    compileSdk = rootProject.extra["compileSdkVersion"] as Int

    defaultConfig {
        applicationId = appPkgName
        minSdk = rootProject.extra["minSdkVersion"] as Int
        versionCode = appVerCode
        versionName = appVerName

        missingDimensionStrategy("mode", "full")
    }

    signingConfigs {
        create("release") {
            storeFile = signingFileFromProp("storeFile")
            storePassword = propOrEnv("storePassword")
            keyAlias = propOrEnv("keyAlias")
            keyPassword = propOrEnv("keyPassword")
        }
    }


    buildTypes {
        debug {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts.add("lib/armeabi-v7a/libc++_shared.so")
            pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
            pickFirsts.add("lib/armeabi-v7a/libtraceharbor-backtrace.so")
            pickFirsts.add("lib/arm64-v8a/libtraceharbor-backtrace.so")
        }
    }
}

traceHarbor {
    trace {
        isEnable = true
        ignorePackages = listOf("com.kernelflux.traceharborsample.subpkg")
        ignoreClasses = listOf(
            "com.kernelflux.traceharborsample.LeakActivity",
            "com.kernelflux.traceharborsample.LeakStore",
        )
    }
}


dependencies {
    val matrixModules = arrayOf(
        "traceharbor-android-commons",
        "traceharbor-android-lib",
        "traceharbor-apk-canary",
        "traceharbor-arscutil",
        "traceharbor-backtrace",
        "traceharbor-battery-canary",
        "traceharbor-commons",
        "traceharbor-fd",
        "traceharbor-hooks",
        "traceharbor-io-canary",
        "traceharbor-mallctl",
        "traceharbor-memguard",
        "traceharbor-memory-canary",
        "traceharbor-opengl-leak",
        "traceharbor-resource-canary-android",
        "traceharbor-resource-canary-common",
        "traceharbor-sqlite-lint-android-sdk",
        "traceharbor-trace-canary",
        "traceharbor-traffic",
    )
    matrixModules.forEach {
        if (it.startsWith("traceharbor-resource")) {
            debugImplementation(project(":traceharbor-resource-canary:$it"))
        } else if (it.startsWith("traceharbor-sqlite")) {
            debugImplementation(project(":traceharbor-sqlite-lint:$it"))
        } else {
            debugImplementation(project(":$it"))
        }
        releaseImplementation(group = "com.kernelflux.mobile", name = it, version = "0.0.1")
    }
    implementation(libs.androidx.recyclerview)
}
