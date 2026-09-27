package com.griefprevention.protection;

import com.griefprevention.claims.ClaimSnapshot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The claims of one world, as the protection policies need to see them. */
public interface ClaimLookup
{
    /**
     * @return the most specific claim containing the block, subdivisions included, or null
     */
    @Nullable ClaimSnapshot claimAt(int x, int y, int z);

    @Nullable ClaimSnapshot claimById(long id);

    default @Nullable ClaimSnapshot claimAt(@NotNull BlockPoint point)
    {
        return claimAt(point.x(), point.y(), point.z());
    }
}
