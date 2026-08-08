package com.griefprevention.persistence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerDataDocumentCodecTest
{
    private final PlayerDataDocumentCodec codec = new PlayerDataDocumentCodec();

    @Test
    void decodesTheUpstreamFourLineFormat() throws Exception
    {
        PlayerDataDocument document = this.codec.decode("\n1200\n300\n\n");

        assertEquals(new PlayerDataDocument(1200, 300), document);
    }

    @Test
    void acceptsLegacyFirstAndTrailingLinesWithoutInterpretingThem() throws Exception
    {
        PlayerDataDocument document = this.codec.decode(
                "2014.01.02.03.04.05\r\n88\r\n12\r\nlegacy-claim-list\r\naddon-data\r\n"
        );

        assertEquals(new PlayerDataDocument(88, 12), document);
    }

    @Test
    void encodesTheCanonicalBukkitShape()
    {
        assertEquals("\n88\n12\n\n", this.codec.encode(new PlayerDataDocument(88, 12)));
    }

    @Test
    void rejectsIncompleteOrNonNumericBalances()
    {
        assertThrows(PlayerDataFormatException.class, () -> this.codec.decode("\n100\n"));
        assertThrows(PlayerDataFormatException.class, () -> this.codec.decode("\none hundred\n0\n"));
        assertThrows(PlayerDataFormatException.class, () -> this.codec.decode("\n100\n0.5\n"));
    }
}
