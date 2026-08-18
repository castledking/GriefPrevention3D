package com.griefprevention.messages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** A run of text sharing one legacy color and style state. */
public final class LegacySegment
{
    private final String text;
    private final @Nullable String color;
    private final boolean bold;
    private final boolean italic;
    private final boolean underlined;
    private final boolean strikethrough;
    private final boolean obfuscated;

    LegacySegment(
            @NotNull String text,
            @Nullable String color,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated)
    {
        this.text = text;
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
    }

    public @NotNull String text()
    {
        return this.text;
    }

    /**
     * @return a single legacy color code character ({@code 0}-{@code 9}, {@code a}-{@code f}), an
     *         {@code #rrggbb} hex color, or null to inherit the caller's default color
     */
    public @Nullable String color()
    {
        return this.color;
    }

    public boolean bold()
    {
        return this.bold;
    }

    public boolean italic()
    {
        return this.italic;
    }

    public boolean underlined()
    {
        return this.underlined;
    }

    public boolean strikethrough()
    {
        return this.strikethrough;
    }

    public boolean obfuscated()
    {
        return this.obfuscated;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other) return true;
        if (!(other instanceof LegacySegment)) return false;
        LegacySegment that = (LegacySegment) other;
        return this.bold == that.bold
                && this.italic == that.italic
                && this.underlined == that.underlined
                && this.strikethrough == that.strikethrough
                && this.obfuscated == that.obfuscated
                && this.text.equals(that.text)
                && Objects.equals(this.color, that.color);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.text, this.color, this.bold, this.italic, this.underlined,
                this.strikethrough, this.obfuscated);
    }

    @Override
    public String toString()
    {
        return "LegacySegment{text='" + this.text + "', color=" + this.color
                + ", bold=" + this.bold
                + ", italic=" + this.italic
                + ", underlined=" + this.underlined
                + ", strikethrough=" + this.strikethrough
                + ", obfuscated=" + this.obfuscated
                + '}';
    }
}
