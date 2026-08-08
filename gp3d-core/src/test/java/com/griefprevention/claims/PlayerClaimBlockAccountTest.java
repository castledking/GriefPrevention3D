package com.griefprevention.claims;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerClaimBlockAccountTest
{
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void derivesRemainingBlocksFromExactTopLevelOwnedArea()
    {
        ClaimBounds shaped = ClaimBounds.shaped(
                OrthogonalPolygon.fromClosedPath(Arrays.asList(
                        new OrthogonalPoint2i(0, 0),
                        new OrthogonalPoint2i(4, 0),
                        new OrthogonalPoint2i(4, 1),
                        new OrthogonalPoint2i(1, 1),
                        new OrthogonalPoint2i(1, 4),
                        new OrthogonalPoint2i(0, 4),
                        new OrthogonalPoint2i(0, 0)
                )),
                -64,
                320
        );
        ClaimSnapshot topLevel = claim(1L, OWNER, null, shaped, false);
        ClaimSnapshot subdivision = claim(
                2L,
                OWNER,
                1L,
                ClaimBounds.rectangle(0, 70, 0, 3, 90, 3),
                true
        );
        ClaimSnapshot otherOwner = claim(
                3L,
                OTHER,
                null,
                ClaimBounds.rectangle(20, -64, 20, 29, 320, 29),
                false
        );
        ClaimSnapshot admin = claim(
                4L,
                null,
                null,
                ClaimBounds.rectangle(40, -64, 40, 49, 320, 49),
                false
        );

        ClaimBlockBalance balance = new PlayerClaimBlockAccount(OWNER, 100, 25, 10)
                .balance(Arrays.asList(topLevel, subdivision, otherOwner, admin));

        assertEquals(16, shaped.area(), "fixture must differ from its 25-block bounding rectangle");
        assertEquals(135, balance.totalEntitlement());
        assertEquals(16, balance.claimedArea());
        assertEquals(119, balance.remaining());
    }

    @Test
    void matchesBukkitOverflowBehavior()
    {
        ClaimBlockBalance entitlementOverflow = new PlayerClaimBlockAccount(
                OWNER,
                Integer.MAX_VALUE,
                1,
                0
        ).balance(Arrays.<ClaimSnapshot>asList());
        assertEquals(Integer.MAX_VALUE, entitlementOverflow.totalEntitlement());
        assertEquals(Integer.MAX_VALUE, entitlementOverflow.remaining());

        ClaimSnapshot oneBlock = claim(
                1L,
                OWNER,
                null,
                ClaimBounds.rectangle(0, 0, 0, 0, 0, 0),
                false
        );
        ClaimBlockBalance subtractionOverflow = new PlayerClaimBlockAccount(
                OWNER,
                Integer.MIN_VALUE,
                0,
                0
        ).balance(Arrays.asList(oneBlock));
        assertEquals(0, subtractionOverflow.remaining());
    }

    private static ClaimSnapshot claim(
            Long id,
            UUID owner,
            Long parentId,
            ClaimBounds bounds,
            boolean subdivision)
    {
        return new ClaimSnapshot(id, "world", owner, parentId, bounds, false, subdivision);
    }
}
