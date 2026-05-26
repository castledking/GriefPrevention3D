package com.griefprevention.geometry;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable closed orthogonal polygon in the X/Z plane.
 */
public final class OrthogonalPolygon
{
    private final List<OrthogonalPoint2i> corners;
    private final List<OrthogonalEdge2i> edges;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;

    OrthogonalPolygon(@NotNull List<OrthogonalPoint2i> corners)
    {
        this.corners = List.copyOf(corners);
        this.edges = OrthogonalPolygonValidator.buildEdges(this.corners);

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (OrthogonalPoint2i corner : this.corners)
        {
            minX = Math.min(minX, corner.x());
            maxX = Math.max(maxX, corner.x());
            minZ = Math.min(minZ, corner.z());
            maxZ = Math.max(maxZ, corner.z());
        }

        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public static @NotNull OrthogonalPolygonValidationResult validatePath(@NotNull List<OrthogonalPoint2i> rawPath)
    {
        return OrthogonalPolygonValidator.validatePath(rawPath);
    }

    @SuppressWarnings("null")
    public static @NotNull OrthogonalPolygon fromRectangle(int minX, int minZ, int maxX, int maxZ)
    {
        return fromClosedPath(List.of(
                new OrthogonalPoint2i(minX, minZ),
                new OrthogonalPoint2i(maxX, minZ),
                new OrthogonalPoint2i(maxX, maxZ),
                new OrthogonalPoint2i(minX, maxZ),
                new OrthogonalPoint2i(minX, minZ)
        ));
    }

    public static @NotNull OrthogonalPolygon fromClosedPath(@NotNull List<OrthogonalPoint2i> rawPath)
    {
        OrthogonalPolygonValidationResult result = validatePath(rawPath);
        if (!result.isValid())
        {
            String message = result.issues().isEmpty()
                    ? "Invalid orthogonal polygon path."
                    : result.issues().get(0).message();
            throw new IllegalArgumentException(message);
        }

        return result.polygon();
    }

    public @NotNull List<OrthogonalPoint2i> corners()
    {
        return this.corners;
    }

    public @NotNull List<OrthogonalPoint2i> closedPath()
    {
        return OrthogonalPolygonValidator.closePath(this.corners);
    }

    public @NotNull List<OrthogonalEdge2i> edges()
    {
        return this.edges;
    }

    public @NotNull List<Integer> edgeIndexesContainingInteriorPoint(@NotNull OrthogonalPoint2i point)
    {
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < this.edges.size(); i++)
        {
            if (this.edges.get(i).containsInteriorPoint(point))
            {
                matches.add(i);
            }
        }

        List<Integer> result = List.copyOf(matches);
        return result;
    }

    public @NotNull OrthogonalPolygon insertNode(int edgeIndex, @NotNull OrthogonalPoint2i point)
    {
        if (edgeIndex < 0 || edgeIndex >= this.edges.size())
        {
            throw new IllegalArgumentException("Edge index out of bounds: " + edgeIndex);
        }

        OrthogonalEdge2i edge = this.edges.get(edgeIndex);
        if (!edge.containsInteriorPoint(point))
        {
            throw new IllegalArgumentException("Node point must lie inside the selected edge.");
        }

        List<OrthogonalPoint2i> updatedCorners = new ArrayList<>(this.corners.size() + 1);
        updatedCorners.addAll(this.corners.subList(0, edgeIndex + 1));
        updatedCorners.add(point);
        updatedCorners.addAll(this.corners.subList(edgeIndex + 1, this.corners.size()));
        return new OrthogonalPolygon(updatedCorners);
    }

    public boolean isRemovableNode(int cornerIndex)
    {
        if (cornerIndex < 0 || cornerIndex >= this.corners.size())
        {
            return false;
        }

        OrthogonalPoint2i previous = this.corners.get(Math.floorMod(cornerIndex - 1, this.corners.size()));
        OrthogonalPoint2i current = this.corners.get(cornerIndex);
        OrthogonalPoint2i next = this.corners.get((cornerIndex + 1) % this.corners.size());
        return (previous.x() == current.x() && current.x() == next.x())
                || (previous.z() == current.z() && current.z() == next.z());
    }

    public @NotNull OrthogonalPolygon removeNode(int cornerIndex)
    {
        if (cornerIndex < 0 || cornerIndex >= this.corners.size())
        {
            throw new IllegalArgumentException("Corner index out of bounds: " + cornerIndex);
        }

        if (!isRemovableNode(cornerIndex))
        {
            throw new IllegalArgumentException("Only segment marker nodes can be removed.");
        }

        List<OrthogonalPoint2i> updatedCorners = new ArrayList<>(this.corners);
        updatedCorners.remove(cornerIndex);
        return fromClosedPath(OrthogonalPolygonValidator.closePath(updatedCorners));
    }

    public @NotNull OrthogonalPolygonValidationResult moveCorner(int cornerIndex, @NotNull OrthogonalPoint2i point)
    {
        if (cornerIndex < 0 || cornerIndex >= this.corners.size())
        {
            throw new IllegalArgumentException("Corner index out of bounds: " + cornerIndex);
        }

        OrthogonalPoint2i current = this.corners.get(cornerIndex);
        if (current.equals(point))
        {
            return new OrthogonalPolygonValidationResult(this.closedPath(), List.of(), this);
        }

        int previousIndex = Math.floorMod(cornerIndex - 1, this.corners.size());
        int nextIndex = (cornerIndex + 1) % this.corners.size();
        OrthogonalPoint2i previous = this.corners.get(previousIndex);
        OrthogonalPoint2i next = this.corners.get(nextIndex);

        List<OrthogonalPoint2i> updatedCorners = new ArrayList<>(this.corners);
        updatedCorners.set(cornerIndex, point);
        updatedCorners.set(previousIndex, alignNeighbor(previous, current, point));
        updatedCorners.set(nextIndex, alignNeighbor(next, current, point));
        return validatePath(OrthogonalPolygonValidator.closePath(normalizeCorners(updatedCorners)));
    }

    private @NotNull OrthogonalPoint2i alignNeighbor(
            @NotNull OrthogonalPoint2i neighbor,
            @NotNull OrthogonalPoint2i current,
            @NotNull OrthogonalPoint2i movedCorner)
    {
        if (neighbor.x() == current.x())
        {
            return new OrthogonalPoint2i(movedCorner.x(), neighbor.z());
        }

        if (neighbor.z() == current.z())
        {
            return new OrthogonalPoint2i(neighbor.x(), movedCorner.z());
        }

        throw new IllegalStateException("Adjacent polygon corners must share an axis.");
    }

    public @NotNull OrthogonalPolygonValidationResult expandEdge(int edgeIndex, int amount)
    {
        if (edgeIndex < 0 || edgeIndex >= this.edges.size())
        {
            throw new IllegalArgumentException("Edge index out of bounds: " + edgeIndex);
        }

        if (amount == 0)
        {
            return new OrthogonalPolygonValidationResult(this.closedPath(), List.of(), this);
        }

        OrthogonalEdge2i edge = this.edges.get(edgeIndex);
        List<OrthogonalPoint2i> updatedCorners = new ArrayList<>(this.corners);

        OrthogonalPoint2i movedStart;
        OrthogonalPoint2i movedEnd;
        if (edge.isHorizontal())
        {
            movedStart = new OrthogonalPoint2i(edge.start().x(), edge.start().z() + amount);
            movedEnd = new OrthogonalPoint2i(edge.end().x(), edge.end().z() + amount);
        }
        else if (edge.isVertical())
        {
            movedStart = new OrthogonalPoint2i(edge.start().x() + amount, edge.start().z());
            movedEnd = new OrthogonalPoint2i(edge.end().x() + amount, edge.end().z());
        }
        else
        {
            throw new IllegalStateException("Cannot expand a non-orthogonal edge.");
        }

        int insertIndex = edgeIndex + 1;
        updatedCorners.add(insertIndex, movedStart);
        updatedCorners.add(insertIndex + 1, movedEnd);
        return validatePath(OrthogonalPolygonValidator.closePath(normalizeCorners(updatedCorners)));
    }

    public @NotNull OrthogonalPolygonValidationResult moveEdgeRun(int startEdgeIndex, int endEdgeIndex, int amount)
    {
        if (startEdgeIndex < 0 || startEdgeIndex >= this.edges.size())
        {
            throw new IllegalArgumentException("Start edge index out of bounds: " + startEdgeIndex);
        }

        if (endEdgeIndex < 0 || endEdgeIndex >= this.edges.size())
        {
            throw new IllegalArgumentException("End edge index out of bounds: " + endEdgeIndex);
        }

        if (amount == 0)
        {
            return new OrthogonalPolygonValidationResult(this.closedPath(), List.of(), this);
        }

        OrthogonalEdge2i referenceEdge = this.edges.get(startEdgeIndex);
        if (!referenceEdge.isOrthogonal())
        {
            throw new IllegalStateException("Cannot move a non-orthogonal edge run.");
        }

        boolean horizontal = referenceEdge.isHorizontal();
        int referenceCoordinate = horizontal ? referenceEdge.start().z() : referenceEdge.start().x();
        List<Integer> runCorners = cornerIndexesForEdgeRun(startEdgeIndex, endEdgeIndex);
        for (int edgeIndex : edgeIndexesInRun(startEdgeIndex, endEdgeIndex))
        {
            OrthogonalEdge2i edge = this.edges.get(edgeIndex);
            boolean sameOrientation = horizontal ? edge.isHorizontal() : edge.isVertical();
            int edgeCoordinate = horizontal ? edge.start().z() : edge.start().x();
            if (!sameOrientation || edgeCoordinate != referenceCoordinate)
            {
                throw new IllegalArgumentException("Edge run must be a contiguous straight face.");
            }
        }

        List<OrthogonalPoint2i> updatedCorners = new ArrayList<>(this.corners);
        for (int cornerIndex : runCorners)
        {
            OrthogonalPoint2i point = updatedCorners.get(cornerIndex);
            updatedCorners.set(
                    cornerIndex,
                    horizontal
                            ? new OrthogonalPoint2i(point.x(), point.z() + amount)
                            : new OrthogonalPoint2i(point.x() + amount, point.z())
            );
        }

        return validatePath(OrthogonalPolygonValidator.closePath(normalizeCorners(updatedCorners)));
    }

    public @NotNull OrthogonalDirection outwardDirectionForEdge(int edgeIndex, int amount)
    {
        OrthogonalEdge2i edge = this.edges.get(edgeIndex);
        OrthogonalDirection positiveDirection = edge.outwardDirectionForPositiveOffset();
        if (amount >= 0)
        {
            return positiveDirection;
        }

        return positiveDirection.opposite();
    }

    public int minX()
    {
        return this.minX;
    }

    public int maxX()
    {
        return this.maxX;
    }

    public int minZ()
    {
        return this.minZ;
    }

    public int maxZ()
    {
        return this.maxZ;
    }

    /**
     * Counts the number of integer-lattice cells (x, z) contained in or on the boundary of this
     * orthogonal polygon. Uses Pick's theorem: total lattice points = A + B/2 + 1, where A is the
     * shoelace area and B is the perimeter lattice-point count (equal to the sum of edge lengths
     * for an axis-aligned polygon with integer corners).
     *
     * @return the cell count, clamped to Integer.MAX_VALUE
     */
    public int cellCount()
    {
        long twoArea = 0L;
        long perimeter = 0L;
        int n = this.corners.size();
        for (int i = 0; i < n; i++)
        {
            OrthogonalPoint2i a = this.corners.get(i);
            OrthogonalPoint2i b = this.corners.get((i + 1) % n);
            twoArea += (long) a.x() * b.z() - (long) b.x() * a.z();
            perimeter += Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
        }
        long area = Math.abs(twoArea) / 2L;
        long total = area + perimeter / 2L + 1L;
        if (total > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) total;
    }

    private @NotNull List<OrthogonalPoint2i> normalizeCorners(@NotNull List<OrthogonalPoint2i> corners)
    {
        List<OrthogonalPoint2i> normalized = new ArrayList<>(corners);
        boolean changed;
        do
        {
            changed = false;
            List<OrthogonalPoint2i> nextPass = new ArrayList<>(normalized.size());
            for (int i = 0; i < normalized.size(); i++)
            {
                OrthogonalPoint2i previous = normalized.get(Math.floorMod(i - 1, normalized.size()));
                OrthogonalPoint2i current = normalized.get(i);
                OrthogonalPoint2i next = normalized.get((i + 1) % normalized.size());

                if (current.equals(previous) || current.equals(next))
                {
                    changed = true;
                    continue;
                }

                boolean sameX = previous.x() == current.x() && current.x() == next.x();
                boolean sameZ = previous.z() == current.z() && current.z() == next.z();
                if (sameX || sameZ)
                {
                    changed = true;
                    continue;
                }

                nextPass.add(current);
            }

            normalized = nextPass;
        }
        while (changed && normalized.size() >= 4);

        return normalized;
    }

    private @NotNull List<Integer> edgeIndexesInRun(int startEdgeIndex, int endEdgeIndex)
    {
        List<Integer> indexes = new ArrayList<>();
        int edgeIndex = startEdgeIndex;
        do
        {
            indexes.add(edgeIndex);
            if (edgeIndex == endEdgeIndex)
            {
                break;
            }
            edgeIndex = (edgeIndex + 1) % this.edges.size();
        }
        while (edgeIndex != startEdgeIndex);

        return indexes;
    }

    private @NotNull List<Integer> cornerIndexesForEdgeRun(int startEdgeIndex, int endEdgeIndex)
    {
        List<Integer> cornerIndexes = new ArrayList<>();
        int cornerIndex = startEdgeIndex;
        cornerIndexes.add(cornerIndex);
        while (cornerIndex != ((endEdgeIndex + 1) % this.corners.size()))
        {
            cornerIndex = (cornerIndex + 1) % this.corners.size();
            cornerIndexes.add(cornerIndex);
        }
        return cornerIndexes;
    }
}
