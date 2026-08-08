package com.griefprevention.claims;

/** Indicates that claim-block configuration cannot be interpreted safely. */
public final class ClaimBlockConfigException extends Exception
{
    public ClaimBlockConfigException(String message)
    {
        super(message);
    }

    public ClaimBlockConfigException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
