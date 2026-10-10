# Fabric Platform Roadmap

Shipping: **one universal jar** for Bukkit/Paper, Fabric **1.21.2 – 1.21.11**, and Fabric **26.1 – 26.3.x**
(every one of the 15 releases is boot-smoked and linkage-checked by the build). It is about
1.9 MB; `checkUniversalJar` fails the build past 5 MB. See `README.md` for the layout and the checks.

---

## Version support

### Done

- [x] Shared adapter sources in `platforms/fabric-common`, compiled once per Minecraft target.
- [x] `fabric-26.1` module on the unobfuscated Loom plugin; both modules share one Loom build (1.17).
- [x] One 26.x binary for 26.1 – 26.3, relocated to `com.griefprevention.fabric.mc26_1` inside the
      universal jar so it sits beside the 1.21.11 adapter.
- [x] Data-driven adapter selection (`fabric-adapters.properties`) and a mixin gate so only the selected
      adapter's mixins apply.
- [x] Boot smoke per release through the official Fabric server launcher, with forced mixin application.
- [x] `FabricLinkageCheck`: resolves every Minecraft/Fabric reference and mixin target of an adapter
      against each release in its range. It already caught `Blocks.WHITE_WOOL` (gone in 26.2),
      `EntityType.BLOCK_DISPLAY` (moved in 26.2), the removed `AxeItem`/`HoeItem`/`ShovelItem` (26.3), and
      renamed classes (`FarmBlock`, `EnderMan`).
- [x] Precise Fabric API module dependencies instead of all of `fabric-api`, as LuckPerms declares them.
- [x] Mixin configs at `compatibilityLevel: JAVA_8`, as LuckPerms ships them, so a config never demands
      a newer Java than the release it is gated to runs on.
- [x] 1.21.2 – 1.21.10 without a new module: the 1.21.11 binary links there except for the calls 1.21.11
      reshaped, which `FabricVersionCompat` routes to the earlier methods (permission levels, PvP, the
      `Transformation` constructor). Player names no longer use `NameAndId`, and the copper golem tag and
      `Entity.level()` are avoided.

### Next: 26.4 and later

Add the release to `fabricTargets` and widen `fabricMinecraft26Range` in `gradle.properties`. If the
linkage check or boot fails, prefer id/tag lookups and `@Pseudo` alternatives in the shared code; only add
a new adapter module when a release breaks too much for one binary.

### Later: older Fabric releases (1.14 → 1.21.1)

Fabric's first stable release was 1.14; there is no official Fabric for 1.8 (Legacy Fabric is a separate
project and out of scope). Each older range becomes another adapter module compiled from the same shared
sources, with version-specific sources only where the API diverges:

| Range | Java | Differences to expect (to be confirmed by compiling and linkage-checking) |
|-------|------|-----------------------------------------------------------------------------|
| 1.20.5 – 1.21.1 | 21 | Explosions and entity damage use the pre-1.21.2 `Explosion`/`hurt` shapes; `UseItemCallback` returns `InteractionResultHolder`. |
| 1.17 – 1.20.4 | 16/17 | Display entities arrived in 1.19.4, so no glowing visualization before it. |
| 1.14 – 1.16.5 | 8 | Oldest Fabric API event set. |

Budget: each adapter adds roughly 130–200 KB, so all of them together stay well under 5 MB. Remaining
design points for that work:

- The bootstrap and gate are already Java 8 and Loader-API-only. Adapters compiled for Java 21 are never
  loaded on older runtimes, but `fabric.mod.json` must drop its `java: >=21` floor once a Java 8/17
  adapter is bundled.
- Move the Fabric claim callbacks (`ClaimCreatedCallback`, …) to a version-neutral API package that
  takes only core types, so Fabric addons need not target one adapter's package.
- If Forge/NeoForge support is wanted, follow LuckPerms: split `fabric-common` into loader-neutral
  Minecraft code and a thin Fabric layer.

---

## Visualization (Phase 1) — done

- [x] Glowing visualization: client-only block-display outlines behind `VisualizationGlow`, in Paper's
      colors, visible only to the viewer.
- [x] `visualizeNearbyClaims`: sneak with the investigation tool to see top-level claims within 150
      blocks (`griefprevention.visualizenearbyclaims`).
- [x] Visualizations refresh when a claim they show is resized or abandoned, or one is created nearby.
- [x] `/claimhere` removed; the investigation tool replaces it (also works up to 100 blocks away).
- [x] Config wired: `InvestigationTool`, `ModificationTool`, `MinimumWidth`, `MinimumArea`,
      `VisualizationGlow`, `Claims.Mode`; tool messages moved to `messages.yml` keys.

- [x] `PlayerOfflineTime` on inspection, from a last-seen store (`LastSeen.yml`) kept as players join
      and leave.
- [x] Administrative claims mode for the shovel and `/claim create` (`/adminclaims`, `/basicclaims`).

Still open:

- [ ] Subdivision, 3D and shaped claim modes for the shovel (`/claim mode`).

---

## Commands (Phase 2)

### Done

- [x] LuckPerms-style registration: one greedy argument per command, Bukkit-style parsing, so
      `alias.yml` names, option aliases and usage work as on Paper; failures are logged.
- [x] plugin.yml permission defaults and parent nodes on Fabric, with or without LuckPerms.
- [x] Trust: `/trust` (with a type), `/accesstrust`, `/containertrust`, `/managetrust`,
      `/permissiontrust`, `/untrust`, `/trustlist`, with Paper's claim-or-all-claims rules and messages.
- [x] `/claimslist`, `/abandonclaim`, `/abandontoplevelclaim`, `/abandonallclaims`, `/claimpvp`,
      `/claim create`.
- [x] Claim blocks: `/acb` (players and `[permission]` groups), `/acball`, `/scb`, `/scball`,
      `/aclaim blocks`.
- [x] `/gpreload` also rereads `alias.yml`.
- [x] Staff claim management: `/ignoreclaims`, `/deleteclaim` (and `/aclaim delete claim|player|world|
      userworld|alladmin`), `/deleteallclaims`, `/deletealladminclaims`, `/adminclaimslist`, `/makeadmin`,
      `/makebasic`. As on Paper, `griefprevention.deleteclaims` may edit other players' claims, and
      `/ignoreclaims` passes whatever the player's bypass permission covers.
- [x] `/transferclaim` (`/giveclaim`, `/claim transfer`, `/aclaim transfer`): staff with
      `griefprevention.transferclaim.others` hand any claim over or make it administrative; players give
      their own claims away under `Claims.TransferClaim`. Fabric has no economy, so a configured price
      refuses the transfer, as Paper does without Vault, unless the player has `.free`.
- [x] `/claimexplosions`, `/witherexplosions`.
- [x] `FabricPermissionDefaults` is checked against plugin.yml by `FabricPermissionTreeParityTest`.

### Still missing

- [ ] `/restrictsubclaim`, `/claim alerts`.
- [ ] `/trapped`, `/siege`, `/extendclaim`, restore nature, buying and selling claim blocks.
- [ ] Subdivision trust inheritance in protection checks (trust commands already copy grants to
      inheriting subdivisions, as Paper does).

---

## Protection parity (Phase 3)

### Done in this release

| Protection | Fabric hook |
|-----------|-------------|
| Bucket fill/empty, boats, lily pads, spawn eggs on water | `UseItemCallback` + view raytrace, client resync |
| Build-trust items: dyes, ink, honeycomb, brushes, end crystals; axes/hoes/shovels on reshapeable blocks | `UseBlockCallback` |
| Container-level blocks without block entities (cake, cauldrons, anchors, berries, pumpkins, anvils) | `UseBlockCallback` |
| Sign editing (build), waxed signs clickable | `UseBlockCallback` |
| Item frame / armor stand interaction (build) | `UseEntityCallback` |
| Entity damage: monsters free, creatures (container), frames/stands/crystals/paintings/vehicles/villagers (build), projectiles included | `Entity.hurtOrSimulate` mixin + `AttackEntityCallback` |
| PvP safe zones and `/claimpvp` toggles | same, `PvpProtectionPolicy` |
| Explosion damage to protected entities | `ServerExplosion.hurtEntities` mixin |
| Fluid flow into claims | `FlowingFluid.spreadTo` mixin, `FluidFlowPolicy` |
| Pistons across claim borders | `PistonBaseBlock.moveBlocks` mixin, `PistonMovementPolicy` |
| Fire spread and burning, lava ignition | `FireBlock.tick` and `LavaFluid.randomTick` mixins, `FirePolicy` |
| Dispensers and droppers across claim borders | `dispenseFrom` mixins |
| Farmland trampling | `FarmBlock`/`FarmlandBlock` mixin |
| Endermen taking blocks | enderman take-block goal mixin |
| Block use as Paper decides it (`BlockUseSettings`): containers with `PreventTheft`; doors, trapdoors, fence gates, beds, buttons and levers behind `LockWoodenDoors`, `LockTrapDoors`, `LockFenceGates` and `PreventButtonsSwitches`; redstone and decorations need build trust; crafting tables and other workstations are free | `UseBlockCallback`, dragon eggs `AttackBlockCallback` |
| Lecterns: reading needs access trust with `LecternReadingRequiresAccessTrust` (otherwise anyone may read); taking or placing the book needs container trust | `UseBlockCallback`, `LecternMenu.clickMenuButton` and `LecternBlockEntity.createMenu` mixins |

The rules live in `gp3d-core` (`com.griefprevention.protection`) as platform-neutral, unit-tested
policies; the Fabric side only maps positions and entities to claims.

Found while porting: Paper's own `isFluidFlowAllowed` does not follow its documented matrix. It lets any
top-level claim, even another owner's, flow into an unrestricted subdivision that touches its border,
and lets a parent flow into a restricted subdivision. The Fabric policy follows the matrix.

### Still missing

| Protection | Paper event | Likely Fabric hook |
|-----------|-------------|--------------------|
| Tree and structure growth into claims (`LimitTreeGrowth`) | `StructureGrowEvent` | sapling/feature placement mixin |
| Ender pearls / chorus fruit into claims (`EnderPearlsRequireAccessTrust`) | `PlayerTeleportEvent` | pearl hit / teleport mixin |
| Lightning-caused fire in claims | `BlockIgniteEvent` | `LightningBolt.spawnFire` mixin |
| Nether portal creation over claims | `PortalCreateEvent` | portal shape mixin |
| Silverfish, rabbits, ravagers, withers changing blocks | `EntityChangeBlockEvent` | per-mob goal mixins |
| Falling-block sand cannons into claims | `EntityChangeBlockEvent` | `FallingBlockEntity` landing mixin |
| Mob melee on frames/stands/armor stands, cactus and other environmental damage | `EntityDamageEvent` | `hurtServer` mixins per class |
| Splash/lingering potions on claimed creatures and players | `PotionSplashEvent`, `AreaEffectCloudApplyEvent` | potion/cloud mixins |
| Fishing rods pulling claimed entities | `PlayerFishEvent` | `FishingHook` mixin |
| Raid triggers, sulfur cubes, hoppers taking death loot, pet damage by environment | various | various |
| Fresh-spawn PvP immunity, siege, creative-world rules | various | — |

Known difference: holding a block item while right-clicking a container asks for build trust on Fabric
(the item might be placed); Paper only asks for container trust unless a block is actually placed.
