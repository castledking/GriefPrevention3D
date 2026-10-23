package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricOfflineOperatorsTest
{
    private static final UUID ADMIN = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MODERATOR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @TempDir
    Path folder;

    @Test
    void readsOperatorsAtTheGameMasterLevelFromOpsJson() throws Exception
    {
        Path ops = this.folder.resolve(FabricOfflineOperators.OPS_FILE);
        Files.writeString(ops, "[\n"
                + "  {\"uuid\": \"" + ADMIN + "\", \"name\": \"Admin\", \"level\": 4, \"bypassesPlayerLimit\": false},\n"
                + "  {\"uuid\": \"" + MODERATOR + "\", \"name\": \"Mod\", \"level\": 1, \"bypassesPlayerLimit\": false}\n"
                + "]\n", StandardCharsets.UTF_8);

        assertTrue(FabricOfflineOperators.isListedOperator(ops, ADMIN));
        // Level 1 only bypasses spawn protection; it is not the level an online op check asks for.
        assertFalse(FabricOfflineOperators.isListedOperator(ops, MODERATOR));
        assertFalse(FabricOfflineOperators.isListedOperator(ops, PLAYER));
    }

    @Test
    void aMissingOrBrokenFileListsNobody() throws Exception
    {
        Path ops = this.folder.resolve(FabricOfflineOperators.OPS_FILE);
        assertFalse(FabricOfflineOperators.isListedOperator(ops, ADMIN));

        Files.writeString(ops, "{ not json", StandardCharsets.UTF_8);
        assertFalse(FabricOfflineOperators.isListedOperator(ops, ADMIN));
    }

    @Test
    void formatsDurationsAsPaperDoes()
    {
        assertEquals("1 second", FabricClaimExpiration.friendlyDuration(1_000L));
        assertEquals("2 hours, 5 minutes", FabricClaimExpiration.friendlyDuration((2 * 60 + 5) * 60_000L));
        assertEquals("60 days", FabricClaimExpiration.friendlyDuration(60L * 24 * 60 * 60_000L));
        assertEquals("3 days, 1 hour", FabricClaimExpiration.friendlyDuration((3 * 24 + 1) * 60L * 60_000L));
    }
}
