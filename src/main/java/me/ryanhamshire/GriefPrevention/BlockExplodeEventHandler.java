package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.compat.CompatUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public final class BlockExplodeEventHandler implements Listener {

    private final DataStore dataStore;

    public BlockExplodeEventHandler(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockExplode(BlockExplodeEvent explodeEvent)
    {
        if (explodeEvent.blockList().isEmpty()) return;

        Object explosionResult = CompatUtil.getExplosionResult(explodeEvent);
        if (CompatUtil.isTriggerBlockExplosion(explosionResult))
        {
            handleExplodeInteract(explodeEvent.getBlock().getLocation(), null, explodeEvent.blockList(), explodeEvent);
        }
        else
        {
            handleExplosion(explodeEvent.getBlock().getLocation(), null, explodeEvent.blockList(), explodeEvent);
        }
    }

    private void handleExplosion(@NotNull Location location, @Nullable Entity entity, @NotNull List<Block> blocks, BlockExplodeEvent explodeEvent)
    {
        World world = location.getWorld();
        if (world == null || !GriefPrevention.instance.claimsEnabledForWorld(world)) return;

        for (Block block : blocks)
        {
            Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, null);
            if (claim != null)
            {
                explodeEvent.setCancelled(true);
                return;
            }
        }
    }

    private void handleExplodeInteract(@NotNull Location location, @Nullable Entity entity, @NotNull List<Block> blocks, @NotNull Event event)
    {
        World world = location.getWorld();
        if (world == null || !GriefPrevention.instance.claimsEnabledForWorld(world)) return;

        Player player = null;
        PlayerData playerData = null;

        if (entity instanceof Projectile)
        {
            ProjectileSource source = ((Projectile) entity).getShooter();
            if (source instanceof Player)
            {
                player = (Player) source;
                playerData = this.dataStore.getPlayerData(player.getUniqueId());
            }
        }

        for (Block block : blocks)
        {
            Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, playerData == null ? null : playerData.lastClaim);
            if (claim != null)
            {
                if (player != null)
                {
                    Supplier<String> noBuildReason = claim.checkPermission(player, ClaimPermission.Build, event);
                    if (noBuildReason != null)
                    {
                        if (event instanceof Cancellable)
                        {
                            ((Cancellable) event).setCancelled(true);
                        }
                        return;
                    }
                }
                else
                {
                    if (event instanceof Cancellable)
                    {
                        ((Cancellable) event).setCancelled(true);
                    }
                    return;
                }
            }
        }
    }
}
