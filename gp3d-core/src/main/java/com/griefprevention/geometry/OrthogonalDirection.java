package com.griefprevention.geometry;

public enum OrthogonalDirection
{
    NORTH,
    EAST,
    SOUTH,
    WEST;

    public OrthogonalDirection opposite()
    {
        switch (this) {
            case NORTH:
                return SOUTH;
            case EAST:
                return WEST;
            case SOUTH:
                return NORTH;
            case WEST:
                return EAST;
            default:
                throw new IllegalStateException("Unhandled direction: " + this);
        }
    }
}
