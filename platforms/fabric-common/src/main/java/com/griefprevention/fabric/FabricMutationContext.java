package com.griefprevention.fabric;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/** Nested mutation scopes are restored even when a vanilla operation throws. */
public final class FabricMutationContext {
    public record Context(BlockPos growthOrigin, Entity actor, boolean portal, boolean silverfish, boolean rabbit, boolean death) {}
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    public static Context current() { return CURRENT.get(); }
    public static <T> T during(Context context, Supplier<T> action) {
        Context previous = CURRENT.get();
        CURRENT.set(context);
        try { return action.get(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    private FabricMutationContext() {}
}
