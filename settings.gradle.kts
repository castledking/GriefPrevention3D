pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "GriefPrevention3D"

include("gp3d-core")

if (providers.gradleProperty("jitpack").orElse("false").get() != "true") {
    include("fabric-1.21.11")
    project(":fabric-1.21.11").projectDir = file("platforms/fabric-1.21.11")
}
