package com.example.aidocumentassistant.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownTableExtractorTest {

    private final MarkdownTableExtractor extractor = new MarkdownTableExtractor();

    @Test
    void extractsGfmPipeTable() {
        String content = """
                Some intro text.

                | Name | Role |
                | --- | --- |
                | Alice | Engineer |
                | Bob | Designer |

                Closing notes.
                """;

        List<TableExtractor.TableData> tables = extractor.extract(content);

        assertEquals(1, tables.size());
        assertEquals(2, tables.get(0).rowCount());
        assertEquals(1, tables.get(0).page());
        assertTrue(tables.get(0).markdown().contains("| Name | Role |"));
        assertTrue(tables.get(0).markdown().contains("| Alice | Engineer |"));
    }

    @Test
    void ignoresPipesWithoutSeparatorRow() {
        String content = "Cost | Benefit\njust | text\n";
        assertTrue(extractor.extract(content).isEmpty());
    }

    @Test
    void ignoresRaggedBodyRows() {
        String content = """
                | A | B |
                | --- | --- |
                | 1 | 2 | 3 |
                """;
        assertTrue(extractor.extract(content).isEmpty());
    }

    @Test
    void handlesPipesWithoutOuterBorders() {
        String content = """
                Region | Sales
                --- | ---
                North | 10
                South | 20
                """;

        List<TableExtractor.TableData> tables = extractor.extract(content);

        assertEquals(1, tables.size());
        assertTrue(tables.get(0).markdown().contains("| North | 10 |"));
    }
}
