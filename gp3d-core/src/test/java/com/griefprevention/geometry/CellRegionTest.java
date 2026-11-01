package com.griefprevention.geometry;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CellRegionTest
{
    @Test
    void compressedCoverageMatchesInclusiveBlockChecks()
    {
        Random random = new Random(1684);
        for (int attempt = 0; attempt < 120; attempt++)
        {
            int x = random.nextInt(20) - 10, z = random.nextInt(20) - 10;
            OrthogonalPolygon polygon = elbow(x, z, 3 + random.nextInt(10));
            CellRegion region = CellRegion.of(polygon);
            long count = 0;
            for (int px = -12; px < 24; px++) for (int pz = -12; pz < 24; pz++)
            {
                boolean expected = polygon.containsCell(px, pz);
                assertEquals(expected, region.contains(px, pz), px + "," + pz + " " + polygon.corners());
                if (expected) count++;
            }
            assertEquals(count, region.cellCount());
        }
    }

    @Test
    void booleanOperationsMatchCellSetsAndOldContourTracing()
    {
        Random random = new Random(1904);
        for (int attempt = 0; attempt < 150; attempt++)
        {
            OrthogonalPolygon a = elbow(random.nextInt(10) - 5, random.nextInt(10) - 5, 8);
            OrthogonalPolygon b = OrthogonalPolygon.fromRectangle(-3, -2, 5 + random.nextInt(5), 7);
            Set<OrthogonalPoint2i> first = cells(a), second = cells(b);
            Set<OrthogonalPoint2i> union = new HashSet<>(first); union.addAll(second);
            Set<OrthogonalPoint2i> subtraction = new HashSet<>(first); subtraction.removeAll(second);
            Set<OrthogonalPoint2i> intersection = new HashSet<>(first); intersection.retainAll(second);
            CellRegion x = CellRegion.of(a), y = CellRegion.of(b);
            assertCoverage(union, x.union(y));
            assertCoverage(subtraction, x.subtract(y));
            assertCoverage(intersection, x.intersect(y));
            assertEquals(first.containsAll(second), x.containsAll(y));
            assertEquals(!intersection.isEmpty(), x.intersects(y));
            // Old tracing is the oracle for the supported boundary representation, including
            // its rejection of holes/pinches and simplification of one-block-wide features.
            OrthogonalPolygon old;
            try { old = OrthogonalPolygon.fromOccupiedPoints(union); }
            catch (IllegalArgumentException invalid) { assertThrows(IllegalArgumentException.class, () -> x.union(y).toPolygon()); continue; }
            assertEquals(cells(old), cells(x.union(y).toPolygon()));
        }
    }

    @Test
    void connectedComponentsAndLongBridgesDoNotWalkEveryCell()
    {
        CellRegion first = CellRegion.rectangle(0, 0, 100_000, 100_000);
        CellRegion second = CellRegion.rectangle(200_000, 0, 210_000, 100_000);
        CellRegion union = first.union(second);
        assertFalse(union.isConnected());
        CellRegion connected = union.connected();
        assertTrue(connected.isConnected());
        assertTrue(connected.containsAll(first));
        assertTrue(connected.containsAll(second));
        assertTrue(connected.contains(150_000, 0));
        assertFalse(connected.contains(150_000, 50_000));
        assertDoesNotThrow(connected::toPolygon);
    }

    @Test
    void coordinateExtremesAndSingleColumnRectanglesRemainExact()
    {
        CellRegion max = CellRegion.of(OrthogonalPolygon.fromRectangle(Integer.MAX_VALUE - 10, -10, Integer.MAX_VALUE, 10));
        assertEquals(231, max.cellCount());
        assertTrue(max.contains(Integer.MAX_VALUE, 0));
        assertEquals(231, CellRegion.of(max.toPolygon()).cellCount());
        assertEquals(21, CellRegion.rectangle(Integer.MAX_VALUE, -10, Integer.MAX_VALUE, 10).cellCount());
    }

    @Test
    void largeCoastalClaimCreationExtensionAndCutsStayBounded() throws Exception
    {
        List<OrthogonalPoint2i> path = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/geometry/large-coastal-claim.txt"), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] coordinates = line.split(",");
                path.add(new OrthogonalPoint2i(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1])));
            }
        }
        if (!path.get(0).equals(path.get(path.size() - 1))) path.add(path.get(0));
        // This real 427-corner coastline covers over 1.5 million columns. The former
        // union implementation exhausts a 192 MiB heap on this rectangle extension.
        assertTimeout(Duration.ofSeconds(2), () ->
        {
            OrthogonalPolygon coast = OrthogonalPolygon.fromClosedPath(path);
            assertEquals(1_562_059, coast.cellCount());
            assertEquals(coast.cellCount(), coast.cellRegion().cellCount());
            OrthogonalPolygon rectangle = OrthogonalPolygon.fromRectangle(600, -8800, 1484, -7800);
            OrthogonalPolygon merged = OrthogonalPolygon.union(rectangle, coast);
            assertTrue(merged.cellRegion().containsAll(rectangle.cellRegion()));
            assertTrue(merged.cellRegion().containsAll(coast.cellRegion()));
            assertTrue(merged.cellRegion().cellCount() < 4_000_000); // The ocean outside the outline stays unclaimed.
            assertEquals(rectangle.cellCount(), merged.cellRegion().intersect(rectangle.cellRegion()).cellCount());
            CellRegion cut = merged.cellRegion().subtract(rectangle.cellRegion());
            assertFalse(cut.intersects(rectangle.cellRegion()));
            assertEquals(merged.cellRegion().cellCount() - rectangle.cellCount(), cut.cellCount());
        });
    }

    @Test
    void detailedCoastlineCoverageMatchesPointQueriesAtCornersAndRandomPositions() throws Exception
    {
        List<OrthogonalPoint2i> path = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/geometry/large-coastal-claim.txt"), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] parts = line.split(",");
                path.add(new OrthogonalPoint2i(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            }
        }
        path.add(path.get(0));
        OrthogonalPolygon coast = OrthogonalPolygon.fromClosedPath(path);
        CellRegion ranges = CellRegion.of(coast);
        for (OrthogonalPoint2i point : path)
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                assertEquals(coast.containsCell(point.x() + dx, point.z() + dz), ranges.contains(point.x() + dx, point.z() + dz));
        Random random = new Random(1702);
        for (int i = 0; i < 10_000; i++)
        {
            int x = 1100 + random.nextInt(1620), z = -9250 + random.nextInt(2250);
            assertEquals(coast.containsCell(x, z), ranges.contains(x, z));
        }
    }

    private static void assertCoverage(Set<OrthogonalPoint2i> expected, CellRegion actual)
    {
        assertEquals(expected.size(), actual.cellCount());
        for (int x = -10; x < 20; x++) for (int z = -10; z < 20; z++)
            assertEquals(expected.contains(new OrthogonalPoint2i(x, z)), actual.contains(x, z), x + "," + z);
    }

    private static Set<OrthogonalPoint2i> cells(OrthogonalPolygon polygon)
    {
        Set<OrthogonalPoint2i> result = new HashSet<>();
        for (int x = polygon.minX(); x <= polygon.maxX(); x++) for (int z = polygon.minZ(); z <= polygon.maxZ(); z++)
            if (polygon.containsCell(x, z)) result.add(new OrthogonalPoint2i(x, z));
        return result;
    }

    private static OrthogonalPolygon elbow(int x, int z, int size)
    {
        List<OrthogonalPoint2i> path = Arrays.asList(new OrthogonalPoint2i(x,z), new OrthogonalPoint2i(x+size,z),
                new OrthogonalPoint2i(x+size,z+2), new OrthogonalPoint2i(x+2,z+2),
                new OrthogonalPoint2i(x+2,z+size), new OrthogonalPoint2i(x,z+size), new OrthogonalPoint2i(x,z));
        return OrthogonalPolygon.fromClosedPath(path);
    }
}
