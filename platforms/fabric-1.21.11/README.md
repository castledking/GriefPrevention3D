# GriefPrevention3D Fabric 1.21.11

This module is the first native Fabric target for GriefPrevention3D.

Current scope:

- Fabric Loader `0.18.2`
- Minecraft `1.21.11`
- Fabric API `0.139.4+1.21.11`
- Java `21`
- Mojang mappings through Fabric Loom

The current entrypoint loads the shared `gp3d-core` module, registers Fabric block protection hooks,
registers first-pass claim tool visualization hooks, and reads/writes a Bukkit-style
`GriefPreventionData` folder for manual testing.

Build:

```bash
./gradlew :fabric-1.21.11:build
```

Temporary data folder:

`config/GriefPreventionData`

```text
GriefPreventionData/
  config.yml
  messages.yml
  ClaimData/
    _nextClaimID
    1.yml
  PlayerData/
```

`config.yml` and `messages.yml` are seeded under the same roots as the Paper plugin. They currently contain only
the Fabric-wired subset and are not overwritten after creation.

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
Modified Date: 1779681984295
```

Temporary admin commands:

- `/gp3d status`
- `/gp3d reload`
- `/gp3d claimhere`
- `/gp3d claim create <radius>`
- `/gp3d claim list`
- `/gp3d claim abandon`
- `/gp3d claim trust <public|uuid|online-player> <access|container|build|manage|neighbor>`
- `/gp3d claim untrust <public|uuid|online-player>`

`claim create` currently creates a top-level rectangular 2D claim centered on the executor and writes it under
`ClaimData`. `claim abandon` removes the claim at the executor's current block and writes the updated files.
`claim trust` and `claim untrust` update the claim at the executor's current block. Trust targets are currently
limited to `public`, a UUID, or an online player name.
This is a temporary test path for native Fabric persistence; claim blocks and player limits are not wired yet.

Temporary claim tool coverage:

- Right-clicking a block with a stick inspects the claim at that block.
- Right-clicking unclaimed land with a golden shovel starts a two-corner basic claim creation session.
- Right-clicking an owned claim corner with a golden shovel starts a resize session; the next golden shovel
  right-click moves that corner.
- Claim creation and resize currently enforce a temporary 5x5 minimum and write directly to `ClaimData`.
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

Next implementation slices:

1. Add permission-provider identifiers, likely LuckPerms when present.
2. Add denial feedback messages with rate limiting.
3. Expand protection hooks into fluid spread, explosion, piston, and entity environmental paths.
4. Expand player-facing claim tools into create, resize, subdivision, and selection sessions.
5. Add claim blocks, limits, accrual behavior, and migration/import tooling once the native model settles.
