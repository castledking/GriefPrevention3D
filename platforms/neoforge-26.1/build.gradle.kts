import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("net.neoforged.moddev")
    id("com.gradleup.shadow")
}

evaluationDependsOn(":neoforge-bootstrap")
evaluationDependsOn(":gp3d-core")

val neoforgeVersion = providers.gradleProperty("neoforge26Version").get()

extra["neoforgeAdapterTag"] = "mc26_1"
extra["neoforgeMinecraftRange"] = providers.gradleProperty("neoforgeMinecraft26Range").get()
extra["neoforgeLoaderRange"] = providers.gradleProperty("neoforgeLoader26Range").get()
extra["neoforgeJavaVersion"] = 25
apply(from = rootProject.file("platforms/neoforge-module.gradle.kts"))

base {
    archivesName.set("GriefPrevention3D-NeoForge-26.1")
}

neoForge {
    version = neoforgeVersion
    addModdingDependenciesTo(sourceSets.test.get())

    runs {
        create("server") {
            server()
            gameDirectory = file("run")
            programArgument("--nogui")
            systemProperty("griefprevention3d.verifyMixins", "true")
        }
    }

    mods {
        create("griefprevention3d") {
            sourceSet(sourceSets.main.get())
            sourceSet(project(":neoforge-bootstrap").sourceSets.main.get())
            sourceSet(project(":gp3d-core").sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(project(":neoforge-bootstrap"))
    implementation(project(":gp3d-core"))
    compileOnly("net.luckperms:api:5.5")
    compileOnly("maven.modrinth:economycraft:xEFUTMcx")
    compileOnly("org.jetbrains:annotations:26.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release.set(25)
    }

    jar {
        val bootstrapJar = project(":neoforge-bootstrap").tasks.named<Jar>("jar")
        val coreShadowJar = project(":gp3d-core").tasks.named<org.gradle.api.tasks.bundling.AbstractArchiveTask>("shadowJar")
        dependsOn(bootstrapJar, coreShadowJar)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(bootstrapJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
            exclude("META-INF/MANIFEST.MF")
        }
        from(coreShadowJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
            exclude("META-INF/MANIFEST.MF")
        }
    }

    register<ShadowJar>("universalAdapterJar") {
        group = "build"
        description = "Packages the relocated NeoForge 26.x adapter for the universal jar."
        archiveClassifier.set("universal-adapter")
        configurations.set(emptyList())
        val standaloneMixinConfig = project.extra["neoforgeStandaloneMixinConfig"] as String
        val relocatedPackage = project.extra["neoforgeRelocatedPackage"] as String
        val relocatedMixinConfigTask = project.extra["neoforgeRelocatedMixinConfigTask"] as TaskProvider<*>
        dependsOn(relocatedMixinConfigTask)
        from(sourceSets.main.get().output) {
            exclude("META-INF/neoforge.mods.toml")
            exclude("META-INF/griefprevention3d/**")
            exclude(standaloneMixinConfig)
        }
        from(relocatedMixinConfigTask)
        relocate("com.griefprevention.fabric", relocatedPackage) {
            exclude("com.griefprevention.neoforge.bootstrap.**")
        }
    }
}
