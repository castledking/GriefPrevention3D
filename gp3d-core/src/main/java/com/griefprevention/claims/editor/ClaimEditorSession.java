package com.griefprevention.claims.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Per-player claim editing state owned by the recode editor layer.
 */
public final class ClaimEditorSession
{
    private final UUID playerId;
    private final ClaimEditorMode mode;
    private final @Nullable ClaimEditTarget activeTarget;
    private final @Nullable ShapedPathDraft openPath;
    private final @Nullable SegmentSelection activeSegment;
    private final ClaimEditPreview preview;
    private final @Nullable ClaimEditSource source;

    public ClaimEditorSession(
            @NotNull UUID playerId,
            @NotNull ClaimEditorMode mode,
            @Nullable ClaimEditTarget activeTarget,
            @Nullable ShapedPathDraft openPath,
            @Nullable SegmentSelection activeSegment,
            @NotNull ClaimEditPreview preview,
            @Nullable ClaimEditSource source
    )
    {
        this.playerId = playerId;
        this.mode = mode;
        this.activeTarget = activeTarget;
        this.openPath = openPath;
        this.activeSegment = activeSegment;
        this.preview = preview;
        this.source = source;
    }

    public static @NotNull ClaimEditorSession idle(@NotNull UUID playerId)
    {
        return new ClaimEditorSession(playerId, ClaimEditorMode.IDLE, null, null, null, ClaimEditPreview.empty(), null);
    }

    public @NotNull UUID playerId()
    {
        return this.playerId;
    }

    public @NotNull ClaimEditorMode mode()
    {
        return this.mode;
    }

    public @Nullable ClaimEditTarget activeTarget()
    {
        return this.activeTarget;
    }

    public @Nullable ShapedPathDraft openPath()
    {
        return this.openPath;
    }

    public @Nullable SegmentSelection activeSegment()
    {
        return this.activeSegment;
    }

    public @NotNull ClaimEditPreview preview()
    {
        return this.preview;
    }

    public @Nullable ClaimEditSource source()
    {
        return this.source;
    }

    public @NotNull ClaimEditorSession withMode(@NotNull ClaimEditorMode mode, @Nullable ClaimEditSource source)
    {
        return new ClaimEditorSession(this.playerId, mode, this.activeTarget, this.openPath, this.activeSegment, this.preview, source);
    }

    public @NotNull ClaimEditorSession withTarget(@Nullable ClaimEditTarget activeTarget)
    {
        return new ClaimEditorSession(this.playerId, this.mode, activeTarget, this.openPath, this.activeSegment, this.preview, this.source);
    }

    public @NotNull ClaimEditorSession withOpenPath(@Nullable ShapedPathDraft openPath)
    {
        return new ClaimEditorSession(this.playerId, this.mode, this.activeTarget, openPath, this.activeSegment, this.preview, this.source);
    }

    public @NotNull ClaimEditorSession withActiveSegment(@Nullable SegmentSelection activeSegment)
    {
        return new ClaimEditorSession(this.playerId, this.mode, this.activeTarget, this.openPath, activeSegment, this.preview, this.source);
    }

    public @NotNull ClaimEditorSession withPreview(@NotNull ClaimEditPreview preview)
    {
        return new ClaimEditorSession(this.playerId, this.mode, this.activeTarget, this.openPath, this.activeSegment, preview, this.source);
    }
}
