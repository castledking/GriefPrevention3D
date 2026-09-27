package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;

/** Thrown when the claim-tool settings in config.yml cannot be read safely. */
public final class ClaimToolConfigException extends Exception
{
    public ClaimToolConfigException(@NotNull String message)
    {
        super(message);
    }

    public ClaimToolConfigException(@NotNull String message, @NotNull Throwable cause)
    {
        super(message, cause);
    }
}
