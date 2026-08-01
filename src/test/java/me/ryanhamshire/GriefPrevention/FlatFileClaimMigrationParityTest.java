package me.ryanhamshire.GriefPrevention;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import com.griefprevention.geometry.OrthogonalPoint2i;
import com.griefprevention.geometry.OrthogonalPolygon;
import com.griefprevention.persistence.ClaimDocument;
import com.griefprevention.persistence.ClaimDocumentCodec;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlatFileClaimMigrationParityTest
{
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BUILDER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final long ROOT_MODIFIED = 1779681984295L;

    private final ClaimDocumentCodec codec = new ClaimDocumentCodec();

    @Test
    void fabricEditSurvivesARealPaperLoadAndSaveWithoutSemanticLoss() throws Exception
    {
        List<ClaimDocument> paperFixture = this.codec.decodeTree(fixtureYaml(), 28L, 1L);
        Map<Long, ClaimDocument> editedByFabric = byId(paperFixture);

        ClaimDocument originalRoot = editedByFabric.get(28L);
        List<OrthogonalPoint2i> resizedShape = Arrays.asList(
                new OrthogonalPoint2i(-2, -2),
                new OrthogonalPoint2i(12, -2),
                new OrthogonalPoint2i(12, 4),
                new OrthogonalPoint2i(4, 4),
                new OrthogonalPoint2i(4, 12),
                new OrthogonalPoint2i(-2, 12)
        );
        ClaimSnapshot resizedSnapshot = new ClaimSnapshot(
                28L,
                "world",
                OWNER,
                null,
                ClaimBounds.shaped(
                        OrthogonalPolygon.fromClosedPath(closed(resizedShape)),
                        -64,
                        320
                ),
                false,
                false
        );
        editedByFabric.put(28L, originalRoot.withSnapshot(resizedSnapshot, ROOT_MODIFIED + 1000L));

        ClaimDocument originalChild = editedByFabric.get(29L);
        Map<String, ClaimTrustLevel> changedPermissions = new LinkedHashMap<>();
        changedPermissions.put(BUILDER.toString(), ClaimTrustLevel.CONTAINER);
        changedPermissions.put("public", ClaimTrustLevel.ACCESS);
        ClaimTrustSnapshot changedChildTrust = new ClaimTrustSnapshot(
                null,
                changedPermissions,
                Collections.singletonList("[gp3d.staff]"),
                Collections.<String>emptyList()
        );
        editedByFabric.put(29L, originalChild.withTrust(changedChildTrust));

        List<ClaimDocument> expected = new ArrayList<>(editedByFabric.values());
        String fabricYaml = this.codec.encodeTree(editedByFabric.get(28L), expected);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);

        FlatFileDataStore paper = new FlatFileDataStore(false);
        try
        {
            ArrayList<Long> parentId = new ArrayList<>();
            Claim paperRoot = paper.loadClaim(
                    fabricYaml,
                    parentId,
                    999L,
                    28L,
                    Collections.singletonList(world)
            );

            String paperYaml = paper.getYamlForClaim(paperRoot);
            List<ClaimDocument> afterPaper = this.codec.decodeTree(paperYaml, 28L, 999L);

            assertEquals(-1L, parentId.get(0).longValue());
            assertEquals(expected, afterPaper);
        }
        finally
        {
            paper.close();
        }
    }

    private static Map<Long, ClaimDocument> byId(List<ClaimDocument> documents)
    {
        Map<Long, ClaimDocument> result = new LinkedHashMap<>();
        for (ClaimDocument document : documents)
        {
            result.put(document.snapshot().id(), document);
        }
        return result;
    }

    private static List<OrthogonalPoint2i> closed(List<OrthogonalPoint2i> corners)
    {
        ArrayList<OrthogonalPoint2i> result = new ArrayList<>(corners);
        result.add(corners.get(0));
        return result;
    }

    private static String fixtureYaml()
    {
        return String.join("\n", Arrays.asList(
                "Claim ID: '28'",
                "Lesser Boundary Corner: world;0;-64;0",
                "Greater Boundary Corner: world;10;320;10",
                "Owner: 11111111-2222-3333-4444-555555555555",
                "Builders:",
                "- aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "Containers: []",
                "Accessors:",
                "- public",
                "Managers:",
                "- '[gp3d.staff]'",
                "Parent Claim ID: -1",
                "inheritNothing: false",
                "inheritNothingForNewSubdivisions: true",
                "Is3D: false",
                "Shape Corners:",
                "- 0,0",
                "- 10,0",
                "- 10,4",
                "- 4,4",
                "- 4,10",
                "- 0,10",
                "Explosives Allowed: true",
                "Wither Explosions Allowed: true",
                "PvP Enabled: false",
                "Alerts Enabled: false",
                "Modified Date: 1779681984295",
                "Addon Metadata:",
                "  flag: keep",
                "  values: [one, two]",
                "Binary Blob: !!binary |",
                "  AQIDBA==",
                "Children:",
                "  '29':",
                "    Claim ID: '29'",
                "    Lesser Boundary Corner: world;1;70;1",
                "    Greater Boundary Corner: world;7;90;7",
                "    Owner: ''",
                "    Builders: []",
                "    Containers: []",
                "    Accessors: []",
                "    Managers: []",
                "    Parent Claim ID: 28",
                "    inheritNothing: true",
                "    inheritNothingForNewSubdivisions: true",
                "    Is3D: true",
                "    Explosives Allowed: false",
                "    Wither Explosions Allowed: false",
                "    PvP Enabled: true",
                "    Alerts Enabled: true",
                "    Modified Date: 1779679843818",
                "    Child Addon Field: preserve-child",
                "    Children:",
                "      '30':",
                "        Claim ID: '30'",
                "        Lesser Boundary Corner: world;2;72;2",
                "        Greater Boundary Corner: world;4;78;4",
                "        Owner: ''",
                "        Builders: []",
                "        Containers: []",
                "        Accessors: []",
                "        Managers: []",
                "        Parent Claim ID: 29",
                "        inheritNothing: true",
                "        inheritNothingForNewSubdivisions: false",
                "        Is3D: true",
                "        Explosives Allowed: false",
                "        Wither Explosions Allowed: true",
                "        PvP Enabled: false",
                "        Alerts Enabled: false",
                "        Modified Date: 1779670000000",
                "        Grandchild Addon Field: preserve-grandchild"
        )) + "\n";
    }
}
