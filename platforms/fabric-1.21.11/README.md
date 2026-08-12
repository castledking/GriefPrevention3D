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
the Fabric-wired subset. On each Fabric boot, newly shipped keys missing from an existing file are inserted while
existing values, comments, ordering, and unknown addon fields remain untouched. Legacy claim-block key values are
carried into their newer Paper-shaped keys instead of being reset to defaults. Fabric reads the existing upstream
explosion keys without rewriting any unrelated config or addon fields.

Claim YAML is decoded through the platform-neutral `gp3d-core` document codec. Shaped corners, nested
subdivisions, 2D/3D state, trust, inheritance flags, explosion/PvP/alert flags, modified dates, and unknown addon
fields survive semantic round trips. Player files under `PlayerData` use the same shared four-line decoder on
Paper and Fabric. Fabric reads accrued and personal bonus entitlements lazily, derives used blocks from exact
top-level claim area, and leaves every player file byte-for-byte untouched during ordinary claim mutations. When
`Claims.AbandonReturnRatio` is not `1.0`, abandoning an owned top-level claim atomically adjusts only the accrued
block line with Bukkit's exact ceiling arithmetic; legacy lines, line endings, and addon data remain unchanged.
A malformed or concurrently changed player record fails the mutation closed rather than being overwritten, and
the claim deletion is rolled back if the entitlement update cannot be committed.

Playtime claim blocks use Bukkit's global ten-minute cadence and integer division, so the default 100-block
hourly rate delivers 16 blocks per check (96 over six uninterrupted checks). The first check treats a player as
active unless they are riding or in liquid; later checks apply `Accrued Idle Threshold` movement detection and
`AccruedIdlePercent`. Accrual applies in every world—including creative and claim-disabled worlds—matching
Bukkit. Each delivered award participates immediately in balance and claim mutations and is atomically written
to only the accrued-block line before the delivery completes. Disconnect and server shutdown retry any award
whose write failed.
The configured maximum uses Bukkit's ordinary integer/cap behavior.
Also like Bukkit, a zero-or-negative hourly rate at startup does not schedule the task; changing it positive then
requires a server restart.

Permission-group bonus files named `PlayerData/$<permission>` are loaded with Bukkit-compatible integer
semantics. When LuckPerms is installed, online-player permissions contribute those bonuses through its optional
API and `griefprevention.overrideclaimcountlimit` bypasses the configured per-player claim cap. Minecraft
operators receive the same bypass without LuckPerms. The universal jar keeps no hard LuckPerms runtime
dependency. Without a permission provider, group bonuses remain zero, matching Bukkit's
offline/no-applicable-permission behavior. The same bridge honors `griefprevention.accruals` (default allowed)
and `griefprevention.accruals.afkbypass` (default operator-only), including explicit LuckPerms denials.
Paper-compatible `[permission.node]` entries in Builders, Containers, Accessors, and Managers are resolved through
that same optional bridge for block/entity protections and explosion-trigger access. General and level-specific
permission-node deny entries are included in the subject evaluation. The temporary trust commands accept either
the quoted bracketed form or a bare dotted permission and persist the bracketed Bukkit representation. Player
names resolve through the online list and Minecraft's previously seen-player cache. Permission-node grants require
`griefprevention.permissiontrust`; on Fabric it inherits from `griefprevention.adminclaims`, with operator status
as the no-provider default. Manage grants use the renamed `griefprevention.managetrust` permission.

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
- `/gp3d claim trust <public|uuid|known-player|permission.node> <access|container|build|manage|neighbor>`
- `/gp3d claim untrust <public|uuid|known-player|permission.node>`

`claim create` currently creates a top-level rectangular 2D claim centered on the executor and writes it under
`ClaimData`. `claim abandon` removes the claim at the executor's current block and writes the updated files.
`claim trust` and `claim untrust` update the claim at the executor's current block. Trust targets may be `public`,
a UUID, a current or previously seen player name, a bare dotted permission node, or `"[permission.node]"`
(quotes are required by Fabric's command parser for the bracketed form). Command success replies are sent only
to the executor rather than broadcast to other operators.
This is a temporary test path for native Fabric persistence. Claim creation enforces the same derived
accrued + personal bonus + applicable group bonus - top-level claim area balance as Bukkit. The upstream
`MaximumNumberOfClaimsPerPlayer` limit and non-default `AbandonReturnRatio` behavior are wired. Playtime accrual,
including its cap and idle rules, is also wired. Economy operations and administrative balance commands are not
wired yet.

Temporary claim tool coverage:

- Right-clicking a block with a stick inspects the claim at that block.
- Right-clicking unclaimed land with a golden shovel starts a two-corner basic claim creation session.
- The first claim corner is rejected at the configured top-level claim-count limit unless the player has the
  upstream override permission (or operator status); persistence rechecks the limit before creating the claim.
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
   resize attempts are denied. Test a positive `MaximumNumberOfClaimsPerPlayer` with and without the operator or
   LuckPerms override. With the default return ratio, shrinking and abandoning make the exact area available.
   With `AbandonReturnRatio: 0.5`, abandoning a 25-block claim removes 13 accrued blocks, matching Bukkit's
   ceiling behavior.
5. Stop the server, then boot Paper without moving the data. Verify geometry, graph relationships, policies,
   trust, unknown fields, and the same remaining claim-block result. Player files must stay byte-identical at the
   default return ratio; at a non-default ratio, only the accrued-block line may differ.
6. Set `Claim Blocks Accrued Per Hour.Default` to `600`, remain active through one global ten-minute check, and
   confirm `/gp3d claim blocks` increases by 100. Restart without a disconnect callback, then boot Fabric or
   Paper and confirm that exact accrued balance persisted. Repeat while stationary with a positive idle threshold and both zero/non-zero
   `AccruedIdlePercent`; operators bypass the idle reduction by default.
7. With LuckPerms installed, grant a second player `gp3d.test.container`, then run
   `/gp3d claim trust gp3d.test.container container` from inside a claim. Confirm the second player can open a
   container but cannot place or break blocks. Restart Fabric, repeat the check, and confirm the claim YAML stores
   `gp3d.test.container` as `[gp3d.test.container]`. Switch to Paper and verify the same permission trust works.

Next implementation slices:

1. Add denial feedback messages with rate limiting.
2. Expand protection hooks into fluid spread, piston, and remaining entity environmental paths.
3. Expand player-facing claim tools into subdivision and richer selection sessions.
4. Add administrative/economy balance operations and legacy/database import tooling.
