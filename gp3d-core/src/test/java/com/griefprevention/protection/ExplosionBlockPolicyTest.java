package com.griefprevention.protection;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustSnapshot;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.persistence.ClaimDocument;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionBlockPolicyTest
{
    @Test
    void appliesIndependentGeneralAndWitherClaimFlags()
    {
        ExplosionProtectionSettings settings = ExplosionProtectionSettings.upstreamDefaults();

        assertTrue(mayDamage(settings, ExplosionSourceType.OTHER, claim(true, false)));
        assertFalse(mayDamage(settings, ExplosionSourceType.WITHER, claim(true, false)));
        assertFalse(mayDamage(settings, ExplosionSourceType.CREEPER, claim(false, true)));
        assertTrue(mayDamage(settings, ExplosionSourceType.WITHER, claim(false, true)));
    }

    @Test
    void globalClaimSettingOverridesPerClaimProtection()
    {
        ExplosionProtectionSettings settings = new ExplosionProtectionSettings(
                false,
                true,
                true,
                Collections.<String, ExplosionProtectionSettings.ClaimWorldMode>emptyMap()
        );

        assertTrue(mayDamage(settings, ExplosionSourceType.CREEPER, claim(false, false)));
        assertTrue(mayDamage(settings, ExplosionSourceType.WITHER, claim(false, false)));
    }

    @Test
    void matchesUpstreamSurfaceThresholdAndEnvironmentRules()
    {
        ExplosionProtectionSettings settings = ExplosionProtectionSettings.upstreamDefaults();

        assertFalse(ExplosionBlockPolicy.mayDamageBlock(
                settings, "world", ExplosionSourceType.CREEPER, true, 63, 56, null));
        assertTrue(ExplosionBlockPolicy.mayDamageBlock(
                settings, "world", ExplosionSourceType.CREEPER, true, 63, 55, null));
        assertFalse(ExplosionBlockPolicy.mayDamageBlock(
                settings, "world", ExplosionSourceType.OTHER, true, 63, 100, null));
        assertTrue(ExplosionBlockPolicy.mayDamageBlock(
                settings, "world_nether", ExplosionSourceType.OTHER, false, 32, 100, null));
    }

    @Test
    void claimWorldModesMatchCreativeAndDisabledRules()
    {
        Map<String, ExplosionProtectionSettings.ClaimWorldMode> modes = new LinkedHashMap<>();
        modes.put("creative", ExplosionProtectionSettings.ClaimWorldMode.CREATIVE);
        modes.put("disabled", ExplosionProtectionSettings.ClaimWorldMode.DISABLED);
        ExplosionProtectionSettings settings = new ExplosionProtectionSettings(true, true, true, modes);

        assertFalse(ExplosionBlockPolicy.mayDamageBlock(
                settings, "creative", ExplosionSourceType.OTHER, true, 63, -60, null));
        assertTrue(ExplosionBlockPolicy.mayDamageBlock(
                settings, "disabled", ExplosionSourceType.WITHER, true, 63, 100, claim(false, false)));
    }

    private static boolean mayDamage(
            ExplosionProtectionSettings settings,
            ExplosionSourceType type,
            ClaimDocument claim)
    {
        return ExplosionBlockPolicy.mayDamageBlock(settings, "world", type, true, 63, 64, claim);
    }

    private static ClaimDocument claim(boolean generalExplosions, boolean witherExplosions)
    {
        ClaimSnapshot snapshot = new ClaimSnapshot(
                1L,
                "world",
                null,
                null,
                ClaimBounds.rectangle(0, -64, 0, 10, 320, 10),
                false,
                false
        );
        return new ClaimDocument(
                snapshot,
                ClaimTrustSnapshot.empty(null),
                Collections.<OrthogonalPoint2i>emptyList(),
                false,
                false,
                generalExplosions,
                witherExplosions,
                true,
                true,
                1L,
                "1",
                Collections.<String, Object>emptyMap()
        );
    }
}
