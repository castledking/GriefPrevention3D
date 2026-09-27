package com.griefprevention.fabric;

import com.griefprevention.messages.MessageCatalog;
import com.griefprevention.messages.MessageCatalogCodec;
import com.griefprevention.messages.MessageCatalogException;
import com.griefprevention.messages.MessageKey;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Reads player-facing text from the shared {@code messages.yml}.
 *
 * <p>Unlike claim data, an unreadable messages file is not worth failing startup over: every key
 * falls back to the Paper default, so protection still explains itself in English.
 */
final class FabricMessages
{
    private final MessageCatalogCodec codec = new MessageCatalogCodec();
    private final Path messagesFile;
    private final Logger logger;

    private volatile MessageCatalog catalog = MessageCatalog.empty();

    FabricMessages(@NotNull Path dataFolder, @NotNull Logger logger)
    {
        this.messagesFile = dataFolder.resolve("messages.yml");
        this.logger = logger;
        reload();
    }

    void reload()
    {
        try
        {
            this.catalog = this.codec.decode(Files.readString(this.messagesFile, StandardCharsets.UTF_8));
        }
        catch (NoSuchFileException exception)
        {
            this.catalog = MessageCatalog.empty();
        }
        catch (IOException | MessageCatalogException exception)
        {
            this.logger.warn(
                    "Could not read Fabric messages from {}; using built-in defaults.",
                    this.messagesFile,
                    exception
            );
            this.catalog = MessageCatalog.empty();
        }
    }

    @NotNull String format(@NotNull MessageKey key, @NotNull String @NotNull... args)
    {
        return this.catalog.format(key, args);
    }
}
