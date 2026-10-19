package com.griefprevention.claims;

import com.griefprevention.geometry.OrthogonalEdge2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code Claims.AllowShapedClaims}, {@code Claims.AllowShapedSubClaims}, {@code Claims.ShapedMinimumWidth}
 * and {@code Claims.ShapedMinimumArea}, and the size rules Paper applies to a shaped outline with them.
 */
public final class ShapedClaimSettings
{
    public static final int DEFAULT_MINIMUM_WIDTH = 1;
    public static final int DEFAULT_MINIMUM_AREA = 25;

    private final boolean allowShapedClaims;
    private final boolean allowShapedSubdivisions;
    private final int minimumWidth;
    private final int minimumArea;

    public ShapedClaimSettings(
            boolean allowShapedClaims,
            boolean allowShapedSubdivisions,
            int minimumWidth,
            int minimumArea)
    {
        this.allowShapedClaims = allowShapedClaims;
        this.allowShapedSubdivisions = allowShapedSubdivisions;
        this.minimumWidth = Math.max(1, minimumWidth);
        this.minimumArea = Math.max(1, minimumArea);
    }

    public static @NotNull ShapedClaimSettings upstreamDefaults()
    {
        return new ShapedClaimSettings(false, false, DEFAULT_MINIMUM_WIDTH, DEFAULT_MINIMUM_AREA);
    }

    public boolean allowShapedClaims()
    {
        return this.allowShapedClaims;
    }

    public boolean allowShapedSubdivisions()
    {
        return this.allowShapedSubdivisions;
    }

    public int minimumWidth()
    {
        return this.minimumWidth;
    }

    public int minimumArea()
    {
        return this.minimumArea;
    }

    /**
     * Why a new top-level claim may not take this outline, as Paper's {@code validateShapedCreationMinimums}.
     * A four-cornered outline is a rectangle and keeps the ordinary claim minimums.
     *
     * @return the rule it breaks, or null when it is big enough
     */
    public @Nullable Shortfall creationShortfall(
            @NotNull OrthogonalPolygon polygon,
            int rectangleMinimumWidth,
            int rectangleMinimumArea)
    {
        return shortfall(polygon, rectangleMinimumWidth, rectangleMinimumArea, Integer.MAX_VALUE);
    }

    /**
     * Why an existing top-level claim may not take this outline, as Paper's {@code validateShapedResizeMinimums}:
     * a claim already below the minimum area may still grow.
     *
     * @param previousArea the claim's area before the change
     * @return the rule it breaks, or null when it is big enough
     */
    public @Nullable Shortfall resizeShortfall(
            @NotNull OrthogonalPolygon polygon,
            int rectangleMinimumWidth,
            int rectangleMinimumArea,
            int previousArea)
    {
        return shortfall(polygon, rectangleMinimumWidth, rectangleMinimumArea, previousArea);
    }

    private @Nullable Shortfall shortfall(
            @NotNull OrthogonalPolygon polygon,
            int rectangleMinimumWidth,
            int rectangleMinimumArea,
            int previousArea)
    {
        boolean rectangle = polygon.corners().size() == 4;
        int width = rectangle ? Math.max(1, rectangleMinimumWidth) : this.minimumWidth;
        int area = rectangle ? Math.max(1, rectangleMinimumArea) : this.minimumArea;

        long xLength = (long) polygon.maxX() - polygon.minX() + 1L;
        long zLength = (long) polygon.maxZ() - polygon.minZ() + 1L;
        if (xLength < width || zLength < width || !edgesMeetWidth(polygon, width))
        {
            return new Shortfall(Shortfall.Rule.TOO_NARROW, width);
        }

        int cells = polygon.cellCount();
        if (cells < area && cells < previousArea)
        {
            return new Shortfall(Shortfall.Rule.TOO_SMALL, area);
        }
        return null;
    }

    /** Every edge spans at least {@code width} blocks, so no part of the outline is thinner than that. */
    private static boolean edgesMeetWidth(@NotNull OrthogonalPolygon polygon, int width)
    {
        int minimumEdgeLength = width - 1;
        if (minimumEdgeLength <= 0)
        {
            return true;
        }
        for (OrthogonalEdge2i edge : polygon.edges())
        {
            if (edge.length() < minimumEdgeLength)
            {
                return false;
            }
        }
        return true;
    }

    /** A size rule an outline breaks, and the minimum it falls short of. */
    public static final class Shortfall
    {
        public enum Rule
        {
            /** Narrower than the minimum width somewhere. */
            TOO_NARROW,
            /** Fewer blocks than the minimum area. */
            TOO_SMALL
        }

        private final @NotNull Rule rule;
        private final int minimum;

        public Shortfall(@NotNull Rule rule, int minimum)
        {
            this.rule = rule;
            this.minimum = minimum;
        }

        public @NotNull Rule rule()
        {
            return this.rule;
        }

        public int minimum()
        {
            return this.minimum;
        }
    }
}
