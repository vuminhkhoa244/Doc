package com.example.aidocumentassistant.document;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageTextExtractorTest {

    @TempDir
    Path tempDir;

    private static OcrEngine engine(boolean enabled, String recognized) {
        return new OcrEngine() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public String recognize(BufferedImage image) {
                return recognized;
            }
        };
    }

    private Path writePng(String name) throws IOException {
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 40, 20);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        Path path = tempDir.resolve(name);
        Files.write(path, bytes.toByteArray());
        return path;
    }

    @Test
    void extractsOcrTextAsFirstPage() throws IOException {
        ImageTextExtractor extractor = new ImageTextExtractor(engine(true, "Company policy grants 20 days of leave."));

        List<ExtractedText> pages = extractor.extract(writePng("policy-scan.png"));

        assertEquals(1, pages.size());
        assertEquals(1, pages.get(0).page());
        assertTrue(pages.get(0).text().contains("20 days of leave"));
    }

    @Test
    void returnsEmptyWhenOcrDisabled() throws IOException {
        ImageTextExtractor extractor = new ImageTextExtractor(engine(false, "never called"));

        assertTrue(extractor.extract(writePng("policy-scan.png")).isEmpty());
    }

    @Test
    void returnsEmptyWhenRecognitionIsBlank() throws IOException {
        ImageTextExtractor extractor = new ImageTextExtractor(engine(true, "  \n\t "));

        assertTrue(extractor.extract(writePng("blank-scan.png")).isEmpty());
    }

    @Test
    void normalizesOcrWhitespaceButKeepsLineBreaks() throws IOException {
        ImageTextExtractor extractor = new ImageTextExtractor(engine(true, "line   one\r\n\r\n\r\n\r\nline two"));

        List<ExtractedText> pages = extractor.extract(writePng("multi.png"));

        assertEquals(1, pages.size());
        assertEquals("line one\n\nline two", pages.get(0).text());
    }

    @Test
    void rejectsUnreadableFiles() {
        ImageTextExtractor extractor = new ImageTextExtractor(engine(true, "text"));
        Path notAnImage = tempDir.resolve("fake.png");
        try {
            Files.writeString(notAnImage, "this is not an image");
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
        assertThrows(IOException.class, () -> extractor.extract(notAnImage));
    }

    @Test
    void documentTypeDetectsImages() {
        assertEquals(DocumentTextExtractor.DocumentType.IMAGE,
                DocumentTextExtractor.DocumentType.from("scan.PNG", null));
        assertEquals(DocumentTextExtractor.DocumentType.IMAGE,
                DocumentTextExtractor.DocumentType.from("photo.jpeg", null));
        assertEquals(DocumentTextExtractor.DocumentType.IMAGE,
                DocumentTextExtractor.DocumentType.from("page.tiff", null));
        assertEquals(DocumentTextExtractor.DocumentType.IMAGE,
                DocumentTextExtractor.DocumentType.from("whatever.bin", "image/webp"));
        assertThrows(IllegalArgumentException.class,
                () -> DocumentTextExtractor.DocumentType.from("archive.zip", null));
    }
}
