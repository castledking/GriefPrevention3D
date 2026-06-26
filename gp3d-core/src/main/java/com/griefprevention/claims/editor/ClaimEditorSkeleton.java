package com.griefprevention.claims.editor;

import com.griefprevention.geometry.OrthogonalEdge2i;
import com.griefprevention.geometry.OrthogonalDirection;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import com.griefprevention.geometry.OrthogonalPolygonValidationIssue;
import com.griefprevention.geometry.OrthogonalPolygonValidationIssueType;
import com.griefprevention.geometry.OrthogonalPolygonValidationResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Collections;

/**
 * Minimal placeholder implementation to anchor the recode editor boundary.
 */
public final class ClaimEditorSkeleton implements ClaimEditor
{
    @Override
    public @NotNull ClaimEditResult apply(@NotNull ClaimEditorSession session, @NotNull ClaimEditIntent intent)
    {
        switch (intent.type())
        {
            case ENTER_MODE:
                return ClaimEditResult.success(
                        session.withMode(intent.mode(), intent.source()).withPreview(ClaimEditPreview.empty()),
                        ClaimEditPreview.empty(),
                        Collections.singletonList("Entered " + intent.mode() + " mode.")
                );
            case EXIT_MODE:
                return ClaimEditResult.success(
                        ClaimEditorSession.idle(session.playerId()),
                        ClaimEditPreview.empty(),
                        Collections.singletonList("Exited claim editor mode.")
                );
            case SELECT_SEGMENT:
                return handleSelectSegment(session, intent);
            case ADD_CORNER:
                return handleAddCorner(session, intent);
            case ADD_NODE:
                return handleAddNode(session, intent);
            case EXPAND_SEGMENT:
                return handleExpandSegment(session, intent);
            case CANCEL_PATH:
                return handleCancelPath(session);
            default:
                return ClaimEditResult.failure(
                        ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                        null,
                        session,
                        session.preview(),
                        Collections.singletonList("Claim editor intent not implemented yet: " + intent.type())
                );
        }
    }

    private @NotNull ClaimEditResult handleSelectSegment(@NotNull ClaimEditorSession session, @NotNull ClaimEditIntent intent)
    {
        if (session.mode() != ClaimEditorMode.SHAPED)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("Segments can only be selected while in shaped mode.")
            );
        }

        if (!intent.holdingModificationTool())
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    ClaimEditMessageKey.MUST_HOLD_MODIFICATION_TOOL_FOR_THAT,
                    session,
                    session.preview(),
                    Collections.singletonList("You must be holding a golden shovel to do that.")
            );
        }

        OrthogonalPolygon polygon = session.preview().polygon();
        if (polygon == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("No parent claim boundary is selected for segment editing.")
            );
        }

        OrthogonalPoint2i point = intent.point();
        if (point == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("No segment selection point was provided.")
            );
        }

        List<Integer> matches = polygon.edgeIndexesContainingInteriorPoint(point);
        if (matches.size() != 1)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("That point does not resolve to a single editable segment.")
            );
        }

        int edgeIndex = matches.get(0);
        SegmentSelection selection = new SegmentSelection(resolveClaimId(session), edgeIndex, null, null, null);
        ClaimEditPreview preview = new ClaimEditPreview(
                polygon,
                selection,
                session.preview().draftPoints(),
                session.preview().snappedPoint(),
                session.preview().conflictPoints(),
                session.preview().issues(),
                Collections.singletonList("Segment selected.")
        );
        ClaimEditorSession updatedSession = session.withActiveSegment(selection).withPreview(preview);
        return ClaimEditResult.success(updatedSession, preview, preview.messages());
    }

    private @NotNull ClaimEditResult handleExpandSegment(@NotNull ClaimEditorSession session, @NotNull ClaimEditIntent intent)
    {
        if (session.mode() != ClaimEditorMode.SHAPED)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("Segments can only be expanded while in shaped mode.")
            );
        }

        if (!intent.holdingModificationTool())
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    ClaimEditMessageKey.MUST_HOLD_MODIFICATION_TOOL_FOR_THAT,
                    session,
                    session.preview(),
                    Collections.singletonList("You must be holding a golden shovel to do that.")
            );
        }

        SegmentSelection activeSegment = session.activeSegment();
        if (activeSegment == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NO_ACTIVE_SEGMENT,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("Select a claim segment before expanding it.")
            );
        }

        OrthogonalPolygon polygon = session.preview().polygon();
        if (polygon == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("No parent claim boundary is selected for segment editing.")
            );
        }

        int amount = intent.amount() == null ? 0 : intent.amount();
        OrthogonalPolygonValidationResult validationResult;
        SegmentSelection updatedSelection;
        try
        {
            SegmentExpansion expansion = expandSegment(polygon, activeSegment, amount);
            validationResult = expansion.result();
            updatedSelection = expansion.selection();
        }
        catch (IllegalArgumentException exception)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList(exception.getMessage())
            );
        }

        if (!validationResult.isValid())
        {
            ClaimEditPreview preview = previewFromPolygonValidation(
                    validationResult,
                    Collections.singletonList("That segment cannot be expanded there.")
            );
            ClaimEditFailureType failureType = validationResult.issues().stream()
                    .anyMatch(issue -> issue.type() == OrthogonalPolygonValidationIssueType.SELF_INTERSECTION)
                    ? ClaimEditFailureType.SELF_INTERSECTION
                    : ClaimEditFailureType.INVALID_GEOMETRY;
            return ClaimEditResult.failure(failureType, null, session.withPreview(preview), preview, preview.messages());
        }

        OrthogonalPolygon updatedPolygon = validationResult.polygon();
        ClaimEditPreview preview = new ClaimEditPreview(
                updatedPolygon,
                updatedSelection,
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("Segment expanded.")
        );
        ClaimEditorSession updatedSession = session.withActiveSegment(updatedSelection).withPreview(preview);
        return ClaimEditResult.success(updatedSession, preview, preview.messages());
    }

    private @NotNull ClaimEditResult handleAddCorner(@NotNull ClaimEditorSession session, @NotNull ClaimEditIntent intent)
    {
        if (session.mode() != ClaimEditorMode.SHAPED)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("Corners can only be added while in shaped mode.")
            );
        }

        if (!intent.holdingModificationTool())
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    ClaimEditMessageKey.MUST_HOLD_MODIFICATION_TOOL_FOR_THAT,
                    session,
                    session.preview(),
                    Collections.singletonList("You must be holding a golden shovel to do that.")
            );
        }

        OrthogonalPoint2i point = intent.point();
        if (point == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("No shaped corner point was provided.")
            );
        }

        ShapedPathDraft draft = session.openPath();
        if (draft == null)
        {
            draft = ShapedPathDraft.empty(intent.claimId());
        }

        OrthogonalPolygon basePolygon = session.activeTarget() != null
                && session.activeTarget().type() == ClaimEditTargetType.EXISTING_PARENT_CLAIM
                ? session.preview().polygon()
                : null;

        List<OrthogonalPoint2i> points = draft.points();
        if (points.isEmpty())
        {
            if (basePolygon != null && !isBoundaryPoint(basePolygon, point))
            {
                return ClaimEditResult.failure(
                        ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                        null,
                        session,
                        session.preview(),
                        Collections.singletonList("Start reshaping by clicking an existing claim boundary point.")
                );
            }

            ShapedPathDraft updatedDraft = draft.withAddedPoint(point);
            ClaimEditPreview preview = draftPreview(
                    updatedDraft,
                    basePolygon,
                    Collections.singletonList(basePolygon == null ? "First shaped corner set." : "Segment point added.")
            );
            return ClaimEditResult.success(
                    session.withOpenPath(updatedDraft).withPreview(preview),
                    preview,
                    preview.messages()
            );
        }

        OrthogonalPoint2i lastPoint = points.get(points.size() - 1);
        OrthogonalPoint2i firstPoint = points.get(0);
        OrthogonalPoint2i rawPoint = point;

        if (point.equals(lastPoint))
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.INVALID_GEOMETRY,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("That corner is already the current endpoint.")
            );
        }

        boolean closesPath = point.equals(firstPoint);
        if (!isOrthogonalStep(lastPoint, point))
        {
            point = snapToOrthogonal(lastPoint, point);
            closesPath = point.equals(firstPoint);

            if (point.equals(lastPoint))
            {
                ShapedPathDraft snappedDraft = draft.withSnappedPreview(point);
                ClaimEditPreview preview = draftPreview(snappedDraft, basePolygon, Collections.singletonList("Shaped claims must turn on the same X or Z axis."));
                return ClaimEditResult.failure(
                        ClaimEditFailureType.INVALID_GEOMETRY,
                        null,
                        session.withOpenPath(snappedDraft).withPreview(preview),
                        preview,
                        preview.messages()
                );
            }
        }

        if (!closesPath && points.contains(point))
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.INVALID_GEOMETRY,
                    null,
                    session,
                    draftPreview(draft, basePolygon, Collections.singletonList("That corner is already part of the current shaped path.")),
                    Collections.singletonList("That corner is already part of the current shaped path.")
            );
        }

        // Snake-eating-itself check: the new segment from lastPoint to point
        // must not cross any existing draft path segments (except the immediately
        // previous one which shares lastPoint). This prevents the path from
        // folding back over itself and creating an invalid shape.
        if (!closesPath && points.size() >= 2)
        {
            OrthogonalEdge2i newSegment = new OrthogonalEdge2i(lastPoint, point);
            for (int i = 0; i < points.size() - 1; i++)
            {
                OrthogonalPoint2i segStart = points.get(i);
                OrthogonalPoint2i segEnd = points.get(i + 1);
                // Skip the segment that shares the new segment's start point
                if (segStart.equals(lastPoint) || segEnd.equals(lastPoint))
                {
                    continue;
                }
                OrthogonalEdge2i existingSegment = new OrthogonalEdge2i(segStart, segEnd);
                if (orthogonalSegmentsCross(newSegment, existingSegment))
                {
                    return ClaimEditResult.failure(
                            ClaimEditFailureType.SELF_INTERSECTION,
                            null,
                            session,
                            draftPreview(draft, basePolygon, Collections.singletonList("That corner can't go there.")),
                            Collections.singletonList("That corner can't go there.")
                    );
                }
            }
        }

        if (closesPath && points.size() < 4)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.INVALID_GEOMETRY,
                    null,
                    session,
                    draftPreview(draft, basePolygon, Collections.singletonList("A shaped claim needs at least 4 corners before it can close.")),
                    Collections.singletonList("A shaped claim needs at least 4 corners before it can close.")
            );
        }

        List<OrthogonalPoint2i> candidatePoints = new ArrayList<>(points);
        List<OrthogonalPoint2i> reconnectCandidates = Collections.emptyList();
        OrthogonalPoint2i mergeBoundaryPoint = null;
        if (basePolygon != null)
        {
            reconnectCandidates = findBoundaryReconnectPoints(
                    basePolygon,
                    lastPoint,
                    point,
                    point.equals(firstPoint) ? firstPoint : null
            );
            if (!reconnectCandidates.isEmpty())
            {
                mergeBoundaryPoint = reconnectCandidates.get(0);
            }
        }

        OrthogonalPoint2i appliedPoint = mergeBoundaryPoint == null ? point : mergeBoundaryPoint;
        candidatePoints.add(appliedPoint);

        boolean reshapeLeavesBoundary = basePolygon != null && reshapeLeavesBoundary(basePolygon, candidatePoints);

        if (basePolygon != null && !reconnectCandidates.isEmpty() && reshapeLeavesBoundary)
        {
            BoundaryMergeAttempt bestMerge = selectBestBoundaryMerge(basePolygon, points, reconnectCandidates);
            if (bestMerge != null)
            {
                ShapedPathDraft updatedDraft = new ShapedPathDraft(draft.claimId(), bestMerge.candidatePoints(), null, true);
                ClaimEditPreview preview = closedPreview(
                        updatedDraft,
                        bestMerge.result().polygon(),
                        Collections.singletonList("Boundary reshape path merged.")
                );
                return ClaimEditResult.success(
                        session.withOpenPath(updatedDraft).withPreview(preview),
                        preview,
                        preview.messages()
                );
            }

            ClaimEditPreview preview = draftPreview(
                    new ShapedPathDraft(draft.claimId(), candidatePoints, null, false),
                    basePolygon,
                    Collections.singletonList("That reshape path cannot merge cleanly into the existing claim boundary.")
            );
            return ClaimEditResult.failure(
                    ClaimEditFailureType.INVALID_GEOMETRY,
                    null,
                    session.withPreview(preview),
                    preview,
                    preview.messages()
            );
        }

        if (basePolygon != null && mergeBoundaryPoint != null)
        {
            // If clicking on another boundary point that extends the segment along the boundary,
            // add it as a proper corner to allow creating multi-segment reshape paths.
            // This enables: 1) creating 2+ segments for /expandclaim, or
            // 2) closing a reshape merge path that goes outside and back to a different segment.
            ShapedPathDraft updatedDraft = new ShapedPathDraft(draft.claimId(), candidatePoints, null, false);
            ClaimEditPreview preview = draftPreview(
                    updatedDraft,
                    basePolygon,
                    Collections.singletonList("Segment point added.")
            );
            return ClaimEditResult.success(
                    session.withOpenPath(updatedDraft).withPreview(preview),
                    preview,
                    preview.messages()
            );
        }

        if (!closesPath)
        {
            ShapedPathDraft updatedDraft = new ShapedPathDraft(draft.claimId(), candidatePoints, null, false);
            ClaimEditPreview preview = draftPreview(
                    updatedDraft,
                    basePolygon,
                    rawPoint.equals(point)
                            ? Collections.singletonList("Shaped corner added.")
                            : Collections.singletonList("Shaped corner snapped into orthogonal alignment.")
            );
            return ClaimEditResult.success(
                    session.withOpenPath(updatedDraft).withPreview(preview),
                    preview,
                    preview.messages()
            );
        }

        OrthogonalPolygonValidationResult validationResult = OrthogonalPolygon.validatePath(candidatePoints);
        if (!validationResult.isValid())
        {
            ClaimEditPreview preview = previewFromValidation(draft, basePolygon, validationResult, Collections.singletonList("This shaped claim would intersect itself or violate closure rules."));
            ClaimEditFailureType failureType = validationResult.issues().stream()
                    .anyMatch(issue -> issue.type() == OrthogonalPolygonValidationIssueType.SELF_INTERSECTION)
                    ? ClaimEditFailureType.SELF_INTERSECTION
                    : ClaimEditFailureType.INVALID_GEOMETRY;
            return ClaimEditResult.failure(failureType, null, session.withPreview(preview), preview, preview.messages());
        }

        ShapedPathDraft updatedDraft = new ShapedPathDraft(draft.claimId(), candidatePoints, null, true);
        ClaimEditPreview preview = closedPreview(
                updatedDraft,
                validationResult.polygon(),
                rawPoint.equals(point) ? Collections.singletonList("Shaped path closed.") : Collections.singletonList("Shaped path snapped closed.")
        );
        return ClaimEditResult.success(
                session.withOpenPath(updatedDraft).withPreview(preview),
                preview,
                preview.messages()
        );
    }

    private static final class BoundaryMergeAttempt
    {
        private final @NotNull OrthogonalPoint2i reconnectPoint;
        private final @NotNull List<OrthogonalPoint2i> candidatePoints;
        private final @NotNull OrthogonalPolygonValidationResult result;
        private final boolean containsOriginal;
        private final int area;
        private final int overlap;
        private final int distance;

        private BoundaryMergeAttempt(
                @NotNull OrthogonalPoint2i reconnectPoint,
                @NotNull List<OrthogonalPoint2i> candidatePoints,
                @NotNull OrthogonalPolygonValidationResult result,
                boolean containsOriginal,
                int area,
                int overlap,
                int distance)
        {
            this.reconnectPoint = reconnectPoint;
            this.candidatePoints = Collections.unmodifiableList(new ArrayList<>(candidatePoints));
            this.result = result;
            this.containsOriginal = containsOriginal;
            this.area = area;
            this.overlap = overlap;
            this.distance = distance;
        }

        @NotNull List<OrthogonalPoint2i> candidatePoints()
        {
            return candidatePoints;
        }

        @NotNull OrthogonalPolygonValidationResult result()
        {
            return result;
        }

        boolean containsOriginal()
        {
            return containsOriginal;
        }

        int area()
        {
            return area;
        }

        int overlap()
        {
            return overlap;
        }

        int distance()
        {
            return distance;
        }
    }

    private @NotNull List<OrthogonalPoint2i> findBoundaryReconnectPoints(
            @NotNull OrthogonalPolygon polygon,
            @NotNull OrthogonalPoint2i from,
            @NotNull OrthogonalPoint2i to,
            @Nullable OrthogonalPoint2i excludedPoint)
    {
        OrthogonalEdge2i segment = new OrthogonalEdge2i(from, to);
        if (!segment.isOrthogonal())
        {
            return Collections.emptyList();
        }

        List<OrthogonalPoint2i> candidates = new ArrayList<>();
        for (OrthogonalEdge2i edge : polygon.edges())
        {
            OrthogonalPoint2i candidate = nearestIntersectionPoint(segment, edge, excludedPoint);
            if (candidate == null || candidate.equals(from) || candidates.contains(candidate))
            {
                continue;
            }
            candidates.add(candidate);
        }

        candidates.sort(Comparator
                .comparingInt((OrthogonalPoint2i candidate) -> Math.abs(candidate.x() - from.x()) + Math.abs(candidate.z() - from.z()))
                .thenComparingInt(OrthogonalPoint2i::x)
                .thenComparingInt(OrthogonalPoint2i::z));
        return Collections.unmodifiableList(new ArrayList<>(candidates));
    }

    private @Nullable BoundaryMergeAttempt selectBestBoundaryMerge(
            @NotNull OrthogonalPolygon basePolygon,
            @NotNull List<OrthogonalPoint2i> draftPoints,
            @NotNull List<OrthogonalPoint2i> reconnectCandidates)
    {
        BoundaryMergeAttempt best = null;
        OrthogonalPoint2i lastPoint = draftPoints.get(draftPoints.size() - 1);
        for (OrthogonalPoint2i reconnectPoint : reconnectCandidates)
        {
            List<OrthogonalPoint2i> candidatePoints = new ArrayList<>(draftPoints);
            candidatePoints.add(reconnectPoint);
            if (!reshapeLeavesBoundary(basePolygon, candidatePoints))
            {
                continue;
            }

            OrthogonalPolygonValidationResult mergeResult;
            try
            {
                mergeResult = mergeDraftIntoExistingPolygon(basePolygon, candidatePoints);
            }
            catch (IllegalArgumentException ignored)
            {
                continue;
            }

            if (!mergeResult.isValid() || mergeResult.polygon() == null)
            {
                continue;
            }

            boolean containsOriginal = polygonContainsPolygon(mergeResult.polygon(), basePolygon);
            int area = polygonArea(mergeResult.polygon());
            int overlap = polygonOverlapArea(basePolygon, mergeResult.polygon());
            int distance = Math.abs(reconnectPoint.x() - lastPoint.x()) + Math.abs(reconnectPoint.z() - lastPoint.z());
            BoundaryMergeAttempt attempt = new BoundaryMergeAttempt(
                    reconnectPoint,
                    Collections.unmodifiableList(new ArrayList<>(candidatePoints)),
                    mergeResult,
                    containsOriginal,
                    area,
                    overlap,
                    distance
            );

            if (best == null
                    || compareBoundaryMergeAttempts(attempt, best) < 0)
            {
                best = attempt;
            }
        }

        return best;
    }

    private int compareBoundaryMergeAttempts(
            @NotNull BoundaryMergeAttempt first,
            @NotNull BoundaryMergeAttempt second)
    {
        if (first.containsOriginal() != second.containsOriginal())
        {
            return first.containsOriginal() ? -1 : 1;
        }

        if (first.area() != second.area())
        {
            return Integer.compare(second.area(), first.area());
        }

        if (first.overlap() != second.overlap())
        {
            return Integer.compare(second.overlap(), first.overlap());
        }

        if (first.distance() != second.distance())
        {
            return Integer.compare(first.distance(), second.distance());
        }

        return 0;
    }

    private @Nullable OrthogonalPoint2i nearestIntersectionPoint(
            @NotNull OrthogonalEdge2i segment,
            @NotNull OrthogonalEdge2i boundary,
            @Nullable OrthogonalPoint2i excludedPoint)
    {
        if (segment.isHorizontal() && boundary.isVertical())
        {
            OrthogonalPoint2i point = new OrthogonalPoint2i(boundary.start().x(), segment.start().z());
            return segment.containsPoint(point) && boundary.containsPoint(point) && !point.equals(excludedPoint) ? point : null;
        }

        if (segment.isVertical() && boundary.isHorizontal())
        {
            OrthogonalPoint2i point = new OrthogonalPoint2i(segment.start().x(), boundary.start().z());
            return segment.containsPoint(point) && boundary.containsPoint(point) && !point.equals(excludedPoint) ? point : null;
        }

        if (segment.isHorizontal() && boundary.isHorizontal() && segment.start().z() == boundary.start().z())
        {
            return nearestOverlapPoint(
                    segment.start(),
                    segment.end(),
                    Math.max(segment.minX(), boundary.minX()),
                    Math.min(segment.maxX(), boundary.maxX()),
                    true,
                    excludedPoint
            );
        }

        if (segment.isVertical() && boundary.isVertical() && segment.start().x() == boundary.start().x())
        {
            return nearestOverlapPoint(
                    segment.start(),
                    segment.end(),
                    Math.max(segment.minZ(), boundary.minZ()),
                    Math.min(segment.maxZ(), boundary.maxZ()),
                    false,
                    excludedPoint
            );
        }

        return null;
    }

    private @Nullable OrthogonalPoint2i nearestOverlapPoint(
            @NotNull OrthogonalPoint2i from,
            @NotNull OrthogonalPoint2i to,
            int overlapMin,
            int overlapMax,
            boolean horizontal,
            @Nullable OrthogonalPoint2i excludedPoint)
    {
        if (overlapMin > overlapMax)
        {
            return null;
        }

        int step = horizontal
                ? Integer.compare(to.x(), from.x())
                : Integer.compare(to.z(), from.z());
        if (step == 0)
        {
            return null;
        }

        int coordinate = horizontal ? from.x() + step : from.z() + step;
        while (coordinate >= overlapMin && coordinate <= overlapMax)
        {
            OrthogonalPoint2i point = horizontal
                    ? new OrthogonalPoint2i(coordinate, from.z())
                    : new OrthogonalPoint2i(from.x(), coordinate);
            if (!point.equals(excludedPoint))
            {
                return point;
            }
            coordinate += step;
        }

        return null;
    }

    private boolean isOrthogonalStep(@NotNull OrthogonalPoint2i first, @NotNull OrthogonalPoint2i second)
    {
        return first.x() == second.x() ^ first.z() == second.z();
    }

    /**
     * Check if two orthogonal segments cross. Assumes both segments are orthogonal
     * (axis-aligned) and non-zero-length. Segments sharing an endpoint are not
     * considered crossing.
     */
    private static boolean orthogonalSegmentsCross(@NotNull OrthogonalEdge2i a, @NotNull OrthogonalEdge2i b)
    {
        // If both have the same orientation, they can't cross (parallel or collinear)
        boolean aVertical = a.start().x() == a.end().x();
        boolean bVertical = b.start().x() == b.end().x();
        if (aVertical == bVertical)
        {
            return false;
        }

        // a is one orientation, b is the other. Find the crossing point.
        OrthogonalEdge2i vertical = aVertical ? a : b;
        OrthogonalEdge2i horizontal = aVertical ? b : a;

        int crossX = vertical.start().x(); // fixed X for vertical segment
        int crossZ = horizontal.start().z(); // fixed Z for horizontal segment

        // Check that crossX is within horizontal's X range
        int hMinX = Math.min(horizontal.start().x(), horizontal.end().x());
        int hMaxX = Math.max(horizontal.start().x(), horizontal.end().x());
        if (crossX < hMinX || crossX > hMaxX)
        {
            return false;
        }

        // Check that crossZ is within vertical's Z range
        int vMinZ = Math.min(vertical.start().z(), vertical.end().z());
        int vMaxZ = Math.max(vertical.start().z(), vertical.end().z());
        if (crossZ < vMinZ || crossZ > vMaxZ)
        {
            return false;
        }

        // The crossing point is strictly inside both segments (not at endpoints)
        // because shared endpoints were already filtered out by the caller.
        return true;
    }

    private @NotNull OrthogonalPoint2i snapToOrthogonal(@NotNull OrthogonalPoint2i anchor, @NotNull OrthogonalPoint2i rawPoint)
    {
        int dX = Math.abs(rawPoint.x() - anchor.x());
        int dZ = Math.abs(rawPoint.z() - anchor.z());
        if (dX < dZ)
        {
            return new OrthogonalPoint2i(anchor.x(), rawPoint.z());
        }

        return new OrthogonalPoint2i(rawPoint.x(), anchor.z());
    }

    private boolean reshapeLeavesBoundary(
            @NotNull OrthogonalPolygon polygon,
            @NotNull List<OrthogonalPoint2i> draftPoints)
    {
        if (draftPoints.size() < 3)
        {
            return false;
        }

        for (int i = 1; i < draftPoints.size() - 1; i++)
        {
            if (!isBoundaryPoint(polygon, draftPoints.get(i)))
            {
                return true;
            }
        }

        return false;
    }

    private @NotNull ClaimEditPreview draftPreview(
            @NotNull ShapedPathDraft draft,
            @Nullable OrthogonalPolygon polygon,
            @NotNull List<String> messages)
    {
        return new ClaimEditPreview(polygon, null, draft.points(), draft.snappedPreviewPoint(), Collections.emptyList(), Collections.emptyList(), messages);
    }

    private @NotNull ClaimEditPreview closedPreview(
            @NotNull ShapedPathDraft draft,
            @Nullable OrthogonalPolygon polygon,
            @NotNull List<String> messages
    )
    {
        return new ClaimEditPreview(polygon, null, draft.points(), draft.snappedPreviewPoint(), Collections.emptyList(), Collections.emptyList(), messages);
    }

    private @NotNull ClaimEditPreview previewFromValidation(
            @NotNull ShapedPathDraft draft,
            @Nullable OrthogonalPolygon polygon,
            @NotNull OrthogonalPolygonValidationResult validationResult,
            @NotNull List<String> messages
    )
    {
        List<OrthogonalPoint2i> conflictPoints = validationResult.issues().stream()
                .map(OrthogonalPolygonValidationIssue::point)
                .filter(point -> point != null)
                .collect(Collectors.toList());
        return new ClaimEditPreview(
                validationResult.polygon() == null ? polygon : validationResult.polygon(),
                null,
                draft.points(),
                draft.snappedPreviewPoint(),
                conflictPoints,
                validationResult.issues(),
                messages
        );
    }

    private @NotNull ClaimEditPreview previewFromPolygonValidation(
            @NotNull OrthogonalPolygonValidationResult validationResult,
            @NotNull List<String> messages
    )
    {
        List<OrthogonalPoint2i> conflictPoints = validationResult.issues().stream()
                .map(OrthogonalPolygonValidationIssue::point)
                .filter(point -> point != null)
                .collect(Collectors.toList());
        return new ClaimEditPreview(
                validationResult.polygon(),
                null,
                Collections.emptyList(),
                null,
                conflictPoints,
                validationResult.issues(),
                messages
        );
    }

    private boolean isBoundaryPoint(@NotNull OrthogonalPolygon polygon, @NotNull OrthogonalPoint2i point)
    {
        if (polygon.corners().contains(point))
        {
            return true;
        }

        return polygon.edgeIndexesContainingInteriorPoint(point).size() == 1;
    }

    private @NotNull OrthogonalPolygonValidationResult mergeDraftIntoExistingPolygon(
            @NotNull OrthogonalPolygon polygon,
            @NotNull List<OrthogonalPoint2i> draftPoints)
    {
        OrthogonalPoint2i start = draftPoints.get(0);
        OrthogonalPoint2i end = draftPoints.get(draftPoints.size() - 1);

        OrthogonalPolygon working = ensureBoundaryVertex(ensureBoundaryVertex(polygon, start), end);
        int startIndex = working.corners().indexOf(start);
        int endIndex = working.corners().indexOf(end);
        if (startIndex < 0 || endIndex < 0 || startIndex == endIndex)
        {
            throw new IllegalArgumentException("Reshape paths must begin and end on different boundary points.");
        }

        OrthogonalPolygonValidationResult forwardCandidate = validateMergedCandidate(
                draftPoints,
                pathAlongPolygon(working.corners(), endIndex, startIndex, true)
        );
        OrthogonalPolygonValidationResult reverseCandidate = validateMergedCandidate(
                draftPoints,
                pathAlongPolygon(working.corners(), endIndex, startIndex, false)
        );

        OrthogonalPolygonValidationResult unionFallback = tryUnionFallback(working, draftPoints, forwardCandidate, reverseCandidate);
        if (unionFallback != null)
        {
            return unionFallback;
        }

        return chooseMergedCandidate(working, draftPoints, forwardCandidate, reverseCandidate);
    }

    private @NotNull OrthogonalPolygon ensureBoundaryVertex(
            @NotNull OrthogonalPolygon polygon,
            @NotNull OrthogonalPoint2i point)
    {
        if (polygon.corners().contains(point))
        {
            return polygon;
        }

        List<Integer> matches = polygon.edgeIndexesContainingInteriorPoint(point);
        if (matches.size() != 1)
        {
            throw new IllegalArgumentException("Point does not lie on the existing claim boundary.");
        }

        return polygon.insertNode(matches.get(0), point);
    }

    private @NotNull List<OrthogonalPoint2i> pathAlongPolygon(
            @NotNull List<OrthogonalPoint2i> corners,
            int fromIndex,
            int toIndex,
            boolean forward)
    {
        List<OrthogonalPoint2i> path = new ArrayList<>();
        int index = fromIndex;
        path.add(corners.get(index));
        while (index != toIndex)
        {
            index = forward
                    ? (index + 1) % corners.size()
                    : (index - 1 + corners.size()) % corners.size();
            path.add(corners.get(index));
        }
        return path;
    }

    private @NotNull OrthogonalPolygonValidationResult validateMergedCandidate(
            @NotNull List<OrthogonalPoint2i> draftPoints,
            @NotNull List<OrthogonalPoint2i> boundaryArc)
    {
        List<OrthogonalPoint2i> candidatePath = new ArrayList<>(draftPoints);
        candidatePath.addAll(boundaryArc.subList(1, boundaryArc.size()));
        return OrthogonalPolygon.validatePath(candidatePath);
    }

    private @NotNull OrthogonalPolygonValidationResult chooseMergedCandidate(
            @NotNull OrthogonalPolygon originalPolygon,
            @NotNull List<OrthogonalPoint2i> draftPoints,
            @Nullable OrthogonalPolygonValidationResult first,
            @Nullable OrthogonalPolygonValidationResult second)
    {
        boolean firstValid = first != null && first.isValid() && first.polygon() != null;
        boolean secondValid = second != null && second.isValid() && second.polygon() != null;

        if (firstValid && !secondValid)
        {
            return first;
        }

        if (!firstValid && secondValid)
        {
            return second;
        }

        if (!firstValid)
        {
            throw new IllegalArgumentException("That reshape path cannot merge cleanly into the existing claim boundary.");
        }

        // Both guaranteed non-null with valid polygons by the firstValid/secondValid guards above
        @SuppressWarnings("null")
        boolean firstContainsOriginal = polygonContainsPolygon(first.polygon(), originalPolygon);
        @SuppressWarnings("null")
        boolean secondContainsOriginal = polygonContainsPolygon(second.polygon(), originalPolygon);
        if (firstContainsOriginal != secondContainsOriginal)
        {
            return firstContainsOriginal ? first : second;
        }

        boolean extendsOutsideOriginal = draftPoints.stream()
                .skip(1)
                .limit(Math.max(0, draftPoints.size() - 2L))
                .anyMatch(point -> !originalPolygon.contains(point));

        int firstArea = polygonArea(first.polygon());
        int secondArea = polygonArea(second.polygon());
        if (firstArea != secondArea)
        {
            if (extendsOutsideOriginal)
            {
                return firstArea > secondArea ? first : second;
            }

            return firstArea < secondArea ? first : second;
        }

        int firstOverlap = polygonOverlapArea(originalPolygon, first.polygon());
        int secondOverlap = polygonOverlapArea(originalPolygon, second.polygon());
        if (firstOverlap != secondOverlap)
        {
            return firstOverlap > secondOverlap ? first : second;
        }

        return first;
    }

    private @Nullable OrthogonalPolygonValidationResult tryUnionFallback(
            @NotNull OrthogonalPolygon originalPolygon,
            @NotNull List<OrthogonalPoint2i> draftPoints,
            @NotNull OrthogonalPolygonValidationResult first,
            @NotNull OrthogonalPolygonValidationResult second)
    {
        boolean extendsOutsideOriginal = draftPoints.stream()
                .skip(1)
                .limit(Math.max(0, draftPoints.size() - 2L))
                .anyMatch(point -> !originalPolygon.contains(point));

        if (!extendsOutsideOriginal)
        {
            return null;
        }

        OrthogonalPolygon patch = null;
        if (first.isValid() && first.polygon() != null && !polygonContainsPolygon(first.polygon(), originalPolygon))
        {
            patch = first.polygon();
        }
        else if (second.isValid() && second.polygon() != null && !polygonContainsPolygon(second.polygon(), originalPolygon))
        {
            patch = second.polygon();
        }

        if (patch == null)
        {
            return null;
        }

        try
        {
            OrthogonalPolygon unionPolygon = OrthogonalPolygon.union(originalPolygon, patch);
            OrthogonalPolygonValidationResult result = OrthogonalPolygon.validatePath(unionPolygon.closedPath());
            return result.isValid() ? result : null;
        }
        catch (IllegalArgumentException exception)
        {
            return null;
        }
    }


    private int polygonArea(@NotNull OrthogonalPolygon polygon)
    {
        int area = 0;
        for (int x = polygon.minX(); x <= polygon.maxX(); x++)
        {
            for (int z = polygon.minZ(); z <= polygon.maxZ(); z++)
            {
                if (polygon.containsCell(x, z))
                {
                    area++;
                }
            }
        }

        return area;
    }

    private boolean polygonContainsPolygon(@NotNull OrthogonalPolygon container, @NotNull OrthogonalPolygon contents)
    {
        for (int x = contents.minX(); x <= contents.maxX(); x++)
        {
            for (int z = contents.minZ(); z <= contents.maxZ(); z++)
            {
                OrthogonalPoint2i point = new OrthogonalPoint2i(x, z);
                if (contents.containsCell(x, z) && !container.containsCell(x, z))
                {
                    return false;
                }
            }
        }

        return true;
    }

    private int polygonOverlapArea(@NotNull OrthogonalPolygon first, @NotNull OrthogonalPolygon second)
    {
        int area = 0;
        int minX = Math.max(first.minX(), second.minX());
        int maxX = Math.min(first.maxX(), second.maxX());
        int minZ = Math.max(first.minZ(), second.minZ());
        int maxZ = Math.min(first.maxZ(), second.maxZ());

        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                OrthogonalPoint2i point = new OrthogonalPoint2i(x, z);
                if (first.containsCell(x, z) && second.containsCell(x, z))
                {
                    area++;
                }
            }
        }

        return area;
    }













    private @NotNull ClaimEditResult handleAddNode(@NotNull ClaimEditorSession session, @NotNull ClaimEditIntent intent)
    {
        if (session.mode() != ClaimEditorMode.SHAPED)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("Nodes can only be added while in shaped mode.")
            );
        }

        if (!intent.holdingModificationTool())
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    ClaimEditMessageKey.MUST_HOLD_MODIFICATION_TOOL_FOR_THAT,
                    session,
                    session.preview(),
                    Collections.singletonList("You must be holding a golden shovel to do that.")
            );
        }

        OrthogonalPolygon polygon = session.preview().polygon();
        if (polygon == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.NOT_EDITABLE_FROM_HERE,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("No parent claim boundary is selected for node editing.")
            );
        }

        OrthogonalPoint2i point = intent.point();
        if (point == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("No node point was provided.")
            );
        }

        int existingCornerIndex = polygon.corners().indexOf(point);
        if (existingCornerIndex >= 0)
        {
            if (!polygon.isRemovableNode(existingCornerIndex))
            {
                return ClaimEditResult.failure(
                        ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                        null,
                        session,
                        session.preview(),
                        Collections.singletonList("That point is already a claim corner.")
                );
            }

            OrthogonalPolygon updatedPolygon;
            try
            {
                updatedPolygon = polygon.removeNode(existingCornerIndex);
            }
            catch (IllegalArgumentException exception)
            {
                return ClaimEditResult.failure(
                        ClaimEditFailureType.INVALID_GEOMETRY,
                        null,
                        session,
                        session.preview(),
                        Collections.singletonList(exception.getMessage())
                );
            }

            Integer mergedEdgeIndex = findMatchingEdgeIndex(updatedPolygon, polygon.corners().get(Math.floorMod(existingCornerIndex - 1, polygon.corners().size())), polygon.corners().get((existingCornerIndex + 1) % polygon.corners().size()));
            SegmentSelection selection = mergedEdgeIndex == null
                    ? null
                    : new SegmentSelection(resolveClaimId(session), mergedEdgeIndex, null, null, null);
            ClaimEditPreview preview = new ClaimEditPreview(
                    updatedPolygon,
                    selection,
                    Collections.emptyList(),
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.singletonList("Boundary segment marker removed.")
            );
            ClaimEditorSession updatedSession = session.withActiveSegment(selection).withPreview(preview);
            return ClaimEditResult.success(updatedSession, preview, preview.messages());
        }

        Integer edgeIndex = resolveNodeEdgeIndex(session, polygon, point);
        if (edgeIndex == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("That node point does not resolve to a single editable boundary segment.")
            );
        }

        OrthogonalPolygon updatedPolygon;
        try
        {
            updatedPolygon = polygon.insertNode(edgeIndex, point);
        }
        catch (IllegalArgumentException exception)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.INVALID_GEOMETRY,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList(exception.getMessage())
            );
        }

        int selectedEdgeIndex = Math.min(edgeIndex + 1, updatedPolygon.edges().size() - 1);
        SegmentSelection selection = new SegmentSelection(resolveClaimId(session), selectedEdgeIndex, null, null, null);
        ClaimEditPreview preview = new ClaimEditPreview(
                updatedPolygon,
                selection,
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("Boundary segment marker added.")
        );
        ClaimEditorSession updatedSession = session.withActiveSegment(selection).withPreview(preview);
        return ClaimEditResult.success(updatedSession, preview, preview.messages());
    }

    private @NotNull ClaimEditResult handleCancelPath(@NotNull ClaimEditorSession session)
    {
        if (session.openPath() == null)
        {
            return ClaimEditResult.failure(
                    ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST,
                    null,
                    session,
                    session.preview(),
                    Collections.singletonList("There is no shaped path to cancel.")
            );
        }

        ClaimEditPreview existingPreview = session.preview();
        ClaimEditPreview preview = new ClaimEditPreview(
                existingPreview.polygon(),
                existingPreview.highlightedSegment(),
                Collections.emptyList(),
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList("Shaped path cancelled.")
        );
        ClaimEditorSession updatedSession = session.withOpenPath(null).withPreview(preview);
        return ClaimEditResult.success(updatedSession, preview, preview.messages());
    }

    private @Nullable Integer resolveNodeEdgeIndex(
            @NotNull ClaimEditorSession session,
            @NotNull OrthogonalPolygon polygon,
            @NotNull OrthogonalPoint2i point
    )
    {
        List<Integer> matches = polygon.edgeIndexesContainingInteriorPoint(point);
        if (matches.isEmpty())
        {
            return null;
        }

        // Keep node placement flexible: allow selecting any boundary segment even when an
        // active segment exists from a previous click.
        if (matches.size() == 1)
        {
            return matches.get(0);
        }

        if (session.activeSegment() != null && matches.contains(session.activeSegment().edgeIndex()))
        {
            return session.activeSegment().edgeIndex();
        }

        return matches.get(0);
    }

    private long resolveClaimId(@NotNull ClaimEditorSession session)
    {
        if (session.activeTarget() != null && session.activeTarget().claimId() != null)
        {
            return session.activeTarget().claimId();
        }

        return -1L;
    }

    private @NotNull SegmentExpansion expandSegment(
            @NotNull OrthogonalPolygon polygon,
            @NotNull SegmentSelection activeSegment,
            int amount)
    {
        if (amount == 0)
        {
            return new SegmentExpansion(
                    OrthogonalPolygon.validatePath(polygon.closedPath()),
                    activeSegment
            );
        }

        if (activeSegment.edgeIndex() < 0 || activeSegment.edgeIndex() >= polygon.edges().size())
        {
            throw new IllegalArgumentException("Selected segment is no longer valid.");
        }

        OrthogonalEdge2i edge = polygon.edges().get(activeSegment.edgeIndex());
        OffsetVector outward = outwardOffset(polygon, edge);
        int signedAmount = amount;
        if (activeSegment.outwardDirection() != null)
        {
            OrthogonalDirection positiveDirection = edge.outwardDirectionForPositiveOffset();
            signedAmount = activeSegment.outwardDirection() == positiveDirection ? amount : -amount;
        }
        int offsetX = outward.dx() * signedAmount;
        int offsetZ = outward.dz() * signedAmount;
        OrthogonalPoint2i movedStart = new OrthogonalPoint2i(edge.start().x() + offsetX, edge.start().z() + offsetZ);
        OrthogonalPoint2i movedEnd = new OrthogonalPoint2i(edge.end().x() + offsetX, edge.end().z() + offsetZ);
        debug("expandSegment claim=%s edgeIndex=%d amount=%d edge=%s movedStart=%s movedEnd=%s polygon=%s",
                activeSegment.claimId(),
                activeSegment.edgeIndex(),
                amount,
                edge,
                movedStart,
                movedEnd,
                polygon.corners());
        OrthogonalPolygonValidationResult directResult = polygon.expandEdge(activeSegment.edgeIndex(), signedAmount);
        if (directResult.isValid() && directResult.polygon() != null)
        {
            Integer directEdgeIndex = findMatchingEdgeIndex(directResult.polygon(), movedStart, movedEnd);
            debug("expandSegment directResult polygon=%s directEdgeIndex=%s issues=%s",
                    directResult.polygon().corners(),
                    directEdgeIndex,
                    directResult.issues());
            SegmentSelection directSelection = directEdgeIndex == null
                    ? null
                    : new SegmentSelection(activeSegment.claimId(), directEdgeIndex, null, null, null);
            return new SegmentExpansion(directResult, directSelection);
        }
        OrthogonalPolygon sweep = OrthogonalPolygon.fromClosedPath(Arrays.asList(edge.start(), edge.end(), movedEnd, movedStart, edge.start()));
        debug("expandSegment sweep=%s", sweep.corners());

        OrthogonalPolygon updatedPolygon = signedAmount > 0
                ? OrthogonalPolygon.union(polygon, sweep)
                : subtractPolygons(polygon, sweep);
        OrthogonalPolygonValidationResult result = OrthogonalPolygon.validatePath(updatedPolygon.closedPath());
        Integer updatedEdgeIndex = findMatchingEdgeIndex(updatedPolygon, movedStart, movedEnd);
        debug("expandSegment updatedPolygon=%s updatedEdgeIndex=%s issues=%s",
                updatedPolygon.corners(),
                updatedEdgeIndex,
                result.issues());
        SegmentSelection updatedSelection = updatedEdgeIndex == null
                ? null
                : new SegmentSelection(activeSegment.claimId(), updatedEdgeIndex, null, null, null);
        return new SegmentExpansion(result, updatedSelection);
    }

    private @NotNull OffsetVector outwardOffset(@NotNull OrthogonalPolygon polygon, @NotNull OrthogonalEdge2i edge)
    {
        int signedArea = signedArea2(polygon.corners());
        boolean counterClockwise = signedArea > 0;
        int dx = Integer.compare(edge.end().x(), edge.start().x());
        int dz = Integer.compare(edge.end().z(), edge.start().z());
        int leftDx = -dz;
        int leftDz = dx;
        return counterClockwise
                ? new OffsetVector(leftDx, leftDz)
                : new OffsetVector(dz, -dx);
    }

    private int signedArea2(@NotNull List<OrthogonalPoint2i> corners)
    {
        long area2 = 0L;
        for (int i = 0; i < corners.size(); i++)
        {
            OrthogonalPoint2i current = corners.get(i);
            OrthogonalPoint2i next = corners.get((i + 1) % corners.size());
            area2 += (long) current.x() * next.z() - (long) next.x() * current.z();
        }

        return (int) Long.signum(area2);
    }

    private @Nullable Integer findMatchingEdgeIndex(
            @NotNull OrthogonalPolygon polygon,
            @NotNull OrthogonalPoint2i start,
            @NotNull OrthogonalPoint2i end)
    {
        OrthogonalEdge2i target = new OrthogonalEdge2i(start, end);
        Integer bestIndex = null;
        int bestDistance = Integer.MAX_VALUE;
        int bestOverlap = -1;

        for (int i = 0; i < polygon.edges().size(); i++)
        {
            OrthogonalEdge2i edge = polygon.edges().get(i);
            if (target.isHorizontal() != edge.isHorizontal() || target.isVertical() != edge.isVertical())
            {
                continue;
            }

            int overlap;
            int distance;
            if (target.isHorizontal())
            {
                overlap = Math.min(target.maxX(), edge.maxX()) - Math.max(target.minX(), edge.minX());
                distance = Math.abs(target.start().z() - edge.start().z());
            }
            else
            {
                overlap = Math.min(target.maxZ(), edge.maxZ()) - Math.max(target.minZ(), edge.minZ());
                distance = Math.abs(target.start().x() - edge.start().x());
            }

            if (overlap <= 0)
            {
                continue;
            }

            if (distance < bestDistance || (distance == bestDistance && overlap > bestOverlap))
            {
                bestDistance = distance;
                bestOverlap = overlap;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private @NotNull OrthogonalPolygon subtractPolygons(
            @NotNull OrthogonalPolygon source,
            @NotNull OrthogonalPolygon cutout)
    {
        Set<OrthogonalPoint2i> occupied = occupiedPoints(source);
        occupied.removeAll(occupiedPoints(cutout));
        if (occupied.isEmpty())
        {
            throw new IllegalArgumentException("That segment move would remove the claim.");
        }

        if (!isConnected(occupied))
        {
            throw new IllegalArgumentException("That segment move would split the claim into multiple pieces.");
        }

        return OrthogonalPolygon.fromOccupiedPoints(occupied);
    }

    private @NotNull Set<OrthogonalPoint2i> occupiedPoints(@NotNull OrthogonalPolygon polygon)
    {
        Set<OrthogonalPoint2i> occupied = new HashSet<>();
        for (int x = polygon.minX(); x <= polygon.maxX(); x++)
        {
            for (int z = polygon.minZ(); z <= polygon.maxZ(); z++)
            {
                OrthogonalPoint2i point = new OrthogonalPoint2i(x, z);
                if (polygon.contains(point))
                {
                    occupied.add(point);
                }
            }
        }
        return occupied;
    }

    private boolean isConnected(@NotNull Set<OrthogonalPoint2i> occupied)
    {
        if (occupied.isEmpty())
        {
            return true;
        }

        Set<OrthogonalPoint2i> visited = new HashSet<>();
        List<OrthogonalPoint2i> queue = new ArrayList<>();
        OrthogonalPoint2i start = occupied.iterator().next();
        queue.add(start);
        visited.add(start);

        for (int i = 0; i < queue.size(); i++)
        {
            OrthogonalPoint2i current = queue.get(i);
            for (OrthogonalPoint2i neighbor : Arrays.asList(new OrthogonalPoint2i(current.x() + 1, current.z()), new OrthogonalPoint2i(current.x() - 1, current.z()), new OrthogonalPoint2i(current.x(), current.z() + 1), new OrthogonalPoint2i(current.x(), current.z() - 1)))
            {
                if (occupied.contains(neighbor) && visited.add(neighbor))
                {
                    queue.add(neighbor);
                }
            }
        }

        return visited.size() == occupied.size();
    }

    private static final class OffsetVector
    {
        private final int dx;
        private final int dz;

        private OffsetVector(int dx, int dz)
        {
            this.dx = dx;
            this.dz = dz;
        }

        int dx()
        {
            return dx;
        }

        int dz()
        {
            return dz;
        }
    }

    private static final class SegmentExpansion
    {
        private final @NotNull OrthogonalPolygonValidationResult result;
        private final @Nullable SegmentSelection selection;

        private SegmentExpansion(
                @NotNull OrthogonalPolygonValidationResult result,
                @Nullable SegmentSelection selection)
        {
            this.result = result;
            this.selection = selection;
        }

        @NotNull OrthogonalPolygonValidationResult result()
        {
            return result;
        }

        @Nullable SegmentSelection selection()
        {
            return selection;
        }
    }

    private static void debug(@NotNull String format, Object... args)
    {
    }

    private @NotNull OrthogonalPolygon unionPolygons(
            @NotNull OrthogonalPolygon first,
            @NotNull OrthogonalPolygon second)
    {
        return OrthogonalPolygon.union(first, second);
    }

}
