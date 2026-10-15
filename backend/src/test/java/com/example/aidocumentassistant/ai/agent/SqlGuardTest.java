package com.example.aidocumentassistant.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlGuardTest {

    private static final Set<String> TABLES = Set.of("employees");

    @Test
    void passesPlainSelectAndAppendsLimit() {
        String sql = SqlGuard.sanitize("SELECT name, department FROM employees", TABLES);
        assertEquals("SELECT name, department FROM employees LIMIT 50", sql);
    }

    @Test
    void keepsExplicitLimit() {
        String sql = SqlGuard.sanitize("SELECT * FROM employees LIMIT 5", TABLES);
        assertEquals("SELECT * FROM employees LIMIT 5", sql);
    }

    @Test
    void stripsMarkdownFencesAndTrailingSemicolon() {
        String sql = SqlGuard.sanitize("```sql\nSELECT * FROM employees;\n```", TABLES);
        assertEquals("SELECT * FROM employees LIMIT 50", sql);
    }

    @Test
    void stripsLineComments() {
        String sql = SqlGuard.sanitize("SELECT * FROM employees -- dump everything", TABLES);
        assertEquals("SELECT * FROM employees LIMIT 50", sql);
    }

    @Test
    void supportsCteSelect() {
        String sql = SqlGuard.sanitize(
                "WITH eng AS (SELECT * FROM employees WHERE department = 'Engineering') SELECT COUNT(*) FROM eng",
                TABLES
        );
        assertTrue(sql.startsWith("WITH eng AS"));
        assertTrue(sql.endsWith("LIMIT 50"));
    }

    @Test
    void rejectsMultipleStatements() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlGuard.sanitize("SELECT * FROM employees; DROP TABLE employees", TABLES));
    }

    @Test
    void rejectsNonSelect() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlGuard.sanitize("DELETE FROM employees", TABLES));
    }

    @Test
    void rejectsDdlStatement() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlGuard.sanitize("CREATE TABLE evil AS SELECT * FROM employees", TABLES));
    }

    @Test
    void rejectsHiddenWriteInsideSelect() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlGuard.sanitize("WITH x AS (DELETE FROM employees RETURNING *) SELECT * FROM x", TABLES));
    }

    @Test
    void rejectsNonAllowlistedTable() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlGuard.sanitize("SELECT * FROM users", TABLES));
        assertThrows(IllegalArgumentException.class, () ->
                SqlGuard.sanitize("SELECT * FROM employees e JOIN sessions s ON s.id = e.id", TABLES));
    }

    @Test
    void rejectsSchemaQualifiedTables() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlGuard.sanitize("SELECT * FROM public.employees", TABLES));
    }

    @Test
    void rejectsDangerousFunctions() {
        assertThrows(IllegalArgumentException.class, () ->
                SqlGuard.sanitize("SELECT FILE_READ('/etc/passwd') FROM employees", TABLES));
    }

    @Test
    void rejectsEmptyAndOverlongInput() {
        assertThrows(IllegalArgumentException.class, () -> SqlGuard.sanitize("   ", TABLES));
        assertThrows(IllegalArgumentException.class, () -> SqlGuard.sanitize(null, TABLES));
        assertThrows(IllegalArgumentException.class, () ->
                SqlGuard.sanitize("SELECT * FROM employees WHERE name = '" + "x".repeat(3000) + "'", TABLES));
    }
}
