package com.griefprevention.protection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** How pistons are limited near claims, as Paper's {@code GriefPrevention.PistonMovement}. */
public enum PistonMode
{
    /** Pistons may not move blocks into a claim of another claim tree. */
    EVERYWHERE,
    /** Like {@link #EVERYWHERE}; Paper checks bounding boxes rather than blocks. */
    EVERYWHERE_SIMPLE,
    /** Pistons only work inside claims, and only on blocks of their own claim. */
    CLAIMS_ONLY,
    /** Pistons are not limited. */
    IGNORED;

    /**
     * @return the named mode, or {@link #CLAIMS_ONLY} for an absent or unknown name, as on Paper
     */
    public static @NotNull PistonMode of(@Nullable String value)
    {
        if (value == null)
        {
            return CLAIMS_ONLY;
        }
        try
        {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception)
        {
            return CLAIMS_ONLY;
        }
    }
}
