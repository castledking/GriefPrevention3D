package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimTrustLevelTest
{
    @Test
    void editGrantsAllLowerLevels()
    {
        assertTrue(ClaimTrustLevel.MANAGE.isGrantedBy(ClaimTrustLevel.EDIT));
        assertTrue(ClaimTrustLevel.BUILD.isGrantedBy(ClaimTrustLevel.EDIT));
        assertTrue(ClaimTrustLevel.CONTAINER.isGrantedBy(ClaimTrustLevel.EDIT));
        assertTrue(ClaimTrustLevel.ACCESS.isGrantedBy(ClaimTrustLevel.EDIT));
        assertTrue(ClaimTrustLevel.NEIGHBOR.isGrantedBy(ClaimTrustLevel.EDIT));
    }

    @Test
    void manageGrantsBuildContainerAccess()
    {
        assertTrue(ClaimTrustLevel.BUILD.isGrantedBy(ClaimTrustLevel.MANAGE));
        assertTrue(ClaimTrustLevel.CONTAINER.isGrantedBy(ClaimTrustLevel.MANAGE));
        assertTrue(ClaimTrustLevel.ACCESS.isGrantedBy(ClaimTrustLevel.MANAGE));
    }

    @Test
    void buildGrantsContainerAndAccess()
    {
        assertTrue(ClaimTrustLevel.CONTAINER.isGrantedBy(ClaimTrustLevel.BUILD));
        assertTrue(ClaimTrustLevel.ACCESS.isGrantedBy(ClaimTrustLevel.BUILD));
    }

    @Test
    void containerGrantsAccess()
    {
        assertTrue(ClaimTrustLevel.ACCESS.isGrantedBy(ClaimTrustLevel.CONTAINER));
    }

    @Test
    void accessDoesNotGrantHigherLevels()
    {
        assertFalse(ClaimTrustLevel.CONTAINER.isGrantedBy(ClaimTrustLevel.ACCESS));
        assertFalse(ClaimTrustLevel.BUILD.isGrantedBy(ClaimTrustLevel.ACCESS));
        assertFalse(ClaimTrustLevel.MANAGE.isGrantedBy(ClaimTrustLevel.ACCESS));
        assertFalse(ClaimTrustLevel.EDIT.isGrantedBy(ClaimTrustLevel.ACCESS));
    }

    @Test
    void accessGrantsNeighbor()
    {
        assertTrue(ClaimTrustLevel.NEIGHBOR.isGrantedBy(ClaimTrustLevel.ACCESS));
    }

    @Test
    void containerDoesNotGrantBuild()
    {
        assertFalse(ClaimTrustLevel.BUILD.isGrantedBy(ClaimTrustLevel.CONTAINER));
    }

    @Test
    void isGrantedBySelfIsTrue()
    {
        for (ClaimTrustLevel level : ClaimTrustLevel.values())
        {
            assertTrue(level.isGrantedBy(level), level + " should grant itself");
        }
    }

    @Test
    void isGrantedByNullIsFalse()
    {
        for (ClaimTrustLevel level : ClaimTrustLevel.values())
        {
            assertFalse(level.isGrantedBy(null), level + " should not be granted by null");
        }
    }

    @Test
    void denySuffixReturnsExpectedValues()
    {
        assertEquals("", ClaimTrustLevel.EDIT.denySuffix());
        assertEquals("#manager", ClaimTrustLevel.MANAGE.denySuffix());
        assertEquals("#build", ClaimTrustLevel.BUILD.denySuffix());
        assertEquals("#inventory", ClaimTrustLevel.CONTAINER.denySuffix());
        assertEquals("#access", ClaimTrustLevel.ACCESS.denySuffix());
        assertEquals("#neighbor", ClaimTrustLevel.NEIGHBOR.denySuffix());
    }

    @Test
    void neighborDoesNotGrantHigherLevels()
    {
        assertFalse(ClaimTrustLevel.ACCESS.isGrantedBy(ClaimTrustLevel.NEIGHBOR));
        assertFalse(ClaimTrustLevel.CONTAINER.isGrantedBy(ClaimTrustLevel.NEIGHBOR));
        assertFalse(ClaimTrustLevel.BUILD.isGrantedBy(ClaimTrustLevel.NEIGHBOR));
        assertFalse(ClaimTrustLevel.MANAGE.isGrantedBy(ClaimTrustLevel.NEIGHBOR));
        assertFalse(ClaimTrustLevel.EDIT.isGrantedBy(ClaimTrustLevel.NEIGHBOR));
    }
}
