package com.griefprevention.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

@SuppressWarnings("null")
class MergeCorridorTest
{
    @Test
    void nibUsesSelectedDepthUntilItReachesWiderParent()
    {
        OrthogonalPolygon claim = claimWithNarrowNib(0, 0);

        MergeCorridor.Nib shallow = MergeCorridor.nib(
                claim,
                0,
                new OrthogonalPoint2i(1, 2));
        MergeCorridor.Nib intoParent = MergeCorridor.nib(
                claim,
                0,
                new OrthogonalPoint2i(1, 5));

        assertEquals(new MergeCorridor.Extent(0, 0, 2, 2), shallow.extent());
        assertEquals(new MergeCorridor.Extent(0, 0, 6, 5), intoParent.extent());
    }

    @Test
    void diagonalShapedClaimsUseDepthWidenedManhattanLegs()
    {
        OrthogonalPolygon first = claimWithNarrowNib(0, 0);
        OrthogonalPolygon second = claimWithNarrowNib(12, 12);

        List<MergeCorridor.Extent> corridor = MergeCorridor.connect(
                first,
                0,
                new OrthogonalPoint2i(1, 5),
                second,
                0,
                new OrthogonalPoint2i(13, 14));

        assertEquals(Arrays.asList(
                new MergeCorridor.Extent(0, 0, 6, 14),
                new MergeCorridor.Extent(0, 12, 14, 14)), corridor);
    }

    private static OrthogonalPolygon claimWithNarrowNib(int offsetX, int offsetZ)
    {
        return OrthogonalPolygon.fromClosedPath(Arrays.asList(
                point(offsetX, offsetZ, 0, 0),
                point(offsetX, offsetZ, 2, 0),
                point(offsetX, offsetZ, 2, 4),
                point(offsetX, offsetZ, 6, 4),
                point(offsetX, offsetZ, 6, 8),
                point(offsetX, offsetZ, 0, 8),
                point(offsetX, offsetZ, 0, 0)));
    }

    private static OrthogonalPoint2i point(int offsetX, int offsetZ, int x, int z)
    {
        return new OrthogonalPoint2i(offsetX + x, offsetZ + z);
    }
}
