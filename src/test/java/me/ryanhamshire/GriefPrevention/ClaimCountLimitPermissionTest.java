package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.jupiter.api.Test;

@SuppressWarnings("null")
class ClaimCountLimitPermissionTest {

    @Test
    void playersWithoutALimitNodeUseTheConfiguredLimit() {
        GriefPrevention plugin = plugin(3);
        Player player = player();

        assertEquals(3, plugin.getMaxClaimsFor(player));
    }

    @Test
    void theHighestLimitNodeWins() {
        GriefPrevention plugin = plugin(3);
        Player player = player("griefprevention.maxclaims.5", "griefprevention.maxclaims.10", "griefprevention.maxclaims.2");

        assertEquals(10, plugin.getMaxClaimsFor(player));
    }

    @Test
    void aLimitNodeAppliesEvenWhenTheConfigIsUnlimited() {
        GriefPrevention plugin = plugin(0);
        Player player = player("griefprevention.maxclaims.4");

        assertEquals(4, plugin.getMaxClaimsFor(player));
    }

    @Test
    void negatedWildcardAndNonPositiveNodesAreIgnored() {
        GriefPrevention plugin = plugin(3);
        Player player = mock(Player.class);
        Set<PermissionAttachmentInfo> permissions = new HashSet<>(Arrays.asList(
            new PermissionAttachmentInfo(player, "griefprevention.maxclaims.50", null, false),
            new PermissionAttachmentInfo(player, "griefprevention.maxclaims.*", null, true),
            new PermissionAttachmentInfo(player, "griefprevention.maxclaims.0", null, true),
            new PermissionAttachmentInfo(player, "griefprevention.maxclaims.-4", null, true),
            new PermissionAttachmentInfo(player, "griefprevention.maxclaims.", null, true)
        ));
        when(player.getEffectivePermissions()).thenReturn(permissions);

        assertEquals(3, plugin.getMaxClaimsFor(player));
    }

    @Test
    void claimCountIsComparedAgainstThePermissionLimit() {
        GriefPrevention plugin = plugin(1);
        Player player = player("griefprevention.maxclaims.3");

        assertFalse(plugin.isAtClaimCountLimit(player, playerData(2)));
        assertTrue(plugin.isAtClaimCountLimit(player, playerData(3)));
    }

    @Test
    void overridePermissionBypassesEveryLimit() {
        GriefPrevention plugin = plugin(1);
        Player player = player("griefprevention.maxclaims.2");
        when(player.hasPermission("griefprevention.overrideclaimcountlimit")).thenReturn(true);

        assertFalse(plugin.isAtClaimCountLimit(player, playerData(10)));
    }

    @Test
    void anUnlimitedConfigWithNoNodesNeverLimits() {
        GriefPrevention plugin = plugin(0);
        Player player = player();

        assertFalse(plugin.isAtClaimCountLimit(player, playerData(100)));
    }

    private static GriefPrevention plugin(int configLimit) {
        GriefPrevention plugin = mock(GriefPrevention.class, CALLS_REAL_METHODS);
        plugin.config_claims_maxClaimsPerPlayer = configLimit;
        return plugin;
    }

    private static Player player(String... nodes) {
        Player player = mock(Player.class);
        Set<PermissionAttachmentInfo> permissions = new HashSet<>();
        for (String node : nodes) {
            permissions.add(new PermissionAttachmentInfo(player, node, null, true));
        }
        when(player.getEffectivePermissions()).thenReturn(permissions);
        return player;
    }

    private static PlayerData playerData(int claimCount) {
        Vector<Claim> claims = new Vector<>();
        for (int i = 0; i < claimCount; i++) {
            claims.add(mock(Claim.class));
        }
        PlayerData playerData = mock(PlayerData.class);
        doReturn(claims).when(playerData).getClaims();
        return playerData;
    }
}
