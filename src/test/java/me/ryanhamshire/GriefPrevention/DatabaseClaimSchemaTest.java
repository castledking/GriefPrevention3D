package me.ryanhamshire.GriefPrevention;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the database backend's claim table against the failure mode that a fresh install hits:
 * a brand new database is stamped at the latest schema, so every versioned {@code ALTER} in
 * {@code initialize()} is skipped and the create statement is the only thing that builds the table.
 * Anything the insert writes but the create omits fails on the first claim save.
 */
class DatabaseClaimSchemaTest
{
    @Test
    void everyColumnTheInsertWritesExistsOnAFreshlyCreatedTable()
    {
        Set<String> created = createdColumns();
        List<String> inserted = insertedColumns();

        List<String> missing = new ArrayList<>();
        for (String column : inserted)
        {
            if (!created.contains(column)) missing.add(column);
        }

        assertEquals(Collections.<String>emptyList(), missing,
                "Columns written by SQL_INSERT_CLAIM but never created on a fresh database");
    }

    @Test
    void theInsertBindsExactlyOnePlaceholderPerColumn()
    {
        String insert = DatabaseDataStore.SQL_INSERT_CLAIM;
        int placeholders = insert.length() - insert.replace("?", "").length();

        assertEquals(insertedColumns().size(), placeholders);
    }

    @Test
    void bothStatementsCarryTheDenyEntryColumn()
    {
        // Deny entries record trust a subdivision revoked. Dropping them on write hands that
        // trust back at the next restart.
        assertTrue(createdColumns().contains("denied"));
        assertTrue(insertedColumns().contains("denied"));
    }

    @Test
    void storedListsRoundTripDenyIdentifiersWithSuffixesAndPermissionNodes()
    {
        // storageStringBuilder emits a trailing delimiter, so parsing has to drop the empty tail.
        assertEquals(
                Arrays.asList("[server.builders]#build", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee#access"),
                DatabaseDataStore.parseStorageList(
                        "[server.builders]#build;aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee#access;"
                )
        );
        assertEquals(Collections.<String>emptyList(), DatabaseDataStore.parseStorageList(""));
        assertEquals(Collections.<String>emptyList(), DatabaseDataStore.parseStorageList(null));
    }

    private static Set<String> createdColumns()
    {
        String create = DatabaseDataStore.SQL_CREATE_CLAIM_TABLE;
        String body = create.substring(create.indexOf('(') + 1, create.lastIndexOf(')'));

        Set<String> columns = new LinkedHashSet<>();
        for (String definition : body.split(","))
        {
            String trimmed = definition.trim();
            if (trimmed.isEmpty()) continue;
            // A definition is "name TYPE [DEFAULT x]"; the name is the leading token. Parenthesised
            // type widths such as VARCHAR(50) split on the comma-free part, so they stay intact.
            columns.add(trimmed.split("\\s+")[0].toLowerCase());
        }
        return columns;
    }

    private static List<String> insertedColumns()
    {
        String insert = DatabaseDataStore.SQL_INSERT_CLAIM;
        String body = insert.substring(insert.indexOf('(') + 1, insert.indexOf(')'));

        List<String> columns = new ArrayList<>();
        for (String column : body.split(","))
        {
            String trimmed = column.trim();
            if (!trimmed.isEmpty()) columns.add(trimmed.toLowerCase());
        }
        return columns;
    }
}
