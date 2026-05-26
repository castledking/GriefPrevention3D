package com.griefprevention.geometry;

/**
 * Failure categories for an orthogonal polygon path.
 */
public enum OrthogonalPolygonValidationIssueType
{
    TOO_FEW_POINTS,
    NOT_CLOSED,
    NON_ORTHOGONAL_EDGE,
    ZERO_LENGTH_EDGE,
    SELF_INTERSECTION,
    DUPLICATE_CORNER,
    DISCONNECTED_SHAPE
}
