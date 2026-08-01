package com.griefprevention.geometry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the corridor that joins two claims that do not touch when they are merged.
 *
 * <p>The corridor follows the same Manhattan route the merge has always taken, but instead of a
 * fixed thin path it is swept at the width of the nib the player selected on each claim. That
 * width comes from how deep the player stood inside the claim: only the part of the claim
 * reachable from the selected edge within that depth counts, so a narrow nib stays narrow, and
 * the moment the depth reaches a wider parent section that section sets the width.</p>
 *
 * <p>The result is one or two rectangles - a straight band when the nibs face each other, an
 * L when they are diagonal - leaving the owner usable land instead of a thin thread.</p>
 */
public final class MergeCorridor
{
    private MergeCorridor()
    {
    }

    /**
     * The axis a nib points along, used to decide which leg of an L-shaped corridor comes first.
     */
    public enum Axis
    {
        X,
        Z,
        UNKNOWN
    }

    /**
     * Build the rectangles that need filling to join two claims.
     *
     * @param first the first claim's boundary
     * @param firstEdgeIndex the edge of {@code first} the player selected, or null if unknown
     * @param firstDepthPoint where the player stood in {@code first}, or null if unknown
     * @param second the second claim's boundary
     * @param secondEdgeIndex the edge of {@code second} the player selected, or null if unknown
     * @param secondDepthPoint where the player stood in {@code second}, or null if unknown
     * @return the corridor rectangles, or null when either side's nib cannot be determined
     */
    public static @Nullable List<Extent> connect(
            @NotNull OrthogonalPolygon first,
            @Nullable Integer firstEdgeIndex,
            @Nullable OrthogonalPoint2i firstDepthPoint,
            @NotNull OrthogonalPolygon second,
            @Nullable Integer secondEdgeIndex,
            @Nullable OrthogonalPoint2i secondDepthPoint)
    {
        Nib firstNib = nib(first, firstEdgeIndex, firstDepthPoint);
        if (firstNib == null) return null;

        Nib secondNib = nib(second, secondEdgeIndex, secondDepthPoint);
        if (secondNib == null) return null;

        return connect(firstNib, secondNib);
    }

    /**
     * Build the rectangles that need filling to join two nibs.
     *
     * @param first the nib the corridor leaves from
     * @param second the nib the corridor arrives at
     * @return the corridor rectangles
     */
    public static @NotNull List<Extent> connect(@NotNull Nib first, @NotNull Nib second)
    {
        Extent a = first.extent();
        Extent b = second.extent();

        boolean overlapX = a.minX() <= b.maxX() && b.minX() <= a.maxX();
        boolean overlapZ = a.minZ() <= b.maxZ() && b.minZ() <= a.maxZ();

        List<Extent> corridor = new ArrayList<>();

        if (overlapX && overlapZ)
        {
            // The nibs already sit on top of one another - one rectangle covers both.
            corridor.add(a.union(b));
            return corridor;
        }

        if (overlapX)
        {
            // Facing each other along Z: one straight band as wide as both nibs together.
            corridor.add(new Extent(
                    Math.min(a.minX(), b.minX()), Math.min(a.minZ(), b.minZ()),
                    Math.max(a.maxX(), b.maxX()), Math.max(a.maxZ(), b.maxZ())));
            return corridor;
        }

        if (overlapZ)
        {
            // Facing each other along X.
            corridor.add(new Extent(
                    Math.min(a.minX(), b.minX()), Math.min(a.minZ(), b.minZ()),
                    Math.max(a.maxX(), b.maxX()), Math.max(a.maxZ(), b.maxZ())));
            return corridor;
        }

        // Diagonal: turn one corner, keeping each leg at the width of the nib it leaves from.
        // The nib the player selected decides which way the corridor sets off.
        boolean travelXFirst = first.axis() != Axis.Z;
        if (travelXFirst)
        {
            corridor.add(new Extent(
                    Math.min(a.minX(), b.minX()), a.minZ(),
                    Math.max(a.maxX(), b.maxX()), a.maxZ()));
            corridor.add(new Extent(
                    b.minX(), Math.min(a.minZ(), b.minZ()),
                    b.maxX(), Math.max(a.maxZ(), b.maxZ())));
        }
        else
        {
            corridor.add(new Extent(
                    a.minX(), Math.min(a.minZ(), b.minZ()),
                    a.maxX(), Math.max(a.maxZ(), b.maxZ())));
            corridor.add(new Extent(
                    Math.min(a.minX(), b.minX()), b.minZ(),
                    Math.max(a.maxX(), b.maxX()), b.maxZ()));
        }

        return corridor;
    }

    /**
     * Measure how much of a claim is reachable from the selected edge within the player's depth.
     *
     * <p>When no edge was selected the whole claim is treated as the nib, but only for
     * rectangular claims - a shaped claim without a selected edge has no meaningful nib and
     * returns null so the caller can fall back to another strategy.</p>
     *
     * @param polygon the claim's boundary
     * @param edgeIndex the selected edge, or null if unknown
     * @param depthPoint where the player stood, or null if unknown
     * @return the nib, or null when it cannot be determined
     */
    public static @Nullable Nib nib(
            @NotNull OrthogonalPolygon polygon,
            @Nullable Integer edgeIndex,
            @Nullable OrthogonalPoint2i depthPoint)
    {
        if (edgeIndex == null
                || depthPoint == null
                || edgeIndex < 0
                || edgeIndex >= polygon.edges().size())
        {
            // A rectangle has no nibs, so the whole claim is its own nib.
            if (polygon.corners().size() != 4) return null;
            return new Nib(
                    new Extent(polygon.minX(), polygon.minZ(), polygon.maxX(), polygon.maxZ()),
                    Axis.UNKNOWN);
        }

        OrthogonalEdge2i edge = polygon.edges().get(edgeIndex);
        if (!edge.isOrthogonal()) return null;

        boolean horizontal = edge.isHorizontal();
        int edgeLine = horizontal ? edge.start().z() : edge.start().x();
        int parallelMin = horizontal ? edge.minX() : edge.minZ();
        int parallelMax = horizontal ? edge.maxX() : edge.maxZ();
        int pointLine = horizontal ? depthPoint.z() : depthPoint.x();

        int step = inwardStep(polygon, horizontal, edgeLine, parallelMin, parallelMax, pointLine);
        int depth = step == 0 ? 0 : (pointLine - edgeLine) * step;
        if (depth < 0) depth = 0;

        int lineLimit = edgeLine + step * depth;
        int lineMin = Math.min(edgeLine, lineLimit);
        int lineMax = Math.max(edgeLine, lineLimit);

        Extent extent = floodFill(polygon, horizontal, edgeLine, parallelMin, parallelMax, lineMin, lineMax);
        if (extent == null) return null;

        // A horizontal edge faces along Z, so that is the way out of the nib.
        return new Nib(extent, horizontal ? Axis.Z : Axis.X);
    }

    /**
     * Collect every claim cell reachable from the edge without leaving the depth band, and
     * report the rectangle covering them. Reachability is what lets a wider parent section
     * take over the width once the depth reaches it, while a neighbouring nib that is only
     * connected further inside the claim is left out.
     */
    private static @Nullable Extent floodFill(
            @NotNull OrthogonalPolygon polygon,
            boolean horizontal,
            int edgeLine,
            int parallelMin,
            int parallelMax,
            int lineMin,
            int lineMax)
    {
        Set<OrthogonalPoint2i> visited = new HashSet<>();
        Set<OrthogonalPoint2i> outside = new HashSet<>();
        Deque<OrthogonalPoint2i> queue = new ArrayDeque<>();

        for (int parallel = parallelMin; parallel <= parallelMax; parallel++)
        {
            int x = horizontal ? parallel : edgeLine;
            int z = horizontal ? edgeLine : parallel;
            if (!polygon.containsCell(x, z)) continue;

            OrthogonalPoint2i cell = new OrthogonalPoint2i(x, z);
            if (visited.add(cell)) queue.add(cell);
        }

        if (visited.isEmpty()) return null;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        while (!queue.isEmpty())
        {
            OrthogonalPoint2i cell = queue.poll();
            minX = Math.min(minX, cell.x());
            maxX = Math.max(maxX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxZ = Math.max(maxZ, cell.z());

            int[][] neighbors = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] neighbor : neighbors)
            {
                int x = cell.x() + neighbor[0];
                int z = cell.z() + neighbor[1];
                int line = horizontal ? z : x;
                if (line < lineMin || line > lineMax) continue;

                OrthogonalPoint2i next = new OrthogonalPoint2i(x, z);
                if (visited.contains(next) || outside.contains(next)) continue;

                if (!polygon.containsCell(x, z))
                {
                    outside.add(next);
                    continue;
                }

                visited.add(next);
                queue.add(next);
            }
        }

        return new Extent(minX, minZ, maxX, maxZ);
    }

    /**
     * Determine which side of the edge the claim's interior is on, expressed as a step of
     * +1 or -1 along the axis perpendicular to the edge. Falls back to the side the player
     * stood on when both sides look identical.
     */
    private static int inwardStep(
            @NotNull OrthogonalPolygon polygon,
            boolean horizontal,
            int edgeLine,
            int parallelMin,
            int parallelMax,
            int pointLine)
    {
        boolean positiveInside = anyCellOnLine(polygon, horizontal, edgeLine + 1, parallelMin, parallelMax);
        boolean negativeInside = anyCellOnLine(polygon, horizontal, edgeLine - 1, parallelMin, parallelMax);

        if (positiveInside && !negativeInside) return 1;
        if (negativeInside && !positiveInside) return -1;

        return Integer.compare(pointLine, edgeLine);
    }

    private static boolean anyCellOnLine(
            @NotNull OrthogonalPolygon polygon,
            boolean horizontal,
            int line,
            int parallelMin,
            int parallelMax)
    {
        for (int parallel = parallelMin; parallel <= parallelMax; parallel++)
        {
            int x = horizontal ? parallel : line;
            int z = horizontal ? line : parallel;
            if (polygon.containsCell(x, z)) return true;
        }

        return false;
    }

    /**
     * The part of a claim a merge corridor attaches to, and the way it points.
     */
    public static final class Nib
    {
        private final @NotNull Extent extent;
        private final @NotNull Axis axis;

        public Nib(@NotNull Extent extent, @NotNull Axis axis)
        {
            this.extent = extent;
            this.axis = axis;
        }

        public @NotNull Extent extent()
        {
            return this.extent;
        }

        public @NotNull Axis axis()
        {
            return this.axis;
        }

        @Override
        public String toString()
        {
            return "Nib[" + this.extent + ", " + this.axis + "]";
        }
    }

    /**
     * An inclusive axis-aligned cell range in the X/Z plane.
     */
    public static final class Extent
    {
        private final int minX;
        private final int minZ;
        private final int maxX;
        private final int maxZ;

        public Extent(int minX, int minZ, int maxX, int maxZ)
        {
            this.minX = Math.min(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxZ = Math.max(minZ, maxZ);
        }

        public int minX()
        {
            return this.minX;
        }

        public int minZ()
        {
            return this.minZ;
        }

        public int maxX()
        {
            return this.maxX;
        }

        public int maxZ()
        {
            return this.maxZ;
        }

        public int cellCount()
        {
            return (this.maxX - this.minX + 1) * (this.maxZ - this.minZ + 1);
        }

        public boolean contains(int x, int z)
        {
            return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
        }

        public @NotNull Extent union(@NotNull Extent other)
        {
            return new Extent(
                    Math.min(this.minX, other.minX),
                    Math.min(this.minZ, other.minZ),
                    Math.max(this.maxX, other.maxX),
                    Math.max(this.maxZ, other.maxZ));
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) return true;
            if (!(other instanceof Extent)) return false;
            Extent that = (Extent) other;
            return this.minX == that.minX
                    && this.minZ == that.minZ
                    && this.maxX == that.maxX
                    && this.maxZ == that.maxZ;
        }

        @Override
        public int hashCode()
        {
            int result = this.minX;
            result = 31 * result + this.minZ;
            result = 31 * result + this.maxX;
            result = 31 * result + this.maxZ;
            return result;
        }

        @Override
        public String toString()
        {
            return "Extent[" + this.minX + ", " + this.minZ + " -> " + this.maxX + ", " + this.maxZ + "]";
        }
    }
}
