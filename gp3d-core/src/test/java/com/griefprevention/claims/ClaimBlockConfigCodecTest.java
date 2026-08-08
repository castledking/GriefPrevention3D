package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClaimBlockConfigCodecTest
{
    private final ClaimBlockConfigCodec codec = new ClaimBlockConfigCodec();

    @Test
    void readsInitialBlocksFromTheUpstreamPath() throws Exception
    {
        ClaimBlockSettings settings = this.codec.decode(
                "GriefPrevention:\n  Claims:\n    InitialBlocks: 450\n"
        );

        assertEquals(450, settings.initialBlocks());
    }

    @Test
    void usesTheUpstreamDefaultWhenTheOptionIsMissing() throws Exception
    {
        assertEquals(100, this.codec.decode("GriefPrevention: {}\n").initialBlocks());
        assertEquals(100, this.codec.decode("").initialBlocks());
    }

    @Test
    void rejectsMalformedValuesAndDuplicateYamlKeys()
    {
        assertThrows(ClaimBlockConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  Claims:\n    InitialBlocks: lots\n"
        ));
        assertThrows(ClaimBlockConfigException.class, () -> this.codec.decode(
                "GriefPrevention:\n  Claims:\n    InitialBlocks: 100\n    InitialBlocks: 200\n"
        ));
    }
}
