package me.ryanhamshire.GriefPrevention;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.jetbrains.annotations.NotNull;

public final class EntityPickupItemEventHandler implements Listener {

    private final EntityEventHandler entityEventHandler;

    public EntityPickupItemEventHandler(EntityEventHandler entityEventHandler) {
        this.entityEventHandler = entityEventHandler;
    }

    @EventHandler
    public void onEntityPickUpItem(@NotNull EntityPickupItemEvent event) {
        this.entityEventHandler.onEntityPickUpItem(event);
    }
}
