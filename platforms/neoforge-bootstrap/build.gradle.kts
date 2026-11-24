plugins {
    `java-library`
}

group = rootProject.group
version = rootProject.version

val neoforgeBootstrapFmlVersion = providers.gradleProperty("neoforgeBootstrapFmlVersion").get()

repositories {
    mavenCentral()
    maven("https://maven.neoforged.net/releases/")
    maven("https://libraries.minecraft.net/")
    maven("https://maven.fabricmc.net/")
}

dependencies {
    compileOnly("net.neoforged.fancymodloader:loader:$neoforgeBootstrapFmlVersion")
    compileOnly("net.neoforged:bus:8.0.5")
    // The mixin gate implements Mixin's config-plugin interface, whose hooks mention ASM tree types.
    compileOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7") {
        isTransitive = false
    }
    compileOnly("org.ow2.asm:asm-tree:9.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}
