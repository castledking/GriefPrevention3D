package com.griefprevention.protection;

import org.jetbrains.annotations.ApiStatus;

/** Indicates that an active explosion-protection config field cannot be interpreted safely. */
@ApiStatus.Internal
public final class ExplosionProtectionConfigException extends Exception
{
    public ExplosionProtectionConfigException(String message)
    {
        super(message);
    }

    public ExplosionProtectionConfigException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
