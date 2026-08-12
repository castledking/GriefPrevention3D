package com.griefprevention.platform.knockback;

import com.griefprevention.platform.PlatformDetection;
import com.griefprevention.platform.PlatformListener;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Platform-specific listener for handling player-caused knockback in claims.
 * <p>
 * Uses Paper-specific or Spigot event depending on the detected platform.
 * This prevents players from using melee attacks (spears), projectiles
 * (wind charges), or other mechanisms to knock entities around in protected
 * claims where such interaction is restricted.
 */
public class KnockbackProtectionListener implements PlatformListener
{

    enum KnockbackApi
    {
        PAPER,
        SPIGOT,
        UNSUPPORTED
    }

    private final DataStore dataStore;
    private final GriefPrevention plugin;

    public KnockbackProtectionListener(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin)
    {
        this.dataStore = dataStore;
        this.plugin = plugin;
    }

    @Override
    public boolean isSupported()
    {
        // Paper-compatible platforms and Spigot expose different knockback events.
        // Verify the selected API is present before loading its handler class.
        switch (knockbackApiFor(PlatformDetection.getPlatform()))
        {
            case PAPER:
                return PlatformDetection.classExists("com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent")
                        && PlatformDetection.classExists("io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent");
            case SPIGOT:
                return PlatformDetection.classExists("org.bukkit.event.entity.EntityKnockbackByEntityEvent");
            default:
                return false;
        }
    }

    @Override
    public @NotNull Listener create()
    {
        switch (knockbackApiFor(PlatformDetection.getPlatform()))
        {
            case PAPER:
                return new PaperKnockbackProtectionHandler(dataStore, plugin);
            case SPIGOT:
                return new SpigotKnockbackProtectionHandler(dataStore, plugin);
            default:
                return new SpigotKnockbackProtectionHandler(dataStore, plugin);
        }
    }

    static @NotNull KnockbackApi knockbackApiFor(@NotNull PlatformDetection.Platform platform)
    {
        switch (platform)
        {
            case CANVAS:
            case PAPER:
                return KnockbackApi.PAPER;
            case SPIGOT:
                return KnockbackApi.SPIGOT;
            default:
                return KnockbackApi.UNSUPPORTED;
        }
    }

}
