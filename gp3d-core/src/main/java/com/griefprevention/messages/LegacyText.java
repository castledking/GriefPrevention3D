package com.griefprevention.messages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the Paper plugin's message text into styled segments a modern client can render.
 *
 * <p>{@code messages.yml} is shared with Paper, whose bundled locales use {@code &}/{@code $} prefix
 * codes, {@code &#RRGGBB} hex colors, and literal {@code \n} escapes. This mirrors Paper's
 * {@code TextColor.translate} and its newline expansion so the same file renders identically on both
 * platforms instead of showing raw codes.
 */
public final class LegacyText
{
    /** The legacy formatting prefix, {@code §}. */
    public static final char SECTION = '\u00A7';

    private static final Pattern AMP_HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern HASH_HEX_PATTERN = Pattern.compile("#([0-9a-fA-F]{6})");
    private static final String COLOR_CODES = "0123456789abcdef";

    private LegacyText()
    {
    }

    /**
     * Converts authoring shorthand into legacy {@code §} codes and real newlines.
     */
    public static @NotNull String translate(@NotNull String text)
    {
        String translated = replaceHex(HASH_HEX_PATTERN, replaceHex(AMP_HEX_PATTERN, text));
        translated = translated.replace('$', SECTION).replace('&', SECTION);
        return translated.replace("\\n", "\n");
    }

    /**
     * @return true when the text carries no visible content, which upstream treats as "message disabled"
     */
    public static boolean isDisabled(@NotNull String translated)
    {
        return strip(translated).trim().isEmpty();
    }

    /**
     * @return the text with every legacy code removed
     */
    public static @NotNull String strip(@NotNull String translated)
    {
        StringBuilder stripped = new StringBuilder(translated.length());
        int index = 0;
        while (index < translated.length())
        {
            int consumed = codeLengthAt(translated, index);
            if (consumed > 0)
            {
                index += consumed;
                continue;
            }
            stripped.append(translated.charAt(index));
            index++;
        }
        return stripped.toString();
    }

    /**
     * Splits translated text into runs of uniform color and style.
     *
     * <p>Segments carrying no text are dropped, so a run of codes with nothing after it yields
     * nothing. A leading color of null means the segment inherits whatever default the caller applies.
     */
    public static @NotNull List<LegacySegment> segments(@NotNull String translated)
    {
        List<LegacySegment> segments = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        String color = null;
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;

        int index = 0;
        while (index < translated.length())
        {
            int consumed = codeLengthAt(translated, index);
            if (consumed == 0)
            {
                pending.append(translated.charAt(index));
                index++;
                continue;
            }

            // A code ends the current run; flush it before the new state takes effect.
            if (pending.length() > 0)
            {
                segments.add(new LegacySegment(pending.toString(), color, bold, italic, underlined,
                        strikethrough, obfuscated));
                pending.setLength(0);
            }

            if (consumed == 14)
            {
                color = hexColorAt(translated, index);
                bold = false;
                italic = false;
                underlined = false;
                strikethrough = false;
                obfuscated = false;
                index += consumed;
                continue;
            }

            char code = Character.toLowerCase(translated.charAt(index + 1));
            if (COLOR_CODES.indexOf(code) >= 0)
            {
                // Vanilla treats a color as a full reset of the preceding style.
                color = String.valueOf(code);
                bold = false;
                italic = false;
                underlined = false;
                strikethrough = false;
                obfuscated = false;
            }
            else if (code == 'r')
            {
                color = null;
                bold = false;
                italic = false;
                underlined = false;
                strikethrough = false;
                obfuscated = false;
            }
            else if (code == 'k')
            {
                obfuscated = true;
            }
            else if (code == 'l')
            {
                bold = true;
            }
            else if (code == 'm')
            {
                strikethrough = true;
            }
            else if (code == 'n')
            {
                underlined = true;
            }
            else if (code == 'o')
            {
                italic = true;
            }
            index += consumed;
        }

        if (pending.length() > 0)
        {
            segments.add(new LegacySegment(pending.toString(), color, bold, italic, underlined,
                    strikethrough, obfuscated));
        }
        return Collections.unmodifiableList(segments);
    }

    /**
     * @return the character length of the legacy code starting at the index, or 0 when there is none
     */
    private static int codeLengthAt(@NotNull String text, int index)
    {
        if (text.charAt(index) != SECTION || index + 1 >= text.length())
        {
            return 0;
        }

        char code = Character.toLowerCase(text.charAt(index + 1));
        if (code == 'x')
        {
            return hexColorAt(text, index) == null ? 0 : 14;
        }
        return "0123456789abcdefklmnor".indexOf(code) >= 0 ? 2 : 0;
    }

    /**
     * Reads a {@code §x§R§R§G§G§B§B} sequence.
     *
     * @return the {@code #rrggbb} color, or null when the sequence is truncated or malformed
     */
    private static @Nullable String hexColorAt(@NotNull String text, int index)
    {
        if (index + 14 > text.length())
        {
            return null;
        }

        StringBuilder hex = new StringBuilder(7).append('#');
        for (int pair = 0; pair < 6; pair++)
        {
            int offset = index + 2 + (pair * 2);
            if (text.charAt(offset) != SECTION)
            {
                return null;
            }
            char digit = Character.toLowerCase(text.charAt(offset + 1));
            if ("0123456789abcdef".indexOf(digit) < 0)
            {
                return null;
            }
            hex.append(digit);
        }
        return hex.toString();
    }

    /**
     * Rewrites {@code #RRGGBB} hex colors into the legacy {@code §x} encoding, matching Paper.
     */
    private static @NotNull String replaceHex(@NotNull Pattern pattern, @NotNull String text)
    {
        Matcher matcher = pattern.matcher(text);
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find())
        {
            String hex = matcher.group(1).toLowerCase();
            StringBuilder legacy = new StringBuilder(14).append(SECTION).append('x');
            for (int i = 0; i < hex.length(); i++)
            {
                legacy.append(SECTION).append(hex.charAt(i));
            }
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(legacy.toString()));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }
}
