package com.griefprevention.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientVersionCompatTest {

    @Test
    void clientsOlderThan1_19_4DoNotGetBlockDisplays() {
        // ViaBackwards turns block displays into armor stands for these clients, which show up as
        // blocks nudged out of alignment.
        assertFalse(ClientVersionCompat.supportsBlockDisplay(761)); // 1.19.3
        assertFalse(ClientVersionCompat.supportsBlockDisplay(760)); // 1.19.2
        assertFalse(ClientVersionCompat.supportsBlockDisplay(47));  // 1.8.x
        assertFalse(ClientVersionCompat.supportsBlockDisplay(1));
    }

    @Test
    void clientsFrom1_19_4OnwardsGetBlockDisplays() {
        assertTrue(ClientVersionCompat.supportsBlockDisplay(ClientVersionCompat.BLOCK_DISPLAY_PROTOCOL)); // 1.19.4
        assertTrue(ClientVersionCompat.supportsBlockDisplay(763)); // 1.20
        assertTrue(ClientVersionCompat.supportsBlockDisplay(775));
    }

    @Test
    void anUnknownVersionIsTreatedAsMatchingTheServer() {
        // No ViaVersion, or a player it is not tracking: the client speaks the server's protocol, so
        // the server's own capability check decides.
        assertTrue(ClientVersionCompat.supportsBlockDisplay(ClientVersionCompat.UNKNOWN_PROTOCOL));
    }

    @Test
    void aNullPlayerHasNoKnownVersion() {
        assertEquals(ClientVersionCompat.UNKNOWN_PROTOCOL, ClientVersionCompat.protocolVersion(null));
    }
}
