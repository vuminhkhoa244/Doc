package com.example.aidocumentassistant.document;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxTableExtractorTest {

    @TempDir
    Path tempDir;

    private final DocxTableExtractor extractor = new DocxTableExtractor();

    private Path docxWithTable(String... header) throws IOException {
        Path path = tempDir.resolve("table.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Quarterly results below.");
            XWPFTable table = document.createTable(3, header.length);
            for (int column = 0; column < header.length; column++) {
                table.getRow(0).getCell(column).setText(header[column]);
            }
            table.getRow(1).getCell(0).setText("Q1");
            table.getRow(1).getCell(1).setText("1200");
            table.getRow(2).getCell(0).setText("Q2");
            table.getRow(2).getCell(1).setText("1800");
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                document.write(bytes);
                Files.write(path, bytes.toByteArray());
            }
        }
        return path;
    }

    @Test
    void extractsDocxTableAsMarkdown() throws IOException {
        List<TableExtractor.TableData> tables = extractor.extract(docxWithTable("Quarter", "Revenue"));

        assertEquals(1, tables.size());
        assertEquals(1, tables.get(0).page());
        assertEquals(2, tables.get(0).rowCount());
        assertTrue(tables.get(0).markdown().contains("| Quarter | Revenue |"));
        assertTrue(tables.get(0).markdown().contains("| Q1 | 1200 |"));
        assertTrue(tables.get(0).markdown().contains("| Q2 | 1800 |"));
    }

    @Test
    void ignoresTablesWithoutEnoughStructure() throws IOException {
        Path path = tempDir.resolve("single.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createTable(1, 1).getRow(0).getCell(0).setText("only");
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                document.write(bytes);
                Files.write(path, bytes.toByteArray());
            }
        }

        assertTrue(extractor.extract(path).isEmpty());
    }
}
