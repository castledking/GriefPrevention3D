package com.griefprevention.fabric;

import com.griefprevention.messages.MessageKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricRestoreNatureTest
{
    @Test
    void restoreModesTransitionProperly()
    {
        FabricClaimModes modes = new FabricClaimModes();
        UUID playerId = UUID.randomUUID();

        assertEquals(FabricClaimModes.Mode.BASIC, modes.mode(playerId));
        assertFalse(modes.mode(playerId).isRestoreNature());

        modes.set(playerId, FabricClaimModes.Mode.RESTORE_NATURE);
        assertEquals(FabricClaimModes.Mode.RESTORE_NATURE, modes.mode(playerId));
        assertTrue(modes.mode(playerId).isRestoreNature());

        modes.set(playerId, FabricClaimModes.Mode.RESTORE_NATURE_AGGRESSIVE);
        assertEquals(FabricClaimModes.Mode.RESTORE_NATURE_AGGRESSIVE, modes.mode(playerId));
        assertTrue(modes.mode(playerId).isRestoreNature());

        modes.set(playerId, FabricClaimModes.Mode.RESTORE_NATURE_FILL);
        assertEquals(FabricClaimModes.Mode.RESTORE_NATURE_FILL, modes.mode(playerId));
        assertTrue(modes.mode(playerId).isRestoreNature());

        // Basic claims mode resets back to basic
        modes.set(playerId, FabricClaimModes.Mode.BASIC);
        assertEquals(FabricClaimModes.Mode.BASIC, modes.mode(playerId));
        assertFalse(modes.mode(playerId).isRestoreNature());
    }

    @Test
    void restoreNatureMessageKeysExist()
    {
        assertNotNull(MessageKey.RESTORE_NATURE_ACTIVATE);
        assertNotNull(MessageKey.RESTORE_NATURE_AGGRESSIVE_ACTIVATE);
        assertNotNull(MessageKey.FILL_MODE_ACTIVE);

        assertEquals("RestoreNatureActivate", MessageKey.RESTORE_NATURE_ACTIVATE.key());
        assertEquals("RestoreNatureAggressiveActivate", MessageKey.RESTORE_NATURE_AGGRESSIVE_ACTIVATE.key());
        assertEquals("FillModeActive", MessageKey.FILL_MODE_ACTIVE.key());
    }

    @Test
    void restoreNaturePermissionsExist()
    {
        assertEquals("griefprevention.restorenature", FabricPermissionDefaults.RESTORE_NATURE);
        assertEquals("griefprevention.restorenatureaggressive", FabricPermissionDefaults.RESTORE_NATURE_AGGRESSIVE);
    }
}
