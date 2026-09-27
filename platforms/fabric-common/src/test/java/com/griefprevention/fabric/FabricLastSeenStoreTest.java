package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricLastSeenStoreTest
{
    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID OTHER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @TempDir
    private Path dataFolder;

    @Test
    void recordedTimesSurviveARestart()
    {
        FabricLastSeenStore store = new FabricLastSeenStore(this.dataFolder, NOPLogger.NOP_LOGGER);
        assertNull(store.lastSeen(PLAYER));

        store.record(PLAYER, 1_700_000_000_000L);
        store.record(OTHER, 1_600_000_000_000L);
        store.record(PLAYER, 1_750_000_000_000L);
        store.flush();

        FabricLastSeenStore reloaded = new FabricLastSeenStore(this.dataFolder, NOPLogger.NOP_LOGGER);
        assertEquals(1_750_000_000_000L, reloaded.lastSeen(PLAYER));
        assertEquals(1_600_000_000_000L, reloaded.lastSeen(OTHER));
    }

    @Test
    void flushingWithoutChangesWritesNothing()
    {
        new FabricLastSeenStore(this.dataFolder, NOPLogger.NOP_LOGGER).flush();

        assertFalse(Files.exists(this.dataFolder.resolve(FabricLastSeenStore.FILE_NAME)));
    }

    @Test
    void unreadableLinesAreSkipped() throws Exception
    {
        Files.writeString(this.dataFolder.resolve(FabricLastSeenStore.FILE_NAME), """
                # comment
                not-a-uuid: 5
                aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee: soon
                '11111111-2222-3333-4444-555555555555': 42
                """, StandardCharsets.UTF_8);

        FabricLastSeenStore store = new FabricLastSeenStore(this.dataFolder, NOPLogger.NOP_LOGGER);

        assertNull(store.lastSeen(PLAYER));
        assertEquals(42L, store.lastSeen(OTHER));
    }
}
