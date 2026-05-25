package com.griefprevention.visualization;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A data holder defining an area to be visualized.
 */
public final class Boundary
{
    private final @NotNull BoundingBox bounds;
    private final @NotNull VisualizationStyle style;
    private final @Nullable Claim claim;

    /**
     * Construct a new {@code Boundary} for a {@link BoundingBox} with the given visualization style.
     *
     * @param bounds the {@code BoundingBox}
     * @param type the {@link VisualizationType}
     */
    public Boundary(@NotNull BoundingBox bounds, @NotNull VisualizationType type)
    {
        this(bounds, type, null);
    }

    public Boundary(@NotNull BoundingBox bounds, @NotNull VisualizationType type, @Nullable Claim claim)
    {
        this(bounds, (VisualizationStyle) type, claim);
    }

    /**
     * Construct a new {@code Boundary} for a {@link BoundingBox} with a custom visualization style.
     *
     * @param bounds the {@code BoundingBox}
     * @param style the {@link VisualizationStyle}
     */
    public Boundary(@NotNull BoundingBox bounds, @NotNull VisualizationStyle style)
    {
        this(bounds, style, null);
    }

    public Boundary(@NotNull BoundingBox bounds, @NotNull VisualizationStyle style, @Nullable Claim claim)
    {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.style = Objects.requireNonNull(style, "style");
        this.claim = claim;
    }

    /**
     * Construct a new {@code Boundary} for a {@link Claim} with the given visualization style.
     *
     * @param claim the {@code Claim}
     * @param type the {@link VisualizationType}
     */
    public Boundary(@NotNull Claim claim, @NotNull VisualizationType type)
    {
        this(createBoundingBox(claim), type, claim);
    }

    /**
     * Construct a new {@code Boundary} for a {@link Claim} with a custom visualization style.
     *
     * @param claim the {@code Claim}
     * @param style the {@link VisualizationStyle}
     */
    public Boundary(@NotNull Claim claim, @NotNull VisualizationStyle style)
    {
        this(createBoundingBox(claim), style, claim);
    }
    
    /**
     * Create a BoundingBox for a claim, respecting 3D boundaries if applicable.
     *
     * @param claim the claim to create a bounding box for
     * @return the bounding box
     */
    private static @NotNull BoundingBox createBoundingBox(@NotNull Claim claim) {
        // For 3D subdivisions, use the actual Y coordinates
        if (claim.is3D() || (claim.parent != null && claim.parent.is3D())) {
            return new BoundingBox(
                claim.getLesserBoundaryCorner(),
                claim.getGreaterBoundaryCorner()
            );
        }
        // For regular claims, use the default behavior (full height)
        return new BoundingBox(claim);
    }

    /**
     * Get the {@link Claim} represented by the {@code Boundary}, if any.
     *
     * @return the {@code Claim} or {@code null} if not present
     */
    public @NotNull BoundingBox bounds()
    {
        return bounds;
    }

    public @NotNull VisualizationStyle style()
    {
        return style;
    }

    /**
     * Get the built-in visualization type for this boundary.
     *
     * @return the built-in type, or {@code null} when this boundary uses a custom style
     */
    public @Nullable VisualizationType type()
    {
        return style instanceof VisualizationType ? (VisualizationType) style : null;
    }

    public @Nullable Claim claim()
    {
        return claim;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof Boundary))
        {
            return false;
        }
        Boundary boundary = (Boundary) other;
        return bounds.equals(boundary.bounds)
                && style.equals(boundary.style)
                && Objects.equals(claim, boundary.claim);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(bounds, style, claim);
    }

    @Override
    public String toString()
    {
        return "Boundary[bounds=" + bounds + ", style=" + style + ", claim=" + claim + ']';
    }
}
