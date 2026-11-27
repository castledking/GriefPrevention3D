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
evaluationDependsOn(":neoforge-bootstrap")
evaluationDependsOn(":neoforge-1.21.11")
evaluationDependsOn(":neoforge-1.21.10")
evaluationDependsOn(":neoforge-26.1")

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

class UniversalNeoForgeAdapter(
    val tag: String,
    val minecraftRange: String,
    val neoforgeRange: String,
    val rootPackage: String,
    val mixinConfig: String,
    val javaMajor: Int
) {
    val entrypoint get() = "$rootPackage.GriefPreventionNeoForge"
    val mixinPackage get() = "$rootPackage.mixin"
    val rootPath get() = rootPackage.replace('.', '/') + "/"
}

val neoforgeAdapters = listOf(
    UniversalNeoForgeAdapter(
        "mc26_1",
        providers.gradleProperty("neoforgeMinecraft26Range").get(),
        providers.gradleProperty("neoforgeLoader26Range").get(),
        "com.griefprevention.neoforge.mc26_1",
        "griefprevention3d-neoforge-mc26_1.mixins.json",
        25
    ),
    UniversalNeoForgeAdapter(
        "mc1_21_11",
        providers.gradleProperty("neoforgeMinecraftRange").get(),
        providers.gradleProperty("neoforgeLoaderRange").get(),
        "com.griefprevention.neoforge.mc1_21_11",
        "griefprevention3d-neoforge-mc1_21_11.mixins.json",
        21
    ),
    // The 1.21.11 adapter remapped to 1.21.10's Mojang names; see platforms/neoforge-1.21.10.
    UniversalNeoForgeAdapter(
        "mc1_21_10",
        providers.gradleProperty("neoforgeLegacyMinecraftRange").get(),
        providers.gradleProperty("neoforgeLegacyLoaderRange").get(),
        "com.griefprevention.neoforge.mc1_21_10",
        "griefprevention3d-neoforge-mc1_21_10.mixins.json",
        21
    )
)

// Every release the jar is proven on: booted in a real Fabric server, and linkage-checked so that
// code paths the boot never reaches still resolve. Each entry names the newest Fabric API for it.
// Releases before 1.21.11 reach the calls 1.21.11 changed through FabricVersionCompat's intermediary
// lookups, which the boot verifies; its Modern half, never loaded there, is left out of their check.
class FabricTarget(
    val minecraft: String,
    val fabricApi: String,
    val java: Int,
    val adapter: String,
    val before1_21_11: Boolean = false
) {
    val taskSuffix get() = minecraft.replace('.', '_')
}

val fabricTargets = listOf(
    FabricTarget("1.21.2", "0.106.1+1.21.2", 21, "mc1_21_11", before1_21_11 = true),
    FabricTarget("1.21.3", "0.114.1+1.21.3", 21, "mc1_21_11", before1_21_11 = true),
    FabricTarget("1.21.4", "0.119.4+1.21.4", 21, "mc1_21_11", before1_21_11 = true),
    FabricTarget("1.21.5", "0.128.2+1.21.5", 21, "mc1_21_11", before1_21_11 = true),
    FabricTarget("1.21.6", "0.128.2+1.21.6", 21, "mc1_21_11", before1_21_11 = true),
    FabricTarget("1.21.7", "0.129.0+1.21.7", 21, "mc1_21_11", before1_21_11 = true),
    FabricTarget("1.21.8", "0.136.1+1.21.8", 21, "mc1_21_11", before1_21_11 = true),
    FabricTarget("1.21.9", "0.134.1+1.21.9", 21, "mc1_21_11", before1_21_11 = true),
    FabricTarget("1.21.10", "0.138.4+1.21.10", 21, "mc1_21_11", before1_21_11 = true),
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
val neoforgeBootstrapJar = project(":neoforge-bootstrap").tasks.named<Jar>("jar")
val neoforge1211AdapterJar = project(":neoforge-1.21.11").tasks.named<AbstractArchiveTask>("universalAdapterJar")
val neoforge12110AdapterJar = project(":neoforge-1.21.10").tasks.named<AbstractArchiveTask>("universalAdapterJar")
val neoforge26AdapterJar = project(":neoforge-26.1").tasks.named<AbstractArchiveTask>("universalAdapterJar")
val fabricModTemplate = rootProject.file("platforms/fabric-common/src/main/resources/fabric.mod.json")
val neoforgeModTemplate = rootProject.file("platforms/neoforge-loader/src/main/resources/META-INF/neoforge.mods.toml")

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

val generateUniversalNeoForgeMetadata = tasks.register("generateUniversalNeoForgeMetadata") {
    group = "build"
    description = "Writes the universal jar's META-INF/neoforge.mods.toml and NeoForge adapter index."
    val output = layout.buildDirectory.dir("generated/universal-neoforge")
    inputs.file(neoforgeModTemplate)
    inputs.property("version", projectVersion)
    inputs.property(
        "adapters",
        neoforgeAdapters.map { "${it.tag}|${it.minecraftRange}|${it.neoforgeRange}|${it.rootPackage}|${it.mixinConfig}" }
    )
    outputs.dir(output)

    doLast {
        val combinedMinecraftRange = neoforgeAdapters.reversed().joinToString(",") { it.minecraftRange }
        val combinedNeoforgeRange = neoforgeAdapters.reversed().joinToString(",") { it.neoforgeRange }
        val mixinsBlock = neoforgeAdapters.joinToString("\n\n") { "[[mixins]]\nconfig=\"${it.mixinConfig}\"" }

        val rendered = neoforgeModTemplate.readText()
            .replace("\${version}", projectVersion)
            .replace("\${minecraftRange}", combinedMinecraftRange)
            .replace("\${neoforgeRange}", combinedNeoforgeRange)
            .replace("\${mixinsBlock}", mixinsBlock)

        val directory = output.get().asFile
        val modsToml = directory.resolve("META-INF/neoforge.mods.toml")
        modsToml.parentFile.mkdirs()
        modsToml.writeText(rendered)

        val index = directory.resolve("META-INF/griefprevention3d/neoforge-adapters.properties")
        index.parentFile.mkdirs()
        index.writeText(buildString {
            appendLine("# Generated by the build: NeoForge adapters in this universal jar, newest first.")
            appendLine("adapters=" + neoforgeAdapters.joinToString(",") { it.tag })
            for (adapter in neoforgeAdapters) {
                appendLine("adapter.${adapter.tag}.minecraft=${adapter.minecraftRange}")
                appendLine("adapter.${adapter.tag}.entrypoint=${adapter.entrypoint}")
                appendLine("adapter.${adapter.tag}.mixins=${adapter.mixinPackage}")
            }
        })
    }
}

val universalJar = tasks.register<Jar>("universalJar") {
    group = "build"
    description = "Assembles one Bukkit, Fabric and NeoForge distribution jar covering every supported release."
    dependsOn(
        bukkitJar,
        bootstrapJar,
        fabric1211RemapJar,
        fabric26AdapterJar,
        neoforgeBootstrapJar,
        neoforge1211AdapterJar,
        neoforge12110AdapterJar,
        neoforge26AdapterJar,
        generateUniversalFabricMetadata,
        generateUniversalNeoForgeMetadata
    )

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
    from(neoforgeBootstrapJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    from(neoforge1211AdapterJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    from(neoforge12110AdapterJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    from(neoforge26AdapterJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    from(generateUniversalFabricMetadata)
    from(generateUniversalNeoForgeMetadata)
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
    inputs.file(neoforgeBootstrapJar.flatMap { it.archiveFile })
    inputs.file(neoforge1211AdapterJar.flatMap { it.archiveFile })
    inputs.file(neoforge12110AdapterJar.flatMap { it.archiveFile })
    inputs.file(neoforge26AdapterJar.flatMap { it.archiveFile })

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
                "META-INF/neoforge.mods.toml",
                "META-INF/griefprevention3d/fabric-adapters.properties",
                "META-INF/griefprevention3d/neoforge-adapters.properties",
                "me/ryanhamshire/GriefPrevention/GriefPrevention.class",
                "com/griefprevention/fabric/bootstrap/UniversalFabricBootstrap.class",
                "com/griefprevention/fabric/bootstrap/AdapterMixinGate.class",
                "com/griefprevention/neoforge/bootstrap/UniversalNeoForgeBootstrap.class",
                "com/griefprevention/neoforge/bootstrap/NeoForgeAdapterMixinGate.class",
                "com/griefprevention/persistence/ClaimDocumentCodec.class",
                "com/griefprevention/internal/lib/snakeyaml/Yaml.class"
            )
            for (adapter in adapters) {
                required += adapter.entrypoint.replace('.', '/') + ".class"
                required += adapter.mixinConfig
                required += adapter.rootPath + "mixin/ServerExplosionMixin.class"
            }
            for (adapter in neoforgeAdapters) {
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
            ZipFile(neoforgeBootstrapJar.get().archiveFile.get().asFile).use { source ->
                requireIdentical(source, universal) { it.endsWith(".class") }
            }
            ZipFile(neoforge1211AdapterJar.get().archiveFile.get().asFile).use { source ->
                requireIdentical(source, universal) { it != "META-INF/MANIFEST.MF" }
            }
            ZipFile(neoforge12110AdapterJar.get().archiveFile.get().asFile).use { source ->
                requireIdentical(source, universal) { it != "META-INF/MANIFEST.MF" }
            }
            ZipFile(neoforge26AdapterJar.get().archiveFile.get().asFile).use { source ->
                requireIdentical(source, universal) { it != "META-INF/MANIFEST.MF" }
            }

            val neoforgeMetadata = bytes(universal, "META-INF/neoforge.mods.toml").toString(Charsets.UTF_8)
            if (!neoforgeMetadata.contains("version=\"$projectVersion\"")) {
                throw GradleException("Universal NeoForge metadata version does not match the build.")
            }
            val neoforgeIndex = java.util.Properties().apply {
                load(bytes(universal, "META-INF/griefprevention3d/neoforge-adapters.properties").inputStream())
            }
            if (neoforgeIndex.getProperty("adapters") != neoforgeAdapters.joinToString(",") { it.tag }) {
                throw GradleException("NeoForge adapter index lists ${neoforgeIndex.getProperty("adapters")}.")
            }
            for (adapter in neoforgeAdapters) {
                if (!neoforgeMetadata.contains("config=\"${adapter.mixinConfig}\"")) {
                    throw GradleException("Universal NeoForge metadata does not declare mixin config ${adapter.mixinConfig}.")
                }
                @Suppress("UNCHECKED_CAST")
                val mixins = JsonSlurper().parse(bytes(universal, adapter.mixinConfig)) as Map<String, Any?>
                if (mixins["package"] != adapter.mixinPackage) {
                    throw GradleException("${adapter.mixinConfig} targets package ${mixins["package"]}.")
                }
                if (mixins["plugin"] != "com.griefprevention.neoforge.bootstrap.NeoForgeAdapterMixinGate") {
                    throw GradleException("${adapter.mixinConfig} is not gated by NeoForgeAdapterMixinGate.")
                }
                if (classMajorVersion(universal, adapter.entrypoint.replace('.', '/') + ".class") != 44 + adapter.javaMajor) {
                    throw GradleException("NeoForge ${adapter.tag} is not Java ${adapter.javaMajor} bytecode.")
                }
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
                // EconomyCraft is discovered only when its mod is installed.
                "--optional", "com/reazip/economycraft/",
                "--optional", "org/jetbrains/annotations/"
            )
            nested.filter { it != "com/griefprevention/fabric/bootstrap/" }.forEach { arguments += listOf("--exclude", it) }
            if (target.before1_21_11) {
                arguments += listOf("--exclude", rootPath + "FabricVersionCompat\$Modern")
            }
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

// Every NeoForge release the jar claims, each with the newest NeoForge build for it. NeoForge runs
// Minecraft under Mojang's names, so each adapter is proven against the names of every release it
// serves: booted in a real server, then linkage-checked against that server's patched Minecraft.
// 1.21.2, 26.1 and 26.1.1 are absent: NeoForge only shipped early betas for them, which the adapters
// do not claim (see gradle.properties).
class NeoForgeTarget(val minecraft: String, val neoforge: String, val java: Int, val adapter: String) {
    val taskSuffix get() = minecraft.replace('.', '_')
}

val neoforgeTargets = listOf(
    NeoForgeTarget("1.21.3", "21.3.100", 21, "mc1_21_10"),
    NeoForgeTarget("1.21.4", "21.4.161", 21, "mc1_21_10"),
    NeoForgeTarget("1.21.5", "21.5.101", 21, "mc1_21_10"),
    NeoForgeTarget("1.21.6", "21.6.20-beta", 21, "mc1_21_10"),
    NeoForgeTarget("1.21.7", "21.7.25-beta", 21, "mc1_21_10"),
    NeoForgeTarget("1.21.8", "21.8.57", 21, "mc1_21_10"),
    NeoForgeTarget("1.21.9", "21.9.16-beta", 21, "mc1_21_10"),
    NeoForgeTarget("1.21.10", "21.10.64", 21, "mc1_21_10"),
    NeoForgeTarget("1.21.11", "21.11.48", 21, "mc1_21_11"),
    NeoForgeTarget("26.1.2", "26.1.2.130", 25, "mc26_1"),
    NeoForgeTarget("26.2", "26.2.0.95", 25, "mc26_1"),
    NeoForgeTarget("26.3", "26.3.0.118", 25, "mc26_1")
)

fun neoforgeAdapterFor(tag: String) = neoforgeAdapters.single { it.tag == tag }

// NeoForge servers are installed once per release into the Gradle cache by the official NeoForge
// installer, as operators install them. NeoForge checks the EULA before it loads mods, so unlike the
// Fabric smoke the server starts fully, on a small flat world, and is stopped from its console.
val neoforgeServersRoot = File(gradle.gradleUserHomeDir, "caches/griefprevention3d/neoforge-servers")
val neoforgeBootTasks = mutableListOf<TaskProvider<*>>()
val neoforgeLinkageTasks = mutableListOf<TaskProvider<*>>()

for (target in neoforgeTargets) {
    val adapter = neoforgeAdapterFor(target.adapter)
    val serverDir = File(neoforgeServersRoot, "${target.minecraft}-${target.neoforge}")
    val launcher = toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(target.java)) }
    val argsFile = "libraries/net/neoforged/neoforge/${target.neoforge}/unix_args.txt"

    val boot = tasks.register("checkNeoForgeBoot_${target.taskSuffix}") {
        group = "verification"
        description = "Boots the universal jar in a NeoForge ${target.neoforge} (Minecraft ${target.minecraft}) server."
        dependsOn(universalJar)
        inputs.file(universalJar.flatMap { it.archiveFile })
        outputs.upToDateWhen { false }

        doLast {
            val javaExecutable = launcher.get().executablePath.asFile.absolutePath
            serverDir.mkdirs()
            if (!File(serverDir, argsFile).isFile) {
                val installer = File(serverDir, "installer.jar")
                val url = "https://maven.neoforged.net/releases/net/neoforged/neoforge/${target.neoforge}/" +
                    "neoforge-${target.neoforge}-installer.jar"
                val partial = File(serverDir, "installer.jar.part")
                URI(url).toURL().openStream().use { input -> Files.copy(input, partial.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                Files.move(partial.toPath(), installer.toPath(), StandardCopyOption.REPLACE_EXISTING)
                val install = ProcessBuilder(javaExecutable, "-jar", installer.name, "--installServer", ".")
                    .directory(serverDir).redirectErrorStream(true).start()
                val installLog = install.inputStream.bufferedReader().readText()
                if (install.waitFor() != 0 || !File(serverDir, argsFile).isFile) {
                    throw GradleException("NeoForge ${target.neoforge} server install failed.\n$installLog")
                }
            }
            for (transient in listOf("mods", "config", "defaultconfigs", "plugins", "logs", "world", "crash-reports")) {
                File(serverDir, transient).deleteRecursively()
            }
            val mods = File(serverDir, "mods").apply { mkdirs() }
            universalJar.get().archiveFile.get().asFile.copyTo(File(mods, "GriefPrevention3D.jar"))
            File(serverDir, "eula.txt").writeText("eula=true\n")
            val port = java.net.ServerSocket(0).use { it.localPort }
            File(serverDir, "server.properties").writeText(
                "level-type=minecraft\\:flat\ngenerate-structures=false\nonline-mode=false\nserver-port=$port\n"
            )

            val process = ProcessBuilder(javaExecutable, "-Dgriefprevention3d.verifyMixins=true", "@$argsFile", "nogui")
                .directory(serverDir).redirectErrorStream(true).start()
            // A server that neither finishes starting nor fails is stopped rather than left running.
            val watchdog = Thread {
                try {
                    if (!process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES)) {
                        process.destroyForcibly()
                    }
                } catch (ignored: InterruptedException) {
                }
            }.apply { isDaemon = true; start() }
            val log = StringBuilder()
            var stopping = false
            process.inputStream.bufferedReader().forEachLine { line ->
                log.appendLine(line)
                if (!stopping && line.contains("Done (")) {
                    stopping = true
                    runCatching {
                        process.outputStream.write("stop\n".toByteArray())
                        process.outputStream.flush()
                    }
                }
            }
            val exitCode = process.waitFor()
            watchdog.interrupt()

            // Netty probes its macOS-only kqueue transport from a log4j appender on some releases.
            val text = log.lineSequence().filterNot { it.contains("io.netty.channel.kqueue") }.joinToString("\n")
            val required = listOf(
                "griefprevention3d",
                "GriefPrevention3D NeoForge adapter loaded with native protection hooks.",
                "GriefPrevention3D verified ",
                "for ${adapter.tag} (${adapter.minecraftRange})",
                "Done ("
            ).filterNot(text::contains)
            val failures = listOf(
                "Failed to create mod instance",
                "Failed to start the minecraft server",
                "Mixin apply failed",
                "MixinApplyError",
                "InvalidMixinException",
                "NoSuchMethodError",
                "NoSuchFieldError",
                "NoClassDefFoundError",
                "IllegalClassLoadError",
                "Error loading class"
            ).filter(text::contains)
            if (exitCode != 0 || !stopping || required.isNotEmpty() || failures.isNotEmpty()) {
                throw GradleException(
                    "NeoForge ${target.minecraft} boot smoke failed (exit $exitCode). " +
                        "Missing markers: $required; failure markers: $failures\n$text"
                )
            }
            logger.lifecycle("NeoForge ${target.minecraft} booted the universal jar with the ${adapter.tag} adapter.")
        }
    }
    neoforgeBootTasks += boot

    neoforgeLinkageTasks += tasks.register<JavaExec>("checkNeoForgeLinkage_${target.taskSuffix}") {
        group = "verification"
        description = "Resolves every Minecraft and NeoForge reference of the ${adapter.tag} adapter on ${target.minecraft}."
        // The boot smoke installs the server this resolves against.
        dependsOn(boot, linkageTool.tasks.named("classes"))
        mustRunAfter(boot)
        javaLauncher.set(launcher)
        classpath = linkageTool.the<SourceSetContainer>()["main"].runtimeClasspath
        mainClass.set("com.griefprevention.fabric.linkage.FabricLinkageCheck")

        doFirst {
            val libraries = File(serverDir, "libraries")
            // The installer writes Minecraft patched by NeoForge beside the vanilla jars it came from;
            // the first copy of a class wins, so the patched jar leads the classpath.
            val patched = libraries.walkTopDown()
                .filter { it.isFile && it.extension == "jar" && it.invariantSeparatorsPath.contains("/net/neoforged/") }
                .sortedBy { it.path }
                .firstOrNull { jar -> ZipFile(jar).use { it.getEntry("net/minecraft/server/MinecraftServer.class") != null } }
                ?: throw GradleException("NeoForge ${target.minecraft} server was not installed at $serverDir.")
            val arguments = mutableListOf(
                "--label", "NeoForge ${target.minecraft}",
                "--jar", universalJar.get().archiveFile.get().asFile.absolutePath,
                "--package", adapter.rootPath,
                "--package", "com/griefprevention/neoforge/bootstrap/",
                "--classpath", patched.absolutePath,
                "--classpath", libraries.absolutePath,
                // NeoForge 26.2 merged this event into EntityInteract; it is only used once found.
                "--optional", "net/neoforged/neoforge/event/entity/player/PlayerInteractEvent\$EntityInteractSpecific",
                "--optional", "net/luckperms/",
                "--optional", "com/reazip/economycraft/",
                "--optional", "org/jetbrains/annotations/"
            )
            if (adapter.tag == "mc1_21_10") {
                // Calls 1.21.11 introduced, which FabricVersionCompat never loads before it.
                arguments += listOf("--exclude", adapter.rootPath + "FabricVersionCompat\$Modern")
            }
            args(arguments)
        }
    }
}

val checkNeoForgeBoot = tasks.register("checkNeoForgeBoot") {
    group = "verification"
    description = "Boots the universal jar on every supported NeoForge release."
    dependsOn(neoforgeBootTasks)
}

val checkNeoForgeLinkage = tasks.register("checkNeoForgeLinkage") {
    group = "verification"
    description = "Linkage-checks each NeoForge adapter against every release it claims."
    dependsOn(neoforgeLinkageTasks)
}

val checkUniversal = tasks.register("checkUniversal") {
    group = "verification"
    description = "Runs universal-jar structure, legacy isolation, Fabric and NeoForge boot and linkage checks."
    dependsOn(
        checkUniversalJar,
        checkUniversalLegacyClassLoading,
        checkFabricBoot,
        checkFabricLinkage,
        checkNeoForgeBoot,
        checkNeoForgeLinkage
    )
}

tasks.named("assemble") {
    dependsOn(universalJar)
}
tasks.named("check") {
    dependsOn(checkUniversal)
}
