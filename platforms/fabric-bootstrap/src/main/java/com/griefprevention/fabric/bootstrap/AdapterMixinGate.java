package com.griefprevention.fabric.bootstrap;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets a mixin config apply only when it belongs to the adapter selected for the running game.
 *
 * <p>The universal jar declares one mixin config per bundled adapter. The others target class
 * names from another Minecraft version (intermediary names on 1.21.11, Mojang names on 26.x), so
 * applying them would fail to find their targets. Vetoing here happens before Mixin resolves any
 * target class. Every method is implemented, rather than relying on interface defaults, so the gate
 * also links against Mixin releases that predate them.
 */
public final class AdapterMixinGate implements IMixinConfigPlugin
{
    private static final Set<String> APPLIED_TARGETS = Collections.synchronizedSet(new LinkedHashSet<String>());

    private boolean enabled;

    @Override
    public void onLoad(String mixinPackage)
    {
        FabricAdapter adapter = FabricAdapters.current().selected();
        this.enabled = adapter != null && adapter.ownsMixinPackage(mixinPackage);
    }

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        // A @Pseudo mixin names a class under each of its names across releases. Skipping the names
        // this release lacks here, before Mixin tries to read them, keeps "Error loading class"
        // warnings out of the server log.
        return this.enabled && classExists(targetClassName);
    }

    private static boolean classExists(String className)
    {
        return AdapterMixinGate.class.getClassLoader().getResource(className.replace('.', '/') + ".class") != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {
        if (this.enabled)
        {
            APPLIED_TARGETS.addAll(myTargets);
        }
    }

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

    /**
     * @return the classes the selected adapter's mixins target, for verification at startup
     */
    static List<String> appliedTargets()
    {
        synchronized (APPLIED_TARGETS)
        {
            return new ArrayList<String>(APPLIED_TARGETS);
        }
    }
}
