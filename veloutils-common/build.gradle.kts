plugins { kotlin("jvm") }

dependencies {
    api(project(":veloutils-api"))
    compileOnly("net.kyori:adventure-api:4.24.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.24.0")
    testImplementation("net.kyori:adventure-api:4.24.0")
    testImplementation("net.kyori:adventure-text-minimessage:4.24.0")
    testImplementation("net.kyori:adventure-text-serializer-plain:4.24.0")
}
