package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import com.example.aidocumentassistant.ai.websearch.WebSearchClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSearchToolTest {

    @Mock
    private WebSearchClient webSearchClient;

    @Mock
    private AiClient aiClient;

    @InjectMocks
    private WebSearchTool tool;

    private static ToolContext context(String question) {
        return new ToolContext(question, List.of(), 1L, List.of(new AiClient.HistoryTurn("earlier", "answer")));
    }

    private static List<WebSearchClient.WebResult> sampleResults() {
        return List.of(
                new WebSearchClient.WebResult("Docker overview", "https://docs.docker.com", "Containers share the host kernel."),
                new WebSearchClient.WebResult("Kernel docs", "https://kernel.org", "The kernel mediates hardware access.")
        );
    }

    @Test
    void supportsReflectsWhetherABackendIsConfigured() {
        when(webSearchClient.isEnabled()).thenReturn(true);
        assertTrue(tool.supports("anything"));

        when(webSearchClient.isEnabled()).thenReturn(false);
        assertFalse(tool.supports("anything"));
    }

    @Test
    void searchesAndReturnsGeneratedAnswer() {
        List<WebSearchClient.WebResult> results = sampleResults();
        when(webSearchClient.search("How do containers work?")).thenReturn(results);
        when(aiClient.generate(any())).thenReturn(new AiClient.GenerateResponse("Containers share the host kernel."));

        ToolResult result = tool.execute(context("How do containers work?"));

        assertEquals("Containers share the host kernel.", result.answer());
        assertEquals(List.of(), result.sources());
        assertEquals("web", result.toolUsed());

        ArgumentCaptor<AiClient.GenerateRequest> captor = ArgumentCaptor.forClass(AiClient.GenerateRequest.class);
        verify(aiClient).generate(captor.capture());
        AiClient.GenerateRequest request = captor.getValue();
        assertEquals("How do containers work?", request.question());
        assertTrue(request.context().contains("[1] Docker overview\nURL: https://docs.docker.com\nContainers share the host kernel."));
        assertTrue(request.context().contains("[2] Kernel docs\nURL: https://kernel.org\nThe kernel mediates hardware access."));
        assertEquals(1, request.history().size());
        assertEquals("earlier", request.history().get(0).question());
    }

    @Test
    void returnsUnavailableMessageWhenSearchFails() {
        when(webSearchClient.search(any())).thenThrow(new ResourceAccessException("connection refused"));

        ToolResult result = tool.execute(context("anything"));

        assertTrue(result.answer().toLowerCase().contains("unavailable"));
        assertEquals("web", result.toolUsed());
        verify(aiClient, never()).generate(any());
    }

    @Test
    void returnsNoResultsMessageWhenSearchIsEmpty() {
        when(webSearchClient.search("no hits")).thenReturn(List.of());

        ToolResult result = tool.execute(context("no hits"));

        assertTrue(result.answer().toLowerCase().contains("could not find any web results"));
        verify(aiClient, never()).generate(any());
    }

    @Test
    void fallsBackToSnippetsWhenGenerationFails() {
        when(webSearchClient.search(any())).thenReturn(sampleResults());
        when(aiClient.generate(any())).thenThrow(new ResourceAccessException("ai-service down"));

        ToolResult result = tool.execute(context("How do containers work?"));

        assertTrue(result.answer().startsWith("Here's what I found on the web:"));
        assertTrue(result.answer().contains("https://docs.docker.com"));
        assertTrue(result.answer().contains("https://kernel.org"));
        assertEquals("web", result.toolUsed());
    }

    @Test
    void fallsBackToSnippetsWhenAnswerIsBlank() {
        when(webSearchClient.search(any())).thenReturn(sampleResults());
        when(aiClient.generate(any())).thenReturn(new AiClient.GenerateResponse("   "));

        ToolResult result = tool.execute(context("How do containers work?"));

        assertTrue(result.answer().startsWith("Here's what I found on the web:"));
    }

    @Test
    void toolIdentityMatchesAgentExpectations() {
        when(webSearchClient.isEnabled()).thenReturn(true);
        assertEquals("web", tool.id());
        assertTrue(tool.supports("any question"));
        assertEquals("Search the internet", tool.description());
    }
}
