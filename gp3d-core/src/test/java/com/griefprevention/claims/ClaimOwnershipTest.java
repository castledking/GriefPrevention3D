package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClaimOwnershipTest
{
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private final Map<Long, ClaimSnapshot> claims = new HashMap<>();

    @Test
    void returnsTheClaimsOwnClaimOwner()
    {
        ClaimSnapshot claim = claim(1L, OWNER, null);

        assertEquals(OWNER, ClaimOwnership.effectiveOwnerId(claim, this::lookup));
    }

    @Test
    void inheritsTheOwnerFromAnOwnerlessSubdivision()
    {
        put(claim(1L, OWNER, null));
        ClaimSnapshot subdivision = claim(2L, null, 1L);

        assertEquals(OWNER, ClaimOwnership.effectiveOwnerId(subdivision, this::lookup));
    }

    @Test
    void inheritsThroughNestedSubdivisions()
    {
        put(claim(1L, OWNER, null));
        put(claim(2L, null, 1L));
        ClaimSnapshot nested = claim(3L, null, 2L);

        assertEquals(OWNER, ClaimOwnership.effectiveOwnerId(nested, this::lookup));
    }

    @Test
    void adminClaimHasNoOwner()
    {
        assertNull(ClaimOwnership.effectiveOwnerId(claim(1L, null, null), this::lookup));
    }

    @Test
    void missingParentHasNoOwner()
    {
        assertNull(ClaimOwnership.effectiveOwnerId(claim(2L, null, 1L), this::lookup));
    }

    @Test
    void cyclicParentChainTerminates()
    {
        put(claim(1L, null, 2L));
        put(claim(2L, null, 1L));

        assertNull(ClaimOwnership.effectiveOwnerId(claims.get(1L), this::lookup));
    }

    private void put(ClaimSnapshot claim)
    {
        this.claims.put(claim.id(), claim);
    }

    private ClaimSnapshot lookup(long id)
    {
        return this.claims.get(id);
    }

    private static ClaimSnapshot claim(long id, UUID ownerId, Long parentId)
    {
        return new ClaimSnapshot(
                id,
                "world",
                ownerId,
                parentId,
                ClaimBounds.rectangle(0, 0, 0, 15, 255, 15),
                false,
                parentId != null
        );
    }
}
