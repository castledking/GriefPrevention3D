package com.griefprevention.persistence;

import org.jetbrains.annotations.ApiStatus;

/**
 * Raised when persisted claim data cannot be interpreted without losing or guessing semantics.
 */
@ApiStatus.Internal
public final class ClaimDocumentFormatException extends Exception
{
    public ClaimDocumentFormatException(String message)
    {
        super(message);
    }

    public ClaimDocumentFormatException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
