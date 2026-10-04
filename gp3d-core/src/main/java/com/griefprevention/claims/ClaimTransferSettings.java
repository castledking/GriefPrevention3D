package com.griefprevention.claims;

import org.jetbrains.annotations.NotNull;

/** {@code Claims.TransferClaim}: whether players may give their claims away, and what it costs. */
public final class ClaimTransferSettings
{
    private final boolean enabled;
    private final double price;

    public ClaimTransferSettings(boolean enabled, double price)
    {
        this.enabled = enabled;
        this.price = price;
    }

    /** Paper's defaults: players cannot give claims away, and staff transfers are always free. */
    public static @NotNull ClaimTransferSettings upstreamDefaults()
    {
        return new ClaimTransferSettings(false, 0.0);
    }

    public boolean enabled()
    {
        return this.enabled;
    }

    public double price()
    {
        return this.price;
    }
}
