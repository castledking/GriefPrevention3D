package com.griefprevention.protection;

import org.jetbrains.annotations.NotNull;

/** {@code Claims.EnderPearlsRequireAccessTrust} and {@code Claims.RefundDeniedEnderPearls}. */
public final class EnderPearlSettings
{
    private final boolean requireAccessTrust;
    private final boolean refundDenied;

    public EnderPearlSettings(boolean requireAccessTrust, boolean refundDenied)
    {
        this.requireAccessTrust = requireAccessTrust;
        this.refundDenied = refundDenied;
    }

    /** Paper's defaults: pearls and chorus fruit only reach claims with access trust, and a denied pearl comes back. */
    public static @NotNull EnderPearlSettings upstreamDefaults()
    {
        return new EnderPearlSettings(true, true);
    }

    /** @return whether landing in a claim by ender pearl or chorus fruit takes access trust */
    public boolean requireAccessTrust()
    {
        return this.requireAccessTrust;
    }

    /** @return whether a pearl that may not land is given back to its thrower */
    public boolean refundDenied()
    {
        return this.refundDenied;
    }
}
