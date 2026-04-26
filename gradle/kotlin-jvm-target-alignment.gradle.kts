import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension

allprojects {
    val alignKotlinJvmTarget = Action<Project> {
        afterEvaluate {
            val javaTargetFromAndroid = runCatching {
                val androidExt = extensions.findByName("android") ?: return@runCatching null
                val compileOptions = androidExt.javaClass.methods
                    .firstOrNull { it.name == "getCompileOptions" }
                    ?.invoke(androidExt)
                    ?: return@runCatching null
                compileOptions.javaClass.methods
                    .firstOrNull { it.name == "getTargetCompatibility" }
                    ?.invoke(compileOptions)
                    ?.toString()
            }.getOrNull()

            val javaTarget = javaTargetFromAndroid
                ?: extensions.findByType(JavaPluginExtension::class.java)
                    ?.targetCompatibility
                    ?.toString()
                ?: (rootProject.extra["javaVersion"] as JavaVersion).toString()

            tasks.matching { task ->
                task.name.startsWith("compile") && task.name.endsWith("Kotlin")
            }.configureEach {
                val kotlinOptions = javaClass.methods
                    .firstOrNull { it.name == "getKotlinOptions" }
                    ?.invoke(this)
                    ?: return@configureEach
                kotlinOptions.javaClass.methods
                    .firstOrNull { it.name == "setJvmTarget" }
                    ?.invoke(kotlinOptions, javaTarget)
            }
        }
    }

    plugins.withId("kotlin-android") { alignKotlinJvmTarget.execute(this@allprojects) }
    plugins.withId("org.jetbrains.kotlin.android") { alignKotlinJvmTarget.execute(this@allprojects) }
    plugins.withId("org.jetbrains.kotlin.jvm") { alignKotlinJvmTarget.execute(this@allprojects) }
    plugins.withId("kotlin") { alignKotlinJvmTarget.execute(this@allprojects) }
}
