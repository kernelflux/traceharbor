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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlinOptions {
        jvmTarget = javaVersion.toString()
    }
}

val gradleExtra = (gradle as org.gradle.api.plugins.ExtensionAware).extensions.extraProperties
val kotlinVersion: String = gradleExtra.get("KOTLIN_VERSION").toString()

dependencies {
    implementation(fileTree("libs") { include("*.jar") })
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation(project(":traceharbor-resource-canary:traceharbor-resource-canary-common"))
    implementation("com.tencent.tinker:tinker-ziputils:1.9.2")
    implementation(project(":traceharbor-android-lib"))
    implementation(project(":traceharbor-android-commons"))
    implementation(project(":traceharbor-backtrace"))
    implementation(project(":traceharbor-resource-canary:traceharbor-resource-canary-analyzer"))

    testImplementation("junit:junit:4.12")
}

version = rootProject.extra["VERSION_NAME"].toString()
group   = rootProject.extra["GROUP"].toString()

extra["publishArtifactId"] = project.property("POM_ARTIFACT_ID").toString()
extra["publishVersion"]    = version.toString()
apply(from = rootProject.file("gradle/maven-publish.gradle.kts"))
