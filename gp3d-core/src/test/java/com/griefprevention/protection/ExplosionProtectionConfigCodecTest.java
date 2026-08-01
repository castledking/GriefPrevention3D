package com.griefprevention.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionProtectionConfigCodecTest
{
    private final ExplosionProtectionConfigCodec codec = new ExplosionProtectionConfigCodec();

    @Test
    void readsOnlyActivatedUpstreamFieldsAndWorldModes() throws Exception
    {
        ExplosionProtectionSettings settings = this.codec.decode(
                "GriefPrevention:\n"
                        + "  BlockLandClaimExplosions: false\n"
                        + "  BlockSurfaceCreeperExplosions: true\n"
                        + "  BlockSurfaceOtherExplosions: false\n"
                        + "  UntouchedAddonSetting: keep\n"
                        + "  Claims:\n"
                        + "    Mode:\n"
                        + "      world: Survival\n"
                        + "      plots: Creative\n"
                        + "      resource: Disabled\n"
        );

        assertFalse(settings.blockLandClaimExplosions());
        assertTrue(settings.blockSurfaceCreeperExplosions());
        assertFalse(settings.blockSurfaceOtherExplosions());
        assertEquals(ExplosionProtectionSettings.ClaimWorldMode.SURVIVAL, settings.worldMode("world"));
        assertEquals(ExplosionProtectionSettings.ClaimWorldMode.CREATIVE, settings.worldMode("plots"));
        assertEquals(ExplosionProtectionSettings.ClaimWorldMode.DISABLED, settings.worldMode("resource"));
        assertEquals(ExplosionProtectionSettings.ClaimWorldMode.SURVIVAL, settings.worldMode("unlisted"));
    }

    @Test
    void usesUpstreamDefaultsWhenFieldsAreAbsent() throws Exception
    {
        ExplosionProtectionSettings settings = this.codec.decode("GriefPrevention:\n  Claims: {}\n");

        assertTrue(settings.blockLandClaimExplosions());
        assertTrue(settings.blockSurfaceCreeperExplosions());
        assertTrue(settings.blockSurfaceOtherExplosions());
    }

    @Test
    void failsClosedForInvalidActiveValuesAndUnsafeTags()
    {
        assertThrows(ExplosionProtectionConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  BlockLandClaimExplosions: definitely\n"
        ));
        assertThrows(ExplosionProtectionConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  Claims:\n    Mode:\n      world: UnknownMode\n"
        ));
        assertThrows(ExplosionProtectionConfigException.class, () -> this.codec.decode(
                "!!com.example.Arbitrary {value: nope}\n"
        ));
    }
}
