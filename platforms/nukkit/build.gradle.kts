// The Cloudburst Nukkit (Minecraft: Bedrock Edition) adapter. It ships inside the universal jar, where
// Nukkit reads nukkit.yml in preference to plugin.yml; Bukkit reads plugin.yml from the same jar and
// never loads these classes. See README.md.
plugins {
    `java-library`
}

group = rootProject.group
version = rootProject.version

val nukkitVersion = providers.gradleProperty("nukkitVersion").get()
val nukkitApiVersion = providers.gradleProperty("nukkitApiVersion").get()
val projectVersion = project.version.toString()
// plugin.yml stays the one place permissions are declared; nukkit.yml copies its permissions section.
val bukkitDescriptor = rootProject.file("src/main/resources/plugin.yml")

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/maven-releases/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
}

dependencies {
    compileOnly("cn.nukkit:nukkit:$nukkitVersion")
    // The universal jar already carries gp3d-core, with SnakeYAML relocated, from the Bukkit jar; the
    // tests run against that same relocated build. Nukkit bundles its own, older SnakeYAML.
    compileOnly(project(":gp3d-core"))
    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation("cn.nukkit:nukkit:$nukkitVersion")
    testImplementation(project(path = ":gp3d-core", configuration = "shadowRuntimeElements"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

java {
    // Nukkit itself is Java 8 bytecode, and its official Docker image still runs Java 8.
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(8)
        options.compilerArgs.add("-Xlint:-options")
    }

    processResources {
        inputs.property("version", projectVersion)
        inputs.property("api", nukkitApiVersion)
        inputs.file(bukkitDescriptor)
        filesMatching("nukkit.yml") {
            expand(
                "version" to projectVersion,
                "api" to nukkitApiVersion,
                "permissions" to permissionsSection(bukkitDescriptor)
            )
        }
    }

    test {
        useJUnitPlatform()
        inputs.file(bukkitDescriptor)
        systemProperty("griefprevention3d.version", projectVersion)
        systemProperty("griefprevention3d.bukkitDescriptor", bukkitDescriptor.absolutePath)
    }
}

/** The top-level {@code permissions:} section of a Bukkit plugin.yml, verbatim. */
fun permissionsSection(descriptor: File): String {
    val lines = descriptor.readLines()
    val start = lines.indexOf("permissions:")
    if (start < 0) {
        throw GradleException("${descriptor.name} has no top-level permissions section.")
    }
    val end = (start + 1 until lines.size).firstOrNull { lines[it].matches(Regex("[A-Za-z].*")) } ?: lines.size
    return lines.subList(start, end).joinToString("\n").trimEnd()
}
