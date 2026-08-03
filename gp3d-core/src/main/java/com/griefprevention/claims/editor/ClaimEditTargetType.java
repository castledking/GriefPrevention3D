package com.griefprevention.claims.editor;

/**
 * The kind of claim object currently targeted by an edit session.
 */
public enum ClaimEditTargetType
{
    NEW_PARENT_CLAIM,
    EXISTING_PARENT_CLAIM,
    EXISTING_SUBCLAIM,
    /** A shaped subdivision that does not exist yet; its path is drawn free-form inside a parent claim. */
    NEW_SUBDIVISION_CLAIM,
    /** An existing shaped subdivision whose boundary is being reshaped. */
    EXISTING_SUBDIVISION_CLAIM
}
