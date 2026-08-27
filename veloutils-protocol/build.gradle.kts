plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":veloutils-api"))
    implementation(project(":veloutils-common"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
