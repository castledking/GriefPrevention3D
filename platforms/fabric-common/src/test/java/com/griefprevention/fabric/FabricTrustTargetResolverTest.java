package com.griefprevention.fabric;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricTrustTargetResolverTest
{
    private static final UUID HISTORICAL_PLAYER =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void previouslySeenPlayerNameResolvesBeforePermissionFallback()
    {
        assertEquals(
                HISTORICAL_PLAYER.toString(),
                FabricTrustTargetResolver.resolve(
                        "Ze_Flash",
                        name -> "Ze_Flash".equalsIgnoreCase(name) ? HISTORICAL_PLAYER : null
                )
        );
    }

    @Test
    void unresolvedSimpleNameFailsWhileDottedOrBracketedTargetsBecomePermissions()
    {
        assertNull(FabricTrustTargetResolver.resolve("NeverJoined", name -> null));
        assertEquals(
                "[gp3d.test.container]",
                FabricTrustTargetResolver.resolve("gp3d.test.container", name -> null)
        );
        assertEquals(
                "[gp3d.test.container]",
                FabricTrustTargetResolver.resolve("[GP3D.TEST.CONTAINER]", name -> null)
        );
        assertNull(FabricTrustTargetResolver.resolve("[broken", name -> null));
    }
}
