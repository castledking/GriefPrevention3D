package com.griefprevention.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPlaceClimbConfigTest
{
    @Test
    void correctionIsOptInAndReadsBothBooleanValues() throws Exception
    {
        WorldProtectionConfigCodec codec = new WorldProtectionConfigCodec();
        assertFalse(codec.decodePreventBlockPlaceClimbing(""));
        assertFalse(codec.decodePreventBlockPlaceClimbing("GriefPrevention:\n  Claims: {}\n"));
        assertTrue(codec.decodePreventBlockPlaceClimbing("GriefPrevention:\n  Claims:\n    PreventBlockPlaceClimbing: true\n"));
        assertFalse(codec.decodePreventBlockPlaceClimbing("GriefPrevention:\n  Claims:\n    PreventBlockPlaceClimbing: false\n"));
        assertThrows(WorldProtectionConfigException.class, () -> codec.decodePreventBlockPlaceClimbing(
                "GriefPrevention:\n  Claims:\n    PreventBlockPlaceClimbing: maybe\n"));
    }
}
