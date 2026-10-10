package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.Collections;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

/** The copy constructor must carry both boundary corners over to the copy. */
@SuppressWarnings("null")
class ClaimTest
{
    @Test
    void theCopyKeepsBothCornersAndTheArea()
    {
        Claim claim = new Claim(
            new Location(null, 0, 0, 0),
            new Location(null, 9, 64, 19),
            null,
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            1L);
        Claim copy = new Claim(claim);

        assertNotSame(claim.getLesserBoundaryCorner(), copy.getLesserBoundaryCorner());
        assertNotSame(claim.getGreaterBoundaryCorner(), copy.getGreaterBoundaryCorner());
        assertEquals(claim.getLesserBoundaryCorner(), copy.getLesserBoundaryCorner());
        assertEquals(claim.getGreaterBoundaryCorner(), copy.getGreaterBoundaryCorner());
        assertEquals(claim.getArea(), copy.getArea());
    }
}
