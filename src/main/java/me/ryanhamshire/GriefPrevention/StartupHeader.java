package me.ryanhamshire.GriefPrevention;

import com.griefprevention.platform.PlatformDetection;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles parsing and display of localized startup headers from startups.txt.
 *
 * <p>Format: each entry is keyed by {locale}{number}[...content...].
 * For example: en1[...], en2[...], es1[...], etc.</p>
 */
public class StartupHeader {

    private static final Pattern ENTRY_KEY_PATTERN = Pattern.compile("^(\\w+?)\\d+\\[$");
    private static final Pattern CLOSING_BRACKET = Pattern.compile("^\\]$");
    private static final Random RANDOM = new Random();

    private final GriefPrevention plugin;

    public StartupHeader(GriefPrevention plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets a random startup header for the configured locale, with all replacements applied.
     *
     * @return the formatted header string, or null if no header could be loaded
     */
    public String getRandomHeader() {
        String langCode = plugin.config_locale != null && !plugin.config_locale.isEmpty()
                ? plugin.config_locale : "en";

        InputStream stream = plugin.getResource("startups.txt");
        if (stream == null) return null;

        Map<String, List<String>> entries = parseStartups(stream);

        List<String> localeEntries = entries.get(langCode);
        if (localeEntries == null || localeEntries.isEmpty()) {
            // Try language prefix: pt_BR -> pt
            int underscore = langCode.indexOf('_');
            if (underscore > 0) {
                localeEntries = entries.get(langCode.substring(0, underscore));
            }
        }
        if (localeEntries == null || localeEntries.isEmpty()) {
            localeEntries = entries.get("en");
        }
        if (localeEntries == null || localeEntries.isEmpty()) return null;

        String header = localeEntries.get(RANDOM.nextInt(localeEntries.size()));
        header = applyReplacements(header, langCode);
        header = TextColor.translate(header);
        return header;
    }

    /**
     * Parses startups.txt into a map of locale -> list of content blocks.
     */
    private Map<String, List<String>> parseStartups(InputStream stream) {
        Map<String, List<String>> result = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String currentLocale = null;
            StringBuilder currentContent = new StringBuilder();
            boolean insideEntry = false;

            String line;
            while ((line = reader.readLine()) != null) {
                if (!insideEntry) {
                    Matcher keyMatcher = ENTRY_KEY_PATTERN.matcher(line.trim());
                    if (keyMatcher.matches()) {
                        currentLocale = keyMatcher.group(1);
                        currentContent.setLength(0);
                        insideEntry = true;
                    }
                } else {
                    if (CLOSING_BRACKET.matcher(line.trim()).matches()) {
                        result.computeIfAbsent(currentLocale, k -> new ArrayList<>())
                                .add(currentContent.toString());
                        insideEntry = false;
                        currentLocale = null;
                    } else {
                        currentContent.append(line).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse startups.txt: " + e.getMessage());
        }

        return result;
    }

    /**
     * Applies all ${...} template replacements to the header content.
     * Builds the ${details} placeholder block with consistent alignment.
     */
    private String applyReplacements(String header, String langCode) {
        String yes = "&#55FF55ON";
        String no = "&#FF5555OFF";

        String version = plugin.getDescription().getVersion();
        header = header.replace("${project.version}", version);
        header = header.replace("${version}", version);
        header = header.replace("${LANG_CODE}", langCode);
        header = header.replace("${startup.time}",
                "&#55FF55" + (System.currentTimeMillis() - plugin.startupStartTime) + "ms");

        // Build the detail block with consistent column alignment
        String storageType = "File";
        if (plugin.dataStore != null) {
            storageType = (plugin.dataStore instanceof FlatFileDataStore) ? "File" : "Database";
        }
        int msgCount = Messages.values().length;
        String localeInfo = plugin.dataStore != null
                ? plugin.dataStore.getMessage(Messages.MessagesLoaded, String.valueOf(msgCount))
                : "Messages loaded: " + msgCount;
        // Strip any leading label from localeInfo to avoid duplication with column header
        String localeCount = localeInfo.replaceAll("^[^:]*:\\s*", "");
        String claimsLoaded = String.valueOf(plugin.dataStore != null ? plugin.dataStore.getClaims().size() : 0);

        String sub3d = plugin.config_claims_allow3DSubdivisions ? yes : no;
        String admin3d = plugin.config_claims_allow3DAdminClaims ? yes : no;
        String nested = plugin.config_claims_allowNestedSubClaims ? yes : no;
        String shaped = plugin.config_claims_allowShapedClaims ? yes : no;

        // Left column: pad entire "label value" to COL_WIDTH so right column aligns
        String platformVersion = PlatformDetection.getServerVersion();

        StringBuilder details = new StringBuilder();
        details.append(formatLine("Platform:", "&#55FF55" + platformVersion, "", ""));
        details.append("\n");
        details.append(formatLine("3D Subdivisions:", "&#55FF55" + sub3d, "3D Admin Claims:", "&#55FF55" + admin3d));
        details.append("\n");
        details.append(formatLine("Nested Claims:", "&#55FF55" + nested, "Shaped Claims:", "&#55FF55" + shaped));
        details.append("\n");
        details.append(formatLine("Locale:", "&#55FF55" + langCode, "Messages loaded:", "&#55FF55" + localeCount));
        details.append("\n");
        details.append(formatLine("Storage:", "&#55FF55" + storageType, "Claims Loaded:", "&#55FF55" + claimsLoaded));

        header = header.replace("${details}", details.toString());

        return header;
    }

    private static final int COL_WIDTH = 26;

    /**
     * Formats a two-column detail line with consistent alignment.
     * Left column (label + value) is padded to {@link #COL_WIDTH} visible chars,
     * then right column is appended.
     */
    private static String formatLine(String leftLabel, String leftValue,
                                     String rightLabel, String rightValue) {
        String leftText = leftLabel + " " + leftValue;
        String paddedLeft = pad(leftText, COL_WIDTH);
        StringBuilder sb = new StringBuilder();
        sb.append("&#AAAAAA").append(paddedLeft);
        if (rightLabel != null && !rightLabel.isEmpty()) {
            sb.append(" &#AAAAAA").append(rightLabel).append(" ").append(rightValue);
        }
        return sb.toString();
    }

    /**
     * Pads a string to the specified width by appending spaces.
     * Color codes ({@code §x} or {@code &#RRGGBB}) are excluded from width calculation.
     */
    private static String pad(String text, int width) {
        String stripped = text.replaceAll("§[0-9a-fk-orx]", "")
                              .replaceAll("&#[0-9a-fA-F]{6}", "")
                              .replaceAll("#[0-9a-fA-F]{6}", "");
        int padding = width - stripped.length();
        if (padding <= 0) return text;
        StringBuilder sb = new StringBuilder(text);
        for (int i = 0; i < padding; i++) sb.append(' ');
        return sb.toString();
    }
}
