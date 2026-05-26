plugins {
    java
    id("fabric-loom") version "1.13-SNAPSHOT"
}

group = rootProject.group
version = rootProject.version

base {
    archivesName.set("GriefPrevention3D-Fabric-1.21.11")
}

val fabricMinecraftVersion = providers.gradleProperty("fabricMinecraftVersion").get()
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion").get()
val fabricApiVersion = providers.gradleProperty("fabricApiVersion").get()
val fabricTargetJavaVersion = providers.gradleProperty("fabricTargetJavaVersion").get().toInt()

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    implementation(project(":gp3d-core"))
    include(project(":gp3d-core"))
    minecraft("com.mojang:minecraft:$fabricMinecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    compileOnly("org.jetbrains:annotations:26.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(fabricTargetJavaVersion))
    }
    withSourcesJar()
}

tasks {
    test {
        useJUnitPlatform()
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(fabricTargetJavaVersion)
    }

    processResources {
        inputs.property("version", project.version.toString())
        inputs.property("minecraftVersion", fabricMinecraftVersion)
        filesMatching("fabric.mod.json") {
            expand(
                "version" to project.version.toString(),
                "minecraftVersion" to fabricMinecraftVersion
            )
        }
    }
}
