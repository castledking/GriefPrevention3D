package com.griefprevention.claims.editor;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import me.ryanhamshire.GriefPrevention.Messages;
import org.junit.jupiter.api.Test;
import org.bukkit.block.BlockFace;

import java.util.List;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClaimEditorSkeletonTest
{
    @Test
    void entersRequestedMode()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID());

        ClaimEditResult result = editor.apply(session, ClaimEditIntent.enterMode(ClaimEditSource.COMMAND, ClaimEditorMode.SHAPED));

        assertTrue(result.success());
        assertEquals(ClaimEditorMode.SHAPED, result.session().mode());
    }

    @Test
    void reportsUnimplementedIntent()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID());

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(ClaimEditIntentType.COMMIT_PREVIEW, ClaimEditSource.GUI_MAP, null, null, null, null, false, List.of())
        );

        assertFalse(result.success());
        assertEquals(ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST, result.failureType());
    }

    @Test
    void snapsDiagonalCornerToOrthogonalPath()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withOpenPath(new ShapedPathDraft(null, List.of(new OrthogonalPoint2i(0, 0)), null, false));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_CORNER,
                        ClaimEditSource.TOOL,
                        null,
                        null,
                        new OrthogonalPoint2i(2, 5),
                        null,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertEquals(List.of(new OrthogonalPoint2i(0, 0), new OrthogonalPoint2i(0, 5)), result.session().openPath().points());
    }

    @Test
    void startsDraftOnFirstCorner()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND);

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_CORNER,
                        ClaimEditSource.TOOL,
                        null,
                        null,
                        new OrthogonalPoint2i(0, 0),
                        null,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertEquals(List.of(new OrthogonalPoint2i(0, 0)), result.session().openPath().points());
        assertEquals(List.of(new OrthogonalPoint2i(0, 0)), result.preview().draftPoints());
    }

    @Test
    void snapsDiagonalCornerInsteadOfRejecting()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withOpenPath(new ShapedPathDraft(null, List.of(new OrthogonalPoint2i(0, 0)), null, false));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_CORNER,
                        ClaimEditSource.TOOL,
                        null,
                        null,
                        new OrthogonalPoint2i(2, 2),
                        null,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertEquals(List.of(new OrthogonalPoint2i(0, 0), new OrthogonalPoint2i(2, 0)), result.preview().draftPoints());
    }

    @Test
    void closesValidRectanglePath()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withOpenPath(new ShapedPathDraft(
                        null,
                        List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(4, 0),
                                new OrthogonalPoint2i(4, 3),
                                new OrthogonalPoint2i(0, 3)
                        ),
                        null,
                        false
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_CORNER,
                        ClaimEditSource.TOOL,
                        null,
                        null,
                        new OrthogonalPoint2i(0, 0),
                        null,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertNotNull(result.preview().polygon());
        assertTrue(result.session().openPath().closureReady());
    }

    @Test
    void reportsSelfIntersectionOnClosure()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withOpenPath(new ShapedPathDraft(
                        null,
                        List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(6, 0),
                                new OrthogonalPoint2i(6, 6),
                                new OrthogonalPoint2i(2, 6),
                                new OrthogonalPoint2i(2, 2),
                                new OrthogonalPoint2i(4, 2),
                                new OrthogonalPoint2i(4, 4),
                                new OrthogonalPoint2i(0, 4)
                        ),
                        null,
                        false
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_CORNER,
                        ClaimEditSource.TOOL,
                        null,
                        null,
                        new OrthogonalPoint2i(0, 0),
                        null,
                        true,
                        List.of()
                )
        );

        assertFalse(result.success());
        assertEquals(ClaimEditFailureType.SELF_INTERSECTION, result.failureType());
        assertFalse(result.preview().conflictPoints().isEmpty());
    }

    @Test
    void addsNodeToSelectedPolygonBoundary()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withPreview(new ClaimEditPreview(
                        com.griefprevention.geometry.OrthogonalPolygon.fromClosedPath(List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(4, 0),
                                new OrthogonalPoint2i(4, 3),
                                new OrthogonalPoint2i(0, 3),
                                new OrthogonalPoint2i(0, 0)
                        )),
                        null,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_NODE,
                        ClaimEditSource.TOOL,
                        null,
                        42L,
                        new OrthogonalPoint2i(2, 0),
                        null,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertEquals(5, result.preview().polygon().corners().size());
        assertNotNull(result.session().activeSegment());
        assertEquals(1, result.session().activeSegment().edgeIndex());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(2, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3)
        ), result.preview().polygon().corners());
    }

    @Test
    void removesExistingSegmentMarkerNode()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withPreview(new ClaimEditPreview(
                        OrthogonalPolygon.fromClosedPath(List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(2, 0),
                                new OrthogonalPoint2i(4, 0),
                                new OrthogonalPoint2i(4, 3),
                                new OrthogonalPoint2i(0, 3),
                                new OrthogonalPoint2i(0, 0)
                        )),
                        null,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_NODE,
                        ClaimEditSource.TOOL,
                        null,
                        42L,
                        new OrthogonalPoint2i(2, 0),
                        null,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3)
        ), result.preview().polygon().corners());
    }

    @Test
    void rejectsAmbiguousNodePointAtCorner()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withPreview(new ClaimEditPreview(
                        com.griefprevention.geometry.OrthogonalPolygon.fromClosedPath(List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(4, 0),
                                new OrthogonalPoint2i(4, 3),
                                new OrthogonalPoint2i(0, 3),
                                new OrthogonalPoint2i(0, 0)
                        )),
                        null,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_NODE,
                        ClaimEditSource.TOOL,
                        null,
                        42L,
                        new OrthogonalPoint2i(0, 0),
                        null,
                        true,
                        List.of()
                )
        );

        assertFalse(result.success());
        assertEquals(ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST, result.failureType());
    }

    @Test
    void requiresGoldenShovelForShapedCorner()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND);

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_CORNER,
                        ClaimEditSource.TOOL,
                        null,
                        null,
                        new OrthogonalPoint2i(0, 0),
                        null,
                        false,
                        List.of()
                )
        );

        assertFalse(result.success());
        assertEquals(Messages.MustHoldModificationToolForThat, result.fallbackMessage());
    }

    @Test
    void cancelsOpenShapedPath()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withOpenPath(new ShapedPathDraft(
                        null,
                        List.of(new OrthogonalPoint2i(0, 0), new OrthogonalPoint2i(0, 5)),
                        new OrthogonalPoint2i(0, 5),
                        false
                ))
                .withPreview(new ClaimEditPreview(
                        null,
                        null,
                        List.of(new OrthogonalPoint2i(0, 0), new OrthogonalPoint2i(0, 5)),
                        new OrthogonalPoint2i(0, 5),
                        List.of(),
                        List.of(),
                        List.of()
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.CANCEL_PATH,
                        ClaimEditSource.COMMAND,
                        null,
                        null,
                        null,
                        null,
                        false,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertEquals(null, result.session().openPath());
        assertTrue(result.preview().draftPoints().isEmpty());
        assertEquals(null, result.preview().snappedPoint());
    }

    @Test
    void selectsSingleBoundarySegment()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withPreview(new ClaimEditPreview(
                        com.griefprevention.geometry.OrthogonalPolygon.fromClosedPath(List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(2, 0),
                                new OrthogonalPoint2i(4, 0),
                                new OrthogonalPoint2i(4, 3),
                                new OrthogonalPoint2i(0, 3),
                                new OrthogonalPoint2i(0, 0)
                        )),
                        null,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.SELECT_SEGMENT,
                        ClaimEditSource.TOOL,
                        null,
                        42L,
                        new OrthogonalPoint2i(1, 0),
                        null,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertNotNull(result.session().activeSegment());
        assertEquals(0, result.session().activeSegment().edgeIndex());
    }

    @Test
    void rejectsAmbiguousSegmentSelectionAtCorner()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withPreview(new ClaimEditPreview(
                        com.griefprevention.geometry.OrthogonalPolygon.fromClosedPath(List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(2, 0),
                                new OrthogonalPoint2i(4, 0),
                                new OrthogonalPoint2i(4, 3),
                                new OrthogonalPoint2i(0, 3),
                                new OrthogonalPoint2i(0, 0)
                        )),
                        null,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.SELECT_SEGMENT,
                        ClaimEditSource.TOOL,
                        null,
                        42L,
                        new OrthogonalPoint2i(2, 0),
                        null,
                        true,
                        List.of()
                )
        );

        assertFalse(result.success());
        assertEquals(ClaimEditFailureType.AMBIGUOUS_EDIT_REQUEST, result.failureType());
    }

    @Test
    void requiresGoldenShovelForSegmentSelection()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withPreview(new ClaimEditPreview(
                        com.griefprevention.geometry.OrthogonalPolygon.fromClosedPath(List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(2, 0),
                                new OrthogonalPoint2i(4, 0),
                                new OrthogonalPoint2i(4, 3),
                                new OrthogonalPoint2i(0, 3),
                                new OrthogonalPoint2i(0, 0)
                        )),
                        null,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.SELECT_SEGMENT,
                        ClaimEditSource.TOOL,
                        null,
                        42L,
                        new OrthogonalPoint2i(1, 0),
                        null,
                        false,
                        List.of()
                )
        );

        assertFalse(result.success());
        assertEquals(Messages.MustHoldModificationToolForThat, result.fallbackMessage());
    }

    @Test
    void expandsSelectedSegmentImmediately()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        SegmentSelection selection = new SegmentSelection(42L, 1, null, null, null);
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withActiveSegment(selection)
                .withPreview(new ClaimEditPreview(
                        com.griefprevention.geometry.OrthogonalPolygon.fromClosedPath(List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(1, 0),
                                new OrthogonalPoint2i(3, 0),
                                new OrthogonalPoint2i(4, 0),
                                new OrthogonalPoint2i(4, 3),
                                new OrthogonalPoint2i(0, 3),
                                new OrthogonalPoint2i(0, 0)
                        )),
                        selection,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.EXPAND_SEGMENT,
                        ClaimEditSource.COMMAND,
                        null,
                        42L,
                        null,
                        2,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(1, 0),
                new OrthogonalPoint2i(1, 2),
                new OrthogonalPoint2i(3, 2),
                new OrthogonalPoint2i(3, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3)
        ), result.preview().polygon().corners());
        assertNotNull(result.session().activeSegment());
        assertEquals(2, result.session().activeSegment().edgeIndex());
    }

    @Test
    void expandingSegmentMergesIntoAdjacentNib()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        SegmentSelection selection = new SegmentSelection(42L, 4, null, null, null);
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withActiveSegment(selection)
                .withPreview(new ClaimEditPreview(
                        OrthogonalPolygon.fromClosedPath(List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(1, 0),
                                new OrthogonalPoint2i(1, 2),
                                new OrthogonalPoint2i(2, 2),
                                new OrthogonalPoint2i(2, 0),
                                new OrthogonalPoint2i(4, 0),
                                new OrthogonalPoint2i(4, 3),
                                new OrthogonalPoint2i(0, 3),
                                new OrthogonalPoint2i(0, 0)
                        )),
                        selection,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.EXPAND_SEGMENT,
                        ClaimEditSource.COMMAND,
                        null,
                        42L,
                        null,
                        2,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(1, 0),
                new OrthogonalPoint2i(1, 2),
                new OrthogonalPoint2i(4, 2),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3)
        ), result.preview().polygon().corners());
    }

    @Test
    void expandsSelectedSegmentTowardRequestedFace()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        SegmentSelection selection = new SegmentSelection(42L, 1, null, null, BlockFace.NORTH);
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withActiveSegment(selection)
                .withPreview(new ClaimEditPreview(
                        OrthogonalPolygon.fromClosedPath(List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(1, 0),
                                new OrthogonalPoint2i(3, 0),
                                new OrthogonalPoint2i(4, 0),
                                new OrthogonalPoint2i(4, 3),
                                new OrthogonalPoint2i(0, 3),
                                new OrthogonalPoint2i(0, 0)
                        )),
                        selection,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.EXPAND_SEGMENT,
                        ClaimEditSource.COMMAND,
                        null,
                        42L,
                        null,
                        1,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(1, 0),
                new OrthogonalPoint2i(1, -1),
                new OrthogonalPoint2i(3, -1),
                new OrthogonalPoint2i(3, 0),
                new OrthogonalPoint2i(4, 0),
                new OrthogonalPoint2i(4, 3),
                new OrthogonalPoint2i(0, 3)
        ), result.preview().polygon().corners());
    }

    @Test
    void rejectsSegmentExpansionWithoutSelection()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND);

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.EXPAND_SEGMENT,
                        ClaimEditSource.COMMAND,
                        null,
                        null,
                        null,
                        1,
                        true,
                        List.of()
                )
        );

        assertFalse(result.success());
        assertEquals(ClaimEditFailureType.NO_ACTIVE_SEGMENT, result.failureType());
    }

    @Test
    void requiresGoldenShovelForSegmentExpansion()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        SegmentSelection selection = new SegmentSelection(42L, 1, null, null, null);
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.COMMAND)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withActiveSegment(selection)
                .withPreview(new ClaimEditPreview(
                        com.griefprevention.geometry.OrthogonalPolygon.fromClosedPath(List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(1, 0),
                                new OrthogonalPoint2i(3, 0),
                                new OrthogonalPoint2i(4, 0),
                                new OrthogonalPoint2i(4, 3),
                                new OrthogonalPoint2i(0, 3),
                                new OrthogonalPoint2i(0, 0)
                        )),
                        selection,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.EXPAND_SEGMENT,
                        ClaimEditSource.COMMAND,
                        null,
                        42L,
                        null,
                        1,
                        false,
                        List.of()
                )
        );

        assertFalse(result.success());
        assertEquals(Messages.MustHoldModificationToolForThat, result.fallbackMessage());
    }

    @Test
    void mergingOutsideBoundaryPathKeepsOriginalClaimBody()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.TOOL)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withPreview(new ClaimEditPreview(
                        com.griefprevention.geometry.OrthogonalPolygon.fromClosedPath(List.of(
                                new OrthogonalPoint2i(0, 0),
                                new OrthogonalPoint2i(8, 0),
                                new OrthogonalPoint2i(8, 8),
                                new OrthogonalPoint2i(0, 8),
                                new OrthogonalPoint2i(0, 0)
                        )),
                        null,
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of()
                ))
                .withOpenPath(new ShapedPathDraft(
                        42L,
                        List.of(
                                new OrthogonalPoint2i(2, 8),
                                new OrthogonalPoint2i(2, 10),
                                new OrthogonalPoint2i(6, 10)
                        ),
                        null,
                        false
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_CORNER,
                        ClaimEditSource.TOOL,
                        null,
                        42L,
                        new OrthogonalPoint2i(6, 8),
                        null,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertNotNull(result.preview().polygon());
        assertTrue(result.preview().polygon().corners().contains(new OrthogonalPoint2i(2, 10)));
        assertTrue(result.preview().polygon().corners().contains(new OrthogonalPoint2i(6, 10)));
        assertTrue(polygonContains(result.preview().polygon(), new OrthogonalPoint2i(4, 4)));
    }

    @Test
    void existingClaimBoundaryClicksDoNotMergeUntilPathLeavesBoundary()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        OrthogonalPolygon original = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(8, 0),
                new OrthogonalPoint2i(8, 8),
                new OrthogonalPoint2i(0, 8),
                new OrthogonalPoint2i(0, 0)
        ));
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.TOOL)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withPreview(new ClaimEditPreview(original, null, List.of(), null, List.of(), List.of(), List.of()))
                .withOpenPath(new ShapedPathDraft(
                        42L,
                        List.of(new OrthogonalPoint2i(0, 4)),
                        null,
                        false
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_CORNER,
                        ClaimEditSource.TOOL,
                        null,
                        42L,
                        new OrthogonalPoint2i(0, 6),
                        null,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertFalse(result.session().openPath().closureReady());
        assertEquals(original.corners(), result.preview().polygon().corners());
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 4),
                new OrthogonalPoint2i(0, 5)
        ), result.preview().draftPoints());
    }

    @Test
    void mergingOutsideBoundaryPathKeepsExistingShapedClaimBody()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        OrthogonalPolygon original = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(8, 0),
                new OrthogonalPoint2i(8, 6),
                new OrthogonalPoint2i(5, 6),
                new OrthogonalPoint2i(5, 8),
                new OrthogonalPoint2i(0, 8),
                new OrthogonalPoint2i(0, 0)
        ));
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.TOOL)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withPreview(new ClaimEditPreview(original, null, List.of(), null, List.of(), List.of(), List.of()))
                .withOpenPath(new ShapedPathDraft(
                        42L,
                        List.of(
                                new OrthogonalPoint2i(5, 8),
                                new OrthogonalPoint2i(5, 10),
                                new OrthogonalPoint2i(10, 10),
                                new OrthogonalPoint2i(10, 2)
                        ),
                        null,
                        false
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_CORNER,
                        ClaimEditSource.TOOL,
                        null,
                        42L,
                        new OrthogonalPoint2i(8, 2),
                        null,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertNotNull(result.preview().polygon());
        assertTrue(polygonContains(result.preview().polygon(), new OrthogonalPoint2i(2, 2)));
        assertTrue(polygonContains(result.preview().polygon(), new OrthogonalPoint2i(2, 7)));
        assertTrue(polygonContains(result.preview().polygon(), new OrthogonalPoint2i(9, 5)));
    }

    @Test
    void diagonalNibFillMergesIntoMainBodyInsteadOfTinyLocalLoop()
    {
        ClaimEditor editor = new ClaimEditorSkeleton();
        OrthogonalPolygon original = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(6, 0),
                new OrthogonalPoint2i(6, 2),
                new OrthogonalPoint2i(4, 2),
                new OrthogonalPoint2i(4, 4),
                new OrthogonalPoint2i(0, 4),
                new OrthogonalPoint2i(0, 0)
        ));
        ClaimEditorSession session = ClaimEditorSession.idle(UUID.randomUUID())
                .withMode(ClaimEditorMode.SHAPED, ClaimEditSource.TOOL)
                .withTarget(new ClaimEditTarget(ClaimEditTargetType.EXISTING_PARENT_CLAIM, 42L))
                .withPreview(new ClaimEditPreview(original, null, List.of(), null, List.of(), List.of(), List.of()))
                .withOpenPath(new ShapedPathDraft(
                        42L,
                        List.of(
                                new OrthogonalPoint2i(4, 2),
                                new OrthogonalPoint2i(6, 2),
                                new OrthogonalPoint2i(6, 4)
                        ),
                        null,
                        false
                ));

        ClaimEditResult result = editor.apply(
                session,
                new ClaimEditIntent(
                        ClaimEditIntentType.ADD_CORNER,
                        ClaimEditSource.TOOL,
                        null,
                        42L,
                        new OrthogonalPoint2i(4, 4),
                        null,
                        true,
                        List.of()
                )
        );

        assertTrue(result.success());
        assertNotNull(result.preview().polygon());
        assertTrue(polygonContains(result.preview().polygon(), new OrthogonalPoint2i(1, 1)));
        assertTrue(polygonContains(result.preview().polygon(), new OrthogonalPoint2i(5, 3)));
        assertEquals(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(6, 0),
                new OrthogonalPoint2i(6, 4),
                new OrthogonalPoint2i(0, 4)
        ), result.preview().polygon().corners());
    }

    @Test
    void unionOfMapCornerNibAndSouthCellIsTraceable()
    {
        ClaimEditorSkeleton editor = new ClaimEditorSkeleton();
        OrthogonalPolygon original = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(14, 0),
                new OrthogonalPoint2i(14, 9),
                new OrthogonalPoint2i(9, 9),
                new OrthogonalPoint2i(9, 4),
                new OrthogonalPoint2i(0, 4),
                new OrthogonalPoint2i(0, 0)
        ));
        OrthogonalPolygon patch = OrthogonalPolygon.fromRectangle(10, 10, 14, 14);

        OrthogonalPolygon merged = assertDoesNotThrow(() -> invokeUnion(editor, original, patch));
        assertTrue(polygonContains(merged, new OrthogonalPoint2i(2, 2)));
        assertTrue(polygonContains(merged, new OrthogonalPoint2i(12, 2)));
        assertTrue(polygonContains(merged, new OrthogonalPoint2i(12, 12)));
    }

    @Test
    void unionFailsWhenMapCellOnlyTouchesOnDiagonal()
    {
        ClaimEditorSkeleton editor = new ClaimEditorSkeleton();
        OrthogonalPolygon original = OrthogonalPolygon.fromClosedPath(List.of(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(14, 0),
                new OrthogonalPoint2i(14, 9),
                new OrthogonalPoint2i(9, 9),
                new OrthogonalPoint2i(9, 4),
                new OrthogonalPoint2i(0, 4),
                new OrthogonalPoint2i(0, 0)
        ));
        OrthogonalPolygon diagonalOnlyPatch = OrthogonalPolygon.fromRectangle(10, 11, 14, 15);

        assertThrows(IllegalArgumentException.class, () -> invokeUnion(editor, original, diagonalOnlyPatch));
    }

    private OrthogonalPolygon invokeUnion(
            ClaimEditorSkeleton editor,
            OrthogonalPolygon original,
            OrthogonalPolygon patch
    ) throws Exception
    {
        Method unionMethod = ClaimEditorSkeleton.class.getDeclaredMethod(
                "unionPolygons",
                OrthogonalPolygon.class,
                OrthogonalPolygon.class
        );
        unionMethod.setAccessible(true);
        try
        {
            return (OrthogonalPolygon) unionMethod.invoke(editor, original, patch);
        }
        catch (InvocationTargetException exception)
        {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception wrapped)
            {
                throw wrapped;
            }
            throw exception;
        }
    }

    private boolean polygonContains(OrthogonalPolygon polygon, OrthogonalPoint2i point)
    {
        if (polygon.corners().contains(point) || !polygon.edgeIndexesContainingInteriorPoint(point).isEmpty())
        {
            return true;
        }

        double sampleX = point.x() + 0.5D;
        double sampleZ = point.z() + 0.5D;
        boolean inside = false;
        List<OrthogonalPoint2i> corners = polygon.corners();
        for (int i = 0, j = corners.size() - 1; i < corners.size(); j = i++)
        {
            OrthogonalPoint2i a = corners.get(i);
            OrthogonalPoint2i b = corners.get(j);
            boolean crosses = (a.z() > sampleZ) != (b.z() > sampleZ);
            if (!crosses)
            {
                continue;
            }

            double intersectionX = (double) (b.x() - a.x()) * (sampleZ - a.z()) / (double) (b.z() - a.z()) + a.x();
            if (sampleX < intersectionX)
            {
                inside = !inside;
            }
        }

        return inside;
    }
}
