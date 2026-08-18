package com.griefprevention.persistence;

import com.griefprevention.claims.ClaimBounds;
import com.griefprevention.claims.ClaimSnapshot;
import com.griefprevention.claims.ClaimTrustLevel;
import com.griefprevention.claims.ClaimTrustSnapshot;
import com.griefprevention.geometry.OrthogonalPoint2i;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimDocumentCodecTest
{
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BUILDER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID OVERLAPPING_TRUST = UUID.fromString("12345678-1234-5678-9abc-123456789abc");
    private static final String PERMISSION_TRUST = "[server.builders]";

    private final ClaimDocumentCodec codec = new ClaimDocumentCodec();

    @Test
    void roundTripsCompleteNestedClaimSemanticsAndUnknownFields() throws Exception
    {
        List<ClaimDocument> decoded = this.codec.decodeTree(completeClaimYaml(), 28L, 100L);

        assertEquals(3, decoded.size());
        Map<Long, ClaimDocument> byId = byId(decoded);

        ClaimDocument root = byId.get(28L);
        assertEquals("world", root.snapshot().worldKey());
        assertEquals(OWNER, root.snapshot().ownerId());
        assertEquals(null, root.snapshot().parentId());
        assertFalse(root.snapshot().threeDimensional());
        assertFalse(root.snapshot().subdivision());
        assertTrue(root.snapshot().bounds().isShaped());
        assertTrue(root.snapshot().bounds().containsColumn(2, 8));
        assertFalse(root.snapshot().bounds().containsColumn(8, 8));
        assertEquals(Arrays.asList(
                new OrthogonalPoint2i(0, 0),
                new OrthogonalPoint2i(10, 0),
                new OrthogonalPoint2i(10, 4),
                new OrthogonalPoint2i(4, 4),
                new OrthogonalPoint2i(4, 10),
                new OrthogonalPoint2i(0, 10)
        ), root.shapeCorners());
        assertFalse(root.inheritNothing());
        assertTrue(root.inheritNothingForNewSubdivisions());
        assertTrue(root.explosivesAllowed());
        assertTrue(root.witherExplosionsAllowed());
        assertFalse(root.pvpEnabled());
        assertFalse(root.alertsEnabled());
        assertEquals(1779681984295L, root.modifiedDate());
        assertEquals("28", root.storageKey());

        assertEquals(ClaimTrustLevel.BUILD,
                root.trust().permissionsByIdentifier().get(BUILDER.toString()));
        assertEquals(ClaimTrustLevel.ACCESS,
                root.trust().permissionsByIdentifier().get(OVERLAPPING_TRUST.toString()));
        assertTrue(root.trust().hasPublicPermission(ClaimTrustLevel.ACCESS));
        assertTrue(root.trust().hasExplicitIdentifierPermission("[gp3d.staff]", ClaimTrustLevel.MANAGE));

        @SuppressWarnings("unchecked")
        Map<String, Object> addonMetadata = (Map<String, Object>) root.extraFields().get("Addon Metadata");
        assertEquals("keep", addonMetadata.get("flag"));
        assertEquals(Arrays.asList("one", "two"), addonMetadata.get("values"));
        assertArrayEquals(new byte[] {1, 2, 3, 4}, (byte[]) root.extraFields().get("Binary Blob"));

        ClaimDocument child = byId.get(29L);
        assertEquals(Long.valueOf(28L), child.snapshot().parentId());
        assertTrue(child.snapshot().threeDimensional());
        assertTrue(child.snapshot().subdivision());
        assertEquals(ClaimBounds.rectangle(1, 70, 1, 3, 80, 3), child.snapshot().bounds());
        assertTrue(child.inheritNothing());
        assertEquals(1779679843818L, child.modifiedDate());

        ClaimDocument grandchild = byId.get(30L);
        assertEquals(Long.valueOf(29L), grandchild.snapshot().parentId());
        assertTrue(grandchild.snapshot().subdivision());
        assertFalse(grandchild.snapshot().threeDimensional());
        assertEquals(child.modifiedDate(), grandchild.modifiedDate());

        String encoded = this.codec.encodeTree(root, decoded);
        List<ClaimDocument> reloaded = this.codec.decodeTree(encoded, 28L, 999L);

        assertEquals(decoded, reloaded);
        assertTrue(encoded.contains("Addon Metadata:"));
        assertTrue(encoded.contains("Children:"));
    }

    @Test
    void rejectsConflictingNestedParentIds()
    {
        String yaml = minimalClaim(1L)
                + "Children:\n"
                + "  '2':\n"
                + indent(minimalClaim(2L).replace("Parent Claim ID: -1", "Parent Claim ID: 99"), 4);

        assertThrows(ClaimDocumentFormatException.class,
                () -> this.codec.decodeTree(yaml, 1L, 100L));
    }

    @Test
    void refusesToGuessMissingChildIdFromItsStorageKey()
    {
        String childWithoutId = minimalClaim(2L).replace("Claim ID: '2'\n", "");
        String yaml = minimalClaim(1L)
                + "Children:\n"
                + "  '2':\n"
                + indent(childWithoutId, 4);

        assertThrows(ClaimDocumentFormatException.class,
                () -> this.codec.decodeTree(yaml, 1L, 100L));
    }

    @Test
    void rejectsDuplicateFieldsAndUnsafeYamlTags()
    {
        String duplicateOwner = minimalClaim(1L) + "Owner: ''\n";
        String unsafeTag = "!!com.example.Arbitrary {value: nope}\n";

        assertThrows(ClaimDocumentFormatException.class,
                () -> this.codec.decodeTree(duplicateOwner, 1L, 100L));
        assertThrows(ClaimDocumentFormatException.class,
                () -> this.codec.decodeTree(unsafeTag, 1L, 100L));
    }

    @Test
    void rejectsFractionalAndMismatchedClaimIds()
    {
        String fractional = minimalClaim(null).replace("Claim ID: ''", "Claim ID: 1.5");

        assertThrows(ClaimDocumentFormatException.class,
                () -> this.codec.decodeTree(fractional, null, 100L));
        assertThrows(ClaimDocumentFormatException.class,
                () -> this.codec.decodeTree(minimalClaim(2L), 1L, 100L));
    }

    @Test
    void refusesToEncodeDocumentsOutsideTheRequestedRoot() throws Exception
    {
        ClaimDocument root = ClaimDocument.create(snapshot(1L, null), 100L);
        ClaimDocument unrelatedRoot = ClaimDocument.create(snapshot(2L, null), 100L);

        assertThrows(ClaimDocumentFormatException.class,
                () -> this.codec.encodeTree(root, Arrays.asList(root, unrelatedRoot)));
    }

    @Test
    void manageTrustDoesNotStripInteractionTrust() throws Exception
    {
        // A player granted /trust and then /managetrust is written to both lists.
        String yaml = "Claim ID: '28'\n"
                + "Lesser Boundary Corner: world;0;-64;0\n"
                + "Greater Boundary Corner: world;10;320;10\n"
                + "Owner: " + OWNER + "\n"
                + "Builders:\n"
                + "- " + BUILDER + "\n"
                + "Containers: []\n"
                + "Accessors: []\n"
                + "Managers:\n"
                + "- " + BUILDER + "\n"
                + "Parent Claim ID: -1\n";

        List<ClaimDocument> decoded = this.codec.decodeTree(yaml, 28L, 100L);
        ClaimDocument root = decoded.get(0);

        assertEquals(ClaimTrustLevel.BUILD,
                root.trust().permissionsByIdentifier().get(BUILDER.toString()));
        assertTrue(root.trust().managerIdentifiers().contains(BUILDER.toString()));
        assertTrue(root.trust().hasExplicitIdentifierPermission(BUILDER.toString(), ClaimTrustLevel.CONTAINER));
        assertTrue(root.trust().hasExplicitIdentifierPermission(BUILDER.toString(), ClaimTrustLevel.MANAGE));

        String encoded = this.codec.encodeTree(root, decoded);
        ClaimDocument reloaded = this.codec.decodeTree(encoded, 28L, 999L).get(0);

        assertEquals(root.trust(), reloaded.trust());
    }

    @Test
    void aRevokedInheritedGrantSurvivesTheRoundTrip() throws Exception
    {
        List<ClaimDocument> decoded = this.codec.decodeTree(revokedInheritedTrustYaml(), 28L, 100L);
        Map<Long, ClaimDocument> byId = byId(decoded);

        ClaimDocument parent = byId.get(28L);
        ClaimDocument child = byId.get(29L);

        assertEquals(ClaimTrustLevel.BUILD,
                parent.trust().permissionsByIdentifier().get(PERMISSION_TRUST));
        assertTrue(parent.trust().deniedIdentifiers().isEmpty());
        assertTrue(child.trust().isPermissionDenied(PERMISSION_TRUST, ClaimTrustLevel.BUILD));

        String encoded = this.codec.encodeTree(parent, decoded);
        assertTrue(encoded.contains("Denied:"));

        Map<Long, ClaimDocument> reloaded = byId(this.codec.decodeTree(encoded, 28L, 999L));

        // The parent keeps handing out build trust; the child keeps refusing to inherit it.
        assertEquals(ClaimTrustLevel.BUILD,
                reloaded.get(28L).trust().permissionsByIdentifier().get(PERMISSION_TRUST));
        assertTrue(reloaded.get(28L).trust().deniedIdentifiers().isEmpty());
        assertTrue(reloaded.get(29L).trust().isPermissionDenied(PERMISSION_TRUST, ClaimTrustLevel.BUILD));
        assertEquals(byId, reloaded);
    }

    @Test
    void omitsTheDeniedListWhenNothingIsRevoked() throws Exception
    {
        List<ClaimDocument> decoded = this.codec.decodeTree(minimalClaim(1L), 1L, 100L);

        assertFalse(this.codec.encodeTree(decoded.get(0), decoded).contains("Denied:"));
    }

    @Test
    void keepsADenyEntryThatCarriesNoLevelSuffix() throws Exception
    {
        // Denying an identifier outright blocks every level, including public trust it would
        // otherwise ride in on. Storage has to distinguish that from a per-level revocation.
        String yaml = minimalClaim(1L)
                .replace("Accessors: []", "Accessors:\n- public")
                + "Denied:\n"
                + "- '" + PERMISSION_TRUST + "'\n";

        List<ClaimDocument> decoded = this.codec.decodeTree(yaml, 1L, 100L);
        ClaimDocument reloaded = this.codec
                .decodeTree(this.codec.encodeTree(decoded.get(0), decoded), 1L, 999L)
                .get(0);

        assertTrue(reloaded.trust().isPermissionDenied(PERMISSION_TRUST));
        assertTrue(reloaded.trust().isPermissionDenied(PERMISSION_TRUST, ClaimTrustLevel.ACCESS));
        assertEquals(decoded.get(0).trust(), reloaded.trust());
    }

    @Test
    void rejectsAMalformedDeniedField()
    {
        String scalar = minimalClaim(1L) + "Denied: '" + PERMISSION_TRUST + "#build'\n";
        String nonString = minimalClaim(1L) + "Denied:\n- 42\n";

        assertThrows(ClaimDocumentFormatException.class,
                () -> this.codec.decodeTree(scalar, 1L, 100L));
        assertThrows(ClaimDocumentFormatException.class,
                () -> this.codec.decodeTree(nonString, 1L, 100L));
    }

    @Test
    void roundTripsManualNeighborTrustAndAllowAllNeighbors() throws Exception
    {
        String yaml = minimalClaim(1L)
                .replace("Accessors: []", "Accessors:\n- " + BUILDER)
                + "Neighbors:\n"
                + "- " + BUILDER + "\n"
                + "- '" + PERMISSION_TRUST + "'\n"
                + "allowAllNeighbors: true\n";

        ClaimDocument decoded = this.codec.decodeTree(yaml, 1L, 100L).get(0);
        ClaimTrustSnapshot trust = decoded.trust();

        assertEquals(ClaimTrustLevel.ACCESS, trust.permissionsByIdentifier().get(BUILDER.toString()));
        assertTrue(trust.neighborIdentifiers().contains(BUILDER.toString()));
        assertTrue(trust.neighborIdentifiers().contains(PERMISSION_TRUST));
        assertTrue(trust.hasExplicitIdentifierPermission(BUILDER.toString(), ClaimTrustLevel.NEIGHBOR));
        assertTrue(decoded.allowAllNeighbors());

        String encoded = this.codec.encodeTree(decoded, Collections.singletonList(decoded));
        assertTrue(encoded.contains("Neighbors:"));
        assertTrue(encoded.contains("allowAllNeighbors: true"));

        ClaimDocument reloaded = this.codec.decodeTree(encoded, 1L, 999L).get(0);

        assertEquals(decoded, reloaded);
    }

    private static String revokedInheritedTrustYaml()
    {
        return "Claim ID: '28'\n"
                + "Lesser Boundary Corner: world;0;-64;0\n"
                + "Greater Boundary Corner: world;10;320;10\n"
                + "Owner: " + OWNER + "\n"
                + "Builders:\n"
                + "- '" + PERMISSION_TRUST + "'\n"
                + "Containers: []\n"
                + "Accessors: []\n"
                + "Managers: []\n"
                + "Parent Claim ID: -1\n"
                + "inheritNothing: false\n"
                + "Is3D: false\n"
                + "Modified Date: 1779681984295\n"
                + "Children:\n"
                + "  '29':\n"
                + "    Claim ID: '29'\n"
                + "    Lesser Boundary Corner: world;1;-64;1\n"
                + "    Greater Boundary Corner: world;3;320;3\n"
                + "    Owner: ''\n"
                + "    Builders: []\n"
                + "    Containers: []\n"
                + "    Accessors: []\n"
                + "    Managers: []\n"
                + "    Denied:\n"
                + "    - '" + PERMISSION_TRUST + "#build'\n"
                + "    Parent Claim ID: 28\n"
                + "    inheritNothing: false\n"
                + "    Is3D: false\n"
                + "    Modified Date: 1779681984295\n";
    }

    private static String completeClaimYaml()
    {
        return "Claim ID: '28'\n"
                + "Lesser Boundary Corner: world;0;-64;0\n"
                + "Greater Boundary Corner: world;10;320;10\n"
                + "Owner: " + OWNER + "\n"
                + "Builders:\n"
                + "- " + BUILDER + "\n"
                + "- " + OVERLAPPING_TRUST + "\n"
                + "Containers: []\n"
                + "Accessors:\n"
                + "- " + OVERLAPPING_TRUST + "\n"
                + "- public\n"
                + "Managers:\n"
                + "- '[gp3d.staff]'\n"
                + "Parent Claim ID: -1\n"
                + "inheritNothing: false\n"
                + "inheritNothingForNewSubdivisions: true\n"
                + "Is3D: false\n"
                + "Shape Corners:\n"
                + "- 0,0\n"
                + "- 10,0\n"
                + "- 10,4\n"
                + "- 4,4\n"
                + "- 4,10\n"
                + "- 0,10\n"
                + "Explosives Allowed: true\n"
                + "Wither Explosions Allowed: true\n"
                + "PvP Enabled: false\n"
                + "Alerts Enabled: false\n"
                + "Modified Date: 1779681984295\n"
                + "Addon Metadata:\n"
                + "  flag: keep\n"
                + "  values: [one, two]\n"
                + "Binary Blob: !!binary |\n"
                + "  AQIDBA==\n"
                + "Children:\n"
                + "  '29':\n"
                + "    Claim ID: '29'\n"
                + "    Lesser Boundary Corner: world;1;70;1\n"
                + "    Greater Boundary Corner: world;3;80;3\n"
                + "    Owner: ''\n"
                + "    Builders: []\n"
                + "    Containers: []\n"
                + "    Accessors: []\n"
                + "    Managers: []\n"
                + "    Parent Claim ID: 28\n"
                + "    inheritNothing: true\n"
                + "    inheritNothingForNewSubdivisions: false\n"
                + "    Is3D: true\n"
                + "    Explosives Allowed: false\n"
                + "    Wither Explosions Allowed: false\n"
                + "    PvP Enabled: true\n"
                + "    Alerts Enabled: true\n"
                + "    Modified Date: 1779679843818\n"
                + "    Children:\n"
                + "      '30':\n"
                + "        Claim ID: '30'\n"
                + "        Lesser Boundary Corner: world;1;71;1\n"
                + "        Greater Boundary Corner: world;2;72;2\n"
                + "        Owner: ''\n"
                + "        Builders: []\n"
                + "        Containers: []\n"
                + "        Accessors: []\n"
                + "        Managers: []\n"
                + "        Parent Claim ID: 29\n"
                + "        inheritNothing: false\n"
                + "        Is3D: false\n";
    }

    private static String minimalClaim(Long id)
    {
        String encodedId = id == null ? "" : String.valueOf(id);
        return "Claim ID: '" + encodedId + "'\n"
                + "Lesser Boundary Corner: world;0;0;0\n"
                + "Greater Boundary Corner: world;1;1;1\n"
                + "Owner: ''\n"
                + "Builders: []\n"
                + "Containers: []\n"
                + "Accessors: []\n"
                + "Managers: []\n"
                + "Parent Claim ID: -1\n"
                + "Is3D: false\n";
    }

    private static String indent(String value, int spaces)
    {
        String prefix = String.join("", Collections.nCopies(spaces, " "));
        return prefix + value.replace("\n", "\n" + prefix);
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

    private static ClaimSnapshot snapshot(long id, Long parentId)
    {
        return new ClaimSnapshot(
                id,
                "world",
                OWNER,
                parentId,
                ClaimBounds.rectangle(0, 0, 0, 1, 1, 1),
                false,
                parentId != null
        );
    }
}
