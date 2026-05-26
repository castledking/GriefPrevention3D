package com.griefprevention.claims.editor;

/**
 * Normalized editor actions shared by tools, commands, and GUIs.
 */
public enum ClaimEditIntentType
{
    ENTER_MODE,
    EXIT_MODE,
    SELECT_CLAIM,
    SELECT_SEGMENT,
    ADD_NODE,
    ADD_CORNER,
    MOVE_SEGMENT,
    EXPAND_SEGMENT,
    UNCLAIM_SEGMENT,
    CLOSE_PATH,
    CANCEL_PATH,
    COMMIT_PREVIEW
}
