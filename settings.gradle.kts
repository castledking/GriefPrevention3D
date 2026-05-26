pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "GriefPrevention3D"

include("gp3d-core")
include("fabric-1.21.11")
project(":fabric-1.21.11").projectDir = file("platforms/fabric-1.21.11")
