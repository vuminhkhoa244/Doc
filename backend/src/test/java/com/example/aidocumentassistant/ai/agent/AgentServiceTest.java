package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private RagTool ragTool;

    @Mock
    private WebSearchTool webSearchTool;

    @Mock
    private SqlTool sqlTool;

    @Mock
    private AgentPlanner planner;

    @InjectMocks
    private AgentService agentService;

    private static ToolContext context(String question) {
        return new ToolContext(question, List.of(), 1L, List.of());
    }

    @Test
    void explicitToolBypassesPlanner() {
        when(ragTool.id()).thenReturn("rag");
        when(webSearchTool.id()).thenReturn("web");
        when(sqlTool.id()).thenReturn("sql");
        when(sqlTool.execute(any())).thenReturn(new ToolResult("answer", List.of(), "sql"));

        AgentService.AgentResult result = agentService.run("How many employees?", List.of(), 1L, "sql", List.of());

        assertEquals("sql", result.toolUsed());
        verify(sqlTool).execute(context("How many employees?"));
        verify(planner, never()).route(any(), anyList());
    }

    @Test
    void autoModeUsesPlannerRoute() {
        when(ragTool.id()).thenReturn("rag");
        when(webSearchTool.id()).thenReturn("web");
        when(planner.route("Latest AI news?", List.of())).thenReturn("web");
        when(webSearchTool.supports("Latest AI news?")).thenReturn(true);
        when(webSearchTool.executeStreaming(any(), any())).thenReturn(new ToolResult("news", List.of(), "web"));

        AgentService.AgentResult result = agentService.runStreaming(
                "Latest AI news?", List.of(), 1L, "auto", List.of(), token -> { }
        );

        assertEquals("web", result.toolUsed());
        verify(webSearchTool).executeStreaming(any(), any());
        verify(ragTool, never()).execute(any());
    }

    @Test
    void autoModeFallsBackWhenRoutedToolIsDisabled() {
        when(ragTool.id()).thenReturn("rag");
        when(webSearchTool.id()).thenReturn("web");
        when(planner.route("Latest AI news?", List.of())).thenReturn("web");
        when(webSearchTool.supports("Latest AI news?")).thenReturn(false);
        when(ragTool.supports("Latest AI news?")).thenReturn(true);
        when(ragTool.execute(any())).thenReturn(new ToolResult("no documents", List.of(), "rag"));

        AgentService.AgentResult result = agentService.run(
                "Latest AI news?", List.of(), 1L, null, List.of()
        );

        assertEquals("rag", result.toolUsed());
        verify(ragTool).execute(any());
        verify(webSearchTool, never()).execute(any());
    }

    @Test
    void blankToolBehavesLikeAuto() {
        when(ragTool.id()).thenReturn("rag");
        when(planner.route("What is annual leave?", List.of(5L))).thenReturn("rag");
        when(ragTool.supports("What is annual leave?")).thenReturn(true);
        when(ragTool.execute(any())).thenReturn(new ToolResult("15 days", List.of(), "rag"));

        AgentService.AgentResult result = agentService.run("What is annual leave?", List.of(5L), 1L, null, List.of());

        assertEquals("rag", result.toolUsed());
        verify(ragTool).execute(any());
    }
}
