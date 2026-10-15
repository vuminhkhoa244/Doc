package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlToolTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SqlTool tool;

    private static ToolContext context(String question) {
        return new ToolContext(question, List.of(), 1L, List.of());
    }

    @Test
    void runsGeneratedQueryAndFormatsMarkdownTable() {
        when(aiClient.complete(any())).thenReturn(new AiClient.CompleteResponse(
                "SELECT department, COUNT(*) AS headcount FROM employees GROUP BY department"
        ));
        SqlTool.QueryOutcome outcome = new SqlTool.QueryOutcome(
                List.of("department", "headcount"),
                List.of(
                        Map.of("department", "Engineering", "headcount", 4L),
                        Map.of("department", "Sales", "headcount", 2L)
                )
        );
        doReturn(outcome).when(jdbcTemplate).execute(any(ConnectionCallback.class));

        ToolResult result = tool.execute(context("How many employees per department?"));

        assertEquals("sql", result.toolUsed());
        assertTrue(result.answer().contains("```sql"));
        assertTrue(result.answer().contains("| department | headcount |"));
        assertTrue(result.answer().contains("| Engineering | 4 |"));
        assertTrue(result.answer().contains("(2 rows)"));
        verify(jdbcTemplate).execute(any(ConnectionCallback.class));
    }

    @Test
    void stripsFencesBeforeExecuting() {
        when(aiClient.complete(any())).thenReturn(new AiClient.CompleteResponse(
                "```sql\nSELECT name FROM employees LIMIT 3\n```"
        ));
        SqlTool.QueryOutcome outcome = new SqlTool.QueryOutcome(List.of("name"), List.of(Map.of("name", "An")));
        doReturn(outcome).when(jdbcTemplate).execute(any(ConnectionCallback.class));

        ToolResult result = tool.execute(context("List three employees"));

        assertFalse(result.answer().contains("```sql\n```sql"));
        assertTrue(result.answer().contains("SELECT name FROM employees LIMIT 3"));
    }

    @Test
    void blocksUnsafeGeneratedSql() {
        when(aiClient.complete(any())).thenReturn(new AiClient.CompleteResponse("DROP TABLE employees"));

        ToolResult result = tool.execute(context("Delete everything"));

        assertTrue(result.answer().contains("could not safely translate"));
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void blocksNonAllowlistedTables() {
        when(aiClient.complete(any())).thenReturn(new AiClient.CompleteResponse("SELECT * FROM users"));

        ToolResult result = tool.execute(context("Show users"));

        assertTrue(result.answer().contains("could not safely translate"));
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void reportsLlmUnavailability() {
        when(aiClient.complete(any())).thenThrow(new ResourceAccessException("connection refused"));

        ToolResult result = tool.execute(context("How many employees?"));

        assertTrue(result.answer().contains("unavailable"));
        verify(jdbcTemplate, never()).execute(any(ConnectionCallback.class));
    }

    @Test
    void reportsDatabaseErrors() {
        when(aiClient.complete(any())).thenReturn(new AiClient.CompleteResponse("SELECT nope FROM employees"));
        doThrow(new QueryTimeoutException("timeout"))
                .when(jdbcTemplate).execute(any(ConnectionCallback.class));

        ToolResult result = tool.execute(context("How many?"));

        assertTrue(result.answer().contains("failed to run"));
    }

    @Test
    void sendsSchemaAndQuestionToAiService() {
        when(aiClient.complete(any())).thenReturn(new AiClient.CompleteResponse("SELECT 1 FROM employees"));
        doReturn(new SqlTool.QueryOutcome(List.of("1"), List.of(Map.of("1", 1))))
                .when(jdbcTemplate).execute(any(ConnectionCallback.class));

        tool.execute(context("How many employees are there?"));

        ArgumentCaptor<AiClient.CompleteRequest> captor = ArgumentCaptor.forClass(AiClient.CompleteRequest.class);
        verify(aiClient).complete(captor.capture());
        AiClient.CompleteRequest request = captor.getValue();
        assertTrue(request.system().contains("read-only"));
        assertTrue(request.prompt().contains("CREATE TABLE employees"));
        assertTrue(request.prompt().contains("How many employees are there?"));
        assertEquals(300, request.maxTokens());
    }
}
