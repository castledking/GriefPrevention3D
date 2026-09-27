package com.griefprevention.protection;

import org.jetbrains.annotations.NotNull;

/** Thrown when the world protection rules in config.yml cannot be read safely. */
public final class WorldProtectionConfigException extends Exception
{
    public WorldProtectionConfigException(@NotNull String message)
    {
        super(message);
    }

    public WorldProtectionConfigException(@NotNull String message, @NotNull Throwable cause)
    {
        super(message, cause);
    }
}
