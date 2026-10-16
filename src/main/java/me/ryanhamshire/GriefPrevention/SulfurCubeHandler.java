package me.ryanhamshire.GriefPrevention;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.ryanhamshire.GriefPrevention.compat.CompatUtil;
import me.ryanhamshire.GriefPrevention.util.SchedulerUtil;

import java.util.function.Supplier;

public class SulfurCubeHandler implements Listener
{
    /**
     * Sulfur cubes arrived in Minecraft 26.2. Older servers have nothing to protect, and 1.8.8 has
     * no {@code ProjectileHitEvent.getHitEntity()} to call.
     */
    private static final boolean SULFUR_CUBES = CompatUtil.canCheckEntityType("SulfurCube");

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
        if (playerData.isIgnoringClaims())
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
                    CompatUtil.teleportSafely(target, frozenLocation);
                    target.setVelocity(new Vector(0, 0, 0));
                }
            }, 1L);

            GriefPrevention.sendRateLimitedErrorMessage(player, noBuildReason.get());
        }
    }

    /**
     * A cube holding a block takes no damage from a hit, so no damage event fires: the hit knocks it
     * away, and a burning arrow lights a TNT cube's fuse. A projectile from a player who could not
     * hurt the cube is stopped as it hits, as a creature's damage would be.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event)
    {
        if (!SULFUR_CUBES)
            return;

        Entity cube = event.getHitEntity();
        ProjectileSource shooter = event.getEntity().getShooter();
        if (!CompatUtil.isEntityType(cube, "SULFUR_CUBE") || !(shooter instanceof Player))
            return;

        Player player = (Player) shooter;
        Supplier<String> denial = denyHit(this.instance, this.dataStore, player, cube, event);
        if (denial == null)
            return;

        CompatUtil.cancelIfPossible(event);
        GriefPrevention.sendRateLimitedErrorMessage(player, denial.get());
    }

    /**
     * Whether a player may hit a sulfur cube, by the rule for hurting a claimed creature: container
     * trust in the claim it is in, with {@code ProtectCreatures} on.
     *
     * @return why the player may not, or null when they may
     */
    static @Nullable Supplier<String> denyHit(
            @NotNull GriefPrevention instance,
            @NotNull DataStore dataStore,
            @NotNull Player player,
            @NotNull Entity cube,
            @Nullable Event event)
    {
        if (!instance.config_claims_protectCreatures || !instance.claimsEnabledForWorld(cube.getWorld()))
            return null;

        PlayerData playerData = dataStore.getPlayerData(player.getUniqueId());
        Claim claim = dataStore.getClaimAt(cube.getLocation(), false, playerData.lastClaim);
        if (claim == null)
            return null;

        playerData.lastClaim = claim;
        Supplier<String> override = () ->
        {
            String message = dataStore.getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
            if (player.hasPermission("griefprevention.ignoreclaims"))
                message += "  " + dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
            return message;
        };
        return claim.checkPermission(player, ClaimPermission.Container, event, override);
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
