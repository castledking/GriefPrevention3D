package com.griefprevention.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientProtocolLookupTest
{
    @Test
    void nullReturnsUnknown()
    {
        assertEquals(ClientProtocolLookup.UNKNOWN_PROTOCOL, ClientProtocolLookup.getProtocolVersion(null));
        assertFalse(ClientProtocolLookup.isPreGlazedTerracotta(null));
        assertTrue(ClientProtocolLookup.supportsBlockDisplay(null));
    }

    @Test
    void withoutViaVersionReportsUnknownAndPermissiveDefaults()
    {
        UUID uuid = UUID.randomUUID();
        assertEquals(ClientProtocolLookup.UNKNOWN_PROTOCOL, ClientProtocolLookup.getProtocolVersion(uuid));
        assertFalse(ClientProtocolLookup.isPreGlazedTerracotta(uuid));
        assertTrue(ClientProtocolLookup.supportsBlockDisplay(uuid));
    }
}
