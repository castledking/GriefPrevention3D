package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimTrustCommandPermissions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricTrustCommandAuthorizationTest
{
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void adminClaimsPermissionSuppliesItsPermissionTrustChild()
    {
        FabricPermissionResolver permissions = (playerId, permission) ->
                ClaimTrustCommandPermissions.ADMIN_CLAIMS.equals(permission) ? true : null;

        assertTrue(FabricTrustCommandAuthorization.canGrantPermissionTrust(
                PLAYER, permissions, false));
    }

    @Test
    void explicitChildDenialOverridesAdminClaimsAndOperatorDefaults()
    {
        FabricPermissionResolver permissions = (playerId, permission) -> {
            if (ClaimTrustCommandPermissions.PERMISSION_TRUST.equals(permission)) return false;
            if (ClaimTrustCommandPermissions.ADMIN_CLAIMS.equals(permission)) return true;
            return null;
        };

        assertFalse(FabricTrustCommandAuthorization.canGrantPermissionTrust(
                PLAYER, permissions, true));
    }

    @Test
    void operatorDefaultOnlyAppliesWhenTheProviderHasNoRelevantValue()
    {
        FabricPermissionResolver undefined = (playerId, permission) -> null;

        assertTrue(FabricTrustCommandAuthorization.canGrantPermissionTrust(
                PLAYER, undefined, true));
        assertFalse(FabricTrustCommandAuthorization.canGrantPermissionTrust(
                PLAYER, undefined, false));
    }

    @Test
    void manageTrustRemainsDefaultAllowedButHonorsExplicitDenial()
    {
        FabricPermissionResolver undefined = (playerId, permission) -> null;
        FabricPermissionResolver denied = (playerId, permission) -> false;

        assertTrue(FabricTrustCommandAuthorization.canGrantManageTrust(PLAYER, undefined));
        assertFalse(FabricTrustCommandAuthorization.canGrantManageTrust(PLAYER, denied));
    }
}
