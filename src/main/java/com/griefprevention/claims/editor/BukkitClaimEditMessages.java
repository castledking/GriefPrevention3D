package com.griefprevention.claims.editor;

import me.ryanhamshire.GriefPrevention.Messages;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit message mapping for the shared claim editor.
 */
public final class BukkitClaimEditMessages
{
    private BukkitClaimEditMessages()
    {
    }

    public static @NotNull Messages toMessage(@NotNull ClaimEditMessageKey key)
    {
        switch (key)
        {
            case MUST_HOLD_MODIFICATION_TOOL_FOR_THAT:
                return Messages.MustHoldModificationToolForThat;
            default:
                throw new IllegalStateException("Unhandled claim edit message key: " + key);
        }
    }
}
