@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.mavenCentralUpload) apply false
    alias(libs.plugins.pluginPublish) apply false
}

apply(from = rootProject.file("gradle/private-properties.gradle.kts"))
apply(from = rootProject.file("gradle/root-extra-properties.gradle.kts"))
apply(from = rootProject.file("gradle/kotlin-jvm-target-alignment.gradle.kts"))

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }

    tasks.withType<Javadoc>().configureEach {
        enabled = false
        options.encoding = "UTF-8"
    }

    plugins.withId("com.android.library") {
        extensions.configure(LibraryExtension::class.java) {
            publishing {
                singleVariant("release") {}
            }
        }
    }

}

