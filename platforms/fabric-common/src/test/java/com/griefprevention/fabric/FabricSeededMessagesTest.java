package com.griefprevention.fabric;

import com.griefprevention.messages.MessageCatalog;
import com.griefprevention.messages.MessageCatalogCodec;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The seeded messages.yml is what operators edit, so every key the mod reads must be present in it
 * and must carry the same text as the Paper default it mirrors.
 */
class FabricSeededMessagesTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricSeededMessagesTest.class);

    @TempDir
    Path dataFolder;

    @Test
    void seedsEveryMessageKeyWithItsPaperDefault() throws Exception
    {
        MessageCatalog seeded = seededCatalog();

        for (MessageKey key : MessageKey.values())
        {
            assertNotNull(seeded.template(key.key()), "messages.yml is missing " + key.key());
            assertEquals(key.defaultValue(), seeded.template(key.key()), "text differs for " + key.key());
        }
    }

    @Test
    void seedingIsIdempotent() throws IOException
    {
        FabricDataFolder.ensureDefaults(this.dataFolder, LOGGER);
        String first = Files.readString(this.dataFolder.resolve("messages.yml"), StandardCharsets.UTF_8);

        FabricDataFolder.ensureDefaults(this.dataFolder, LOGGER);

        assertEquals(
                first,
                Files.readString(this.dataFolder.resolve("messages.yml"), StandardCharsets.UTF_8)
        );
    }

    private MessageCatalog seededCatalog() throws Exception
    {
        FabricDataFolder.ensureDefaults(this.dataFolder, LOGGER);
        return new MessageCatalogCodec().decode(
                Files.readString(this.dataFolder.resolve("messages.yml"), StandardCharsets.UTF_8)
        );
    }
}
