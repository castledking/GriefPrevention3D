package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.griefprevention.test.ServerMocks;
import java.util.Collections;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("null")
class ClaimPvpLegacyToggleTest {

    private static final UUID OWNER_ID = UUID.fromString("9d1f3b7a-2c4e-4a6b-8e0d-5f7a9c1b3d2e");

    private static GriefPrevention plugin;

    @BeforeAll
    static void beforeAll() {
        Server server = ServerMocks.newServer();
        doReturn(mock(PluginManager.class)).when(server).getPluginManager();
        Bukkit.setServer(server);

        plugin = mock(GriefPrevention.class, CALLS_REAL_METHODS);
        plugin.config_pvp_toggleCostClaimEnabled = true;
        plugin.config_pvp_toggleCostSubdivisionEnabled = true;
        GriefPrevention.instance = plugin;
    }

    @AfterAll
    static void afterAll() {
        GriefPrevention.instance = null;
        ServerMocks.unsetBukkitServer();
    }

    @Test
    void aStoredDisabledClaimWithoutTheMarkerCountsAsAnExplicitToggle() {
        Claim claim = claim(1L);
        claim.restorePvpToggle(false, false);

        assertFalse(claim.pvpEnabled);
        assertTrue(claim.pvpToggleSet);
    }

    @Test
    void aStoredDefaultClaimWithoutTheMarkerStaysUntoggled() {
        Claim claim = claim(2L);
        claim.restorePvpToggle(true, false);

        assertTrue(claim.pvpEnabled);
        assertFalse(claim.pvpToggleSet);
    }

    @Test
    void theStoredMarkerIsKept() {
        Claim claim = claim(3L);
        claim.restorePvpToggle(true, true);

        assertTrue(claim.pvpEnabled);
        assertTrue(claim.pvpToggleSet);
    }

    @Test
    void legacyDisabledClaimsStayProtectedWhenTheGlobalConfigAllowsCombat() {
        plugin.config_pvp_noCombatInPlayerLandClaims = false;

        Claim claim = claim(4L);
        claim.restorePvpToggle(false, false);

        assertTrue(plugin.claimIsPvPSafeZone(claim));
    }

    @Test
    void legacyDisabledSubdivisionsStayProtectedWhenTheGlobalConfigAllowsCombat() {
        plugin.config_pvp_noCombatInPlayerLandClaims = false;

        Claim parent = claim(5L);
        Claim subdivision = claim(6L);
        subdivision.parent = parent;
        parent.children.add(subdivision);
        subdivision.restorePvpToggle(false, false);

        assertTrue(plugin.claimIsPvPSafeZone(subdivision));
    }

    @Test
    void untoggledClaimsStillFollowTheGlobalConfig() {
        Claim claim = claim(7L);
        claim.restorePvpToggle(true, false);

        plugin.config_pvp_noCombatInPlayerLandClaims = false;
        assertFalse(plugin.claimIsPvPSafeZone(claim));

        plugin.config_pvp_noCombatInPlayerLandClaims = true;
        assertTrue(plugin.claimIsPvPSafeZone(claim));
    }

    private static Claim claim(long id) {
        return new Claim(
            new Location(null, 0, 64, 0),
            new Location(null, 9, 64, 9),
            OWNER_ID,
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            id
        );
    }
}
