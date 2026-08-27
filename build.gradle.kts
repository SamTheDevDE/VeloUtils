import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.zip.ZipFile

plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}

group = "de.samthedev.veloutils"
version = providers.gradleProperty("projectVersion").get()

allprojects {
    group = rootProject.group
    version = rootProject.version
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
            javaParameters.set(true)
            allWarningsAsErrors.set(true)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"(kotlin("test"))
    }
}

tasks.register("printVersion") {
    group = "help"
    description = "Prints the project version."
    val projectVersion = providers.gradleProperty("projectVersion")
    inputs.property("projectVersion", projectVersion)
    doLast { println(projectVersion.get()) }
}

tasks.register("verifyNoProductionJava") {
    group = "verification"
    val productionJava = fileTree(rootDir) {
        include("**/src/main/**/*.java")
        exclude("**/build/**")
    }
    inputs.files(productionJava)
    doLast {
        check(productionJava.isEmpty) {
            "Production Java sources are not allowed: ${productionJava.files.sorted().joinToString()}"
        }
    }
}

tasks.register("verifyPluginArtifacts") {
    group = "verification"
    dependsOn(":veloutils-api:jar", ":veloutils-proxy:shadowJar", ":veloutils-bridge:shadowJar")
    val embeddedVersion = version.toString()
    val apiJar = layout.projectDirectory.file("veloutils-api/build/libs/veloutils-api-$embeddedVersion.jar").asFile
    val proxyJar = layout.projectDirectory.file("veloutils-proxy/build/libs/veloutils-proxy-$embeddedVersion.jar").asFile
    val bridgeJar = layout.projectDirectory.file("veloutils-bridge/build/libs/veloutils-bridge-$embeddedVersion.jar").asFile
    val expected = listOf(apiJar, proxyJar, bridgeJar)
    inputs.files(expected)
    doLast {
        expected.forEach { check(it.isFile && it.length() > 0) { "Missing plugin artifact: $it" } }
        ZipFile(proxyJar).use { archive ->
            val metadata = checkNotNull(archive.getEntry("velocity-plugin.json")) { "Proxy metadata is missing" }
            val text = archive.getInputStream(metadata).bufferedReader().use { it.readText() }
            check("\"version\":\"$embeddedVersion\"" in text.replace(" ", "")) { "Proxy version is not embedded correctly" }
            check(archive.getEntry("kotlin/Unit.class") != null) { "Proxy artifact does not contain the Kotlin runtime" }
        }
        ZipFile(bridgeJar).use { archive ->
            val metadata = checkNotNull(archive.getEntry("plugin.yml")) { "Bridge metadata is missing" }
            val text = archive.getInputStream(metadata).bufferedReader().use { it.readText() }
            check("version: '$embeddedVersion'" in text) { "Bridge version is not embedded correctly" }
            check(archive.getEntry("kotlin/Unit.class") != null) { "Bridge artifact does not contain the Kotlin runtime" }
        }
    }
}

tasks.register("qualityGate") {
    group = "verification"
    dependsOn("verifyNoProductionJava", "verifyPluginArtifacts")
    dependsOn(subprojects.map { "${it.path}:build" })
}
