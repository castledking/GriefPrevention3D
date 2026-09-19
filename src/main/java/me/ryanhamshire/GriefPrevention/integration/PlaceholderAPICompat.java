package me.ryanhamshire.GriefPrevention.integration;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

//Runs PlaceholderAPI placeholders over message text for a player.
//PlaceholderAPI is only a soft-dependency, so every call is guarded.
public final class PlaceholderAPICompat
{
    private PlaceholderAPICompat()
    {
        throw new AssertionError("Instantiation of an utility class.");
    }

    //checks whether PlaceholderAPI is loaded and enabled at runtime
    public static boolean isAvailable()
    {
        try
        {
            return Bukkit.getPluginManager() != null
                    && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    //parses PlaceholderAPI placeholders in the given text for the player.
    //Returns the text unchanged when PlaceholderAPI is unavailable, the player
    //is offline, or the text contains no "%..." pattern to expand.
    public static @Nullable String parse(@Nullable OfflinePlayer player, @Nullable String text)
    {
        if (!isAvailable() || player == null || text == null || !text.contains("%"))
        {
            return text;
        }

        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null)
        {
            return text;
        }

        try
        {
            return PlaceholderAPI.setPlaceholders(onlinePlayer, text);
        }
        catch (Throwable ignored)
        {
            return text;
        }
    }
}