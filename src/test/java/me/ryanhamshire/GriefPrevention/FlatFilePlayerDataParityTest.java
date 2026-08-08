package me.ryanhamshire.GriefPrevention;

import com.griefprevention.persistence.PlayerDataFormatException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlatFilePlayerDataParityTest
{
    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void paperReadsAndWritesTheSharedUpstreamPlayerDataShape() throws Exception
    {
        PlayerData playerData = FlatFileDataStore.decodePlayerData(PLAYER, "\n1200\n300\n\n");

        assertEquals(PLAYER, playerData.playerID);
        assertEquals(1200, playerData.getAccruedClaimBlocks());
        assertEquals(300, playerData.getBonusClaimBlocks());
        assertEquals("\n1200\n300\n\n", FlatFileDataStore.encodePlayerData(playerData));
    }

    @Test
    void paperRejectsTheSameMalformedRecordAsFabric()
    {
        assertThrows(
                PlayerDataFormatException.class,
                () -> FlatFileDataStore.decodePlayerData(PLAYER, "\nnot-a-number\n300\n")
        );
    }
}
