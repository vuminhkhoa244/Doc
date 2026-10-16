package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.config.AppProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageAssetExtractorTest {

    @TempDir
    Path tempDir;

    private static AppProperties properties(boolean enabled, int minPixelSize) {
        return new AppProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                new AppProperties.Images(enabled, minPixelSize, 30)
        );
    }

    private static OcrEngine engine(String caption) {
        return new OcrEngine() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String recognize(BufferedImage image) {
                return caption;
            }
        };
    }

    private static BufferedImage sampleImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private Path pdfWithEmbeddedImage(int drawCount) throws IOException {
        Path path = tempDir.resolve("with-image.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(document, sampleImage(120, 80, Color.BLUE));
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (int index = 0; index < drawCount; index++) {
                    content.drawImage(image, 72 + index * 130, 500, 120, 80);
                }
            }
            document.save(path.toFile());
        }
        return path;
    }

    @Test
    void extractsEmbeddedImagesWithOcrCaption() throws IOException {
        ImageAssetExtractor extractor = new ImageAssetExtractor(engine("Architecture diagram"), properties(true, 64));
        Document document = new Document(null, "with-image.pdf", 0, "application/pdf", "x");
        Path assetsDir = tempDir.resolve("assets");

        List<ImageAsset> assets = extractor.extractFromPdf(document, pdfWithEmbeddedImage(1), assetsDir);

        assertEquals(1, assets.size());
        ImageAsset asset = assets.get(0);
        assertEquals(1, asset.getPage());
        assertEquals(120, asset.getWidth());
        assertEquals(80, asset.getHeight());
        assertEquals("image/png", asset.getContentType());
        assertEquals("Architecture diagram", asset.getOcrText());
        assertTrue(Files.exists(Path.of(asset.getStoragePath())));
    }

    @Test
    void deduplicatesRepeatedImagesOnTheSamePage() throws IOException {
        ImageAssetExtractor extractor = new ImageAssetExtractor(engine("caption"), properties(true, 64));

        List<ImageAsset> assets = extractor.extractFromPdf(
                new Document(null, "dup.pdf", 0, "application/pdf", "x"),
                pdfWithEmbeddedImage(3),
                tempDir.resolve("assets"));

        assertEquals(1, assets.size());
    }

    @Test
    void skipsImagesBelowMinPixelSize() throws IOException {
        ImageAssetExtractor extractor = new ImageAssetExtractor(engine("caption"), properties(true, 64));

        Path path = tempDir.resolve("tiny.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(document, sampleImage(32, 32, Color.RED));
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 72, 500, 32, 32);
            }
            document.save(path.toFile());
        }

        List<ImageAsset> assets = extractor.extractFromPdf(
                new Document(null, "tiny.pdf", 0, "application/pdf", "x"), path, tempDir.resolve("assets"));

        assertTrue(assets.isEmpty());
    }

    @Test
    void standaloneUploadCreatesAssetWithCaption() throws IOException {
        ImageAssetExtractor extractor = new ImageAssetExtractor(engine("ignored"), properties(true, 64));
        Path png = tempDir.resolve("scan.png");
        ImageIO.write(sampleImage(200, 100, Color.WHITE), "png", png.toFile());
        Document document = new Document(null, "scan.png", Files.size(png), "image/png", png.toString());

        List<ImageAsset> assets = extractor.fromStandaloneUpload(
                document, png, tempDir.resolve("assets"), "25 days of paid annual leave");

        assertEquals(1, assets.size());
        ImageAsset asset = assets.get(0);
        assertEquals(1, asset.getPage());
        assertEquals(200, asset.getWidth());
        assertEquals(100, asset.getHeight());
        assertEquals("image/png", asset.getContentType());
        assertEquals("25 days of paid annual leave", asset.getOcrText());
        assertTrue(Files.exists(Path.of(asset.getStoragePath())));
    }

    @Test
    void disabledConfigProducesNoAssets() throws IOException {
        ImageAssetExtractor extractor = new ImageAssetExtractor(engine("caption"), properties(false, 64));

        assertTrue(extractor.extractFromPdf(
                new Document(null, "x.pdf", 0, "application/pdf", "x"),
                pdfWithEmbeddedImage(1),
                tempDir.resolve("assets")).isEmpty());
    }
}
