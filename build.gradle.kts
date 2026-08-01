import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.zip.ZipFile

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

val shadedCore by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
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

eclipse {
    classpath {
        plusConfigurations.add(paperApi)
        containers.clear()
        containers.add("org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-21")
    }
    jdt {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        javaRuntimeName = "JavaSE-21"
    }
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

configurations {
    named("compatModernCompileOnly") {
        extendsFrom(compileOnly.get())
    }
}

dependencies {
    implementation(coreProject)
    shadedCore(project(path = ":gp3d-core", configuration = "shadowRuntimeElements"))
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
        inputs.property("version", project.version.toString())
        filesMatching("plugin.yml") {
            expand("project" to mapOf("version" to project.version.toString()))
        }
    }

    jar {
        archiveBaseName.set(project.name)
        archiveVersion.set("")
        dependsOn(coreProject.tasks.named("shadowJar"))
        from({ shadedCore.map(::zipTree) })
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

val fabricAdapterProject = findProject(":fabric-1.21.11")
if (fabricAdapterProject != null) {
    evaluationDependsOn(":fabric-1.21.11")

    val fabricMinecraftVersion = providers.gradleProperty("fabricMinecraftVersion").get()
    val fabricApiVersion = providers.gradleProperty("fabricApiVersion").get()
    val bukkitJar = tasks.named<Jar>("jar")
    val fabricRemapJar = fabricAdapterProject.tasks.named<AbstractArchiveTask>("remapJar")
    val processedFabricMetadata = fabricAdapterProject.layout.buildDirectory
        .file("resources/main/fabric.mod.json")

    val universalFabricSmokeMappings = configurations.create("universalFabricSmokeMappings") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
    val universalFabricSmokeMods = configurations.create("universalFabricSmokeMods") {
        isCanBeResolved = true
        isCanBeConsumed = false
    }
    dependencies {
        add(
            universalFabricSmokeMappings.name,
            "net.fabricmc:intermediary:$fabricMinecraftVersion:v2"
        ) {
            isTransitive = false
        }
        add(
            universalFabricSmokeMods.name,
            "net.fabricmc.fabric-api:fabric-api:$fabricApiVersion"
        ) {
            isTransitive = false
        }
    }

    val universalJar = tasks.register<Jar>("universalJar") {
        group = "build"
        description = "Assembles one byte-identical Bukkit and Fabric distribution jar."
        dependsOn(bukkitJar, fabricRemapJar, fabricAdapterProject.tasks.named("processResources"))

        archiveBaseName.set("GriefPrevention3D-Universal")
        archiveVersion.set(project.version.toString())
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true

        from(bukkitJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) })
        from(fabricRemapJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
            exclude("fabric.mod.json")
            exclude("META-INF/MANIFEST.MF")
            exclude("META-INF/jars")
            exclude("META-INF/jars/**")
            exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        }
        from(processedFabricMetadata)
    }

    val checkUniversalJar = tasks.register("checkUniversalJar") {
        group = "verification"
        description = "Verifies dual-platform metadata, bytecode isolation, and jar contents."
        dependsOn(universalJar)
        inputs.file(universalJar.flatMap { it.archiveFile })
        inputs.file(bukkitJar.flatMap { it.archiveFile })
        inputs.file(fabricRemapJar.flatMap { it.archiveFile })

        doLast {
            val universalFile = universalJar.get().archiveFile.get().asFile
            val bukkitFile = bukkitJar.get().archiveFile.get().asFile
            val fabricFile = fabricRemapJar.get().archiveFile.get().asFile

            ZipFile(universalFile).use { universal ->
                ZipFile(bukkitFile).use { bukkit ->
                    ZipFile(fabricFile).use { fabric ->
                        val universalEntries = universal.entries().asSequence().toList()
                        val universalNames = universalEntries.map { it.name }
                        val duplicateNames = universalNames.groupingBy { it }.eachCount()
                            .filterValues { it > 1 }
                            .keys
                        if (duplicateNames.isNotEmpty()) {
                            throw GradleException("Duplicate universal-jar entries: $duplicateNames")
                        }

                        val required = listOf(
                            "plugin.yml",
                            "fabric.mod.json",
                            "griefprevention3d.mixins.json",
                            "me/ryanhamshire/GriefPrevention/GriefPrevention.class",
                            "com/griefprevention/fabric/bootstrap/UniversalFabricBootstrap.class",
                            "com/griefprevention/fabric/GriefPreventionFabric.class",
                            "com/griefprevention/fabric/mixin/ServerExplosionMixin.class",
                            "com/griefprevention/persistence/ClaimDocumentCodec.class",
                            "com/griefprevention/internal/lib/snakeyaml/Yaml.class"
                        )
                        val missing = required.filterNot { it in universalNames }
                        if (missing.isNotEmpty()) {
                            throw GradleException("Universal jar is missing required entries: $missing")
                        }
                        if (universalNames.any { it.startsWith("META-INF/jars/") }) {
                            throw GradleException("Universal jar contains a duplicate nested dependency payload.")
                        }
                        if (universalNames.any { it.startsWith("org/yaml/snakeyaml/") }) {
                            throw GradleException("Universal jar exposes unrelocated SnakeYAML classes.")
                        }

                        fun bytes(archive: ZipFile, name: String): ByteArray {
                            val entry = archive.getEntry(name)
                                ?: throw GradleException("Missing $name in ${archive.name}")
                            return archive.getInputStream(entry).use { it.readBytes() }
                        }

                        val fabricMetadata = bytes(universal, "fabric.mod.json").toString(Charsets.UTF_8)
                        if (!fabricMetadata.contains(
                                "com.griefprevention.fabric.bootstrap.UniversalFabricBootstrap"
                            )) {
                            throw GradleException("Universal Fabric metadata does not use the Java 8 bootstrap.")
                        }
                        if (Regex("\\\"jars\\\"\\s*:").containsMatchIn(fabricMetadata)) {
                            throw GradleException("Universal Fabric metadata still references nested jars.")
                        }
                        if (!fabricMetadata.contains("\"version\": \"${project.version}\"")) {
                            throw GradleException("Universal Fabric metadata version does not match the build.")
                        }

                        val pluginMetadata = bytes(universal, "plugin.yml").toString(Charsets.UTF_8)
                        if (!pluginMetadata.contains("version: \"${project.version}\"")) {
                            throw GradleException("Universal Bukkit metadata version does not match the build.")
                        }

                        fun classMajorVersion(name: String): Int {
                            return DataInputStream(bytes(universal, name).inputStream()).use { input ->
                                if (input.readInt() != 0xCAFEBABE.toInt()) {
                                    throw GradleException("$name is not a JVM class file.")
                                }
                                input.readUnsignedShort()
                                input.readUnsignedShort()
                            }
                        }

                        val java8Major = 44 + 8
                        val java21Major = 44 + 21
                        if (classMajorVersion("me/ryanhamshire/GriefPrevention/GriefPrevention.class") != java8Major) {
                            throw GradleException("Bukkit entrypoint is not Java 8 bytecode.")
                        }
                        if (classMajorVersion(
                                "com/griefprevention/fabric/bootstrap/UniversalFabricBootstrap.class"
                            ) != java8Major) {
                            throw GradleException("Fabric bootstrap is not Java 8 bytecode.")
                        }
                        if (classMajorVersion(
                                "com/griefprevention/fabric/GriefPreventionFabric.class"
                            ) != java21Major) {
                            throw GradleException("Fabric 1.21.11 adapter is not Java 21 bytecode.")
                        }

                        for (entry in bukkit.entries().asSequence().filter { !it.isDirectory }) {
                            if (entry.name == "META-INF/MANIFEST.MF") continue
                            if (!bytes(bukkit, entry.name).contentEquals(bytes(universal, entry.name))) {
                                throw GradleException(
                                    "Universal assembly changed Bukkit entry ${entry.name}."
                                )
                            }
                        }
                        for (entry in fabric.entries().asSequence().filter {
                            !it.isDirectory && (
                                it.name.startsWith("com/griefprevention/fabric/")
                                    || it.name == "griefprevention3d.mixins.json"
                            )
                        }) {
                            if (!bytes(fabric, entry.name).contentEquals(bytes(universal, entry.name))) {
                                throw GradleException(
                                    "Universal assembly changed Fabric entry ${entry.name}."
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val checkUniversalLegacyClassLoading = tasks.register<JavaExec>("checkUniversalLegacyClassLoading") {
        group = "verification"
        description = "Loads Bukkit entry classes from the universal jar on a real Java 8 runtime."
        dependsOn(universalJar)
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(8))
        })
        classpath = files(universalJar.flatMap { it.archiveFile }) +
            sourceSets["compatLegacy"].runtimeClasspath.filter { it.isFile }
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

    val universalFabricSmokeDirectory = layout.buildDirectory.dir(
        "universal-smoke/fabric-$fabricMinecraftVersion"
    )
    val prepareUniversalFabricSmoke = tasks.register<Sync>("prepareUniversalFabricSmoke") {
        group = "verification"
        description = "Prepares an isolated production Fabric server using the universal jar."
        dependsOn(universalJar)
        into(universalFabricSmokeDirectory)
        from(universalJar.flatMap { it.archiveFile }) {
            into("mods")
        }
        from(universalFabricSmokeMods) {
            into("mods")
        }
        outputs.upToDateWhen { false }

        doLast {
            destinationDir.resolve("eula.txt").writeText("eula=false\n")
            destinationDir.resolve("server.properties").writeText("\n")
        }
    }

    val fabricRuntimeClasspath = fabricAdapterProject.configurations.getByName("runtimeClasspath")
    val productionFabricLauncherClasspath = fabricRuntimeClasspath.filter { candidate ->
        val path = candidate.invariantSeparatorsPath
        val name = candidate.name
        candidate.isFile
            && !path.contains("/.gradle/loom-cache/remapped_mods/")
            && !name.startsWith("minecraft-merged-")
            && !name.startsWith("fabric-bootstrap-")
            && !name.startsWith("gp3d-core-")
            && !name.startsWith("snakeyaml-")
            && !name.startsWith("dev-launch-injector-")
    }
    val minecraftServerJar = File(
        gradle.gradleUserHomeDir,
        "caches/fabric-loom/$fabricMinecraftVersion/minecraft-server.jar"
    )
    val universalFabricSmokeOutput = ByteArrayOutputStream()

    val checkUniversalFabricBoot = tasks.register<JavaExec>("checkUniversalFabricBoot") {
        group = "verification"
        description = "Boot-smokes the exact universal jar in a production-style Fabric server."
        dependsOn(prepareUniversalFabricSmoke)
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        })
        classpath = universalFabricSmokeMappings + productionFabricLauncherClasspath
        mainClass.set("net.fabricmc.loader.impl.launch.server.FabricServerLauncher")
        workingDir(universalFabricSmokeDirectory)
        systemProperty("fabric.gameJarPath", minecraftServerJar.absolutePath)
        args("nogui")
        isIgnoreExitValue = true
        standardOutput = universalFabricSmokeOutput
        errorOutput = universalFabricSmokeOutput
        outputs.upToDateWhen { false }

        doFirst {
            universalFabricSmokeOutput.reset()
            if (!minecraftServerJar.isFile) {
                throw GradleException(
                    "Loom did not provision the Minecraft $fabricMinecraftVersion server jar at " +
                        minecraftServerJar
                )
            }
        }

        doLast {
            val log = universalFabricSmokeOutput.toString(Charsets.UTF_8.name())
            val requiredMessages = listOf(
                "Loading Minecraft $fabricMinecraftVersion with Fabric Loader",
                "griefprevention3d ${project.version}",
                "GriefPrevention3D Fabric adapter loaded with native protection hooks.",
                "You need to agree to the EULA"
            )
            val missingMessages = requiredMessages.filterNot(log::contains)
            val forbiddenMessages = listOf(
                "Could not execute entrypoint",
                "Failed to start the minecraft server",
                "IllegalClassLoadError"
            ).filter(log::contains)
            val exitCode = executionResult.get().exitValue

            if (exitCode != 0 || missingMessages.isNotEmpty() || forbiddenMessages.isNotEmpty()) {
                throw GradleException(
                    "Universal Fabric boot smoke failed (exit $exitCode). " +
                        "Missing markers: $missingMessages; failure markers: $forbiddenMessages\n$log"
                )
            }
            logger.lifecycle(
                "Universal Fabric boot smoke passed with the exact ${universalJar.get().archiveFileName.get()}."
            )
        }
    }

    val checkUniversal = tasks.register("checkUniversal") {
        group = "verification"
        description = "Runs universal-jar structure, legacy isolation, and Fabric boot checks."
        dependsOn(checkUniversalJar, checkUniversalLegacyClassLoading, checkUniversalFabricBoot)
    }

    tasks.named("assemble") {
        dependsOn(universalJar)
    }
    tasks.named("check") {
        dependsOn(checkUniversal)
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
