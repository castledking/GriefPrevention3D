package me.ryanhamshire.GriefPrevention;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CreativeEntityLimitTest {
    @AfterEach
    void clearPlugin() {
        GriefPrevention.instance = null;
    }

    @Test
    void creativeSpawnIsCancelledWhenClaimIsAtEntityLimit() {
        GriefPrevention plugin = mock(GriefPrevention.class);
        GriefPrevention.instance = plugin;
        DataStore dataStore = mock(DataStore.class);
        Claim claim = mock(Claim.class);
        Location location = mock(Location.class);
        CreatureSpawnEvent event = mock(CreatureSpawnEvent.class);

        when(plugin.creativeRulesApply(location)).thenReturn(true);
        when(event.getLocation()).thenReturn(location);
        when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);
        when(event.getEntityType()).thenReturn(EntityType.COW);
        when(dataStore.getClaimAt(location, false, null)).thenReturn(claim);
        when(claim.allowMoreEntities(true)).thenReturn("too many entities");

        new EntityEventHandler(dataStore, plugin).onEntitySpawn(event);

        verify(event).setCancelled(true);
    }
}
