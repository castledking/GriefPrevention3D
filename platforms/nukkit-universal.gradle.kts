// Adds the Nukkit adapter to the universal jar and proves Nukkit loads it. Applied from the root build
// script after fabric-universal.gradle.kts, which defines the universal jar.
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.net.DatagramSocket
import java.time.Duration
import java.util.zip.ZipFile

evaluationDependsOn(":nukkit")

val toolchains = the<JavaToolchainService>()
val projectVersion = project.version.toString()
val nukkitVersion = providers.gradleProperty("nukkitVersion").get()
val nukkitEntrypoint = "com/griefprevention/nukkit/GriefPreventionNukkit.class"
val nukkitJar = project(":nukkit").tasks.named<Jar>("jar")
val universalJar = tasks.named<Jar>("universalJar")

universalJar.configure {
    dependsOn(nukkitJar)
    from(nukkitJar.flatMap { it.archiveFile }.map { zipTree(it.asFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
}

val checkUniversalNukkit = tasks.register("checkUniversalNukkit") {
    group = "verification"
    description = "Verifies the universal jar carries the Nukkit descriptor and adapter unchanged."
    dependsOn(universalJar)
    inputs.file(universalJar.flatMap { it.archiveFile })
    inputs.file(nukkitJar.flatMap { it.archiveFile })

    doLast {
        fun bytes(archive: ZipFile, name: String): ByteArray {
            val entry = archive.getEntry(name) ?: throw GradleException("Missing $name in ${archive.name}")
            return archive.getInputStream(entry).use { it.readBytes() }
        }

        ZipFile(universalJar.get().archiveFile.get().asFile).use { universal ->
            ZipFile(nukkitJar.get().archiveFile.get().asFile).use { source ->
                val names = source.entries().asSequence().filter { !it.isDirectory && it.name != "META-INF/MANIFEST.MF" }
                    .map { it.name }.toList()
                if ("nukkit.yml" !in names || nukkitEntrypoint !in names) {
                    throw GradleException("The Nukkit jar lacks nukkit.yml or its entrypoint: $names")
                }
                for (name in names) {
                    if (!name.startsWith("com/griefprevention/nukkit/") && name != "nukkit.yml") {
                        throw GradleException("The Nukkit jar would add $name outside its package to the universal jar.")
                    }
                    if (!bytes(source, name).contentEquals(bytes(universal, name))) {
                        throw GradleException("Universal assembly changed $name from ${source.name}.")
                    }
                }
            }

            val descriptor = bytes(universal, "nukkit.yml").toString(Charsets.UTF_8)
            if (!descriptor.contains("version: \"$projectVersion\"")) {
                throw GradleException("Universal Nukkit metadata version does not match the build.")
            }
            DataInputStream(bytes(universal, nukkitEntrypoint).inputStream()).use { input ->
                input.readInt()
                input.readUnsignedShort()
                if (input.readUnsignedShort() != 52) {
                    throw GradleException("Nukkit entrypoint is not Java 8 bytecode.")
                }
            }
        }
    }
}

// A real Nukkit server, resolved from the same pinned build the adapter compiles against.
val nukkitServer = configurations.create("nukkitServer") {
    isCanBeResolved = true
    isCanBeConsumed = false
}
repositories {
    maven("https://repo.opencollab.dev/maven-releases/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
}
dependencies.add(nukkitServer.name, "cn.nukkit:nukkit:$nukkitVersion")

val nukkitServerDir = layout.buildDirectory.dir("nukkit-server")
val nukkitBootOutput = ByteArrayOutputStream()

val checkNukkitBoot = tasks.register<JavaExec>("checkNukkitBoot") {
    group = "verification"
    description = "Boots the universal jar in a Nukkit server on Java 8."
    dependsOn(universalJar)
    inputs.file(universalJar.flatMap { it.archiveFile })
    inputs.files(nukkitServer)
    javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    classpath = nukkitServer
    mainClass.set("cn.nukkit.Nukkit")
    args("--language", "eng")
    workingDir(nukkitServerDir)
    // Nukkit discards console input that arrives before it has started, so "stop" waits for "Done (".
    standardInput = object : InputStream() {
        private val command = "stop\n".toByteArray()
        private var sent = 0

        override fun read(): Int {
            while (sent == 0 && !nukkitBootOutput.toString(Charsets.UTF_8.name()).contains("Done (")) {
                Thread.sleep(200)
            }
            return if (sent < command.size) command[sent++].toInt() else -1
        }
    }
    timeout.set(Duration.ofMinutes(5))
    isIgnoreExitValue = true
    standardOutput = nukkitBootOutput
    errorOutput = nukkitBootOutput
    outputs.upToDateWhen { false }

    doFirst {
        val serverDir = nukkitServerDir.get().asFile
        serverDir.deleteRecursively()
        val plugins = File(serverDir, "plugins").apply { mkdirs() }
        universalJar.get().archiveFile.get().asFile.copyTo(File(plugins, "GriefPrevention3D.jar"))
        val port = DatagramSocket(0).use { it.localPort }
        File(serverDir, "server.properties").writeText("server-port=$port\nlevel-name=world\n")
        nukkitBootOutput.reset()
    }

    doLast {
        val log = nukkitBootOutput.toString(Charsets.UTF_8.name())
        val required = listOf(
            "Loading GriefPrevention3D v$projectVersion",
            "Enabling GriefPrevention3D v$projectVersion",
            "GriefPrevention3D Nukkit adapter loaded on Nukkit API ",
            "Done ("
        ).filterNot(log::contains)
        val failures = listOf(
            "Incompatible API version",
            "Could not load plugin",
            "NoClassDefFoundError",
            "NoSuchMethodError",
            "NoSuchFieldError",
            "UnsupportedClassVersionError"
        ).filter(log::contains)
        val exitCode = executionResult.get().exitValue
        if (exitCode != 0 || required.isNotEmpty() || failures.isNotEmpty()) {
            throw GradleException(
                "Nukkit boot smoke failed (exit $exitCode). Missing markers: $required; failure markers: $failures\n$log"
            )
        }
        logger.lifecycle("Nukkit $nukkitVersion booted the universal jar.")
    }
}

tasks.named("checkUniversal") {
    dependsOn(checkUniversalNukkit, checkNukkitBoot)
}
