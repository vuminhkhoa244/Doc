package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentPlannerTest {

    @Mock
    private AiClient aiClient;

    @InjectMocks
    private AgentPlanner planner;

    @Test
    void routesUsingLlmJson() {
        when(aiClient.complete(any())).thenReturn(new AiClient.CompleteResponse(
                "{\"tool\":\"web\",\"reason\":\"current events question\"}"
        ));
        assertEquals("web", planner.route("What is the latest AI news today?", List.of()));
    }

    @Test
    void parsesFencedJsonWithSurroundingProse() {
        when(aiClient.complete(any())).thenReturn(new AiClient.CompleteResponse(
                "Sure!\n```json\n{\"tool\":\"sql\",\"reason\":\"headcount question\"}\n```"
        ));
        assertEquals("sql", planner.route("How many employees work in Engineering?", List.of()));
    }

    @Test
    void fallsBackToKeywordsWhenLlmUnavailable() {
        when(aiClient.complete(any())).thenThrow(new ResourceAccessException("connection refused"));
        assertEquals("sql", planner.route("How many employees work in Engineering?", List.of()));
        assertEquals("web", planner.route("Search the internet for Docker best practices", List.of()));
        assertEquals("rag", planner.route("What does the employee handbook say about leave?", List.of(7L)));
    }

    @Test
    void fallsBackToKeywordsWhenLlmReturnsGarbage() {
        when(aiClient.complete(any())).thenReturn(new AiClient.CompleteResponse("I cannot help with that."));
        assertEquals("rag", planner.route("What is annual leave?", List.of(3L)));
    }

    @Test
    void prefersSqlOnSalaryQuestions() {
        lenient().when(aiClient.complete(any())).thenReturn(new AiClient.CompleteResponse(""));
        assertEquals("sql", planner.route("average salary by department", null));
    }

    @Test
    void defaultsToRagForAmbiguousQuestionsWithDocumentsSelected() {
        lenient().when(aiClient.complete(any())).thenThrow(new ResourceAccessException("down"));
        assertEquals("rag", planner.route("tell me more", List.of(1L, 2L)));
    }
}
