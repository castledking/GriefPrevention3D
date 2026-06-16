package me.ryanhamshire.GriefPrevention;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for converting user-friendly color codes to Minecraft legacy format.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@code $} or {@code &} prefix codes (e.g. {@code $a}, {@code &b}) → {@code §a}, {@code §b}</li>
 *   <li>{@code &amp;#RRGGBB} or {@code #RRGGBB} hex codes → {@code §x§R§R§G§G§B§B}</li>
 * </ul>
 */
public final class TextColor {

    private static final Pattern AMP_HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern HASH_HEX_PATTERN = Pattern.compile("#([0-9a-fA-F]{6})");

    private TextColor() {}

    /**
     * Applies all color code transformations to the input string.
     *
     * <p>Order of operations:
     * <ol>
     *   <li>Convert {@code &amp;#RRGGBB} hex codes to Minecraft legacy format</li>
     *   <li>Convert {@code #RRGGBB} hex codes to Minecraft legacy format</li>
     *   <li>Convert {@code $} prefix codes to {@code §}</li>
     *   <li>Convert {@code &amp;} prefix codes to {@code §}</li>
     * </ol>
     *
     * @param text the text to process
     * @return the text with all color codes converted to Minecraft legacy format
     */
    public static String translate(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Convert &#RRGGBB hex codes
        text = convertHexColors(AMP_HEX_PATTERN, text);

        // Convert #RRGGBB hex codes
        text = convertHexColors(HASH_HEX_PATTERN, text);

        // Convert $ and & prefix codes to §
        text = text.replace('$', '\u00A7');
        text = text.replace('&', '\u00A7');

        return text;
    }

    /**
     * Converts hex color codes matching the given pattern to Minecraft legacy §x§R§R§G§G§B§B format.
     */
    private static String convertHexColors(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1).toLowerCase();
            String legacy = "\u00A7x"
                    + "\u00A7" + hex.charAt(0)
                    + "\u00A7" + hex.charAt(1)
                    + "\u00A7" + hex.charAt(2)
                    + "\u00A7" + hex.charAt(3)
                    + "\u00A7" + hex.charAt(4)
                    + "\u00A7" + hex.charAt(5);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(legacy));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
