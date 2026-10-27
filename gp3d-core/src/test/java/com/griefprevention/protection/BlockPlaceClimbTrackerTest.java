package com.griefprevention.protection;

import com.griefprevention.protection.BlockPlaceClimbTracker.Box;
import com.griefprevention.protection.BlockPlaceClimbTracker.Position;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BlockPlaceClimbTrackerTest
{
    private static final Position GROUND = new Position(0.5, 64, 0.5);
    private static final List<Box> BLOCK = Collections.singletonList(new Box(0, 64, 0, 1, 65, 1));

    @Test
    void climbingOnARejectedBlockReturnsTheLastSupportedPosition()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        assertSame(GROUND, tracker.denyPlacement(new Position(0.5, 65.1, 0.5), BLOCK, 100));
    }

    @Test
    void movementArrivingAfterTheCancellationIsAlsoCorrected()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        assertNull(tracker.denyPlacement(GROUND, BLOCK, 10));
        assertSame(GROUND, tracker.setback(new Position(0.5, 65.2, 0.5), 100));
        assertNull(tracker.setback(new Position(0.5, 64.4, 0.5), 110));
    }

    @Test
    void rapidlyRepeatingPlacementAndJumpCannotRaiseTheCheckpoint()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        for (int attempt = 1; attempt <= 20; attempt++)
        {
            long now = attempt * 20;
            assertSame(GROUND, tracker.denyPlacement(new Position(0.5, 65.1, 0.5), BLOCK, now));
        }
    }

    @Test
    void aSupportedLedgeCannotBecomeTheCheckpointWhileARejectedStepIsPending()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        tracker.denyPlacement(GROUND, BLOCK, 10);
        tracker.recordSupported(new Position(0.5, 66, 0.5), 20);
        assertSame(GROUND, tracker.setback(new Position(0.5, 66, 0.5), 30));
    }

    @Test
    void ordinaryJumpsWithoutARejectedPlacementAreUntouched()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        assertNull(tracker.setback(new Position(0.5, 65.2, 0.5), 100));
    }

    @Test
    void aRejectedBlockAwayFromTheFeetDoesNotArmASetback()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        assertNull(tracker.denyPlacement(GROUND, Collections.singletonList(new Box(2, 64, 0, 3, 65, 1)), 10));
        assertNull(tracker.setback(new Position(0.5, 65.2, 0.5), 100));
    }

    @Test
    void overheadAndNonCollidingPlacementsDoNotArmASetback()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        assertNull(tracker.denyPlacement(GROUND, Collections.singletonList(new Box(0, 66, 0, 1, 67, 1)), 10));
        assertNull(tracker.denyPlacement(GROUND, Collections.emptyList(), 10));
        assertNull(tracker.setback(new Position(0.5, 65.2, 0.5), 100));
    }

    @Test
    void oldOrMissingCheckpointsNeverTeleportThePlayer()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        assertNull(tracker.denyPlacement(new Position(0.5, 65.1, 0.5), BLOCK, 100));
        tracker.recordSupported(GROUND, 0);
        assertNull(tracker.denyPlacement(new Position(0.5, 65.1, 0.5), BLOCK, 5000));
    }

    @Test
    void fallingOrMovingFarAwayDoesNotPullThePlayerBack()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        tracker.denyPlacement(GROUND, BLOCK, 10);
        assertNull(tracker.setback(new Position(0.5, 63, 0.5), 100));
        tracker.denyPlacement(GROUND, BLOCK, 110);
        assertNull(tracker.setback(new Position(20, 65, 0.5), 120));
    }

    @Test
    void theCorrectionWindowExpiresAndResetClearsIt()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        tracker.denyPlacement(GROUND, BLOCK, 10);
        assertNull(tracker.setback(new Position(0.5, 65.2, 0.5), 5000));
        tracker.recordSupported(GROUND, 5000);
        tracker.denyPlacement(GROUND, BLOCK, 5010);
        tracker.clear();
        assertNull(tracker.setback(new Position(0.5, 65.2, 0.5), 5020));
    }

    @Test
    void repeatedRejectedLaddersCannotRaiseTheSupportedCheckpoint()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        List<BlockPoint> ladder = Collections.singletonList(new BlockPoint(0, 64, 0));
        for (int attempt = 1; attempt <= 20; attempt++)
        {
            long now = attempt * 20;
            assertNull(tracker.denyClimbablePlacement(GROUND, ladder, now));
            Position ledge = new Position(0.5, 65, 0.5);
            tracker.recordSupported(ledge, now + 1);
            assertSame(GROUND, tracker.setback(ledge, now + 2));
        }
    }

    @Test
    void pendingClimbablesDoNotAffectJumpsThatNeverEnterTheirBlock()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        tracker.denyClimbablePlacement(GROUND, Collections.singletonList(new BlockPoint(1, 64, 0)), 10);
        assertNull(tracker.setback(new Position(0.5, 64.4, 0.5), 20));
        assertSame(GROUND, tracker.setback(new Position(1.1, 64.6, 0.5), 30));
    }

    @Test
    void aLadderContactSurvivesALaterRejectedPlacementInAnotherBlock()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        tracker.recordSupported(GROUND, 0);
        tracker.denyClimbablePlacement(GROUND, Collections.singletonList(new BlockPoint(0, 64, 0)), 10);
        tracker.denyClimbablePlacement(GROUND, Collections.singletonList(new BlockPoint(1, 64, 0)), 20);
        assertSame(GROUND, tracker.setback(new Position(1.2, 64.4, 0.5), 30));
    }

    @Test
    void ladderContactUsesFloorCoordinatesIncludingNegativeBlocks()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        Position ground = new Position(-0.5, 64, -0.5);
        tracker.recordSupported(ground, 0);
        tracker.denyClimbablePlacement(ground, Collections.singletonList(new BlockPoint(-1, 64, -1)), 10);
        assertSame(ground, tracker.setback(new Position(-0.5, 64.2, -0.5), 20));
    }

    @Test
    void expiredOrMissingSupportedPositionsDoNotArmClimbableCorrections()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        List<BlockPoint> ladder = Collections.singletonList(new BlockPoint(0, 64, 0));
        tracker.denyClimbablePlacement(GROUND, ladder, 0);
        assertNull(tracker.setback(new Position(0.5, 64.2, 0.5), 10));
        tracker.recordSupported(GROUND, 0);
        tracker.denyClimbablePlacement(GROUND, ladder, 5000);
        assertNull(tracker.setback(new Position(0.5, 64.2, 0.5), 5010));
    }

    @Test
    void expiredClimbableAttemptsAndFallingDoNotPullPlayersBack()
    {
        BlockPlaceClimbTracker tracker = new BlockPlaceClimbTracker();
        List<BlockPoint> ladder = Collections.singletonList(new BlockPoint(0, 64, 0));
        tracker.recordSupported(GROUND, 0);
        tracker.denyClimbablePlacement(GROUND, ladder, 10);
        assertNull(tracker.setback(new Position(0.5, 64.2, 0.5), 1000));
        tracker.recordSupported(GROUND, 1000);
        tracker.denyClimbablePlacement(GROUND, ladder, 1010);
        assertNull(tracker.setback(new Position(0.5, 63.5, 0.5), 1020));
        assertNull(tracker.setback(new Position(0.5, 64.2, 0.5), 1030));
    }
}
