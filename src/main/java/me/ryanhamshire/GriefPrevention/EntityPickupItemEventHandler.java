package me.ryanhamshire.GriefPrevention;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class EntityPickupItemEventHandler implements Listener {

    private final DataStore dataStore;

    public EntityPickupItemEventHandler(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @EventHandler
    public void onEntityPickUpItem(@NotNull EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Monster) return;

        Player player = null;
        if (event.getEntity() instanceof Player) {
            player = (Player) event.getEntity();
        }

        protectLockedDrops(event, player);
        preventPvpSpawnCamp(event, player);
    }

    private void protectLockedDrops(@NotNull EntityPickupItemEvent event, @Nullable Player player) {
        org.bukkit.entity.Item item = event.getItem();
        List<MetadataValue> data = item.getMetadata("GP_ITEMOWNER");

        if (data.isEmpty() || !(data.get(0).value() instanceof UUID)) return;
        UUID ownerID = (UUID) data.get(0).value();

        OfflinePlayer owner = GriefPrevention.instance.getServer().getOfflinePlayer(ownerID);

        if (!owner.isOnline() || (player != null && player.getUniqueId().equals(ownerID))) return;

        PlayerData playerData = this.dataStore.getPlayerData(ownerID);

        if (playerData.dropsAreUnlocked) return;

        event.setCancelled(true);

        if (player == null) return;

        if (!playerData.receivedDropUnlockAdvertisement) {
            GriefPrevention.sendMessage(owner.getPlayer(), TextMode.Instr, Messages.DropUnlockAdvertisement);
            GriefPrevention.sendMessage(player, TextMode.Err, Messages.PickupBlockedExplanation, GriefPrevention.lookupPlayerName(ownerID));
            playerData.receivedDropUnlockAdvertisement = true;
        }
    }

    private void preventPvpSpawnCamp(@NotNull EntityPickupItemEvent event, @Nullable Player player) {
        if (player == null || !GriefPrevention.instance.pvpRulesApply(player.getWorld())) return;

        if (GriefPrevention.instance.config_pvp_protectFreshSpawns
                && (GriefPrevention.instance.getItemInHand(player, EquipmentSlot.HAND).getType() == org.bukkit.Material.AIR)) {
            PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
            if (playerData.pvpImmune) {
                playerData.pvpImmune = false;
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.PvPImmunityEnd);
            }
        }
    }
}
