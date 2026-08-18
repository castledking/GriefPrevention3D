# Fabric Platform Roadmap

Current target: **Minecraft 1.21.11** (Mounts of Mayhem, Dec 2025)
Next target: **Minecraft 26.1.2** (Tiny Takeover hotfix, Apr 2026)

---

## Version Support

### What's next: 26.1.2

26.1 is the first **unobfuscated** Minecraft release. This is a big deal — it means:

- **No more Yarn/Intermediary mappings** — code compiles against raw Mojang names
- **Loom plugin changes** — `fabric-loom` → `net.fabricmc.fabric-loom` (non-remapping variant)
- **Java 25 minimum** (up from Java 21)
- All mods must be **recompiled** but don't need remapping

Fabric API is available: `0.155.2+26.1.2`

### Effort to port

| Area | Risk | Notes |
|------|------|-------|
| Build config | Low | Swap Loom plugin ID, bump versions in `gradle.properties` |
| Mixin (`ServerExplosionMixin`) | **High** | `ServerExplosion` class layout may have changed between 1.21.11 and 26.1 |
| `ClientboundBlockUpdatePacket` | Medium | Packet constructors shift between versions |
| Item classes | Low | `BucketItem`, `SpawnEggItem`, etc. are stable |
| Fabric API events | Low | `UseBlockCallback`, `AttackEntityCallback`, etc. are cross-version stable |
| Explosion enums | Medium | `Explosion.BlockInteraction` values may be renamed |

### Multi-version strategy

**Per-version modules** is the right pattern (one Gradle module per MC version). The build is already templatizable — every version-sensitive value lives in `gradle.properties`.

For **26.1.x family** (26.1, 26.1.1, 26.1.2): these are all API-compatible. A single module with `"minecraft": ">=26.1"` could cover all three. Fabric API `0.155.2+26.1.2` lists support for 26.1, 26.1.1, and 26.1.2.

For **1.21.x family** (1.21.4 through 1.21.11): same deal — one module with `"minecraft": ">=1.21.4"` could cover the range, since the obfuscated-to-Mojang migration only happened at 26.1.

**Cross-era** (1.21.x ↔ 26.x): impossible in one jar. The Loom plugin is different (`fabric-loom-remap` vs `fabric-loom`). Keep separate modules.

### Version history

```
1.21.4  → 1.21.5 → 1.21.6 → 1.21.7 → 1.21.8 → 1.21.9 → 1.21.10 → 1.21.11
                                                              ↑ we are here
26.1 → 26.1.1 → 26.1.2 → 26.2 → 26.3 (snapshots)
        ↑ next target
```

---

## Visualization

### GlowingVisualization

Uses **Block Display entities** (1.19.3+) alongside the existing fake block visualization. Block Displays render a block model in the world without placing it — positioned and rotated freely, not grid-aligned.

**Implementation sketch:**
- Spawn `BlockDisplay` entities at claim corners/edges
- Set `blockState` to desired marker block
- Set `viewRange` to control visibility distance
- Auto-expire after 60 seconds

### visualizeNearbyClaims / refreshVisualization

`visualizeNearbyClaims` exists in Bukkit (`BoundaryVisualization.java:423`) but not Fabric. It's called from the claim tool dispatcher to show claim boundaries near the player when they enter a claim area.

**What's needed:**
- Port `visualizeNearbyClaims` logic to `FabricFakeBlockVisualization`
- Wire it into `FabricClaimToolHooks` when a player enters a claim (not just on tool use)
- Add `refreshVisualization` to update markers when claims change (create/resize/delete)

### Remove /claimhere command

The stick (investigation tool) already does everything `/claimhere` does, plus shows visualization. Once `visualizeNearbyClaims` is wired, `/claimhere` becomes redundant.

---

## Protection Parity

The Bukkit side has ~50+ event handlers. Fabric currently has 4. Here's what's missing, grouped by priority:

### High priority (common gameplay)

| Protection | Bukkit event | Fabric equivalent |
|-----------|-------------|-------------------|
| Block placement | `BlockPlaceEvent` | `UseBlockCallback` (partially covered, but no placement-specific check) |
| Piston push/pull | `BlockPistonExtendEvent` | Mixin on `PistonBaseBlock` or `PistonMovingBlockEntity` |
| Fluid spread | `BlockFromToEvent` | Mixin on fluid flow logic |
| Fire spread | `BlockBurnEvent` | Mixin on fire tick |
| Tree/structure growth | `StructureGrowEvent` | Mixin on tree generator |

### Medium priority (entity interactions)

| Protection | Bukkit event | Fabric equivalent |
|-----------|-------------|-------------------|
| Leashing | `PlayerLeashEntityEvent` | `LeashKnotEntity` interaction callback |
| Vehicle enter/exit | `VehicleEnterEvent` | `UseEntityCallback` (already partially covered) |
| Entity damage by entity | `EntityDamageByEntityEvent` | Mixin on `LivingEntity.hurt()` |
| Falling blocks | `EntityChangeBlockEvent` | `FallingBlockEntity` tick mixin |
| Enderman block steal | `EntityChangeBlockEvent` | Enderman AI mixin |

### Lower priority (nice to have)

| Protection | Bukkit event | Fabric equivalent |
|-----------|-------------|-------------------|
| Item frame break/place | `HangingBreakEvent`/`HangingPlaceEvent` | Entity interaction callbacks |
| Painting break/place | `HangingBreakEvent`/`HangingPlaceEvent` | Same |
| Bucket fill/empty | `PlayerBucketEmptyEvent` | `UseBlockCallback` (partially covered) |
| Portal creation | `PortalCreateEvent` | Mixin on portal logic |
| Sign editing | `SignChangeEvent` | Mixin on `SignBlockEntity` |
| Mob spawn control | `CreatureSpawnEvent` | Mixin on spawn logic |

### Vehicle/Entity specifics

The Bukkit side protects:
- Entering vehicles in claims (requires permission)
- Breaking vehicles in claims
- Leashing mobs in claims (requires permission)

Fabric has none of these. The `UseEntityCallback` covers right-click interactions but not vehicle-specific or leash-specific logic.

---

## Version Bump to 26.1.2

To actually build for 26.1.2, we need to:

1. **Create `platforms/fabric-26.1.2/`** — copy `fabric-1.21.11`, update `gradle.properties`:
   ```
   fabricMinecraftVersion=26.1.2
   fabricLoaderVersion=0.19.3
   fabricApiVersion=0.155.2+26.1.2
   fabricTargetJavaVersion=25
   ```

2. **Update `settings.gradle.kts`** — add `:fabric-26.1.2`

3. **Update `build.gradle.kts` (root)** — point universal jar pipeline at new module

4. **Swap Loom plugin** — `fabric-loom` → `net.fabricmc.fabric-loom`

5. **Test the mixin** — boot a 26.1.2 server, verify `ServerExplosionMixin` still injects

6. **Verify packet construction** — `ClientboundBlockUpdatePacket` may have changed

**Estimated effort:** 1-2 days for someone familiar with Fabric modding. The mixin verification is the hard part.

---

## Summary

```
Phase 1: Visualization
  [ ] GlowingVisualization (entity-based glowing markers)
  [ ] Port visualizeNearbyClaims from Bukkit
  [ ] Wire refreshVisualization into claim create/resize/delete
  [ ] Remove /claimhere (stick tool replaces it)

Phase 2: Version 26.1.2
  [ ] Create fabric-26.1.2 module
  [ ] Update build config (Loom plugin, versions)
  [ ] Verify/fix ServerExplosionMixin
  [ ] Verify ClientboundBlockUpdatePacket
  [ ] Verify item class names
  [ ] Boot smoke test

Phase 3: Protection Parity
  [ ] Piston protection (mixin)
  [ ] Fluid flow protection (mixin)
  [ ] Fire spread protection (mixin)
  [ ] Leash protection (entity callback)
  [ ] Vehicle protection (entity callback)
  [ ] Entity damage protection (mixin)
  [ ] Falling block / enderman protection (mixin)

Phase 4: Multi-version (optional)
  [ ] Design version-range module structure
  [ ] Support 26.1.x family in one module
  [ ] Support 1.21.x family in one module
```
