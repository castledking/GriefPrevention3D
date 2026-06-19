package me.ryanhamshire.GriefPrevention;

import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for converting user-friendly color codes to Minecraft legacy format.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@code $} or {@code &} prefix codes (e.g. {@code $a}, {@code &b}) → {@code §a}, {@code §b}</li>
 *   <li>{@code &amp;#RRGGBB} or {@code #RRGGBB} hex codes → {@code §x§R§R§G§G§B§B} (1.16+)</li>
 *   <li>On pre-1.16 servers, hex codes are stripped to avoid raw {@code §x} output</li>
 * </ul>
 */
public final class TextColor {

    private static final Pattern AMP_HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern HASH_HEX_PATTERN = Pattern.compile("#([0-9a-fA-F]{6})");
    private static final Boolean hexColorSupported;

    static {
        boolean supported = true;
        try {
            String version = Bukkit.getBukkitVersion();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)").matcher(version);
            if (m.find()) {
                int major = Integer.parseInt(m.group(1));
                int minor = Integer.parseInt(m.group(2));
                // Hex colors (§x) introduced in Minecraft 1.16
                supported = major > 1 || minor >= 16;
            }
        } catch (Exception ignored) {
        }
        hexColorSupported = supported;
    }

    private TextColor() {}

    /**
     * Applies all color code transformations to the input string.
     *
     * <p>Order of operations:
     * <ol>
     *   <li>Convert {@code &amp;#RRGGBB} hex codes to Minecraft legacy format (1.16+) or strip them</li>
     *   <li>Convert {@code #RRGGBB} hex codes to Minecraft legacy format (1.16+) or strip them</li>
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

        if (hexColorSupported) {
            // Convert &#RRGGBB hex codes to §x§R§R§G§G§B§B format (1.16+)
            text = convertHexColors(text);
        } else {
            // Strip hex codes entirely on pre-1.16 (raw §x would display as garbage)
            text = stripHexColors(text);
        }

        // Convert $ and & prefix codes to §
        text = text.replace('$', '\u00A7');
        text = text.replace('&', '\u00A7');

        return text;
    }

    /**
     * Converts &#RRGGBB and #RRGGBB hex codes to Minecraft legacy §x§R§R§G§G§B§B format (1.16+).
     */
    private static String convertHexColors(String text) {
        text = replaceHexPattern(AMP_HEX_PATTERN, text);
        text = replaceHexPattern(HASH_HEX_PATTERN, text);
        return text;
    }

    private static String replaceHexPattern(Pattern pattern, String text) {
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

    /**
     * Strips all hex color codes (&#RRGGBB and #RRGGBB) from the text on pre-1.16 servers.
     */
    private static String stripHexColors(String text) {
        text = AMP_HEX_PATTERN.matcher(text).replaceAll("");
        text = HASH_HEX_PATTERN.matcher(text).replaceAll("");
        return text;
    }
}
