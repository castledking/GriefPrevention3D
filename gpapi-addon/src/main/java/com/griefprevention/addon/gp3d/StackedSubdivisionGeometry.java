package com.griefprevention.addon.gp3d;

import com.griefprevention.api.claim.ClaimGeometry;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

final class StackedSubdivisionGeometry implements ClaimGeometry
{
    static final NamespacedKey KEY = Objects.requireNonNull(NamespacedKey.fromString("gp3daddon:stacked_subdivision"));

    @Override
    public @NotNull NamespacedKey getKey()
    {
        return KEY;
    }

    @Override
    public @NotNull BoundingBox getLookupBounds(@NotNull Claim claim)
    {
        // Use exact claim corners. BoundingBox(Claim) intentionally expands maxY to world height
        // for legacy 2D claim behavior, which would break stacked exact-Y semantics.
        return new BoundingBox(claim.getLesserBoundaryCorner(), claim.getGreaterBoundaryCorner());
    }

    @Override
    public boolean contains(@NotNull Claim claim, @NotNull Location location, boolean ignoreHeight)
    {
        if (!Objects.equals(location.getWorld(), claim.getLesserBoundaryCorner().getWorld()))
        {
            return false;
        }

        BoundingBox bounds = getLookupBounds(claim);
        if (ignoreHeight)
        {
            return bounds.contains2d(location.getBlockX(), location.getBlockZ());
        }

        return bounds.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public boolean overlaps(@NotNull Claim claim, @NotNull Claim otherClaim)
    {
        if (!Objects.equals(claim.getLesserBoundaryCorner().getWorld(), otherClaim.getLesserBoundaryCorner().getWorld()))
        {
            return false;
        }

        BoundingBox bounds = getLookupBounds(claim);
        BoundingBox otherBounds = otherClaim.getLookupBounds();
        boolean horizontalOverlap = bounds.getMinX() <= otherBounds.getMaxX()
                && bounds.getMaxX() >= otherBounds.getMinX()
                && bounds.getMinZ() <= otherBounds.getMaxZ()
                && bounds.getMaxZ() >= otherBounds.getMinZ();
        if (!horizontalOverlap)
        {
            return false;
        }

        return bounds.getMinY() <= otherBounds.getMaxY() && bounds.getMaxY() >= otherBounds.getMinY();
    }
}
