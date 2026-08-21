/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

/**
 * Enum representing the permissions available in a {@link Claim}.
 */
public enum ClaimPermission
{
    /**
     * ClaimPermission used for owner-based checks. Cannot be granted and grants all other permissions.
     */
    Edit(Messages.OnlyOwnersModifyClaims),
    /**
     * ClaimPermission that allows users to grant ClaimPermissions. Also grants {@link #Build},
     * {@link #Container}, and {@link #Access}. Command: /managetrust
     */
    Manage(Messages.NoManageTrust),
    /**
     * ClaimPermission used for building checks. Grants {@link #Container} and {@link #Access}.
     * Command: /trust
     */
    Build(Messages.NoBuildPermission),
    /**
     * ClaimPermission used for inventory management, such as containers and farming. Grants {@link #Access}.
     * Command: /containertrust
     */
    Container(Messages.NoContainersPermission),
    /**
     * Legacy alias for {@link #Container}. Kept for addons compiled against older GriefPrevention APIs.
     */
    @Deprecated
    Inventory(Messages.NoContainersPermission),
    /**
     * ClaimPermission used for basic access. Command: /accesstrust
     */
    Access(Messages.NoAccessPermission),
    /**
     * ClaimPermission used for neighbor trust. Allows bypassing minimum distance checks for claim creation.
     * Command: /neighbortrust or /distancetrust
     */
    Neighbor(Messages.NoAccessPermission),
    /**
     * ClaimPermission for combat against players inside a claim. Command: /pvptrust.
     * Standalone: it is never granted by any other trust level and grants nothing else.
     * Only enforced when {@code Claims.AllowPvPTrust} is enabled; otherwise legacy rules apply.
     */
    PVP(Messages.NoPvPTrust),
    /**
     * ClaimPermission for combat against creatures (animals, villagers) inside a claim. Command: /pvetrust.
     * Standalone: it is never granted by any other trust level and grants nothing else.
     * Only enforced when {@code Claims.AllowPvETrust} is enabled; otherwise Container trust governs creatures.
     */
    PVE(Messages.NoPvETrust);

    private final Messages denialMessage;

    ClaimPermission(Messages messages)
    {
        this.denialMessage = messages;
    }

    /**
     * @return the {@link Messages Message} used when alerting a user that they lack the ClaimPermission
     */
    public Messages getDenialMessage()
    {
        return denialMessage;
    }

    /**
     * Check if a ClaimPermission is granted by another ClaimPermission.
     *
     * <p>{@link #PVP} and {@link #PVE} are standalone combat trusts: they are never implied by
     * any other trust level and imply nothing beyond themselves.
     *
     * @param other the ClaimPermission to compare against
     * @return true if this ClaimPermission is equal or lesser than the provided ClaimPermission
     */
    public boolean isGrantedBy(ClaimPermission other)
    {
        if (this == PVP || this == PVE) return other == this;
        if (other == null) return false;
        return other.getTrustLevel() <= this.getTrustLevel();
    }

    /**
     * @return true if this ClaimPermission is a standalone combat trust (PVP/PVE) that sits
     * outside the normal implication hierarchy
     */
    public boolean isCombatTrust()
    {
        return this == PVP || this == PVE;
    }

    public boolean isContainer()
    {
        return this == Container || this == Inventory;
    }

    private int getTrustLevel()
    {
        switch (this)
        {
            case Edit:
                return 0;
            case Manage:
                return 1;
            case Build:
                return 2;
            case Container:
            case Inventory:
                return 3;
            case Access:
                return 4;
            case Neighbor:
                return 5;
            case PVP:
                return 6;
            case PVE:
                return 7;
            default:
                throw new IllegalStateException("Unknown claim permission: " + this);
        }
    }

}
