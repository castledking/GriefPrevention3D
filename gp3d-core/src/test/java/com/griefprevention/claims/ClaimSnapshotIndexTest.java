package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Arrays;
import java.util.Collections;

class ClaimSnapshotIndexTest
{
    @Test
    void indexesAndFindsById()
    {
        ClaimSnapshotIndex index = new ClaimSnapshotIndex();
        ClaimSnapshot claim = claim(1L, "world", null, false, ClaimBounds.rectangle(0, 0, 0, 10, 255, 10));

        index.put(claim);

        assertSame(claim, index.get(1L));
        assertEquals(Collections.singletonList(claim), index.snapshots());
    }

    @Test
    void replacesClaimAndRemovesOldChunkEntries()
    {
        ClaimSnapshotIndex index = new ClaimSnapshotIndex();
        ClaimSnapshot oldClaim = claim(1L, "world", null, false, ClaimBounds.rectangle(0, 0, 0, 10, 255, 10));
        ClaimSnapshot newClaim = claim(1L, "world", null, false, ClaimBounds.rectangle(100, 0, 100, 110, 255, 110));

        index.put(oldClaim);
        index.put(newClaim);

        assertEquals(Collections.emptyList(), index.candidates("world", ClaimBounds.rectangle(0, 0, 0, 0, 0, 0)));
        assertEquals(Collections.singletonList(newClaim), index.candidates("world", ClaimBounds.rectangle(100, 0, 100, 100, 0, 100)));
    }

    @Test
    void removesClaimFromIndex()
    {
        ClaimSnapshotIndex index = new ClaimSnapshotIndex();
        ClaimSnapshot claim = claim(1L, "world", null, false, ClaimBounds.rectangle(0, 0, 0, 10, 255, 10));

        index.put(claim);

        assertSame(claim, index.remove(1L));
        assertNull(index.get(1L));
        assertEquals(Collections.emptyList(), index.candidates("world", ClaimBounds.rectangle(0, 0, 0, 0, 0, 0)));
    }

    @Test
    void keepsWorldsSeparate()
    {
        ClaimSnapshotIndex index = new ClaimSnapshotIndex();
        ClaimSnapshot overworld = claim(1L, "world", null, false, ClaimBounds.rectangle(0, 0, 0, 10, 255, 10));
        ClaimSnapshot nether = claim(2L, "world_nether", null, false, ClaimBounds.rectangle(0, 0, 0, 10, 255, 10));

        index.put(overworld);
        index.put(nether);

        assertEquals(Collections.singletonList(overworld), index.candidates("world", ClaimBounds.rectangle(0, 0, 0, 0, 0, 0)));
        assertEquals(Collections.singletonList(nether), index.candidates("world_nether", ClaimBounds.rectangle(0, 0, 0, 0, 0, 0)));
    }

    @Test
    void findsMostSpecificClaimAtLocation()
    {
        ClaimSnapshotIndex index = new ClaimSnapshotIndex();
        ClaimSnapshot parent = claim(1L, "world", null, false, ClaimBounds.rectangle(0, -64, 0, 20, 320, 20));
        ClaimSnapshot child2d = claim(2L, "world", 1L, false, ClaimBounds.rectangle(0, 0, 0, 10, 10, 10));
        ClaimSnapshot child3dWide = claim(3L, "world", 1L, true, ClaimBounds.rectangle(0, 0, 0, 10, 30, 10));
        ClaimSnapshot child3dNarrow = claim(4L, "world", 1L, true, ClaimBounds.rectangle(0, 5, 0, 10, 8, 10));

        index.rebuild(Arrays.asList(parent, child2d, child3dWide, child3dNarrow));

        assertSame(child3dNarrow, index.findAt("world", 5, 6, 5, false, false));
        assertSame(child3dWide, index.findAt("world", 5, 20, 5, false, false));
        assertSame(child2d, index.findAt("world", 5, 100, 5, false, false));
    }

    @Test
    void canIgnoreSubclaims()
    {
        ClaimSnapshotIndex index = new ClaimSnapshotIndex();
        ClaimSnapshot parent = claim(1L, "world", null, false, ClaimBounds.rectangle(0, -64, 0, 20, 320, 20));
        ClaimSnapshot child = claim(2L, "world", 1L, false, ClaimBounds.rectangle(0, 0, 0, 10, 10, 10));

        index.rebuild(Arrays.asList(parent, child));

        assertSame(child, index.findAt("world", 5, 70, 5, false, false));
        assertSame(parent, index.findAt("world", 5, 70, 5, false, true));
    }

    @Test
    void rejectsIdlessSnapshots()
    {
        ClaimSnapshotIndex index = new ClaimSnapshotIndex();

        assertThrows(IllegalArgumentException.class, () -> index.put(
                new ClaimSnapshot(null, "world", null, null, ClaimBounds.rectangle(0, 0, 0, 1, 1, 1), false, false)
        ));
    }

    private static ClaimSnapshot claim(long id, String world, Long parentId, boolean threeDimensional, ClaimBounds bounds)
    {
        return new ClaimSnapshot(id, world, null, parentId, bounds, threeDimensional, parentId != null);
    }
}
