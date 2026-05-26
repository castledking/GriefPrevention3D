package com.griefprevention.claims.editor;

import org.jetbrains.annotations.NotNull;

/**
 * Internal editor entry point for claim creation and modification.
 */
public interface ClaimEditor
{
    @NotNull ClaimEditResult apply(@NotNull ClaimEditorSession session, @NotNull ClaimEditIntent intent);
}
