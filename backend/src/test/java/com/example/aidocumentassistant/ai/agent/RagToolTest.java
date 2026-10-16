package com.example.aidocumentassistant.ai.agent;

import com.example.aidocumentassistant.ai.AiClient;
import com.example.aidocumentassistant.document.Document;
import com.example.aidocumentassistant.document.DocumentRepository;
import com.example.aidocumentassistant.document.SemanticSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagToolTest {

    @Mock
    private SemanticSearchService semanticSearchService;

    @Mock
    private AiClient aiClient;

    @Mock
    private DocumentRepository documents;

    @InjectMocks
    private RagTool tool;

    private static ToolContext context(String question, List<Long> selectedIds) {
        return new ToolContext(question, selectedIds, 7L, List.of());
    }

    private void stubOwnerDocs() {
        Document docker = mock(Document.class);
        when(docker.getId()).thenReturn(1L);
        Document handbook = mock(Document.class);
        when(handbook.getId()).thenReturn(2L);
        when(documents.findByOwnerIdOrderByUploadTimeDesc(7L)).thenReturn(List.of(docker, handbook));
    }

    @Test
    void selectionBecomesFocusOverOwnerUniverse() {
        stubOwnerDocs();
        when(semanticSearchService.tokenize(any())).thenReturn(List.of("leave", "days"));
        when(aiClient.query(any())).thenReturn(new AiClient.QueryResponse("15 days", List.of()));

        tool.execute(context("How many leave days?", List.of(1L)));

        ArgumentCaptor<AiClient.QueryRequest> captor = ArgumentCaptor.forClass(AiClient.QueryRequest.class);
        verify(aiClient).query(captor.capture());
        AiClient.QueryRequest request = captor.getValue();
        assertEquals(List.of(1L, 2L), request.documentIds());
        assertEquals(List.of(1L), request.focusDocumentIds());
    }

    @Test
    void noSelectionSearchesWholeLibraryWithoutFocus() {
        stubOwnerDocs();
        when(semanticSearchService.tokenize(any())).thenReturn(List.of("leave", "days"));
        when(aiClient.query(any())).thenReturn(new AiClient.QueryResponse("15 days", List.of()));

        tool.execute(context("How many leave days?", List.of()));

        ArgumentCaptor<AiClient.QueryRequest> captor = ArgumentCaptor.forClass(AiClient.QueryRequest.class);
        verify(aiClient).query(captor.capture());
        AiClient.QueryRequest request = captor.getValue();
        assertEquals(List.of(1L, 2L), request.documentIds());
        assertNull(request.focusDocumentIds());
    }

    @Test
    void foreignDocumentIdsAreDroppedFromFocus() {
        stubOwnerDocs();
        when(semanticSearchService.tokenize(any())).thenReturn(List.of("leave", "days"));
        when(aiClient.query(any())).thenReturn(new AiClient.QueryResponse("15 days", List.of()));

        tool.execute(context("How many leave days?", List.of(1L, 99L)));

        ArgumentCaptor<AiClient.QueryRequest> captor = ArgumentCaptor.forClass(AiClient.QueryRequest.class);
        verify(aiClient).query(captor.capture());
        AiClient.QueryRequest request = captor.getValue();
        assertEquals(List.of(1L, 2L), request.documentIds());
        assertEquals(List.of(1L), request.focusDocumentIds());
    }

    @Test
    void selectionOfOnlyForeignDocsFallsBackToUniverse() {
        stubOwnerDocs();
        when(semanticSearchService.tokenize(any())).thenReturn(List.of("leave", "days"));
        when(aiClient.query(any())).thenReturn(new AiClient.QueryResponse("15 days", List.of()));

        tool.execute(context("How many leave days?", List.of(99L)));

        ArgumentCaptor<AiClient.QueryRequest> captor = ArgumentCaptor.forClass(AiClient.QueryRequest.class);
        verify(aiClient).query(captor.capture());
        AiClient.QueryRequest request = captor.getValue();
        assertEquals(List.of(1L, 2L), request.documentIds());
        assertNull(request.focusDocumentIds());
    }

    @Test
    void returnsAnswerAndSourcesFromAiService() {
        stubOwnerDocs();
        when(semanticSearchService.tokenize(any())).thenReturn(List.of("leave", "days"));
        when(aiClient.query(any())).thenReturn(new AiClient.QueryResponse(
                "Employees receive 15 days.",
                List.of(new AiClient.SourcePayload(2L, "handbook.txt", 12))
        ));

        ToolResult result = tool.execute(context("How many leave days?", List.of(1L)));

        assertEquals("Employees receive 15 days.", result.answer());
        assertEquals("rag", result.toolUsed());
        assertEquals(1, result.sources().size());
        assertEquals(2L, result.sources().get(0).documentId());
    }
}
