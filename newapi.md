# New API Surface (Combined Branches)

This document summarizes the non-upstream API surface introduced across the four extension PR branches.

## 1) Claim Tool Handler API

Purpose: intercept claim tool interactions (golden shovel and stick) before GP built-in handling.

Key types:
- `com.griefprevention.api.ClaimToolHandler`
- `com.griefprevention.api.ClaimToolContext`
- `com.griefprevention.api.ClaimToolHandlerRegistry`

Entry point:
- `GriefPrevention#getClaimToolHandlerRegistry()`

Behavior:
- handlers are priority-ordered (`getPriority()`, higher first)
- first handler returning `true` consumes the interaction
- if no handler consumes it, default GP behavior runs

Minimal registration example:

```java
public final class MyAddon extends JavaPlugin {
    private final ClaimToolHandler handler = context -> {
        // custom right-click shovel behavior here
        return false; // false = let GP continue
    };

    @Override
    public void onEnable() {
        GriefPrevention.instance.getClaimToolHandlerRegistry().register(handler);
    }

    @Override
    public void onDisable() {
        GriefPrevention.instance.getClaimToolHandlerRegistry().unregister(handler);
    }
}
```

## 2) Visualization Style Registry + Renderer Hooks

Purpose: stop hard-growing `VisualizationType` and support custom visualization styles/renderers.

Key types:
- `com.griefprevention.visualization.VisualizationStyle`
- `com.griefprevention.visualization.VisualizationStyleRegistry`
- `com.griefprevention.visualization.BlockBoundaryRenderer`
- `com.griefprevention.events.BoundaryVisualizationEvent`
- `com.griefprevention.visualization.VisualizationProvider`

Entry point:
- `GriefPrevention#getVisualizationStyleRegistry()`

Notes:
- built-in enum styles still exist (`VisualizationType`) and are pre-registered
- custom style can provide block renderer via `getBlockRenderer()`
- visualization provider can be swapped per event via `BoundaryVisualizationEvent#setProvider(...)`

## 3) Claim Metadata + Geometry API

Purpose: let addons store namespaced claim metadata and define claim shape/containment semantics.

Key types:
- `com.griefprevention.api.claim.ClaimMetadataContainer`
- `com.griefprevention.api.claim.ClaimGeometry`
- `com.griefprevention.api.claim.ClaimGeometryRegistry`
- `com.griefprevention.api.claim.ClaimCreationCustomizer`

Entry points:
- `GriefPrevention#getClaimGeometryRegistry()`
- `Claim#getMetadata()`
- `Claim#setGeometryKey(NamespacedKey)`
- `Claim#getGeometry()`
- `DataStore#createClaim(..., ClaimCreationCustomizer customizer)`

What geometry controls:
- lookup bounds
- containment (`contains`)
- overlap checks (`overlaps`)
- optional visualization bounds override
- chunk indexing (`getChunkHashes`, optional override)

### Quick shaped-claim example: circle

```java
public final class CircleGeometry implements ClaimGeometry {
    public static final NamespacedKey KEY = NamespacedKey.fromString("gpapiaddon:circle");
    private static final NamespacedKey CENTER_X = NamespacedKey.fromString("gpapiaddon:center_x");
    private static final NamespacedKey CENTER_Z = NamespacedKey.fromString("gpapiaddon:center_z");
    private static final NamespacedKey RADIUS = NamespacedKey.fromString("gpapiaddon:radius");

    @Override
    public @NotNull NamespacedKey getKey() { return KEY; }

    @Override
    public @NotNull BoundingBox getLookupBounds(@NotNull Claim claim) {
        int cx = (int) claim.getMetadata().get(CENTER_X);
        int cz = (int) claim.getMetadata().get(CENTER_Z);
        int r = (int) claim.getMetadata().get(RADIUS);
        int minY = claim.getLesserBoundaryCorner().getBlockY();
        int maxY = claim.getGreaterBoundaryCorner().getBlockY();
        return new BoundingBox(cx - r, minY, cz - r, cx + r, maxY, cz + r);
    }

    @Override
    public boolean contains(@NotNull Claim claim, @NotNull Location loc, boolean ignoreHeight) {
        if (!ignoreHeight) {
            int minY = claim.getLesserBoundaryCorner().getBlockY();
            int maxY = claim.getGreaterBoundaryCorner().getBlockY();
            if (loc.getBlockY() < minY || loc.getBlockY() > maxY) return false;
        }
        int cx = (int) claim.getMetadata().get(CENTER_X);
        int cz = (int) claim.getMetadata().get(CENTER_Z);
        int r = (int) claim.getMetadata().get(RADIUS);
        int dx = loc.getBlockX() - cx;
        int dz = loc.getBlockZ() - cz;
        return (dx * dx + dz * dz) <= (r * r);
    }

    @Override
    public boolean overlaps(@NotNull Claim a, @NotNull Claim b) {
        // Simplest safe fallback: lookup-bounds intersection.
        // Replace with shape-accurate overlap for production.
        return getLookupBounds(a).intersects(b.getLookupBounds());
    }
}
```

Creating a circular claim with `ClaimCreationCustomizer`:

```java
CreateClaimResult result = gp.dataStore.createClaim(
        world, x1, x2, y1, y2, z1, z2,
        ownerId, parent, null, player, false,
        claim -> {
            claim.setGeometryKey(CircleGeometry.KEY);
            claim.getMetadata().set(NamespacedKey.fromString("gpapiaddon:center_x"), centerX);
            claim.getMetadata().set(NamespacedKey.fromString("gpapiaddon:center_z"), centerZ);
            claim.getMetadata().set(NamespacedKey.fromString("gpapiaddon:radius"), radius);
        });
```

### Quick shaped-claim example: regular polygon (N sides)

Store:
- center x/z
- radius
- `sides` (`>= 3`)
- optional rotation

Implement `ClaimGeometry#contains(...)` with point-in-polygon:
1. Generate N vertices around center using radius/rotation.
2. Run ray-cast or winding-number against player block x/z.
3. Keep Y bounds logic same as circle example.

This gives configurable shapes such as triangle, square, pentagon, etc, from one geometry implementation.

## 4) `/claim` Subcommand and Mode Extension API

Purpose: move beyond tab-completion hooks and allow full command + mode registration.

Key types:
- `com.griefprevention.api.claimcommand.ClaimCommandExtensionRegistry`
- `com.griefprevention.api.claimcommand.ClaimCommandSubcommand`
- `com.griefprevention.api.claimcommand.ClaimCommandMode`
- `com.griefprevention.api.claimcommand.ClaimCommandContext`

Entry point:
- `GriefPrevention#getClaimCommandExtensionRegistry()`

Capabilities:
- register `/claim <subcommand>`
- register `/claim mode <modeName>`
- aliases supported for both
- mode keyword `mode` is reserved

Minimal subcommand example:

```java
ClaimCommandSubcommand ping = new ClaimCommandSubcommand() {
    @Override
    public @NotNull String getName() { return "ping"; }

    @Override
    public boolean onCommand(@NotNull ClaimCommandContext context, @NotNull String[] args) {
        context.getPlayer().sendMessage("claim pong");
        return true;
    }
};

GriefPrevention.instance.getClaimCommandExtensionRegistry().registerSubcommand(ping);
```

## Notes for Addon Authors

- Use namespaced metadata keys (`myaddon:key`) for all addon state.
- Keep metadata values YAML-serializable.
- Geometry implementations should prioritize correctness of `contains` and `overlaps`.
- Register/unregister hooks on plugin enable/disable to avoid stale handlers.
