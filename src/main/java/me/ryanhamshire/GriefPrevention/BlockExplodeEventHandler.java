package me.ryanhamshire.GriefPrevention;

import me.ryanhamshire.GriefPrevention.compat.CompatUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
            handleExplosion(explodeEvent.getBlock().getLocation(), null, explodeEvent.blockList());
        }
    }

    private void handleExplosion(@NotNull Location location, @Nullable Entity entity, @NotNull List<Block> blocks)
    {
        World world = location.getWorld();
        if (world == null || !GriefPrevention.instance.claimsEnabledForWorld(world)) return;

        // Protect claimed blocks by removing them from the explosion, never by cancelling the
        // event. Cancelling a BlockExplodeEvent suppresses the whole explosion - including the
        // knockback the server delivers to players - which silently broke effects like the
        // mace's Wind Burst self-launch inside claims. This mirrors
        // EntityEventHandler#handleExplosion.
        List<Block> explodedBlocks = new ArrayList<>();
        Claim cachedClaim = null;

        for (Block block : blocks)
        {
            Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, cachedClaim);

            if (claim == null)
            {
                explodedBlocks.add(block);
                continue;
            }

            cachedClaim = claim;

            // Respect the same explosion permissions as entity-caused explosions, so
            // /claimexplosions works here too.
            if (!GriefPrevention.instance.config_blockClaimExplosions || claim.areExplosivesAllowed)
            {
                explodedBlocks.add(block);
            }
        }

        blocks.clear();
        blocks.addAll(explodedBlocks);
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

        // As above: strip protected blocks rather than cancelling, so non-block effects of the
        // explosion (notably player knockback) still happen. Mirrors
        // EntityEventHandler#handleExplodeInteract.
        List<Block> removed = new ArrayList<>();
        Claim cachedClaim = playerData == null ? null : playerData.lastClaim;

        for (Block block : blocks)
        {
            Claim claim = this.dataStore.getClaimAt(block.getLocation(), false, cachedClaim);
            if (claim == null) continue;

            cachedClaim = claim;

            // With no known player, nothing can be authorised - protect the block.
            if (player == null)
            {
                removed.add(block);
                continue;
            }

            Supplier<String> noBuildReason = claim.checkPermission(player, ClaimPermission.Build, event);
            if (noBuildReason != null)
            {
                removed.add(block);
            }
        }

        if (playerData != null && cachedClaim != null)
            playerData.lastClaim = cachedClaim;

        blocks.removeAll(removed);
    }
}
