package com.griefprevention.claims.editor;

import me.ryanhamshire.GriefPrevention.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Output from applying an editor intent.
 */
public final class ClaimEditResult
{
    private final boolean success;
    private final @Nullable ClaimEditFailureType failureType;
    private final @Nullable Messages fallbackMessage;
    private final @NotNull ClaimEditorSession session;
    private final @NotNull ClaimEditPreview preview;
    private final @NotNull List<String> messages;

    public ClaimEditResult(
            boolean success,
            @Nullable ClaimEditFailureType failureType,
            @Nullable Messages fallbackMessage,
            @NotNull ClaimEditorSession session,
            @NotNull ClaimEditPreview preview,
            @NotNull List<String> messages)
    {
        this.success = success;
        this.failureType = failureType;
        this.fallbackMessage = fallbackMessage;
        this.session = session;
        this.preview = preview;
        this.messages = List.copyOf(messages);
    }

    public static @NotNull ClaimEditResult success(
            @NotNull ClaimEditorSession session,
            @NotNull ClaimEditPreview preview,
            @NotNull List<String> messages
    )
    {
        return new ClaimEditResult(true, null, null, session, preview, messages);
    }

    public static @NotNull ClaimEditResult failure(
            @NotNull ClaimEditFailureType failureType,
            @Nullable Messages fallbackMessage,
            @NotNull ClaimEditorSession session,
            @NotNull ClaimEditPreview preview,
            @NotNull List<String> messages
    )
    {
        return new ClaimEditResult(false, failureType, fallbackMessage, session, preview, messages);
    }

    public boolean success()
    {
        return success;
    }

    public @Nullable ClaimEditFailureType failureType()
    {
        return failureType;
    }

    public @Nullable Messages fallbackMessage()
    {
        return fallbackMessage;
    }

    public @NotNull ClaimEditorSession session()
    {
        return session;
    }

    public @NotNull ClaimEditPreview preview()
    {
        return preview;
    }

    public @NotNull List<String> messages()
    {
        return messages;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof ClaimEditResult))
        {
            return false;
        }
        ClaimEditResult that = (ClaimEditResult) other;
        return success == that.success
                && failureType == that.failureType
                && fallbackMessage == that.fallbackMessage
                && session.equals(that.session)
                && preview.equals(that.preview)
                && messages.equals(that.messages);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(success, failureType, fallbackMessage, session, preview, messages);
    }

    @Override
    public String toString()
    {
        return "ClaimEditResult[success=" + success
                + ", failureType=" + failureType
                + ", fallbackMessage=" + fallbackMessage
                + ", session=" + session
                + ", preview=" + preview
                + ", messages=" + messages
                + ']';
    }
}
