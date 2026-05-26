package com.griefprevention.claims.editor;

/**
 * High-level editor failure categories for UX and preview handling.
 */
public enum ClaimEditFailureType
{
    INVALID_GEOMETRY,
    SELF_INTERSECTION,
    MIN_WIDTH,
    INSUFFICIENT_CLAIM_BLOCKS,
    OVERLAPS_OTHER_CLAIM,
    NOT_OWNER,
    NOT_EDITABLE_FROM_HERE,
    AMBIGUOUS_EDIT_REQUEST,
    NO_ACTIVE_SEGMENT
}
