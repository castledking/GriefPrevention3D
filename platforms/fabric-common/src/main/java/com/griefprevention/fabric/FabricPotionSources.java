package com.griefprevention.fabric;

import com.griefprevention.protection.BlockPoint;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps a dispenser's actual position with its potion and the lingering cloud it creates. */
@ApiStatus.Internal
public final class FabricPotionSources
{
    public record Origin(@NotNull String world, @NotNull BlockPoint position) {}

    private static final Map<Object, Origin> ORIGINS = Collections.synchronizedMap(new WeakHashMap<>());
    private record Impact(@Nullable Origin origin) {}
    private static final ThreadLocal<Impact> IMPACT = new ThreadLocal<>();

    private FabricPotionSources() {}

    public static void remember(@NotNull Object projectile, @NotNull Origin origin)
    {
        ORIGINS.put(projectile, origin);
    }

    public static @Nullable Origin origin(@NotNull Object entity)
    {
        return ORIGINS.get(entity);
    }

    public static void duringImpact(@NotNull Object projectile, @NotNull Runnable impact)
    {
        Impact previous = IMPACT.get();
        IMPACT.set(new Impact(origin(projectile)));
        try
        {
            impact.run();
        }
        finally
        {
            if (previous == null) IMPACT.remove();
            else IMPACT.set(previous);
        }
    }

    public static void inheritImpactOrigin(@NotNull Object cloud)
    {
        Impact impact = IMPACT.get();
        if (impact != null && impact.origin() != null) remember(cloud, impact.origin());
    }

    public static boolean isPotionImpact()
    {
        return IMPACT.get() != null;
    }
}
