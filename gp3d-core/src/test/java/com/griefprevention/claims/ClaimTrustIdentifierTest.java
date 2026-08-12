package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimTrustIdentifierTest
{
    @Test
    void commandTargetsUseOneNormalizedCrossPlatformEncoding()
    {
        assertEquals("[gp3d.vip]", ClaimTrustIdentifier.fromPermissionTarget("gp3d.vip"));
        assertEquals("[gp3d.vip]", ClaimTrustIdentifier.fromPermissionTarget("[GP3D.VIP]"));
        assertEquals("[gp3d.vip]", ClaimTrustIdentifier.fromPermissionTarget("[ gp3d.vip ]"));
        assertNull(ClaimTrustIdentifier.fromPermissionTarget("offlinePlayer"));
        assertNull(ClaimTrustIdentifier.fromPermissionTarget("[broken"));
        assertNull(ClaimTrustIdentifier.fromPermissionTarget("[]"));
    }

    @Test
    void storedPermissionIdentifiersSupportLevelSpecificDenySuffixes()
    {
        assertEquals("gp3d.vip", ClaimTrustIdentifier.permissionNode("[GP3D.VIP]"));
        assertEquals("gp3d.vip", ClaimTrustIdentifier.permissionNode("[gp3d.vip]#inventory"));
        assertNull(ClaimTrustIdentifier.permissionNode("player-identifier"));
        assertNull(ClaimTrustIdentifier.permissionNode("[gp3d.vip]#unknown"));
    }

    @Test
    void blankNodesCannotBeEncoded()
    {
        assertThrows(IllegalArgumentException.class, () -> ClaimTrustIdentifier.forPermission("  "));
    }
}
