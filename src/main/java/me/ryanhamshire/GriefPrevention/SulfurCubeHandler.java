package me.ryanhamshire.GriefPrevention;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.util.Vector;

import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;

import java.util.function.Supplier;

public class SulfurCubeHandler implements Listener
{
    private final GriefPrevention instance;
    private final DataStore dataStore;

    public SulfurCubeHandler(GriefPrevention instance, DataStore dataStore)
    {
        this.instance = instance;
        this.dataStore = dataStore;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerSwing(PlayerAnimationEvent event)
    {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING)
            return;

        Player player = event.getPlayer();
        Entity target = getTargetSulfurCube(player);
        if (target == null)
            return;

        if (!instance.claimsEnabledForWorld(target.getWorld()))
            return;

        PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
        if (playerData.ignoreClaims)
            return;

        Claim claim = this.dataStore.getClaimAt(target.getLocation(), false, playerData.lastClaim);
        if (claim == null)
            return;

        playerData.lastClaim = claim;
        Supplier<String> noBuildReason = claim.checkPermission(player, ClaimPermission.Build, event);
        if (noBuildReason != null)
        {
            Location frozenLocation = target.getLocation().clone();
            SchedulerUtil.runLaterEntity(instance, target, () ->
            {
                if (target.isValid())
                {
                    target.teleport(frozenLocation);
                    target.setVelocity(new Vector(0, 0, 0));
                }
            }, 1L);

            GriefPrevention.sendRateLimitedErrorMessage(player, noBuildReason.get());
        }
    }

    private Entity getTargetSulfurCube(Player player)
    {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        double maxDistance = player.getGameMode() == org.bukkit.GameMode.CREATIVE ? 5.0 : 3.0;

        Entity closest = null;
        double closestDot = -1;

        for (Entity entity : world.getNearbyEntities(eye, maxDistance, maxDistance, maxDistance))
        {
            if (entity.equals(player))
                continue;
            if (!entity.getType().name().equals("SULFUR_CUBE"))
                continue;

            Vector toEntity = entity.getLocation().toVector().subtract(eye.toVector());
            double distance = toEntity.length();
            if (distance > maxDistance || distance < 0.1)
                continue;

            Vector toEntityNorm = toEntity.normalize();
            double dot = direction.dot(toEntityNorm);
            double tolerance = Math.max(0.85, 1.0 - (0.3 / distance));

            if (dot > closestDot && dot >= tolerance)
            {
                closestDot = dot;
                closest = entity;
            }
        }

        return closest;
    }
}
