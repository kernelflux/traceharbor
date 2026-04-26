// =============================================================================
// 通用 Maven Central 发布配置 — Kotlin DSL.
//
// Ported from gradle/maven-publish.gradle as part of Stage 5 of
// docs/plans/2026-04-23-brand-and-modernize-plan.md.
//
// 用法 (still-Groovy 模块仍可 apply from):
//   ext {
//       publishArtifactId = "traceharbor-xxx"        // 必需
//       publishVersion    = rootProject.ext.VERSION_NAME  // 必需
//       // publishBundleName = "..."                 // 可选，默认按 artifactId 生成
//       // publishAdditionalPublications = [...]     // flavor 模块可选
//   }
//   apply from: rootProject.file('gradle/maven-publish.gradle.kts')
//
// 主要工作：apply `com.kernelflux.maven.publish` 并配置 `mavenCentralUpload`。
// 对 flavor 模块（设置了 `ext.publishAdditionalPublications`）会先 apply
// `maven-publish` 并在 `afterEvaluate` 内创建对应 publication，避免 kfx 默认
// =============================================================================
import org.gradle.api.Action
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import java.io.File
import java.util.Properties

// `MavenCentralUploadExtension` is intentionally NOT imported at the script
// top-level. Reasons:
//   1. apply-from KTS scripts compile in their own classloader and do NOT
//      inherit the parent project's buildscript classpath, so the type would
//      be Unresolved at compile time without our own `buildscript {}` block.
//   2. Adding our own `buildscript {}` here creates a SEPARATE classloader
//      for the same jar that root build.gradle.kts already loaded, which
//      breaks Extension type lookup ("Extension of type X does not exist"
//      even when X is in the registered list, because the type identities
//      differ across classloaders).
//
// The fix that works for both: do all configuration via `extensions.configure`
// by NAME ("mavenCentralUpload"), use `Any` as the typed receiver, and access
// `Property<String>` setters via Groovy-style dynamic dispatch through
// `withGroovyBuilder` / direct method invocation on `Provider`. The runtime
// behavior is identical to a typed configure block.

if (!project.hasProperty("publishArtifactId")) {
    throw GradleException("publishArtifactId must be defined before applying gradle/maven-publish.gradle.kts")
}
if (!project.hasProperty("publishVersion")) {
    throw GradleException("publishVersion must be defined before applying gradle/maven-publish.gradle.kts")
}

// Mirror fluxrouter/gradle/maven-publish.gradle: hard-code the publish group so
// we never depend on rootProject.ext.GROUP for the actual coordinate. All
// artifacts (regular AAR/jar and the gradle plugin jar) live under this group;
// only the artifactId differs per module.
val sGroupId = "com.kernelflux.mobile"
// Groovy modules may set ext.publishArtifactId via interpolated GString literals
// ("${...}") which Kotlin sees as `groovy.lang.GString`, not String — force-
// stringify with `.toString()` rather than the brittle `as String` cast.
val publishArtifactIdValue = project.extra["publishArtifactId"].toString()
val publishVersionValue = project.extra["publishVersion"].toString()
val publishBundleNameValue: String =
    if (project.hasProperty("publishBundleName")) {
        project.extra["publishBundleName"].toString()
    } else {
        "${publishArtifactIdValue.replace('-', '_')}_bundle_v$publishVersionValue"
    }

// 多发布支持（仅 traceharbor-sqlite-lint-android-sdk 这种带 flavor 的模块需要）：
//   ext.publishAdditionalPublications = [[name:'release', component:'fullRelease', artifactId:'...'], ...]
// 必须在 kfx 插件 apply 之前创建 'release' publication，否则 kfx 默认会用不存在的
// `components.release`（flavor 模块的组件名是 `<flavor>Release`）失败。
// Groovy modules build the list as `[[name:..., component:..., ...], ...]`
// where map values are GString | String — keep value type as Any? and stringify
// at access time.
@Suppress("UNCHECKED_CAST")
val extraPublications: List<Map<String, Any?>> =
    if (project.hasProperty("publishAdditionalPublications")) {
        project.extra["publishAdditionalPublications"] as List<Map<String, Any?>>
    } else {
        emptyList()
    }

// ---- 凭据/签名读取顺序：-P / gradle.properties -> private.properties -> rootProject.ext ----
// `gradle.privateProperties` 由 root build.gradle.kts 顶部 apply 的
// gradle/private-properties.gradle.kts 提供。
val gradleExtra = (gradle as org.gradle.api.plugins.ExtensionAware).extensions.extraProperties
val sharedPrivateProps: Properties =
    if (gradleExtra.has("privateProperties")) {
        gradleExtra.get("privateProperties") as Properties
    } else {
        Properties()
    }

fun readCred(propName: String): String? {
    val v = project.findProperty(propName) ?: sharedPrivateProps.getProperty(propName)
    return v?.toString()
}

val sonatypeUsername = readCred("sonatypeUsername")
val sonatypePassword = readCred("sonatypePassword")
var signingKeyFileProp = readCred("signingKeyFile")
var signingPassProp = readCred("signingPass")

// rootProject.ext 兜底（fluxrouter 同款）
val rootExtra = project.rootProject.extra
fun rootExtraOrNull(name: String): String? {
    return try {
        val v = rootExtra.get(name) ?: return null
        val s = v.toString()
        if (s.isEmpty() || s.contains("extension")) null else s
    } catch (_: Exception) {
        null
    }
}
if (signingKeyFileProp == null) signingKeyFileProp = rootExtraOrNull("signingKeyFile")
if (signingPassProp == null) signingPassProp = rootExtraOrNull("signingPass")

// 共享 POM 块：从 rootProject.ext.POM_* 读，与现有 build.gradle 保持兼容。
// `.toString()` not `as String` so Groovy GString values are accepted as well.
fun configureSharedPom(mavenPom: MavenPom, pomName: String) {
    mavenPom.name.set(pomName)
    mavenPom.description.set(rootExtra.get("POM_DESCRIPTION").toString())
    mavenPom.url.set(rootExtra.get("POM_URL").toString())
    mavenPom.licenses {
        license {
            name.set(rootExtra.get("POM_LICENCE_NAME").toString())
            url.set(rootExtra.get("POM_LICENCE_URL").toString())
        }
    }
    mavenPom.developers {
        developer {
            id.set(rootExtra.get("POM_DEVELOPER_ID").toString())
            name.set(rootExtra.get("POM_DEVELOPER_NAME").toString())
        }
    }
    mavenPom.scm {
        url.set(rootExtra.get("POM_URL").toString())
        connection.set(rootExtra.get("POM_SCM_URL").toString())
        developerConnection.set(rootExtra.get("POM_SCM_URL").toString())
    }
}

if (extraPublications.isNotEmpty()) {
    if (!project.plugins.hasPlugin("maven-publish")) {
        project.plugins.apply("maven-publish")
    }
    project.afterEvaluate {
        val publishingExtension = project.extensions.getByType(PublishingExtension::class.java)
        publishingExtension.publications {
            extraPublications.forEach { cfg ->
                val name = cfg["name"].toString()
                val componentName = cfg["component"].toString()
                val artifactIdValue = cfg["artifactId"].toString()
                val pub = findByName(name) as MavenPublication?
                    ?: create(name, MavenPublication::class.java)
                pub.groupId = sGroupId
                pub.version = publishVersionValue
                pub.artifactId = artifactIdValue
                if (project.components.findByName(componentName) == null) {
                    throw GradleException("Component '$componentName' not found for publication '$name' in ${project.path}")
                }
                pub.from(project.components.getByName(componentName))
                configureSharedPom(pub.pom, cfg["pomName"]?.toString() ?: artifactIdValue)
            }
        }
    }
}

if (!project.plugins.hasPlugin("com.kernelflux.maven.publish")) {
    project.plugins.apply("com.kernelflux.maven.publish")
}

// Configure mavenCentralUpload by name (see top-of-file comment). Each
// `Property<String>` setter is reached via `getXxx().set(...)` reflection on
// the live extension instance — works regardless of which classloader the
// extension type was loaded from.
val mcu: Any = project.extensions.getByName("mavenCentralUpload")
fun setStringProp(getterName: String, value: String) {
    val prop = mcu.javaClass.getMethod(getterName).invoke(mcu)
    val setMethod = prop.javaClass.methods.first { it.name == "set" && it.parameterCount == 1 && it.parameterTypes[0] == Any::class.java }
    setMethod.invoke(prop, value)
}
fun setActionProp(getterName: String, action: Any) {
    val prop = mcu.javaClass.getMethod(getterName).invoke(mcu)
    val setMethod = prop.javaClass.methods.first { it.name == "set" && it.parameterCount == 1 && it.parameterTypes[0] == Any::class.java }
    setMethod.invoke(prop, action)
}

setStringProp("getUploadBundleName", publishBundleNameValue)
setStringProp("getGroupId", sGroupId)
setStringProp("getArtifactId", publishArtifactIdValue)
setStringProp("getVersion", publishVersionValue)

sonatypeUsername?.let { setStringProp("getUsername", it) }
sonatypePassword?.let { setStringProp("getPassword", it) }

signingKeyFileProp?.let { keyStr: String ->
    val keyFileObj = File(keyStr)
    val rootDirStr = project.rootProject.projectDir.toString()
    val finalPath: String = if (keyFileObj.isAbsolute && keyFileObj.exists()) {
        keyFileObj.absolutePath
    } else {
        // 处理 "/gpg/secret-key.asc" 这种以 / 开头但是相对路径的写法（fluxrouter 同款）
        val normalized = if (keyStr.startsWith("/") && !keyFileObj.isAbsolute) keyStr.substring(1) else keyStr
        File(rootDirStr, normalized).absolutePath
    }
    if (File(finalPath).exists()) {
        setStringProp("getSigningKeyFile", finalPath)
    } else {
        println("Maven Central Upload: signingKeyFile not found: $finalPath, signing will be disabled")
    }
}
signingPassProp?.let { setStringProp("getSigningPass", it) }

setActionProp("getPom", object : Action<MavenPom> {
    override fun execute(mavenPom: MavenPom) {
        val pomName = if (project.hasProperty("POM_NAME")) {
            project.property("POM_NAME").toString()
        } else {
            publishArtifactIdValue
        }
        configureSharedPom(mavenPom, pomName)
    }
})

// gradle/check.gradle was ported to KTS in Stage 5; cross-language `apply from:`
// (KTS -> KTS) works without ceremony.
if (project.rootProject.file("gradle/check.gradle.kts").exists()) {
    apply(from = rootProject.file("gradle/check.gradle.kts"))
}
