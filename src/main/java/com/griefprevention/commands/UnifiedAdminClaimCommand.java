package com.griefprevention.commands;

import me.ryanhamshire.GriefPrevention.*;
import me.ryanhamshire.GriefPrevention.DataStore.NoTransferException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Unified command handler for /aclaim with administrative subcommands
 */
public class UnifiedAdminClaimCommand extends UnifiedCommandHandler {

    public UnifiedAdminClaimCommand(@NotNull GriefPrevention plugin) {
        super(plugin, "aclaim");
        // Register subcommands
        registerSubcommand("restore", createRestoreTabExecutor());
        registerSubcommand("ignore", this::handleIgnore);
        registerSubcommand("mode", this::handleMode);
        registerSubcommand("adminlist", this::handleAdminList);
        registerSubcommand("list", this::handleList);
        registerSubcommand("checkexpiry", this::handleCheckExpiry);
        registerSubcommand("blocks", this::handleBlocks);
        registerSubcommand(
            "delete",
            this::handleDelete,
            "deleteclaim",
            "deleteallclaims",
            "deleteclaimsinworld",
            "deleteuserclaimsinworld",
            "deletealladminclaims"
        );
        registerSubcommand("transfer", this::handleTransfer);
        registerSubcommand("help", this::handleHelp);

        // Register standalone commands from Alias enum
        registerStandaloneCommand(Alias.AClaimRestore, createRestoreTabExecutor());
        // Legacy standalone commands - redirect to unified restore with appropriate type
        registerLegacyStandaloneCommand(
            "restorenatureaggressive",
            "griefprevention.restorenatureaggressive",
            (sender, args) -> handleRestore(sender, prependArg("aggressive", args)),
            java.util.Arrays.asList("1", "2", "3", "4", "5", "10")
        );
        registerLegacyStandaloneCommand(
            "restorenaturefill",
            "griefprevention.restorenatureaggressive",
            (sender, args) -> handleRestore(sender, prependArg("fill", args)),
            java.util.Arrays.asList("1", "2", "3", "4", "5", "10")
        );
        registerStandaloneCommand(Alias.AClaimIgnore, this::handleIgnore);
        registerStandaloneCommand(Alias.AClaimMode, this::handleMode);
        registerStandaloneCommand(Alias.AClaimAdminList, this::handleAdminList);
        registerStandaloneCommand(Alias.AClaimList, this::handleList);
        registerStandaloneCommand(Alias.AClaimCheckExpiry, this::handleCheckExpiry);
        registerStandaloneCommand(Alias.AClaimBlocks, this::handleBlocks);
        registerLegacyStandaloneCommand(
            "adjustbonusclaimblocks",
            "griefprevention.adjustclaimblocks",
            (sender, args) -> handleBlocks(sender, prependArg("bonus", args)),
            null
        );
        registerLegacyStandaloneCommand(
            "acb",
            "griefprevention.adjustclaimblocks",
            (sender, args) -> handleBlocks(sender, prependArg("bonus", args)),
            null
        );
        registerLegacyStandaloneCommand(
            "setaccruedclaimblocks",
            "griefprevention.adjustclaimblocks",
            (sender, args) -> handleBlocks(sender, prependArg("accrued", args)),
            null
        );
        registerLegacyStandaloneCommand(
            "scb",
            "griefprevention.adjustclaimblocks",
            (sender, args) -> handleBlocks(sender, prependArg("accrued", args)),
            null
        );
        registerLegacyStandaloneCommand(
            "adjustbonusclaimblocksall",
            "griefprevention.adjustclaimblocks",
            (sender, args) -> handleBlocks(sender, prependArg("bonus", prependArg("all", args))),
            null
        );
        registerLegacyStandaloneCommand(
            "acball",
            "griefprevention.adjustclaimblocks",
            (sender, args) -> handleBlocks(sender, prependArg("bonus", prependArg("all", args))),
            null
        );
        registerLegacyStandaloneCommand(
            "setaccruedclaimblocksall",
            "griefprevention.adjustclaimblocks",
            (sender, args) -> handleBlocks(sender, prependArg("accrued", prependArg("all", args))),
            null
        );
        registerLegacyStandaloneCommand(
            "scball",
            "griefprevention.adjustclaimblocks",
            (sender, args) -> handleBlocks(sender, prependArg("accrued", prependArg("all", args))),
            null
        );
        registerStandaloneCommand(Alias.AClaimDelete, this::handleDelete);
        registerStandaloneCommand(Alias.AClaimTransfer, this::handleTransfer);
        registerStandaloneCommand(Alias.AClaimHelp, this::handleHelp);
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

        // Default behavior: set admin claim mode
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return;
        }

        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
        playerData.shovelMode = ShovelMode.Admin;
        GriefPrevention.sendMessage(player, TextMode.Success, Messages.AdminClaimsMode);
        return;
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

    private boolean handleRestore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        // Check permission
        if (!player.hasPermission("griefprevention.restorenature")) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
            return true;
        }

        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());

        // Check for mode argument
        if (args.length > 0) {
            String mode = args[0].toLowerCase();
            switch (mode) {
                case "aggressive" -> {
                    playerData.shovelMode = ShovelMode.RestoreNatureAggressive;
                    GriefPrevention.sendMessage(player, TextMode.Success, Messages.RestoreNatureAggressiveActivate);
                }
                case "fill" -> {
                    // Check for radius argument
                    if (args.length > 1) {
                        try {
                            playerData.fillRadius = Integer.parseInt(args[1]);
                        } catch (NumberFormatException e) {
                            playerData.fillRadius = 2; // default
                        }
                    } else {
                        playerData.fillRadius = 2; // default
                    }
                    playerData.shovelMode = ShovelMode.RestoreNatureFill;
                    GriefPrevention.sendMessage(
                        player,
                        TextMode.Success,
                        Messages.FillModeActive,
                        String.valueOf(playerData.fillRadius)
                    );
                }
                default -> {
                    // Default restore nature mode
                    playerData.shovelMode = ShovelMode.RestoreNature;
                    GriefPrevention.sendMessage(player, TextMode.Success, Messages.RestoreNatureActivate);
                }
            }
        } else {
            // Default restore nature mode
            playerData.shovelMode = ShovelMode.RestoreNature;
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.RestoreNatureActivate);
        }

        return true;
    }

    private org.bukkit.command.TabExecutor createRestoreTabExecutor() {
        return new org.bukkit.command.TabExecutor() {
            @Override
            public boolean onCommand(
                @NotNull CommandSender sender,
                @NotNull org.bukkit.command.Command command,
                @NotNull String label,
                String[] args
            ) {
                return handleRestore(sender, args);
            }

            @Override
            public java.util.List<String> onTabComplete(
                @NotNull CommandSender sender,
                @NotNull org.bukkit.command.Command command,
                @NotNull String label,
                String[] args
            ) {
                if (args.length == 1) {
                    return java.util.Arrays.asList("nature", "aggressive", "fill")
                        .stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(java.util.stream.Collectors.toList());
                }
                // Show radius suggestions for all restore types
                if (args.length == 2) {
                    return java.util.Arrays.asList("1", "2", "3", "4", "5", "10")
                        .stream()
                        .filter(s -> s.startsWith(args[1]))
                        .collect(java.util.stream.Collectors.toList());
                }
                return java.util.Collections.emptyList();
            }
        };
    }

    private static String[] prependArg(String first, String[] rest) {
        String[] result = new String[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    private boolean handleIgnore(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return false;

        // Permission check - requires griefprevention.ignoreclaims
        if (!player.hasPermission("griefprevention.ignoreclaims")) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
            return true;
        }

        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
        playerData.ignoreClaims = !playerData.ignoreClaims;

        if (!playerData.ignoreClaims) {
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.RespectingClaims);
        } else {
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.IgnoringClaims);
        }
        return true;
    }

    private boolean handleMode(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return false;

        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());

        // No args = standalone /adminclaims command - set admin mode directly
        if (args.length == 0 || "admin".equalsIgnoreCase(args[0])) {
            playerData.shovelMode = ShovelMode.Admin;
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.AdminClaimsMode);
            return true;
        }

        GriefPrevention.sendMessage(player, TextMode.Err, Messages.CommandInvalidMode);
        return true;
    }

    private boolean handleAdminList(CommandSender sender, String[] args) {
        // Delegate to handleList which shows admin claims
        return handleList(sender, args);
    }

    private boolean handleList(CommandSender sender, String[] args) {
        // Show admin claims list (similar to /adminclaimslist)
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        // Check permission
        if (!player.hasPermission("griefprevention.adminclaims")) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoAdminClaimsPermission);
            return true;
        }

        // Find admin claims
        java.util.Vector<Claim> claims = new java.util.Vector<>();
        for (Claim claim : plugin.dataStore.getClaims()) {
            if (
                claim.ownerID == null // admin claim
            ) {
                claims.add(claim);
            }
        }

        if (claims.size() > 0) {
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ClaimsListHeader);
            for (Claim claim : claims) {
                GriefPrevention.sendMessage(
                    player,
                    TextMode.Instr,
                    GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner())
                );
            }
        } else {
            GriefPrevention.sendMessage(player, TextMode.Info, "No administrative claims found.");
        }

        return true;
    }

    private boolean handleBlocks(CommandSender sender, String[] args) {
        // Usage: /aclaim blocks <bonus|accrued> <player|all> <amount>
        if (args.length < 3) {
            if (sender instanceof Player) {
                GriefPrevention.sendMessage(
                    (Player) sender,
                    TextMode.Info,
                    "Usage: /aclaim blocks <bonus|accrued> <player|all> <amount>"
                );
            } else {
                sender.sendMessage("Usage: /aclaim blocks <bonus|accrued> <player|all> <amount>");
            }
            return true;
        }

        String type = args[0].toLowerCase();
        String target = args[1];
        int amount;

        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            if (sender instanceof Player) {
                GriefPrevention.sendMessage((Player) sender, TextMode.Err, "Invalid amount: " + args[2]);
            } else {
                sender.sendMessage("Invalid amount: " + args[2]);
            }
            return true;
        }

        if (!type.equals("bonus") && !type.equals("accrued")) {
            if (sender instanceof Player) {
                GriefPrevention.sendMessage(
                    (Player) sender,
                    TextMode.Err,
                    "Invalid block type: " + type + ". Use 'bonus' or 'accrued'."
                );
            } else {
                sender.sendMessage("Invalid block type: " + type + ". Use 'bonus' or 'accrued'.");
            }
            return true;
        }

        if (target.equalsIgnoreCase("all")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                adjustPlayerBlocks(sender, player, type, amount);
            }
            if (sender instanceof Player) {
                GriefPrevention.sendMessage(
                    (Player) sender,
                    TextMode.Success,
                    "Adjusted " + type + " blocks for all online players by " + amount + "."
                );
            } else {
                sender.sendMessage("Adjusted " + type + " blocks for all online players by " + amount + ".");
            }
        } else {
            OfflinePlayer offlinePlayer = plugin.resolvePlayerByName(target);
            if (offlinePlayer == null) {
                if (sender instanceof Player) {
                    GriefPrevention.sendMessage((Player) sender, TextMode.Err, Messages.PlayerNotFound2);
                } else {
                    sender.sendMessage(plugin.dataStore.getMessage(Messages.PlayerNotFound2));
                }
                return true;
            }
            adjustPlayerBlocks(sender, offlinePlayer, type, amount);
        }

        return true;
    }

    private void adjustPlayerBlocks(CommandSender sender, OfflinePlayer player, String type, int amount) {
        PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
        if (type.equals("bonus")) {
            playerData.setBonusClaimBlocks(playerData.getBonusClaimBlocks() + amount);
        } else {
            // accrued
            playerData.setAccruedClaimBlocks(playerData.getAccruedClaimBlocks() + amount);
        }
        plugin.dataStore.savePlayerData(player.getUniqueId(), playerData);

        if (sender instanceof Player) {
            GriefPrevention.sendMessage(
                (Player) sender,
                TextMode.Success,
                "Adjusted " + player.getName() + "'s " + type + " blocks by " + amount + "."
            );
        } else {
            sender.sendMessage("Adjusted " + player.getName() + "'s " + type + " blocks by " + amount + ".");
        }
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        // Usage: /aclaim delete [claim|player <name>|world <world>|alladmin]
        if (args.length == 0) {
            // Default: delete claim player is standing in
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Usage: /aclaim delete <claim|player <name>|world <world>|alladmin>");
                return true;
            }
            return deleteCurrentClaim(player);
        }

        String subOp = args[0].toLowerCase();

        switch (subOp) {
            case "claim" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This sub-command can only be used by players.");
                    return true;
                }
                return deleteCurrentClaim(player);
            }
            case "player" -> {
                if (args.length < 2) {
                    if (sender instanceof Player player) {
                        GriefPrevention.sendMessage(player, TextMode.Err, "Usage: /aclaim delete player <name>");
                    } else {
                        sender.sendMessage("Usage: /aclaim delete player <name>");
                    }
                    return true;
                }
                return deletePlayerClaims(sender, args[1]);
            }
            case "world" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /aclaim delete world <worldname>");
                    return true;
                }
                return deleteWorldClaims(sender, args[1], true);
            }
            case "userworld" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /aclaim delete userworld <worldname>");
                    return true;
                }
                return deleteWorldClaims(sender, args[1], false);
            }
            case "alladmin" -> {
                return deleteAllAdminClaims(sender);
            }
            default -> {
                if (sender instanceof Player player) {
                    GriefPrevention.sendMessage(player, TextMode.Err, "Unknown delete operation: " + subOp);
                } else {
                    sender.sendMessage("Unknown delete operation: " + subOp);
                }
                return true;
            }
        }
    }

    private boolean deleteCurrentClaim(Player player) {
        Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.DeleteClaimMissing);
            return true;
        }

        // deleting an admin claim additionally requires the adminclaims permission
        if (!claim.isAdminClaim() || player.hasPermission("griefprevention.adminclaims")) {
            PlayerData playerData = plugin.dataStore.getPlayerData(player.getUniqueId());
            if (claim.children.size() > 0 && !playerData.warnedAboutMajorDeletion) {
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.DeletionSubdivisionWarning);
                playerData.warnedAboutMajorDeletion = true;
            } else {
                plugin.deleteClaimPublic(claim, true);
                GriefPrevention.sendMessage(player, TextMode.Success, Messages.DeleteSuccess);
                GriefPrevention.AddLogEntry(
                    player.getName() +
                        " deleted " +
                        claim.getOwnerName() +
                        "'s claim at " +
                        GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()),
                    CustomLogEntryTypes.AdminActivity
                );
                playerData.setVisibleBoundaries(null);
                playerData.warnedAboutMajorDeletion = false;
            }
        } else {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.CantDeleteAdminClaim);
        }
        return true;
    }

    private boolean deletePlayerClaims(CommandSender sender, String playerName) {
        OfflinePlayer targetPlayer = plugin.resolvePlayerByName(playerName);
        if (targetPlayer == null) {
            if (sender instanceof Player player) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
            } else {
                sender.sendMessage("Player not found: " + playerName);
            }
            return true;
        }

        plugin.dataStore.deleteClaimsForPlayer(targetPlayer.getUniqueId(), true);

        if (sender instanceof Player player) {
            GriefPrevention.sendMessage(player, TextMode.Success, Messages.DeleteAllSuccess, targetPlayer.getName());
            plugin.dataStore.getPlayerData(player.getUniqueId()).setVisibleBoundaries(null);
        } else {
            sender.sendMessage("Deleted all claims belonging to " + targetPlayer.getName());
        }

        GriefPrevention.AddLogEntry(
            (sender instanceof Player ? ((Player) sender).getName() : "Console") +
                " deleted all claims belonging to " +
                targetPlayer.getName(),
            CustomLogEntryTypes.AdminActivity
        );
        return true;
    }

    private boolean deleteWorldClaims(CommandSender sender, String worldName, boolean includeAdmin) {
        org.bukkit.World world = Bukkit.getServer().getWorld(worldName);
        if (world == null) {
            if (sender instanceof Player player) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.WorldNotFound);
            } else {
                sender.sendMessage("World not found: " + worldName);
            }
            return true;
        }

        plugin.deleteClaimsInWorldPublic(world, includeAdmin);
        String message = includeAdmin ? "Deleted all claims in world: " : "Deleted all user claims in world: ";
        if (sender instanceof Player player) {
            GriefPrevention.sendMessage(player, TextMode.Success, message + world.getName());
        } else {
            sender.sendMessage(message + world.getName());
        }

        GriefPrevention.AddLogEntry(message + world.getName(), CustomLogEntryTypes.AdminActivity);
        return true;
    }

    private boolean deleteAllAdminClaims(CommandSender sender) {
        plugin.dataStore.deleteClaimsForPlayer(null, true);

        if (sender instanceof Player player) {
            GriefPrevention.sendMessage(player, TextMode.Success, "Deleted all administrative claims.");
        } else {
            sender.sendMessage("Deleted all administrative claims.");
        }

        GriefPrevention.AddLogEntry("Deleted all administrative claims.", CustomLogEntryTypes.AdminActivity);
        return true;
    }

    private boolean handleTransfer(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        // which claim is the user in?
        Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), false, null);
        if (claim == null) {
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.TransferClaimMissing);
            return true;
        }

        // check additional permission for admin claims
        if (claim.isAdminClaim() && !player.hasPermission("griefprevention.adminclaims")) {
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.TransferClaimPermission);
            return true;
        }

        java.util.UUID newOwnerID = null; // no argument = make an admin claim
        String ownerName = "admin";

        if (args.length > 0) {
            OfflinePlayer targetPlayer = plugin.resolvePlayerByName(args[0]);
            if (targetPlayer == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerNotFound2);
                return true;
            }
            newOwnerID = targetPlayer.getUniqueId();
            ownerName = targetPlayer.getName();
        }

        // change ownership
        try {
            plugin.changeClaimOwnerPublic(claim, newOwnerID);
        } catch (NoTransferException e) {
            GriefPrevention.sendMessage(player, TextMode.Instr, Messages.TransferTopLevel);
            return true;
        }

        // confirm
        GriefPrevention.sendMessage(player, TextMode.Success, Messages.TransferSuccess);
        GriefPrevention.AddLogEntry(
            player.getName() +
                " transferred a claim at " +
                GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()) +
                " to " +
                ownerName +
                ".",
            CustomLogEntryTypes.AdminActivity
        );

        return true;
    }

    private boolean handleCheckExpiry(CommandSender sender, String[] args) {
        // Check permission
        if (sender instanceof Player player) {
            if (!player.hasPermission("griefprevention.checkclaimexpiry")) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.NoPermissionForCommand);
                return true;
            }
        } else {
            // Console requires a player target
            if (args.length == 0) {
                sender.sendMessage("Usage: /aclaim checkexpiry <player>");
                return false;
            }
        }

        // Handle no arguments - check current claim if player is standing in one
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Console must specify a player target.");
                return false;
            }

            // Find claim at player's location
            Claim claim = plugin.dataStore.getClaimAt(player.getLocation(), true, null);
            if (claim == null) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimExpiryNoClaim);
                return true;
            }

            // Show expiry info for this specific claim
            showSingleClaimExpiry(sender, claim);
            return true;
        }

        // Handle player target argument
        String targetPlayerName = args[0];

        // Look up the player (online or offline)
        OfflinePlayer targetPlayer = plugin.resolvePlayerByName(targetPlayerName);
        if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
            if (sender instanceof Player player) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimExpiryPlayerNotFound, targetPlayerName);
            } else {
                sender.sendMessage("Could not find player: " + targetPlayerName);
            }
            return true;
        }

        // Get player data
        PlayerData playerData = plugin.dataStore.getPlayerData(targetPlayer.getUniqueId());
        if (playerData == null) {
            if (sender instanceof Player player) {
                GriefPrevention.sendMessage(player, TextMode.Err, Messages.ClaimExpiryPlayerNotFound, targetPlayerName);
            } else {
                sender.sendMessage("Could not find player data for: " + targetPlayerName);
            }
            return true;
        }

        // Get all claims owned by the player
        java.util.List<Claim> claims = playerData.getClaims();
        if (claims.isEmpty()) {
            if (sender instanceof Player player) {
                GriefPrevention.sendMessage(
                    player,
                    TextMode.Info,
                    Messages.ClaimExpiryPlayerNoClaims,
                    targetPlayer.getName()
                );
            } else {
                sender.sendMessage(targetPlayer.getName() + " has no claims.");
            }
            return true;
        }

        // Show all claims for the player
        showPlayerClaimsExpiry(sender, targetPlayer, claims);
        return true;
    }

    private void showSingleClaimExpiry(CommandSender sender, Claim claim) {
        // Show header for single claim
        if (sender instanceof Player player) {
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.ClaimExpiryHeader, claim.getOwnerName());
        } else {
            sender.sendMessage("Claim expiration for " + claim.getOwnerName() + ":");
        }

        // Show location
        String location =
            claim.getLesserBoundaryCorner().getWorld().getName() +
            " (" +
            claim.getLesserBoundaryCorner().getBlockX() +
            ", " +
            claim.getLesserBoundaryCorner().getBlockY() +
            ", " +
            claim.getLesserBoundaryCorner().getBlockZ() +
            ")";

        if (sender instanceof Player player) {
            GriefPrevention.sendMessage(player, TextMode.Info, Messages.ClaimExpiryLocation, location);
        } else {
            sender.sendMessage("Location: " + location);
        }

        // Show expiry info
        String expiryInfo = getSingleClaimExpiryInfo(claim);
        if (sender instanceof Player player) {
            player.sendMessage(expiryInfo);
        } else {
            sender.sendMessage(expiryInfo);
        }
    }

    private void showPlayerClaimsExpiry(
        CommandSender sender,
        OfflinePlayer targetPlayer,
        java.util.List<Claim> claims
    ) {
        // Check if player is online
        boolean isOnline = targetPlayer.isOnline();

        // Send header
        if (sender instanceof Player player) {
            GriefPrevention.sendMessage(
                player,
                TextMode.Info,
                Messages.ClaimExpiryPlayerHeading,
                targetPlayer.getName()
            );
            if (isOnline) {
                int maxDays = plugin.config_claims_expirationDays;
                if (maxDays > 0) {
                    player.sendMessage(
                        "§7Player is online. Claims will expire after §e" + maxDays + " days§7 of inactivity."
                    );
                } else {
                    player.sendMessage("§7Player is online. Claims are configured to never expire.");
                }
            }
        } else {
            sender.sendMessage("Claim expiration for " + targetPlayer.getName() + ":");
            if (isOnline) {
                int maxDays = plugin.config_claims_expirationDays;
                if (maxDays > 0) {
                    sender.sendMessage(
                        "Player is online. Claims will expire after " + maxDays + " days of inactivity."
                    );
                } else {
                    sender.sendMessage("Player is online. Claims are configured to never expire.");
                }
            }
        }

        // Display each claim with expiry info
        for (Claim claim : claims) {
            String expiryInfo = getClaimExpiryInfo(claim, targetPlayer, isOnline);
            String location =
                claim.getLesserBoundaryCorner().getWorld().getName() +
                " (" +
                claim.getLesserBoundaryCorner().getBlockX() +
                ", " +
                claim.getLesserBoundaryCorner().getBlockY() +
                ", " +
                claim.getLesserBoundaryCorner().getBlockZ() +
                ")";

            if (sender instanceof Player player) {
                GriefPrevention.sendMessage(player, TextMode.Info, Messages.ClaimExpiryListEntry, location, expiryInfo);
            } else {
                sender.sendMessage("- " + location + ": " + expiryInfo);
            }
        }
    }

    private String getSingleClaimExpiryInfo(Claim claim) {
        // Admin claims never expire
        if (claim.isAdminClaim()) {
            return "§aAdministrative claim (never expires)";
        }

        // Get the claim owner
        OfflinePlayer owner = null;
        if (claim.getOwnerID() != null) {
            owner = Bukkit.getOfflinePlayer(claim.getOwnerID());
        }

        if (owner == null || !owner.hasPlayedBefore()) {
            return "§aAdministrative claim (never expires)";
        }

        // Check if owner is online
        boolean isOnline = owner.isOnline();

        // If player is online, show max expiry time
        if (isOnline) {
            int maxDays = plugin.config_claims_expirationDays;
            if (maxDays > 0) {
                return "§7Owner is online. Claim will expire after §e" + maxDays + " days§7 of inactivity.";
            } else {
                return "§aOwner is online. Claim is configured to never expire.";
            }
        }

        // Get expiry configuration
        int expirationDays = plugin.config_claims_expirationDays;

        // Check if this is a chest claim
        int areaOfDefaultClaim = 0;
        if (plugin.config_claims_automaticClaimsForNewPlayersRadius >= 0) {
            areaOfDefaultClaim = (int) Math.pow(plugin.config_claims_automaticClaimsForNewPlayersRadius * 2 + 1, 2);
        }

        PlayerData ownerData = plugin.dataStore.getPlayerData(owner.getUniqueId());
        boolean isChestClaim =
            ownerData.getClaims().size() == 1 &&
            claim.getArea() <= areaOfDefaultClaim &&
            plugin.config_claims_chestClaimExpirationDays > 0;

        if (isChestClaim) {
            expirationDays = plugin.config_claims_chestClaimExpirationDays;
        }

        // If expiration is disabled
        if (expirationDays <= 0) {
            return "§aThis claim will never expire.";
        }

        // Calculate days since last login
        long lastPlayed = owner.getLastPlayed();
        long now = System.currentTimeMillis();
        long daysSinceLogin = (now - lastPlayed) / (1000 * 60 * 60 * 24);
        long daysUntilExpiry = expirationDays - daysSinceLogin;

        if (daysUntilExpiry > 0) {
            return "§eExpires in " + daysUntilExpiry + " day" + (daysUntilExpiry == 1 ? "" : "s");
        } else if (daysUntilExpiry == 0) {
            return "§cExpires today";
        } else {
            long daysExpired = Math.abs(daysUntilExpiry);
            return "§cExpired " + daysExpired + " day" + (daysExpired == 1 ? "" : "s") + " ago";
        }
    }

    private String getClaimExpiryInfo(Claim claim, OfflinePlayer player, boolean isOnline) {
        // Admin claims never expire
        if (claim.isAdminClaim()) {
            return "§aAdministrative claim (never expires)";
        }

        // If player is online, show max expiry time
        if (isOnline) {
            return "§7(see above)";
        }

        // Get expiry configuration
        int expirationDays = plugin.config_claims_expirationDays;

        // Check if this is a chest claim
        int areaOfDefaultClaim = 0;
        if (plugin.config_claims_automaticClaimsForNewPlayersRadius >= 0) {
            areaOfDefaultClaim = (int) Math.pow(plugin.config_claims_automaticClaimsForNewPlayersRadius * 2 + 1, 2);
        }

        PlayerData ownerData = plugin.dataStore.getPlayerData(player.getUniqueId());
        boolean isChestClaim =
            ownerData.getClaims().size() == 1 &&
            claim.getArea() <= areaOfDefaultClaim &&
            plugin.config_claims_chestClaimExpirationDays > 0;

        if (isChestClaim) {
            expirationDays = plugin.config_claims_chestClaimExpirationDays;
        }

        // If expiration is disabled
        if (expirationDays <= 0) {
            return "§aNever expires";
        }

        // Calculate days since last login
        long lastPlayed = player.getLastPlayed();
        long now = System.currentTimeMillis();
        long daysSinceLogin = (now - lastPlayed) / (1000 * 60 * 60 * 24);
        long daysUntilExpiry = expirationDays - daysSinceLogin;

        if (daysUntilExpiry > 0) {
            return "§eExpires in " + daysUntilExpiry + " day" + (daysUntilExpiry == 1 ? "" : "s");
        } else if (daysUntilExpiry == 0) {
            return "§cExpires today";
        } else {
            long daysExpired = Math.abs(daysUntilExpiry);
            return "§cExpired " + daysExpired + " day" + (daysExpired == 1 ? "" : "s") + " ago";
        }
    }

    private boolean handleHelp(CommandSender sender, String[] args) {
        sendHelpMessage(sender, args);
        return true;
    }
}
