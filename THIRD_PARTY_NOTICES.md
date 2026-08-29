# Third-party notices

VeloUtils is distributed under GPL-3.0-only. Dependencies remain subject to their own licenses. This page lists the main build and runtime dependencies. License information packaged with each dependency is the final reference.

## Bundled runtime components

| Component | Purpose | License |
|---|---|---|
| Kotlin | Language runtime | Apache-2.0 |
| kotlinx.coroutines | Structured concurrency | Apache-2.0 |
| kotlinx.serialization | JSON serialization | Apache-2.0 |
| Configurate | YAML configuration | MIT |
| HikariCP | JDBC pooling | Apache-2.0 |
| SQLite JDBC | SQLite driver | Apache-2.0 |
| MySQL Connector/J | MySQL driver | GPL-2.0 with Universal FOSS Exception |
| PostgreSQL JDBC | PostgreSQL driver | BSD-2-Clause |

## Compile-time or platform-provided components

| Component | Purpose | License |
|---|---|---|
| Velocity API | Proxy API | GPL-3.0 |
| Paper API | Backend API | MIT |
| Adventure | Text API | MIT |
| PlaceholderAPI | Optional placeholders integration | GPL-3.0 |
| TAB API | Optional Velocity placeholders integration | Apache-2.0 |
| Gradle Wrapper | Build bootstrap | Apache-2.0 |

Velocity, Paper, Folia, Minecraft, Discord, Tebex, LuckPerms, PlaceholderAPI, and TAB are names or marks of their respective owners. VeloUtils is not affiliated with Mojang Studios or Microsoft.

Dependency versions are declared in the Gradle build files and should be reviewed whenever this notice or the shaded artifact contents change.
