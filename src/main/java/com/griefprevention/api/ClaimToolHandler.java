package com.griefprevention.api;

import org.jetbrains.annotations.NotNull;

public interface ClaimToolHandler
{
    default int getPriority()
    {
        return 0;
    }

    boolean handle(@NotNull ClaimToolContext context);
}
