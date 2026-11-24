package com.griefprevention.fabric;

import com.griefprevention.claims.ClaimSnapshot;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * {@link ModPlatform} implementation backed by Fabric Loader and Fabric API.
 */
final class FabricModPlatform implements ModPlatform
{
    private static final Identifier AFTER_OTHER_MODS =
            Identifier.fromNamespaceAndPath(ModPlatform.MOD_ID, "after_other_mods");

    @Override
    public @NotNull String loaderName()
    {
        return "Fabric";
    }

    @Override
    public @NotNull Path gameDir()
    {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public @NotNull Path configDir()
    {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(@NotNull String modId)
    {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public @Nullable String minecraftVersion()
    {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion().getFriendlyString())
                .orElse(null);
    }

    @Override
    public boolean isBefore1_21_11()
    {
        Version version = FabricLoader.getInstance().getModContainer("minecraft")
                .map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion())
                .orElse(null);
        if (version == null)
        {
            return false;
        }
        try
        {
            return VersionPredicate.parse("<1.21.11-").test(version);
        }
        catch (VersionParsingException unreadable)
        {
            throw new IllegalStateException(unreadable);
        }
    }

    @Override
    public @NotNull String mapIntermediaryMethod(
            @NotNull String intermediaryOwner,
            @NotNull String intermediaryMethod,
            @NotNull String descriptor,
            @NotNull String mojangFallback)
    {
        MappingResolver mappings = FabricLoader.getInstance().getMappingResolver();
        return mappings.mapMethodName("intermediary", intermediaryOwner, intermediaryMethod, descriptor);
    }

    @Override
    public void onServerStarted(@NotNull Consumer<MinecraftServer> listener)
    {
        ServerLifecycleEvents.SERVER_STARTED.register(listener::accept);
    }

    @Override
    public void onServerStopping(@NotNull Consumer<MinecraftServer> listener)
    {
        ServerLifecycleEvents.SERVER_STOPPING.register(listener::accept);
    }

    @Override
    public void onServerStopped(@NotNull Consumer<MinecraftServer> listener)
    {
        ServerLifecycleEvents.SERVER_STOPPED.register(listener::accept);
    }

    @Override
    public void onEndServerTick(@NotNull Consumer<MinecraftServer> listener)
    {
        ServerTickEvents.END_SERVER_TICK.register(listener::accept);
    }

    @Override
    public void onPlayerJoin(@NotNull BiConsumer<ServerPlayer, MinecraftServer> listener)
    {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                listener.accept(handler.getPlayer(), server));
    }

    @Override
    public void onPlayerDisconnect(@NotNull BiConsumer<ServerPlayer, MinecraftServer> listener)
    {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                listener.accept(handler.getPlayer(), server));
    }

    @Override
    public void onChatMessage(@NotNull BiConsumer<ServerPlayer, String> listener)
    {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String raw = message.signedContent();
            if (raw == null || raw.isEmpty())
            {
                raw = message.decoratedContent().getString();
            }
            listener.accept(sender, raw == null ? "" : raw);
        });
    }

    @Override
    public void onRegisterCommands(@NotNull Consumer<CommandDispatcher<CommandSourceStack>> listener)
    {
        // Registering after other mods lets install() see their commands and step aside, instead of
        // another mod merging its command into a GriefPrevention name such as /create.
        CommandRegistrationCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, AFTER_OTHER_MODS);
        CommandRegistrationCallback.EVENT.register(AFTER_OTHER_MODS,
                (dispatcher, buildContext, selection) -> listener.accept(dispatcher));
    }

    @Override
    public void onBlockBreakBefore(@NotNull BlockBreakBeforeListener listener)
    {
        PlayerBlockBreakEvents.BEFORE.register(listener::allowBlockBreak);
    }

    @Override
    public void onBlockBreakAfter(@NotNull BlockBreakListener listener)
    {
        PlayerBlockBreakEvents.AFTER.register(listener::onBlockBreak);
    }

    @Override
    public void onBlockBreakCanceled(@NotNull BlockBreakListener listener)
    {
        PlayerBlockBreakEvents.CANCELED.register(listener::onBlockBreak);
    }

    @Override
    public void onAttackBlock(@NotNull AttackBlockListener listener)
    {
        AttackBlockCallback.EVENT.register(listener::onAttackBlock);
    }

    @Override
    public void onUseBlock(@NotNull UseBlockListener listener)
    {
        UseBlockCallback.EVENT.register(listener::onUseBlock);
    }

    @Override
    public void onUseItem(@NotNull UseItemListener listener)
    {
        UseItemCallback.EVENT.register(listener::onUseItem);
    }

    @Override
    public void onAttackEntity(@NotNull AttackEntityListener listener)
    {
        AttackEntityCallback.EVENT.register(listener::onAttackEntity);
    }

    @Override
    public void onUseEntity(@NotNull UseEntityListener listener)
    {
        UseEntityCallback.EVENT.register(listener::onUseEntity);
    }

    @Override
    public void onClaimCreated(@NotNull BiConsumer<ClaimSnapshot, @Nullable ServerPlayer> listener)
    {
        ClaimCreatedCallback.EVENT.register(listener::accept);
    }

    @Override
    public void onClaimModified(@NotNull ClaimModifiedListener listener)
    {
        ClaimModifiedCallback.EVENT.register(listener::onClaimModified);
    }

    @Override
    public void onClaimDeleted(@NotNull BiConsumer<ClaimSnapshot, @Nullable ServerPlayer> listener)
    {
        ClaimDeletedCallback.EVENT.register(listener::accept);
    }

    @Override
    public void fireClaimCreated(@NotNull ClaimSnapshot claim, @Nullable ServerPlayer actor)
    {
        ClaimCreatedCallback.EVENT.invoker().onClaimCreated(claim, actor);
    }

    @Override
    public void fireClaimModified(
            @NotNull ClaimSnapshot previous,
            @NotNull ClaimSnapshot updated,
            @Nullable ServerPlayer actor)
    {
        ClaimModifiedCallback.EVENT.invoker().onClaimModified(previous, updated, actor);
    }

    @Override
    public void fireClaimDeleted(@NotNull ClaimSnapshot claim, @Nullable ServerPlayer actor)
    {
        ClaimDeletedCallback.EVENT.invoker().onClaimDeleted(claim, actor);
    }

    @Override
    public void fireClaimTransferred(
            @NotNull ClaimSnapshot claim,
            @Nullable UUID newOwnerId,
            @Nullable ServerPlayer actor)
    {
        ClaimTransferredCallback.EVENT.invoker().onClaimTransferred(claim, newOwnerId, actor);
    }

    @Override
    public boolean fireClaimExpiration(@NotNull ClaimSnapshot claim)
    {
        return ClaimExpirationCallback.EVENT.invoker().allowExpiration(claim);
    }
}
