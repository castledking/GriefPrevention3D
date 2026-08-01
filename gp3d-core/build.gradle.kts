import java.util.zip.ZipFile

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.6.1"
}

group = rootProject.group
version = rootProject.version

java {
    val javaVersion = JavaVersion.toVersion(providers.gradleProperty("targetJavaVersion").get())
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.6")
    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(providers.gradleProperty("targetJavaVersion").get().toInt())
}

tasks {
    shadowJar {
        archiveClassifier.set("all")
        relocate("org.yaml.snakeyaml", "com.griefprevention.internal.lib.snakeyaml")
    }

    test {
        useJUnitPlatform()
    }

    val checkCoreBoundary by registering {
        group = "verification"
        description = "Fails if gp3d-core sources import platform APIs."

        doLast {
            val forbidden = listOf(
                "org.bukkit",
                "net.minecraft",
                "net.fabricmc",
                "net.minecraftforge",
                "net.neoforged"
            )
            val violations = fileTree("src/main/java") {
                include("**/*.java")
            }.files.flatMap { sourceFile ->
                sourceFile.readLines().mapIndexedNotNull { index, line ->
                    val trimmed = line.trim()
                    val forbiddenImport = forbidden.firstOrNull { trimmed.startsWith("import $it.") }
                    if (forbiddenImport == null) {
                        null
                    } else {
                        "${sourceFile.relativeTo(projectDir)}:${index + 1}: $trimmed"
                    }
                }
            }

            if (violations.isNotEmpty()) {
                throw GradleException(
                    "gp3d-core must stay platform-neutral. Forbidden imports:\n" +
                        violations.joinToString("\n")
                )
            }
        }
    }

    val checkShadedCoreIsolation by registering {
        group = "verification"
        description = "Verifies bundled YAML classes are relocated away from addon/mod namespaces."
        dependsOn(shadowJar)

        doLast {
            val shadedJar = shadowJar.get().archiveFile.get().asFile
            ZipFile(shadedJar).use { archive ->
                val entries = archive.entries().asSequence().map { it.name }.toList()
                if (entries.any { it.startsWith("org/yaml/snakeyaml/") && it.endsWith(".class") }) {
                    throw GradleException("Unrelocated SnakeYAML classes found in ${shadedJar.name}.")
                }
                if ("com/griefprevention/internal/lib/snakeyaml/Yaml.class" !in entries) {
                    throw GradleException("Relocated SnakeYAML payload missing from ${shadedJar.name}.")
                }
            }
        }
    }

    named("check") {
        dependsOn(checkCoreBoundary, checkShadedCoreIsolation)
    }
}
