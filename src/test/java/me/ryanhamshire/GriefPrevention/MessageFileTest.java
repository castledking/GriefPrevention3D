package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MessageFileTest {

    private static MessageFile parse(String contents) throws IOException {
        return MessageFile.load(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsUnquotedColorCodesLiterally() throws IOException {
        MessageFile file = parse("Messages:\n  IgnoringClaims: &1Now ignoring claims.\n");

        assertEquals("&1Now ignoring claims.", file.getString("Messages.IgnoringClaims", "missing"));
    }

    @Test
    void stripsSurroundingQuotes() throws IOException {
        MessageFile file = parse("Messages:\n"
                + "  Single: '&aYou don''t say.'\n"
                + "  Double: \"&aQuoted \\\"text\\\"\"\n");

        assertEquals("&aYou don't say.", file.getString("Messages.Single", "missing"));
        assertEquals("&aQuoted \"text\"", file.getString("Messages.Double", "missing"));
    }

    @Test
    void keepsHashesBackslashesAndColonsInValues() throws IOException {
        MessageFile file = parse("Messages:\n"
                + "  HexColor: &#ff0000Red text # not a comment\n"
                + "  HowToClaimRegex: (^|.*\\W)how\\W.*\\W(claim|protect|lock)(\\W.*|$)\n"
                + "  WithColon: Usage: /claim pvp [true|false]\n");

        assertEquals("&#ff0000Red text # not a comment", file.getString("Messages.HexColor", "missing"));
        assertEquals("(^|.*\\W)how\\W.*\\W(claim|protect|lock)(\\W.*|$)",
                file.getString("Messages.HowToClaimRegex", "missing"));
        assertEquals("Usage: /claim pvp [true|false]", file.getString("Messages.WithColon", "missing"));
    }

    @Test
    void joinsValuesWrappedOntoContinuationLines() throws IOException {
        MessageFile file = parse("Messages:\n"
                + "  Plain: You don't have permission to do that here, and this message is deliberately\n"
                + "    long enough to wrap.\n"
                + "  Quoted: '&aAdjusted {0}''s bonus claim blocks by {1}. New total bonus\n"
                + "    blocks: {2}.'\n"
                + "  After: still parsed\n");

        assertEquals("You don't have permission to do that here, and this message is deliberately"
                + " long enough to wrap.", file.getString("Messages.Plain", "missing"));
        assertEquals("&aAdjusted {0}'s bonus claim blocks by {1}. New total bonus blocks: {2}.",
                file.getString("Messages.Quoted", "missing"));
        assertEquals("still parsed", file.getString("Messages.After", "missing"));
    }

    @Test
    void collapsesDoubledApostrophesInUnquotedValues() throws IOException {
        MessageFile file = parse("Messages:\n"
                + "  Legacy: You don''t have permission to delete claims.\n"
                + "  Quoted: 'Revoked {0}''s access to this claim.'\n"
                + "  Escaped: 'Two in a row: '''' here.'\n");

        assertEquals("You don't have permission to delete claims.", file.getString("Messages.Legacy", "missing"));
        assertEquals("Revoked {0}'s access to this claim.", file.getString("Messages.Quoted", "missing"));
        assertEquals("Two in a row: '' here.", file.getString("Messages.Escaped", "missing"));
    }

    @Test
    void ignoresExtraSpacesBetweenTheColonAndTheValue() throws IOException {
        // some hand-edited locale files line their values up in a column
        MessageFile file = parse("Messages:\n"
                + "  Padded:   Place a chest to claim land.\n"
                + "  PaddedQuoted:   '  leading spaces kept inside quotes'\n");

        assertEquals("Place a chest to claim land.", file.getString("Messages.Padded", "missing"));
        assertEquals("  leading spaces kept inside quotes", file.getString("Messages.PaddedQuoted", "missing"));
    }

    @Test
    void indentedCommentEndsAValueButNotAWrappedQuotedOne() throws IOException {
        MessageFile file = parse("Messages:\n"
                + "  Plain: a plain value\n"
                + "    # an indented comment\n"
                + "  Quoted: 'a quoted value that wraps\n"
                + "    #ff0000 is part of it'\n");

        assertEquals("a plain value", file.getString("Messages.Plain", "missing"));
        assertEquals("a quoted value that wraps #ff0000 is part of it",
                file.getString("Messages.Quoted", "missing"));
    }

    @Test
    void readsBackEveryValueBukkitWouldHaveWritten(@TempDir File tempDir) throws Exception {
        // the old loader re-saved message files through Bukkit's YAML, which wraps anything past
        // ~80 characters onto continuation lines and single-quotes values containing apostrophes
        MessageFile bundled = MessageFile.load(new File("src/main/resources/messages_en.yml"));

        YamlConfiguration config = new YamlConfiguration();
        for (String key : bundled.keys()) {
            config.set(key, bundled.getString(key, ""));
        }

        File saved = new File(tempDir, "messages.yml");
        config.save(saved);

        MessageFile reloaded = MessageFile.load(saved);
        for (String key : bundled.keys()) {
            assertEquals(bundled.getString(key, ""), reloaded.getString(key, "!missing!"), key);
        }
    }

    @Test
    void ignoresCommentsAndBlankLines() throws IOException {
        MessageFile file = parse("# header\n\nMessages:\n  # a note\n  Present: value\n");

        assertTrue(file.contains("Messages.Present"));
        assertFalse(file.contains("Messages.Missing"));
        assertEquals("fallback", file.getString("Messages.Missing", "fallback"));
    }

    @Test
    void readsLegacyNestedTextKeys() throws IOException {
        MessageFile file = parse("Messages:\n  Legacy:\n    Text: &6Legacy text\n    Notes: 0: something\n");

        assertEquals("&6Legacy text", file.getString("Messages.Legacy.Text", "missing"));
    }

    @Test
    void savingOnlyAppendsMissingKeys(@TempDir File tempDir) throws IOException {
        File file = new File(tempDir, "messages.yml");
        String original = "# my header\nMessages:\n  Kept: &1Now ignoring claims.\n  AlsoKept: 'quoted &2value'\n";
        Files.write(file.toPath(), original.getBytes(StandardCharsets.UTF_8));

        MessageFile messageFile = MessageFile.load(file);
        messageFile.set("Messages.Added", "&aBrand new message.", "0: player name");
        messageFile.save(file);

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        assertEquals("# my header", lines.get(0));
        assertEquals("Messages:", lines.get(1));
        assertEquals("  Kept: &1Now ignoring claims.", lines.get(2));
        assertEquals("  AlsoKept: 'quoted &2value'", lines.get(3));
        assertEquals("  # 0: player name", lines.get(4));
        assertEquals("  Added: '&aBrand new message.'", lines.get(5));

        // the appended value survives a round trip
        MessageFile reloaded = MessageFile.load(file);
        assertEquals("&aBrand new message.", reloaded.getString("Messages.Added", "missing"));
        assertEquals("&1Now ignoring claims.", reloaded.getString("Messages.Kept", "missing"));
    }

    @Test
    void appendsAfterAValueThatWraps(@TempDir File tempDir) throws IOException {
        File file = new File(tempDir, "messages.yml");
        String original = "Messages:\n"
                + "  Wrapped: You don't have permission to do that here, and this message is\n"
                + "    deliberately long enough to wrap.\n";
        Files.write(file.toPath(), original.getBytes(StandardCharsets.UTF_8));

        MessageFile messageFile = MessageFile.load(file);
        messageFile.set("Messages.Added", "&aBrand new message.", null);
        messageFile.save(file);

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        assertEquals("    deliberately long enough to wrap.", lines.get(2));
        assertEquals("  Added: '&aBrand new message.'", lines.get(3));

        MessageFile reloaded = MessageFile.load(file);
        assertEquals("You don't have permission to do that here, and this message is deliberately"
                + " long enough to wrap.", reloaded.getString("Messages.Wrapped", "missing"));
        assertEquals("&aBrand new message.", reloaded.getString("Messages.Added", "missing"));
    }

    @Test
    void savingIsSkippedWhenNothingIsMissing(@TempDir File tempDir) throws IOException {
        File file = new File(tempDir, "messages.yml");
        String original = "Messages:\n  Kept: &1Now ignoring claims.\n";
        Files.write(file.toPath(), original.getBytes(StandardCharsets.UTF_8));

        MessageFile messageFile = MessageFile.load(file);
        assertFalse(messageFile.hasPendingAdditions());
        messageFile.save(file);

        assertEquals(original, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    void createsMissingSectionWhenAppending(@TempDir File tempDir) throws IOException {
        File file = new File(tempDir, "messages.yml");
        Files.write(file.toPath(), "# only a header\n".getBytes(StandardCharsets.UTF_8));

        MessageFile messageFile = MessageFile.load(file);
        messageFile.set("Messages.Added", "&aBrand new message.", null);
        messageFile.save(file);

        MessageFile reloaded = MessageFile.load(file);
        assertEquals("&aBrand new message.", reloaded.getString("Messages.Added", "missing"));
    }

    @Test
    void bundledLocaleFilesParseIntoKnownMessages() throws IOException {
        Set<String> known = new HashSet<>();
        for (Messages message : Messages.values()) {
            known.add("Messages." + message.name());
        }

        for (String locale : MessageLocalization.SUPPORTED_LOCALE_CODES) {
            File resource = new File("src/main/resources/messages_" + locale + ".yml");
            assertTrue(resource.exists(), "missing bundled file for " + locale);

            MessageFile file = MessageFile.load(resource);
            Collection<String> keys = file.keys();
            assertTrue(keys.size() > 300, locale + " only parsed " + keys.size() + " messages");

            for (String key : keys) {
                assertTrue(known.contains(key), locale + " has unknown key " + key);

                String value = file.getString(key, "");
                assertFalse(value.startsWith("'") && value.endsWith("'"),
                        locale + " kept quotes around " + key);
            }
        }
    }
}
