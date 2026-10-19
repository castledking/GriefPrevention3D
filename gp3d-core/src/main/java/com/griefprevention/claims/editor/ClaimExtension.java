package com.griefprevention.claims.editor;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.geometry.OrthogonalDirection;
import com.griefprevention.geometry.OrthogonalEdge2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.UUID;

/**
 * Geometry for {@code /extendclaim}: which way a player's view pushes a rectangle's sides, and which
 * boundary segment of a shaped claim the player stands in front of and faces. Directions are a
 * player's look vector; positions are their block coordinates with fractions.
 */
public final class ClaimExtension
{
    // Looking further up or down than this targets the top or bottom of a 3D claim.
    private static final double VERTICAL_LOOK_THRESHOLD = 0.75D;
    // Looking within this of an axis moves only the side across it; anything else is a diagonal.
    private static final double AXIS_LOOK_THRESHOLD = 0.3D;
    // How far behind the player an edge may lie and still count as faced.
    private static final double EDGE_BEHIND_TOLERANCE = 0.5D;

    private static final ClaimEditor EDITOR = new ClaimEditorSkeleton();

    private ClaimExtension()
    {
    }

    /** What an extension of a rectangular claim came to. */
    public enum Outcome
    {
        /** The claim takes {@link RectangleExtension#bounds()}. */
        EXTENDED,
        /** A 2D claim already reaches the top of the world. */
        ALREADY_REACHES_SKY,
        /** A 2D claim's floor follows what is built under it, so it cannot be pushed down by hand. */
        EXTENDS_DOWNWARD_BY_ITSELF,
        /** The moved side would pass the opposite side or exceed integer block coordinates. */
        LIMIT_REACHED
    }

    /** The bounds a rectangular claim would take, or why it takes none. */
    public static final class RectangleExtension
    {
        private final @NotNull Outcome outcome;
        private final @Nullable ClaimBounds bounds;

        private RectangleExtension(@NotNull Outcome outcome, @Nullable ClaimBounds bounds)
        {
            this.outcome = outcome;
            this.bounds = bounds;
        }

        public @NotNull Outcome outcome()
        {
            return this.outcome;
        }

        /** @return the new bounds when {@link #outcome()} is {@link Outcome#EXTENDED}, else null */
        public @Nullable ClaimBounds bounds()
        {
            return this.bounds;
        }
    }

    /**
     * Paper's {@code /extendclaim} for a rectangle: the side the player looks toward moves by
     * {@code amount} (a negative amount pulls it in), both sides on a diagonal, and the top or bottom
     * of a 3D claim when the player looks steeply up or down.
     */
    public static @NotNull RectangleExtension extendRectangle(
            @NotNull ClaimBounds bounds,
            boolean threeDimensional,
            double lookX,
            double lookY,
            double lookZ,
            int amount)
    {
        int minX = bounds.minX();
        int maxX = bounds.maxX();
        int minY = bounds.minY();
        int maxY = bounds.maxY();
        int minZ = bounds.minZ();
        int maxZ = bounds.maxZ();

        long nextMinX = minX;
        long nextMaxX = maxX;
        long nextMinY = minY;
        long nextMaxY = maxY;
        long nextMinZ = minZ;
        long nextMaxZ = maxZ;

        if (lookY > VERTICAL_LOOK_THRESHOLD)
        {
            if (!threeDimensional)
            {
                return new RectangleExtension(Outcome.ALREADY_REACHES_SKY, null);
            }
            nextMaxY += amount;
        }
        else if (lookY < -VERTICAL_LOOK_THRESHOLD)
        {
            if (!threeDimensional)
            {
                return new RectangleExtension(Outcome.EXTENDS_DOWNWARD_BY_ITSELF, null);
            }
            nextMinY -= amount;
        }
        else if (Math.abs(lookX) < AXIS_LOOK_THRESHOLD)
        {
            if (lookZ > 0) nextMaxZ += amount;
            else nextMinZ -= amount;
        }
        else if (Math.abs(lookZ) < AXIS_LOOK_THRESHOLD)
        {
            if (lookX > 0) nextMaxX += amount;
            else nextMinX -= amount;
        }
        else
        {
            if (lookX > 0) nextMaxX += amount;
            else nextMinX -= amount;
            if (lookZ > 0) nextMaxZ += amount;
            else nextMinZ -= amount;
        }

        if (nextMinX > nextMaxX || nextMinY > nextMaxY || nextMinZ > nextMaxZ
                || nextMinX < Integer.MIN_VALUE || nextMaxX > Integer.MAX_VALUE
                || nextMinY < Integer.MIN_VALUE || nextMaxY > Integer.MAX_VALUE
                || nextMinZ < Integer.MIN_VALUE || nextMaxZ > Integer.MAX_VALUE)
        {
            return new RectangleExtension(Outcome.LIMIT_REACHED, null);
        }

        return new RectangleExtension(
                Outcome.EXTENDED,
                ClaimBounds.rectangle((int) nextMinX, (int) nextMinY, (int) nextMinZ,
                        (int) nextMaxX, (int) nextMaxY, (int) nextMaxZ));
    }

    /**
     * The boundary segment of a shaped outline the player stands in front of and faces: along the
     * axis they mostly look down, the nearest edge ahead of them whose span covers their column.
     *
     * @return the edge's index in {@link OrthogonalPolygon#edges()}, or null when none is ahead
     */
    public static @Nullable Integer facedSegment(
            @NotNull OrthogonalPolygon polygon,
            double x,
            double z,
            double lookX,
            double lookZ)
    {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        if (Math.abs(lookX) >= Math.abs(lookZ))
        {
            return nearestEdge(polygon, false, blockZ, x, lookX >= 0);
        }
        return nearestEdge(polygon, true, blockX, z, lookZ >= 0);
    }

    /** The way the player pushes a faced edge: away from them along their view. */
    public static @NotNull OrthogonalDirection expansionDirection(
            @NotNull OrthogonalEdge2i edge,
            double lookX,
            double lookZ)
    {
        if (edge.isHorizontal())
        {
            return lookZ >= 0 ? OrthogonalDirection.SOUTH : OrthogonalDirection.NORTH;
        }
        if (edge.isVertical())
        {
            return lookX >= 0 ? OrthogonalDirection.EAST : OrthogonalDirection.WEST;
        }
        return edge.outwardDirectionForPositiveOffset();
    }

    /**
     * Moves one segment of a shaped outline by {@code amount} blocks in {@code direction}: away from
     * the claim grows it, toward the claim shrinks it.
     *
     * @param claimId the claim the outline belongs to
     * @param subdivision whether that claim is a subdivision
     * @return the editor's result: on success its preview holds the new outline
     */
    public static @NotNull ClaimEditResult expandSegment(
            long claimId,
            boolean subdivision,
            @NotNull OrthogonalPolygon polygon,
            int edgeIndex,
            @Nullable OrthogonalDirection direction,
            int amount)
    {
        return expandSegment(new UUID(0L, 0L), claimId, subdivision, polygon, edgeIndex, direction, amount);
    }

    /** The editor result for a segment expansion, associated with its requesting player. */
    public static @NotNull ClaimEditResult expandSegment(
            @NotNull UUID playerId,
            long claimId,
            boolean subdivision,
            @NotNull OrthogonalPolygon polygon,
            int edgeIndex,
            @Nullable OrthogonalDirection direction,
            int amount)
    {
        SegmentSelection selection = new SegmentSelection(claimId, edgeIndex, null, null, direction);
        ClaimEditorSession session = ClaimEditorSession.idle(playerId)
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withTarget(new ClaimEditTarget(
                        subdivision ? ClaimEditTargetType.EXISTING_SUBDIVISION_CLAIM : ClaimEditTargetType.EXISTING_PARENT_CLAIM,
                        claimId))
                .withActiveSegment(selection)
                .withPreview(new ClaimEditPreview(
                        polygon,
                        selection,
                        Collections.emptyList(),
                        null,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()));
        return EDITOR.apply(session, new ClaimEditIntent(
                ClaimEditIntentType.EXPAND_SEGMENT,
                ClaimEditSource.COMMAND,
                null,
                claimId,
                null,
                amount,
                true,
                Collections.emptyList()));
    }

    /**
     * @param horizontal whether to look at edges running along X (crossed when moving along Z)
     * @param column the player's block coordinate along the edge
     * @param position the player's coordinate across the edge
     * @param towardPositive whether the player looks toward increasing coordinates across the edge
     */
    private static @Nullable Integer nearestEdge(
            @NotNull OrthogonalPolygon polygon,
            boolean horizontal,
            int column,
            double position,
            boolean towardPositive)
    {
        Integer bestIndex = null;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < polygon.edges().size(); i++)
        {
            OrthogonalEdge2i edge = polygon.edges().get(i);
            if (horizontal ? !edge.isHorizontal() : !edge.isVertical())
            {
                continue;
            }
            int spanMin = horizontal ? edge.minX() : edge.minZ();
            int spanMax = horizontal ? edge.maxX() : edge.maxZ();
            if (column < spanMin || column > spanMax)
            {
                continue;
            }

            double edgePosition = horizontal ? edge.start().z() : edge.start().x();
            double distance = edgePosition - position;
            if (towardPositive ? distance < -EDGE_BEHIND_TOLERANCE : distance > EDGE_BEHIND_TOLERANCE)
            {
                continue;
            }

            double absoluteDistance = Math.abs(distance);
            if (absoluteDistance < bestDistance)
            {
                bestDistance = absoluteDistance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }
}
