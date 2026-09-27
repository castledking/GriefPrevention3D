plugins {
    java
    id("net.fabricmc.fabric-loom-remap")
}

evaluationDependsOn(":fabric-bootstrap")

val fabricMinecraftVersion = providers.gradleProperty("fabricMinecraftVersion").get()
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion").get()
val fabricApiVersion = providers.gradleProperty("fabricApiVersion").get()
val fabricTargetJavaVersion = providers.gradleProperty("fabricTargetJavaVersion").get().toInt()

extra["fabricAdapterTag"] = "mc1_21_11"
extra["fabricMinecraftRange"] = providers.gradleProperty("fabricMinecraftRange").get()
extra["fabricJavaVersion"] = fabricTargetJavaVersion
apply(from = rootProject.file("platforms/fabric-module.gradle.kts"))
@Suppress("UNCHECKED_CAST")
val fabricApiModules = extra["fabricApiModules"] as List<String>

base {
    archivesName.set("GriefPrevention3D-Fabric-1.21.11")
}

dependencies {
    implementation(project(":fabric-bootstrap"))
    implementation(project(":gp3d-core"))
    include(project(path = ":gp3d-core", configuration = "shadowRuntimeElements"))
    minecraft("com.mojang:minecraft:$fabricMinecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    for (module in fabricApiModules) {
        modImplementation(fabricApi.module(module, fabricApiVersion))
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
        languageVersion.set(JavaLanguageVersion.of(fabricTargetJavaVersion))
    }
    withSourcesJar()
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release.set(fabricTargetJavaVersion)
    }

    jar {
        val bootstrapJar = project(":fabric-bootstrap").tasks.named<Jar>("jar")
        dependsOn(bootstrapJar)
        from(bootstrapJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
            exclude("META-INF/MANIFEST.MF")
        }
    }
}
