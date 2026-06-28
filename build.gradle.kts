import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    `maven-publish`
    eclipse
}

group = "com.griefprevention"
version = providers.gradleProperty("version").get()

val minecraftVersion = providers.gradleProperty("minecraftVersion").get()
val paperMinecraftVersion = providers.gradleProperty("paperMinecraftVersion").get()
val legacyBukkitVersion = providers.gradleProperty("legacyBukkitVersion").get()
val targetJavaVersion = providers.gradleProperty("targetJavaVersion").get().toInt()
val legacyTargetJavaVersion = providers.gradleProperty("legacyTargetJavaVersion").get().toInt()
val gpFlagsRepo = providers.gradleProperty("gpFlagsRepo")
    .orElse("/mnt/storage/repos/GPFlags")
val gpExpansionRepo = providers.gradleProperty("gpExpansionRepo")
    .orElse("/mnt/storage/repos/GPExpansion")
val coreProject = project(":gp3d-core")

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.org/repository/maven-public")
    maven("https://repo.purpurmc.org/snapshots")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

sourceSets {
    val main by getting

    create("compatLegacy") {
        java.srcDir("src/compat/legacy/java")
        resources.srcDir("src/compat/legacy/resources")
        compileClasspath += main.output
        runtimeClasspath += output + compileClasspath
    }

    create("compatModern") {
        java.srcDir("src/compat/modern/java")
        resources.srcDir("src/compat/modern/resources")
        compileClasspath += main.output + main.compileClasspath
        runtimeClasspath += output + compileClasspath
    }
}

val paperApi by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val gpFlagsCompatCompileClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val gpExpansionCompatCompileClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    named("compatModernCompileOnly") {
        extendsFrom(compileOnly.get())
    }
}

dependencies {
    implementation(coreProject)
    compileOnly("org.spigotmc:spigot-api:$minecraftVersion-R0.1-SNAPSHOT")
    paperApi("io.papermc.paper:paper-api:$paperMinecraftVersion-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")

    add("compatLegacyCompileOnly", "org.bukkit:bukkit:$legacyBukkitVersion-R0.1-SNAPSHOT")
    add("compatLegacyCompileOnly", "org.jetbrains:annotations:26.0.2")

    testImplementation("org.spigotmc:spigot-api:$minecraftVersion-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
    testImplementation("org.mockito:mockito-junit-jupiter:5.16.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.jetbrains:annotations:26.0.2")

    gpFlagsCompatCompileClasspath("org.purpurmc.purpur:purpur-api:1.21.11-R0.1-SNAPSHOT")
    gpFlagsCompatCompileClasspath("com.gmail.nossr50.mcMMO:mcMMO:2.1.227") {
        exclude(group = "com.sk89q.worldguard", module = "worldguard-core")
        exclude(group = "com.sk89q.worldguard", module = "worldguard-legacy")
    }
    gpFlagsCompatCompileClasspath("org.jetbrains:annotations:24.1.0")
    gpFlagsCompatCompileClasspath("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    gpFlagsCompatCompileClasspath("io.lumine:Mythic-Dist:5.6.2")
    gpFlagsCompatCompileClasspath("org.apache.maven:maven-artifact:3.9.8")
    gpFlagsCompatCompileClasspath("com.github.PlaceholderAPI:PlaceholderAPI:2.11.5")
    gpFlagsCompatCompileClasspath("org.bstats:bstats-bukkit:3.0.2")

    gpExpansionCompatCompileClasspath("io.papermc.paper:paper-api:26.1.2.build.18-alpha")
    gpExpansionCompatCompileClasspath("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    gpExpansionCompatCompileClasspath("net.kyori:adventure-text-minimessage:4.17.0")
    gpExpansionCompatCompileClasspath("net.kyori:adventure-text-serializer-legacy:4.17.0")
    gpExpansionCompatCompileClasspath("net.kyori:adventure-text-serializer-plain:4.17.0")
    gpExpansionCompatCompileClasspath("me.clip:placeholderapi:2.11.6")
    gpExpansionCompatCompileClasspath("commons-lang:commons-lang:2.6")
    gpExpansionCompatCompileClasspath("org.jetbrains:annotations:26.0.2")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(targetJavaVersion)
    }

    named<JavaCompile>("compileCompatLegacyJava") {
        options.release.set(legacyTargetJavaVersion)
        options.compilerArgs.add("-Xlint:-options")
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("project" to mapOf("version" to project.version.toString()))
        }
    }

    jar {
        archiveBaseName.set(project.name)
        archiveVersion.set("")
        dependsOn(coreProject.tasks.named("classes"))
        from(coreProject.layout.buildDirectory.dir("classes/java/main"))
        from(sourceSets["compatLegacy"].output)
        from(sourceSets["compatModern"].output)
    }

    test {
        useJUnitPlatform()
        jvmArgs("-Xshare:off", "-Dnet.bytebuddy.experimental=true")
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    val checkPomVersion by registering {
        group = "verification"
        description = "Fails if pom.xml and Gradle project versions drift apart."

        inputs.file("pom.xml")
        inputs.property("gradleVersion", project.version.toString())

        doLast {
            val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file("pom.xml"))
            val projectNode = document.documentElement
            val children = projectNode.childNodes
            var pomVersion: String? = null

            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE && child.nodeName == "version") {
                    pomVersion = child.textContent.trim()
                    break
                }
            }

            val gradleVersion = project.version.toString()
            if (pomVersion == null) {
                throw GradleException("pom.xml does not declare a project <version>.")
            }
            if (pomVersion != gradleVersion) {
                throw GradleException(
                    "pom.xml version $pomVersion does not match Gradle version $gradleVersion."
                )
            }
        }
    }

    named("check") {
        dependsOn(checkPomVersion)
    }

    val compileGpFlagsCompatibility by registering(JavaCompile::class) {
        group = "verification"
        description = "Compiles GPFlags against this checkout's Bukkit-facing API."

        val repoDir = file(gpFlagsRepo.get())
        val sourceDir = repoDir.resolve("src/main/java")

        onlyIf {
            if (!sourceDir.isDirectory) {
                logger.lifecycle("Skipping GPFlags compatibility check; source directory not found: $sourceDir")
                false
            } else {
                true
            }
        }

        dependsOn(jar)
        source(sourceDir)
        source("src/addonCompatibility/stubs/java")
        include("**/*.java")
        destinationDirectory.set(layout.buildDirectory.dir("compat-check/gpflags/classes"))
        classpath = files(jar.flatMap { it.archiveFile }) + gpFlagsCompatCompileClasspath
        options.encoding = "UTF-8"
        options.release.set(9)
    }

    val compileGpExpansionCompatibility by registering(JavaCompile::class) {
        group = "verification"
        description = "Compiles GPExpansion against this checkout's Bukkit-facing API."

        val repoDir = file(gpExpansionRepo.get())
        val sourceDir = repoDir.resolve("src/main/java")

        onlyIf {
            if (!sourceDir.isDirectory) {
                logger.lifecycle("Skipping GPExpansion compatibility check; source directory not found: $sourceDir")
                false
            } else {
                true
            }
        }

        dependsOn(jar)
        source(sourceDir)
        include("**/*.java")
        destinationDirectory.set(layout.buildDirectory.dir("compat-check/gpexpansion/classes"))
        classpath = files(jar.flatMap { it.archiveFile }) + gpExpansionCompatCompileClasspath
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    register("checkAddonCompatibility") {
        group = "verification"
        description = "Runs local addon compatibility checks for maintained GP3D addons."
        dependsOn(compileGpFlagsCompatibility, compileGpExpansionCompatibility)
    }

    val checkWorldHeightCompatUsage by registering {
        group = "verification"
        description = "Ensures direct Bukkit world-height calls stay isolated to the compat layer."

        doLast {
            val allowedFiles = setOf(
                file("src/main/java/com/griefprevention/compat/WorldHeightCompatProvider.java").canonicalFile,
                file("src/compat/legacy/java/com/griefprevention/compat/legacy/LegacyWorldHeightCompat.java").canonicalFile,
                file("src/compat/modern/java/com/griefprevention/compat/modern/ModernWorldHeightCompat.java").canonicalFile
            )
            val sourceFiles = files(
                fileTree("src/main/java") { include("**/*.java") },
                fileTree("src/compat") { include("**/*.java") }
            )

            val violations = sourceFiles.files
                .filter { it.isFile && it.canonicalFile !in allowedFiles }
                .flatMap { sourceFile ->
                    sourceFile.readLines().mapIndexedNotNull { index, line ->
                        if (line.contains(".getMinHeight(") || line.contains(".getMaxHeight(")) {
                            "${sourceFile.relativeTo(projectDir)}:${index + 1}: ${line.trim()}"
                        } else {
                            null
                        }
                    }
                }

            if (violations.isNotEmpty()) {
                throw GradleException(
                    "Use GriefPrevention.getWorldMinY/getWorldMaxY instead of direct Bukkit world-height calls:\n" +
                        violations.joinToString("\n")
                )
            }
        }
    }

    val checkLegacyClassLoading by registering(JavaExec::class) {
        group = "verification"
        description = "Initializes key plugin classes with only legacy Bukkit on the runtime classpath."
        dependsOn(named("classes"), named("compatLegacyClasses"), coreProject.tasks.named("classes"))
        classpath = sourceSets["compatLegacy"].runtimeClasspath + files(coreProject.layout.buildDirectory.dir("classes/java/main"))
        mainClass.set("com.griefprevention.compat.legacy.LegacyClassLoadingCheck")
        args(
            "me.ryanhamshire.GriefPrevention.GriefPrevention",
            "me.ryanhamshire.GriefPrevention.BlockEventHandler",
            "me.ryanhamshire.GriefPrevention.EntityEventHandler",
            "me.ryanhamshire.GriefPrevention.EntityDamageHandler",
            "me.ryanhamshire.GriefPrevention.PlayerEventHandler",
            "me.ryanhamshire.GriefPrevention.PlayerEventHandler#construct"
        )
    }

    register("checkLegacyCompatibility") {
        group = "verification"
        description = "Compiles legacy-server compatibility sources with the configured legacy Java target."
        dependsOn(named("compileCompatLegacyJava"), checkWorldHeightCompatUsage, checkLegacyClassLoading)
    }
}

sourceSets {
    named("main") {
        compileClasspath += paperApi
    }
    named("test") {
        compileClasspath += paperApi
    }
    named("compatModern") {
        compileClasspath += paperApi
        runtimeClasspath += paperApi
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.named<Jar>("jar").flatMap { it.archiveFile })
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
        }
    }
}
