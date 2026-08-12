package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimBlockConfigCodecTest
{
    private final ClaimBlockConfigCodec codec = new ClaimBlockConfigCodec();

    @Test
    void readsMutationSettingsFromTheUpstreamPaths() throws Exception
    {
        ClaimBlockSettings settings = this.codec.decode(
                "GriefPrevention:\n"
                        + "  Claims:\n"
                        + "    InitialBlocks: 450\n"
                        + "    BlocksAccruedPerHour: 25\n"
                        + "    Claim Blocks Accrued Per Hour:\n"
                        + "      Default: 120\n"
                        + "    MaxAccruedBlocks: 70000\n"
                        + "    Max Accrued Claim Blocks:\n"
                        + "      Default: 90000\n"
                        + "    AccruedIdleThreshold: 2\n"
                        + "    Accrued Idle Threshold: 5\n"
                        + "    AccruedIdlePercent: 40\n"
                        + "    MaximumNumberOfClaimsPerPlayer: 8\n"
                        + "    AbandonReturnRatio: 0.75\n"
        );

        assertEquals(450, settings.initialBlocks());
        assertEquals(120, settings.blocksAccruedPerHour());
        assertEquals(90000, settings.maximumAccruedClaimBlocks());
        assertEquals(5, settings.accruedIdleThreshold());
        assertEquals(40, settings.accruedIdlePercent());
        assertEquals(8, settings.maximumClaimsPerPlayer());
        assertEquals(0.75D, settings.abandonReturnRatio());
    }

    @Test
    void usesTheUpstreamDefaultWhenTheOptionIsMissing() throws Exception
    {
        assertEquals(100, this.codec.decode("GriefPrevention: {}\n").initialBlocks());
        ClaimBlockSettings empty = this.codec.decode("");
        assertEquals(100, empty.initialBlocks());
        assertEquals(100, empty.blocksAccruedPerHour());
        assertEquals(80000, empty.maximumAccruedClaimBlocks());
        assertEquals(0, empty.accruedIdleThreshold());
        assertEquals(0, empty.accruedIdlePercent());
        assertEquals(0, empty.maximumClaimsPerPlayer());
        assertEquals(1.0D, empty.abandonReturnRatio());
    }

    @Test
    void rejectsMalformedValuesAndDuplicateYamlKeys()
    {
        assertThrows(ClaimBlockConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  Claims:\n    InitialBlocks: lots\n"
        ));
        assertThrows(ClaimBlockConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  Claims:\n    InitialBlocks: 100\n    InitialBlocks: 200\n"
        ));
        assertThrows(ClaimBlockConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  Claims:\n    MaximumNumberOfClaimsPerPlayer: many\n"
        ));
        assertThrows(ClaimBlockConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  Claims:\n    AbandonReturnRatio: most\n"
        ));
    }

    @Test
    void supportsLegacyAccrualKeysAndClampsOnlyTheIdlePercentageFloor() throws Exception
    {
        ClaimBlockSettings settings = this.codec.decode(
                "GriefPrevention:\n"
                        + "  Claims:\n"
                        + "    BlocksAccruedPerHour: 42\n"
                        + "    MaxAccruedBlocks: 1234\n"
                        + "    AccruedIdleThreshold: -7\n"
                        + "    AccruedIdlePercent: -25\n"
        );

        assertEquals(42, settings.blocksAccruedPerHour());
        assertEquals(1234, settings.maximumAccruedClaimBlocks());
        assertEquals(-7, settings.accruedIdleThreshold());
        assertEquals(0, settings.accruedIdlePercent());
    }
}
