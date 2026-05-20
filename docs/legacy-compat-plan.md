# Legacy Compatibility Plan

Goal: split compatibility work into sane release tracks:

- A Bukkit-family jar for Bukkit/Spigot/Paper/Folia, targeting the widest practical 1.8-to-latest server range.
- Separate mod-loader jars for Fabric, Forge, NeoForge, and any other loader worth supporting.
- A shared core that keeps claim logic reusable across those targets.

## DiscordSRV Pattern

DiscordSRV's current branch does not use separate per-Minecraft-version source folders for its main compatibility strategy. Its compatibility model is:

- Compile main code to Java 8 bytecode.
- Use a modern Paper API as `compileOnly`.
- Keep optional/new server features behind `Class.forName`, reflection, or delayed listener registration.
- Only instantiate classes that directly reference modern APIs after the runtime server has those classes.
- Use reflection for cross-version API shape changes such as `Server#getOnlinePlayers`, Folia schedulers, sounds, NMS class names, and optional plugin hooks.
- Shade and relocate dependencies so the runtime server's old libraries do not conflict with plugin dependencies.

This lets the jar load on old servers because old servers never load classes that require newer APIs.

## GP3D Blockers

GP3D currently has larger blockers than a POM/Gradle conversion:

- Java 16+ syntax: `record` is used in production code.
- Java 9+ library calls: `List.of`, `Set.of`, `List.copyOf`, `getFirst`, `getLast`, `Stream.toList`, `String.repeat`, and `String.isBlank`.
- Modern Bukkit/Paper API hard references: `BlockData`, `Tag`, `NamespacedKey`, `PlayerProfile`, `BlockDisplay`, `Display`, `Transformation`, `Waterlogged`, newer player events, newer entity classes, and many modern `Material` constants.
- Modern world-height API: `World#getMinHeight` and `World#getMaxHeight` need a compatibility wrapper for pre-1.17 servers.
- Modern visualization pipeline: glowing/block-display visualization cannot load on legacy servers and needs a legacy fallback.
- Tests and mocks assume modern registry APIs.

## Distribution Model

Do not target one jar across Bukkit, Fabric, Forge, NeoForge, Quilt, and BTA. That creates too many incompatible loader, mapping, metadata, event, and runtime assumptions in one artifact.

Target these artifacts instead:

- `GriefPrevention3D-Bukkit`: one Bukkit-family plugin jar for Bukkit, Spigot, Paper, Folia, Purpur, and similar server forks. This is where the 1.8-to-latest compatibility goal belongs.
- `GriefPrevention3D-Fabric`: Fabric server/integrated-server mod jar.
- `GriefPrevention3D-NeoForge`: NeoForge server/integrated-server mod jar.
- `GriefPrevention3D-Forge`: Forge jar only if older Forge demand justifies maintaining a separate line.
- `GriefPrevention3D-Quilt`: optional later target if Quilt demand is real; Fabric compatibility may be enough for an initial pass.

All target artifacts should depend on or include a shared `gp3d-core` module. The platform jars should be adapters: entrypoints, command registration, event hooks, permissions/economy integration, world/player/block wrappers, visualization, and persistence bootstrapping.

## Addon Compatibility

Existing GriefPrevention and GriefPrevention3D addons are part of the compatibility surface. The current Gradle scaffold does not change runtime behavior or public APIs, but later module extraction can break addons if public classes move or artifact coordinates change.

Protect these surfaces for the Bukkit-family jar:

- Bukkit plugin identity: keep `plugin.yml` name `GriefPrevention` and main class `me.ryanhamshire.GriefPrevention.GriefPrevention`.
- Maven coordinates during the transition: keep publishing/consuming `com.griefprevention:GriefPrevention3D` for the Bukkit artifact, or publish a compatibility artifact with those coordinates.
- Classic package API: preserve `me.ryanhamshire.GriefPrevention.Claim`, `DataStore`, `PlayerData`, `ClaimPermission`, `Messages`, events under `me.ryanhamshire.GriefPrevention.events`, and common static access through `GriefPrevention.instance`.
- GP3D command addon API: preserve `com.griefprevention.api.ClaimCommandAddon`, `ClaimCommandAddonRegistry`, and `ClaimCommandContext`.
- Bukkit event contracts: keep existing claim events and cancellation behavior stable unless a major-version migration explicitly documents a breaking change.
- Runtime dependency behavior: keep Vault, PlaceholderAPI, WorldEdit-style command integrations, and softdepend names stable from the Bukkit jar's perspective.

Core extraction rule: move implementation behind these APIs, not the APIs themselves. If a class must move into `gp3d-core`, leave a deprecated Bukkit-facing wrapper or adapter in the old package until a documented major release.

Mod-loader artifacts should not promise Bukkit addon compatibility. They can expose a new platform-neutral API later, but Bukkit addons should target the Bukkit-family jar.

### GPExpansion Compatibility

`/mnt/storage/repos/GPExpansion` is a maintained addon and should be used as a concrete compatibility check before merging modularization work. Its `GPBridge` uses reflection so it can run across GP variants, but that means several internal names are de facto compatibility contracts for the Bukkit-family jar.

Protect these additional GPExpansion dependencies:

- Plugin discovery through Bukkit plugin name `GriefPrevention`, class `me.ryanhamshire.GriefPrevention.GriefPrevention`, static `GriefPrevention.instance`, and instance field `dataStore`.
- `DataStore` methods: `getClaims`, `getClaimAt` variants, `getPlayerData(UUID)`, `savePlayerData(UUID, PlayerData)`, `saveClaim(Claim)`, `deleteClaim(Claim)`, `changeClaimOwner(Claim, UUID)`, `createClaim(...)`, `resizeClaim(...)`, `updateShapedClaim(Player, PlayerData, Claim, OrthogonalPolygon)`, and `getMessage(Messages, String...)`.
- `Claim` methods/fields: `getID`, `getOwnerID`, `setOwnerID`, `ownerID`, `parent`, `children`, `getChildren`, `getSubclaims`, `getLesserBoundaryCorner`, `getGreaterBoundaryCorner`, `getArea`, `contains(Location, boolean, boolean)`, `isAdminClaim`, `is3D`, `containsY`, `getMinY`, `getMaxY`, `isShaped`, `getBoundaryPolygon`, `getSafeTeleportLocation`, `setPermission(String, ClaimPermission)`, `dropPermission(String)`, trust getters such as `getBuildTrust`, `getInventoryTrust`, `getContainerTrust`, `getAccessTrust`, and `getManagerTrust`.
- `ClaimPermission` enum names, especially `Build` and `Inventory`.
- `CreateClaimResult`/resize result shape: public fields or getters for `succeeded`, `claim`, and `denialMessage`.
- Shaped-claim geometry classes under `com.griefprevention.geometry`: `OrthogonalPolygon`, `OrthogonalPoint2i`, polygon factory methods such as `fromRectangle` and `fromClosedPath`, and polygon methods used for map editing such as `corners`, `edges`, `insertNode`, `expandEdge`, and `edgeIndexesContainingInteriorPoint`.
- `ClaimEditorSkeleton` helper methods currently reached reflectively for map editing, especially `unionPolygons` and `subtractPolygons`.
- Visualization bridge: `com.griefprevention.visualization.VisualizationType`, `BoundaryVisualization.visualizeClaim(Player, Claim, VisualizationType)`, and enum constants `CLAIM`, `ADMIN_CLAIM`, `SUBDIVISION_3D`, and `ADMIN_CLAIM_3D`.
- Config fields used by GPExpansion: `config_claims_allowShapedClaims`, `config_claims_allowNestedSubClaims`, `config_claims_minWidth`, `config_claims_minArea`, `config_claims_shapedMinWidth`, `config_claims_shapedMinArea`, `config_economy_claimBlocksEnabled`, and `config_economy_claimBlocksPurchaseCost`.
- `PlayerData` methods/fields for claim-block accounting: `getAccruedClaimBlocks`, `getBonusClaimBlocks`, `getTotalClaimBlocks`, `getRemainingClaimBlocks`, `setAccruedClaimBlocks`, `setBonusClaimBlocks`, `accruedClaimBlocks`, and `bonusClaimBlocks`.
- `com.griefprevention.api.ClaimCommandAddon` and `ClaimCommandAddonRegistry` behavior, including delegation of unknown `/claim` subcommands via `ClaimCommandContext`, because GPExpansion registers `snapshot` and adds tab completions through this bridge.

This does not mean every internal method must remain forever. It means the Bukkit jar needs either the same reflective surface or a first-class replacement API before those names are removed. The current branch protects GPExpansion with `AddonCompatibilitySurfaceTest` for reflected GP3D entry points and `./gradlew compileGpExpansionCompatibility`, which compiles the local GPExpansion checkout against the produced Bukkit-facing jar. The next GPExpansion guardrail should be an integration smoke test that loads GPExpansion against the built Bukkit artifact and exercises `GPBridge.isAvailable`, claim lookup, claim id/corners, trust mutation, `create1x1SubdivisionAt`, shaped map editing, and `/claim snapshot` delegation.

### GPFlags Compatibility

`/mnt/storage/repos/GPFlags` is also a maintained addon, but it is a stronger compatibility constraint than GPExpansion because it compiles directly against GriefPrevention classes. It currently depends on `plugin.yml` loading `GriefPrevention`, imports `me.ryanhamshire.GriefPrevention.*`, and uses both direct fields and Bukkit custom events.

Protect these GPFlags dependencies for the Bukkit-family jar:

- Plugin and artifact compatibility: keep Bukkit plugin name `GriefPrevention`, main class `me.ryanhamshire.GriefPrevention.GriefPrevention`, static `GriefPrevention.instance`, and public `dataStore`. Keep the Bukkit artifact consumable by addons that currently compile against GP coordinates, either by preserving the transition coordinates or publishing a compatibility artifact with the classic packages.
- `GriefPrevention` methods used by GPFlags: `getDescription().getVersion()`, `claimsEnabledForWorld(World)`, and `ejectPlayer(Player)`.
- `DataStore` methods: `getClaims()`, `getClaim(Long)`, `getClaimAt(Location, boolean, Claim)`, `getClaimAt(Location, boolean, boolean, Claim)`, `getPlayerData(UUID)`, and `saveClaim(Claim)`.
- `PlayerData` public fields and methods: `lastClaim`, `ignoreClaims`, and `getClaims()`.
- `Claim` methods/fields: `getID()`, `getOwnerID()`, `getLesserBoundaryCorner()`, `getGreaterBoundaryCorner()`, `contains(Location, boolean, boolean)`, `isAdminClaim()`, `getChunks()`, `getPermission(String)`, `setPermission(String, ClaimPermission)`, public `parent`, and public `children`.
- `ClaimPermission` enum constants: `Access`, `Inventory`, `Build`, `Manage`, and `Edit`.
- Permission methods: `Claim#checkPermission(Player, ClaimPermission, Event)` must keep the current null-means-allowed behavior. GPFlags also has fallbacks for older GP methods, so keep `allowAccess(Player)`, `allowContainers(Player)`, `allowBuild(Player, Material)`, `allowGrantPermission(Player)`, and `allowEdit(Player)` available unless GPFlags is migrated first.
- Event classes under `me.ryanhamshire.GriefPrevention.events`: `ClaimResizeEvent`, `ClaimModifiedEvent`, `ClaimCreatedEvent`, `ClaimTransferEvent`, `ClaimDeletedEvent`, `TrustChangedEvent`, `ClaimPermissionCheckEvent`, `ClaimExpirationEvent`, `SaveTrappedPlayerEvent`, `ProtectDeathDropsEvent`, `PreventPvPEvent`, and `PreventBlockBreakEvent`.
- Event method contracts: `ClaimResizeEvent`/`ClaimModifiedEvent` need `getFrom()` and `getTo()`; `ClaimCreatedEvent`, `ClaimDeletedEvent`, `ClaimExpirationEvent`, `ProtectDeathDropsEvent`, and `SaveTrappedPlayerEvent` need `getClaim()`; `ClaimTransferEvent` needs `getClaim()` and `getNewOwner()`; `TrustChangedEvent` needs `getIdentifier()` and `getClaims()`.
- Mutable event behavior: `ClaimPermissionCheckEvent` needs `getCheckedPlayer()`, `getRequiredPermission()`, `getTriggeringEvent()`, and `setDenialReason(Supplier<String>)` with `null` accepted to allow the action. `PreventPvPEvent`, `ProtectDeathDropsEvent`, `PreventBlockBreakEvent`, and `ClaimExpirationEvent` must remain cancellable with the same semantics GPFlags expects.
- `PreventPvPEvent#getDefender()` is used to decide whether an `AllowPvP` flag should cancel GP's PvP protection. `PreventBlockBreakEvent#getInnerEvent()` is used by the Spleef flag to access and allow the underlying `BlockBreakEvent`.

The current branch adds `./gradlew checkAddonCompatibility`, which compiles GPFlags and GPExpansion against the produced Bukkit-facing jar using the local addon checkouts. The next GPFlags guardrail should be a smoke test that loads GP3D plus GPFlags and exercises claim flag lookup, enter/exit claim border events, resize/delete/transfer events, `ClaimPermissionCheckEvent` denial override, buy-trust commands that call `setPermission`/`saveClaim`, and the flight/trust-change path. If core extraction changes any of these internals, add deprecated Bukkit-facing wrappers first and migrate GPFlags only after the replacement API exists.

### gpapiaddon-workspace Review

`origin/feature/gpapiaddon-workspace` is an unrelated-root workspace snapshot, not a normal feature branch against this compatibility branch. It creates a Maven parent with `griefprevention/` and `gpapi-addon/` modules, targets Java 21 and 1.21.11 APIs, and moves the plugin source into the `griefprevention` module. Do not merge it wholesale into this branch.

The branch has useful ideas worth mirroring selectively:

- Claim tool interception: `ClaimToolHandler`, `ClaimToolContext`, `ClaimToolHandlerRegistry`, and `ClaimToolDispatcher`.
- Command extension API: `ClaimCommandExtensionRegistry`, `ClaimCommandSubcommand`, `ClaimCommandMode`, and the rewritten `/claim` command dispatch.
- Claim metadata and geometry hooks: `ClaimMetadataContainer`, `ClaimGeometry`, `ClaimGeometryRegistry`, and `ClaimCreationCustomizer`.
- Visualization extensibility: `VisualizationStyle`, `VisualizationStyleRegistry`, `VisualizationProvider`, `BoundaryVisualizationEvent`, and the `Boundary` model.
- The `gpapi-addon` proof-of-concept demonstrates stacked/3D subdivisions as an addon using those APIs.

The branch is not compatible with the maintained addon surface as-is:

- GPExpansion's current command bridge looks for `com.griefprevention.api.ClaimCommandAddon`, `ClaimCommandAddonRegistry`, and `ClaimCommandContext`. The workspace branch replaces that with `com.griefprevention.api.claimcommand.*`, so `/claim snapshot` registration would fail unless the old classes remain as wrappers.
- GPExpansion's shaped-map bridge reflects `com.griefprevention.geometry.OrthogonalPolygon`, `OrthogonalPoint2i`, and `com.griefprevention.claims.editor.ClaimEditorSkeleton`. Those classes are not present in the workspace jar.
- GPExpansion expects shaped/3D claim methods and config fields such as `Claim#is3D`, `containsY`, `getMinY`, `getMaxY`, `isShaped`, `getBoundaryPolygon`, `setOwnerID`, `getSubclaims`/`getChildren`, `DataStore#updateShapedClaim`, `config_claims_allowShapedClaims`, `config_claims_shapedMinWidth`, and economy claim-block fields. The workspace jar does not expose those contracts.
- GPFlags looks mostly closer to compatible: the workspace jar keeps `GriefPrevention.instance`, `dataStore`, `Claim.parent`, `Claim.children`, `PlayerData.lastClaim`, `PlayerData.ignoreClaims`, `ClaimPermission.Inventory`, the permission-check events, and the legacy claim events GPFlags uses. It still needs the maintained-addon compile guardrail before any code is mirrored.
- The recoded `PlayerEventHandler` directly imports modern APIs such as `Tag`, `BlockData`, `Waterlogged`, `PlayerProfile`, `CopperGolem`, `PlayerSignOpenEvent`, `PlayerTakeLecternBookEvent`, and uses Java 9+ helpers such as `List.of`, so it is not directly usable for the Java 8 / 1.8 Bukkit-family target.

Selective mirror rule: copy API shape and small adapter concepts, not the workspace layout or recoded handlers. Any port must preserve the old Bukkit-facing classes first, add wrappers from old APIs to new registries, and pass `checkAddonCompatibility` plus `AddonCompatibilitySurfaceTest` before replacing current command, claim-tool, visualization, or geometry internals. `AddonCompatibilitySurfaceTest` protects GPExpansion's reflected command, shaped-claim, claim, datastore, event, permission, and visualization entry points.

## Bukkit Initial Direction

Do not start by trying to compile everything as Java 8. First make the codebase loadable by isolating modern APIs behind compatibility boundaries.

1. Add Gradle alongside Maven or in a dedicated branch only.
2. Set Gradle target bytecode to Java 8 once production syntax is Java 8-compatible.
3. Add a `com.griefprevention.compat` package with small wrappers for version-sensitive behavior.
4. Replace direct modern calls incrementally with wrappers.
5. Move classes that directly import modern-only APIs behind factories that use runtime class probes.
6. Add a legacy smoke-test target that attempts plugin class loading against an old Bukkit/Spigot API.

Current branch state: main sources still target Java 21, but `src/compat/legacy/java` now compiles with `legacyTargetJavaVersion=8` through `./gradlew checkLegacyCompatibility`. Its compile classpath uses `legacyBukkitVersion=1.8.8`, so legacy adapters fail fast if they reference APIs missing from old Bukkit. Use that source set for new legacy-server adapters as modern-only Bukkit/Paper calls are extracted behind compatibility boundaries.

## Proposed Compatibility Boundaries

- `ServerPlatform`: detect Bukkit/Spigot/Paper/Folia and Minecraft API features.
- `WorldCompat`: min/max world height, world border, biome keys.
- `MaterialCompat`: safe material lookup by name with fallbacks for renamed/missing materials.
- `BlockDataCompat`: block state/data operations such as waterlogged, chest facing, directional blocks.
- `EventCompat`: listener registration for modern-only events only when classes exist.
- `VisualizationCompat`: select legacy fake-block visualization on old servers and block-display/glowing visualization on modern servers.
- `SchedulerCompat`: keep Folia reflection support while avoiding hard loads on old Bukkit.
- `ProfileBanCompat`: hide `BanList<PlayerProfile>` and modern profile APIs.

First adapter slice: `WorldHeightCompat` lives in main source, with `LegacyWorldHeightCompat` in `src/compat/legacy/java` returning min Y `0` and using old Bukkit `World#getMaxHeight()`, and `ModernWorldHeightCompat` in `src/compat/modern/java` using modern `World#getMinHeight()`/`getMaxHeight()`. `WorldHeightCompatProvider` selects the packaged implementation by runtime feature detection and has a Maven-safe fallback for builds that do not include Gradle compat source sets. `GriefPrevention#getWorldMinY` and `getWorldMaxY` now route through this provider. Production world-height access now goes through those helpers; direct Bukkit height calls are isolated to the compat provider and compat implementations. `./gradlew checkLegacyCompatibility` includes `checkWorldHeightCompatUsage` to keep that boundary from regressing.

First material slice: `MaterialCompat` resolves Bukkit `Material` values by string name and skips names missing on the running server. `Claim` now builds its farm-crop trust whitelist through `MaterialCompat.availableSet(...)`, including both old names such as `CROPS`, `CARROT`, `POTATO`, and `NETHER_WARTS` and modern names such as `CARROTS`, `POTATOES`, `NETHER_WART`, `GLOW_BERRIES`, and `CAVE_VINES`. `AutoExtendClaimTask` also uses name-based add/remove helpers for its manually maintained player-block lists, including old aliases such as `WORKBENCH`, `PORTAL`, `DIODE_BLOCK_ON`, `WEB`, and `SKULL`. This avoids direct static references to modern material constants while keeping modern behavior when those materials exist.

First tag slice: `MaterialTagCompat` resolves Bukkit `Tag` fields reflectively by name and returns an empty set when the runtime does not have Bukkit tags. It avoids initializing Bukkit tags when no server registry is available, which keeps ordinary unit tests from poisoning `org.bukkit.Tag` initialization. `AutoExtendClaimTask` now uses this helper for its material-tag groups instead of importing `org.bukkit.Tag` directly. `BlockEventHandler` also uses it for saplings, fire, and infiniburn checks, with material-name fallbacks for legacy saplings, fire blocks, and trash-block initialization. `FakeBlockVisualization` uses it for fence/sign/wall transparency checks. Legacy servers will still need more fallback material-name lists for behavior parity where tags do not exist, but this removes more hard class-linkage points from the auto-extend, block-event, and fake-block visualization paths.

## Future Mod Loader Track

PacketEvents is a useful reference for broader loader support, but its current structure is not an Architectury single-module build. It uses a Gradle multi-project layout:

- `api`: platform-neutral API and protocol/domain code.
- `netty-common`: shared runtime implementation needed by multiple platforms.
- `spigot`, `bungeecord`, `velocity`, `sponge`: platform-specific adapter jars.
- `fabric-common`: Fabric-loader-facing common code.
- `fabric-official` and `fabric-intermediary`: mapping-specific Fabric modules.
- `fabric`: aggregate jar that includes API, shared code, and Fabric-specific modules.

For GP3D, copy the shape before copying the exact modules. The long-term layout should be:

- `gp3d-core`: claim geometry, claim editing, trust rules, player claim-block accounting, message keys, and persistence models with no Bukkit imports.
- `gp3d-bukkit`: current Spigot/Paper plugin entrypoint, Bukkit event listeners, Bukkit commands, Vault, PlaceholderAPI, and Bukkit visualization.
- `gp3d-bukkit-legacy`: optional old-Bukkit implementations when reflection alone is too fragile.
- `gp3d-bukkit-modern`: Paper/Folia/current Bukkit implementations.
- `gp3d-mod-common`: shared mod-loader abstractions for Minecraft server/player/world/block access.
- `gp3d-fabric`: Fabric server mod entrypoint and event wiring.
- `gp3d-neoforge`: NeoForge server mod entrypoint and event wiring.
- `gp3d-forge`: only if targeting older Forge versions is worth the support cost.

Architectury may help once `gp3d-core` exists. Architectury API can abstract some Fabric/Forge/NeoForge loader calls, events, registries, networking, and platform checks, and Architectury's Gradle tooling can support common-module multi-loader builds. It does not remove the need to split Bukkit-specific code away from core logic, and it is not a bridge from Bukkit APIs to mod-loader APIs.

CustomPlayerModels is a second useful reference, especially for very wide version support. Its strategy is different from PacketEvents:

- It keeps a root shared API/common project at `CustomPlayerModels/src/shared`.
- It has many standalone Gradle projects by Minecraft version and loader, such as `CustomPlayerModels-1.8`, `CustomPlayerModelsFabric-1.14`, `CustomPlayerModelsQuilt-1.20`, `CustomPlayerModelsLexForge-1.21.11`, `CustomPlayerModels-BTA`, and `CustomPlayerModels-Bukkit`.
- Each target project pulls shared code with `sourceSets.main.java.srcDir "../CustomPlayerModels/src/shared/java"` and then adds version/platform-specific source folders such as `src/platform-shared/java` or loader-local `src/main/java`.
- Old Forge targets use old ForgeGradle and Java 8, for example `CustomPlayerModels-1.8` uses ForgeGradle 2.1.3, Minecraft Forge `1.8.9-11.15.1.2318-1.8.9`, MCP mappings, and `sourceCompatibility = targetCompatibility = '1.8'`.
- Fabric targets use Fabric Loom and the matching Minecraft/Fabric API versions for that target.
- Quilt targets use Quilt Loom and QSL/QFAPI.
- BTA support is another standalone Fabric-like project with custom Minecraft metadata and BTA-specific repositories.
- The Bukkit target is a Bukkit adapter that includes the same shared code and populates shared access interfaces from Bukkit services.

This means CustomPlayerModels supports a broad matrix by producing target-specific jars. It is not a single universal jar across Forge/Fabric/Quilt/Bukkit/BTA. For GP3D, follow the same artifact strategy:

- Keep the Bukkit/Spigot/Paper line as a one-jar compatibility goal where practical.
- Treat mod loaders as separate target artifacts that reuse a shared `gp3d-core`, because loader mappings, Gradle plugins, metadata, entrypoints, and event APIs vary too much for one jar to be the right product.

Singleplayer/LAN support is a separate product decision. It is possible only as a mod, not as a Bukkit plugin. A practical first version would protect integrated-server worlds in LAN play using the same core claim model, but it would need different UX assumptions: no Vault, no PlaceholderAPI, no Bukkit permissions, different commands/screens, and local-world persistence.

## First Milestones

1. **Build scaffold**
   Add Gradle wrapper and a Gradle build that mirrors the current Maven build while still compiling with Java 21. Keep behavior unchanged. Add empty `compatLegacy` and `compatModern` source sets so version-specific code has a clear destination before the implementation moves.

2. **Java 8 syntax audit**
   Replace production `record` types with normal final classes, replace Java 9+ collection helpers, and remove Java 10+ `var`.

3. **Legacy API compile target**
   Introduce a second compile check against an older Bukkit/Spigot API, expecting failures at first. Use its failure list as the backlog.

4. **Runtime class-load audit**
   Add a test or script that loads every production class with only legacy Bukkit on the classpath. Any `NoClassDefFoundError` identifies a class that must be moved behind a compatibility boundary.

5. **Feature fallback pass**
   Disable or degrade modern-only features on legacy servers instead of blocking plugin startup.

6. **Core extraction scout**
   Start moving code that can become platform-neutral into a future core boundary. The first candidates are geometry, shaped-claim editing decisions, trust calculation inputs/outputs, and persistence DTOs. Avoid moving event handlers, Bukkit commands, visualization, or economy integrations until platform interfaces exist.

## Risk

One Bukkit-family jar across 1.8 through current Paper/Folia is possible, but it is a long-running compatibility project. The highest-risk area is not Gradle; it is preventing old servers from loading classes that mention modern APIs.

Mod-loader support is a larger project than Bukkit legacy support because it requires a Bukkit-free core. It should begin with extraction and adapter seams, not with Fabric/Forge entrypoints.
