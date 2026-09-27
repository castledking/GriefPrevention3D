package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimFlag;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.fabric.FabricDenialFeedback.TextMode;
import com.griefprevention.messages.MessageKey;
import com.griefprevention.protection.WorldProtectionSettings;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The player claim commands: creating, listing and abandoning claims, PvP toggles and claim modes. */
final class FabricClaimCommands
{
    /** Keeps a typed radius inside the world border, where claim arithmetic cannot overflow. */
    private static final int MAXIMUM_RADIUS = 30_000_000;

    private final FabricClaimRepository claims;
    private final FabricDenialFeedback feedback;
    private final FabricSettings settings;
    private final FabricClaimModes modes;
    private final FabricFakeBlockVisualization visualization;
    private final Logger logger;
    /** The PvP change each player was last asked to confirm, as Paper's {@code pendingPvpToggle}. */
    private final Map<UUID, PendingPvpToggle> pendingPvpToggles = new ConcurrentHashMap<>();

    FabricClaimCommands(
            @NotNull FabricClaimRepository claims,
            @NotNull FabricDenialFeedback feedback,
            @NotNull FabricSettings settings,
            @NotNull FabricClaimModes modes,
            @NotNull FabricFakeBlockVisualization visualization,
            @NotNull Logger logger)
    {
        this.claims = claims;
        this.feedback = feedback;
        this.settings = settings;
        this.modes = modes;
        this.visualization = visualization;
        this.logger = logger;
    }

    void register()
    {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                this.pendingPvpToggles.remove(handler.getPlayer().getUUID()));
    }

    /**
     * {@code /claim create [radius]}: claims a square centered on the player, sized to the minimum
     * claim area unless a radius is given, as Paper's claim command does.
     */
    boolean create(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null || !sender.checkPermission(FabricPermissionDefaults.CREATE_CLAIMS))
        {
            return true;
        }
        if (args.length > 1)
        {
            return false;
        }

        ServerLevel level = (ServerLevel) player.level();
        if (!this.settings.tools().claimsEnabled(this.claims.worldKey(level)))
        {
            sender.sendError(MessageKey.CLAIMS_DISABLED_WORLD);
            return true;
        }

        boolean adminClaim = this.modes.createsAdminClaims(player, this.claims);
        if (!adminClaim && this.claims.hasReachedClaimCountLimit(player))
        {
            sender.sendError(MessageKey.CLAIM_CREATION_FAILED_OVER_CLAIM_COUNT_LIMIT);
            return true;
        }

        int minimumRadius = (int) Math.ceil(Math.sqrt(this.settings.tools().minimumArea()) / 2);
        int radius = minimumRadius;
        if (args.length == 1)
        {
            try
            {
                radius = Integer.parseInt(args[0]);
            }
            catch (NumberFormatException notANumber)
            {
                return false;
            }
            if (radius < minimumRadius)
            {
                sender.sendError(MessageKey.MINIMUM_RADIUS, String.valueOf(minimumRadius));
                return true;
            }
            if (radius > MAXIMUM_RADIUS)
            {
                return false;
            }
        }

        try
        {
            FabricClaimRepository.CreateClaimResult result = this.claims.createClaim(
                    level,
                    player.blockPosition(),
                    adminClaim ? null : player.getUUID(),
                    radius,
                    player);
            if (result.hasReachedClaimCountLimit())
            {
                sender.sendError(MessageKey.CLAIM_CREATION_FAILED_OVER_CLAIM_COUNT_LIMIT);
                return true;
            }
            if (result.hasInsufficientClaimBlocks())
            {
                sender.sendError(MessageKey.CREATE_CLAIM_INSUFFICIENT_BLOCKS, String.valueOf(result.blocksNeeded()));
                return true;
            }
            ClaimSnapshot created = result.createdClaim();
            if (created == null)
            {
                sender.sendError(MessageKey.CREATE_CLAIM_FAIL_OVERLAP_SHORT);
                return true;
            }
            sender.send(TextMode.SUCCESS, MessageKey.CREATE_CLAIM_SUCCESS);
            this.visualization.visualizeClaim(player, level, created, player.blockPosition());
        }
        catch (IOException exception)
        {
            this.logger.error("Could not save the claim {} created.", sender.name(), exception);
            sender.sendText(TextMode.ERROR, "Could not save the claim: " + exception.getMessage());
        }
        return true;
    }

    /** {@code /claimslist [player]}: a player's claim-block math and their claims. */
    boolean claimsList(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.player();
        if (player != null && !sender.checkPermission(FabricPermissionDefaults.CLAIMS_LIST))
        {
            return true;
        }
        if (args.length > 1)
        {
            return false;
        }

        NameAndId target;
        if (args.length == 1 && (player == null || sender.hasPermission(FabricPermissionDefaults.CLAIMS_LIST_OTHER)))
        {
            target = FabricPlayerLookup.find(sender.server(), args[0]);
            if (target == null)
            {
                sender.sendError(MessageKey.PLAYER_NOT_FOUND_2);
                return true;
            }
        }
        else if (player != null)
        {
            // As on Paper, a player without the claimslistother permission always sees their own.
            target = player.nameAndId();
        }
        else
        {
            return false;
        }

        try
        {
            FabricClaimBlockService.Entitlement entitlement =
                    this.claims.claimBlockService().entitlement(target.id());
            int bonus = entitlement.bonus() + entitlement.groupBonus();
            sender.send(TextMode.INSTRUCTION, MessageKey.START_BLOCK_MATH,
                    String.valueOf(entitlement.accrued()),
                    String.valueOf(bonus),
                    String.valueOf(entitlement.accrued() + bonus));

            List<ClaimSnapshot> owned = this.claims.topLevelClaimsOwnedBy(target.id());
            if (!owned.isEmpty())
            {
                sender.send(TextMode.INSTRUCTION, MessageKey.CLAIMS_LIST_HEADER);
                for (ClaimSnapshot claim : owned)
                {
                    sender.sendText(TextMode.INSTRUCTION, friendlyLocation(claim)
                            + sender.format(MessageKey.CONTINUE_BLOCK_MATH, String.valueOf(claim.bounds().area())));
                }
                sender.send(TextMode.INSTRUCTION, MessageKey.END_BLOCK_MATH,
                        String.valueOf(this.claims.claimBlockBalance(target.id()).remaining()));
            }
        }
        catch (IOException exception)
        {
            sender.sendText(TextMode.ERROR, "Could not read the claim-block balance: " + exception.getMessage());
        }
        return true;
    }

    /** {@code /claim abandon [all|toplevel]}. */
    boolean abandon(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (args.length > 1)
        {
            return false;
        }
        if (args.length == 1 && "all".equalsIgnoreCase(args[0]))
        {
            return !sender.checkPermission(FabricPermissionDefaults.ABANDON_ALL_CLAIMS) || abandonAll(sender);
        }
        if (args.length == 1 && "toplevel".equalsIgnoreCase(args[0]))
        {
            return abandonTopLevelClaim(sender, new String[0]);
        }
        if (args.length == 1)
        {
            return false;
        }
        return abandonClaim(sender, args);
    }

    /** {@code /abandonclaim}: the claim the player stands in, unless it has subdivisions. */
    boolean abandonClaim(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (args.length > 0)
        {
            return false;
        }
        return !sender.checkPermission(FabricPermissionDefaults.ABANDON_CLAIM) || abandonHere(sender, false);
    }

    /** {@code /abandontoplevelclaim}: the claim the player stands in, subdivisions and all. */
    boolean abandonTopLevelClaim(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (args.length > 0)
        {
            return false;
        }
        return !sender.checkPermission(FabricPermissionDefaults.ABANDON_TOP_LEVEL_CLAIM) || abandonHere(sender, true);
    }

    /** {@code /abandonallclaims confirm}. */
    boolean abandonAllClaims(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        if (!sender.checkPermission(FabricPermissionDefaults.ABANDON_ALL_CLAIMS))
        {
            return true;
        }
        if (args.length > 1)
        {
            return false;
        }
        if (args.length != 1 || !"confirm".equalsIgnoreCase(args[0]))
        {
            sender.sendError(MessageKey.CONFIRM_ABANDON_ALL_CLAIMS);
            return true;
        }
        return abandonAll(sender);
    }

    private boolean abandonHere(@NotNull FabricCommandSender sender, boolean includingSubdivisions)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }

        ClaimSnapshot claim = this.claims.findClaimAt((ServerLevel) player.level(), player.blockPosition());
        if (claim == null || claim.id() == null)
        {
            sender.sendError(MessageKey.BLOCK_NOT_CLAIMED);
            return true;
        }
        if (!this.claims.allows(claim, player, ClaimTrustLevel.EDIT))
        {
            sender.sendError(MessageKey.NOT_YOUR_CLAIM);
            return true;
        }
        if (!includingSubdivisions && this.claims.hasSubdivisions(claim))
        {
            sender.send(TextMode.INSTRUCTION, MessageKey.DELETE_TOP_LEVEL_CLAIM);
            return true;
        }

        try
        {
            this.claims.deleteClaim(claim.id(), player);
            int remaining = this.claims.claimBlockBalance(player.getUUID()).remaining();
            sender.send(TextMode.SUCCESS, MessageKey.ABANDON_SUCCESS, String.valueOf(remaining));
        }
        catch (IOException exception)
        {
            this.logger.error("Could not save claims after {} abandoned claim {}.", sender.name(), claim.id(), exception);
            sender.sendText(TextMode.ERROR, "Could not save claims after abandoning: " + exception.getMessage());
        }
        return true;
    }

    private boolean abandonAll(@NotNull FabricCommandSender sender)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }

        List<ClaimSnapshot> owned = this.claims.topLevelClaimsOwnedBy(player.getUUID());
        if (owned.isEmpty())
        {
            sender.sendError(MessageKey.YOU_HAVE_NO_CLAIMS);
            return true;
        }

        try
        {
            for (ClaimSnapshot claim : owned)
            {
                if (claim.id() != null)
                {
                    this.claims.deleteClaim(claim.id(), player);
                }
            }
            int remaining = this.claims.claimBlockBalance(player.getUUID()).remaining();
            sender.send(TextMode.SUCCESS, MessageKey.SUCCESSFUL_ABANDON, String.valueOf(remaining));
        }
        catch (IOException exception)
        {
            this.logger.error("Could not save claims after {} abandoned all claims.", sender.name(), exception);
            sender.sendText(TextMode.ERROR, "Could not save claims after abandoning: " + exception.getMessage());
        }
        return true;
    }

    /** {@code /claimpvp [true|false|on|off] [confirm]}. */
    boolean claimPvp(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null || !sender.checkPermission(FabricPermissionDefaults.CLAIM_PVP))
        {
            return true;
        }
        if (args.length > 2)
        {
            return false;
        }
        String stateArg = args.length > 0 ? args[0] : null;
        String confirmArg = args.length > 1 ? args[1] : null;

        // As on Paper, /claimpvp is off until a server enables it for claims or subdivisions; the
        // protection rules only read a claim's own PvP setting when it is.
        WorldProtectionSettings rules = this.settings.world();
        if (!rules.pvpToggleForClaims() && !rules.pvpToggleForSubdivisions())
        {
            sender.sendError(MessageKey.PVP_TOGGLE_NOT_ENABLED);
            return true;
        }

        ClaimSnapshot claim = this.claims.findClaimAt((ServerLevel) player.level(), player.blockPosition());
        if (claim == null || claim.id() == null)
        {
            sender.sendError(MessageKey.NOT_YOUR_CLAIM);
            return true;
        }
        if (claim.parentId() == null ? !rules.pvpToggleForClaims() : !rules.pvpToggleForSubdivisions())
        {
            sender.sendError(MessageKey.PVP_TOGGLE_NOT_ENABLED_FOR_CLAIM_TYPE);
            return true;
        }
        if (!this.claims.allows(claim, player, ClaimTrustLevel.MANAGE))
        {
            sender.sendError(MessageKey.ONLY_OWNERS_MODIFY_CLAIMS, this.feedback.ownerName(player, claim));
            return true;
        }

        Boolean currentPvpState = this.claims.flag(claim.id(), ClaimFlag.PVP);
        if (currentPvpState == null)
        {
            sender.sendError(MessageKey.NOT_YOUR_CLAIM);
            return true;
        }

        boolean toggleTo;
        if (stateArg == null)
        {
            toggleTo = !currentPvpState;
        }
        else
        {
            switch (stateArg.trim().toLowerCase(Locale.ROOT))
            {
                case "true", "on", "enable" -> toggleTo = true;
                case "false", "off", "disable" -> toggleTo = false;
                default ->
                {
                    sender.sendError(MessageKey.PVP_TOGGLE_USAGE);
                    return true;
                }
            }
        }

        String claimType = sender.format(claim.subdivision() ? MessageKey.SUBDIVISION_LABEL : MessageKey.CLAIM_LABEL);
        if (currentPvpState == toggleTo)
        {
            sender.sendError(
                    toggleTo ? MessageKey.PVP_TOGGLE_ALREADY_ENABLED : MessageKey.PVP_TOGGLE_ALREADY_DISABLED,
                    claimType);
            return true;
        }

        if (!"confirm".equalsIgnoreCase(confirmArg))
        {
            this.pendingPvpToggles.put(player.getUUID(), new PendingPvpToggle(claim.id(), toggleTo));
            sender.send(TextMode.INSTRUCTION,
                    toggleTo ? MessageKey.CONFIRM_PVP_TOGGLE_ENABLED_NO_FEE : MessageKey.CONFIRM_PVP_TOGGLE_DISABLED_NO_FEE,
                    claimType);
            sender.send(TextMode.INSTRUCTION, MessageKey.CONFIRM_PVP_TOGGLE_INSTRUCTION, toggleTo ? "true" : "false");
            return true;
        }

        this.pendingPvpToggles.remove(player.getUUID());
        try
        {
            this.claims.setPvpToggle(claim.id(), toggleTo);
            sender.send(TextMode.SUCCESS, toggleTo ? MessageKey.PVP_TOGGLE_ENABLED : MessageKey.PVP_TOGGLE_DISABLED,
                    claimType);
        }
        catch (IOException exception)
        {
            sender.sendText(TextMode.ERROR, "Could not save the PvP toggle: " + exception.getMessage());
        }
        return true;
    }

    /** {@code /claimpvpconfirm}: applies the change {@code /claimpvp} last asked to confirm. */
    boolean claimPvpConfirm(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null || !sender.checkPermission(FabricPermissionDefaults.CLAIM_PVP))
        {
            return true;
        }
        if (args.length > 0)
        {
            return false;
        }

        PendingPvpToggle pending = this.pendingPvpToggles.remove(player.getUUID());
        if (pending == null)
        {
            sender.sendError(MessageKey.NO_PENDING_PVP_TOGGLE);
            return true;
        }
        // The confirmation only counts in the claim it was asked for, as on Paper.
        ClaimSnapshot claim = this.claims.findClaimAt((ServerLevel) player.level(), player.blockPosition());
        if (claim == null || claim.id() == null || claim.id() != pending.claimId)
        {
            sender.sendError(MessageKey.PENDING_PVP_TOGGLE_EXPIRED);
            return true;
        }
        return claimPvp(sender, new String[] {pending.enable ? "true" : "false", "confirm"});
    }

    /** {@code /claim mode [basic]} and {@code /basicclaims}. */
    boolean claimMode(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }
        if (args.length > 1)
        {
            return false;
        }
        if (args.length == 0 || "basic".equalsIgnoreCase(args[0]))
        {
            if (sender.checkPermission(FabricPermissionDefaults.BASIC_CLAIMS))
            {
                this.modes.set(player.getUUID(), FabricClaimModes.Mode.BASIC);
                sender.send(TextMode.SUCCESS, MessageKey.BASIC_CLAIMS_MODE);
            }
            return true;
        }
        FabricCommandRegistrar.sendUnavailable(sender);
        return true;
    }

    /** {@code /aclaim mode admin} and {@code /adminclaims}. */
    boolean adminMode(@NotNull FabricCommandSender sender, @NotNull String @NotNull [] args)
    {
        ServerPlayer player = sender.requirePlayer();
        if (player == null)
        {
            return true;
        }
        if (args.length > 1)
        {
            return false;
        }
        if (args.length == 0 || "admin".equalsIgnoreCase(args[0]))
        {
            if (sender.checkPermission(FabricPermissionDefaults.ADMIN_CLAIMS))
            {
                this.modes.set(player.getUUID(), FabricClaimModes.Mode.ADMIN);
                sender.send(TextMode.SUCCESS, MessageKey.ADMIN_CLAIMS_MODE);
            }
            return true;
        }
        if ("admin3d".equalsIgnoreCase(args[0]))
        {
            FabricCommandRegistrar.sendUnavailable(sender);
            return true;
        }
        return false;
    }

    private static final class PendingPvpToggle
    {
        private final long claimId;
        private final boolean enable;

        private PendingPvpToggle(long claimId, boolean enable)
        {
            this.claimId = claimId;
            this.enable = enable;
        }
    }

    /** Paper's {@code getfriendlyLocationString} for a claim's lesser corner. */
    static @NotNull String friendlyLocation(@NotNull ClaimSnapshot claim)
    {
        ClaimBounds bounds = claim.bounds();
        return claim.worldKey() + ": x" + bounds.minX() + ", z" + bounds.minZ();
    }
}
