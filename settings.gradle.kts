pluginManagement {
    val loomVersion = providers.gradleProperty("loomVersion").get()
    val moddevVersion = providers.gradleProperty("moddevVersion").get()

    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        // Every Fabric module must share one Loom build: obfuscated targets apply the remapping
        // plugin and 26.x targets the unobfuscated one, but both come from the same artifact.
        id("net.fabricmc.fabric-loom-remap") version loomVersion
        id("net.fabricmc.fabric-loom") version loomVersion
        id("net.neoforged.moddev") version moddevVersion
        id("com.gradleup.shadow") version "9.6.1"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "GriefPrevention3D"

include("gp3d-core")

if (providers.gradleProperty("jitpack").orElse("false").get() != "true") {
    include("fabric-bootstrap")
    project(":fabric-bootstrap").projectDir = file("platforms/fabric-bootstrap")

    include("fabric-1.21.11")
    project(":fabric-1.21.11").projectDir = file("platforms/fabric-1.21.11")

    include("fabric-26.1")
    project(":fabric-26.1").projectDir = file("platforms/fabric-26.1")

    include("fabric-linkage-check")
    project(":fabric-linkage-check").projectDir = file("platforms/fabric-linkage-check")

    include("neoforge-remap")
    project(":neoforge-remap").projectDir = file("platforms/neoforge-remap")

    include("neoforge-bootstrap")
    project(":neoforge-bootstrap").projectDir = file("platforms/neoforge-bootstrap")

    include("neoforge-1.21.11")
    project(":neoforge-1.21.11").projectDir = file("platforms/neoforge-1.21.11")

    include("neoforge-1.21.10")
    project(":neoforge-1.21.10").projectDir = file("platforms/neoforge-1.21.10")

    include("neoforge-26.1")
    project(":neoforge-26.1").projectDir = file("platforms/neoforge-26.1")
}
