package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricClaimBlockAccrualTest
{
    @Test
    void usesTheGlobalTenMinuteBukkitCadence()
    {
        FabricClaimBlockService service = new FabricClaimBlockService(
                java.nio.file.Path.of("unused"),
                org.slf4j.helpers.NOPLogger.NOP_LOGGER,
                (playerId, permission) -> null
        );
        FabricClaimBlockAccrual accrual = new FabricClaimBlockAccrual(
                service,
                org.slf4j.helpers.NOPLogger.NOP_LOGGER
        );

        for (long tick = 1; tick < FabricClaimBlockAccrual.DELIVERY_INTERVAL_TICKS; tick++)
        {
            assertFalse(accrual.advanceTick());
        }
        assertTrue(accrual.advanceTick());
        assertFalse(accrual.advanceTick());
    }

    @Test
    void mirrorsLegacyMovementVehicleLiquidAndWorldIdleDetection()
    {
        FabricClaimBlockAccrual.PlayerPosition origin = position("world", 0, 64, 0);
        assertFalse(FabricClaimBlockAccrual.isIdle(false, false, null, origin, 0));
        assertTrue(FabricClaimBlockAccrual.isIdle(true, false, null, origin, 0));
        assertTrue(FabricClaimBlockAccrual.isIdle(false, true, null, origin, 0));
        assertTrue(FabricClaimBlockAccrual.isIdle(false, false, origin, origin, 0));
        assertTrue(FabricClaimBlockAccrual.isIdle(
                false,
                false,
                origin,
                position("world", 2, 64, 0),
                4
        ));
        assertFalse(FabricClaimBlockAccrual.isIdle(
                false,
                false,
                origin,
                position("world", 3, 64, 0),
                4
        ));
        assertFalse(FabricClaimBlockAccrual.isIdle(
                false,
                false,
                origin,
                position("world_nether", 0, 64, 0),
                100
        ));
        assertFalse(FabricClaimBlockAccrual.isIdle(false, false, origin, origin, -1));
    }

    private static FabricClaimBlockAccrual.PlayerPosition position(
            String dimension,
            double x,
            double y,
            double z)
    {
        return new FabricClaimBlockAccrual.PlayerPosition(dimension, x, y, z);
    }
}
