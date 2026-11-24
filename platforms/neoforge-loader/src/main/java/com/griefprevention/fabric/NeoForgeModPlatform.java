package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * {@link ModPlatform} implementation backed by NeoForge's game event bus and FML.
 */
final class NeoForgeModPlatform implements ModPlatform
{
    private final List<BiConsumer<ClaimSnapshot, @Nullable ServerPlayer>> claimCreatedListeners =
            new CopyOnWriteArrayList<>();
    private final List<ClaimModifiedListener> claimModifiedListeners =
            new CopyOnWriteArrayList<>();
    private final List<BiConsumer<ClaimSnapshot, @Nullable ServerPlayer>> claimDeletedListeners =
            new CopyOnWriteArrayList<>();

    @Override
    public @NotNull String loaderName()
    {
        return "NeoForge";
    }

    @Override
    public @NotNull Path gameDir()
    {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public @NotNull Path configDir()
    {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(@NotNull String modId)
    {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    @Override
    public @Nullable String minecraftVersion()
    {
        FMLLoader loader = FMLLoader.getCurrentOrNull();
        if (loader != null && loader.getVersionInfo() != null)
        {
            return loader.getVersionInfo().mcVersion();
        }
        return null;
    }

    @Override
    public boolean isBefore1_21_11()
    {
        // Both bundled NeoForge adapters target 1.21.11 and 26.1+, where modern APIs exist.
        return false;
    }

    @Override
    public @NotNull String mapIntermediaryMethod(
            @NotNull String intermediaryOwner,
            @NotNull String intermediaryMethod,
            @NotNull String descriptor,
            @NotNull String mojangFallback)
    {
        return mojangFallback;
    }

    @Override
    public void onServerStarted(@NotNull Consumer<MinecraftServer> listener)
    {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerStopping(@NotNull Consumer<MinecraftServer> listener)
    {
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onServerStopped(@NotNull Consumer<MinecraftServer> listener)
    {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onEndServerTick(@NotNull Consumer<MinecraftServer> listener)
    {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> listener.accept(event.getServer()));
    }

    @Override
    public void onPlayerJoin(@NotNull BiConsumer<ServerPlayer, MinecraftServer> listener)
    {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level)
            {
                listener.accept(player, level.getServer());
            }
        });
    }

    @Override
    public void onPlayerDisconnect(@NotNull BiConsumer<ServerPlayer, MinecraftServer> listener)
    {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level)
            {
                listener.accept(player, level.getServer());
            }
        });
    }

    @Override
    public void onChatMessage(@NotNull BiConsumer<ServerPlayer, String> listener)
    {
        NeoForge.EVENT_BUS.addListener((ServerChatEvent event) -> {
            String raw = event.getRawText();
            if (raw == null || raw.isEmpty())
            {
                raw = event.getMessage().getString();
            }
            listener.accept(event.getPlayer(), raw == null ? "" : raw);
        });
    }

    @Override
    public void onRegisterCommands(@NotNull Consumer<CommandDispatcher<CommandSourceStack>> listener)
    {
        // Register at LOW priority so other mods claim their command names first, letting
        // FabricCommandRegistrar step aside cleanly as on Fabric's AFTER_OTHER_MODS phase.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW,
                (RegisterCommandsEvent event) -> listener.accept(event.getDispatcher()));
    }

    @Override
    public void onBlockBreakBefore(@NotNull BlockBreakBeforeListener listener)
    {
        NeoForgeBlockBreakHook.onBlockBreakBefore(listener);
    }

    @Override
    public void onBlockBreakAfter(@NotNull BlockBreakListener listener)
    {
        NeoForgeBlockBreakHook.onBlockBreakAfter(listener);
    }

    @Override
    public void onBlockBreakCanceled(@NotNull BlockBreakListener listener)
    {
        NeoForgeBlockBreakHook.onBlockBreakCanceled(listener);
    }

    @Override
    public void onAttackBlock(@NotNull AttackBlockListener listener)
    {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock event) -> {
            if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START)
            {
                return;
            }
            Direction face = event.getFace() != null ? event.getFace() : Direction.UP;
            InteractionResult result = listener.onAttackBlock(
                    event.getEntity(), event.getLevel(), event.getHand(), event.getPos(), face);
            if (result != InteractionResult.PASS)
            {
                event.setCanceled(true);
            }
        });
    }

    @Override
    public void onUseBlock(@NotNull UseBlockListener listener)
    {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            InteractionResult result = listener.onUseBlock(
                    event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result != InteractionResult.PASS)
            {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
    }

    @Override
    public void onUseItem(@NotNull UseItemListener listener)
    {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
            InteractionResult result = listener.onUseItem(
                    event.getEntity(), event.getLevel(), event.getHand());
            if (result != InteractionResult.PASS)
            {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
    }

    @Override
    public void onAttackEntity(@NotNull AttackEntityListener listener)
    {
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent event) -> {
            InteractionResult result = listener.onAttackEntity(
                    event.getEntity(), event.getEntity().level(), InteractionHand.MAIN_HAND, event.getTarget(), null);
            if (result != InteractionResult.PASS)
            {
                event.setCanceled(true);
            }
        });
    }

    @Override
    public void onUseEntity(@NotNull UseEntityListener listener)
    {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific event) -> {
            EntityHitResult hit = new EntityHitResult(
                    event.getTarget(), event.getTarget().position().add(event.getLocalPos()));
            InteractionResult result = listener.onUseEntity(
                    event.getEntity(), event.getLevel(), event.getHand(), event.getTarget(), hit);
            if (result != InteractionResult.PASS)
            {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            InteractionResult result = listener.onUseEntity(
                    event.getEntity(), event.getLevel(), event.getHand(), event.getTarget(), null);
            if (result != InteractionResult.PASS)
            {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
    }

    @Override
    public void onClaimCreated(@NotNull BiConsumer<ClaimSnapshot, @Nullable ServerPlayer> listener)
    {
        this.claimCreatedListeners.add(listener);
    }

    @Override
    public void onClaimModified(@NotNull ClaimModifiedListener listener)
    {
        this.claimModifiedListeners.add(listener);
    }

    @Override
    public void onClaimDeleted(@NotNull BiConsumer<ClaimSnapshot, @Nullable ServerPlayer> listener)
    {
        this.claimDeletedListeners.add(listener);
    }

    @Override
    public void fireClaimCreated(@NotNull ClaimSnapshot claim, @Nullable ServerPlayer actor)
    {
        for (BiConsumer<ClaimSnapshot, @Nullable ServerPlayer> listener : this.claimCreatedListeners)
        {
            listener.accept(claim, actor);
        }
        NeoForge.EVENT_BUS.post(new NeoForgeClaimEvents.ClaimCreatedEvent(claim, actor));
    }

    @Override
    public void fireClaimModified(
            @NotNull ClaimSnapshot previous,
            @NotNull ClaimSnapshot updated,
            @Nullable ServerPlayer actor)
    {
        for (ClaimModifiedListener listener : this.claimModifiedListeners)
        {
            listener.onClaimModified(previous, updated, actor);
        }
        NeoForge.EVENT_BUS.post(new NeoForgeClaimEvents.ClaimModifiedEvent(previous, updated, actor));
    }

    @Override
    public void fireClaimDeleted(@NotNull ClaimSnapshot claim, @Nullable ServerPlayer actor)
    {
        for (BiConsumer<ClaimSnapshot, @Nullable ServerPlayer> listener : this.claimDeletedListeners)
        {
            listener.accept(claim, actor);
        }
        NeoForge.EVENT_BUS.post(new NeoForgeClaimEvents.ClaimDeletedEvent(claim, actor));
    }

    @Override
    public void fireClaimTransferred(
            @NotNull ClaimSnapshot claim,
            @Nullable UUID newOwnerId,
            @Nullable ServerPlayer actor)
    {
        NeoForge.EVENT_BUS.post(new NeoForgeClaimEvents.ClaimTransferredEvent(claim, newOwnerId, actor));
    }

    @Override
    public boolean fireClaimExpiration(@NotNull ClaimSnapshot claim)
    {
        NeoForgeClaimEvents.ClaimExpirationEvent event = new NeoForgeClaimEvents.ClaimExpirationEvent(claim);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }
}
