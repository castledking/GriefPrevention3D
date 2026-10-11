package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

/**
 * Where a subdivision may sit, and how far a claim with subdivisions may shrink, as Paper decides it
 * when the golden shovel creates or resizes them.
 */
public final class ClaimPlacement
{
    private ClaimPlacement()
    {
    }

    /**
     * A subdivision stays in its parent's columns. A 3D one also stays at or above the parent's floor,
     * and inside its heights when the parent is 3D itself; a 2D subdivision spans every height.
     */
    public static boolean fitsInParent(
            @NotNull ClaimSnapshot parent,
            @NotNull ClaimBounds bounds,
            boolean threeDimensional)
    {
        if (!containsColumns(parent.bounds(), bounds))
        {
            return false;
        }
        if (!threeDimensional)
        {
            return true;
        }
        ClaimBounds outer = parent.bounds();
        return bounds.minY() >= outer.minY() && (!parent.threeDimensional() || bounds.maxY() <= outer.maxY());
    }

    /**
     * @param subdivision the subdivision as it would be, with the id it has if it already exists
     * @param siblings the parent's other subdivisions
     * @return the sibling it would overlap, or null; two 2D subdivisions conflict in any shared column,
     *         a 3D one only where the heights meet too
     */
    public static @Nullable ClaimSnapshot overlappingSibling(
            @NotNull ClaimSnapshot subdivision,
            @NotNull Collection<ClaimSnapshot> siblings)
    {
        for (ClaimSnapshot sibling : siblings)
        {
            if (subdivision.id() != null && Objects.equals(subdivision.id(), sibling.id()))
            {
                continue;
            }
            if (subdivision.overlaps(sibling))
            {
                return sibling;
            }
        }
        return null;
    }

    /** @return a subdivision the claim's new bounds would no longer hold, or null when all still fit */
    public static @Nullable ClaimSnapshot subdivisionLeftOutside(
            @NotNull ClaimSnapshot resized,
            @NotNull Collection<ClaimSnapshot> subdivisions)
    {
        for (ClaimSnapshot subdivision : subdivisions)
        {
            if (!fitsInParent(resized, subdivision.bounds(), subdivision.threeDimensional()))
            {
                return subdivision;
            }
        }
        return null;
    }

    /**
     * Whether every column of {@code inner} is a column of {@code outer}. A shaped claim has no holes,
     * so a rectangle whose edge lies inside it lies inside it completely; a shaped inner claim may bend
     * inwards, so each of its columns is checked.
     */
    public static boolean containsColumns(@NotNull ClaimBounds outer, @NotNull ClaimBounds inner)
    {
        if (inner.minX() < outer.minX() || inner.maxX() > outer.maxX()
                || inner.minZ() < outer.minZ() || inner.maxZ() > outer.maxZ())
        {
            return false;
        }
        if (!outer.isShaped())
        {
            return true;
        }
        if (inner.isShaped())
        {
            for (int x = inner.minX(); x <= inner.maxX(); x++)
            {
                for (int z = inner.minZ(); z <= inner.maxZ(); z++)
                {
                    if (!inColumns(outer, inner, x, z))
                    {
                        return false;
                    }
                }
            }
            return true;
        }
        for (int x = inner.minX(); x <= inner.maxX(); x++)
        {
            if (!inColumns(outer, inner, x, inner.minZ()) || !inColumns(outer, inner, x, inner.maxZ()))
            {
                return false;
            }
        }
        for (int z = inner.minZ(); z <= inner.maxZ(); z++)
        {
            if (!inColumns(outer, inner, inner.minX(), z) || !inColumns(outer, inner, inner.maxX(), z))
            {
                return false;
            }
        }
        return true;
    }

    /** A column the inner claim does not cover cannot leave the outer one. */
    private static boolean inColumns(@NotNull ClaimBounds outer, @NotNull ClaimBounds inner, int x, int z)
    {
        return !inner.containsColumn(x, z) || outer.containsColumn(x, z);
    }
}
