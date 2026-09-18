package me.ryanhamshire.GriefPrevention;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorMessageRateLimitTest {
    private static final UUID PLAYER = UUID.fromString("fa8d60a7-9645-4a9f-b74d-173966174739");
    private static final UUID OTHER_PLAYER = UUID.fromString("1f4e2c58-0c1f-4b44-9d48-2a0f0d4b1f11");
    private static final long COOLDOWN_MS = 10_000L;

    @Test
    void repeatedMessageIsSuppressedWithinTheWindow() {
        long now = 1_000_000L;

        assertTrue(GriefPrevention.allowErrorMessage(PLAYER, "NoBuildPermission", now));
        assertFalse(GriefPrevention.allowErrorMessage(PLAYER, "NoBuildPermission", now + 1));
        assertFalse(GriefPrevention.allowErrorMessage(PLAYER, "NoBuildPermission", now + COOLDOWN_MS - 1));

        GriefPrevention.clearErrorMessageCooldowns(PLAYER);
    }

    @Test
    void repeatedMessageIsSentAgainAfterTheWindow() {
        long now = 2_000_000L;

        assertTrue(GriefPrevention.allowErrorMessage(PLAYER, "NoBuildPermission", now));
        assertTrue(GriefPrevention.allowErrorMessage(PLAYER, "NoBuildPermission", now + COOLDOWN_MS));

        GriefPrevention.clearErrorMessageCooldowns(PLAYER);
    }

    @Test
    void aThrottledMessageDoesNotSilenceADifferentOne() {
        // The reason cooldowns are keyed by message: a player denied at a claim border should still
        // be told why the next, unrelated action failed.
        long now = 3_000_000L;

        assertTrue(GriefPrevention.allowErrorMessage(PLAYER, "NoBuildPermission", now));
        assertTrue(GriefPrevention.allowErrorMessage(PLAYER, "CantFightWhileImmune", now + 1));
        assertTrue(GriefPrevention.allowErrorMessage(PLAYER, "NoDamageClaimedEntity", now + 2));
        assertFalse(GriefPrevention.allowErrorMessage(PLAYER, "CantFightWhileImmune", now + 3));

        GriefPrevention.clearErrorMessageCooldowns(PLAYER);
    }

    @Test
    void cooldownsAreTrackedPerPlayer() {
        long now = 4_000_000L;

        assertTrue(GriefPrevention.allowErrorMessage(PLAYER, "NoBuildPermission", now));
        assertTrue(GriefPrevention.allowErrorMessage(OTHER_PLAYER, "NoBuildPermission", now + 1));

        GriefPrevention.clearErrorMessageCooldowns(PLAYER);
        GriefPrevention.clearErrorMessageCooldowns(OTHER_PLAYER);
    }

    @Test
    void clearingCooldownsLetsTheNextMessageThrough() {
        long now = 5_000_000L;

        assertTrue(GriefPrevention.allowErrorMessage(PLAYER, "NoBuildPermission", now));

        GriefPrevention.clearErrorMessageCooldowns(PLAYER);

        assertTrue(GriefPrevention.allowErrorMessage(PLAYER, "NoBuildPermission", now + 1));

        GriefPrevention.clearErrorMessageCooldowns(PLAYER);
    }

    @Test
    void lapsedEntriesDoNotAccumulate() {
        // Denials built from a resolved string are keyed by their text, so a claim owner's name or a
        // size in the message would otherwise leave an entry behind for the rest of the session.
        long now = 6_000_000L;

        for (int i = 0; i < 1000; i++) {
            assertTrue(GriefPrevention.allowErrorMessage(PLAYER, "That belongs to player" + i, now + i * COOLDOWN_MS));
        }

        // Only entries inside the window survive: the last message, and nothing older.
        assertTrue(GriefPrevention.errorMessageCooldownCount(PLAYER) <= 2);

        GriefPrevention.clearErrorMessageCooldowns(PLAYER);
    }

    @Test
    void defaultActionBarMessagesAreRealMessageIds() {
        // The list is matched against Messages#name, so a typo would silently send to chat.
        for (String name : GriefPrevention.DEFAULT_ACTION_BAR_MESSAGES) {
            assertDoesNotThrow(() -> Messages.valueOf(name), name + " is not a Messages constant");
        }
    }

    @Test
    void repeatableWarningsRenderInTheActionBarByDefault() {
        assertTrue(GriefPrevention.DEFAULT_ACTION_BAR_MESSAGES.contains("NoPistonsOutsideClaims"));
        assertTrue(GriefPrevention.DEFAULT_ACTION_BAR_MESSAGES.contains("TooDeepToClaim"));
        assertTrue(GriefPrevention.DEFAULT_ACTION_BAR_MESSAGES.contains("NoEnoughBlocksForChestClaim"));
    }
}
