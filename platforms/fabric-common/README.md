# GriefPrevention3D on Fabric

One universal jar serves Bukkit/Paper and every supported Fabric release. On Fabric it carries one
adapter per Minecraft range and starts the one that matches the running game:

| Minecraft | Adapter | Compiled against | Java | Package in the universal jar |
|-----------|---------|------------------|------|------------------------------|
| 1.21.11 | `mc1_21_11` | 1.21.11 (Mojang names, remapped to intermediary) | 21 | `com.griefprevention.fabric` |
| 26.1 – 26.3.x | `mc26_1` | 26.1 (unobfuscated) | 25 | `com.griefprevention.fabric.mc26_1` |

The jar needs Fabric Loader `>=0.18.2` and these Fabric API modules, all part of the regular Fabric API
download: `fabric-api-base`, `fabric-command-api-v2`, `fabric-events-interaction-v0`,
`fabric-lifecycle-events-v1`, `fabric-networking-api-v1`.

## Layout

- `platforms/fabric-bootstrap` — Java 8. `UniversalFabricBootstrap` reads the adapter index
  (`META-INF/griefprevention3d/fabric-adapters.properties`), matches the running Minecraft version with
  Fabric Loader's version predicates, and starts that adapter. `AdapterMixinGate` is the plugin of every
  adapter's mixin config and vetoes the configs of adapters that were not selected, before Mixin looks up
  a single target class.
- `platforms/fabric-common` — the adapter sources, tests and resources shared by every Minecraft target.
- `platforms/fabric-1.21.11` — compiles the shared sources against 1.21.11 with the remapping Loom
  plugin.
- `platforms/fabric-26.1` — compiles the same sources against 26.1 with the unobfuscated Loom plugin, and
  relocates the result under `com.griefprevention.fabric.mc26_1` for the universal jar.
- `platforms/fabric-linkage-check` — a build-time tool, never shipped (see below).
- `platforms/fabric-module.gradle.kts` and `platforms/fabric-universal.gradle.kts` — the shared module
  build logic and the universal-jar assembly and checks.

A module may add version-specific sources under its own `src/`; so far none is needed. Where
Minecraft renamed or removed something within a range, the shared code avoids it: blocks and entity
types are looked up by id (26.2 removed fields such as `Blocks.WHITE_WOOL` and moved entity types to
`EntityTypes`), tools are recognized by item tag (26.3 removed `AxeItem`, `HoeItem` and `ShovelItem`),
and mixins whose target class was renamed (`FarmBlock` → `FarmlandBlock`, `EnderMan` → `Enderman`) list
both names under `@Pseudo`, with `@Group` requiring that exactly one alternative applies.

## Building and verifying

```bash
./gradlew universalJar            # build/libs/GriefPrevention3D-Universal-<version>.jar, for plugins/ and mods/ alike
./gradlew checkUniversal          # everything below
```

`build/libs/GriefPrevention3D.jar` is only the Bukkit input the universal jar is assembled
from; Fabric reports it as a "non-fabric mod" and ignores it. A release renames the universal jar
to `GriefPrevention3D.jar` when it publishes it.

- `checkUniversalJar` — metadata, adapter index, mixin configs, bytecode levels, byte-identical Bukkit
  and adapter inputs, that each adapter only names Minecraft classes in its own namespace, and that the
  jar stays under 5 MB (it is about 1.9 MB).
- `checkUniversalLegacyClassLoading` — loads the Bukkit entry classes on a real Java 8 runtime.
- `checkFabricBoot` — boots the exact jar on every release in `fabricTargets`
  (`platforms/fabric-universal.gradle.kts`): 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. Each server
  is installed once, by the official Fabric server launcher, under
  `~/.gradle/caches/griefprevention3d/fabric-servers/` and reused. The boot runs with
  `-Dgriefprevention3d.verifyMixins=true`, which loads every class the selected adapter's mixins target
  so a broken injection fails the boot instead of waiting for the first explosion or piston.
- `checkFabricLinkage` — the JVM links lazily, so code the boot never runs can still hold a
  `NoSuchFieldError`. `FabricLinkageCheck` resolves every class, field, method, lambda and mixin
  (targets, `@Shadow`, injector selectors, `@At` targets, invokers) of each adapter against the server
  jar, libraries, Fabric Loader and Fabric API of every release in its range, the way the JVM would.
- `checkUniversalNukkit` and `checkNukkitBoot` — the Nukkit adapter the same jar carries; see
  [`platforms/nukkit/README.md`](../nukkit/README.md).

To claim a new release, add it to `fabricTargets` and widen the adapter's range in `gradle.properties`.
If the linkage check or boot fails, either keep the shared code on APIs every release has, or add a new
adapter module compiled against the release that broke compatibility.

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

Player-facing text is read from `messages.yml` rather than hardcoded. Both shapes Paper accepts are understood: a
key mapped straight to its text, and the legacy `<Key>.Text` sub-key. A key missing from the file falls back to the
same default the Paper plugin ships, and an unreadable or malformed file logs a warning and falls back to defaults
rather than failing startup. Unknown addon keys are preserved on read. As on Paper, `&`/`$` prefix codes,
`&#RRGGBB` hex colors, and literal `\n` escapes are translated when the file is read — before placeholder
arguments are substituted, so a player name cannot inject formatting — and blanking a message disables it.
`/gpreload` re-reads `messages.yml` alongside claim data.

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
permission-node deny entries are included in the subject evaluation. The trust commands accept either the
bracketed form or a bare dotted permission and persist the bracketed Bukkit representation. Permission-node
grants require `griefprevention.permissiontrust`, which `griefprevention.adminclaims` brings with it, as on Paper.

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

Commands work as on Paper. `/claim` and `/aclaim`, their subcommand names, option aliases, usage text and
the standalone names all come from `alias.yml`; Paper's plugin.yml names and aliases (`/tr`, `/at`, `/acb`,
`/scb`, ...) are registered too. Like LuckPerms on Fabric, each command takes its arguments as one string that
GriefPrevention splits itself, so `[permission.node]` targets and negative amounts need no quoting. Permission
nodes and their defaults follow plugin.yml, with or without LuckPerms: `griefprevention.claims` and its children
for everyone, `griefprevention.admin.*` children (and `griefprevention.adminclaims`) for operators. A command
that fails is logged with its stack trace instead of vanilla's silent "unexpected error". Commands are
registered after other mods', so a name another mod already uses (such as `/create`) is left to that mod.

| Command | What it does |
|---------|--------------|
| `/claim [create [radius]]` | Claims a square around you: the minimum claim area, or the given radius. |
| `/trust <player> [access\|container\|build\|manage]`, `/accesstrust`, `/containertrust`, `/managetrust` | Trusts a player, `public`, or a `[permission]` in the claim you stand in (needs manage trust there), or in all your claims when you stand outside them. |
| `/permissiontrust <permission> <type>`, `/aclaim trust permission <permission> <type>` | Trusts a permission node. |
| `/untrust <player\|all\|public>` | Revokes trust the same way; only the owner may clear everyone or demote a manager. |
| `/trustlist` | Lists the trust in the claim you stand in. |
| `/claimslist [player]` | Claim-block math and claims; another player's needs `griefprevention.claimslistother`. |
| `/abandonclaim`, `/abandontoplevelclaim`, `/abandonallclaims confirm`, `/claim abandon [all\|toplevel]` | Abandons claims you own. |
| `/claimpvp [true\|false] [confirm]` | Per-claim PvP, when `Claims.PvPToggle` allows it. |
| `/adminclaims`, `/aclaim mode admin` / `/basicclaims`, `/claim mode basic` | Switches the modification tool to administrative claims and back (needs `griefprevention.adminclaims`). |
| `/adjustbonusclaimblocks <player\|[permission]> <amount>` (`/acb`) | Adds bonus blocks to a player, or to everyone with a permission (`PlayerData/$<permission>`). |
| `/adjustbonusclaimblocksall <amount>` (`/acball`) | Adds bonus blocks to every online player. |
| `/setaccruedclaimblocks <player> <amount>` (`/scb`), `/setaccruedclaimblocksall <amount>` (`/scball`) | Sets accrued blocks, ignoring the accrual cap as Bukkit does. |
| `/aclaim blocks <bonus\|accrued> <player\|all> <amount>` | Adds to either balance. |
| `/gpreload`, `/gpstatus` | Reloads claims, messages, settings and `alias.yml`; shows the loaded claim count. |

Player names resolve from online players, then the players the server has seen (vanilla's `usercache.json`), then
a UUID, as Paper only knows players who have logged in. Mojang is never asked, so an offline-mode server gets
the UUID the player actually joins with. A name no player has, containing a dot, is read as a permission node.

The balance commands need `griefprevention.adjustclaimblocks`, work from the console, and change only the accrued
and bonus lines of `PlayerData/<uuid>`, so unknown addon data survives. Paper subcommands Fabric does not have yet
(subdivision and shaped modes, restore nature, siege, trapped, explosion toggles, buying and selling blocks, admin
deletion and transfer) answer that they are not available on Fabric.

Administrative claims are free, count toward nobody's claim limit, and are drawn in Paper's admin colors. Staff
with `griefprevention.adminclaims` hold every permission in them: they can build, resize, trust and abandon. The
claim mode lasts for the session, as Paper's does.

Claim tools (configured by `Claims.InvestigationTool` and `Claims.ModificationTool`; Paper material
names such as `STICK`, legacy names such as `GOLD_SPADE`, and namespaced item ids all work):

- Right-click a block with the investigation tool, or anything up to 100 blocks away, to see who owns
  it (`BlockClaimed` / `BlockNotClaimed`) and the claim's boundary. Players with
  `griefprevention.seeclaimsize` (operators by default) also see its size. Pointing at nothing within
  100 blocks answers `TooFarAway`.
- Sneak and right-click with it to show every top-level claim within 150 blocks (`ShowNearbyClaims`),
  when allowed by `griefprevention.visualizenearbyclaims` (default true).
- Right-click unclaimed land with the modification tool to start a claim (`ClaimStart`) and again at
  the opposite corner to create it (`CreateClaimSuccess`). `Claims.MinimumWidth` and
  `Claims.MinimumArea` apply, as do claim blocks, the claim-count limit, overlap checks and
  `griefprevention.createclaims`. Right-click a corner of your claim to resize it (`ResizeStart`,
  `ClaimResizeSuccess`). Clicking inside someone else's claim, or inside your own away from a corner,
  explains why and shows the claim in conflict colors.
- Taking the modification tool in hand reports your remaining claim blocks (`RemainingBlocks`) and
  shows the claim you stand in; putting it away cancels a half-set claim.
- Staff with `griefprevention.seeinactivity` or `griefprevention.deleteclaims` also see how long ago a
  claim's owner was last online (`PlayerOfflineTime`). Fabric has no record of this, so the times are kept
  in `LastSeen.yml` in the data folder, updated as players join and leave; owners not seen since that
  file appeared are left out of the inspection.
- Tools do nothing in worlds whose `Claims.Mode` is `Disabled`, where the modification tool answers
  `ClaimsDisabledWorld`.

Boundary visualization:

- Boundaries are drawn with client-only fake blocks in Paper's colors, sent to the viewing player only,
  cleared after 60 seconds or by the next visualization, and re-sent a tick after the tool click.
- With `VisualizationGlow: true`, every marker also gets a glowing block-display outline in Paper's glow
  colors. The displays exist only as packets to the viewing player, so no one else sees them and nothing
  is left in the world.
- A visualization remembers what it shows. When a claim in it is resized or abandoned, or a claim is
  created near an overview, it is redrawn from the current claims.

Protection coverage (Paper's rules and config keys; environmental rules apply in worlds whose
`Claims.Mode` is not `Disabled`):

- Breaking blocks needs build trust. Placing blocks, and right-clicking with items that change the world
  (buckets, flint and steel, bone meal, spawn eggs, frames, stands, minecarts, boats, lily pads, end
  crystals, dyes, ink sacs, honeycomb, brushes) needs build trust at the clicked block and where the
  item acts. Axes, hoes and shovels need build trust on blocks they would strip, till or flatten, but not
  on doors, gates, buttons, levers or blocks with a menu.
- Emptying or filling buckets, and setting boats, lily pads or spawn eggs on water, are checked where the
  item looks, and the client's prediction is undone when refused.
- Containers and other block entities need container trust, as do cake, cauldrons, respawn anchors,
  berry bushes, glow berries, pumpkins and anvils. Editing an unwaxed sign needs build trust; waxed
  signs stay clickable so their commands work. Other blocks need access trust.
- Item frames and armor stands need build trust to use; other entities need container trust.
- Monsters are never protected. Item frames, paintings, armor stands, end crystals and vehicles in a
  claim need build trust to damage, by hand or by projectile; villagers too while
  `Claims.ProtectCreatures` is on. Other creatures need container trust (`NoDamageClaimedEntity`); a pet's
  owner may still hurt it. Projectiles from mobs and dispensers cannot hurt them either.
- PvP: a claim protects the players in it according to `PvP.ProtectPlayersInLandClaims`
  (`CantFightWhileImmune`, `PlayerInPvPSafeZone`). With `Claims.PvPToggle.Claim.Enabled` or
  `.Subdivision.Enabled`, `/claimpvp` lets a claim decide for itself, stored exactly as Paper stores it.
- Explosions: TNT, creepers, withers and other explosions filter claimed blocks with the same global
  config, per-claim flags, creative-world rule and sea-level threshold as Paper, and never hurt protected
  entities in a claim. Trigger-only explosions need access trust, with Paper's same-claim dispenser
  exception.
- Fluids never flow into a claim from outside it, following Paper's documented flow matrix; straight
  down is always allowed.
- Pistons follow `PistonMovement` (`CLAIMS_ONLY` by default, so pistons only move blocks inside their own
  claim; also `EVERYWHERE`, `EVERYWHERE_SIMPLE`, `IGNORED`, and the legacy flags). Placing a piston
  outside claims warns with `NoPistonsOutsideClaims`.
- Fire follows `FireSpreads`, `FireDestroys`, `Claims.FireSpreadsInClaims` and
  `Claims.FireDamagesInClaims`, never crosses owners, and a fire that may not burn or spread is put out
  unless it sits on an infiniburn block. Lava ignites nothing while fire may not spread.
- Dispensers and droppers only act within their own claim, or wilderness to wilderness.
- Farmland is trampled only by players with build trust; creatures never trample it where claims apply,
  and elsewhere only with `CreaturesTrampleCrops`.
- Endermen pick up no blocks unless `EndermenMoveBlocks` is on.

Denial feedback:

- A refused block break, block use, entity attack, or entity interaction now tells the player why, using the same
  `NoBuildPermission`, `NoContainersPermission`, and `NoAccessPermission` text as Paper, in red chat.
- The `{0}` placeholder resolves to the claim owner's name. Subdivisions inherit the name from their top-level
  parent, and admin claims use `OwnerNameForAdminClaims`. Names come from the online player list and Minecraft's
  seen-player cache only; no profile fetch is issued from the interaction path, so an owner the server has never
  seen falls back to their UUID.
- Protection denials are rate limited to one message per player per ten seconds across all denials, matching
  Bukkit's `sendRateLimitedErrorMessage`, so holding a mouse button inside someone else's claim cannot flood chat.
  A player's cooldown is dropped on disconnect.
- Claim tool and `/claim create` denials — claim count limit, minimum size, insufficient claim blocks, and
  overlap — are sent unthrottled through the same message keys, matching upstream's choice to send those directly
  since each requires a deliberate action. These moved from the action bar to red chat to match Paper.
- Explosion-trigger denials are not wired to feedback yet; upstream is silent on that path too.

Manual explosion/migration gate:

1. Back up a Paper `plugins/GriefPreventionData` fixture. Leave it in place when switching the same server to
   Fabric. Include a shaped top-level claim, nested 3D subdivisions, trust, non-default policy flags, and player
   balances.
2. Boot Fabric (1.21.11 or any 26.x release) and confirm the claims with `/claimslist <owner>` and the stick
   inspection tool.
3. Test TNT and creeper damage with `Explosives Allowed` both `false` and `true`; test wither and wither-skull
   damage independently with `Wither Explosions Allowed` both `false` and `true`.
4. Check `/claimslist`, then create, resize, and abandon a claim on Fabric. Confirm insufficient create and
   resize attempts are denied. Test a positive `MaximumNumberOfClaimsPerPlayer` with and without the operator or
   LuckPerms override. With the default return ratio, shrinking and abandoning make the exact area available.
   With `AbandonReturnRatio: 0.5`, abandoning a 25-block claim removes 13 accrued blocks, matching Bukkit's
   ceiling behavior.
5. Stop the server, then boot Paper without moving the data. Verify geometry, graph relationships, policies,
   trust, unknown fields, and the same remaining claim-block result. Player files must stay byte-identical at the
   default return ratio; at a non-default ratio, only the accrued-block line may differ.
6. Set `Claim Blocks Accrued Per Hour.Default` to `600`, remain active through one global ten-minute check, and
   confirm the accrued figure in `/claimslist` increases by 100. Restart without a disconnect callback, then boot Fabric or
   Paper and confirm that exact accrued balance persisted. Repeat while stationary with a positive idle threshold and both zero/non-zero
   `AccruedIdlePercent`; operators bypass the idle reduction by default.
7. With LuckPerms installed, grant a second player `gp3d.test.container`, then run
   `/trust gp3d.test.container container` from inside a claim. Confirm the second player can open a
   container but cannot place or break blocks. Restart Fabric, repeat the check, and confirm the claim YAML stores
   `gp3d.test.container` as `[gp3d.test.container]`. Switch to Paper and verify the same permission trust works.
8. As a second, untrusted player, break a block inside another player's claim and confirm the red
   `You don't have <owner>'s permission to build here.` reply naming the real owner. Repeat inside a subdivision
   and confirm it names the parent claim's owner, and inside an admin claim to confirm `an administrator`. Open a
   container for the access/container wording. Hold the break key for a minute and confirm at most one message per
   ten seconds. Edit `NoBuildPermission` in `messages.yml` to include `&6` and a `\n`, run `/gpreload`, and
   confirm the color and line break render; blank the value and confirm the message stops being sent.

9. With the investigation tool, sneak and right-click to see nearby claims; resize one with the shovel and
   confirm the overview redraws. Set `VisualizationGlow: true`, `/gpreload`, and confirm the glowing
   outlines appear only for you and vanish after a minute.
10. As an untrusted player next to a claim: pour lava and water toward it, push blocks into it with a
    piston from outside, light a fire against its wall (with `FireSpreads` on), aim a dispenser of water
    into it, shoot its animals, frames and armor stands with a bow, and trample its farmland. Each must fail.
11. Commands: standing in someone else's claim, `/trust <yourself>` and `/abandonclaim` must be refused. As the
    owner, `/trust <friend>`, `/trust <friend> access`, `/trust [gp3d.vip] container` and `/untrust <friend>`
    must answer with Paper's messages, and `/trustlist` must show them. From the console run
    `acb <player> 250`, `acb [gp3d.vip] 50`, `scb <player> 1000` and `aclaim blocks accrued <player> -100`, and
    compare `claimslist <player>` and `PlayerData/<uuid>` after each. As an operator, `/adminclaims`, claim land
    with the shovel, confirm it cost nothing and a non-operator cannot build there, then `/basicclaims`.
12. With the stick, inspect a claim whose owner is offline and confirm `Last login: N days ago.`; restart and
    confirm the time survives.

See `ROADMAP.md` for what is still missing.
