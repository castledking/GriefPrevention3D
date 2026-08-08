# GriefPrevention3D Fabric 1.21.11

This module is the first native Fabric target for GriefPrevention3D.

Current scope:

- Fabric Loader `0.19.3`
- Minecraft `1.21.11`
- Fabric API `0.141.6+1.21.11`
- Java `21`
- Mojang mappings through Fabric Loom

The Java 8-safe universal entrypoint selects this Minecraft-specific adapter without loading adapter
classes for any other platform or game version. The adapter loads the shared `gp3d-core` module,
registers Fabric block and explosion protection hooks, registers first-pass claim tool visualization
hooks, and reads/writes the Bukkit `GriefPreventionData` flat-file format for manual testing.

Build:

```bash
./gradlew :fabric-1.21.11:build
```

Universal-jar tracer build and verification:

```bash
./gradlew universalJar checkUniversal
```

This produces `build/libs/GriefPrevention3D-Universal-<version>.jar`. The same physical file can be
placed in a Bukkit server's `plugins` directory or a Fabric 1.21.11 server's `mods` directory. It
contains both platform descriptors, one relocated copy of the shared core, a Java 8 Bukkit/bootstrap
surface, and the Java 21 Fabric 1.21.11 adapter. `checkUniversal` verifies byte-identical Bukkit and
Fabric inputs, loads the Bukkit entry classes on a real Java 8 runtime, and boot-smokes the exact jar
with production mappings through Fabric Loader. This is the first-version tracer; older Fabric
adapters and the complete advertised Fabric version range are not packaged yet.

Shared Paper/Fabric data folder:

`plugins/GriefPreventionData`

```text
GriefPreventionData/
  _fabricDataLocation
  _schemaVersion
  config.yml
  messages.yml
  ClaimData/
    _nextClaimID
    1.yml
  PlayerData/
```

Fabric now uses Paper's datastore location directly, so switching loaders does not require copying files. On the
first upgraded Fabric boot, an existing `config/GriefPreventionData` tree is copied to a staging directory, the
complete claim graph is validated, and the copy is atomically promoted to `plugins/GriefPreventionData`. The old
`config` tree remains untouched as a rollback backup. If both locations already exist without a completed import
marker, startup fails closed instead of guessing which data is authoritative.

`config.yml` and `messages.yml` are seeded under the same roots as the Paper plugin. They currently contain only
the Fabric-wired subset and are not overwritten after creation. Fabric reads the existing upstream explosion
keys without rewriting any unrelated config or addon fields.

Claim YAML is decoded through the platform-neutral `gp3d-core` document codec. Shaped corners, nested
subdivisions, 2D/3D state, trust, inheritance flags, explosion/PvP/alert flags, modified dates, and unknown addon
fields survive semantic round trips. Player files under `PlayerData` use the same shared four-line decoder on
Paper and Fabric. Fabric reads accrued and personal bonus entitlements lazily, derives used blocks from exact
top-level claim area, and leaves every player file byte-for-byte untouched during claim mutations. A malformed
player record fails that player's mutation closed rather than overwriting the record.

Permission-group bonus files named `PlayerData/$<permission>` are loaded with Bukkit-compatible integer
semantics. When LuckPerms is installed, online-player permissions contribute those bonuses through its optional
API; the universal jar keeps no hard LuckPerms runtime dependency. Without a permission provider, group bonuses
remain zero, matching Bukkit's offline/no-applicable-permission behavior.

An older or unversioned YAML layout is migrated only after the complete claim graph validates. Before promotion,
the existing data folder is copied under `GriefPreventionData/MigrationBackups/`. Invalid, ambiguous, or newer
schemas abort Fabric protection startup instead of silently loading an empty or partial claim set.

Claim files follow the Paper plugin's flat-file shape:

```yaml
Claim ID: '1'
Lesser Boundary Corner: world;0;-64;0
Greater Boundary Corner: world;15;320;15
Owner: 00000000-0000-0000-0000-000000000000
Builders: []
Containers: []
Accessors:
- public
Managers: []
Parent Claim ID: -1
inheritNothing: false
inheritNothingForNewSubdivisions: false
Is3D: false
Explosives Allowed: false
Wither Explosions Allowed: false
PvP Enabled: true
Alerts Enabled: true
Modified Date: 1779681984295
```

Temporary admin commands:

- `/gp3d status`
- `/gp3d reload`
- `/gp3d claimhere`
- `/gp3d claim create <radius>`
- `/gp3d claim blocks`
- `/gp3d claim list`
- `/gp3d claim abandon`
- `/gp3d claim trust <public|uuid|online-player> <access|container|build|manage|neighbor>`
- `/gp3d claim untrust <public|uuid|online-player>`

`claim create` currently creates a top-level rectangular 2D claim centered on the executor and writes it under
`ClaimData`. `claim abandon` removes the claim at the executor's current block and writes the updated files.
`claim trust` and `claim untrust` update the claim at the executor's current block. Trust targets are currently
limited to `public`, a UUID, or an online player name.
This is a temporary test path for native Fabric persistence. Claim creation enforces the same derived
accrued + personal bonus + applicable group bonus - top-level claim area balance as Bukkit. Player limits,
playtime accrual, economy operations, and administrative balance commands are not wired yet.

Temporary claim tool coverage:

- Right-clicking a block with a stick inspects the claim at that block.
- Right-clicking unclaimed land with a golden shovel starts a two-corner basic claim creation session.
- Right-clicking an owned claim corner with a golden shovel starts a resize session; the next golden shovel
  right-click moves that corner.
- Claim creation and resize enforce a temporary 5x5 minimum, enforce the owner's available claim blocks, and
  write directly to `ClaimData`. Resizing refunds the previous top-level area before charging the replacement;
  subdivisions do not consume additional blocks.
- The selected claim is shown with client-only fake block updates, using the same corner/side block language as
  the Bukkit fake block visualization.
- Fake blocks are only sent to the interacting player and are restored automatically after 60 seconds or when a new
  claim visualization replaces them.
- Fake block markers are re-sent after tool right-clicks so vanilla use acknowledgements cannot erase the clicked
  corner, and individual markers are removed only after a successful block break.
- BlockDisplay/glowing visualization is intentionally not wired yet; it will sit behind the future Fabric config.

Temporary protection coverage:

- Block breaks require build trust.
- Right-clicks with block/fluid/entity-placement style items require build trust at the clicked block and adjacent
  placement target.
- Block-entity right-clicks require container trust.
- Other block right-clicks require access trust.
- Entity attacks require build trust.
- Entity right-clicks require container trust, or build trust when the held item is placement/build-like.
- TNT, creeper, wither, and other destructive explosions filter affected blocks using the same global config,
  per-claim flags, creative-world rule, and overworld sea-level threshold as the Bukkit implementation.
- Trigger-only explosions require access trust; ownerless projectiles originating inside the same claim retain
  upstream's dispenser exception.

Manual explosion/migration gate:

1. Back up a Paper `plugins/GriefPreventionData` fixture. Leave it in place when switching the same server to
   Fabric. Include a shaped top-level claim, nested 3D subdivisions, trust, non-default policy flags, and player
   balances.
2. Boot Fabric 1.21.11 and confirm all claims with `/gp3d claim list` and the stick inspection tool.
3. Test TNT and creeper damage with `Explosives Allowed` both `false` and `true`; test wither and wither-skull
   damage independently with `Wither Explosions Allowed` both `false` and `true`.
4. Check `/gp3d claim blocks`, then create, resize, and abandon a claim on Fabric. Confirm insufficient create and
   resize attempts are denied, while shrinking and abandoning make the exact area available again.
5. Stop the server, then boot Paper without moving the data. Verify geometry, graph relationships, policies,
   trust, unknown fields, byte-identical player entitlement files, and the same remaining claim-block result.

Next implementation slices:

1. Apply permission-provider identifiers to trust subjects in addition to the claim-block LuckPerms bridge.
2. Add denial feedback messages with rate limiting.
3. Expand protection hooks into fluid spread, piston, and remaining entity environmental paths.
4. Expand player-facing claim tools into subdivision and richer selection sessions.
5. Add player limits, playtime accrual, non-default abandon-return behavior, and legacy/database import tooling.
