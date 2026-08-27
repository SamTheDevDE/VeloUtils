plugins { kotlin("jvm") }

dependencies { compileOnly("net.kyori:adventure-api:4.24.0") }

tasks.jar { archiveBaseName.set("veloutils-api") }
