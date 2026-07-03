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
 * The combined startup text is built from localized messages (AuthorTag,
 * PluginTag, StartupDetails, BootFinished) and inserted via the
 * {@code ${startup}} placeholder.</p>
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

        // Try locale-specific entries
        List<String> localeEntries = entries.get(langCode);
        if (localeEntries == null || localeEntries.isEmpty()) {
            int underscore = langCode.indexOf('_');
            if (underscore > 0) {
                localeEntries = entries.get(langCode.substring(0, underscore));
            }
        }
        // Fall back to any available entry (locale-agnostic ASCII art)
        if (localeEntries == null || localeEntries.isEmpty()) {
            for (List<String> list : entries.values()) {
                if (list != null && !list.isEmpty()) {
                    localeEntries = list;
                    break;
                }
            }
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
     * Builds the combined ${startup} placeholder from localized messages.
     */
    private String applyReplacements(String header, String langCode) {
        String version = plugin.getDescription().getVersion();
        header = header.replace("${project.version}", version);
        header = header.replace("${version}", version);

        String startup = buildStartupBlock(langCode);
        header = header.replace("${startup}", startup);

        return header;
    }

    /**
     * Builds the combined startup block from locale messages.
     * Format: \n + AuthorTag + \n + PluginTag + \n + StartupDetails + \n + details body + \n + BootFinished
     */
    private String buildStartupBlock(String langCode) {
        String authorTag = getMessage(Messages.AuthorTag);
        String pluginTag = getMessage(Messages.PluginTag);
        String bootTime = "&a" + (System.currentTimeMillis() - plugin.startupStartTime) + "ms";
        String bootFinished = getMessage(Messages.BootFinished, bootTime);

        String detailsBody = buildDetailsBody(langCode);
        String startupDetails = getMessage(Messages.StartupDetails, "");

        StringBuilder sb = new StringBuilder();
        sb.append("\n&r\n");
        sb.append(authorTag).append("\n&r\n");
        sb.append(pluginTag).append("\n&r\n");
        sb.append(startupDetails).append("\n&r\n");
        sb.append(detailsBody).append("\n&r\n");
        sb.append(bootFinished);
        return sb.toString();
    }

    /**
     * Gets a localized message via DataStore, falling back to the Messages enum default.
     */
    private String getMessage(Messages message, String... args) {
        if (plugin.dataStore != null) {
            return plugin.dataStore.getMessage(message, args);
        }
        String value = message.defaultValue;
        for (int i = 0; i < args.length; i++) {
            value = value.replace("{" + i + "}", args[i] != null ? args[i] : "");
        }
        return value;
    }

    /**
     * Builds the startup detail block with platform, features, locale, storage info.
     */
    private String buildDetailsBody(String langCode) {
        String yes = "&aON";
        String no = "&cOFF";

        String storageType = "File";
        if (plugin.dataStore != null) {
            storageType = (plugin.dataStore instanceof FlatFileDataStore) ? "File" : "Database";
        }
        int msgCount = Messages.values().length;
        String localeInfo = plugin.dataStore != null
                ? plugin.dataStore.getMessage(Messages.MessagesLoaded, String.valueOf(msgCount))
                : "Messages loaded: " + msgCount;
        String localeCount = localeInfo.replaceAll("^[^:]*:\\s*", "");
        String claimsLoaded = String.valueOf(plugin.dataStore != null ? plugin.dataStore.getClaims().size() : 0);

        String sub3d = plugin.config_claims_allow3DSubdivisions ? yes : no;
        String admin3d = plugin.config_claims_allow3DAdminClaims ? yes : no;
        String nested = plugin.config_claims_allowNestedSubClaims ? yes : no;
        String shaped = plugin.config_claims_allowShapedClaims ? yes : no;

        String platformVersion = PlatformDetection.getServerVersion();

        StringBuilder details = new StringBuilder();
        details.append(formatLine("Platform:", "&a" + platformVersion, "", ""));
        details.append("\n");
        details.append(formatLine("3D Subdivisions:", "&a" + sub3d, "3D Admin Claims:", "&a" + admin3d));
        details.append("\n");
        details.append(formatLine("Nested Claims:", "&a" + nested, "Shaped Claims:", "&a" + shaped));
        details.append("\n");
        details.append(formatLine("Locale:", "&a" + langCode, "Messages loaded:", "&a" + localeCount));
        details.append("\n");
        details.append(formatLine("Storage:", "&a" + storageType, "Claims Loaded:", "&a" + claimsLoaded));

        return details.toString();
    }

    private static final int COL_WIDTH = 26;

    /**
     * Formats a two-column detail line with consistent alignment.
     */
    private static String formatLine(String leftLabel, String leftValue,
                                     String rightLabel, String rightValue) {
        String leftText = leftLabel + " " + leftValue;
        String paddedLeft = pad(leftText, COL_WIDTH);
        StringBuilder sb = new StringBuilder();
        sb.append("&7").append(paddedLeft);
        if (rightLabel != null && !rightLabel.isEmpty()) {
            sb.append(" &7").append(rightLabel).append(" ").append(rightValue);
        }
        return sb.toString();
    }

    /**
     * Pads a string to the specified width by appending spaces.
     * Color codes are excluded from width calculation.
     */
    private static String pad(String text, int width) {
        String stripped = text.replaceAll("[&§][0-9a-fk-orx]", "")
                              .replaceAll("&#[0-9a-fA-F]{6}", "")
                              .replaceAll("#[0-9a-fA-F]{6}", "");
        int padding = width - stripped.length();
        if (padding <= 0) return text;
        StringBuilder sb = new StringBuilder(text);
        for (int i = 0; i < padding; i++) sb.append(' ');
        return sb.toString();
    }
}
