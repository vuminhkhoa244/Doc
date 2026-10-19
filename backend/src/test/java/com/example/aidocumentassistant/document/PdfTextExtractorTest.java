package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.config.AppProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfTextExtractorTest {

    @TempDir
    Path tempDir;

    private static AppProperties properties(boolean ocrEnabled) {
        return new AppProperties(
                null,
                null,
                null,
                null,
                null,
                new AppProperties.Ocr(ocrEnabled, "/nonexistent-tessdata", "vie+eng", 300, 50, 10),
                null,
                null,
                null
        );
    }

    private Path textPdf(String text) throws IOException {
        Path path = tempDir.resolve("text.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(path.toFile());
        }
        return path;
    }

    private Path blankPdf(int pages) throws IOException {
        Path path = tempDir.resolve("blank.pdf");
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage(PDRectangle.A4));
            }
            document.save(path.toFile());
        }
        return path;
    }

    private static class StubOcr implements OcrEngine {
        private final boolean enabled;
        private final String result;
        final AtomicInteger calls = new AtomicInteger();

        StubOcr(boolean enabled, String result) {
            this.enabled = enabled;
            this.result = result;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public String recognize(BufferedImage image) {
            calls.incrementAndGet();
            return result;
        }
    }

    @Test
    void extractsTextFromTextBasedPdfWithoutOcr() throws IOException {
        StubOcr ocr = new StubOcr(true, "should not be used");
        PdfTextExtractor extractor = new PdfTextExtractor(ocr, properties(true));

        List<ExtractedText> pages = extractor.extract(textPdf("Employees receive 15 days of annual leave."));

        assertEquals(1, pages.size());
        assertEquals(1, pages.get(0).page());
        assertTrue(pages.get(0).text().contains("15 days"));
        assertEquals(0, ocr.calls.get());
    }

    @Test
    void fillsSparsePagesWithOcrWhenEnabled() throws IOException {
        StubOcr ocr = new StubOcr(true, "Nhan vien duoc nghi phep 15 ngay moi nam.");
        PdfTextExtractor extractor = new PdfTextExtractor(ocr, properties(true));

        List<ExtractedText> pages = extractor.extract(blankPdf(1));

        assertEquals(1, pages.size());
        assertEquals(1, pages.get(0).page());
        assertTrue(pages.get(0).text().contains("15 ngay"));
        assertEquals(1, ocr.calls.get());
    }

    @Test
    void skipsOcrWhenDisabledAndReturnsEmptyForScannedPdf() throws IOException {
        StubOcr ocr = new StubOcr(false, "should not be used");
        PdfTextExtractor extractor = new PdfTextExtractor(ocr, properties(false));

        List<ExtractedText> pages = extractor.extract(blankPdf(2));

        assertTrue(pages.isEmpty());
        assertEquals(0, ocr.calls.get());
    }

    @Test
    void keepsOcrResultOnlyWhenLongerThanNativeText() throws IOException {
        StubOcr ocr = new StubOcr(true, "Nhan vien duoc nghi phep 15 ngay moi nam sau khi thu viec.");
        PdfTextExtractor extractor = new PdfTextExtractor(ocr, properties(true));

        Path path = tempDir.resolve("sparse.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("p. 3");
                content.endText();
            }
            document.save(path.toFile());
        }

        List<ExtractedText> pages = extractor.extract(path);

        assertEquals(1, pages.size());
        assertTrue(pages.get(0).text().contains("15 ngay"));
        assertEquals(1, ocr.calls.get());
    }

    @Test
    void honorsOcrPageBudget() throws IOException {
        StubOcr ocr = new StubOcr(true, "OCR text for the scanned page.");
        AppProperties properties = new AppProperties(
                null, null, null, null, null,
                new AppProperties.Ocr(true, "/nonexistent", "vie+eng", 300, 2, 10),
                null,
                null,
                null
        );
        PdfTextExtractor extractor = new PdfTextExtractor(ocr, properties);

        List<ExtractedText> pages = extractor.extract(blankPdf(4));

        assertEquals(2, pages.size());
        assertEquals(2, ocr.calls.get());
    }
}
