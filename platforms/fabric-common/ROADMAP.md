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
- [x] Subdivision and 3D subdivision modes, plus shaped 2D claim creation and reshaping
      (`/claim mode shaped`, `/shapedclaims`, `/shapedclaim`).

---

## Commands (Phase 2) — done

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
- [x] `/extendclaim` (`/expandclaim`, `/resizeclaim`) extends or shrinks the most specific claim at
      the player's location, or the claim selected with the shovel nib. It handles 2D and 3D claims
      and subdivisions, including shaped outlines and the boundary segment the player faces.
- [x] `FabricPermissionDefaults` is checked against plugin.yml by `FabricPermissionTreeParityTest`.
- [x] `/restrictsubclaim` toggles inheritance for subdivisions
- [x] Subdivision trust inheritance in protection checks (trust commands already copy grants to
       inheriting subdivisions, as Paper does).
- [x] `/scrollresize` (`/claim scrollresize`, `sr`, `src`): the mouse wheel pushes or pulls the side of
      the claim or subdivision the camera points at, with a live preview of the outline and the claims
      around it and the size and claim blocks left above the hotbar. Right-click applies it through the
      golden shovel's checks; left-clicking a block or entity, Q, F, a number key, putting the shovel
      away or a minute idle cancels. A corner selected with the shovel limits it to that corner's sides.
      A mixin on the hotbar packet stands in for `PlayerItemHeldEvent`. Left-clicking the air cannot
      cancel: 26.3 removed the swing handler that would report it.
- [x] `/trapped` (`/claim trapped`): ten seconds standing still in a claim the player cannot build in
      moves them past the claim's west and north corner to the surface, as on Paper. Overworld only;
      administrative claims only with `Claims.AllowTrappedInAdminClaims`.
- [x] `/claim alerts [on|off]` for the claim the player stands in, `/claimtogglealerts [on|off]` for all
      their claims; owners are warned when pistons or fluids are stopped at their claim's border, with
      Paper's deduplication and burst summaries (`BoundaryViolationAlerts`).
- [x] Claim expiration, as Paper's `FindUnusedClaimsTask`: one claim owner is checked every
      `Advanced.ClaimExpirationCheckRate` seconds. `Claims.Expiration.AllClaims.DaysInactive` expires all of
      an absent owner's claims, and `Claims.Expiration.ChestClaimDays` a lone claim no bigger than a new
      player's (`Claims.AutomaticNewPlayerClaimsRadius`). Owners with enough claim blocks
      (`ExceptWhenOwnerHas...ClaimBlocks`) or `griefprevention.dontexpire` keep their claims; for an offline
      owner the permission is read from LuckPerms storage (`loadUser`, then `cleanupUser`), otherwise from
      the plugin.yml default with `ops.json`. Last-online times come from `LastSeen.yml`, so owners not seen
      since it began recording (18.4.7) never expire. A bad or unreadable setting turns expiration off
      rather than falling back to the defaults. `ClaimExpirationCallback` lets other mods keep a claim, as
      Paper's `ClaimExpirationEvent`.
- [x] `/checkclaimexpiry [player]` (`/aclaim checkexpiry`): when the claim the player stands in, or a
      player's claims, expire and what keeps them; another player's takes
      `griefprevention.admin.checkclaimexpiry`.

### Still missing

- [ ] Shaped subdivision creation and shaped `/scrollresize`; restore nature, buying and selling
      claim blocks, and `/siege`.
- [ ] The chat hint that points players who say "trapped" or "stuck" at `/trapped` (needs Fabric's
      message API module).
- [ ] `Claims.Expiration.UnusedClaimDays` (small creative-world claims scored by the blocks placed in
      them) and `AutomaticNatureRestoration`, which both need restore nature.

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
| Optional setback for climbing on rejected block placements (`Claims.PreventBlockPlaceClimbing`, default false) | Rejected block-item use and `ServerGamePacketListenerImpl.handleMovePlayer`, shared `BlockPlaceClimbTracker`; collision steps and climbable activation space, real support and safe destination checks |
| Endermen taking blocks | enderman take-block goal mixin |
| Weaving cobwebs: players need build trust at each destination; mobs cannot place webs in claims | `WeavingMobEffect.onMobRemoved` context and per-web placement mixins, including 26.3's `setBlockAndUpdate` |
| Splash and lingering weaving on claimed animals and villagers need container trust; dispenser potions retain their source through cloud creation | `LivingEntity.addEffect`, potion impact, dispenser projectile and cloud-constructor mixins |
| Block use as Paper decides it (`BlockUseSettings`): containers with `PreventTheft`; doors, trapdoors, fence gates, beds, buttons and levers behind `LockWoodenDoors`, `LockTrapDoors`, `LockFenceGates` and `PreventButtonsSwitches`; redstone and decorations need build trust; crafting tables and other workstations are free | `UseBlockCallback`, dragon eggs `AttackBlockCallback` |
| Lecterns: reading needs access trust with `LecternReadingRequiresAccessTrust` (otherwise anyone may read); taking or placing the book needs container trust | `UseBlockCallback`, `LecternMenu.clickMenuButton` and `LecternBlockEntity.createMenu` mixins |
| Ender pearls and chorus fruit into claims need access trust (`EnderPearlsRequireAccessTrust`); a refused pearl breaks and comes back with `RefundDeniedEnderPearls` (not in creative, where it was never spent); a refused chorus jump is undone and vanilla tries another spot | `ThrownEnderpearl.onHit` mixin; `randomTeleport` wrapped in `TeleportRandomlyConsumeEffect.apply` (the 26.3 overload too) |
| Lightning starts no fire in claims | `LightningBolt.spawnFire` mixin |
| Fishing rods pulling claimed animals, armor stands or sulfur cubes need container trust | `FishingHook.pullEntity` mixin |
| Sword sweeps get the same protection as a direct hit: claimed creatures, villagers, stands and players in safe zones beside the target are neither hurt nor knocked back | sweep damage call in `Player.attack`/`doSweepAttack` wrapped, three shapes across 1.21.2 – 26.3 |
| Sulfur cubes (26.2+) are protected like other creatures: hits, burning arrows and fire aspect cannot reach a claimed one, so a TNT cube cannot be lit, and using one takes container trust | `hurtOrSimulate` runs before the cube's own damage handling; `UseEntityCallback` |
| Falling blocks that drifted sideways (sand cannons) only land in a claim holding their origin, or in an unrestricted subdivision of the claim they fell from; otherwise they drop as items | `setBlock`/`setBlockAndUpdate` wrapped in `FallingBlockEntity.tick` |
| Boundary alerts to claim owners for stopped pistons and fluids, unless turned off with `/claim alerts` | piston and fluid hooks above, `BoundaryViolationAlerts` |
| Starting or extending a raid in a claimed village requires build trust in the raid center's claim, falling back to the triggering player's claim | `Raids.createOrExtendRaid` mixin, `Claims.RaidTriggersRequireBuildTrust` |
| Subdivision trust inheritance, restriction, and admin handling: first-level subdivisions inherit parent trust unless restricted, admin subdivisions modifiable only by staff | Modified trust evaluation in `allows()` method, `isAdminSubdivision()`, `toggleSubdivisionRestriction()`, `toggleNewSubdivisionRestriction()`, subdivision placement validation |


The rules live in `gp3d-core` (`com.griefprevention.protection`) as platform-neutral, unit-tested
policies; the Fabric side only maps positions and entities to claims.

Found while porting: Paper's own `isFluidFlowAllowed` does not follow its documented matrix. It lets any
top-level claim, even another owner's, flow into an unrestricted subdivision that touches its border,
and lets a parent flow into a restricted subdivision. The Fabric policy follows the matrix.

Also found while porting, and handled more strictly on Fabric:

- Paper's `/restrictsubclaim` lets anyone standing in a subdivision restrict or unrestrict it, and
  unrestricting hands it the parent's trust. Fabric asks for manage trust in the claim that decides.
- Paper's `/claim alerts` lets anyone standing in a claim mute its owner's alerts. Fabric asks for
  permission to edit the claim. `/claimtogglealerts` also covers the subdivisions of the player's
  claims, which Paper leaves as they were.

### Still missing

| Protection | Paper event | Likely Fabric hook |
|-----------|-------------|--------------------|
| Tree and structure growth into claims (`LimitTreeGrowth`) | `StructureGrowEvent` | sapling/feature placement mixin |
| Nether portal creation over claims | `PortalCreateEvent` | portal shape mixin |
| Silverfish, rabbits, ravagers, withers changing blocks | `EntityChangeBlockEvent` | per-mob goal mixins |
| Mob melee on frames/stands/armor stands, cactus and other environmental damage | `EntityDamageEvent` | `hurtServer` mixins per class |
| Other splash/lingering potion effects on claimed creatures and players (weaving is protected) | `PotionSplashEvent`, `AreaEffectCloudApplyEvent` | potion/cloud mixins |
| Hoppers taking death loot, pet damage by environment, walking a sulfur cube out of a claim | various | various |
| Fresh-spawn PvP immunity, siege, creative-world rules | various | — |

Known difference: holding a block item while right-clicking a container asks for build trust on Fabric
(the item might be placed); Paper only asks for container trust unless a block is actually placed.
