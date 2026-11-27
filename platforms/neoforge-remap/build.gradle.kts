// Build-time tool, never shipped: rewrites the 1.21.11 NeoForge adapter to the Mojang names of an
// older release. See NeoForgeAdapterRemapper.
plugins {
    java
}

group = rootProject.group
version = rootProject.version

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    implementation("net.fabricmc:tiny-remapper:0.15.0")
    implementation("net.fabricmc:mapping-io:0.8.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
