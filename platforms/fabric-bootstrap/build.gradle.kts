plugins {
    `java-library`
}

group = rootProject.group
version = rootProject.version

val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion").get()

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    compileOnly("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    // The mixin gate implements Mixin's config-plugin interface, whose hooks mention ASM tree types.
    compileOnly("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7") {
        isTransitive = false
    }
    compileOnly("org.ow2.asm:asm-tree:9.8")
    testImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-Xlint:-options")
}

tasks.test {
    useJUnitPlatform()
}
