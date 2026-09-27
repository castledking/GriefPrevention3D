package com.griefprevention.fabric;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Splits the text after a command's name into Bukkit-style arguments. Each GriefPrevention command
 * takes its arguments as one greedy string, as LuckPerms does on Fabric, so {@code [permission.node]}
 * targets, negative amounts and option aliases all reach the handler exactly as typed.
 */
final class FabricCommandLine
{
    private static final String[] NO_ARGUMENTS = new String[0];

    private FabricCommandLine()
    {
    }

    static @NotNull String @NotNull [] split(@NotNull String raw)
    {
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? NO_ARGUMENTS : trimmed.split("\\s+");
    }

    /** @return the argument being typed at the end of {@code raw}, and those before it */
    static @NotNull Completion completion(@NotNull String raw)
    {
        int lastSpace = raw.lastIndexOf(' ');
        return new Completion(
                split(raw.substring(0, lastSpace + 1)),
                raw.substring(lastSpace + 1),
                lastSpace + 1);
    }

    /** @return the options starting with {@code prefix}, ignoring case, in order and without repeats */
    static @NotNull List<String> matching(@NotNull Iterable<String> options, @NotNull String prefix)
    {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        Set<String> result = new LinkedHashSet<>();
        for (String option : options)
        {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerPrefix))
            {
                result.add(option);
            }
        }
        return new ArrayList<>(result);
    }

    static final class Completion
    {
        private final @NotNull String @NotNull [] previous;
        private final @NotNull String current;
        private final int currentStart;

        private Completion(@NotNull String @NotNull [] previous, @NotNull String current, int currentStart)
        {
            this.previous = previous;
            this.current = current;
            this.currentStart = currentStart;
        }

        /** @return the complete arguments before the one being typed */
        @NotNull String @NotNull [] previous()
        {
            return this.previous;
        }

        /** @return what has been typed of the current argument so far */
        @NotNull String current()
        {
            return this.current;
        }

        /** @return where the current argument starts within the raw text */
        int currentStart()
        {
            return this.currentStart;
        }
    }
}
