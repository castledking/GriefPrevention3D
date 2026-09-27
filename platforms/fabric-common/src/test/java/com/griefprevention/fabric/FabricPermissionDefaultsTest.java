package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricPermissionDefaultsTest
{
    private static final Function<String, Boolean> UNDEFINED = permission -> null;

    @Test
    void playerCommandsAreGrantedToEveryoneByDefault()
    {
        assertTrue(FabricPermissionDefaults.resolve(FabricPermissionDefaults.TRUST, UNDEFINED, false));
        assertTrue(FabricPermissionDefaults.resolve(FabricPermissionDefaults.MANAGE_TRUST, UNDEFINED, false));
        assertTrue(FabricPermissionDefaults.resolve(FabricPermissionDefaults.CLAIMS_LIST, UNDEFINED, false));
    }

    @Test
    void staffCommandsNeedOperatorByDefault()
    {
        assertFalse(FabricPermissionDefaults.resolve(FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS, UNDEFINED, false));
        assertTrue(FabricPermissionDefaults.resolve(FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS, UNDEFINED, true));
        assertFalse(FabricPermissionDefaults.resolve(FabricPermissionDefaults.ADMIN_CLAIMS, UNDEFINED, false));
        assertTrue(FabricPermissionDefaults.resolve(FabricPermissionDefaults.ADMIN_CLAIMS, UNDEFINED, true));
    }

    @Test
    void unknownNodesAreOperatorOnly()
    {
        assertFalse(FabricPermissionDefaults.resolve("griefprevention.somethingnew", UNDEFINED, false));
        assertTrue(FabricPermissionDefaults.resolve("griefprevention.somethingnew", UNDEFINED, true));
    }

    @Test
    void adminClaimsPermissionSuppliesItsPermissionTrustChild()
    {
        Function<String, Boolean> adminClaims = explicit(Map.of(FabricPermissionDefaults.ADMIN_CLAIMS, true));

        assertTrue(FabricPermissionDefaults.resolve(FabricPermissionDefaults.PERMISSION_TRUST, adminClaims, false));
        assertFalse(FabricPermissionDefaults.resolve(FabricPermissionDefaults.PERMISSION_TRUST, UNDEFINED, false));
        assertTrue(FabricPermissionDefaults.resolve(FabricPermissionDefaults.PERMISSION_TRUST, UNDEFINED, true));
    }

    @Test
    void explicitChildDenialOverridesParentGrantsAndOperatorDefaults()
    {
        Function<String, Boolean> denied = explicit(Map.of(
                FabricPermissionDefaults.PERMISSION_TRUST, false,
                FabricPermissionDefaults.ADMIN_CLAIMS, true));

        assertFalse(FabricPermissionDefaults.resolve(FabricPermissionDefaults.PERMISSION_TRUST, denied, true));
    }

    @Test
    void denyingTheParentTakesItsChildrenAway()
    {
        Function<String, Boolean> noClaims = explicit(Map.of(FabricPermissionDefaults.CLAIMS, false));
        Function<String, Boolean> noAdmin = explicit(Map.of(FabricPermissionDefaults.ADMIN, false));

        assertFalse(FabricPermissionDefaults.resolve(FabricPermissionDefaults.TRUST, noClaims, true));
        assertFalse(FabricPermissionDefaults.resolve(FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS, noAdmin, true));
    }

    @Test
    void grantingTheAdminBundleGrantsStaffCommandsToNonOperators()
    {
        Function<String, Boolean> admin = explicit(Map.of(FabricPermissionDefaults.ADMIN, true));

        assertTrue(FabricPermissionDefaults.resolve(FabricPermissionDefaults.ADJUST_CLAIM_BLOCKS, admin, false));
        assertTrue(FabricPermissionDefaults.resolve(FabricPermissionDefaults.ADMIN_CLAIMS, admin, false));
    }

    private static Function<String, Boolean> explicit(Map<String, Boolean> values)
    {
        return values::get;
    }
}
