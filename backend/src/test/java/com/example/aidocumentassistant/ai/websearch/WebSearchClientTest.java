package com.example.aidocumentassistant.ai.websearch;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchClientTest {

    private static WebSearchClient.SearxResponse parse(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(json, WebSearchClient.SearxResponse.class);
    }

    @Test
    void parsesSearxJsonPayload() throws Exception {
        String json = """
                {
                  "query": "docker kernel",
                  "number_of_results": 2,
                  "results": [
                    {"title": "Docker overview", "url": "https://docs.docker.com", "content": "Containers share the host kernel.", "engine": "duckduckgo"},
                    {"title": "Linux containers", "url": "https://en.wikipedia.org/wiki/Container_(virtualization)", "content": "Containers are an OS-level virtualization method."}
                  ]
                }
                """;
        List<WebSearchClient.WebResult> results = WebSearchClient.normalize(parse(json), 5);
        assertEquals(2, results.size());
        assertEquals("Docker overview", results.get(0).title());
        assertEquals("https://docs.docker.com", results.get(0).url());
        assertEquals("Containers share the host kernel.", results.get(0).snippet());
        assertEquals("Linux containers", results.get(1).title());
    }

    @Test
    void normalizeFiltersBlankAndDuplicateResults() {
        WebSearchClient.SearxResponse raw = new WebSearchClient.SearxResponse(List.of(
                new WebSearchClient.SearxResult("First", "https://a.example", "snippet one"),
                new WebSearchClient.SearxResult("", "https://b.example", "missing title"),
                new WebSearchClient.SearxResult("Duplicate", "https://a.example", "same url again"),
                new WebSearchClient.SearxResult("No content", "https://c.example", "   "),
                new WebSearchClient.SearxResult("Second", "https://d.example", "snippet two")
        ));

        List<WebSearchClient.WebResult> results = WebSearchClient.normalize(raw, 5);

        assertEquals(2, results.size());
        assertEquals("First", results.get(0).title());
        assertEquals("Second", results.get(1).title());
    }

    @Test
    void normalizeHonorsMaxResults() {
        WebSearchClient.SearxResponse raw = new WebSearchClient.SearxResponse(List.of(
                new WebSearchClient.SearxResult("One", "https://1", "a"),
                new WebSearchClient.SearxResult("Two", "https://2", "b"),
                new WebSearchClient.SearxResult("Three", "https://3", "c")
        ));

        List<WebSearchClient.WebResult> results = WebSearchClient.normalize(raw, 2);

        assertEquals(2, results.size());
        assertEquals("One", results.get(0).title());
        assertEquals("Two", results.get(1).title());
    }

    @Test
    void normalizeHandlesNullPayloads() {
        assertTrue(WebSearchClient.normalize(null, 5).isEmpty());
        assertTrue(WebSearchClient.normalize(new WebSearchClient.SearxResponse(null), 5).isEmpty());
        assertTrue(WebSearchClient.normalize(new WebSearchClient.SearxResponse(List.of()), 5).isEmpty());
        assertTrue(WebSearchClient.normalize(
                new WebSearchClient.SearxResponse(Arrays.asList((WebSearchClient.SearxResult) null)), 5
        ).isEmpty());
    }
}
