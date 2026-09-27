package com.griefprevention.persistence;

import org.jetbrains.annotations.NotNull;

/** Reads and writes the upstream four-line flat-file player-data format. */
public final class PlayerDataDocumentCodec
{
    public @NotNull PlayerDataDocument decode(@NotNull String input)
            throws PlayerDataFormatException
    {
        String[] lines = input.split("\\r\\n|\\n|\\r", -1);
        if (lines.length < 3)
        {
            throw new PlayerDataFormatException(
                    "Player data must contain the unused first line, accrued blocks, and bonus blocks."
            );
        }

        return new PlayerDataDocument(
                integer(lines[1], "accrued claim blocks"),
                integer(lines[2], "bonus claim blocks")
        );
    }

    public @NotNull String encode(@NotNull PlayerDataDocument document)
    {
        return "\n"
                + document.accruedClaimBlocks()
                + "\n"
                + document.bonusClaimBlocks()
                + "\n\n";
    }

    /**
     * Replaces only the accrued-block line while preserving every other byte represented by the
     * input string, including its line-ending convention and unknown trailing addon data.
     */
    public @NotNull String replaceAccruedClaimBlocks(@NotNull String input, int accruedClaimBlocks)
            throws PlayerDataFormatException
    {
        return replaceLine(input, 1, accruedClaimBlocks, "accrued-block");
    }

    /**
     * Replaces only the bonus-block line, preserving everything else the same way
     * {@link #replaceAccruedClaimBlocks} does.
     */
    public @NotNull String replaceBonusClaimBlocks(@NotNull String input, int bonusClaimBlocks)
            throws PlayerDataFormatException
    {
        return replaceLine(input, 2, bonusClaimBlocks, "bonus-block");
    }

    private @NotNull String replaceLine(
            @NotNull String input,
            int lineIndex,
            int value,
            @NotNull String description)
            throws PlayerDataFormatException
    {
        decode(input);

        int lineStart = 0;
        for (int i = 0; i < lineIndex; i++)
        {
            lineStart = lineSeparatorEnd(input, lineSeparatorStart(input, lineStart));
            if (lineStart < 0)
            {
                throw new PlayerDataFormatException("Player data is missing the " + description + " line.");
            }
        }
        int lineEnd = lineSeparatorStart(input, lineStart);
        if (lineEnd < 0)
        {
            // The last line need not end with a separator.
            lineEnd = input.length();
        }

        return input.substring(0, lineStart)
                + value
                + input.substring(lineEnd);
    }

    private static int integer(@NotNull String input, @NotNull String field)
            throws PlayerDataFormatException
    {
        try
        {
            return Integer.parseInt(input);
        }
        catch (NumberFormatException exception)
        {
            throw new PlayerDataFormatException(
                    "Invalid " + field + " value '" + input + "'.",
                    exception
            );
        }
    }

    private static int lineSeparatorStart(@NotNull String input, int fromIndex)
    {
        for (int i = Math.max(0, fromIndex); i < input.length(); i++)
        {
            char character = input.charAt(i);
            if (character == '\r' || character == '\n')
            {
                return i;
            }
        }
        return -1;
    }

    private static int lineSeparatorEnd(@NotNull String input, int separatorStart)
    {
        if (separatorStart < 0 || separatorStart >= input.length())
        {
            return -1;
        }
        if (input.charAt(separatorStart) == '\r'
                && separatorStart + 1 < input.length()
                && input.charAt(separatorStart + 1) == '\n')
        {
            return separatorStart + 2;
        }
        return separatorStart + 1;
    }
}
