package com.griefprevention.addon.gp3d;

import com.griefprevention.api.claimcommand.ClaimCommandContext;
import com.griefprevention.api.claimcommand.ClaimCommandMode;
import com.griefprevention.api.claimcommand.ClaimCommandSubcommand;
import com.griefprevention.events.BoundaryVisualizationEvent;
import com.griefprevention.visualization.Boundary;
import com.griefprevention.visualization.BoundaryVisualization;
import com.griefprevention.visualization.VisualizationType;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.TextMode;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GP3DAddon extends JavaPlugin implements Listener
{
    private static final String LEGACY_MODE_PERMISSION = "griefprevention.subdivide3d";

    private final StackedSubdivisionGeometry geometry = new StackedSubdivisionGeometry();
    private final StackedSubdivisionStyle style = new StackedSubdivisionStyle();
    private final StackedSubdivisionToolHandler toolHandler = new StackedSubdivisionToolHandler(this);
    private final ClaimCommandMode mode = new StackedMode();
    private final ClaimCommandSubcommand subcommand = new StackedSubcommand();
    private final Map<UUID, SelectionSession> sessions = new ConcurrentHashMap<>();

    private @Nullable GriefPrevention griefPrevention;
    private boolean visualizationGlow;

    @Override
    public void onEnable()
    {
        Plugin dependency = getServer().getPluginManager().getPlugin("GriefPrevention");
        if (!(dependency instanceof GriefPrevention griefPrevention))
        {
            getLogger().severe("GriefPrevention is required.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.griefPrevention = griefPrevention;
        saveDefaultConfig();
        visualizationGlow = getConfig().getBoolean("visualizationGlow", true);
        griefPrevention.getClaimGeometryRegistry().register(geometry);
        griefPrevention.getVisualizationStyleRegistry().register(style);
        griefPrevention.getClaimToolHandlerRegistry().register(toolHandler);
        griefPrevention.getClaimCommandExtensionRegistry().registerMode(mode);
        griefPrevention.getClaimCommandExtensionRegistry().registerSubcommand(subcommand);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable()
    {
        if (griefPrevention == null)
        {
            return;
        }

        griefPrevention.getClaimCommandExtensionRegistry().unregisterMode(mode.getName());
        griefPrevention.getClaimCommandExtensionRegistry().unregisterSubcommand(subcommand.getName());
        griefPrevention.getClaimToolHandlerRegistry().unregister(toolHandler);
        griefPrevention.getVisualizationStyleRegistry().unregister(style.getKey());
        griefPrevention.getClaimGeometryRegistry().unregister(geometry.getKey());
        GlowingStackedSubdivisionVisualization.clearAllDisplays();
        sessions.clear();
    }

    boolean isModeActive(@NotNull UUID playerId)
    {
        return sessions.getOrDefault(playerId, SelectionSession.EMPTY).active();
    }

    @NotNull SelectionSession getOrCreateSession(@NotNull UUID playerId)
    {
        return sessions.computeIfAbsent(playerId, ignored -> SelectionSession.EMPTY);
    }

    void startSelection(@NotNull Player player, @NotNull Claim parent, @NotNull Location firstCorner)
    {
        sessions.put(player.getUniqueId(), new SelectionSession(true, parent, firstCorner));
        GriefPrevention.sendMessage(player, TextMode.Instr, "First corner set. Right-click the second corner to create a stacked subdivision.");
        BoundaryVisualization.visualizeArea(player, new BoundingBox(firstCorner, firstCorner), style);
    }

    void clearSelection(@NotNull UUID playerId)
    {
        SelectionSession current = sessions.get(playerId);
        if (current != null)
        {
            sessions.put(playerId, new SelectionSession(current.active(), null, null));
        }
    }

    private void enableMode(@NotNull Player player)
    {
        sessions.put(player.getUniqueId(), new SelectionSession(true, null, null));
        GriefPrevention.sendMessage(player, TextMode.Instr,
                "3D subdivision mode enabled. Right-click two corners inside a top-level claim with the golden shovel.");
        GriefPrevention.sendMessage(player, TextMode.Info,
                "Use /claim mode basic, /claim stacked off, or /3dsubdivideclaims off to leave this mode.");
    }

    private void disableMode(@NotNull UUID playerId, boolean forgetSelection)
    {
        if (forgetSelection)
        {
            sessions.remove(playerId);
            return;
        }

        SelectionSession current = sessions.get(playerId);
        if (current == null)
        {
            return;
        }

        sessions.put(playerId, new SelectionSession(false, null, null));
    }

    private static void clearToolState(@NotNull PlayerData playerData)
    {
        playerData.claimResizing = null;
        playerData.claimSubdividing = null;
        playerData.lastShovelLocation = null;
    }

    void visualizeClaim(@NotNull Player player, @NotNull Claim claim)
    {
        Claim root = claim.parent == null ? claim : claim.parent;
        Set<Boundary> boundaries = new LinkedHashSet<>();
        boundaries.add(new Boundary(root, root.isAdminClaim() ? VisualizationType.ADMIN_CLAIM : VisualizationType.CLAIM));
        if (claim.getGeometryKey().equals(StackedSubdivisionGeometry.KEY))
        {
            boundaries.add(new Boundary(claim.getVisualizationBounds(), style, claim));
        }
        else if (claim.parent != null)
        {
            boundaries.add(new Boundary(claim, VisualizationType.SUBDIVISION));
        }

        BoundaryVisualization.callAndVisualize(new BoundaryVisualizationEvent(player, boundaries, player.getEyeLocation().getBlockY()));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBoundaryVisualization(@NotNull BoundaryVisualizationEvent event)
    {
        Collection<Boundary> boundaries = event.getBoundaries();
        List<Boundary> replacements = new ArrayList<>();
        boolean requiresCustomProvider = false;

        for (Boundary boundary : List.copyOf(boundaries))
        {
            if (style.getKey().equals(boundary.style().getKey()))
            {
                requiresCustomProvider = true;
                continue;
            }

            Claim claim = boundary.claim();
            if (claim == null || !StackedSubdivisionGeometry.KEY.equals(claim.getGeometryKey()))
            {
                continue;
            }

            if (boundary.type() == VisualizationType.SUBDIVISION)
            {
                boundaries.remove(boundary);
                replacements.add(new Boundary(claim.getVisualizationBounds(), style, claim));
                requiresCustomProvider = true;
            }
        }

        boundaries.addAll(replacements);
        if (requiresCustomProvider)
        {
            if (visualizationGlow)
            {
                event.setProvider((world, visualizeFrom, height) ->
                        new GlowingStackedSubdivisionVisualization(this, world, visualizeFrom, height));
            }
            else
            {
                event.setProvider(StackedSubdivisionVisualization::new);
            }
        }
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event)
    {
        sessions.remove(event.getPlayer().getUniqueId());
        GlowingStackedSubdivisionVisualization.clearDisplaysForPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemHeldChange(@NotNull PlayerItemHeldEvent event)
    {
        if (griefPrevention == null)
        {
            return;
        }

        Player player = event.getPlayer();
        if (!isModeActive(player.getUniqueId()))
        {
            return;
        }

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        Material newType = newItem == null ? Material.AIR : newItem.getType();
        if (newType == griefPrevention.config_claims_modificationTool
                || newType == griefPrevention.config_claims_investigationTool)
        {
            return;
        }

        disableMode(player.getUniqueId(), true);

        PlayerData playerData = griefPrevention.dataStore.getPlayerData(player.getUniqueId());
        clearToolState(playerData);
        GriefPrevention.sendMessage(player, TextMode.Success, Messages.BasicClaimsMode);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommandPreprocess(@NotNull PlayerCommandPreprocessEvent event)
    {
        String command = event.getMessage().toLowerCase(Locale.ROOT);
        if (command.equals("/3dsubdivideclaims")
                || command.startsWith("/3dsubdivideclaims ")
                || command.equals("/3dsubdivideclaim")
                || command.startsWith("/3dsubdivideclaim ")
                || command.equals("/3dsub")
                || command.startsWith("/3dsub "))
        {
            return;
        }

        if (command.equals("/claim stacked")
                || command.startsWith("/claim stacked ")
                || command.equals("/claim 3dsubdivide")
                || command.startsWith("/claim 3dsubdivide ")
                || command.equals("/claim mode stacked")
                || command.startsWith("/claim mode stacked ")
                || command.equals("/claim mode stacked3d")
                || command.startsWith("/claim mode stacked3d ")
                || command.equals("/claim mode 3d")
                || command.startsWith("/claim mode 3d "))
        {
            return;
        }

        if (command.startsWith("/claim")
                || command.startsWith("/basicclaims")
                || command.startsWith("/adminclaims")
                || command.startsWith("/subdivideclaims"))
        {
            UUID playerId = event.getPlayer().getUniqueId();
            disableMode(playerId, true);
            if (griefPrevention != null)
            {
                clearToolState(griefPrevention.dataStore.getPlayerData(playerId));
            }
        }
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args)
    {
        if (!(sender instanceof Player player))
        {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("3dsubdivideclaims") || name.equals("3dsubdivideclaim") || name.equals("3dsub"))
        {
            return handleLegacyCommand(player, args);
        }

        return false;
    }

    private boolean handleLegacyCommand(@NotNull Player player, @NotNull String[] args)
    {
        if (!player.hasPermission(LEGACY_MODE_PERMISSION))
        {
            GriefPrevention.sendMessage(player, TextMode.Err, "You don't have permission to create 3D subdivisions.");
            return true;
        }

        return handleModeCommand(player, args);
    }

    private boolean handleModeCommand(@NotNull ClaimCommandContext context, @NotNull String[] args)
    {
        return handleModeCommand(context.getPlayer(), args);
    }

    private boolean handleModeCommand(@NotNull Player player, @NotNull String[] args)
    {
        PlayerData playerData = griefPrevention != null
                ? griefPrevention.dataStore.getPlayerData(player.getUniqueId())
                : null;

        if (args.length > 0)
        {
            String action = args[0].toLowerCase(Locale.ROOT);
            if ("off".equals(action) || "disable".equals(action))
            {
                disableMode(player.getUniqueId(), true);
                if (playerData != null)
                {
                    clearToolState(playerData);
                }
                GriefPrevention.sendMessage(player, TextMode.Info, "3D subdivision mode disabled.");
                return true;
            }

            if ("cancel".equals(action) || "clear".equals(action))
            {
                clearSelection(player.getUniqueId());
                if (playerData != null)
                {
                    clearToolState(playerData);
                }
                GriefPrevention.sendMessage(player, TextMode.Info, "3D subdivision selection cleared.");
                return true;
            }
        }

        enableMode(player);
        return true;
    }

    record SelectionSession(boolean active, @Nullable Claim parent, @Nullable Location firstCorner)
    {
        private static final SelectionSession EMPTY = new SelectionSession(false, null, null);
    }

    private final class StackedMode implements ClaimCommandMode
    {
        @Override
        public @NotNull String getName()
        {
            return "stacked";
        }

        @Override
        public @NotNull List<String> getAliases()
        {
            return List.of("stacked3d", "3d");
        }

        @Override
        public boolean onCommand(@NotNull ClaimCommandContext context, @NotNull String[] args)
        {
            return handleModeCommand(context, args);
        }

        @Override
        public @NotNull List<String> onTabComplete(@NotNull ClaimCommandContext context, @NotNull String[] args)
        {
            return args.length <= 1 ? List.of("cancel", "off") : List.of();
        }
    }

    private final class StackedSubcommand implements ClaimCommandSubcommand
    {
        @Override
        public @NotNull String getName()
        {
            return "3dsubdivide";
        }

        @Override
        public @NotNull List<String> getAliases()
        {
            return List.of("stacked", "stacked3d", "3d");
        }

        @Override
        public boolean onCommand(@NotNull ClaimCommandContext context, @NotNull String[] args)
        {
            return handleModeCommand(context, args);
        }

        @Override
        public @NotNull List<String> onTabComplete(@NotNull ClaimCommandContext context, @NotNull String[] args)
        {
            return args.length <= 1 ? List.of("cancel", "off") : List.of();
        }
    }
}
