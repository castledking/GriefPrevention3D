package com.griefprevention.messages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Thrown when {@code messages.yml} cannot be understood. */
public class MessageCatalogException extends Exception
{
    private static final long serialVersionUID = 1L;

    public MessageCatalogException(@NotNull String message)
    {
        super(message);
    }

    public MessageCatalogException(@NotNull String message, @Nullable Throwable cause)
    {
        super(message, cause);
    }
}
