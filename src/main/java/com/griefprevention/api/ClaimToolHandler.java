package com.griefprevention.api;

import org.jetbrains.annotations.NotNull;

/**
 * Addon hook for claim tool interactions.
 *
 * <p>Handlers are called before GriefPrevention's built-in stick and shovel logic. Returning {@code true}
 * indicates that the interaction was fully handled and built-in processing should stop.</p>
 */
public interface ClaimToolHandler
{

    /**
     * Determine handler ordering. Higher values run first.
     *
     * @return the handler priority
     */
    default int getPriority()
    {
        return 0;
    }

    /**
     * Handle a claim tool interaction.
     *
     * @param context the tool interaction context
     * @return true if the interaction was handled
     */
    boolean handle(@NotNull ClaimToolContext context);

}
