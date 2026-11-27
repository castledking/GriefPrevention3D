// NeoForge 1.21.3 through 1.21.10: the 1.21.11 adapter, remapped to 1.21.10's Mojang names.
//
// NeoForge runs Minecraft under Mojang's names, and 1.21.11 renamed hundreds of them
// (ResourceLocation became Identifier), so the 1.21.11 adapter cannot link on earlier releases.
// Its code is otherwise what the Fabric 1.21.11 adapter already runs on 1.21.2 and later, so rather
// than keep a second source tree this rewrites the compiled adapter through Fabric's intermediary
// names, which do not change between releases, and moves it under its own package. 1.21.3 through
// 1.21.10 share 1.21.10's names for everything the adapter touches; the NeoForge boot and linkage
// checks in platforms/fabric-universal.gradle.kts prove it on each of them.
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import groovy.json.JsonSlurper
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    java
    id("com.gradleup.shadow")
}

evaluationDependsOn(":neoforge-1.21.11")
evaluationDependsOn(":neoforge-remap")

group = rootProject.group
version = rootProject.version

val sourceMinecraft = providers.gradleProperty("minecraftVersion").get()
val targetMinecraft = providers.gradleProperty("neoforgeLegacyMappingsVersion").get()
val sourceTag = "mc1_21_11"
val targetTag = "mc" + targetMinecraft.replace('.', '_')
val sourcePackage = "com.griefprevention.neoforge.$sourceTag"
val targetPackage = "com.griefprevention.neoforge.$targetTag"
val sourceMixinConfig = "griefprevention3d-neoforge-$sourceTag.mixins.json"
val targetMixinConfig = "griefprevention3d-neoforge-$targetTag.mixins.json"

repositories {
    maven("https://maven.fabricmc.net/")
}

val sourceIntermediary = configurations.create("sourceIntermediary") { isTransitive = false }
val targetIntermediary = configurations.create("targetIntermediary") { isTransitive = false }
dependencies {
    sourceIntermediary("net.fabricmc:intermediary:$sourceMinecraft:v2")
    targetIntermediary("net.fabricmc:intermediary:$targetMinecraft:v2")
}

// Mojang publishes each release's names next to its server jar. They never change once published,
// so they are fetched once into the Gradle cache, like the boot checks' servers.
val mappingsRoot = File(gradle.gradleUserHomeDir, "caches/griefprevention3d/mojang-mappings")
fun mojangMappings(minecraft: String) = File(mappingsRoot, "$minecraft-server.txt")

val downloadMojangMappings = tasks.register("downloadMojangMappings") {
    group = "build"
    description = "Fetches Mojang's server mappings for $sourceMinecraft and $targetMinecraft."
    val releases = listOf(sourceMinecraft, targetMinecraft)
    outputs.files(releases.map(::mojangMappings))

    doLast {
        val missing = releases.filterNot { mojangMappings(it).isFile }
        if (missing.isEmpty()) {
            return@doLast
        }
        val manifest = URI("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").toURL()
            .openStream().use { JsonSlurper().parse(it) } as Map<*, *>
        for (minecraft in missing) {
            val entry = (manifest["versions"] as List<*>).map { it as Map<*, *> }.singleOrNull { it["id"] == minecraft }
                ?: throw GradleException("Mojang's version manifest has no Minecraft $minecraft.")
            val release = URI(entry["url"] as String).toURL().openStream().use { JsonSlurper().parse(it) } as Map<*, *>
            val mappings = ((release["downloads"] as Map<*, *>)["server_mappings"] as Map<*, *>?)?.get("url") as String?
                ?: throw GradleException("Minecraft $minecraft publishes no server mappings.")
            val file = mojangMappings(minecraft)
            file.parentFile.mkdirs()
            val partial = File(file.parentFile, file.name + ".part")
            URI(mappings).toURL().openStream().use { input ->
                Files.copy(input, partial.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            Files.move(partial.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

val sourceProject = project(":neoforge-1.21.11")
val sourceAdapterJar = sourceProject.tasks.named<AbstractArchiveTask>("universalAdapterJar")
// The 1.21.11 compile classpath carries Minecraft and NeoForge under 1.21.11's names, which the
// remapper reads to resolve members the adapter reaches through a subclass.
val sourceClasspath = sourceProject.the<SourceSetContainer>()["main"].compileClasspath
val remappedJar = layout.buildDirectory.file("remapped/adapter-$targetMinecraft.jar")

val remapAdapter = tasks.register<JavaExec>("remapAdapter") {
    group = "build"
    description = "Rewrites the NeoForge $sourceMinecraft adapter to Minecraft $targetMinecraft's Mojang names."
    dependsOn(downloadMojangMappings, sourceAdapterJar)
    inputs.file(sourceAdapterJar.flatMap { it.archiveFile })
    inputs.files(sourceIntermediary, targetIntermediary, sourceClasspath)
    inputs.files(downloadMojangMappings)
    outputs.file(remappedJar)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    classpath = project(":neoforge-remap").the<SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("com.griefprevention.neoforge.remap.NeoForgeAdapterRemapper")

    doFirst {
        val arguments = mutableListOf(
            "--input", sourceAdapterJar.get().archiveFile.get().asFile.absolutePath,
            "--output", remappedJar.get().asFile.absolutePath,
            "--source-mojang", mojangMappings(sourceMinecraft).absolutePath,
            "--source-intermediary", sourceIntermediary.singleFile.absolutePath,
            "--target-mojang", mojangMappings(targetMinecraft).absolutePath,
            "--target-intermediary", targetIntermediary.singleFile.absolutePath
        )
        sourceClasspath.filter { it.isFile }.forEach { arguments += listOf("--classpath", it.absolutePath) }
        args(arguments)
    }
}

val relocatedMixinConfig = tasks.register("relocatedMixinConfig") {
    val output = layout.buildDirectory.dir("generated/neoforge-universal-mixins")
    dependsOn(remapAdapter)
    inputs.file(remappedJar)
    outputs.dir(output)

    doLast {
        val original = zipTree(remappedJar.get().asFile).matching { include(sourceMixinConfig) }.singleFile.readText()
        val updated = original.replace("\"$sourcePackage.mixin\"", "\"$targetPackage.mixin\"")
        if (updated == original) {
            throw GradleException("$sourceMixinConfig does not name the $sourcePackage.mixin package.")
        }
        val file = output.get().file(targetMixinConfig).asFile
        file.parentFile.mkdirs()
        file.writeText(updated)
    }
}

tasks.named("jar") {
    enabled = false
}

tasks.named("shadowJar") {
    enabled = false
}

tasks.register<ShadowJar>("universalAdapterJar") {
    group = "build"
    description = "Packages the NeoForge 1.21.3-1.21.10 adapter for the universal jar."
    archiveBaseName.set("GriefPrevention3D-NeoForge-$targetMinecraft")
    archiveClassifier.set("universal-adapter")
    configurations.set(emptyList())
    dependsOn(remapAdapter, relocatedMixinConfig)
    from(remappedJar.map { zipTree(it.asFile) }) {
        exclude(sourceMixinConfig)
        exclude("META-INF/MANIFEST.MF")
    }
    from(relocatedMixinConfig)
    relocate(sourcePackage, targetPackage)
}
