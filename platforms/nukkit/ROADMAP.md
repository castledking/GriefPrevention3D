# Nukkit Platform Roadmap

Target: Cloudburst Nukkit, plugin API `1.1.0`, pinned build `1.0-20260919.123832-1250` (Bedrock
1.26.50), Java 8. It ships in the same universal jar as Bukkit/Paper and Fabric. See `README.md`.

---

## Phase 0: scaffold (done)

- [x] Gradle project `:nukkit` in `platforms/nukkit`, compiled against the pinned Nukkit build.
- [x] `GriefPreventionNukkit` entrypoint, named by `nukkit.yml`, which Nukkit reads before `plugin.yml`.
- [x] `nukkit.yml` generated with `plugin.yml`'s permission tree, checked by `NukkitDescriptorTest`.
- [x] Adapter and descriptor in the universal jar, verified by `checkUniversalNukkit`.
- [x] `checkNukkitBoot`: the universal jar boots and stops in a real Nukkit server on Java 8.
- [x] `gp3d-core`'s `checkCoreBoundary` rejects `cn.nukkit` imports.

---

## Phase 1: share the server runtime with Fabric

Much of `platforms/fabric-common` does not touch Minecraft or Fabric at all. It is the server runtime
Nukkit needs too:

| Class | What it does |
|-------|--------------|
| `FabricClaimFileStore` | Reads and writes claim files in Paper's format |
| `FabricClaimBlockService` | Player data, accrued/bonus/group claim blocks |
| `FabricDataFolder` | Shared `plugins/GriefPreventionData` folder, default config and messages |
| `FabricMessages` | `messages.yml` catalog |
| `YamlDefaultsUpdater` | Adds new default keys to existing YAML files |
| `FabricCommandLine` | Bukkit-style argument parsing |
| `FabricClaimTrustEvaluator`, `FabricTrustTargetResolver` | Trust checks and `/trust` targets |

Move these into `gp3d-core`, which is Java 8, platform-neutral and already inside the universal jar,
under platform-neutral names. Both adapters then call the same code. Three things to change on the way:

- They log through SLF4J, which Nukkit does not ship. Use a small core logging interface, as
  `CommandAliasConfiguration.Logger` already does, and let each adapter bridge it to its platform's
  logger.
- `FabricDataFolder` uses text blocks and `YamlDefaultsUpdater` uses `String.isBlank`/`repeat`, which
  are not Java 8. `gp3d-core`'s `Compat` covers the latter.
- Run `checkUniversal` afterwards: the Fabric boot and linkage checks must still pass on every release.

Nukkit does not need these Fabric pieces:

- `FabricPermissionDefaults`: Nukkit resolves `nukkit.yml`'s permission tree itself, and LuckPerms has a
  Nukkit build.
- `FabricPlayerLookup`: `Server.lookupName` is Nukkit's local name→UUID store, the counterpart of
  `usercache.json`.
- Most of `FabricLastSeenStore`: Nukkit already records `IPlayer.getLastPlayed()`.

## Phase 2: claims on Nukkit

- [ ] Load claims from `plugins/GriefPreventionData` (`Server.getPluginPath()`), Paper's layout, not
      Nukkit's default `plugins/GriefPrevention3D` data folder.
- [ ] World keys are `Level.getFolderName()` (`world`, `nether`, `the_end`). Heights come from each
      level's `getMinBlockY()`/`getMaxBlockY()` (overworld −64 to 319, nether 0 to 127, end 0 to 255).
- [ ] Claim tools: `Item.fromString` resolves Paper's `GOLDEN_SHOVEL` and `STICK`. Check every
      material name the config accepts, since Nukkit uses numeric ids and its constant names do not
      always match Bukkit's.
- [ ] Claim creation, resizing and inspection from `PlayerInteractEvent` (`LEFT_`/`RIGHT_CLICK_BLOCK`).
- [ ] Visualization: fake blocks through `UpdateBlockPacket` with runtime ids from
      `GlobalBlockPalette`, restored with `Level.sendBlocks`. Bedrock has no block displays, so there
      is no glowing mode.
- [ ] Claim block accrual on a `ServerScheduler.scheduleRepeatingTask` timer.

## Phase 3: commands

- [ ] Register commands from `alias.yml` with `SimpleCommandMap.register`, one `Command` per root,
      as `FabricCommandRegistrar` does. Nukkit's default overload is already a single optional
      `RAWTEXT` argument.
- [ ] Bedrock gamertags may contain spaces. Nukkit's command map honors double quotes
      (`/trust "Some Player"`), so name arguments must accept quoted names.
- [ ] Bedrock clients complete commands from the overloads and enums sent at login, not from server
      callbacks. Offer subcommands and trust levels as `CommandEnum`s.

## Phase 4: protection

Each Paper rule maps to a Nukkit event:

| Protection | Nukkit hook |
|-----------|-------------|
| Building and breaking | `BlockBreakEvent`, `BlockPlaceEvent` |
| Containers, doors, buttons, levers | `PlayerInteractEvent` (`RIGHT_CLICK_BLOCK`), `InventoryOpenEvent` |
| Farmland trampling | `PlayerInteractEvent` with `Action.PHYSICAL` |
| Buckets | `PlayerBucketEmptyEvent`, `PlayerBucketFillEvent` |
| Signs | `SignChangeEvent`, `SignColorChangeEvent`, `SignGlowEvent` |
| Item frames (blocks on Bedrock) | `PlayerInteractEvent` on `BlockItemFrame`, `ItemFrameDropItemEvent` |
| Armor stands, paintings, vehicles | `PlayerInteractEntityEvent`, `EntityDamageByEntityEvent`, `VehicleDamageEvent`, `VehicleDestroyEvent` |
| Creatures, PvP, projectiles | `EntityDamageByEntityEvent`, `EntityDamageByChildEntityEvent`, `PvpProtectionPolicy` |
| Explosions | `EntityExplodeEvent`, `BlockExplodeEvent` (`setBlockList`) |
| Fluid flow | `LiquidFlowEvent`, `FluidFlowPolicy` |
| Pistons | `BlockPistonEvent` (`getBlocks`, `getDestroyedBlocks`), `PistonMovementPolicy` |
| Fire | `BlockIgniteEvent` (`SPREAD`, `LAVA`, …), `BlockBurnEvent`, `FirePolicy` |
| Tree growth | `StructureGrowEvent` |
| Ender pearls, chorus fruit | `PlayerTeleportEvent` (`ENDER_PEARL`, `CHORUS_FRUIT`) |
| Hoppers | `InventoryMoveItemEvent` |
| Dispensers | No event; wrap each behavior in `DispenseBehaviorRegister` |

The rules themselves are already in `gp3d-core` (`com.griefprevention.protection`). The adapter only
maps Nukkit blocks and entities to claims.

## Nukkit differences to keep in mind

- **No mob AI.** Cloudburst Nukkit's mobs do not move or attack unless a plugin such as MobPlugin adds
  AI. Rules about pets, polar bears or endermen only matter on servers that run one.
- **Item frames are blocks.** They have block entities, not entity hitboxes.
- **Numeric ids.** Blocks and items are identified by legacy numeric ids and meta, not namespaced names.
- **Permission children are always granted.** A child listed as `false` is still granted, so
  `plugin.yml` must never use one (`NukkitDescriptorTest` enforces this).
- **Identity.** A player's UUID comes from the Xbox identity in the login chain. With `xbox-auth=off`
  the client supplies it unchecked, much like an offline-mode Java server, so claims are only as safe
  as the server's authentication. Tell operators to keep `xbox-auth=on`, or to use a proxy that
  authenticates players.
- **Economy.** There is no Vault. Paid `/claimpvp` and `/transferclaim` need an EconomyAPI bridge, or
  stay free on Nukkit.
