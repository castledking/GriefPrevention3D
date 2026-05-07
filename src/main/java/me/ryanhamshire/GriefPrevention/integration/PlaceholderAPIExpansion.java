package me.ryanhamshire.GriefPrevention.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlaceholderAPIExpansion extends PlaceholderExpansion
{
    private final GriefPrevention plugin;

    public PlaceholderAPIExpansion(@NotNull GriefPrevention plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier()
    {
        return "gp3d";
    }

    @Override
    public @NotNull String getAuthor()
    {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion()
    {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist()
    {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params)
    {
        Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
        if (player == null)
        {
            return "";
        }

        Location location = player.getLocation();
        Claim topLevelClaim = plugin.dataStore.getClaimAt(location, false, null);
        Claim claim = findInnermostClaim(topLevelClaim, location);

        return switch (params.toLowerCase()) {
            case "in_subdivision" -> claim != null && claim.parent != null ? "true" : "false";
            case "in_3d_subdivision" -> claim != null && claim.parent != null && claim.is3D() ? "true" : "false";
            default -> "";
        };
    }

    private Claim findInnermostClaim(Claim claim, Location location)
    {
        if (claim == null || !contains(claim, location))
        {
            return null;
        }

        Claim innermost = claim;
        for (Claim child : claim.children)
        {
            Claim childMatch = findInnermostClaim(child, location);
            if (childMatch != null)
            {
                innermost = childMatch;
            }
        }

        return innermost;
    }

    private boolean contains(Claim claim, Location location)
    {
        World world = location.getWorld();
        if (world == null || !world.equals(claim.getLesserBoundaryCorner().getWorld()))
        {
            return false;
        }

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        int minX = Math.min(claim.getLesserBoundaryCorner().getBlockX(), claim.getGreaterBoundaryCorner().getBlockX());
        int maxX = Math.max(claim.getLesserBoundaryCorner().getBlockX(), claim.getGreaterBoundaryCorner().getBlockX());
        int minZ = Math.min(claim.getLesserBoundaryCorner().getBlockZ(), claim.getGreaterBoundaryCorner().getBlockZ());
        int maxZ = Math.max(claim.getLesserBoundaryCorner().getBlockZ(), claim.getGreaterBoundaryCorner().getBlockZ());
        int minY = claim.is3D() ? Math.min(claim.getLesserBoundaryCorner().getBlockY(), claim.getGreaterBoundaryCorner().getBlockY()) : world.getMinHeight();
        int maxY = claim.is3D() ? Math.max(claim.getLesserBoundaryCorner().getBlockY(), claim.getGreaterBoundaryCorner().getBlockY()) : world.getMaxHeight();

        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
