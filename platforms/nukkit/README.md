# GriefPrevention3D on Nukkit (Bedrock Edition)

> **Scaffold only.** The adapter loads on Nukkit and reports itself, but it reads no claims and
> protects nothing yet. [`ROADMAP.md`](ROADMAP.md) lists the work ahead.

The universal jar is also a Cloudburst Nukkit plugin. Nukkit's plugin loader reads `nukkit.yml` in
preference to `plugin.yml`, so the same jar starts `GriefPreventionNukkit` on Nukkit and the Bukkit
plugin on Bukkit/Paper. Neither platform ever loads the other's classes.

| Server | Target | Java | Package in the universal jar |
|--------|--------|------|------------------------------|
| [Cloudburst Nukkit](https://github.com/CloudburstMC/Nukkit) | API `1.1.0`, build `1.0-20260919.123832-1250` (git `b3caad6`, Bedrock 1.26.50) | 8 | `com.griefprevention.nukkit` |

## Why this target

- Cloudburst Nukkit is the maintained upstream: it follows each Bedrock release within weeks and its
  plugin API has been `1.1.0` since before December 2024.
- It only publishes `1.0-SNAPSHOT`, so `gradle.properties` pins one timestamped snapshot
  (`nukkitVersion`). opencollab keeps every snapshot since 2022, so the pin keeps resolving. Moving to a
  newer build is a one-line change, and `checkNukkitBoot` then proves it.
- Nukkit is Java 8 bytecode and its official Docker image runs Java 8. The adapter is Java 8 like
  `gp3d-core` and the Bukkit plugin, and the boot smoke runs on Java 8.
- Forks such as PowerNukkitX and Lumi changed the API and are out of scope. Each would get its own
  module if it is ever wanted.

## Layout

- `platforms/nukkit`: the Gradle project `:nukkit`. Like `platforms/fabric-common`, it holds the
  adapter's sources and tests in one flat package, `com.griefprevention.nukkit`, with `Nukkit*` classes.
  Nukkit has one API line, so there is no bootstrap or per-version module.
- `src/main/resources/nukkit.yml`: the descriptor template. The build fills in the version and API, and
  appends `plugin.yml`'s `permissions:` section verbatim, so both platforms declare the same nodes,
  defaults and children. Commands are left out on purpose: as on Fabric, the adapter will register them
  itself from `alias.yml`.
- `platforms/nukkit-universal.gradle.kts`: adds the adapter jar to the universal jar and registers its
  checks. It is applied from the root build after `fabric-universal.gradle.kts`.

The adapter uses `gp3d-core` for claims, persistence, messages and protection rules. It does not
bundle a copy: the universal jar already carries the core, with SnakeYAML relocated, from the Bukkit
jar. The adapter must never import `org.yaml.snakeyaml` directly, because on Nukkit that is Nukkit's
own SnakeYAML 1.33. `gp3d-core`'s `checkCoreBoundary` rejects `cn.nukkit` imports, so the core stays
platform-neutral.

## Building and verifying

```bash
./gradlew :nukkit:test            # descriptor tests
./gradlew checkUniversalNukkit    # the universal jar carries nukkit.yml and the adapter unchanged
./gradlew checkNukkitBoot         # boots the universal jar in a real Nukkit server on Java 8
```

`checkUniversal` runs both checks. `checkNukkitBoot` resolves the pinned Nukkit build and its
libraries from opencollab. It starts the server under `build/nukkit-server` on a free UDP port with the
universal jar in `plugins/`. When the server has started, it sends `stop` and requires the adapter's
load and enable lines in the log. Nukkit throws away console input that arrives before startup
finishes, so `stop` is held back until then.

`NukkitDescriptorTest` reads the built `nukkit.yml` with Nukkit's own parser. It checks the entrypoint
and version, and that the API is one this Nukkit accepts. It also checks that the permission tree is
identical to `plugin.yml`'s, that every default is a value Nukkit understands, and that no permission
lists a child as `false`. That last check matters because Nukkit grants every listed child, whatever
its value, where Bukkit revokes `false` ones.
