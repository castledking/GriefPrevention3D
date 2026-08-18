package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A per-claim policy toggle a player can flip from the claim commands.
 *
 * <p>These mirror the boolean fields Bukkit exposes on {@code Claim}. Changing one does not touch the
 * claim's modified date, matching upstream, where the flags are plain fields and the date is only set
 * when the claim itself is built.
 */
public enum ClaimFlag
{
    EXPLOSIONS("explosions"),
    WITHER_EXPLOSIONS("witherexplosions"),
    PVP("pvp"),
    ALERTS("alerts");

    private final String commandName;

    ClaimFlag(@NotNull String commandName)
    {
        this.commandName = commandName;
    }

    /**
     * @return the subcommand name this flag is set through, as on Paper
     */
    public @NotNull String commandName()
    {
        return this.commandName;
    }

    public static @Nullable ClaimFlag byCommandName(@NotNull String name)
    {
        String normalized = name.toLowerCase(Locale.ROOT);
        for (ClaimFlag flag : values())
        {
            if (flag.commandName.equals(normalized))
            {
                return flag;
            }
        }
        return null;
    }
}
