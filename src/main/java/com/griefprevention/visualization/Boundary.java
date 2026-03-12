package com.griefprevention.visualization;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A data holder defining an area to be visualized.
 */
public record Boundary(
        @NotNull BoundingBox bounds,
        @NotNull VisualizationStyle style,
        @Nullable Claim claim)
{

    /**
     * Construct a new {@code Boundary} for a {@link BoundingBox} with the given visualization style.
     *
     * @param bounds the {@code BoundingBox}
     * @param style the {@link VisualizationStyle}
     */
    public Boundary(@NotNull BoundingBox bounds, @NotNull VisualizationStyle style)
    {
        this(bounds, style, null);
    }

    /**
     * Construct a new {@code Boundary} for a {@link Claim} with the given visualization style.
     *
     * @param claim the {@code Claim}
     * @param style the {@link VisualizationStyle}
     */
    public Boundary(@NotNull Claim claim, @NotNull VisualizationStyle style)
    {
        this(new BoundingBox(claim), style, claim);
    }

    /**
     * Construct a new {@code Boundary} using a built-in {@link VisualizationType}.
     *
     * @param bounds the {@code BoundingBox}
     * @param type the built-in style type
     * @param claim the represented claim, if any
     */
    public Boundary(@NotNull BoundingBox bounds, @NotNull VisualizationType type, @Nullable Claim claim)
    {
        this(bounds, (VisualizationStyle) type, claim);
    }

    /**
     * Construct a new {@code Boundary} using a built-in {@link VisualizationType}.
     *
     * @param bounds the {@code BoundingBox}
     * @param type the built-in style type
     */
    public Boundary(@NotNull BoundingBox bounds, @NotNull VisualizationType type)
    {
        this(bounds, (VisualizationStyle) type, null);
    }

    /**
     * Construct a new {@code Boundary} using a built-in {@link VisualizationType}.
     *
     * @param claim the {@code Claim}
     * @param type the built-in style type
     */
    public Boundary(@NotNull Claim claim, @NotNull VisualizationType type)
    {
        this(new BoundingBox(claim), type, claim);
    }

    /**
     * Get the {@link Claim} represented by the {@code Boundary}, if any.
     *
     * @return the {@code Claim} or {@code null} if not present
     */
    @Override
    public @Nullable Claim claim()
    {
        return claim;
    }

    /**
     * Get the built-in visualization type for this boundary, if one is being used.
     *
     * @return the built-in type, or {@code null} for custom styles
     */
    public @Nullable VisualizationType type()
    {
        return style instanceof VisualizationType visualizationType ? visualizationType : null;
    }

}
