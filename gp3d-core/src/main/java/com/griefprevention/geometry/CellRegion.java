package com.griefprevention.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Exact inclusive block-column coverage, stored as horizontal ranges over bands of rows.
 * Work depends on boundary complexity rather than the number of blocks inside a claim.
 * Half-open long coordinates keep max-coordinate + 1 and range arithmetic from overflowing.
 */
public final class CellRegion
{
    private final List<Band> bands;

    private CellRegion(List<Band> bands) { this.bands = bands; }

    public static CellRegion of(OrthogonalPolygon polygon)
    {
        TreeSet<Long> rows = new TreeSet<>();
        for (OrthogonalPoint2i p : polygon.corners())
        {
            rows.add((long) p.z());
            rows.add((long) p.z() + 1);
        }
        List<Long> cuts = new ArrayList<>(rows);
        List<Band> bands = new ArrayList<>();
        for (int row = 0; row + 1 < cuts.size(); row++)
        {
            long z = cuts.get(row);
            List<Long> crossings = new ArrayList<>();
            List<Run> runs = new ArrayList<>();
            for (OrthogonalEdge2i edge : polygon.edges())
            {
                if (edge.isHorizontal())
                {
                    if (edge.start().z() == z) runs.add(new Run(edge.minX(), (long) edge.maxX() + 1));
                }
                else
                {
                    if (edge.minZ() <= z && z < edge.maxZ()) crossings.add((long) edge.start().x());
                    if (edge.minZ() <= z && z <= edge.maxZ())
                        runs.add(new Run(edge.start().x(), (long) edge.start().x() + 1));
                }
            }
            Collections.sort(crossings);
            for (int i = 0; i + 1 < crossings.size(); i += 2)
                runs.add(new Run(crossings.get(i), crossings.get(i + 1) + 1));
            append(bands, z, cuts.get(row + 1), normalize(runs));
        }
        return new CellRegion(bands);
    }

    public static CellRegion rectangle(int minX, int minZ, int maxX, int maxZ)
    {
        return rectangle((long) minX, minZ, (long) maxX + 1, (long) maxZ + 1);
    }

    private static CellRegion rectangle(long x0, long z0, long x1, long z1)
    {
        if (x0 >= x1 || z0 >= z1) return new CellRegion(Collections.emptyList());
        return new CellRegion(Collections.singletonList(new Band(z0, z1, Collections.singletonList(new Run(x0, x1)))));
    }

    public CellRegion union(CellRegion other) { return combine(other, 0); }
    public CellRegion subtract(CellRegion other) { return combine(other, 1); }
    public CellRegion intersect(CellRegion other) { return combine(other, 2); }
    public boolean isEmpty() { return bands.isEmpty(); }
    public boolean containsAll(CellRegion other) { return other.subtract(this).isEmpty(); }
    public boolean intersects(CellRegion other) { return !intersect(other).isEmpty(); }

    public boolean contains(int x, int z)
    {
        int low = 0, high = bands.size() - 1;
        while (low <= high)
        {
            int middle = (low + high) >>> 1;
            Band band = bands.get(middle);
            if (z < band.z0) high = middle - 1;
            else if (z >= band.z1) low = middle + 1;
            else
            {
                for (Run run : band.runs)
                {
                    if (x < run.x0) return false;
                    if (x < run.x1) return true;
                }
                return false;
            }
        }
        return false;
    }

    public OrthogonalPoint2i firstCell()
    {
        if (isEmpty()) throw new IllegalStateException("Empty region has no first cell.");
        return new OrthogonalPoint2i(Math.toIntExact(bands.get(0).runs.get(0).x0), Math.toIntExact(bands.get(0).z0));
    }

    public long cellCount()
    {
        long count = 0;
        for (Band band : bands)
            for (Run run : band.runs)
            {
                try { count = Math.addExact(count, Math.multiplyExact(run.x1 - run.x0, band.z1 - band.z0)); }
                catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
            }
        return count;
    }

    private CellRegion combine(CellRegion other, int operation)
    {
        TreeSet<Long> cuts = new TreeSet<>();
        for (Band band : bands) { cuts.add(band.z0); cuts.add(band.z1); }
        for (Band band : other.bands) { cuts.add(band.z0); cuts.add(band.z1); }
        List<Long> rows = new ArrayList<>(cuts);
        List<Band> result = new ArrayList<>();
        int a = 0, b = 0;
        for (int i = 0; i + 1 < rows.size(); i++)
        {
            long z = rows.get(i);
            while (a < bands.size() && bands.get(a).z1 <= z) a++;
            while (b < other.bands.size() && other.bands.get(b).z1 <= z) b++;
            List<Run> first = a < bands.size() && bands.get(a).z0 <= z ? bands.get(a).runs : Collections.emptyList();
            List<Run> second = b < other.bands.size() && other.bands.get(b).z0 <= z ? other.bands.get(b).runs : Collections.emptyList();
            List<Run> runs;
            if (operation == 0)
            {
                runs = new ArrayList<>(first);
                runs.addAll(second);
                runs = normalize(runs);
            }
            else if (operation == 1) runs = difference(first, second);
            else
            {
                runs = new ArrayList<>();
                int j = 0, k = 0;
                while (j < first.size() && k < second.size())
                {
                    Run x = first.get(j), y = second.get(k);
                    long start = Math.max(x.x0, y.x0), end = Math.min(x.x1, y.x1);
                    if (start < end) runs.add(new Run(start, end));
                    if (x.x1 < y.x1) j++; else k++;
                }
            }
            append(result, z, rows.get(i + 1), runs);
        }
        return new CellRegion(result);
    }

    private static List<Run> normalize(List<Run> runs)
    {
        runs.sort(Comparator.comparingLong(run -> run.x0));
        List<Run> result = new ArrayList<>();
        for (Run run : runs)
        {
            if (!result.isEmpty() && result.get(result.size() - 1).x1 >= run.x0)
            {
                Run last = result.remove(result.size() - 1);
                result.add(new Run(last.x0, Math.max(last.x1, run.x1)));
            }
            else result.add(run);
        }
        return result;
    }

    private static List<Run> difference(List<Run> first, List<Run> second)
    {
        List<Run> result = new ArrayList<>();
        int j = 0;
        for (Run a : first)
        {
            long cursor = a.x0;
            while (j < second.size() && second.get(j).x1 <= cursor) j++;
            for (int k = j; k < second.size() && second.get(k).x0 < a.x1; k++)
            {
                Run b = second.get(k);
                if (b.x0 > cursor) result.add(new Run(cursor, b.x0));
                cursor = Math.max(cursor, b.x1);
                if (cursor >= a.x1) break;
            }
            if (cursor < a.x1) result.add(new Run(cursor, a.x1));
        }
        return result;
    }

    private static void append(List<Band> bands, long z0, long z1, List<Run> runs)
    {
        if (runs.isEmpty()) return;
        if (!bands.isEmpty())
        {
            Band last = bands.get(bands.size() - 1);
            if (last.z1 == z0 && last.runs.equals(runs))
            {
                bands.set(bands.size() - 1, new Band(last.z0, z1, runs));
                return;
            }
        }
        bands.add(new Band(z0, z1, runs));
    }

    private List<List<Rect>> components()
    {
        List<Rect> rects = new ArrayList<>();
        List<Integer> parents = new ArrayList<>();
        int previousStart = 0, previousEnd = 0;
        long previousZ = Long.MIN_VALUE;
        for (Band band : bands)
        {
            int start = rects.size();
            for (Run run : band.runs)
            {
                parents.add(rects.size());
                rects.add(new Rect(run.x0, band.z0, run.x1, band.z1));
            }
            if (previousZ == band.z0)
            {
                int a = previousStart, b = start;
                while (a < previousEnd && b < rects.size())
                {
                    Rect first = rects.get(a), second = rects.get(b);
                    if (first.x0 < second.x1 && second.x0 < first.x1)
                        parents.set(root(parents, b), root(parents, a));
                    if (first.x1 < second.x1) a++; else b++;
                }
            }
            previousStart = start;
            previousEnd = rects.size();
            previousZ = band.z1;
        }
        Map<Integer, List<Rect>> groups = new LinkedHashMap<>();
        for (int i = 0; i < rects.size(); i++)
            groups.computeIfAbsent(root(parents, i), ignored -> new ArrayList<>()).add(rects.get(i));
        return new ArrayList<>(groups.values());
    }

    private static int root(List<Integer> parents, int i)
    {
        while (parents.get(i) != i)
        {
            parents.set(i, parents.get(parents.get(i)));
            i = parents.get(i);
        }
        return i;
    }

    public boolean isConnected() { return components().size() <= 1; }

    /** Bounds of the connected portions reachable from the seed rectangle, without visiting blocks. */
    public int[] connectedBounds(int minX, int minZ, int maxX, int maxZ)
    {
        Rect seed = new Rect(minX, minZ, (long) maxX + 1, (long) maxZ + 1);
        long x0 = Long.MAX_VALUE, z0 = Long.MAX_VALUE, x1 = Long.MIN_VALUE, z1 = Long.MIN_VALUE;
        for (List<Rect> component : components())
        {
            boolean touches = false;
            for (Rect rect : component) if (rect.intersects(seed)) { touches = true; break; }
            if (!touches) continue;
            for (Rect rect : component)
            {
                x0 = Math.min(x0, rect.x0); z0 = Math.min(z0, rect.z0);
                x1 = Math.max(x1, rect.x1); z1 = Math.max(z1, rect.z1);
            }
        }
        return x0 == Long.MAX_VALUE ? null : new int[] {
                Math.toIntExact(x0), Math.toIntExact(z0), Math.toIntExact(x1 - 1), Math.toIntExact(z1 - 1)};
    }

    /** Preserve the existing automatic, three-cell-wide Manhattan connection for disjoint unions. */
    public CellRegion connected()
    {
        CellRegion result = this;
        List<List<Rect>> groups;
        while ((groups = result.components()).size() > 1)
        {
            long distance = Long.MAX_VALUE, ax = 0, az = 0, bx = 0, bz = 0;
            for (int i = 0; i < groups.size(); i++)
                for (int j = i + 1; j < groups.size(); j++)
                    for (Rect a : groups.get(i)) for (Rect b : groups.get(j))
                    {
                        long x = Math.max(a.x0, Math.min(b.x0, a.x1 - 1));
                        long z = Math.max(a.z0, Math.min(b.z0, a.z1 - 1));
                        long tx = Math.max(b.x0, Math.min(x, b.x1 - 1));
                        long tz = Math.max(b.z0, Math.min(z, b.z1 - 1));
                        long d = Math.abs(x - tx) + Math.abs(z - tz);
                        if (d < distance) { distance = d; ax = x; az = z; bx = tx; bz = tz; }
                    }
            long left = Math.min(ax, bx), right = Math.max(ax, bx);
            long top = Math.min(az, bz), bottom = Math.max(az, bz);
            result = result.union(rectangle(left, az - 1, right + 1, az + 2))
                    .union(rectangle(left - 1, az, right + 2, az + 1))
                    .union(rectangle(bx - 1, top, bx + 2, bottom + 1))
                    .union(rectangle(bx, top - 1, bx + 1, bottom + 2));
        }
        return result;
    }

    public OrthogonalPolygon toPolygon()
    {
        Map<Vertex, Vertex> outgoing = new HashMap<>();
        int edgeCount = 0;
        for (int i = 0; i < bands.size(); i++)
        {
            Band band = bands.get(i);
            List<Run> above = i > 0 && bands.get(i - 1).z1 == band.z0 ? bands.get(i - 1).runs : Collections.emptyList();
            List<Run> below = i + 1 < bands.size() && bands.get(i + 1).z0 == band.z1 ? bands.get(i + 1).runs : Collections.emptyList();
            for (Run run : band.runs)
            {
                edge(outgoing, run.x0, band.z1, run.x0, band.z0);
                edge(outgoing, run.x1, band.z0, run.x1, band.z1);
                edgeCount += 2;
            }
            for (Run run : difference(band.runs, above)) { edge(outgoing, run.x0, band.z0, run.x1, band.z0); edgeCount++; }
            for (Run run : difference(band.runs, below)) { edge(outgoing, run.x1, band.z1, run.x0, band.z1); edgeCount++; }
        }
        if (outgoing.isEmpty()) throw new IllegalArgumentException("The resulting claim is empty.");
        Vertex start = Collections.min(outgoing.keySet(), Comparator.comparingLong((Vertex v) -> v.z).thenComparingLong(v -> v.x));
        List<Vertex> contour = new ArrayList<>();
        Vertex current = start;
        do
        {
            contour.add(current);
            current = outgoing.get(current);
            if (current == null || contour.size() > edgeCount) throw new IllegalArgumentException("Merged claim boundary could not be followed.");
        } while (!current.equals(start));
        if (contour.size() != edgeCount) throw new IllegalArgumentException("The resulting claim has a hole or disconnected boundary.");
        List<Vertex> turns = new ArrayList<>();
        for (int i = 0; i < contour.size(); i++)
        {
            Vertex previous = contour.get((i + contour.size() - 1) % contour.size());
            Vertex vertex = contour.get(i), next = contour.get((i + 1) % contour.size());
            if (direction(previous, vertex) != direction(vertex, next)) turns.add(vertex);
        }
        List<OrthogonalPoint2i> corners = new ArrayList<>();
        for (int i = 0; i < turns.size(); i++)
        {
            Vertex vertex = turns.get(i);
            int incoming = (direction(turns.get((i + turns.size() - 1) % turns.size()), vertex) + 1) % 4;
            int next = (direction(vertex, turns.get((i + 1) % turns.size())) + 1) % 4;
            long x = vertex.x - (incoming == 2 || next == 2 ? 1 : 0);
            long z = vertex.z - (incoming == 3 || next == 3 ? 1 : 0);
            OrthogonalPoint2i point = new OrthogonalPoint2i(Math.toIntExact(x), Math.toIntExact(z));
            if (!contains(point.x(), point.z())) throw new IllegalArgumentException("Merged claim boundary could not be followed.");
            if (corners.isEmpty() || !corners.get(corners.size() - 1).equals(point)) corners.add(point);
        }
        boolean changed;
        do
        {
            changed = false;
            for (int i = 0; i < corners.size() && corners.size() >= 4; i++)
            {
                OrthogonalPoint2i a = corners.get((i + corners.size() - 1) % corners.size());
                OrthogonalPoint2i b = corners.get(i), c = corners.get((i + 1) % corners.size());
                if (a.equals(b) || b.equals(c) || (a.x() == b.x() && b.x() == c.x()) || (a.z() == b.z() && b.z() == c.z()))
                { corners.remove(i); changed = true; break; }
            }
        } while (changed);
        if (corners.size() < 4) throw new IllegalArgumentException("The resulting claim is too narrow.");
        corners.add(corners.get(0));
        return OrthogonalPolygon.fromClosedPath(corners);
    }

    private static void edge(Map<Vertex, Vertex> edges, long x0, long z0, long x1, long z1)
    {
        if (edges.put(new Vertex(x0, z0), new Vertex(x1, z1)) != null)
            throw new IllegalArgumentException("The resulting claim has a pinched boundary.");
    }

    private static int direction(Vertex a, Vertex b) { return b.x > a.x ? 0 : b.z > a.z ? 1 : b.x < a.x ? 2 : 3; }

    private static final class Band
    {
        final long z0, z1;
        final List<Run> runs;
        Band(long z0, long z1, List<Run> runs) { this.z0 = z0; this.z1 = z1; this.runs = runs; }
    }

    private static final class Run
    {
        final long x0, x1;
        Run(long x0, long x1) { this.x0 = x0; this.x1 = x1; }
        @Override public boolean equals(Object o) { return o instanceof Run && ((Run) o).x0 == x0 && ((Run) o).x1 == x1; }
        @Override public int hashCode() { return Long.hashCode(x0) * 31 + Long.hashCode(x1); }
    }

    private static final class Rect
    {
        final long x0, z0, x1, z1;
        Rect(long x0, long z0, long x1, long z1) { this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; }
        boolean intersects(Rect b) { return x0 < b.x1 && b.x0 < x1 && z0 < b.z1 && b.z0 < z1; }
    }

    private static final class Vertex
    {
        final long x, z;
        Vertex(long x, long z) { this.x = x; this.z = z; }
        @Override public boolean equals(Object o) { return o instanceof Vertex && ((Vertex) o).x == x && ((Vertex) o).z == z; }
        @Override public int hashCode() { return Long.hashCode(x) * 31 + Long.hashCode(z); }
    }
}
