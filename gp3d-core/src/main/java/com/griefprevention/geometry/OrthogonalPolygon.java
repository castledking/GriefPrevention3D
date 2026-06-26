package com.griefprevention.geometry;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

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
        this.corners = Collections.unmodifiableList(new ArrayList<>(corners));
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
        return fromClosedPath(Arrays.asList(new OrthogonalPoint2i(minX, minZ), new OrthogonalPoint2i(maxX, minZ), new OrthogonalPoint2i(maxX, maxZ), new OrthogonalPoint2i(minX, maxZ), new OrthogonalPoint2i(minX, minZ)));
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

        List<Integer> result = Collections.unmodifiableList(new ArrayList<>(matches));
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
            return new OrthogonalPolygonValidationResult(this.closedPath(), Collections.emptyList(), this);
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
            return new OrthogonalPolygonValidationResult(this.closedPath(), Collections.emptyList(), this);
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
            return new OrthogonalPolygonValidationResult(this.closedPath(), Collections.emptyList(), this);
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

    public boolean contains(@NotNull OrthogonalPoint2i point)
    {
        if (this.corners().contains(point))
        {
            return true;
        }

        if (!this.edgeIndexesContainingInteriorPoint(point).isEmpty())
        {
            return true;
        }

        double sampleX = point.x() + 0.5D;
        double sampleZ = point.z() + 0.5D;
        boolean inside = false;
        List<OrthogonalPoint2i> corners = this.corners();
        for (int i = 0, j = corners.size() - 1; i < corners.size(); j = i++)
        {
            OrthogonalPoint2i a = corners.get(i);
            OrthogonalPoint2i b = corners.get(j);
            boolean crosses = (a.z() > sampleZ) != (b.z() > sampleZ);
            if (!crosses)
            {
                continue;
            }

            double intersectionX = (double) (b.x() - a.x()) * (sampleZ - a.z()) / (double) (b.z() - a.z()) + a.x();
            if (sampleX < intersectionX)
            {
                inside = !inside;
            }
        }

        return inside;
    }


    public boolean containsCell(int x, int z)
    {
        OrthogonalPoint2i point = new OrthogonalPoint2i(x, z);
        if (this.corners().contains(point))
        {
            return true;
        }

        if (!this.edgeIndexesContainingInteriorPoint(point).isEmpty())
        {
            return true;
        }

        double sampleX = point.x() + 0.5D;
        double sampleZ = point.z() + 0.5D;
        boolean inside = false;
        List<OrthogonalPoint2i> corners = this.corners();
        for (int i = 0, j = corners.size() - 1; i < corners.size(); j = i++)
        {
            OrthogonalPoint2i a = corners.get(i);
            OrthogonalPoint2i b = corners.get(j);
            boolean crosses = (a.z() > sampleZ) != (b.z() > sampleZ);
            if (!crosses)
            {
                continue;
            }

            double intersectionX = (double) (b.x() - a.x()) * (sampleZ - a.z()) / (double) (b.z() - a.z()) + a.x();
            if (sampleX < intersectionX)
            {
                inside = !inside;
            }
        }

        return inside;
    }

    public static @NotNull OrthogonalPolygon union(
            @NotNull OrthogonalPolygon first,
            @NotNull OrthogonalPolygon second)
    {
        Set<OrthogonalPoint2i> occupied = new HashSet<>();
        int minX = Math.min(first.minX(), second.minX());
        int maxX = Math.max(first.maxX(), second.maxX());
        int minZ = Math.min(first.minZ(), second.minZ());
        int maxZ = Math.max(first.maxZ(), second.maxZ());

        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                OrthogonalPoint2i point = new OrthogonalPoint2i(x, z);
                if (first.containsCell(x, z) || second.containsCell(x, z))
                {
                    occupied.add(point);
                }
            }
        }

        if (occupied.isEmpty())
        {
            throw new IllegalArgumentException("The union of the polygons is empty.");
        }

        // Ensure the occupied set is connected. If the two polygons are
        // disconnected (e.g. reshape path through unclaimed land between
        // two claims), fill cells along Manhattan paths between the
        // closest cells of each component until the set is connected.
        ensureConnected(occupied);

        return fromOccupiedPoints(occupied);
    }

    /**
     * Ensure all cells in the occupied set are connected by filling gaps
     * between disconnected components using the FULL bounding box between
     * the closest pair of cells (not just a 1-cell corridor).
     *
     * A thin 1-cell corridor gets compressed away by the contour simplifier,
     * causing the merged shape to collapse to a rectangle. Using the full
     * bounding box between components ensures the connection survives
     * simplification and the intended shape is preserved.
     */
    private static void ensureConnected(@NotNull Set<OrthogonalPoint2i> occupied)
    {
        if (occupied.size() <= 1) return;

        // Find connected components using BFS (4-connectivity)
        Set<OrthogonalPoint2i> unvisited = new HashSet<>(occupied);
        List<Set<OrthogonalPoint2i>> components = new ArrayList<>();

        while (!unvisited.isEmpty())
        {
            OrthogonalPoint2i start = unvisited.iterator().next();
            Set<OrthogonalPoint2i> component = new HashSet<>();
            Queue<OrthogonalPoint2i> queue = new LinkedList<>();
            queue.add(start);
            unvisited.remove(start);

            while (!queue.isEmpty())
            {
                OrthogonalPoint2i current = queue.poll();
                component.add(current);

                int[][] neighbors = {{1,0},{-1,0},{0,1},{0,-1}};
                for (int[] n : neighbors)
                {
                    OrthogonalPoint2i neighbor = new OrthogonalPoint2i(current.x() + n[0], current.z() + n[1]);
                    if (unvisited.remove(neighbor))
                    {
                        queue.add(neighbor);
                    }
                }
            }
            components.add(component);
        }

        if (components.size() <= 1) return; // Already connected

        // Iteratively connect the closest pair of components until fully connected
        while (components.size() > 1)
        {
            int bestI = 0, bestJ = 1;
            int bestDist = Integer.MAX_VALUE;
            OrthogonalPoint2i bestA = null, bestB = null;

            for (int i = 0; i < components.size(); i++)
            {
                for (int j = i + 1; j < components.size(); j++)
                {
                    for (OrthogonalPoint2i a : components.get(i))
                    {
                        for (OrthogonalPoint2i b : components.get(j))
                        {
                            int dist = Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
                            if (dist < bestDist)
                            {
                                bestDist = dist;
                                bestA = a;
                                bestB = b;
                                bestI = i;
                                bestJ = j;
                            }
                        }
                    }
                }
            }

            // Fill a 2-cell-wide Manhattan path between bestA and bestB.
            // This connects the components while preserving the original
            // shapes, unlike a full bounding box which always produces a
            // rectangle. A 2-cell width avoids contour tracing failures
            // that can occur at diagonal convergence points with 1-cell
            // corridors, and prevents the path from being collapsed by
            // the contour simplifier.
            int cx = bestA.x(), cz = bestA.z();
            int tx = bestB.x(), tz = bestB.z();
            while (cx != tx || cz != tz)
            {
                fillCellAndOrthogonalNeighbors(occupied, cx, cz);
                if (cx < tx) cx++;
                else if (cx > tx) cx--;
                else if (cz < tz) cz++;
                else if (cz > tz) cz--;
            }
            fillCellAndOrthogonalNeighbors(occupied, tx, tz);

            // Merge the two components
            components.get(bestI).addAll(components.get(bestJ));
            components.remove(bestJ);
        }
    }

    private static void fillCellAndOrthogonalNeighbors(
            @NotNull Set<OrthogonalPoint2i> occupied,
            int x, int z)
    {
        occupied.add(new OrthogonalPoint2i(x, z));
        occupied.add(new OrthogonalPoint2i(x + 1, z));
        occupied.add(new OrthogonalPoint2i(x - 1, z));
        occupied.add(new OrthogonalPoint2i(x, z + 1));
        occupied.add(new OrthogonalPoint2i(x, z - 1));
    }

    public static @NotNull OrthogonalPolygon fromOccupiedPoints(@NotNull Set<OrthogonalPoint2i> occupied)
    {
        List<ContourVertex> tracedContour = traceOccupiedContour(occupied);
        List<ContourVertex> compressedContour = compressContourPath(tracedContour);
        List<OrthogonalPoint2i> mappedCorners = mapContourCornersToOccupiedPoints(compressedContour, occupied);
        List<OrthogonalPoint2i> normalizedCorners = compressOccupiedBoundaryPath(mappedCorners);
        return OrthogonalPolygon.fromClosedPath(normalizedCorners);
    }

    private static @NotNull List<ContourVertex> traceOccupiedContour(@NotNull Set<OrthogonalPoint2i> occupied)
    {
        List<ContourEdge> edges = new ArrayList<>();
        for (OrthogonalPoint2i point : occupied)
        {
            addContourEdgesForPoint(occupied, point, edges);
        }

        if (edges.isEmpty())
        {
            throw new IllegalArgumentException("Unable to trace merged claim boundary.");
        }

        Map<ContourVertex, List<ContourEdge>> outgoing = new HashMap<>();
        for (ContourEdge edge : edges)
        {
            outgoing.computeIfAbsent(edge.start(), ignored -> new ArrayList<>()).add(edge);
        }

        ContourEdge startEdge = edges.stream()
                .min(Comparator.comparingInt((ContourEdge edge) -> edge.start().z())
                        .thenComparingInt(edge -> edge.start().x())
                        .thenComparingInt(edge -> edge.end().x())
                        .thenComparingInt(edge -> edge.end().z()))
                .get();

        List<ContourVertex> traced = new ArrayList<>();
        traced.add(startEdge.start());

        Set<ContourEdge> visited = new HashSet<>();
        ContourEdge current = startEdge;
        int guard = edges.size() + 1;
        while (guard-- > 0)
        {
            if (!visited.add(current))
            {
                throw new IllegalArgumentException("Merged claim boundary could not be followed.");
            }

            traced.add(current.end());
            if (current.end().equals(startEdge.start()))
            {
                break;
            }

            List<ContourEdge> nextEdges = outgoing.get(current.end());
            if (nextEdges == null || nextEdges.size() != 1)
            {
                throw new IllegalArgumentException("Merged claim boundary could not be followed.");
            }

            current = nextEdges.get(0);
        }

        if (!traced.get(traced.size() - 1).equals(startEdge.start()))
        {
            throw new IllegalArgumentException("Merged claim boundary did not close.");
        }

        if (visited.size() != edges.size())
        {
            throw new IllegalArgumentException("Merged claim boundary is disconnected.");
        }

        return traced;
    }

    private static void addContourEdgesForPoint(
            @NotNull Set<OrthogonalPoint2i> occupied,
            @NotNull OrthogonalPoint2i point,
            @NotNull List<ContourEdge> edges)
    {
        int x = point.x();
        int z = point.z();

        if (!occupied.contains(new OrthogonalPoint2i(x, z - 1)))
        {
            edges.add(new ContourEdge(
                    new ContourVertex(2 * x - 1, 2 * z - 1),
                    new ContourVertex(2 * x + 1, 2 * z - 1)
            ));
        }

        if (!occupied.contains(new OrthogonalPoint2i(x + 1, z)))
        {
            edges.add(new ContourEdge(
                    new ContourVertex(2 * x + 1, 2 * z - 1),
                    new ContourVertex(2 * x + 1, 2 * z + 1)
            ));
        }

        if (!occupied.contains(new OrthogonalPoint2i(x, z + 1)))
        {
            edges.add(new ContourEdge(
                    new ContourVertex(2 * x + 1, 2 * z + 1),
                    new ContourVertex(2 * x - 1, 2 * z + 1)
            ));
        }

        if (!occupied.contains(new OrthogonalPoint2i(x - 1, z)))
        {
            edges.add(new ContourEdge(
                    new ContourVertex(2 * x - 1, 2 * z + 1),
                    new ContourVertex(2 * x - 1, 2 * z - 1)
            ));
        }
    }

    private static @NotNull List<ContourVertex> compressContourPath(@NotNull List<ContourVertex> traced)
    {
        if (traced.size() < 4)
        {
            throw new IllegalArgumentException("Merged claim boundary is too small.");
        }

        List<ContourVertex> compressed = new ArrayList<>();
        int cycleLength = traced.size() - 1;
        for (int i = 0; i < cycleLength; i++)
        {
            ContourVertex previous = traced.get((i - 1 + cycleLength) % cycleLength);
            ContourVertex current = traced.get(i);
            ContourVertex next = traced.get((i + 1) % cycleLength);

            int dx1 = Integer.compare(current.x(), previous.x());
            int dz1 = Integer.compare(current.z(), previous.z());
            int dx2 = Integer.compare(next.x(), current.x());
            int dz2 = Integer.compare(next.z(), current.z());

            if (i == 0 || dx1 != dx2 || dz1 != dz2)
            {
                compressed.add(current);
            }
        }

        compressed.add(compressed.get(0));
        return compressed;
    }

    private static @NotNull List<OrthogonalPoint2i> mapContourCornersToOccupiedPoints(
            @NotNull List<ContourVertex> contour,
            @NotNull Set<OrthogonalPoint2i> occupied)
    {
        List<OrthogonalPoint2i> mapped = new ArrayList<>();
        int cycleLength = contour.size() - 1;
        for (int i = 0; i < cycleLength; i++)
        {
            ContourVertex previous = contour.get((i - 1 + cycleLength) % cycleLength);
            ContourVertex current = contour.get(i);
            ContourVertex next = contour.get((i + 1) % cycleLength);
            OrthogonalPoint2i point = resolveContourCornerPoint(previous, current, next, occupied);
            if (mapped.isEmpty() || !mapped.get(mapped.size() - 1).equals(point))
            {
                mapped.add(point);
            }
        }

        if (mapped.size() < 4)
        {
            throw new IllegalArgumentException("Merged claim boundary is too small.");
        }

        mapped.add(mapped.get(0));
        return mapped;
    }

    private static @NotNull OrthogonalPoint2i resolveContourCornerPoint(
            @NotNull ContourVertex previous,
            @NotNull ContourVertex vertex,
            @NotNull ContourVertex next,
            @NotNull Set<OrthogonalPoint2i> occupied)
    {
        int lowX = Math.floorDiv(vertex.x(), 2);
        int highX = Math.floorDiv(vertex.x() + 1, 2);
        int lowZ = Math.floorDiv(vertex.z(), 2);
        int highZ = Math.floorDiv(vertex.z() + 1, 2);

        int incomingDirection = contourDirection(previous, vertex);
        int outgoingDirection = contourDirection(vertex, next);
        int interiorFromIncoming = rotateRight(incomingDirection);
        int interiorFromOutgoing = rotateRight(outgoingDirection);

        int resolvedX;
        if (interiorFromIncoming == 0 || interiorFromOutgoing == 0)
        {
            resolvedX = highX;
        }
        else if (interiorFromIncoming == 2 || interiorFromOutgoing == 2)
        {
            resolvedX = lowX;
        }
        else
        {
            throw new IllegalArgumentException("Merged claim boundary could not be followed.");
        }

        int resolvedZ;
        if (interiorFromIncoming == 1 || interiorFromOutgoing == 1)
        {
            resolvedZ = highZ;
        }
        else if (interiorFromIncoming == 3 || interiorFromOutgoing == 3)
        {
            resolvedZ = lowZ;
        }
        else
        {
            throw new IllegalArgumentException("Merged claim boundary could not be followed.");
        }

        OrthogonalPoint2i match = new OrthogonalPoint2i(resolvedX, resolvedZ);
        if (!occupied.contains(match))
        {
            throw new IllegalArgumentException("Merged claim boundary could not be followed.");
        }

        return match;
    }

    private static int contourDirection(@NotNull ContourVertex start, @NotNull ContourVertex end)
    {
        if (end.x() > start.x())
        {
            return 0;
        }
        if (end.z() > start.z())
        {
            return 1;
        }
        if (end.x() < start.x())
        {
            return 2;
        }
        if (end.z() < start.z())
        {
            return 3;
        }
        throw new IllegalArgumentException("Contour path cannot contain duplicate vertices.");
    }

    private static int rotateRight(int direction)
    {
        switch (direction)
        {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 0;
            default:
                throw new IllegalArgumentException("Unknown contour direction.");
        }
    }

    private static @NotNull List<OrthogonalPoint2i> compressOccupiedBoundaryPath(@NotNull List<OrthogonalPoint2i> traced)
    {
        List<OrthogonalPoint2i> path = new ArrayList<>(traced);
        if (path.size() < 4)
        {
            throw new IllegalArgumentException("Merged claim boundary is too small.");
        }

        List<OrthogonalPoint2i> compressed = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++)
        {
            OrthogonalPoint2i previous = path.get((i - 1 + path.size() - 1) % (path.size() - 1));
            OrthogonalPoint2i current = path.get(i);
            OrthogonalPoint2i next = path.get((i + 1) % (path.size() - 1));

            int dx1 = Integer.compare(current.x(), previous.x());
            int dz1 = Integer.compare(current.z(), previous.z());
            int dx2 = Integer.compare(next.x(), current.x());
            int dz2 = Integer.compare(next.z(), current.z());

            if (i == 0 || dx1 != dx2 || dz1 != dz2)
            {
                compressed.add(current);
            }
        }

        compressed.add(compressed.get(0));
        return compressed;
    }

    private static final class ContourVertex
    {
        private final int x;
        private final int z;

        private ContourVertex(int x, int z)
        {
            this.x = x;
            this.z = z;
        }

        int x()
        {
            return x;
        }

        int z()
        {
            return z;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }
            if (!(other instanceof ContourVertex))
            {
                return false;
            }
            ContourVertex that = (ContourVertex) other;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(x, z);
        }
    }

    private static final class ContourEdge
    {
        private final @NotNull ContourVertex start;
        private final @NotNull ContourVertex end;

        private ContourEdge(@NotNull ContourVertex start, @NotNull ContourVertex end)
        {
            this.start = start;
            this.end = end;
        }

        @NotNull ContourVertex start()
        {
            return start;
        }

        @NotNull ContourVertex end()
        {
            return end;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other)
            {
                return true;
            }
            if (!(other instanceof ContourEdge))
            {
                return false;
            }
            ContourEdge that = (ContourEdge) other;
            return start.equals(that.start) && end.equals(that.end);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(start, end);
        }
    }
}
