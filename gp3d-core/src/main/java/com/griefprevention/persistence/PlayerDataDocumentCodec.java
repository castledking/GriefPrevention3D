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
}
