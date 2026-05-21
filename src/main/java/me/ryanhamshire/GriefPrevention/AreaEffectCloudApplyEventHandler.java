package me.ryanhamshire.GriefPrevention;

import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Supplier;

public final class AreaEffectCloudApplyEventHandler implements Listener {

    private final EntityDamageHandler entityDamageHandler;

    public AreaEffectCloudApplyEventHandler(EntityDamageHandler entityDamageHandler) {
        this.entityDamageHandler = entityDamageHandler;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onAreaEffectCloudApply(@NotNull AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        ProjectileSource source = cloud.getSource();

        // Only handle player-thrown potions
        if (!(source instanceof Player))
            return;
        Player thrower = (Player) source;

        Collection<PotionEffect> effects = cloud.getCustomEffects();
        boolean isHarmful = effects.stream().anyMatch(effect -> {
            PotionEffectType type = effect.getType();
            return type.equals(PotionEffectType.POISON) ||
                    type.equals(PotionEffectType.SLOWNESS) ||
                    type.equals(PotionEffectType.WEAKNESS);
        });

        event.getAffectedEntities().removeIf(affected -> {
            if (affected.equals(thrower))
                return false;

            if (affected instanceof Player) {
                Player affectedPlayer = (Player) affected;
                PlayerData playerData = this.entityDamageHandler.dataStore.getPlayerData(thrower.getUniqueId());
                Claim claim = this.entityDamageHandler.dataStore.getClaimAt(affected.getLocation(), false, playerData.lastClaim);
                if (claim != null) {
                    playerData.lastClaim = claim;
                    return this.entityDamageHandler.handlePvpInClaim(thrower, affectedPlayer, affected.getLocation(), playerData, () -> {
                    });
                }
                return false;
            }
            else if (affected instanceof LivingEntity) {
                Location loc = affected.getLocation();
                Claim claim = this.entityDamageHandler.dataStore.getClaimAt(loc, false, null);

                if (claim == null)
                    return false;

                @SuppressWarnings("deprecation")
                boolean hasAccess = claim.allowAccess(thrower) == null;
                if (hasAccess) {
                    return false;
                }

                if (isHarmful) {
                    if (affected instanceof Animals) {
                        Supplier<String> override = () -> this.entityDamageHandler.instance.dataStore
                                .getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
                        final Supplier<String> noContainersReason = claim.checkPermission(thrower,
                                ClaimPermission.Container, event, override);
                        return noContainersReason != null;
                    }
                    return !(affected instanceof Monster || affected instanceof Slime || affected instanceof Phantom);
                }

                return false;
            }

            return false;
        });
    }
}
