package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Loader-neutral bridge implemented by the Fabric and NeoForge entrypoints so the entire shared
 * Mojang-mapped claim, command, visualization, and protection runtime runs identically on both
 * loaders without referencing loader APIs directly.
 */
public interface ModPlatform
{
    String MOD_ID = "griefprevention3d";

    @FunctionalInterface
    interface BlockBreakBeforeListener
    {
        boolean allowBlockBreak(
                @NotNull Level level,
                @NotNull Player player,
                @NotNull BlockPos pos,
                @NotNull BlockState state,
                @Nullable BlockEntity blockEntity);
    }

    @FunctionalInterface
    interface BlockBreakListener
    {
        void onBlockBreak(
                @NotNull Level level,
                @NotNull Player player,
                @NotNull BlockPos pos,
                @NotNull BlockState state,
                @Nullable BlockEntity blockEntity);
    }

    @FunctionalInterface
    interface AttackBlockListener
    {
        @NotNull InteractionResult onAttackBlock(
                @NotNull Player player,
                @NotNull Level level,
                @NotNull InteractionHand hand,
                @NotNull BlockPos pos,
                @NotNull Direction direction);
    }

    @FunctionalInterface
    interface UseBlockListener
    {
        @NotNull InteractionResult onUseBlock(
                @NotNull Player player,
                @NotNull Level level,
                @NotNull InteractionHand hand,
                @NotNull BlockHitResult hitResult);
    }

    @FunctionalInterface
    interface UseItemListener
    {
        @NotNull InteractionResult onUseItem(
                @NotNull Player player,
                @NotNull Level level,
                @NotNull InteractionHand hand);
    }

    @FunctionalInterface
    interface AttackEntityListener
    {
        @NotNull InteractionResult onAttackEntity(
                @NotNull Player player,
                @NotNull Level level,
                @NotNull InteractionHand hand,
                @NotNull Entity entity,
                @Nullable EntityHitResult hitResult);
    }

    @FunctionalInterface
    interface UseEntityListener
    {
        @NotNull InteractionResult onUseEntity(
                @NotNull Player player,
                @NotNull Level level,
                @NotNull InteractionHand hand,
                @NotNull Entity entity,
                @Nullable EntityHitResult hitResult);
    }

    @FunctionalInterface
    interface ClaimModifiedListener
    {
        void onClaimModified(
                @NotNull ClaimSnapshot previous,
                @NotNull ClaimSnapshot updated,
                @Nullable ServerPlayer actor);
    }

    @NotNull String loaderName();

    @NotNull Path gameDir();

    @NotNull Path configDir();

    boolean isModLoaded(@NotNull String modId);

    @Nullable String minecraftVersion();

    boolean isBefore1_21_11();

    @NotNull String mapIntermediaryMethod(
            @NotNull String intermediaryOwner,
            @NotNull String intermediaryMethod,
            @NotNull String descriptor,
            @NotNull String mojangFallback);

    void onServerStarted(@NotNull Consumer<MinecraftServer> listener);

    void onServerStopping(@NotNull Consumer<MinecraftServer> listener);

    void onServerStopped(@NotNull Consumer<MinecraftServer> listener);

    void onEndServerTick(@NotNull Consumer<MinecraftServer> listener);

    void onPlayerJoin(@NotNull BiConsumer<ServerPlayer, MinecraftServer> listener);

    void onPlayerDisconnect(@NotNull BiConsumer<ServerPlayer, MinecraftServer> listener);

    void onChatMessage(@NotNull BiConsumer<ServerPlayer, String> listener);

    void onRegisterCommands(@NotNull Consumer<CommandDispatcher<CommandSourceStack>> listener);

    void onBlockBreakBefore(@NotNull BlockBreakBeforeListener listener);

    void onBlockBreakAfter(@NotNull BlockBreakListener listener);

    void onBlockBreakCanceled(@NotNull BlockBreakListener listener);

    void onAttackBlock(@NotNull AttackBlockListener listener);

    void onUseBlock(@NotNull UseBlockListener listener);

    void onUseItem(@NotNull UseItemListener listener);

    void onAttackEntity(@NotNull AttackEntityListener listener);

    void onUseEntity(@NotNull UseEntityListener listener);

    void onClaimCreated(@NotNull BiConsumer<ClaimSnapshot, @Nullable ServerPlayer> listener);

    void onClaimModified(@NotNull ClaimModifiedListener listener);

    void onClaimDeleted(@NotNull BiConsumer<ClaimSnapshot, @Nullable ServerPlayer> listener);

    void fireClaimCreated(@NotNull ClaimSnapshot claim, @Nullable ServerPlayer actor);

    void fireClaimModified(@NotNull ClaimSnapshot previous, @NotNull ClaimSnapshot updated, @Nullable ServerPlayer actor);

    void fireClaimDeleted(@NotNull ClaimSnapshot claim, @Nullable ServerPlayer actor);

    void fireClaimTransferred(@NotNull ClaimSnapshot claim, @Nullable UUID newOwnerId, @Nullable ServerPlayer actor);

    boolean fireClaimExpiration(@NotNull ClaimSnapshot claim);

    final class Holder
    {
        private static volatile @Nullable ModPlatform active;

        private Holder()
        {
        }
    }

    static void install(@NotNull ModPlatform platform)
    {
        Holder.active = platform;
    }

    static @NotNull ModPlatform current()
    {
        ModPlatform platform = Holder.active;
        if (platform == null)
        {
            throw new IllegalStateException("GriefPrevention3D ModPlatform has not been installed.");
        }
        return platform;
    }

    static @Nullable ModPlatform currentOrNull()
    {
        return Holder.active;
    }

    static @NotNull String currentLoaderName()
    {
        ModPlatform platform = Holder.active;
        return platform == null ? "Fabric" : platform.loaderName();
    }

    static void fireClaimCreatedIfInstalled(@NotNull ClaimSnapshot claim, @Nullable ServerPlayer actor)
    {
        ModPlatform platform = Holder.active;
        if (platform != null)
        {
            platform.fireClaimCreated(claim, actor);
        }
    }

    static void fireClaimModifiedIfInstalled(
            @NotNull ClaimSnapshot previous,
            @NotNull ClaimSnapshot updated,
            @Nullable ServerPlayer actor)
    {
        ModPlatform platform = Holder.active;
        if (platform != null)
        {
            platform.fireClaimModified(previous, updated, actor);
        }
    }

    static void fireClaimDeletedIfInstalled(@NotNull ClaimSnapshot claim, @Nullable ServerPlayer actor)
    {
        ModPlatform platform = Holder.active;
        if (platform != null)
        {
            platform.fireClaimDeleted(claim, actor);
        }
    }

    static void fireClaimTransferredIfInstalled(
            @NotNull ClaimSnapshot claim,
            @Nullable UUID newOwnerId,
            @Nullable ServerPlayer actor)
    {
        ModPlatform platform = Holder.active;
        if (platform != null)
        {
            platform.fireClaimTransferred(claim, newOwnerId, actor);
        }
    }

    static boolean fireClaimExpirationIfInstalled(@NotNull ClaimSnapshot claim)
    {
        ModPlatform platform = Holder.active;
        return platform == null || platform.fireClaimExpiration(claim);
    }
}
