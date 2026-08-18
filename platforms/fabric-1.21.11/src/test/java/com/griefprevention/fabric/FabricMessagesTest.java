package com.griefprevention.fabric;

import com.griefprevention.messages.LegacyText;
import com.griefprevention.messages.MessageKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FabricMessagesTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricMessagesTest.class);

    @TempDir
    Path dataFolder;

    @Test
    void usesConfiguredText() throws IOException
    {
        write("Messages:\n  NoBuildPermission: \"{0} owns this.\"\n");

        assertEquals(
                "Notch owns this.",
                new FabricMessages(this.dataFolder, LOGGER).format(MessageKey.NO_BUILD_PERMISSION, "Notch")
        );
    }

    @Test
    void missingFileFallsBackToPaperDefaults()
    {
        assertEquals(
                "You don't have Notch's permission to build here.",
                new FabricMessages(this.dataFolder, LOGGER).format(MessageKey.NO_BUILD_PERMISSION, "Notch")
        );
    }

    @Test
    void unreadableFileFallsBackToPaperDefaults() throws IOException
    {
        write("Messages:\n  - this is not a mapping\n");

        assertEquals(
                "You don't have Notch's permission to build here.",
                new FabricMessages(this.dataFolder, LOGGER).format(MessageKey.NO_BUILD_PERMISSION, "Notch")
        );
    }

    @Test
    void reloadPicksUpEdits() throws IOException
    {
        write("Messages:\n  NoAccessPermission: \"first\"\n");
        FabricMessages messages = new FabricMessages(this.dataFolder, LOGGER);
        assertEquals("first", messages.format(MessageKey.NO_ACCESS_PERMISSION));

        write("Messages:\n  NoAccessPermission: \"second\"\n");
        messages.reload();

        assertEquals("second", messages.format(MessageKey.NO_ACCESS_PERMISSION));
    }

    @Test
    void translatesPaperColorCodesAndNewlines() throws IOException
    {
        // Single-quoted so the \n reaches the message layer as the literal escape Paper expands.
        write("Messages:\n  NoBuildPermission: '&cStop, {0}!\\nGo away.'\n");

        assertEquals(
                LegacyText.SECTION + "cStop, Notch!\nGo away.",
                new FabricMessages(this.dataFolder, LOGGER).format(MessageKey.NO_BUILD_PERMISSION, "Notch")
        );
    }

    private void write(String contents) throws IOException
    {
        Files.write(this.dataFolder.resolve("messages.yml"), contents.getBytes(StandardCharsets.UTF_8));
    }
}
