package com.griefprevention.commands;

import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.*;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Unified command handler for /claim with subcommands
 */
public class UnifiedClaimCommand extends UnifiedCommandHandler {

    public UnifiedClaimCommand(@NotNull GriefPrevention plugin) {
        super(plugin, "claim");

        // Register subcommands
        registerSubcommand("protect", this::handleProtect);
        registerSubcommand("trust", this::handleTrust, "accesstrust", "containertrust", "permissiontrust");
        registerSubcommand("untrust", this::handleUntrust);
        registerSubcommand("trustlist", this::handleTrustList);
        registerSubcommand("list", this::handleList);
        registerSubcommand("mode", createModeTabExecutor());
        registerSubcommand("restrictsubclaim", this::handleRestrictSubclaim);
        registerSubcommand("explosions", this::handleExplosions);
        registerSubcommand("buyblocks", this::handleBuyBlocks);
        registerSubcommand("sellblocks", this::handleSellBlocks);
        registerSubcommand("abandon", this::handleAbandon, "abandonall");
        registerSubcommand("siege", this::handleSiege);
        registerSubcommand("trapped", this::handleTrapped);
        registerSubcommand("help", this::handleHelp);

        // Register standalone commands from Alias enum
        registerStandaloneCommand(Alias.ClaimProtect, this::handleProtect);
        registerStandaloneCommand(Alias.ClaimTrust, this::handleTrust);
        registerStandaloneCommand(Alias.ClaimUntrust, this::handleUntrust);
        registerStandaloneCommand(Alias.ClaimTrustlist, this::handleTrustList);
        registerStandaloneCommand(Alias.ClaimList, this::handleList);
        registerStandaloneCommand(Alias.ClaimMode, createModeTabExecutor());
        registerStandaloneCommand(Alias.ClaimRestrictSubclaim, this::handleRestrictSubclaim);
        registerStandaloneCommand(Alias.ClaimExplosions, this::handleExplosions);
        registerStandaloneCommand(Alias.ClaimBuyBlocks, this::handleBuyBlocks);
        registerStandaloneCommand(Alias.ClaimSellBlocks, this::handleSellBlocks);
        registerStandaloneCommand(Alias.ClaimAbandon, this::handleAbandon);
        registerStandaloneCommand(Alias.ClaimSiege, this::handleSiege);
        registerStandaloneCommand(Alias.ClaimTrapped, this::handleTrapped);
        registerStandaloneCommand(Alias.ClaimHelp, this::handleHelp);
    }

    @Override
    protected void handleDefault(CommandSender sender) {
        // Check if root command is disabled first
        if (!rootCommandEnabled) {
            sender.sendMessage("This command is disabled.");
            return;
        }

        // Check if use-as-help-cmd is enabled
        if (rootCommandConfig != null && rootCommandConfig.shouldUseAsHelpCmd()) {
            sendHelpMessage(sender, new String[0]);
            return;
        }

        // Default behavior: create a claim
        handleProtect(sender, new String[0]);
    }

    @Override
    protected boolean handleUnknownSubcommand(CommandSender sender, String subcommand, String[] args) {
        if (sender instanceof Player player) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.CommandNotFound, subcommand);
        } else {
            sender.sendMessage("Unknown subcommand: " + subcommand);
        }
        return true;
    }

    private boolean handleProtect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        World world = player.getWorld();
        if (!plugin.claimsEnabledForWorld(world)) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimsDisabledWorld);
            return true;
        }

        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());

        // Check claim count limit
        if (plugin.config_claims_maxClaimsPerPlayer > 0 &&
                !player.hasPermission("griefprevention.overrideclaimcountlimit") &&
                playerData.getClaims().size() >= plugin.config_claims_maxClaimsPerPlayer) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimCreationFailedOverClaimCountLimit);
            return true;
        }

        int radius = -1;

        // Allow specifying radius
        if (args.length > 0) {
            if (needsShovel(playerData, player)) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.RadiusRequiresGoldenShovel);
                return true;
            }

            try {
                radius = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                return false;
            }

            int minRadius = getClaimMinRadius();
            if (radius < minRadius) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.MinimumRadius, String.valueOf(minRadius));
                return true;
            }
        }

        // If no claims and auto-claims enabled, use automatic radius
        if (radius < 0 && playerData.getClaims().isEmpty()
                && plugin.config_claims_automaticClaimsForNewPlayersRadius >= 0) {
            radius = plugin.config_claims_automaticClaimsForNewPlayersRadius;
        }

        // If has claims, use minimum radius
        if (radius < 0) {
            if (needsShovel(playerData, player)) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.MustHoldModificationToolForThat);
                return true;
            }
            radius = getClaimMinRadius();
        }

        if (radius < 0)
            radius = 0;

        Location playerLoc = player.getLocation();
        int lesserX, lesserZ, greaterX, greaterZ;

        try {
            lesserX = Math.subtractExact(playerLoc.getBlockX(), radius);
            lesserZ = Math.subtractExact(playerLoc.getBlockZ(), radius);
            greaterX = Math.addExact(playerLoc.getBlockX(), radius);
            greaterZ = Math.addExact(playerLoc.getBlockZ(), radius);
        } catch (ArithmeticException e) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks,
                    String.valueOf(Integer.MAX_VALUE));
            return true;
        }

        Location lesser = new Location(world, lesserX, playerLoc.getY(), lesserZ);
        Location greater = new Location(world, greaterX, world.getMaxHeight(), greaterZ);

        UUID ownerId = playerData.shovelMode == ShovelMode.Admin ? null : player.getUniqueId();

        if (ownerId != null) {
            // Check claim blocks
            int area;
            try {
                int dX = Math.addExact(Math.subtractExact(greater.getBlockX(), lesser.getBlockX()), 1);
                int dZ = Math.addExact(Math.subtractExact(greater.getBlockZ(), lesser.getBlockZ()), 1);
                area = Math.abs(Math.multiplyExact(dX, dZ));
            } catch (ArithmeticException e) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks,
                        String.valueOf(Integer.MAX_VALUE));
                return true;
            }

            int remaining = playerData.getRemainingClaimBlocks();
            if (remaining < area) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimInsufficientBlocks,
                        String.valueOf(area - remaining));
                plugin.dataStore.tryAdvertiseAdminAlternatives(player);
                return true;
            }
        }

        createClaim(player, playerData, lesser, greater, ownerId);
        return true;
    }

    private void createClaim(Player player, PlayerData playerData, Location lesser, Location greater, UUID ownerId) {
        World world = player.getWorld();
        int minY, maxY;

        Claim parentClaim = plugin.dataStore.getClaimAt(lesser, true, null);
        if (parentClaim != null) {
            if (lesser.getBlockY() == greater.getBlockY()) {
                minY = parentClaim.getLesserBoundaryCorner().getBlockY();
                maxY = parentClaim.getGreaterBoundaryCorner().getBlockY();
            } else {
                minY = Math.min(lesser.getBlockY(), greater.getBlockY());
                maxY = Math.max(lesser.getBlockY(), greater.getBlockY());
            }
        } else {
            minY = lesser.getBlockY() - plugin.config_claims_claimsExtendIntoGroundDistance - 1;
            maxY = world.getHighestBlockYAt(greater) - plugin.config_claims_claimsExtendIntoGroundDistance - 1;
        }

        CreateClaimResult result = plugin.dataStore.createClaim(world,
                lesser.getBlockX(), greater.getBlockX(),
                minY, maxY,
                lesser.getBlockZ(), greater.getBlockZ(),
                ownerId, null, null, player);

        if (!result.succeeded || result.claim == null) {
            if (result.claim != null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapShort);
                BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CONFLICT_ZONE);
            } else {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.CreateClaimFailOverlapRegion);
            }
        } else {
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.CreateClaimSuccess);

            if (plugin.creativeRulesApply(player.getLocation())) {
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.CreativeBasicsVideo2,
                        DataStore.CREATIVE_VIDEO_URL);
            } else if (plugin.claimsEnabledForWorld(world)) {
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.SurvivalBasicsVideo2,
                        DataStore.SURVIVAL_VIDEO_URL);
            }

            BoundaryVisualization.visualizeClaim(player, result.claim, VisualizationType.CLAIM);
            playerData.claimResizing = null;
            playerData.lastShovelLocation = null;

            AutoExtendClaimTask.scheduleAsync(result.claim);
        }
    }

    private boolean handleTrust(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player))
            return false;

        if (args.length < 1 || args.length > 2)
            return false;

        String recipientName = args[0];

        if (args.length == 1) {
            // No trust type specified, use default trust command
            return plugin.handleTrustCommand(sender, new String[] { recipientName });
        }

        // Handle specific trust types
        String type = args[1].toLowerCase();
        switch (type) {
            case "access":
                return plugin.getCommand("accesstrust").execute(sender, "accesstrust", new String[] { recipientName });
            case "container":
                return plugin.getCommand("containertrust").execute(sender, "containertrust",
                        new String[] { recipientName });
            case "permission":
                return plugin.getCommand("permissiontrust").execute(sender, "permissiontrust",
                        new String[] { recipientName });
            default:
                return false;
        }
    }

    private boolean handleUntrust(CommandSender sender, String[] args) {
        // Delegate to existing untrust command logic
        return plugin.handleUntrustCommand(sender, args);
    }

    private boolean handleTrustList(CommandSender sender, String[] args) {
        // Delegate to existing trustlist command logic
        return plugin.handleTrustListCommand(sender, args);
    }

    private boolean handleList(CommandSender sender, String[] args) {
        // Delegate to existing claimslist command logic
        return plugin.handleClaimsListCommand(sender, args);
    }

    private boolean handleMode(CommandSender sender, String[] args) {
        // Handle mode switching (basic, subdivide, 3d)
        return plugin.handleModeCommand(sender, args);
    }

    private TabExecutor createModeTabExecutor() {
        return new TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command,
                    @NotNull String alias, @NotNull String[] args) {
                return handleMode(sender, args);
            }

            @Override
            public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                    @NotNull org.bukkit.command.Command command, @NotNull String alias, @NotNull String[] args) {
                // Provide tab completion for mode options
                if (args.length == 1) {
                    String prefix = args[0].toLowerCase();
                    return java.util.Arrays.asList("basic", "2d", "3d").stream()
                            .filter(mode -> mode.toLowerCase().startsWith(prefix))
                            .collect(java.util.stream.Collectors.toList());
                }
                return java.util.Collections.emptyList();
            }
        };
    }

    private boolean handleRestrictSubclaim(CommandSender sender, String[] args) {
        // Delegate to existing restrictsubclaim command logic
        return plugin.handleRestrictSubclaimCommand(sender, args);
    }

    
    private boolean handleExplosions(CommandSender sender, String[] args) {
        // Delegate to existing claimexplosions command logic
        return plugin.handleClaimExplosionsCommand(sender, args);
    }

    private boolean handleBuyBlocks(CommandSender sender, String[] args) {
        // Economy integration is not available in this version
        if (sender instanceof Player player) {
            GriefPrevention.sendMessage(player, TextMode.Info, "The buy claim blocks feature requires economy integration which is not available.");
        } else {
            sender.sendMessage("The buy claim blocks feature requires economy integration which is not available.");
        }
        return true;
    }

    private boolean handleSellBlocks(CommandSender sender, String[] args) {
        // Economy integration is not available in this version
        if (sender instanceof Player player) {
            GriefPrevention.sendMessage(player, TextMode.Info, "The sell claim blocks feature requires economy integration which is not available.");
        } else {
            sender.sendMessage("The sell claim blocks feature requires economy integration which is not available.");
        }
        return true;
    }

    private boolean handleAbandon(CommandSender sender, String[] args) {
        if (args.length > 0 && "all".equalsIgnoreCase(args[0])) {
            return plugin.abandonAllClaimsHandler(sender);
        } else {
            if (sender instanceof Player player) {
                return plugin.abandonClaimHandler(player, false);
            } else {
                return false;
            }
        }
    }

    private boolean handleSiege(CommandSender sender, String[] args) {
        // Siege feature is not available in this version
        if (sender instanceof Player player) {
            GriefPrevention.sendMessage(player, TextMode.Info, "The siege feature is not available in this version.");
        } else {
            sender.sendMessage("The siege feature is not available in this version.");
        }
        return true;
    }

    private boolean handleTrapped(CommandSender sender, String[] args) {
        if (sender instanceof Player player) {
            return plugin.handleTrappedCommand(player, args);
        } else {
            return false;
        }
    }

    private boolean handleHelp(CommandSender sender, String[] args) {
        sendHelpMessage(sender, args);
        return true;
    }

    private boolean needsShovel(PlayerData playerData, Player player) {
        return playerData.getClaims().size() < 2
                && player.getGameMode() != GameMode.CREATIVE
                && player.getInventory().getItemInMainHand().getType() != plugin.config_claims_modificationTool;
    }

    private int getClaimMinRadius() {
        return (int) Math.ceil(Math.sqrt(plugin.config_claims_minArea) / 2);
    }
}
