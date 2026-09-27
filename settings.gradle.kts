pluginManagement {
    val loomVersion = providers.gradleProperty("loomVersion").get()

    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        // Every Fabric module must share one Loom build: obfuscated targets apply the remapping
        // plugin and 26.x targets the unobfuscated one, but both come from the same artifact.
        id("net.fabricmc.fabric-loom-remap") version loomVersion
        id("net.fabricmc.fabric-loom") version loomVersion
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
}
