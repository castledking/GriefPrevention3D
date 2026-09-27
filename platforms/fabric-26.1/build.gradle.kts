import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

// One adapter binary for every 26.x release in fabricMinecraft26Range. Minecraft is unobfuscated
// from 26.1 on, so this module compiles against Mojang names and ships them without remapping.
// It compiles against the oldest release in the range; checkFabricLinkage then proves every
// Minecraft and Fabric API member it links against still exists in each newer release.
plugins {
    java
    id("net.fabricmc.fabric-loom")
    id("com.gradleup.shadow")
}

evaluationDependsOn(":fabric-bootstrap")

val fabricMinecraftVersion = providers.gradleProperty("fabricMinecraft26Version").get()
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion").get()
val fabricApiVersion = providers.gradleProperty("fabricApi26Version").get()
val adapterTag = "mc26_1"
val relocatedPackage = "com.griefprevention.fabric.$adapterTag"

extra["fabricAdapterTag"] = adapterTag
extra["fabricMinecraftRange"] = providers.gradleProperty("fabricMinecraft26Range").get()
// Minecraft 26.x itself requires Java 25.
extra["fabricJavaVersion"] = 25
apply(from = rootProject.file("platforms/fabric-module.gradle.kts"))
@Suppress("UNCHECKED_CAST")
val fabricApiModules = extra["fabricApiModules"] as List<String>

base {
    archivesName.set("GriefPrevention3D-Fabric-26.1")
}

dependencies {
    implementation(project(":fabric-bootstrap"))
    implementation(project(":gp3d-core"))
    include(project(path = ":gp3d-core", configuration = "shadowRuntimeElements"))
    minecraft("com.mojang:minecraft:$fabricMinecraftVersion")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    for (module in fabricApiModules) {
        implementation(fabricApi.module(module, fabricApiVersion))
    }
    compileOnly("net.luckperms:api:5.5")
    // Bundled with Fabric Loader at runtime; the protection mixins use its wrap and local sugar.
    compileOnly("io.github.llamalad7:mixinextras-fabric:0.5.0")
    compileOnly("org.jetbrains:annotations:26.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

tasks {
    withType<JavaCompile>().configureEach {
        // Java 21 bytecode keeps the adapter's mixin config at the same compatibility level as the
        // 1.21.11 adapter's; both configs load on every server the universal jar runs on.
        options.release.set(21)
    }

    // The default fat jar would bundle Minecraft itself; nothing needs it.
    named("shadowJar") {
        enabled = false
    }

    jar {
        val bootstrapJar = project(":fabric-bootstrap").tasks.named<Jar>("jar")
        dependsOn(bootstrapJar)
        from(bootstrapJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
            exclude("META-INF/MANIFEST.MF")
        }
    }

    val relocatedMixinConfig = register("relocatedMixinConfig") {
        val source = rootProject.file("platforms/fabric-common/src/main/resources/griefprevention3d.mixins.json")
        val output = layout.buildDirectory.dir("generated/universal-mixins")
        inputs.file(source)
        inputs.property("relocatedPackage", relocatedPackage)
        outputs.dir(output)

        doLast {
            val original = source.readText()
            val relocated = original.replace(
                "\"package\": \"com.griefprevention.fabric.mixin\"",
                "\"package\": \"$relocatedPackage.mixin\""
            )
            if (relocated == original) {
                throw GradleException("Could not relocate the mixin package in $source.")
            }
            output.get().file("griefprevention3d-$adapterTag.mixins.json").asFile.writeText(relocated)
        }
    }

    // This adapter's classes as the universal jar carries them: moved under their own package so
    // they sit beside the 1.21.11 adapter, which keeps com.griefprevention.fabric.
    register<ShadowJar>("universalAdapterJar") {
        group = "build"
        description = "Packages the relocated 26.x adapter for the universal jar."
        archiveClassifier.set("universal-adapter")
        configurations.set(emptyList())
        from(sourceSets.main.get().output) {
            exclude("fabric.mod.json")
            exclude("griefprevention3d.mixins.json")
            exclude("META-INF/griefprevention3d/**")
        }
        from(relocatedMixinConfig)
        relocate("com.griefprevention.fabric", relocatedPackage) {
            exclude("com.griefprevention.fabric.bootstrap.**")
        }
    }
}
