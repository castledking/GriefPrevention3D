package com.griefprevention.fabric;

import com.griefprevention.protection.BlockPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricPotionSourcesTest
{
    @Test
    void cloudKeepsTheDispenserOriginAfterTheProjectileHasMoved()
    {
        Object projectile = new Object();
        Object cloud = new Object();
        FabricPotionSources.Origin origin = new FabricPotionSources.Origin("world", new BlockPoint(-5, 64, 0));
        FabricPotionSources.remember(projectile, origin);
        FabricPotionSources.duringImpact(projectile, () -> FabricPotionSources.inheritImpactOrigin(cloud));
        assertFalse(FabricPotionSources.isPotionImpact());
        assertEquals(origin, FabricPotionSources.origin(cloud));
        Object unrelated = new Object();
        FabricPotionSources.inheritImpactOrigin(unrelated);
        assertNull(FabricPotionSources.origin(unrelated));
    }

    @Test
    void nestedOrFailedImpactsCannotLeakAnOriginIntoAnotherPotion()
    {
        Object outer = new Object();
        Object inner = new Object();
        Object outerCloud = new Object();
        Object innerCloud = new Object();
        FabricPotionSources.Origin origin = new FabricPotionSources.Origin("world", new BlockPoint(0, 64, 0));
        FabricPotionSources.remember(outer, origin);
        assertThrows(IllegalStateException.class, () -> FabricPotionSources.duringImpact(outer, () -> {
            FabricPotionSources.duringImpact(inner, () -> {
                assertTrue(FabricPotionSources.isPotionImpact());
                FabricPotionSources.inheritImpactOrigin(innerCloud);
            });
            FabricPotionSources.inheritImpactOrigin(outerCloud);
            throw new IllegalStateException("impact failed");
        }));
        assertNull(FabricPotionSources.origin(innerCloud));
        assertEquals(origin, FabricPotionSources.origin(outerCloud));
        Object laterCloud = new Object();
        FabricPotionSources.inheritImpactOrigin(laterCloud);
        assertNull(FabricPotionSources.origin(laterCloud));
        assertFalse(FabricPotionSources.isPotionImpact());
    }
}
