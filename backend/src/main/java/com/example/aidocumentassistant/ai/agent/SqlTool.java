package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SqlTool implements Tool {
    private static final Logger log = LoggerFactory.getLogger(SqlTool.class);
    private static final String ID = "sql";
    private static final String DESCRIPTION = "Query structured data";
    private static final int QUERY_TIMEOUT_SECONDS = 8;
    private static final int MAX_DISPLAY_ROWS = 20;
    private static final int MAX_CELL_CHARS = 60;
    private static final Set<String> ALLOWED_TABLES = Set.of("employees");

    static final String SCHEMA = """
            CREATE TABLE employees (
              id BIGINT,
              name VARCHAR(120),
              department VARCHAR(60),
              role VARCHAR(80),
              salary NUMERIC(12,2),
              hired_date DATE,
              location VARCHAR(60)
            );
            """;

    static final String SYSTEM_PROMPT = """
            You are a SQL expert working against a read-only analytics database.
            Reply with EXACTLY ONE SQL statement and nothing else: no markdown fences, no explanations.
            Rules:
            - Only SELECT (or WITH ... SELECT); a single statement.
            - Only read the tables listed in the schema.
            - Add LIMIT 50 unless the question asks for an aggregate such as COUNT or AVG that returns one row.
            """;

    private final AiClient aiClient;
    private final JdbcTemplate jdbcTemplate;

    public SqlTool(AiClient aiClient, JdbcTemplate jdbcTemplate) {
        this.aiClient = aiClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public boolean supports(String question) {
        return true;
    }

    @Override
    public ToolResult execute(ToolContext context) {
        String rawSql;
        try {
            rawSql = generateSql(context.question());
        } catch (RestClientException exception) {
            log.warn("SQL generation failed: {}", exception.getMessage());
            return new ToolResult(
                    "SQL generation is unavailable right now because the AI service could not be reached. Please try again.",
                    List.of(),
                    ID
            );
        }

        String sql;
        try {
            sql = SqlGuard.sanitize(rawSql, ALLOWED_TABLES);
        } catch (IllegalArgumentException exception) {
            log.warn("Blocked unsafe generated SQL: {} (raw: {})", exception.getMessage(), rawSql);
            return new ToolResult(
                    "I could not safely translate that question into a read-only query ("
                            + exception.getMessage()
                            + "). Try rephrasing it, for example: \"How many employees work in Engineering?\"",
                    List.of(),
                    ID
            );
        }

        try {
            QueryOutcome outcome = runQuery(sql);
            return formatAnswer(sql, outcome.columns(), outcome.rows());
        } catch (DataAccessException exception) {
            log.warn("SQL execution failed for [{}]: {}", sql, exception.getMessage());
            return new ToolResult(
                    "The generated query failed to run: " + safeError(exception.getMostSpecificCause().getMessage()),
                    List.of(),
                    ID
            );
        }
    }

    private String generateSql(String question) {
        String prompt = "Schema:\n" + SCHEMA + "\nQuestion: " + question + "\nSQL:";
        AiClient.CompleteResponse response = aiClient.complete(
                new AiClient.CompleteRequest(SYSTEM_PROMPT, prompt, 300)
        );
        return response == null || response.answer() == null ? "" : response.answer();
    }

    private QueryOutcome runQuery(String sql) {
        return jdbcTemplate.execute((ConnectionCallback<QueryOutcome>) connection -> {
            try {
                connection.setReadOnly(true);
            } catch (SQLException exception) {
                // Already inside a Spring transaction (e.g. Postgres rejects mid-tx
                // read-only flips); SqlGuard still enforces SELECT-only + LIMIT.
                log.debug("Could not mark connection read-only: {}", exception.getMessage());
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                statement.setMaxRows(SqlGuard.DEFAULT_MAX_ROWS);
                try (ResultSet resultSet = statement.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    List<String> columns = new ArrayList<>(columnCount);
                    Set<String> seen = new LinkedHashSet<>();
                    for (int index = 1; index <= columnCount; index++) {
                        String label = metaData.getColumnLabel(index);
                        if (!seen.add(label.toLowerCase(Locale.ROOT))) {
                            label = label + "_" + index;
                        }
                        columns.add(label);
                    }

                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (resultSet.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int index = 1; index <= columnCount; index++) {
                            row.put(columns.get(index - 1), resultSet.getObject(index));
                        }
                        rows.add(row);
                    }
                    return new QueryOutcome(columns, rows);
                }
            }
        });
    }

    private ToolResult formatAnswer(String sql, List<String> columns, List<Map<String, Object>> rows) {
        StringBuilder answer = new StringBuilder();
        answer.append("Here's what I found in the `employees` table:\n\n");
        answer.append("```sql\n").append(sql).append("\n```\n\n");

        if (rows.isEmpty()) {
            answer.append("The query ran successfully but matched **0 rows**.");
            return new ToolResult(answer.toString(), List.of(), ID);
        }

        answer.append("| ").append(String.join(" | ", columns)).append(" |\n");
        answer.append("|").append(" --- |".repeat(columns.size())).append("\n");

        int shown = 0;
        for (Map<String, Object> row : rows) {
            if (shown >= MAX_DISPLAY_ROWS) {
                break;
            }
            List<String> cells = new ArrayList<>(columns.size());
            for (String column : columns) {
                cells.add(formatCell(row.get(column)));
            }
            answer.append("| ").append(String.join(" | ", cells)).append(" |\n");
            shown++;
        }

        if (rows.size() > MAX_DISPLAY_ROWS) {
            answer.append("\n…and ").append(rows.size() - MAX_DISPLAY_ROWS)
                    .append(" more rows (results are capped at ").append(SqlGuard.DEFAULT_MAX_ROWS).append(").");
        }
        answer.append("\n_(").append(rows.size()).append(rows.size() == 1 ? " row" : " rows").append(")_");
        return new ToolResult(answer.toString(), List.of(), ID);
    }

    private String formatCell(Object value) {
        if (value == null) {
            return "—";
        }
        if (value instanceof BigDecimal decimal) {
            value = decimal.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
        String text = String.valueOf(value).replace("|", "\\|").replace("\n", " ").trim();
        if (text.length() > MAX_CELL_CHARS) {
            text = text.substring(0, MAX_CELL_CHARS - 1) + "…";
        }
        return text.isBlank() ? "—" : text;
    }

    private String safeError(String message) {
        if (message == null || message.isBlank()) {
            return "unknown database error.";
        }
        String cleaned = message.replace("\n", " ").trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 197) + "..." : cleaned;
    }

    record QueryOutcome(List<String> columns, List<Map<String, Object>> rows) {
    }
}
