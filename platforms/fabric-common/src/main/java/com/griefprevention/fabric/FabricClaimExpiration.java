package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimExpirationPolicy;
import com.griefprevention.claims.ClaimExpirationSettings;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Deletes the claims of players who stopped playing, as Paper's {@code FindUnusedClaimsTask}: one claim
 * owner is checked every {@code Advanced.ClaimExpirationCheckRate} seconds, in a shuffled order that starts
 * over once everyone has been checked. {@link ClaimExpirationPolicy} decides; this checks
 * {@code griefprevention.dontexpire} for the owners it would expire, and deletes on the server thread.
 *
 * <p>When an owner was last online comes from {@link FabricLastSeenStore}. Owners it has never recorded
 * keep their claims, as Paper keeps those of players without a last-played time.
 */
final class FabricClaimExpiration
{
    private static final int TICKS_PER_SECOND = 20;
    /** Paper waits a minute after startup before the first check. */
    private static final long FIRST_CHECK_DELAY_TICKS = 60L * TICKS_PER_SECOND;

    private final FabricClaimRepository claims;
    private final FabricSettings settings;
    private final FabricLastSeenStore lastSeen;
    private final Logger logger;
    /** Owners whose dontexpire lookup is still running, so a slow lookup is never started twice. */
    private final Set<UUID> pending = Collections.synchronizedSet(new HashSet<>());
    private @NotNull Iterator<UUID> owners = Collections.emptyIterator();
    private boolean ownersLoaded;
    private long tick;
    private long nextCheckTick = FIRST_CHECK_DELAY_TICKS;

    FabricClaimExpiration(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricSettings settings,
            @NotNull FabricLastSeenStore lastSeen,
            @NotNull Logger logger)
    {
        this.claims = claims;
        this.settings = settings;
        this.lastSeen = lastSeen;
        this.logger = logger;
    }

    void register()
    {
        ServerTickEvents.END_SERVER_TICK.register(this::onTick);
    }

    // ---------------------------------------------------------------------------------------------
    // The recurring check
    // ---------------------------------------------------------------------------------------------

    private void onTick(@NotNull MinecraftServer server)
    {
        this.tick++;
        if (this.tick < this.nextCheckTick)
        {
            return;
        }
        ClaimExpirationSettings expiration = this.settings.expiration();
        this.nextCheckTick = this.tick + (long) expiration.checkRateSeconds() * TICKS_PER_SECOND;
        if (!expiration.enabled())
        {
            return;
        }

        // As on Paper, the check that finds the list used up only starts it over.
        if (!this.ownersLoaded || !this.owners.hasNext())
        {
            this.owners = shuffledOwners().iterator();
            this.ownersLoaded = true;
            return;
        }
        check(server, this.owners.next());
    }

    private @NotNull List<UUID> shuffledOwners()
    {
        Set<UUID> owners = new LinkedHashSet<>();
        for (ClaimSnapshot claim : this.claims.snapshots())
        {
            if (claim.ownerId() != null && claim.parentId() == null && !claim.subdivision())
            {
                owners.add(claim.ownerId());
            }
        }
        List<UUID> shuffled = new ArrayList<>(owners);
        Collections.shuffle(shuffled);
        this.logger.debug("Checking {} claim owners for inactivity.", shuffled.size());
        return shuffled;
    }

    private void check(@NotNull MinecraftServer server, @NotNull UUID ownerId)
    {
        if (this.pending.contains(ownerId))
        {
            return;
        }
        Assessment assessment = assess(server, ownerId);
        if (assessment == null || !assessment.decision.expires())
        {
            this.logger.debug("Claims of {} stay: {}.", ownerId,
                    assessment == null ? "unreadable claim blocks" : assessment.decision.reason());
            return;
        }

        this.pending.add(ownerId);
        whenExempt(server, ownerId, exempt -> {
            this.pending.remove(ownerId);
            if (exempt)
            {
                this.logger.debug("Claims of {} stay: griefprevention.dontexpire.", ownerId);
                return;
            }
            // The owner may have come back, or their claims changed, while the permission was looked up.
            Assessment current = assess(server, ownerId);
            if (current != null && current.decision.expires())
            {
                expire(server, ownerId, current);
            }
        });
    }

    private void expire(@NotNull MinecraftServer server, @NotNull UUID ownerId, @NotNull Assessment assessment)
    {
        List<ClaimSnapshot> expiring = assessment.decision.outcome() == ClaimExpirationPolicy.Outcome.EXPIRE_NEW_PLAYER_CLAIM
                ? Collections.singletonList(assessment.claims.get(0))
                : assessment.claims;
        String ownerName = FabricPlayerLookup.name(server, ownerId);
        int deleted = 0;
        for (ClaimSnapshot claim : expiring)
        {
            Long id = claim.id();
            if (id == null || !ClaimExpirationCallback.EVENT.invoker().allowExpiration(claim))
            {
                continue;
            }
            try
            {
                // No acting player: an expired claim is not abandoned, so no AbandonReturnRatio applies.
                if (this.claims.deleteClaimAs(id, null, null) != null)
                {
                    deleted++;
                    this.logger.info("Expired {}'s claim at {}.", ownerName, FabricClaimCommands.friendlyLocation(claim));
                }
            }
            catch (IOException exception)
            {
                this.logger.error("Could not delete {}'s expired claim at {}.",
                        ownerName, FabricClaimCommands.friendlyLocation(claim), exception);
            }
        }
        if (deleted == 0)
        {
            return;
        }
        if (assessment.decision.outcome() == ClaimExpirationPolicy.Outcome.EXPIRE_NEW_PLAYER_CLAIM)
        {
            this.logger.info("{}'s new player claim expired.", ownerName);
        }
        else
        {
            this.logger.info("{} of {}'s claims expired after {} days away.",
                    deleted, ownerName, this.settings.expiration().daysInactive());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // /checkclaimexpiry
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code /checkclaimexpiry [player]}: when the claim the sender stands in, or a player's claims, would
     * expire, and what keeps them. Another player's claims take {@code griefprevention.admin.checkclaimexpiry}.
     */
    boolean checkExpiry(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.CHECK_CLAIM_EXPIRY))
        {
            return true;
        }
        if (args.length > 1)
        {
            return false;
        }

        MinecraftServer server = sender.server();
        UUID ownerId;
        String ownerName;
        if (args.length == 0)
        {
            ServerPlayer player = sender.requirePlayer();
            if (player == null)
            {
                return true;
            }
            ClaimSnapshot claim = topLevel(this.claims.findClaimAt((ServerLevel) player.level(), player.blockPosition()));
            if (claim == null)
            {
                sender.sendError(MessageKey.CLAIM_EXPIRY_NO_CLAIM);
                return true;
            }
            ownerId = claim.ownerId();
            if (ownerId == null)
            {
                sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_NEVER_EXPIRES);
                return true;
            }
            ownerName = FabricPlayerLookup.name(server, ownerId);
        }
        else
        {
            if (!sender.checkPermission(FabricPermissionDefaults.CHECK_OTHERS_CLAIM_EXPIRY))
            {
                return true;
            }
            FabricPlayerLookup.KnownPlayer known = FabricPlayerLookup.find(server, args[0]);
            if (known == null)
            {
                sender.sendError(MessageKey.CLAIM_EXPIRY_PLAYER_NOT_FOUND, args[0]);
                return true;
            }
            ownerId = known.id();
            ownerName = known.name();
        }

        report(sender, server, ownerId, ownerName);
        return true;
    }

    private void report(
            @NotNull FabricCommandSender sender,
            @NotNull MinecraftServer server,
            @NotNull UUID ownerId,
            @NotNull String ownerName)
    {
        ClaimExpirationSettings expiration = this.settings.expiration();
        boolean online = server.getPlayerList().getPlayer(ownerId) != null;
        Long seen = online ? Long.valueOf(System.currentTimeMillis()) : this.lastSeen.lastSeen(ownerId);
        long now = System.currentTimeMillis();

        sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_PLAYER_HEADING, ownerName);
        sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_OWNER_STATUS, ownerName, sender.format(online
                ? MessageKey.CLAIM_EXPIRY_OWNER_ONLINE
                : MessageKey.CLAIM_EXPIRY_OWNER_OFFLINE));
        if (seen == null)
        {
            sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_LAST_ACTIVE_UNKNOWN);
        }
        else if (!online)
        {
            sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_LAST_ACTIVE, friendlyDuration(now - seen));
        }

        List<ClaimSnapshot> owned = ownedByAge(ownerId);
        if (owned.isEmpty())
        {
            sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_PLAYER_NO_CLAIMS, ownerName);
            return;
        }
        if (!expiration.enabled())
        {
            sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_NEVER_EXPIRES);
            return;
        }
        FabricClaimBlockService.Entitlement blocks = entitlement(ownerId);
        if (blocks == null)
        {
            sender.sendText(TextMode.ERROR, "Could not read " + ownerName + "'s claim blocks.");
            return;
        }
        if (blocks.bonus() >= expiration.exceptWhenOwnerHasBonusClaimBlocks()
                || (long) blocks.bonus() + blocks.accrued() >= expiration.exceptWhenOwnerHasTotalClaimBlocks())
        {
            sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_EXEMPT, "enough claim blocks");
            return;
        }
        if (seen == null)
        {
            sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_EXEMPT, "not seen since the server started recording");
            return;
        }

        Long expiresAt = ClaimExpirationPolicy.expiresAtMillis(
                expiration, seen, owned.size(), owned.get(0).bounds().area());
        if (expiresAt == null)
        {
            sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_NEVER_EXPIRES);
            return;
        }
        whenExempt(server, ownerId, exempt -> {
            if (exempt)
            {
                sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_EXEMPT, FabricPermissionDefaults.DONT_EXPIRE);
            }
            else if (expiresAt <= now)
            {
                sender.send(TextMode.WARNING, MessageKey.CLAIM_EXPIRY_EXPIRED, friendlyDuration(now - expiresAt));
            }
            else
            {
                sender.send(TextMode.INFO, MessageKey.CLAIM_EXPIRY_EXPIRES_IN, friendlyDuration(expiresAt - now));
            }
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** What the policy says about an owner now, or null when their claim blocks cannot be read. */
    private @Nullable Assessment assess(@NotNull MinecraftServer server, @NotNull UUID ownerId)
    {
        FabricClaimBlockService.Entitlement blocks = entitlement(ownerId);
        if (blocks == null)
        {
            return null;
        }
        List<ClaimSnapshot> owned = ownedByAge(ownerId);
        ClaimExpirationPolicy.Decision decision = ClaimExpirationPolicy.decide(
                this.settings.expiration(),
                server.getPlayerList().getPlayer(ownerId) != null,
                this.lastSeen.lastSeen(ownerId),
                blocks.accrued(),
                blocks.bonus(),
                owned.size(),
                owned.isEmpty() ? 0 : owned.get(0).bounds().area(),
                System.currentTimeMillis());
        return new Assessment(decision, owned);
    }

    /**
     * Looks up {@code griefprevention.dontexpire} for an owner who may be offline, then hands the answer
     * to {@code then} on the server thread. A failed lookup counts as exempt, so nothing is deleted on it.
     */
    private void whenExempt(@NotNull MinecraftServer server, @NotNull UUID ownerId, @NotNull Consumer<Boolean> then)
    {
        boolean operator = FabricOfflineOperators.isOperator(server, ownerId);
        this.claims.hasPermissionOffline(ownerId, FabricPermissionDefaults.DONT_EXPIRE, operator)
                .whenComplete((exempt, failure) -> {
                    if (failure != null)
                    {
                        this.logger.warn("Could not look up {} for {}; their claims stay for now.",
                                FabricPermissionDefaults.DONT_EXPIRE, ownerId, failure);
                    }
                    boolean keep = failure != null || Boolean.TRUE.equals(exempt);
                    server.execute(() -> then.accept(keep));
                });
    }

    private @Nullable FabricClaimBlockService.Entitlement entitlement(@NotNull UUID ownerId)
    {
        try
        {
            return this.claims.claimBlockService().entitlement(ownerId);
        }
        catch (IOException exception)
        {
            this.logger.warn("Could not read the claim blocks of {}.", ownerId, exception);
            return null;
        }
    }

    /** The owner's top-level claims, oldest first, so a lone claim is the one Paper would look at. */
    private @NotNull List<ClaimSnapshot> ownedByAge(@NotNull UUID ownerId)
    {
        List<ClaimSnapshot> owned = new ArrayList<>(this.claims.topLevelClaimsOwnedBy(ownerId));
        owned.sort(Comparator.comparing(claim -> claim.id() == null ? Long.MAX_VALUE : claim.id()));
        return owned;
    }

    private @Nullable ClaimSnapshot topLevel(@Nullable ClaimSnapshot claim)
    {
        ClaimSnapshot cursor = claim;
        for (int depth = 0; cursor != null && cursor.parentId() != null && depth < 16; depth++)
        {
            cursor = this.claims.claimById(cursor.parentId());
        }
        return cursor;
    }

    /** Paper's {@code getFriendlyTimeString}. */
    static @NotNull String friendlyDuration(long durationMillis)
    {
        if (durationMillis < 0)
        {
            return "0 seconds";
        }
        long seconds = durationMillis / 1000L;
        if (seconds < 60)
        {
            return plural(seconds, "second");
        }
        long minutes = seconds / 60L;
        if (minutes < 60)
        {
            return plural(minutes, "minute");
        }
        long hours = minutes / 60L;
        minutes %= 60L;
        if (hours < 24)
        {
            return minutes > 0 ? plural(hours, "hour") + ", " + plural(minutes, "minute") : plural(hours, "hour");
        }
        long days = hours / 24L;
        hours %= 24L;
        return hours > 0 ? plural(days, "day") + ", " + plural(hours, "hour") : plural(days, "day");
    }

    private static @NotNull String plural(long amount, @NotNull String unit)
    {
        return amount + " " + unit + (amount == 1 ? "" : "s");
    }

    private static final class Assessment
    {
        private final @NotNull ClaimExpirationPolicy.Decision decision;
        private final @NotNull List<ClaimSnapshot> claims;

        private Assessment(@NotNull ClaimExpirationPolicy.Decision decision, @NotNull List<ClaimSnapshot> claims)
        {
            this.decision = decision;
            this.claims = claims;
        }
    }
}
