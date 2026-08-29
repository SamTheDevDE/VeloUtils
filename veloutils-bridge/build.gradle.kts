import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":veloutils-api"))
    implementation(project(":veloutils-common"))
    implementation(project(":veloutils-core"))
    implementation(project(":veloutils-protocol"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.119-stable")
    compileOnly("me.clip:placeholderapi:2.11.7")
    implementation("org.spongepowered:configurate-yaml:4.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("net.kyori:adventure-text-minimessage:4.24.0")
    testImplementation("net.kyori:adventure-text-serializer-plain:4.24.0")
}

tasks.processResources {
    val projectVersion = project.version.toString()
    inputs.property("version", projectVersion)
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) { expand("version" to projectVersion) }
}

tasks.jar { enabled = false }

tasks.named<ShadowJar>("shadowJar") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    archiveBaseName.set("VeloUtils-Paper")
    archiveClassifier.set("")
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt")
    relocate("org.spongepowered.configurate", "de.samthedev.veloutils.lib.configurate")
    relocate("kotlinx", "de.samthedev.veloutils.lib.kotlinx")
}

tasks.assemble { dependsOn(tasks.shadowJar) }
