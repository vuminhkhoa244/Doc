package com.example.aidocumentassistant.ai.agent;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hardening layer between LLM-generated SQL and the JDBC connection.
 * Only single-statement SELECT/WITH queries over allowlisted tables pass.
 */
public final class SqlGuard {
    static final int MAX_SQL_LENGTH = 2000;
    static final int DEFAULT_MAX_ROWS = 50;

    private static final Pattern FENCE = Pattern.compile("^```(?:sql)?\\s*|\\s*```$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_COMMENT = Pattern.compile("--.*$", Pattern.MULTILINE);
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern CTE_NAME = Pattern.compile(
            "\\bWITH\\s+(?:RECURSIVE\\s+)?([a-zA-Z_][a-zA-Z0-9_]*)\\s+AS\\s*\\(|,\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s+AS\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_REFERENCE =
            Pattern.compile("\\b(?:FROM|JOIN)\\s+([a-zA-Z_][a-zA-Z0-9_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMITClause = Pattern.compile("\\bLIMIT\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_TABLES = Set.of("employees");
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|MERGE|UPSERT|DROP|ALTER|CREATE|TRUNCATE|REPLACE|GRANT|REVOKE|"
                    + "CALL|EXECUTE?|ATTACH|DETACH|COPY|VACUUM|REINDEX|ANALYZE|COMMENT|SET|SHOW|PRAGMA|"
                    + "INSTALL|LOAD|IMPORT|EXPORT|SHUTDOWN|BACKUP|RESTORE|LOCK|UNLOCK|FETCH|DECLARE|CURSOR)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_FUNCTIONS = Pattern.compile(
            "\\b(FILE_READ|CSVREAD|SYSTEM_RANGE_TABLE|SYSTEM_RANGE_DIAGRAM|SYSTEM_COMPUTE_HASH|"
                    + "MEMORY_USED|RAND_UUID|GETTABLE|LINK_SCHEMA)\\b",
            Pattern.CASE_INSENSITIVE);

    private SqlGuard() {
    }

    /**
     * Normalizes and validates an LLM-generated SQL statement.
     *
     * @param rawSql statement as produced by the model (may include fences/comments)
     * @param allowedTables lowercase table names the agent may read
     * @return a safe single-statement SELECT with a LIMIT enforced
     * @throws IllegalArgumentException when the statement cannot be trusted
     */
    public static String sanitize(String rawSql, Set<String> allowedTables) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new IllegalArgumentException("The AI service returned an empty SQL statement.");
        }
        Set<String> allowlist = allowedTables == null || allowedTables.isEmpty()
                ? ALLOWED_TABLES
                : allowedTables;

        String sql = rawSql.strip();
        sql = stripFences(sql).strip();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).strip();
        }
        if (sql.length() > MAX_SQL_LENGTH) {
            throw new IllegalArgumentException("Generated SQL exceeds the allowed length.");
        }
        if (sql.contains(";")) {
            throw new IllegalArgumentException("Multiple SQL statements are not allowed.");
        }

        sql = LINE_COMMENT.matcher(sql).replaceAll(" ");
        sql = BLOCK_COMMENT.matcher(sql).replaceAll(" ").strip();
        if (sql.isBlank()) {
            throw new IllegalArgumentException("The AI service returned an empty SQL statement.");
        }

        String upper = sql.toUpperCase(Locale.ROOT);
        if (!(upper.startsWith("SELECT") || upper.startsWith("WITH"))) {
            throw new IllegalArgumentException("Only SELECT queries are allowed.");
        }
        if (FORBIDDEN.matcher(sql).find()) {
            throw new IllegalArgumentException("The generated SQL contains forbidden keywords.");
        }
        if (DANGEROUS_FUNCTIONS.matcher(sql).find()) {
            throw new IllegalArgumentException("The generated SQL contains forbidden functions.");
        }

        Set<String> readable = new LinkedHashSet<>(allowlist);
        Matcher cte = CTE_NAME.matcher(sql);
        while (cte.find()) {
            String name = cte.group(1) != null ? cte.group(1) : cte.group(2);
            readable.add(name.toLowerCase(Locale.ROOT));
        }

        Matcher matcher = TABLE_REFERENCE.matcher(sql);
        while (matcher.find()) {
            String table = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!readable.contains(table)) {
                throw new IllegalArgumentException("Query references a table that is not allowlisted: " + table);
            }
        }

        if (!LIMITClause.matcher(sql).find()) {
            sql = sql + " LIMIT " + DEFAULT_MAX_ROWS;
        }
        return sql;
    }

    private static String stripFences(String sql) {
        Matcher matcher = FENCE.matcher(sql);
        if (matcher.find()) {
            return matcher.replaceAll("").strip();
        }
        return sql;
    }

    static Set<String> allowedTables() {
        return new LinkedHashSet<>(ALLOWED_TABLES);
    }
}
