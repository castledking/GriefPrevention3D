package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable platform-neutral view of a claim.
 */
public final class ClaimSnapshot
{
    private final @Nullable Long id;
    private final @NotNull String worldKey;
    private final @Nullable UUID ownerId;
    private final @Nullable Long parentId;
    private final @NotNull ClaimBounds bounds;
    private final boolean threeDimensional;
    private final boolean subdivision;

    public ClaimSnapshot(
            @Nullable Long id,
            @NotNull String worldKey,
            @Nullable UUID ownerId,
            @Nullable Long parentId,
            @NotNull ClaimBounds bounds,
            boolean threeDimensional,
            boolean subdivision)
    {
        this.id = id;
        this.worldKey = worldKey;
        this.ownerId = ownerId;
        this.parentId = parentId;
        this.bounds = bounds;
        this.threeDimensional = threeDimensional;
        this.subdivision = subdivision;
    }

    public @Nullable Long id()
    {
        return this.id;
    }

    public @NotNull String worldKey()
    {
        return this.worldKey;
    }

    public @Nullable UUID ownerId()
    {
        return this.ownerId;
    }

    public @Nullable Long parentId()
    {
        return this.parentId;
    }

    public @NotNull ClaimBounds bounds()
    {
        return this.bounds;
    }

    public boolean threeDimensional()
    {
        return this.threeDimensional;
    }

    public boolean subdivision()
    {
        return this.subdivision;
    }

    public boolean adminClaim()
    {
        return this.ownerId == null;
    }

    public boolean contains(@NotNull String worldKey, int x, int y, int z, boolean ignoreHeight)
    {
        if (!this.worldKey.equals(worldKey))
        {
            return false;
        }

        boolean ignoreY = ignoreHeight || (!this.threeDimensional && this.subdivision);
        return this.bounds.contains(x, y, z, ignoreY);
    }

    public boolean overlaps(@NotNull ClaimSnapshot other)
    {
        if (!this.worldKey.equals(other.worldKey))
        {
            return false;
        }

        boolean ignoreY = !this.threeDimensional && !other.threeDimensional;
        return this.bounds.overlaps(other.bounds, ignoreY);
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof ClaimSnapshot)) return false;
        ClaimSnapshot that = (ClaimSnapshot) other;
        return this.threeDimensional == that.threeDimensional
                && this.subdivision == that.subdivision
                && Objects.equals(this.id, that.id)
                && this.worldKey.equals(that.worldKey)
                && Objects.equals(this.ownerId, that.ownerId)
                && Objects.equals(this.parentId, that.parentId)
                && this.bounds.equals(that.bounds);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.id, this.worldKey, this.ownerId, this.parentId, this.bounds,
                this.threeDimensional, this.subdivision);
    }

    @Override
    public String toString()
    {
        return "ClaimSnapshot[id=" + this.id
                + ", worldKey=" + this.worldKey
                + ", ownerId=" + this.ownerId
                + ", parentId=" + this.parentId
                + ", bounds=" + this.bounds
                + ", threeDimensional=" + this.threeDimensional
                + ", subdivision=" + this.subdivision
                + "]";
    }
}
