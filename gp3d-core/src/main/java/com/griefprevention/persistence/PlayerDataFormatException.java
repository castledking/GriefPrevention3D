package com.griefprevention.persistence;

/** Indicates that an upstream flat-file player-data record cannot be read safely. */
public final class PlayerDataFormatException extends Exception
{
    public PlayerDataFormatException(String message)
    {
        super(message);
    }

    public PlayerDataFormatException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
