package com.griefprevention.fabric;

import net.minecraft.server.players.NameAndId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricPlayerLookupTest
{
    @TempDir
    private Path serverDirectory;

    @Test
    void findsSeenPlayersIgnoringCaseAndPrefersTheMostRecentEntry() throws Exception
    {
        Path cache = writeCache("""
                [{"uuid":"5627dd98-e6be-3c21-b8a8-e92344183641","name":"Steve","expiresOn":"2026-10-26 21:25:40 -0700"},
                 {"uuid":"not-a-uuid","name":"Alex","expiresOn":"2026-10-26 21:25:40 -0700"},
                 {"uuid":"11111111-2222-3333-4444-555555555555","name":"alex","expiresOn":"2026-10-20 10:00:00 -0700"},
                 {"uuid":"99999999-8888-7777-6666-555555555555","name":"steve","expiresOn":"2026-09-01 10:00:00 -0700"}]
                """);

        NameAndId steve = FabricPlayerLookup.findInUserCache(cache, "STEVE");
        assertEquals(UUID.fromString("5627dd98-e6be-3c21-b8a8-e92344183641"), steve.id());
        assertEquals("Steve", steve.name());
        assertEquals(UUID.fromString("11111111-2222-3333-4444-555555555555"),
                FabricPlayerLookup.findInUserCache(cache, "Alex").id());
    }

    @Test
    void unknownNamesAndUnreadableCachesFindNobody() throws Exception
    {
        assertNull(FabricPlayerLookup.findInUserCache(this.serverDirectory.resolve("usercache.json"), "Steve"));

        Path cache = writeCache("[{\"uuid\":\"5627dd98-e6be-3c21-b8a8-e92344183641\",\"name\":\"Steve\"}]");
        assertNull(FabricPlayerLookup.findInUserCache(cache, "vip.builders"));

        Files.writeString(cache, "{not json", StandardCharsets.UTF_8);
        assertNull(FabricPlayerLookup.findInUserCache(cache, "Steve"));
    }

    private Path writeCache(String contents) throws Exception
    {
        Path cache = this.serverDirectory.resolve(FabricPlayerLookup.USER_CACHE_FILE);
        Files.writeString(cache, contents, StandardCharsets.UTF_8);
        return cache;
    }
}
