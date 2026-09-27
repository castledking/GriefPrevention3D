package com.griefprevention.protection;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionPolicyTest
{
    private static final String WORLD = "world";
    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    // Claim A1 (0..99) holds subdivisions A1_1 and A1_2; A2 is a second claim of player A; B is player B's.
    private final ClaimSnapshot a1 = claim(1, PLAYER_A, null, 0, 99);
    private final ClaimSnapshot a1Sub1 = claim(11, null, 1L, 10, 19);
    private final ClaimSnapshot a1Sub2 = claim(12, null, 1L, 30, 39);
    private final ClaimSnapshot a2 = claim(2, PLAYER_A, null, 200, 299);
    private final ClaimSnapshot b = claim(3, PLAYER_B, null, 400, 499);
    private final ClaimSnapshot admin = claim(4, null, null, 600, 699);
    private final ClaimSnapshot adminSub = claim(41, null, 4L, 610, 619);
    private final Lookup claims = new Lookup(a1, a1Sub1, a1Sub2, a2, b, admin, adminSub);

    @Test
    void fluidsFollowPapersFlowMatrix()
    {
        ClaimSnapshot[] cells = {null, a1, a1Sub1, a1Sub2, a2, b};
        // Rows: from; columns: to (wild, A1, A1_1, A1_2, A2, B), copied from Paper's documented matrix.
        boolean[][] expected = {
                {true, false, false, false, false, false},
                {true, true, true, true, true, false},
                {true, false, true, false, false, false},
                {true, false, false, true, false, false},
                {true, true, false, false, true, false},
                {true, false, false, false, false, true},
        };
        for (int from = 0; from < cells.length; from++)
        {
            for (int to = 0; to < cells.length; to++)
            {
                assertEquals(
                        expected[from][to],
                        FluidFlowPolicy.mayFlow(cells[from], cells[to], false, this.claims::claimById),
                        "from " + name(cells[from]) + " to " + name(cells[to]));
            }
        }
    }

    @Test
    void restrictedSubdivisionsTakeNoFluidFromTheirParent()
    {
        assertFalse(FluidFlowPolicy.mayFlow(this.a1, this.a1Sub1, true, this.claims::claimById));
        assertTrue(FluidFlowPolicy.mayFlow(this.a1Sub1, this.a1Sub1, true, this.claims::claimById));
    }

    @Test
    void fireNeitherSpreadsNorBurnsByDefault()
    {
        WorldProtectionSettings defaults = WorldProtectionSettings.upstreamDefaults();

        assertFalse(FirePolicy.maySpread(defaults, null, null, this.claims::claimById));
        assertFalse(FirePolicy.mayBurn(defaults, null, null, this.claims::claimById));
    }

    @Test
    void fireCrossesNoOwnerBoundaryEvenWhenEnabled()
    {
        WorldProtectionSettings wild = fire(true, true, false, false);
        assertTrue(FirePolicy.maySpread(wild, null, null, this.claims::claimById));
        assertTrue(FirePolicy.mayBurn(wild, null, null, this.claims::claimById));
        assertFalse(FirePolicy.maySpread(wild, this.a1, this.a1, this.claims::claimById));
        assertFalse(FirePolicy.mayBurn(wild, this.a1, this.a1, this.claims::claimById));

        WorldProtectionSettings inClaims = fire(true, true, true, true);
        assertTrue(FirePolicy.maySpread(inClaims, this.a1, this.a2, this.claims::claimById));
        assertTrue(FirePolicy.mayBurn(inClaims, this.a1Sub1, this.a1, this.claims::claimById));
        assertFalse(FirePolicy.maySpread(inClaims, null, this.a1, this.claims::claimById));
        assertFalse(FirePolicy.maySpread(inClaims, this.b, this.a1, this.claims::claimById));
        assertFalse(FirePolicy.mayBurn(inClaims, this.a1, null, this.claims::claimById));
        // Paper lets fire spread onward in the wilderness wherever it started.
        assertTrue(FirePolicy.maySpread(inClaims, this.a1, null, this.claims::claimById));
    }

    @Test
    void claimsOnlyPistonsWorkOnlyWithinTheirOwnClaim()
    {
        List<BlockPoint> inside = Arrays.asList(new BlockPoint(50, 64, 50), new BlockPoint(51, 64, 50));
        List<BlockPoint> crossing = Arrays.asList(new BlockPoint(99, 64, 50), new BlockPoint(100, 64, 50));

        assertTrue(PistonMovementPolicy.mayMove(PistonMode.CLAIMS_ONLY, WORLD, this.a1, inside, null, this.claims));
        assertFalse(PistonMovementPolicy.mayMove(PistonMode.CLAIMS_ONLY, WORLD, this.a1, crossing, null, this.claims));
        assertFalse(PistonMovementPolicy.mayMove(
                PistonMode.CLAIMS_ONLY, WORLD, null, Collections.singletonList(new BlockPoint(150, 64, 50)), null, this.claims));
        assertTrue(PistonMovementPolicy.mayMove(PistonMode.IGNORED, WORLD, null, crossing, null, this.claims));
    }

    @Test
    void everywherePistonsMayNotEnterAnotherClaimTree()
    {
        List<BlockPoint> intoA1 = Arrays.asList(new BlockPoint(-1, 64, 50), new BlockPoint(0, 64, 50));
        List<BlockPoint> intoWilderness = Arrays.asList(new BlockPoint(150, 64, 50), new BlockPoint(151, 64, 50));
        List<BlockPoint> intoOwnSubdivision = Arrays.asList(new BlockPoint(9, 64, 12), new BlockPoint(10, 64, 12));

        assertFalse(PistonMovementPolicy.mayMove(PistonMode.EVERYWHERE, WORLD, null, intoA1, null, this.claims));
        assertTrue(PistonMovementPolicy.mayMove(PistonMode.EVERYWHERE, WORLD, null, intoWilderness, null, this.claims));
        assertTrue(PistonMovementPolicy.mayMove(PistonMode.EVERYWHERE, WORLD, this.a1, intoOwnSubdivision, null, this.claims));
        assertFalse(PistonMovementPolicy.mayMove(PistonMode.EVERYWHERE, WORLD, this.a2, intoA1, null, this.claims));
    }

    @Test
    void anEmptyPushOnlyChecksWhereTheHeadGoes()
    {
        List<BlockPoint> none = new ArrayList<>();

        assertTrue(PistonMovementPolicy.mayMove(PistonMode.EVERYWHERE, WORLD, null, none, null, this.claims));
        assertFalse(PistonMovementPolicy.mayMove(PistonMode.EVERYWHERE, WORLD, null, none, new BlockPoint(0, 64, 0), this.claims));
        assertTrue(PistonMovementPolicy.mayMove(PistonMode.CLAIMS_ONLY, WORLD, this.a1, none, new BlockPoint(15, 64, 15), this.claims));
    }

    @Test
    void claimsProtectPlayersByDefaultAndToggledClaimsDecideForThemselves()
    {
        WorldProtectionSettings defaults = WorldProtectionSettings.upstreamDefaults();
        assertTrue(PvpProtectionPolicy.isSafeZone(defaults, this.a1, true, false, this.claims::claimById));
        assertTrue(PvpProtectionPolicy.isSafeZone(defaults, this.admin, true, false, this.claims::claimById));
        // Without the toggle feature, /claimpvp settings are ignored, as on Paper.
        assertTrue(PvpProtectionPolicy.isSafeZone(defaults, this.a1, true, true, this.claims::claimById));

        WorldProtectionSettings toggles = toggles(true, false);
        assertFalse(PvpProtectionPolicy.isSafeZone(toggles, this.a1, true, true, this.claims::claimById));
        assertTrue(PvpProtectionPolicy.isSafeZone(toggles, this.a1, false, false, this.claims::claimById));
        // Never toggled: fall back to the global rule.
        assertTrue(PvpProtectionPolicy.isSafeZone(toggles, this.a1, true, false, this.claims::claimById));
        // The subdivision toggle is separate.
        assertTrue(PvpProtectionPolicy.isSafeZone(toggles, this.adminSub, true, true, this.claims::claimById));
    }

    private static WorldProtectionSettings fire(
            boolean spreads,
            boolean destroys,
            boolean spreadsInClaims,
            boolean damagesInClaims)
    {
        return new WorldProtectionSettings(spreads, destroys, spreadsInClaims, damagesInClaims,
                PistonMode.CLAIMS_ONLY, false, false, true, true, true, true, false, false,
                Collections.emptyMap());
    }

    private static WorldProtectionSettings toggles(boolean claims, boolean subdivisions)
    {
        return new WorldProtectionSettings(false, false, false, false,
                PistonMode.CLAIMS_ONLY, false, false, true, true, true, true, claims, subdivisions,
                Collections.emptyMap());
    }

    private static ClaimSnapshot claim(long id, UUID owner, Long parent, int min, int max)
    {
        return new ClaimSnapshot(id, WORLD, owner, parent,
                ClaimBounds.rectangle(min, -64, min, max, 319, max), false, parent != null);
    }

    private static String name(ClaimSnapshot claim)
    {
        return claim == null ? "wilderness" : "#" + claim.id();
    }

    private static final class Lookup implements ClaimLookup
    {
        private final List<ClaimSnapshot> claims;

        private Lookup(ClaimSnapshot... claims)
        {
            this.claims = Arrays.asList(claims);
        }

        @Override
        public ClaimSnapshot claimAt(int x, int y, int z)
        {
            ClaimSnapshot best = null;
            for (ClaimSnapshot claim : this.claims)
            {
                if (claim.contains(WORLD, x, y, z, false)
                        && (best == null || claim.bounds().area() < best.bounds().area()))
                {
                    best = claim;
                }
            }
            return best;
        }

        @Override
        public ClaimSnapshot claimById(long id)
        {
            for (ClaimSnapshot claim : this.claims)
            {
                if (claim.id() == id)
                {
                    return claim;
                }
            }
            return null;
        }
    }
}
