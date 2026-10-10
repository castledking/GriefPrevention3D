package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTransferSettings;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claim commands for staff, and for players handing a claim over: ignoring claims, deleting,
 * transferring and converting claims, and listing administrative claims, as Paper's
 * {@code UnifiedAdminClaimCommand} does them.
 */
final class FabricAdminClaimCommands
{
    private final FabricClaimRepository claims;
    private final FabricDenialFeedback feedback;
    private final FabricSettings settings;
    private final Logger logger;
    /** Players warned that the claim they are deleting has subdivisions, as Paper's {@code warnedAboutMajorDeletion}. */
    private final Set<UUID> warnedAboutMajorDeletion = ConcurrentHashMap.newKeySet();

    FabricAdminClaimCommands(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricDenialFeedback feedback,
            @NotNull FabricSettings settings,
            @NotNull Logger logger)
    {
        this.claims = claims;
        this.feedback = feedback;
        this.settings = settings;
        this.logger = logger;
    }

    void register()
    {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.getPlayer().getUUID();
            this.claims.stopIgnoringClaims(playerId);
            this.warnedAboutMajorDeletion.remove(playerId);
        });
    }

    // ---------------------------------------------------------------------------------------------
    // /ignoreclaims
    // ---------------------------------------------------------------------------------------------

    /** {@code /ignoreclaims}: toggles ignoring claims until the player logs out. */
    boolean ignoreClaims(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null || !sender.checkPermission(FabricPermissionDefaults.IGNORE_CLAIMS))
        {
            return true;
        }
        boolean ignoring = this.claims.toggleIgnoringClaims(player.getUUID());
        sender.send(TextMode.SUCCESS, ignoring ? MessageKey.IGNORING_CLAIMS : MessageKey.RESPECTING_CLAIMS);
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Deleting claims
    // ---------------------------------------------------------------------------------------------

    /** {@code /aclaim delete [claim|player <name>|world <world>|userworld <world>|alladmin]} and {@code /deleteclaim}. */
    boolean delete(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        String operation = args.length == 0 ? "claim" : args[0].toLowerCase(Locale.ROOT);
        if (!sender.checkPermission(deletePermission(operation)))
        {
            return true;
        }
        if ("alladmin".equals(operation) && !sender.checkPermission(FabricPermissionDefaults.DELETE_CLAIMS))
        {
            return true;
        }

        switch (operation)
        {
            case "claim":
                return args.length <= 1 && deleteHere(sender);
            case "player":
                return args.length == 2 && deleteOwnedBy(sender, args[1]);
            case "world":
            case "userworld":
                return args.length == 2 && deleteInWorld(sender, args[1], "world".equals(operation));
            case "alladmin":
                return args.length == 1 && deleteAllAdmin(sender);
            default:
                sender.sendText(TextMode.ERROR, "Unknown delete operation: " + operation);
                return true;
        }
    }

    /** {@code /deleteallclaims <player>}. */
    boolean deleteAllClaims(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.DELETE_CLAIMS))
        {
            return true;
        }
        return args.length == 1 && deleteOwnedBy(sender, args[0]);
    }

    /** {@code /deletealladminclaims}. */
    boolean deleteAllAdminClaims(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.DELETE_ALL_ADMIN_CLAIMS)
                || !sender.checkPermission(FabricPermissionDefaults.DELETE_CLAIMS))
        {
            return true;
        }
        return deleteAllAdmin(sender);
    }

    private static @NotNull String deletePermission(@NotNull String operation)
    {
        switch (operation)
        {
            case "world":
            case "userworld":
                return FabricPermissionDefaults.DELETE_CLAIMS_IN_WORLD;
            case "alladmin":
                return FabricPermissionDefaults.DELETE_ALL_ADMIN_CLAIMS;
            default:
                return FabricPermissionDefaults.DELETE_CLAIMS;
        }
    }

    private boolean deleteHere(@NotNull FabricCommandSender sender)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }
        ClaimSnapshot claim = claimHere(player);
        if (claim == null)
        {
            sender.sendError(MessageKey.DELETE_CLAIM_MISSING);
            return true;
        }
        // Deleting an administrative claim also takes the adminclaims permission.
        if (this.claims.isAdminClaim(claim) && !sender.hasPermission(FabricPermissionDefaults.ADMIN_CLAIMS))
        {
            sender.sendError(MessageKey.CANT_DELETE_ADMIN_CLAIM);
            return true;
        }
        if (this.claims.hasSubdivisions(claim) && this.warnedAboutMajorDeletion.add(player.getUUID()))
        {
            sender.send(TextMode.WARNING, MessageKey.DELETION_SUBDIVISION_WARNING);
            return true;
        }

        String owner = ownerName(sender, claim);
        try
        {
            this.claims.deleteClaim(claim.id(), player);
        }
        catch (IOException exception)
        {
            return saveFailed(sender, "deleting a claim", exception);
        }
        this.warnedAboutMajorDeletion.remove(player.getUUID());
        sender.send(TextMode.SUCCESS, MessageKey.DELETE_SUCCESS);
        this.logger.info("{} deleted {}'s claim at {}.", sender.name(), owner, FabricClaimCommands.friendlyLocation(claim));
        return true;
    }

    private boolean deleteOwnedBy(@NotNull FabricCommandSender sender, @NotNull String name)
    {
        FabricPlayerLookup.KnownPlayer owner = FabricPlayerLookup.find(sender.server(), name);
        if (owner == null)
        {
            sender.sendError(MessageKey.PLAYER_NOT_FOUND_2);
            return true;
        }
        if (!deleteAll(sender, this.claims.topLevelClaimsOwnedBy(owner.id())))
        {
            return true;
        }
        sender.send(TextMode.SUCCESS, MessageKey.DELETE_ALL_SUCCESS, owner.name());
        this.logger.info("{} deleted all claims belonging to {}.", sender.name(), owner.name());
        return true;
    }

    private boolean deleteInWorld(@NotNull FabricCommandSender sender, @NotNull String worldName, boolean includeAdmin)
    {
        String worldKey = null;
        for (ServerLevel level : sender.server().getAllLevels())
        {
            String key = this.claims.worldKey(level);
            if (key.equalsIgnoreCase(worldName))
            {
                worldKey = key;
                break;
            }
        }
        if (worldKey == null)
        {
            sender.sendError(MessageKey.WORLD_NOT_FOUND);
            return true;
        }

        List<ClaimSnapshot> doomed = new ArrayList<>();
        for (ClaimSnapshot claim : this.claims.snapshots())
        {
            if (claim.parentId() == null && worldKey.equals(claim.worldKey()) && (includeAdmin || claim.ownerId() != null))
            {
                doomed.add(claim);
            }
        }
        if (!deleteAll(sender, doomed))
        {
            return true;
        }
        String message = (includeAdmin ? "Deleted all claims in world: " : "Deleted all user claims in world: ") + worldKey;
        sender.sendText(TextMode.SUCCESS, message);
        this.logger.info("{}: {}", sender.name(), message);
        return true;
    }

    private boolean deleteAllAdmin(@NotNull FabricCommandSender sender)
    {
        if (!deleteAll(sender, this.claims.topLevelAdminClaims()))
        {
            return true;
        }
        sender.send(TextMode.SUCCESS, MessageKey.ALL_ADMIN_DELETED);
        this.logger.info("{} deleted all administrative claims.", sender.name());
        return true;
    }

    /** @return false when saving failed, which the sender has been told about */
    private boolean deleteAll(@NotNull FabricCommandSender sender, @NotNull List<ClaimSnapshot> doomed)
    {
        try
        {
            for (ClaimSnapshot claim : doomed)
            {
                if (claim.id() != null)
                {
                    // A staff deletion returns the owner's claim blocks in full: only abandoning costs any.
                    this.claims.deleteClaim(claim.id(), sender.player());
                }
            }
            return true;
        }
        catch (IOException exception)
        {
            saveFailed(sender, "deleting claims", exception);
            return false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // /adminclaimslist
    // ---------------------------------------------------------------------------------------------

    /** {@code /adminclaimslist}: where every administrative claim is. */
    boolean adminClaimsList(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.ADMIN_CLAIMS_LIST))
        {
            return true;
        }
        List<ClaimSnapshot> adminClaims = this.claims.topLevelAdminClaims();
        if (adminClaims.isEmpty())
        {
            sender.sendText(TextMode.INFO, "No administrative claims found.");
            return true;
        }
        sender.send(TextMode.INSTRUCTION, MessageKey.CLAIMS_LIST_HEADER);
        for (ClaimSnapshot claim : adminClaims)
        {
            sender.sendText(TextMode.INSTRUCTION, FabricClaimCommands.friendlyLocation(claim));
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // /transferclaim
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code /transferclaim <player> [confirm]}: staff with {@code griefprevention.transferclaim.others}
     * hand any claim to a player, or make it administrative with no name; everyone else gives away a
     * claim of their own once {@code Claims.TransferClaim.Enabled} allows it.
     */
    boolean transfer(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null || !sender.checkPermission(FabricPermissionDefaults.TRANSFER_CLAIM))
        {
            return true;
        }
        if (sender.hasPermission(FabricPermissionDefaults.TRANSFER_CLAIM_OTHERS))
        {
            return transferAsStaff(sender, player, args);
        }
        return giveAway(sender, player, args);
    }

    private boolean transferAsStaff(
            @NotNull FabricCommandSender sender,
            @NotNull ServerPlayer player,
            @NotNull String @NotNull [] args)
    {
        ClaimSnapshot claim = claimHere(player);
        if (claim == null)
        {
            sender.send(TextMode.INSTRUCTION, MessageKey.TRANSFER_CLAIM_MISSING);
            return true;
        }
        if (this.claims.isAdminClaim(claim) && !sender.hasPermission(FabricPermissionDefaults.ADMIN_CLAIMS))
        {
            sender.sendError(MessageKey.TRANSFER_CLAIM_PERMISSION);
            return true;
        }

        // No name makes the claim administrative.
        UUID newOwnerId = null;
        String newOwnerName = "admin";
        if (args.length > 0)
        {
            FabricPlayerLookup.KnownPlayer target = FabricPlayerLookup.find(sender.server(), args[0]);
            if (target == null)
            {
                sender.sendError(MessageKey.PLAYER_NOT_FOUND_2);
                return true;
            }
            newOwnerId = target.id();
            newOwnerName = target.name();
        }
        if (claim.parentId() != null)
        {
            sender.send(TextMode.INSTRUCTION, MessageKey.TRANSFER_TOP_LEVEL);
            return true;
        }

        try
        {
            this.claims.changeOwner(claim.id(), newOwnerId, player);
        }
        catch (IOException exception)
        {
            return saveFailed(sender, "transferring a claim", exception);
        }
        sender.send(TextMode.SUCCESS, MessageKey.TRANSFER_SUCCESS);
        this.logger.info("{} transferred a claim at {} to {}.",
                sender.name(), FabricClaimCommands.friendlyLocation(claim), newOwnerName);
        return true;
    }

    private boolean giveAway(
            @NotNull FabricCommandSender sender,
            @NotNull ServerPlayer player,
            @NotNull String @NotNull [] args)
    {
        ClaimTransferSettings transfer = this.settings.transfer();
        if (!transfer.enabled())
        {
            sender.sendError(MessageKey.TRANSFER_CLAIM_NOT_ENABLED);
            return true;
        }
        if (args.length < 1 || args.length > 2)
        {
            return false;
        }
        boolean confirmed = args.length == 2;
        if (confirmed && !"confirm".equalsIgnoreCase(args[1]))
        {
            return false;
        }

        ClaimSnapshot claim = claimHere(player);
        if (claim == null)
        {
            sender.sendError(MessageKey.TRANSFER_CLAIM_NO_CLAIM);
            return true;
        }
        if (claim.parentId() != null)
        {
            sender.send(TextMode.INSTRUCTION, MessageKey.TRANSFER_TOP_LEVEL);
            return true;
        }
        if (!player.getUUID().equals(claim.ownerId()))
        {
            sender.sendError(MessageKey.NOT_YOUR_CLAIM);
            return true;
        }

        FabricPlayerLookup.KnownPlayer recipient = FabricPlayerLookup.find(sender.server(), args[0]);
        if (recipient == null)
        {
            sender.sendError(MessageKey.PLAYER_NOT_FOUND_2);
            return true;
        }
        if (recipient.id().equals(player.getUUID()))
        {
            sender.sendError(MessageKey.TRANSFER_CLAIM_SELF);
            return true;
        }

        // The recipient takes on the claim's area and one of their claim slots, just as if they had
        // claimed the land themselves.
        long missingBlocks;
        try
        {
            missingBlocks = (long) claim.bounds().area() - this.claims.claimBlockBalance(recipient.id()).remaining();
        }
        catch (IOException exception)
        {
            sender.sendText(TextMode.ERROR, "Could not read the claim-block balance: " + exception.getMessage());
            return true;
        }
        if (missingBlocks > 0)
        {
            sender.sendError(MessageKey.TRANSFER_CLAIM_RECIPIENT_NEEDS_BLOCKS, recipient.name(), String.valueOf(missingBlocks));
            return true;
        }
        ServerPlayer onlineRecipient = sender.server().getPlayerList().getPlayer(recipient.id());
        if (this.claims.isAtClaimCountLimit(recipient.id(), onlineRecipient))
        {
            sender.sendError(MessageKey.TRANSFER_CLAIM_RECIPIENT_AT_LIMIT, recipient.name());
            return true;
        }

        // Fabric has no economy: a priced transfer is refused, as Paper refuses one without Vault.
        double fee = sender.hasPermission(FabricPermissionDefaults.TRANSFER_CLAIM_FREE) ? 0.0 : transfer.price();
        if (fee > 0.0)
        {
            sender.sendError(MessageKey.ECONOMY_NO_VAULT);
            return true;
        }
        if (!confirmed)
        {
            sender.send(TextMode.INSTRUCTION, MessageKey.CONFIRM_TRANSFER_CLAIM_NO_FEE, recipient.name());
            sender.send(TextMode.INSTRUCTION, MessageKey.CONFIRM_TRANSFER_CLAIM_INSTRUCTION, recipient.name());
            return true;
        }

        try
        {
            this.claims.changeOwner(claim.id(), recipient.id(), player);
        }
        catch (IOException exception)
        {
            return saveFailed(sender, "giving a claim away", exception);
        }
        String location = FabricClaimCommands.friendlyLocation(claim);
        sender.send(TextMode.SUCCESS, MessageKey.TRANSFER_CLAIM_SUCCESS, recipient.name());
        if (onlineRecipient != null)
        {
            this.feedback.send(onlineRecipient, TextMode.INFO, MessageKey.TRANSFER_CLAIM_RECEIVED,
                    FabricPlayerLookup.nameOf(player), location);
        }
        this.logger.info("{} gave their claim at {} to {}.", sender.name(), location, recipient.name());
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // /makeadmin and /makebasic
    // ---------------------------------------------------------------------------------------------

    /** {@code /makeadmin}: turns the claim the player stands in into an administrative claim. */
    boolean makeAdmin(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        return convert(sender, args, true);
    }

    /** {@code /makebasic}: turns the administrative claim the player stands in into a claim they own. */
    boolean makeBasic(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        return convert(sender, args, false);
    }

    private boolean convert(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args, boolean makeAdmin)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null || !sender.checkPermission(FabricPermissionDefaults.CONVERT_CLAIMS))
        {
            return true;
        }
        if (args.length > 0)
        {
            return false;
        }

        ClaimSnapshot claim = claimHere(player);
        if (claim == null)
        {
            sender.send(TextMode.INSTRUCTION, MessageKey.CONVERT_CLAIM_MISSING);
            return true;
        }
        // Paper's administrative subdivisions are not on Fabric yet.
        if (claim.parentId() != null)
        {
            sender.send(TextMode.INSTRUCTION, MessageKey.TRANSFER_TOP_LEVEL);
            return true;
        }
        if (this.claims.isAdminClaim(claim) == makeAdmin)
        {
            sender.send(TextMode.INSTRUCTION,
                    makeAdmin ? MessageKey.CONVERT_CLAIM_ALREADY_ADMIN : MessageKey.CONVERT_CLAIM_ALREADY_BASIC);
            return true;
        }

        try
        {
            this.claims.changeOwner(claim.id(), makeAdmin ? null : player.getUUID(), player);
        }
        catch (IOException exception)
        {
            return saveFailed(sender, "converting a claim", exception);
        }
        sender.send(TextMode.SUCCESS,
                makeAdmin ? MessageKey.CONVERT_CLAIM_ADMIN_SUCCESS : MessageKey.CONVERT_CLAIM_BASIC_SUCCESS);
        this.logger.info(makeAdmin
                        ? "{} converted the claim at {} to administrative ownership."
                        : "{} converted the administrative claim at {} to a basic claim they own.",
                sender.name(), FabricClaimCommands.friendlyLocation(claim));
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private @Nullable ClaimSnapshot claimHere(@NotNull ServerPlayer player)
    {
        ClaimSnapshot claim = this.claims.findClaimAt((ServerLevel) player.level(), player.blockPosition());
        return claim == null || claim.id() == null ? null : claim;
    }

    private @NotNull String ownerName(@NotNull FabricCommandSender sender, @NotNull ClaimSnapshot claim)
    {
        UUID ownerId = this.claims.effectiveOwnerId(claim);
        return ownerId == null ? "an administrator" : FabricPlayerLookup.name(sender.server(), ownerId);
    }

    private boolean saveFailed(@NotNull FabricCommandSender sender, @NotNull String action, @NotNull IOException exception)
    {
        this.logger.error("Could not save claims after {} by {}.", action, sender.name(), exception);
        sender.sendText(TextMode.ERROR, "Could not save claims: " + exception.getMessage());
        return true;
    }
}
