package com.griefprevention.protection;

import com.griefprevention.messages.MessageKey;
import com.griefprevention.protection.BoundaryViolationAlerts.Burst;
import com.griefprevention.protection.BoundaryViolationAlerts.Direction;
import com.griefprevention.protection.BoundaryViolationAlerts.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundaryViolationAlertsTest
{
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID NEIGHBOR = UUID.fromString("66666666-7777-8888-9999-000000000000");

    private final BoundaryViolationAlerts alerts = new BoundaryViolationAlerts();

    @Test
    void aSingleViolationIsReportedWhereItHappenedOnceTheWindowCloses()
    {
        this.alerts.record(OWNER, "world", 10, 64, -5, Kind.PISTON, Direction.EXTERNAL,
                BoundaryViolationAlerts.PISTON_RADIUS, 0L);

        assertTrue(this.alerts.due(1999L).isEmpty());
        List<Burst> due = this.alerts.due(2000L);
        assertEquals(1, due.size());
        assertEquals(MessageKey.EXTERNAL_PISTON_BOUNDARY_VIOLATION, due.get(0).message());
        assertArrayEquals(new String[] {"10", "64", "-5"}, due.get(0).arguments());
        assertTrue(this.alerts.due(5000L).isEmpty());
    }

    @Test
    void repeatsNearAReportedViolationStayQuietUntilItExpires()
    {
        int radius = BoundaryViolationAlerts.liquidRadius(false, false);
        this.alerts.record(OWNER, "world", 0, 64, 0, Kind.LIQUID, Direction.EXTERNAL, radius, 0L);
        this.alerts.due(2000L);

        this.alerts.record(OWNER, "world", 8, 64, 8, Kind.LIQUID, Direction.EXTERNAL, radius, 10_000L);
        assertTrue(this.alerts.due(20_000L).isEmpty());

        this.alerts.record(OWNER, "world", 8, 64, 8, Kind.LIQUID, Direction.EXTERNAL, radius,
                BoundaryViolationAlerts.EXPIRY_MILLIS + 1L);
        assertEquals(1, this.alerts.due(BoundaryViolationAlerts.EXPIRY_MILLIS + 2001L).size());
    }

    @Test
    void aBurstIsSummarizedAsOneMessage()
    {
        for (int x = 0; x < 3; x++)
        {
            this.alerts.record(OWNER, "world", x * 20, 70, 0, Kind.LIQUID, Direction.EXTERNAL,
                    BoundaryViolationAlerts.WATER_RADIUS, x * 100L);
        }

        List<Burst> due = this.alerts.due(2000L);
        assertEquals(1, due.size());
        assertEquals(MessageKey.BOUNDARY_VIOLATION_BURST_SUMMARY, due.get(0).message());
        assertArrayEquals(new String[] {"3", "liquid", "0", "70", "0"}, due.get(0).arguments());
    }

    @Test
    void ownersKindsAndDirectionsAreReportedSeparately()
    {
        this.alerts.record(OWNER, "world", 0, 64, 0, Kind.PISTON, Direction.EXTERNAL, 0, 0L);
        this.alerts.record(OWNER, "world", 0, 64, 0, Kind.PISTON, Direction.INTERNAL, 0, 0L);
        this.alerts.record(NEIGHBOR, "world", 0, 64, 0, Kind.PISTON, Direction.EXTERNAL, 0, 0L);

        List<Burst> due = this.alerts.due(2000L);
        assertEquals(3, due.size());
        assertEquals(MessageKey.INTERNAL_PISTON_BOUNDARY_VIOLATION, due.get(1).message());
    }

    @Test
    void netherLavaIsGivenMoreRoom()
    {
        assertEquals(BoundaryViolationAlerts.LAVA_RADIUS, BoundaryViolationAlerts.liquidRadius(true, false));
        assertEquals(BoundaryViolationAlerts.NETHER_LAVA_RADIUS, BoundaryViolationAlerts.liquidRadius(true, true));
    }

    @Test
    void forgettingAnOwnerDropsTheirPendingWarnings()
    {
        this.alerts.record(OWNER, "world", 0, 64, 0, Kind.PISTON, Direction.EXTERNAL, 0, 0L);
        this.alerts.forget(OWNER);

        assertTrue(this.alerts.due(2000L).isEmpty());
    }
}
