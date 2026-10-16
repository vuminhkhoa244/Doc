package com.example.aidocumentassistant.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfRuledTableExtractorTest {

    @TempDir
    Path tempDir;

    private final PdfRuledTableExtractor extractor = new PdfRuledTableExtractor();

    private Path ruledTablePdf() throws IOException {
        Path path = tempDir.resolve("ruled-table.pdf");
        String[][] cells = {
                {"Name", "Role", "Team"},
                {"Alice", "Engineer", "Platform"},
                {"Bob", "Designer", "Growth"}
        };
        float originX = 72;
        float originY = 700;
        float cellWidth = 140;
        float cellHeight = 30;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (int row = 0; row <= 3; row++) {
                    content.moveTo(originX, originY - row * cellHeight);
                    content.lineTo(originX + 3 * cellWidth, originY - row * cellHeight);
                    content.stroke();
                }
                for (int column = 0; column <= 3; column++) {
                    content.moveTo(originX + column * cellWidth, originY);
                    content.lineTo(originX + column * cellWidth, originY - 3 * cellHeight);
                    content.stroke();
                }
                for (int row = 0; row < 3; row++) {
                    for (int column = 0; column < 3; column++) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.setTextMatrix(Matrix.getTranslateInstance(
                                originX + column * cellWidth + 8,
                                originY - row * cellHeight - 20
                        ));
                        content.showText(cells[row][column]);
                        content.endText();
                    }
                }
            }
            document.save(path.toFile());
        }
        return path;
    }

    @Test
    void detectsRuledGridTable() throws IOException {
        List<TableExtractor.TableData> tables = extractor.extract(ruledTablePdf());

        assertEquals(1, tables.size());
        TableExtractor.TableData table = tables.get(0);
        assertEquals(1, table.page());
        assertEquals(3, table.rowCount());
        assertTrue(table.markdown().contains("| Name | Role | Team |"));
        assertTrue(table.markdown().contains("Name"));
        assertTrue(table.markdown().contains("Alice"));
        assertTrue(table.markdown().contains("Platform"));
        assertTrue(table.markdown().contains("Bob"));
    }

    @Test
    void ignoresTableFreePages() throws IOException {
        Path path = tempDir.resolve("plain.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText("Just a paragraph of ordinary text.");
                content.endText();
            }
            document.save(path.toFile());
        }

        assertTrue(extractor.extract(path).isEmpty());
    }
}
