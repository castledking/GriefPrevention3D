package com.griefprevention.platform.knockback;

import com.griefprevention.platform.PlatformDetection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KnockbackProtectionListenerTest
{

    @Test
    void canvasUsesPaperKnockbackEvents()
    {
        assertEquals(
                KnockbackProtectionListener.KnockbackApi.PAPER,
                KnockbackProtectionListener.knockbackApiFor(PlatformDetection.Platform.CANVAS)
        );
    }

    @Test
    void paperAndSpigotKeepTheirNativeKnockbackEvents()
    {
        assertEquals(
                KnockbackProtectionListener.KnockbackApi.PAPER,
                KnockbackProtectionListener.knockbackApiFor(PlatformDetection.Platform.PAPER)
        );
        assertEquals(
                KnockbackProtectionListener.KnockbackApi.SPIGOT,
                KnockbackProtectionListener.knockbackApiFor(PlatformDetection.Platform.SPIGOT)
        );
    }

}
