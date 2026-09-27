// Assembles the universal jar - the Bukkit plugin plus every Fabric adapter - and proves it works
// on every Minecraft release it claims. Applied from the root build script.
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

evaluationDependsOn(":fabric-bootstrap")
evaluationDependsOn(":fabric-1.21.11")
evaluationDependsOn(":fabric-26.1")
evaluationDependsOn(":fabric-linkage-check")

val toolchains = the<JavaToolchainService>()
val rootSourceSets = the<SourceSetContainer>()
val projectVersion = project.version.toString()
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion").get()
val fabricInstallerVersion = "1.1.2"
// The universal jar must stay small enough for every download host the project publishes to.
val universalJarSizeLimit = 5L * 1024L * 1024L

// Adapters bundled in the universal jar, newest first; the bootstrap starts the first whose range
// includes the running release. The 1.21.11 adapter keeps the source package so its public
// callbacks stay where 1.21.11 mods already find them; later adapters move under their tag.
class UniversalAdapter(
    val tag: String,
    val minecraftRange: String,
    val rootPackage: String,
    val mixinConfig: String,
    val javaMajor: Int
) {
    val entrypoint get() = "$rootPackage.GriefPreventionFabric"
    val mixinPackage get() = "$rootPackage.mixin"
    val rootPath get() = rootPackage.replace('.', '/') + "/"
}

val adapters = listOf(
    UniversalAdapter(
        "mc26_1",
        providers.gradleProperty("fabricMinecraft26Range").get(),
        "com.griefprevention.fabric.mc26_1",
        "griefprevention3d-mc26_1.mixins.json",
        21
    ),
    UniversalAdapter(
        "mc1_21_11",
        providers.gradleProperty("fabricMinecraftRange").get(),
        "com.griefprevention.fabric",
        "griefprevention3d.mixins.json",
        21
    )
)

// Every release the jar is proven on: booted in a real Fabric server, and linkage-checked so that
// code paths the boot never reaches still resolve. Each entry names the newest Fabric API for it.
class FabricTarget(val minecraft: String, val fabricApi: String, val java: Int, val adapter: String) {
    val taskSuffix get() = minecraft.replace('.', '_')
}

val fabricTargets = listOf(
    FabricTarget("1.21.11", "0.141.6+1.21.11", 21, "mc1_21_11"),
    FabricTarget("26.1", "0.145.1+26.1", 25, "mc26_1"),
    FabricTarget("26.1.1", "0.145.4+26.1.1", 25, "mc26_1"),
    FabricTarget("26.1.2", "0.155.3+26.1.2", 25, "mc26_1"),
    FabricTarget("26.2", "0.161.0+26.2", 25, "mc26_1"),
    FabricTarget("26.3", "0.161.0+26.3", 25, "mc26_1")
)

fun adapterFor(tag: String) = adapters.single { it.tag == tag }

// Packages the adapter's classes occupy in the universal jar, excluding any nested adapter.
fun adapterPackages(adapter: UniversalAdapter): Pair<String, List<String>> {
    val nested = adapters.filter { it !== adapter && it.rootPath.startsWith(adapter.rootPath) }
        .map { it.rootPath } + "com/griefprevention/fabric/bootstrap/"
    return adapter.rootPath to nested
}

// The root jar task keeps the GriefPrevention3D.jar name and stays Bukkit-only. The universal jar
// is named apart from it, so the release build can name the artifact it ships without a
// filesystem search that could pick either one.
val bukkitJar = tasks.named<Jar>("jar")
val bootstrapJar = project(":fabric-bootstrap").tasks.named<Jar>("jar")
val fabric1211RemapJar = project(":fabric-1.21.11").tasks.named<AbstractArchiveTask>("remapJar")
val fabric26AdapterJar = project(":fabric-26.1").tasks.named<AbstractArchiveTask>("universalAdapterJar")
val fabricModTemplate = rootProject.file("platforms/fabric-common/src/main/resources/fabric.mod.json")

val generateUniversalFabricMetadata = tasks.register("generateUniversalFabricMetadata") {
    group = "build"
    description = "Writes the universal jar's fabric.mod.json and adapter index."
    val output = layout.buildDirectory.dir("generated/universal-fabric")
    inputs.file(fabricModTemplate)
    inputs.property("version", projectVersion)
    inputs.property("adapters", adapters.map { "${it.tag}|${it.minecraftRange}|${it.rootPackage}|${it.mixinConfig}" })
    outputs.dir(output)

    doLast {
        val minimumJava = adapters.minOf { it.javaMajor }
        val template = fabricModTemplate.readText()
            .replace("\${version}", projectVersion)
            .replace("\${minecraftRange}", "")
            .replace("\${javaVersion}", minimumJava.toString())
        @Suppress("UNCHECKED_CAST")
        val metadata = JsonSlurper().parseText(template) as MutableMap<String, Any?>
        metadata["mixins"] = adapters.map { it.mixinConfig }
        @Suppress("UNCHECKED_CAST")
        val depends = metadata["depends"] as MutableMap<String, Any?>
        // A list means any one range must match.
        depends["minecraft"] = adapters.map { it.minecraftRange }

        val directory = output.get().asFile
        directory.resolve("fabric.mod.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)) + "\n")

        val index = directory.resolve("META-INF/griefprevention3d/fabric-adapters.properties")
        index.parentFile.mkdirs()
        index.writeText(buildString {
            appendLine("# Generated by the build: adapters in this universal jar, newest first.")
            appendLine("adapters=" + adapters.joinToString(",") { it.tag })
            for (adapter in adapters) {
                appendLine("adapter.${adapter.tag}.minecraft=${adapter.minecraftRange}")
                appendLine("adapter.${adapter.tag}.entrypoint=${adapter.entrypoint}")
                appendLine("adapter.${adapter.tag}.mixins=${adapter.mixinPackage}")
            }
        })
    }
}

val universalJar = tasks.register<Jar>("universalJar") {
    group = "build"
    description = "Assembles one Bukkit and Fabric distribution jar covering every supported release."
    dependsOn(bukkitJar, bootstrapJar, fabric1211RemapJar, fabric26AdapterJar, generateUniversalFabricMetadata)

    archiveBaseName.set("${project.name}-Universal")
    archiveVersion.set(projectVersion)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    from(bukkitJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) })
    from(bootstrapJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    from(fabric1211RemapJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
        exclude("fabric.mod.json")
        exclude("META-INF/MANIFEST.MF")
        exclude("META-INF/jars", "META-INF/jars/**")
        exclude("META-INF/griefprevention3d/**")
        exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
        exclude("com/griefprevention/fabric/bootstrap/**")
    }
    from(fabric26AdapterJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    from(generateUniversalFabricMetadata)
}

val checkUniversalJar = tasks.register("checkUniversalJar") {
    group = "verification"
    description = "Verifies the universal jar's metadata, adapter isolation, contents and size."
    dependsOn(universalJar)
    inputs.file(universalJar.flatMap { it.archiveFile })
    inputs.file(bukkitJar.flatMap { it.archiveFile })
    inputs.file(bootstrapJar.flatMap { it.archiveFile })
    inputs.file(fabric1211RemapJar.flatMap { it.archiveFile })
    inputs.file(fabric26AdapterJar.flatMap { it.archiveFile })

    doLast {
        val universalFile = universalJar.get().archiveFile.get().asFile
        if (universalFile.length() > universalJarSizeLimit) {
            throw GradleException(
                "Universal jar is ${universalFile.length()} bytes, over the $universalJarSizeLimit byte limit."
            )
        }

        fun bytes(archive: ZipFile, name: String): ByteArray {
            val entry = archive.getEntry(name) ?: throw GradleException("Missing $name in ${archive.name}")
            return archive.getInputStream(entry).use { it.readBytes() }
        }

        fun classMajorVersion(archive: ZipFile, name: String): Int =
            DataInputStream(bytes(archive, name).inputStream()).use { input ->
                if (input.readInt() != 0xCAFEBABE.toInt()) {
                    throw GradleException("$name is not a JVM class file.")
                }
                input.readUnsignedShort()
                input.readUnsignedShort()
            }

        fun requireIdentical(source: ZipFile, universal: ZipFile, filter: (String) -> Boolean) {
            val names = source.entries().asSequence().filter { !it.isDirectory && filter(it.name) }.map { it.name }.toList()
            if (names.isEmpty()) {
                throw GradleException("No entries selected from ${source.name}.")
            }
            for (name in names) {
                if (!bytes(source, name).contentEquals(bytes(universal, name))) {
                    throw GradleException("Universal assembly changed $name from ${source.name}.")
                }
            }
        }

        ZipFile(universalFile).use { universal ->
            val names = universal.entries().asSequence().map { it.name }.toList()
            val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (duplicates.isNotEmpty()) {
                throw GradleException("Duplicate universal-jar entries: $duplicates")
            }

            val required = mutableListOf(
                "plugin.yml",
                "fabric.mod.json",
                "META-INF/griefprevention3d/fabric-adapters.properties",
                "me/ryanhamshire/GriefPrevention/GriefPrevention.class",
                "com/griefprevention/fabric/bootstrap/UniversalFabricBootstrap.class",
                "com/griefprevention/fabric/bootstrap/AdapterMixinGate.class",
                "com/griefprevention/persistence/ClaimDocumentCodec.class",
                "com/griefprevention/internal/lib/snakeyaml/Yaml.class"
            )
            for (adapter in adapters) {
                required += adapter.entrypoint.replace('.', '/') + ".class"
                required += adapter.mixinConfig
                required += adapter.rootPath + "mixin/ServerExplosionMixin.class"
            }
            val missing = required.filterNot { it in names }
            if (missing.isNotEmpty()) {
                throw GradleException("Universal jar is missing required entries: $missing")
            }
            if (names.any { it.startsWith("META-INF/jars/") }) {
                throw GradleException("Universal jar contains a nested jar payload.")
            }
            if (names.any { it.startsWith("org/yaml/snakeyaml/") }) {
                throw GradleException("Universal jar exposes unrelocated SnakeYAML classes.")
            }

            @Suppress("UNCHECKED_CAST")
            val metadata = JsonSlurper().parse(bytes(universal, "fabric.mod.json")) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val entrypoints = (metadata["entrypoints"] as Map<String, List<Any?>>)["main"]
            if (entrypoints != listOf("com.griefprevention.fabric.bootstrap.UniversalFabricBootstrap")) {
                throw GradleException("Universal Fabric metadata does not start the Java 8 bootstrap: $entrypoints")
            }
            if (metadata.containsKey("jars")) {
                throw GradleException("Universal Fabric metadata still references nested jars.")
            }
            if (metadata["version"] != projectVersion) {
                throw GradleException("Universal Fabric metadata version does not match the build.")
            }
            if (metadata["mixins"] != adapters.map { it.mixinConfig }) {
                throw GradleException("Universal Fabric metadata lists mixins ${metadata["mixins"]}.")
            }
            @Suppress("UNCHECKED_CAST")
            val minecraft = (metadata["depends"] as Map<String, Any?>)["minecraft"]
            if (minecraft != adapters.map { it.minecraftRange }) {
                throw GradleException("Universal Fabric metadata supports Minecraft $minecraft.")
            }

            val index = java.util.Properties().apply {
                load(bytes(universal, "META-INF/griefprevention3d/fabric-adapters.properties").inputStream())
            }
            if (index.getProperty("adapters") != adapters.joinToString(",") { it.tag }) {
                throw GradleException("Adapter index lists ${index.getProperty("adapters")}.")
            }
            for (adapter in adapters) {
                val expected = mapOf(
                    "minecraft" to adapter.minecraftRange,
                    "entrypoint" to adapter.entrypoint,
                    "mixins" to adapter.mixinPackage
                )
                for ((field, value) in expected) {
                    if (index.getProperty("adapter.${adapter.tag}.$field") != value) {
                        throw GradleException("Adapter index has the wrong $field for ${adapter.tag}.")
                    }
                }

                @Suppress("UNCHECKED_CAST")
                val mixins = JsonSlurper().parse(bytes(universal, adapter.mixinConfig)) as Map<String, Any?>
                if (mixins["package"] != adapter.mixinPackage) {
                    throw GradleException("${adapter.mixinConfig} targets package ${mixins["package"]}.")
                }
                if (mixins["plugin"] != "com.griefprevention.fabric.bootstrap.AdapterMixinGate") {
                    throw GradleException("${adapter.mixinConfig} is not gated to its Minecraft releases.")
                }

                if (classMajorVersion(universal, adapter.entrypoint.replace('.', '/') + ".class") != 44 + adapter.javaMajor) {
                    throw GradleException("${adapter.tag} is not Java ${adapter.javaMajor} bytecode.")
                }

                // An adapter must only name Minecraft classes in its own runtime namespace:
                // intermediary (net/minecraft/class_N) before 26.1, Mojang names from 26.1 on.
                // Intermediary keeps the names of the few launch entry classes. Mixins are left to
                // the linkage check: a @Pseudo mixin lists other releases' class names on purpose.
                val (rootPath, nested) = adapterPackages(adapter)
                val intermediary = adapter.tag == "mc1_21_11"
                val foreign = if (intermediary) {
                    Regex("net/minecraft/(?!server/MinecraftServer\\b|server/Main\\b|client/Minecraft\\b|client/main/Main\\b)[a-z]+/")
                } else {
                    Regex("net/minecraft/class_[0-9]+")
                }
                for (name in names) {
                    if (!name.endsWith(".class") || !name.startsWith(rootPath) || nested.any { name.startsWith(it) }
                        || name.startsWith(rootPath + "mixin/")) {
                        continue
                    }
                    val constantPool = String(bytes(universal, name), Charsets.ISO_8859_1)
                    val hit = foreign.find(constantPool)
                    if (hit != null) {
                        throw GradleException("$name (${adapter.tag}) references ${hit.value} from the wrong namespace.")
                    }
                }
            }

            val pluginMetadata = bytes(universal, "plugin.yml").toString(Charsets.UTF_8)
            if (!pluginMetadata.contains("version: \"$projectVersion\"")) {
                throw GradleException("Universal Bukkit metadata version does not match the build.")
            }
            if (classMajorVersion(universal, "me/ryanhamshire/GriefPrevention/GriefPrevention.class") != 52) {
                throw GradleException("Bukkit entrypoint is not Java 8 bytecode.")
            }
            if (classMajorVersion(universal, "com/griefprevention/fabric/bootstrap/UniversalFabricBootstrap.class") != 52) {
                throw GradleException("Fabric bootstrap is not Java 8 bytecode.")
            }

            ZipFile(bukkitJar.get().archiveFile.get().asFile).use { source ->
                requireIdentical(source, universal) { it != "META-INF/MANIFEST.MF" }
            }
            ZipFile(bootstrapJar.get().archiveFile.get().asFile).use { source ->
                requireIdentical(source, universal) { it.endsWith(".class") }
            }
            ZipFile(fabric1211RemapJar.get().archiveFile.get().asFile).use { source ->
                requireIdentical(source, universal) {
                    (it.startsWith("com/griefprevention/fabric/") && !it.startsWith("com/griefprevention/fabric/bootstrap/"))
                        || it == "griefprevention3d.mixins.json"
                }
            }
            ZipFile(fabric26AdapterJar.get().archiveFile.get().asFile).use { source ->
                requireIdentical(source, universal) { it != "META-INF/MANIFEST.MF" }
            }
        }
        logger.lifecycle("Universal jar ${universalFile.name} is ${universalFile.length() / 1024} KiB.")
    }
}

val legacyClassLoadingChecks = listOf(
    "me.ryanhamshire.GriefPrevention.GriefPrevention",
    "me.ryanhamshire.GriefPrevention.BlockEventHandler",
    "me.ryanhamshire.GriefPrevention.EntityEventHandler",
    "me.ryanhamshire.GriefPrevention.EntityDamageHandler",
    "me.ryanhamshire.GriefPrevention.PlayerEventHandler",
    "me.ryanhamshire.GriefPrevention.PlayerEventHandler#construct",
    "me.ryanhamshire.GriefPrevention.RestoreNatureProcessingTask#restore-nature-biome"
)

val checkUniversalLegacyClassLoading = tasks.register<JavaExec>("checkUniversalLegacyClassLoading") {
    group = "verification"
    description = "Loads Bukkit entry classes from the universal jar on a real Java 8 runtime."
    dependsOn(universalJar)
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    classpath = files(universalJar.flatMap { it.archiveFile }) +
        rootSourceSets["compatLegacy"].runtimeClasspath.filter { it.isFile }
    mainClass.set("com.griefprevention.compat.legacy.LegacyClassLoadingCheck")
    args(legacyClassLoadingChecks)
}

// Fabric servers are installed once per release into the Gradle cache by the official Fabric
// server launcher, exactly as operators install them, and reused by later builds.
val fabricServersRoot = File(gradle.gradleUserHomeDir, "caches/griefprevention3d/fabric-servers")
val linkageTool = project(":fabric-linkage-check")
val bootTasks = mutableListOf<TaskProvider<*>>()
val linkageTasks = mutableListOf<TaskProvider<*>>()

for (target in fabricTargets) {
    val adapter = adapterFor(target.adapter)
    val serverDir = File(fabricServersRoot, "${target.minecraft}-loader$fabricLoaderVersion")
    val fabricApi = configurations.create("fabricApi${target.taskSuffix}") {
        isCanBeResolved = true
        isCanBeConsumed = false
        isTransitive = false
    }
    dependencies.add(fabricApi.name, "net.fabricmc.fabric-api:fabric-api:${target.fabricApi}")
    val output = ByteArrayOutputStream()

    val boot = tasks.register<JavaExec>("checkFabricBoot_${target.taskSuffix}") {
        group = "verification"
        description = "Boots the universal jar in a Fabric ${target.minecraft} server."
        dependsOn(universalJar)
        inputs.file(universalJar.flatMap { it.archiveFile })
        inputs.files(fabricApi)
        javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(target.java)) })
        classpath = files(File(serverDir, "fabric-server-launch.jar"))
        mainClass.set("net.fabricmc.installer.ServerLauncher")
        workingDir(serverDir)
        jvmArgs("-Dgriefprevention3d.verifyMixins=true")
        args("nogui")
        isIgnoreExitValue = true
        standardOutput = output
        errorOutput = output
        outputs.upToDateWhen { false }

        doFirst {
            serverDir.mkdirs()
            val launcher = File(serverDir, "fabric-server-launch.jar")
            if (!launcher.isFile) {
                val url = "https://meta.fabricmc.net/v2/versions/loader/${target.minecraft}/" +
                    "$fabricLoaderVersion/$fabricInstallerVersion/server/jar"
                val partial = File(serverDir, "fabric-server-launch.jar.part")
                URI(url).toURL().openStream().use { input -> Files.copy(input, partial.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                Files.move(partial.toPath(), launcher.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            for (transient in listOf("mods", "config", "plugins", "logs", "world", "crash-reports")) {
                File(serverDir, transient).deleteRecursively()
            }
            val mods = File(serverDir, "mods").apply { mkdirs() }
            universalJar.get().archiveFile.get().asFile.copyTo(File(mods, "GriefPrevention3D.jar"))
            fabricApi.files.forEach { it.copyTo(File(mods, it.name)) }
            File(serverDir, "eula.txt").writeText("eula=false\n")
            File(serverDir, "server.properties").writeText("\n")
            output.reset()
        }

        doLast {
            val log = output.toString(Charsets.UTF_8.name())
            val required = listOf(
                "Loading Minecraft ${target.minecraft} with Fabric Loader",
                "griefprevention3d $projectVersion",
                "GriefPrevention3D Fabric adapter loaded with native protection hooks.",
                "GriefPrevention3D verified ",
                "for ${adapter.tag} (Minecraft ${adapter.minecraftRange})",
                "You need to agree to the EULA"
            ).filterNot(log::contains)
            val failures = listOf(
                "Could not execute entrypoint",
                "Failed to start the minecraft server",
                "IllegalClassLoadError",
                "Mixin apply failed",
                "MixinApplyError",
                "InvalidMixinException",
                "NoSuchMethodError",
                "NoSuchFieldError",
                "NoClassDefFoundError",
                // Mixin reading a target class this release lacks: a @Pseudo name the gate should skip.
                "Error loading class"
            ).filter(log::contains)
            val exitCode = executionResult.get().exitValue
            if (exitCode != 0 || required.isNotEmpty() || failures.isNotEmpty()) {
                throw GradleException(
                    "Fabric ${target.minecraft} boot smoke failed (exit $exitCode). " +
                        "Missing markers: $required; failure markers: $failures\n$log"
                )
            }
            logger.lifecycle("Fabric ${target.minecraft} booted the universal jar with the ${adapter.tag} adapter.")
        }
    }
    bootTasks += boot

    linkageTasks += tasks.register<JavaExec>("checkFabricLinkage_${target.taskSuffix}") {
        group = "verification"
        description = "Resolves every Minecraft and Fabric reference of the ${adapter.tag} adapter on ${target.minecraft}."
        // The boot smoke installs the server this resolves against.
        dependsOn(boot, linkageTool.tasks.named("classes"))
        mustRunAfter(boot)
        javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(target.java)) })
        classpath = linkageTool.the<SourceSetContainer>()["main"].runtimeClasspath
        mainClass.set("com.griefprevention.fabric.linkage.FabricLinkageCheck")

        doFirst {
            // Loader remaps obfuscated releases to intermediary on first launch; 26.x runs as shipped.
            val remapped = File(serverDir, ".fabric/remappedJars").walkTopDown()
                .firstOrNull { it.name == "server-intermediary.jar" }
            val minecraftJar = remapped ?: File(serverDir, "versions/${target.minecraft}/server-${target.minecraft}.jar")
            if (!minecraftJar.isFile) {
                throw GradleException("Fabric ${target.minecraft} server was not installed at $serverDir.")
            }
            val (rootPath, nested) = adapterPackages(adapter)
            val arguments = mutableListOf(
                "--label", target.minecraft,
                "--jar", universalJar.get().archiveFile.get().asFile.absolutePath,
                "--package", rootPath,
                "--package", "com/griefprevention/fabric/bootstrap/",
                "--classpath", minecraftJar.absolutePath,
                "--classpath", File(serverDir, "libraries").absolutePath,
                // LuckPerms is optional and only touched after its API class is found.
                "--optional", "net/luckperms/",
                "--optional", "org/jetbrains/annotations/"
            )
            nested.filter { it != "com/griefprevention/fabric/bootstrap/" }.forEach { arguments += listOf("--exclude", it) }
            fabricApi.files.forEach { arguments += listOf("--classpath", it.absolutePath) }
            args(arguments)
        }
    }
}

val checkFabricBoot = tasks.register("checkFabricBoot") {
    group = "verification"
    description = "Boots the universal jar on every supported Fabric release."
    dependsOn(bootTasks)
}

val checkFabricLinkage = tasks.register("checkFabricLinkage") {
    group = "verification"
    description = "Linkage-checks each Fabric adapter against every release it claims."
    dependsOn(linkageTasks)
}

val checkUniversal = tasks.register("checkUniversal") {
    group = "verification"
    description = "Runs universal-jar structure, legacy isolation, Fabric boot and linkage checks."
    dependsOn(checkUniversalJar, checkUniversalLegacyClassLoading, checkFabricBoot, checkFabricLinkage)
}

tasks.named("assemble") {
    dependsOn(universalJar)
}
tasks.named("check") {
    dependsOn(checkUniversal)
}
