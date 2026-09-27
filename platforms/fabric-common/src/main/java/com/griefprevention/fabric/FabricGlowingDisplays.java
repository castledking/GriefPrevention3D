package com.griefprevention.fabric;

import com.griefprevention.fabric.mixin.BlockDisplayAccessor;
import com.griefprevention.fabric.mixin.DisplayAccessor;
import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Glowing block-display outlines for boundary visualizations, shown to one player only.
 *
 * <p>The Fabric counterpart of Paper's GlowingVisualization. Paper spawns real entities and hides
 * them from everyone else; vanilla has no per-player visibility, so these displays never join the
 * world. Each is built as an unsaved entity only to encode its spawn and data packets, which means no
 * other player can see it and nothing is left behind if the server stops mid-visualization.
 */
final class FabricGlowingDisplays
{
    /** Slightly larger than a block, so the outline wraps the fake block drawn at the same spot. */
    private static final float OUTLINE_SCALE = 1.005F;
    private static final float OUTLINE_OFFSET = -(OUTLINE_SCALE - 1.0F) / 2.0F;
    /** A multiple of the entity render distance; 1.5 is about 96 blocks, as on Paper. */
    private static final float VIEW_RANGE = 1.5F;
    private static final int FULL_BRIGHTNESS = 12;

    private FabricGlowingDisplays()
    {
    }

    /**
     * @return the client-side entity id, for {@link #remove}
     */
    static int spawn(
            @NotNull ServerPlayer player,
            @NotNull ServerLevel level,
            @NotNull BlockPos pos,
            @NotNull BlockState state,
            int glowColor)
    {
        Display.BlockDisplay display = new Display.BlockDisplay(blockDisplayType(), level);
        display.setPos(pos.getX(), pos.getY(), pos.getZ());
        ((BlockDisplayAccessor) display).griefPrevention$setBlockState(state);

        DisplayAccessor accessor = (DisplayAccessor) display;
        accessor.griefPrevention$setTransformation(new Transformation(
                new Vector3f(OUTLINE_OFFSET, OUTLINE_OFFSET, OUTLINE_OFFSET),
                new Quaternionf(),
                new Vector3f(OUTLINE_SCALE, OUTLINE_SCALE, OUTLINE_SCALE),
                new Quaternionf()
        ));
        accessor.griefPrevention$setBrightnessOverride(new Brightness(FULL_BRIGHTNESS, FULL_BRIGHTNESS));
        accessor.griefPrevention$setViewRange(VIEW_RANGE);
        accessor.griefPrevention$setShadowRadius(0.0F);
        accessor.griefPrevention$setShadowStrength(0.0F);
        accessor.griefPrevention$setGlowColorOverride(glowColor);
        display.setGlowingTag(true);

        player.connection.send(new ClientboundAddEntityPacket(display, 0, pos));
        List<SynchedEntityData.DataValue<?>> data = display.getEntityData().getNonDefaultValues();
        if (data != null && !data.isEmpty())
        {
            player.connection.send(new ClientboundSetEntityDataPacket(display.getId(), data));
        }
        return display.getId();
    }

    /**
     * Looked up by id: 26.2 moved the entity type constants from {@code EntityType} to a new
     * {@code EntityTypes} class, so the field this adapter would compile against is gone there.
     */
    private static @NotNull EntityType<?> blockDisplayType()
    {
        return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("block_display"));
    }

    static void remove(@NotNull ServerPlayer player, int @NotNull... entityIds)
    {
        if (entityIds.length > 0)
        {
            player.connection.send(new ClientboundRemoveEntitiesPacket(entityIds));
        }
    }
}
