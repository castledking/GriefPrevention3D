pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
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
}
