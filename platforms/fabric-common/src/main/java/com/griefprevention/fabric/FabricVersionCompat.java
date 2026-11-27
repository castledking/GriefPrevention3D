package com.griefprevention.fabric;

import com.griefprevention.fabric.mixin.EntityAccessor;
import com.mojang.math.Transformation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * The few Minecraft calls whose shape changed inside the range one adapter binary serves.
 *
 * <p>1.21.11 replaced permission levels with permission sets and made PvP a per-world game rule.
 * The 1.21.11 adapter also runs on 1.21.2 through 1.21.10, where it calls the earlier methods by
 * their intermediary names. {@link #verify} resolves them when the adapter starts, so a release that
 * lacks one fails the boot check rather than the first command. The calls 1.21.11 introduced sit in
 * {@link Modern}, which older releases never load and the linkage check skips on them.
 */
final class FabricVersionCompat
{
    /** Bukkit's {@code op} default: the permission level of a game master. */
    private static final int GAME_MASTER_LEVEL = 2;
    private static final @Nullable Legacy LEGACY = Legacy.resolve();
    private static final MethodHandle TRANSFORMATION = transformationConstructor();

    private FabricVersionCompat()
    {
    }

    /** Resolves every call for the running release, failing if one is missing. */
    static void verify()
    {
        // Loading the class resolved everything; this only makes the moment explicit.
    }

    /** @return whether the player has the operator permission level */
    static boolean isGameMaster(@NotNull Player player)
    {
        Legacy legacy = LEGACY;
        return legacy == null ? Modern.isGameMaster(player) : legacy.playerHasPermissions(player);
    }

    /** @return whether the command source has the operator permission level */
    static boolean isGameMaster(@NotNull CommandSourceStack source)
    {
        Legacy legacy = LEGACY;
        return legacy == null ? Modern.isGameMaster(source) : legacy.sourceHasPermission(source);
    }

    /** @return whether players may fight in the world: a game rule since 1.21.11, a server setting before */
    static boolean isPvpAllowed(@NotNull ServerLevel level)
    {
        Legacy legacy = LEGACY;
        return legacy == null ? Modern.isPvpAllowed(level) : legacy.serverIsPvpAllowed(level.getServer());
    }

    /**
     * The player's level, read from the {@code Entity.level} field, whose name has held in both
     * Mojang's names and intermediary on every release.
     *
     * <p>No accessor method does: {@code ServerPlayer#serverLevel()} became {@code level()} under
     * Mojang's names in 1.21.6, which NeoForge runs under and serves 1.21.3 through 1.21.10 from one
     * adapter remapped to 1.21.10's names; and {@code Entity#level()} moved to an interface in 1.21.9,
     * changing the intermediary name Fabric calls it by.
     */
    static @NotNull ServerLevel level(@NotNull ServerPlayer player)
    {
        return (ServerLevel) ((EntityAccessor) player).griefPrevention$getLevel();
    }

    /** {@code new Transformation(...)}, whose parameters became read-only joml views in 1.21.11. */
    static @NotNull Transformation transformation(
            @NotNull Vector3f translation,
            @NotNull Quaternionf leftRotation,
            @NotNull Vector3f scale,
            @NotNull Quaternionf rightRotation)
    {
        try
        {
            return (Transformation) TRANSFORMATION.invokeExact(translation, leftRotation, scale, rightRotation);
        }
        catch (Throwable failure)
        {
            throw rethrow(failure);
        }
    }

    private static @NotNull MethodHandle transformationConstructor()
    {
        MethodType wanted = MethodType.methodType(
                Transformation.class, Vector3f.class, Quaternionf.class, Vector3f.class, Quaternionf.class);
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        try
        {
            return lookup.findConstructor(Transformation.class, MethodType.methodType(
                    void.class, Vector3fc.class, Quaternionfc.class, Vector3fc.class, Quaternionfc.class)).asType(wanted);
        }
        catch (NoSuchMethodException | IllegalAccessException modernMissing)
        {
            try
            {
                return lookup.findConstructor(Transformation.class, wanted.changeReturnType(void.class)).asType(wanted);
            }
            catch (NoSuchMethodException | IllegalAccessException legacyMissing)
            {
                legacyMissing.addSuppressed(modernMissing);
                throw new IllegalStateException("This Minecraft release has no Transformation constructor GriefPrevention3D knows.", legacyMissing);
            }
        }
    }

    private static @NotNull RuntimeException rethrow(@NotNull Throwable failure)
    {
        if (failure instanceof RuntimeException runtime)
        {
            return runtime;
        }
        if (failure instanceof Error error)
        {
            throw error;
        }
        return new IllegalStateException(failure);
    }

    /** Calls 1.21.11 introduced. Never loaded on older releases. */
    private static final class Modern
    {
        private static boolean isGameMaster(@NotNull Player player)
        {
            return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
        }

        private static boolean isGameMaster(@NotNull CommandSourceStack source)
        {
            return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
        }

        private static boolean isPvpAllowed(@NotNull ServerLevel level)
        {
            return level.isPvpAllowed();
        }
    }

    /** 1.21.2 to 1.21.10: the earlier methods, found by their intermediary names. */
    private static final class Legacy
    {
        private final MethodHandle playerHasPermissions;
        private final MethodHandle sourceHasPermission;
        private final MethodHandle serverIsPvpAllowed;

        private Legacy(MethodHandle playerHasPermissions, MethodHandle sourceHasPermission, MethodHandle serverIsPvpAllowed)
        {
            this.playerHasPermissions = playerHasPermissions;
            this.sourceHasPermission = sourceHasPermission;
            this.serverIsPvpAllowed = serverIsPvpAllowed;
        }

        /** @return the earlier calls on releases before 1.21.11, otherwise null */
        private static @Nullable Legacy resolve()
        {
            ModPlatform platform = ModPlatform.currentOrNull();
            if (platform == null || !platform.isBefore1_21_11())
            {
                return null;
            }

            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            try
            {
                // Player#hasPermissions(int), CommandSourceStack#hasPermission(int), MinecraftServer#isPvpAllowed()
                MethodHandle player = lookup.findVirtual(Player.class,
                                platform.mapIntermediaryMethod("net.minecraft.class_1657", "method_64475", "(I)Z", "hasPermissions"),
                                MethodType.methodType(boolean.class, int.class))
                        .asType(MethodType.methodType(boolean.class, Player.class, int.class));
                MethodHandle source = lookup.findVirtual(CommandSourceStack.class,
                                platform.mapIntermediaryMethod("net.minecraft.class_2168", "method_9259", "(I)Z", "hasPermission"),
                                MethodType.methodType(boolean.class, int.class))
                        .asType(MethodType.methodType(boolean.class, CommandSourceStack.class, int.class));
                MethodHandle pvp = lookup.findVirtual(MinecraftServer.class,
                                platform.mapIntermediaryMethod("net.minecraft.server.MinecraftServer", "method_3852", "()Z", "isPvpAllowed"),
                                MethodType.methodType(boolean.class))
                        .asType(MethodType.methodType(boolean.class, MinecraftServer.class));
                return new Legacy(player, source, pvp);
            }
            catch (NoSuchMethodException | IllegalAccessException missing)
            {
                throw new IllegalStateException("GriefPrevention3D does not support Minecraft "
                        + platform.minecraftVersion() + ": " + missing.getMessage(), missing);
            }
        }

        private boolean playerHasPermissions(@NotNull Player player)
        {
            try
            {
                return (boolean) this.playerHasPermissions.invokeExact(player, GAME_MASTER_LEVEL);
            }
            catch (Throwable failure)
            {
                throw rethrow(failure);
            }
        }

        private boolean sourceHasPermission(@NotNull CommandSourceStack source)
        {
            try
            {
                return (boolean) this.sourceHasPermission.invokeExact(source, GAME_MASTER_LEVEL);
            }
            catch (Throwable failure)
            {
                throw rethrow(failure);
            }
        }

        private boolean serverIsPvpAllowed(@NotNull MinecraftServer server)
        {
            try
            {
                return (boolean) this.serverIsPvpAllowed.invokeExact(server);
            }
            catch (Throwable failure)
            {
                throw rethrow(failure);
            }
        }
    }
}
