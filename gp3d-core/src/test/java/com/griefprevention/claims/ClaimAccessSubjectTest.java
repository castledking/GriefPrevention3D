package com.griefprevention.claims;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimAccessSubjectTest
{
    @Test
    void createWithPlayerIdOnly()
    {
        UUID id = UUID.randomUUID();
        ClaimAccessSubject subject = ClaimAccessSubject.of(id);

        assertEquals(id, subject.playerId());
        assertTrue(subject.identifiers().isEmpty());
    }

    @Test
    void createWithPlayerIdAndIdentifiers()
    {
        UUID id = UUID.randomUUID();
        ClaimAccessSubject subject = ClaimAccessSubject.of(id, Arrays.asList("group.admin", "group.builder"));

        assertEquals(id, subject.playerId());
        assertEquals(2, subject.identifiers().size());
    }

    @Test
    void emptyIdentifiersAreFilteredOut()
    {
        UUID id = UUID.randomUUID();
        ClaimAccessSubject subject = ClaimAccessSubject.of(id, Arrays.asList("valid", "", "  "));

        Set<String> identifiers = subject.identifiers();
        assertFalse(identifiers.isEmpty());
    }

    @Test
    void identifiersAreImmutable()
    {
        UUID id = UUID.randomUUID();
        ClaimAccessSubject subject = ClaimAccessSubject.of(id, Collections.singletonList("test"));

        Set<String> identifiers = subject.identifiers();
        try
        {
            identifiers.add("should-fail");
            // If no exception, the set is a copy — still valid
        }
        catch (UnsupportedOperationException e)
        {
            // Expected for unmodifiable set
        }
    }

    @Test
    void equalsWithSameValues()
    {
        UUID id = UUID.randomUUID();
        ClaimAccessSubject s1 = ClaimAccessSubject.of(id, Collections.singletonList("group.a"));
        ClaimAccessSubject s2 = ClaimAccessSubject.of(id, Collections.singletonList("group.a"));

        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void notEqualWithDifferentPlayerId()
    {
        ClaimAccessSubject s1 = ClaimAccessSubject.of(UUID.randomUUID());
        ClaimAccessSubject s2 = ClaimAccessSubject.of(UUID.randomUUID());

        assertNotEquals(s1, s2);
    }

    @Test
    void notEqualWithDifferentIdentifiers()
    {
        UUID id = UUID.randomUUID();
        ClaimAccessSubject s1 = ClaimAccessSubject.of(id, Collections.singletonList("a"));
        ClaimAccessSubject s2 = ClaimAccessSubject.of(id, Collections.singletonList("b"));

        assertNotEquals(s1, s2);
    }

    @Test
    void toStringContainsPlayerId()
    {
        UUID id = UUID.randomUUID();
        ClaimAccessSubject subject = ClaimAccessSubject.of(id);

        String str = subject.toString();
        assertTrue(str.contains(id.toString()));
    }

    @Test
    void equalsSameInstanceReturnsTrue()
    {
        ClaimAccessSubject subject = ClaimAccessSubject.of(UUID.randomUUID());
        assertEquals(subject, subject);
    }

    @Test
    void equalsNullReturnsFalse()
    {
        ClaimAccessSubject subject = ClaimAccessSubject.of(UUID.randomUUID());
        assertNotEquals(null, subject);
    }

    @Test
    void equalsDifferentTypeReturnsFalse()
    {
        ClaimAccessSubject subject = ClaimAccessSubject.of(UUID.randomUUID());
        assertNotEquals("string", subject);
    }
}
