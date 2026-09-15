package me.ryanhamshire.GriefPrevention;

import com.griefprevention.geometry.OrthogonalEdge2i;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Geometry for {@code /scrollresize}: how a hotbar scroll maps to a resize step, which side of a claim the
 * camera picks, and how box bounds change. Bounds are {@code {minX, minY, minZ, maxX, maxY, maxZ}}.
 */
final class ScrollResizeGeometry
{
    static final int MIN_X = 0;
    static final int MIN_Y = 1;
    static final int MIN_Z = 2;
    static final int MAX_X = 3;
    static final int MAX_Y = 4;
    static final int MAX_Z = 5;

    // Matches /extendclaim: looking further up or down than this targets the top or bottom of a 3D claim.
    private static final double VERTICAL_LOOK_THRESHOLD = 0.75;

    enum Face
    {
        WEST(-1, 0, 0),
        EAST(1, 0, 0),
        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1);

        final int dx;
        final int dy;
        final int dz;

        Face(int dx, int dy, int dz)
        {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        @NotNull Face opposite()
        {
            switch (this)
            {
                case WEST: return EAST;
                case EAST: return WEST;
                case DOWN: return UP;
                case UP: return DOWN;
                case NORTH: return SOUTH;
                default: return NORTH;
            }
        }
    }

    private ScrollResizeGeometry() {}

    /**
     * Interprets a hotbar slot change as a mouse wheel step.
     *
     * <p>Scrolling up selects the previous slot and returns {@code +1} (push the side away); scrolling down selects
     * the next slot and returns {@code -1} (pull it closer). Jumps of more than one slot come from the number keys and
     * return {@code 0}.
     * While scrolling quickly the client reports slots relative to the last slot it sent, before the server's slot
     * reset reaches it, so a step is accepted relative to either slot.
     *
     * @param previousSlot the slot the server has selected
     * @param newSlot the slot the client asked for
     * @param clientSlot the slot the client most recently asked for
     * @return {@code +1} for scroll up, {@code -1} for scroll down, or {@code 0} when this was not a scroll
     */
    static int scrollStep(int previousSlot, int newSlot, int clientSlot)
    {
        int fromClient = wrapSlotDelta(newSlot - clientSlot);
        if (Math.abs(fromClient) == 1) return -fromClient;

        int fromServer = wrapSlotDelta(newSlot - previousSlot);
        if (Math.abs(fromServer) == 1) return -fromServer;

        return 0;
    }

    private static int wrapSlotDelta(int delta)
    {
        int wrapped = delta % 9;
        if (wrapped > 4) wrapped -= 9;
        if (wrapped < -4) wrapped += 9;
        return wrapped;
    }

    static @NotNull EnumSet<Face> allFaces(boolean is3D)
    {
        return is3D ? EnumSet.allOf(Face.class) : EnumSet.of(Face.WEST, Face.EAST, Face.NORTH, Face.SOUTH);
    }

    /**
     * The sides that meet at a selected corner: two for a 2D claim, plus the top or bottom for a 3D claim. A claim one
     * block thick on an axis has both of that axis's sides at every corner, so a 1x1x1 claim keeps all six. A point
     * that isn't a corner of the bounds leaves every side available.
     */
    static @NotNull EnumSet<Face> cornerFaces(int @NotNull [] bounds, int x, int y, int z, boolean is3D)
    {
        EnumSet<Face> faces = EnumSet.noneOf(Face.class);
        boolean onXSide = addCornerSides(faces, x, bounds[MIN_X], bounds[MAX_X], Face.WEST, Face.EAST);
        boolean onZSide = addCornerSides(faces, z, bounds[MIN_Z], bounds[MAX_Z], Face.NORTH, Face.SOUTH);
        if (!onXSide || !onZSide) return allFaces(is3D);

        if (is3D)
        {
            if (bounds[MIN_Y] == bounds[MAX_Y])
            {
                faces.add(Face.DOWN);
                faces.add(Face.UP);
            }
            else
            {
                faces.add(2 * y >= bounds[MIN_Y] + bounds[MAX_Y] ? Face.UP : Face.DOWN);
            }
        }
        return faces;
    }

    private static boolean addCornerSides(
            @NotNull EnumSet<Face> faces,
            int coordinate,
            int min,
            int max,
            @NotNull Face minSide,
            @NotNull Face maxSide)
    {
        if (min == max)
        {
            if (coordinate != min) return false;
            faces.add(minSide);
            faces.add(maxSide);
            return true;
        }

        if (coordinate == min) faces.add(minSide);
        else if (coordinate == max) faces.add(maxSide);
        else return false;
        return true;
    }

    /**
     * Picks the side the camera points at. Looking steeply up or down picks the top or bottom when allowed;
     * otherwise the dominant horizontal axis decides, and the side facing the camera wins when both sides of that
     * axis are allowed.
     */
    static @Nullable Face pickFace(double lookX, double lookY, double lookZ, @NotNull Set<Face> allowed)
    {
        if (Math.abs(lookY) > VERTICAL_LOOK_THRESHOLD)
        {
            Face vertical = pickOnAxis(allowed, lookY > 0 ? Face.UP : Face.DOWN);
            if (vertical != null) return vertical;
        }

        boolean alongX = Math.abs(lookX) >= Math.abs(lookZ);
        Face facingX = lookX >= 0 ? Face.EAST : Face.WEST;
        Face facingZ = lookZ >= 0 ? Face.SOUTH : Face.NORTH;

        Face face = pickOnAxis(allowed, alongX ? facingX : facingZ);
        if (face != null) return face;

        face = pickOnAxis(allowed, alongX ? facingZ : facingX);
        if (face != null) return face;

        return allowed.isEmpty() ? null : allowed.iterator().next();
    }

    private static @Nullable Face pickOnAxis(@NotNull Set<Face> allowed, @NotNull Face preferred)
    {
        if (allowed.contains(preferred)) return preferred;
        Face opposite = preferred.opposite();
        return allowed.contains(opposite) ? opposite : null;
    }

    /**
     * Moves one side of the bounds outward by {@code amount} (inward when negative).
     *
     * @return the new bounds, or {@code null} if the claim would be less than one block thick
     */
    static int @Nullable [] step(int @NotNull [] bounds, @NotNull Face face, int amount)
    {
        int[] next = bounds.clone();
        switch (face)
        {
            case WEST: next[MIN_X] -= amount; break;
            case EAST: next[MAX_X] += amount; break;
            case DOWN: next[MIN_Y] -= amount; break;
            case UP: next[MAX_Y] += amount; break;
            case NORTH: next[MIN_Z] -= amount; break;
            default: next[MAX_Z] += amount; break;
        }

        if (next[MIN_X] > next[MAX_X] || next[MIN_Y] > next[MAX_Y] || next[MIN_Z] > next[MAX_Z]) return null;
        return next;
    }

    /**
     * The side of a claim's box the player's line of sight points at. From outside the claim this is the side the ray
     * enters through, which faces the player (the top when looking down on it); from inside it is the side the ray
     * leaves through. 2D claims have no top or bottom, so only their walls count.
     *
     * @return the targeted side, or {@code null} if the ray misses the claim or only runs along its height
     */
    static @Nullable Face rayFace(
            double eyeX,
            double eyeY,
            double eyeZ,
            double lookX,
            double lookY,
            double lookZ,
            int @NotNull [] bounds,
            boolean is3D)
    {
        RaySlab slab = new RaySlab();
        if (!slab.clip(eyeX, lookX, bounds[MIN_X], bounds[MAX_X] + 1, Face.WEST, Face.EAST)) return null;
        if (!slab.clip(eyeZ, lookZ, bounds[MIN_Z], bounds[MAX_Z] + 1, Face.NORTH, Face.SOUTH)) return null;
        if (is3D && !slab.clip(eyeY, lookY, bounds[MIN_Y], bounds[MAX_Y] + 1, Face.DOWN, Face.UP)) return null;

        if (slab.near > slab.far || slab.far < 0) return null;
        return slab.near >= 0 ? slab.nearFace : slab.farFace;
    }

    private static final class RaySlab
    {
        private double near = Double.NEGATIVE_INFINITY;
        private double far = Double.POSITIVE_INFINITY;
        private @Nullable Face nearFace;
        private @Nullable Face farFace;

        private boolean clip(double origin, double direction, double min, double max, Face minSide, Face maxSide)
        {
            if (Math.abs(direction) < 1.0E-9) return origin >= min && origin <= max;

            double toMin = (min - origin) / direction;
            double toMax = (max - origin) / direction;
            double enter = direction > 0 ? toMin : toMax;
            double exit = direction > 0 ? toMax : toMin;

            if (enter > this.near)
            {
                this.near = enter;
                this.nearFace = direction > 0 ? minSide : maxSide;
            }
            if (exit < this.far)
            {
                this.far = exit;
                this.farFace = direction > 0 ? maxSide : minSide;
            }
            return true;
        }
    }

    /**
     * Makes scrolling follow the camera: scrolling up pushes the side away from the player and scrolling down pulls it
     * toward them. {@code facing} is how far the side's outward direction points along the camera; when the player
     * looks back at a side the step flips, so scrolling up still moves it away.
     */
    static int directedStep(int scroll, double facing)
    {
        return facing < 0 ? -scroll : scroll;
    }

    static double facing(@NotNull Face face, double lookX, double lookY, double lookZ)
    {
        return face.dx * lookX + face.dy * lookY + face.dz * lookZ;
    }

    /**
     * The outward direction of a shaped outline's edge as {@code {dx, dz}}: the direction the claim editor moves the
     * edge for a positive expansion. Z grows southward, so a positive signed area is a clockwise loop on the map.
     */
    static int @NotNull [] outwardNormal(@NotNull OrthogonalPolygon polygon, int edgeIndex)
    {
        List<OrthogonalPoint2i> corners = polygon.corners();
        long area2 = 0L;
        for (int i = 0; i < corners.size(); i++)
        {
            OrthogonalPoint2i current = corners.get(i);
            OrthogonalPoint2i next = corners.get((i + 1) % corners.size());
            area2 += (long) current.x() * next.z() - (long) next.x() * current.z();
        }

        OrthogonalEdge2i edge = polygon.edges().get(edgeIndex);
        int dx = Integer.compare(edge.end().x(), edge.start().x());
        int dz = Integer.compare(edge.end().z(), edge.start().z());
        return area2 > 0 ? new int[] {dz, -dx} : new int[] {-dz, dx};
    }

    /**
     * Picks which of the two edges meeting at a shaped claim's corner the camera points along: looking along X moves
     * the edge that runs north-south, looking along Z moves the edge that runs east-west.
     */
    static @Nullable Integer cornerEdge(
            @NotNull OrthogonalPolygon polygon,
            @NotNull OrthogonalPoint2i corner,
            double lookX,
            double lookZ)
    {
        Integer runsAlongZ = null;
        Integer runsAlongX = null;
        List<OrthogonalEdge2i> edges = polygon.edges();
        for (int i = 0; i < edges.size(); i++)
        {
            OrthogonalEdge2i edge = edges.get(i);
            if (!edge.start().equals(corner) && !edge.end().equals(corner)) continue;

            if (edge.isVertical()) runsAlongZ = i;
            else if (edge.isHorizontal()) runsAlongX = i;
        }

        boolean alongX = Math.abs(lookX) >= Math.abs(lookZ);
        Integer preferred = alongX ? runsAlongZ : runsAlongX;
        return preferred != null ? preferred : (alongX ? runsAlongX : runsAlongZ);
    }

    /** Finds where a selected corner ended up after its edge moved, within {@code maxDistance} blocks. */
    static @Nullable OrthogonalPoint2i nearestCorner(
            @NotNull OrthogonalPolygon polygon,
            @NotNull OrthogonalPoint2i previous,
            int maxDistance)
    {
        OrthogonalPoint2i best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (OrthogonalPoint2i corner : polygon.corners())
        {
            int distance = Math.abs(corner.x() - previous.x()) + Math.abs(corner.z() - previous.z());
            if (distance <= maxDistance && distance < bestDistance)
            {
                best = corner;
                bestDistance = distance;
            }
        }
        return best;
    }
}
