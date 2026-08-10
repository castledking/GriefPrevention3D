package me.ryanhamshire.GriefPrevention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import com.griefprevention.geometry.OrthogonalPolygonValidationResult;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

@SuppressWarnings("null")
class ShapedClaimResizeTest {

    @Test
    void concaveNibDragsDoNotAcceptValidButUnchangedCandidates() throws Exception {
        OrthogonalPolygon polygon = reportedSteppedNib();
        Claim claim = claim(polygon);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString("710f96df-8b04-4c91-8828-b2b5afc45cd3"));
        when(player.getLocation()).thenReturn(new Location(null, -111, 70, 211));

        OrthogonalPolygonValidationResult attachmentMove = resolve(
            player, claim, polygon, 4, new OrthogonalPoint2i(-105, 222));

        assertTrue(attachmentMove.isValid());
        assertNotEquals(polygon.corners(), attachmentMove.polygon().corners());
        assertTrue(attachmentMove.polygon().corners().contains(new OrthogonalPoint2i(-105, 222)));
    }

    @Test
    void eitherEndOfInnerNibFaceProducesTheSameCardinalResize() throws Exception {
        OrthogonalPolygon polygon = reportedSteppedNib();
        Claim claim = claim(polygon);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.fromString("710f96df-8b04-4c91-8828-b2b5afc45cd3"));
        when(player.getLocation()).thenReturn(new Location(null, -96, 70, 228));

        OrthogonalPoint2i target = new OrthogonalPoint2i(-104, 220);
        OrthogonalPolygonValidationResult fromTop = resolve(player, claim, polygon, 5,
            new OrthogonalPoint2i(-104, 222));
        OrthogonalPolygonValidationResult fromBottom = resolve(player, claim, polygon, 6, target);

        assertTrue(fromTop.isValid());
        assertTrue(fromBottom.isValid());
        assertEquals(fromTop.polygon().corners(), fromBottom.polygon().corners());
    }

    private static Claim claim(OrthogonalPolygon polygon) {
        Claim claim = new Claim(
            new Location(null, -108, 0, 214),
            new Location(null, -88, 256, 233),
            UUID.fromString("710f96df-8b04-4c91-8828-b2b5afc45cd3"),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            1L
        );
        claim.setShapedCorners(polygon.corners());
        return claim;
    }

    private static OrthogonalPolygonValidationResult resolve(
        Player player,
        Claim claim,
        OrthogonalPolygon polygon,
        int cornerIndex,
        OrthogonalPoint2i target
    ) throws Exception {
        ClaimToolDispatcher dispatcher = mock(ClaimToolDispatcher.class, CALLS_REAL_METHODS);
        Method method = ClaimToolDispatcher.class.getDeclaredMethod(
            "resolveShapedResizeMove",
            Player.class,
            Claim.class,
            OrthogonalPolygon.class,
            int.class,
            OrthogonalPoint2i.class
        );
        method.setAccessible(true);
        return (OrthogonalPolygonValidationResult) method.invoke(
            dispatcher,
            player,
            claim,
            polygon,
            cornerIndex,
            target
        );
    }

    private static OrthogonalPolygon reportedSteppedNib() {
        return OrthogonalPolygon.fromClosedPath(
            Arrays.asList(
                new OrthogonalPoint2i(-102, 214),
                new OrthogonalPoint2i(-88, 214),
                new OrthogonalPoint2i(-88, 233),
                new OrthogonalPoint2i(-102, 233),
                new OrthogonalPoint2i(-102, 222),
                new OrthogonalPoint2i(-106, 222),
                new OrthogonalPoint2i(-106, 220),
                new OrthogonalPoint2i(-108, 220),
                new OrthogonalPoint2i(-108, 216),
                new OrthogonalPoint2i(-102, 216),
                new OrthogonalPoint2i(-102, 214)
            )
        );
    }
}
