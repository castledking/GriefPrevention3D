import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
}

group = "com.griefprevention"
version = providers.gradleProperty("version").get()

val minecraftVersion = providers.gradleProperty("minecraftVersion").get()
val paperMinecraftVersion = providers.gradleProperty("paperMinecraftVersion").get()
val targetJavaVersion = providers.gradleProperty("targetJavaVersion").get().toInt()
val gpFlagsRepo = providers.gradleProperty("gpFlagsRepo")
    .orElse("/mnt/storage/repos/GPFlags")

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.org/repository/maven-public")
    maven("https://repo.purpurmc.org/snapshots")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
}

java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

sourceSets {
    val main by getting

    create("compatLegacy") {
        java.srcDir("src/compat/legacy/java")
        resources.srcDir("src/compat/legacy/resources")
        compileClasspath += main.output + main.compileClasspath
        runtimeClasspath += output + compileClasspath
    }

    create("compatModern") {
        java.srcDir("src/compat/modern/java")
        resources.srcDir("src/compat/modern/resources")
        compileClasspath += main.output + main.compileClasspath
        runtimeClasspath += output + compileClasspath
    }
}

val paperApi by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val gpFlagsCompatCompileClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

configurations {
    named("compatLegacyCompileOnly") {
        extendsFrom(compileOnly.get())
    }
    named("compatModernCompileOnly") {
        extendsFrom(compileOnly.get())
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:$minecraftVersion-R0.1-SNAPSHOT")
    paperApi("io.papermc.paper:paper-api:$paperMinecraftVersion-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")

    testImplementation("org.spigotmc:spigot-api:$minecraftVersion-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
    testImplementation("org.mockito:mockito-junit-jupiter:5.16.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.jetbrains:annotations:26.0.2")

    gpFlagsCompatCompileClasspath("org.purpurmc.purpur:purpur-api:1.21.11-R0.1-SNAPSHOT")
    gpFlagsCompatCompileClasspath("com.gmail.nossr50.mcMMO:mcMMO:2.1.227") {
        exclude(group = "com.sk89q.worldguard", module = "worldguard-core")
        exclude(group = "com.sk89q.worldguard", module = "worldguard-legacy")
    }
    gpFlagsCompatCompileClasspath("org.jetbrains:annotations:24.1.0")
    gpFlagsCompatCompileClasspath("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    gpFlagsCompatCompileClasspath("io.lumine:Mythic-Dist:5.6.2")
    gpFlagsCompatCompileClasspath("org.apache.maven:maven-artifact:3.9.8")
    gpFlagsCompatCompileClasspath("com.github.PlaceholderAPI:PlaceholderAPI:2.11.5")
    gpFlagsCompatCompileClasspath("org.bstats:bstats-bukkit:3.0.2")
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(targetJavaVersion)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("project" to mapOf("version" to project.version.toString()))
        }
    }

    jar {
        archiveBaseName.set(project.name)
        archiveVersion.set(project.version.toString())
        from(sourceSets["compatLegacy"].output)
        from(sourceSets["compatModern"].output)
    }

    test {
        useJUnitPlatform()
        jvmArgs("-Xshare:off", "-Dnet.bytebuddy.experimental=true")
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    val compileGpFlagsCompatibility by registering(JavaCompile::class) {
        group = "verification"
        description = "Compiles GPFlags against this checkout's Bukkit-facing API."

        val repoDir = file(gpFlagsRepo.get())
        val sourceDir = repoDir.resolve("src/main/java")

        onlyIf {
            if (!sourceDir.isDirectory) {
                logger.lifecycle("Skipping GPFlags compatibility check; source directory not found: $sourceDir")
                false
            } else {
                true
            }
        }

        dependsOn(jar)
        source(sourceDir)
        source("src/addonCompatibility/stubs/java")
        include("**/*.java")
        destinationDirectory.set(layout.buildDirectory.dir("compat-check/gpflags/classes"))
        classpath = files(jar.flatMap { it.archiveFile }) + gpFlagsCompatCompileClasspath
        options.encoding = "UTF-8"
        options.release.set(9)
    }

    register("checkAddonCompatibility") {
        group = "verification"
        description = "Runs local addon compatibility checks for maintained GP3D addons."
        dependsOn(compileGpFlagsCompatibility)
    }
}

sourceSets {
    named("main") {
        compileClasspath += paperApi
    }
    named("test") {
        compileClasspath += paperApi
    }
    named("compatModern") {
        compileClasspath += paperApi
        runtimeClasspath += paperApi
    }
}
