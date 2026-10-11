package com.griefprevention.protection;

import com.griefprevention.claims.ClaimTrustLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockUseSettingsTest
{
    private final WorldProtectionConfigCodec codec = new WorldProtectionConfigCodec();

    @Test
    void papersDefaultsLeaveDoorsAndTrapdoorsOpen() throws Exception
    {
        BlockUseSettings settings = this.codec.decodeBlockUse("GriefPrevention: {}\n");

        assertEquals(ClaimTrustLevel.CONTAINER, settings.requiredTrust(BlockUseKind.CONTAINER));
        assertNull(settings.requiredTrust(BlockUseKind.DOOR));
        assertNull(settings.requiredTrust(BlockUseKind.TRAPDOOR));
        assertEquals(ClaimTrustLevel.ACCESS, settings.requiredTrust(BlockUseKind.FENCE_GATE));
        assertEquals(ClaimTrustLevel.ACCESS, settings.requiredTrust(BlockUseKind.BED));
        assertEquals(ClaimTrustLevel.ACCESS, settings.requiredTrust(BlockUseKind.SWITCH));
        assertEquals(ClaimTrustLevel.BUILD, settings.requiredTrust(BlockUseKind.BUILD));
        assertNull(settings.requiredTrust(BlockUseKind.UNPROTECTED));
    }

    @Test
    void readingALecternTakesAccessTrustButItsBookTakesContainerTrust() throws Exception
    {
        BlockUseSettings settings = this.codec.decodeBlockUse("GriefPrevention: {}\n");

        assertEquals(ClaimTrustLevel.ACCESS, settings.requiredTrust(BlockUseKind.LECTERN));
        assertEquals(ClaimTrustLevel.CONTAINER, settings.requiredTrust(BlockUseKind.LECTERN_BOOK));
    }

    @Test
    void lecternsCanBeLeftOpenToReadersButNeverToThieves() throws Exception
    {
        BlockUseSettings settings = this.codec.decodeBlockUse(
                "GriefPrevention:\n"
                        + "  Claims:\n"
                        + "    LecternReadingRequiresAccessTrust: false\n"
                        + "    PreventTheft: false\n");

        assertNull(settings.requiredTrust(BlockUseKind.LECTERN));
        assertEquals(ClaimTrustLevel.CONTAINER, settings.requiredTrust(BlockUseKind.LECTERN_BOOK));
        assertNull(settings.requiredTrust(BlockUseKind.CONTAINER));
    }

    @Test
    void readsPapersLockSwitches() throws Exception
    {
        BlockUseSettings settings = this.codec.decodeBlockUse(
                "GriefPrevention:\n"
                        + "  Claims:\n"
                        + "    LockWoodenDoors: true\n"
                        + "    LockTrapDoors: true\n"
                        + "    LockFenceGates: false\n"
                        + "    PreventButtonsSwitches: false\n");

        assertEquals(ClaimTrustLevel.ACCESS, settings.requiredTrust(BlockUseKind.DOOR));
        assertEquals(ClaimTrustLevel.ACCESS, settings.requiredTrust(BlockUseKind.TRAPDOOR));
        assertNull(settings.requiredTrust(BlockUseKind.FENCE_GATE));
        assertNull(settings.requiredTrust(BlockUseKind.BED));
        assertNull(settings.requiredTrust(BlockUseKind.SWITCH));
    }

    @Test
    void anEmptyConfigUsesPapersDefaults() throws Exception
    {
        BlockUseSettings settings = this.codec.decodeBlockUse("");

        assertEquals(ClaimTrustLevel.ACCESS, settings.requiredTrust(BlockUseKind.LECTERN));
        assertNull(settings.requiredTrust(BlockUseKind.DOOR));
    }

    @Test
    void enderPearlsNeedAccessTrustAndComeBackByDefault() throws Exception
    {
        EnderPearlSettings defaults = this.codec.decodeEnderPearls("GriefPrevention: {}\n");
        EnderPearlSettings configured = this.codec.decodeEnderPearls(
                "GriefPrevention:\n  Claims:\n    EnderPearlsRequireAccessTrust: false\n    RefundDeniedEnderPearls: false\n");

        assertTrue(defaults.requireAccessTrust());
        assertTrue(defaults.refundDenied());
        assertFalse(configured.requireAccessTrust());
        assertFalse(configured.refundDenied());
    }

    @Test
    void rejectsSwitchesThatAreNotBooleans()
    {
        assertThrows(WorldProtectionConfigException.class, () -> this.codec.decodeBlockUse(
                "GriefPrevention:\n  Claims:\n    LecternReadingRequiresAccessTrust: sometimes\n"));
    }
}
