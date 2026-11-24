package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * NeoForge bus events fired for claim lifecycle changes, mirroring Fabric's {@code Claim*Callback}
 * hooks and Paper's Bukkit claim events.
 */
public final class NeoForgeClaimEvents
{
    private NeoForgeClaimEvents()
    {
    }

    public static final class ClaimCreatedEvent extends Event
    {
        private final ClaimSnapshot claim;
        private final @Nullable ServerPlayer player;

        public ClaimCreatedEvent(@NotNull ClaimSnapshot claim, @Nullable ServerPlayer player)
        {
            this.claim = claim;
            this.player = player;
        }

        public @NotNull ClaimSnapshot getClaim()
        {
            return this.claim;
        }

        public @Nullable ServerPlayer getPlayer()
        {
            return this.player;
        }
    }

    public static final class ClaimModifiedEvent extends Event
    {
        private final ClaimSnapshot previous;
        private final ClaimSnapshot updated;
        private final @Nullable ServerPlayer player;

        public ClaimModifiedEvent(
                @NotNull ClaimSnapshot previous,
                @NotNull ClaimSnapshot updated,
                @Nullable ServerPlayer player)
        {
            this.previous = previous;
            this.updated = updated;
            this.player = player;
        }

        public @NotNull ClaimSnapshot getPreviousClaim()
        {
            return this.previous;
        }

        public @NotNull ClaimSnapshot getUpdatedClaim()
        {
            return this.updated;
        }

        public @Nullable ServerPlayer getPlayer()
        {
            return this.player;
        }
    }

    public static final class ClaimDeletedEvent extends Event
    {
        private final ClaimSnapshot claim;
        private final @Nullable ServerPlayer player;

        public ClaimDeletedEvent(@NotNull ClaimSnapshot claim, @Nullable ServerPlayer player)
        {
            this.claim = claim;
            this.player = player;
        }

        public @NotNull ClaimSnapshot getClaim()
        {
            return this.claim;
        }

        public @Nullable ServerPlayer getPlayer()
        {
            return this.player;
        }
    }

    public static final class ClaimTransferredEvent extends Event
    {
        private final ClaimSnapshot claim;
        private final @Nullable UUID newOwner;
        private final @Nullable ServerPlayer player;

        public ClaimTransferredEvent(
                @NotNull ClaimSnapshot claim,
                @Nullable UUID newOwner,
                @Nullable ServerPlayer player)
        {
            this.claim = claim;
            this.newOwner = newOwner;
            this.player = player;
        }

        public @NotNull ClaimSnapshot getClaim()
        {
            return this.claim;
        }

        public @Nullable UUID getNewOwner()
        {
            return this.newOwner;
        }

        public @Nullable ServerPlayer getPlayer()
        {
            return this.player;
        }
    }

    public static final class ClaimExpirationEvent extends Event implements ICancellableEvent
    {
        private final ClaimSnapshot claim;

        public ClaimExpirationEvent(@NotNull ClaimSnapshot claim)
        {
            this.claim = claim;
        }

        public @NotNull ClaimSnapshot getClaim()
        {
            return this.claim;
        }
    }
}
