package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.config.AppProperties;
import com.example.aidocumentassistant.storage.Storage;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageAssetExtractorTest {

    @TempDir
    Path tempDir;

    /** Minimal in-memory {@link Storage} so the extractor test does not touch the filesystem. */
    private static final class InMemoryStorage implements Storage {
        private final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public String store(String key, byte[] bytes, String contentType) {
            objects.put(key, bytes);
            return key;
        }

        @Override
        public InputStream openRead(String key) {
            return new ByteArrayInputStream(readAll(key));
        }

        @Override
        public Path materialize(String key) throws IOException {
            Path temp = Files.createTempFile("storage-", ".bin");
            Files.write(temp, readAll(key));
            temp.toFile().deleteOnExit();
            return temp;
        }

        @Override
        public void copyTo(String key, Path target) throws IOException {
            Files.createDirectories(target.getParent());
            Files.write(target, readAll(key));
        }

        @Override
        public byte[] readAll(String key) {
            byte[] bytes = objects.get(key);
            if (bytes == null) {
                throw new IllegalArgumentException("No object stored under key: " + key);
            }
            return bytes;
        }

        @Override
        public void writeTo(String key, OutputStream out) throws IOException {
            out.write(readAll(key));
        }

        @Override
        public boolean exists(String key) {
            return objects.containsKey(key);
        }

        @Override
        public long size(String key) {
            byte[] bytes = objects.get(key);
            return bytes == null ? -1 : bytes.length;
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    private static AppProperties properties(boolean enabled, int minPixelSize) {
        return new AppProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                new AppProperties.Images(enabled, minPixelSize, 30),
                null,
                null
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
        InMemoryStorage storage = new InMemoryStorage();
        ImageAssetExtractor extractor = new ImageAssetExtractor(engine("Architecture diagram"), properties(true, 64), storage);
        Document document = new Document(null, "with-image.pdf", 0, "application/pdf", "x");

        List<ImageAsset> assets = extractor.extractFromPdf(document, pdfWithEmbeddedImage(1), storage);

        assertEquals(1, assets.size());
        ImageAsset asset = assets.get(0);
        assertEquals(1, asset.getPage());
        assertEquals(120, asset.getWidth());
        assertEquals(80, asset.getHeight());
        assertEquals("image/png", asset.getContentType());
        assertEquals("Architecture diagram", asset.getOcrText());
        assertTrue(storage.exists(asset.getStoragePath()));
    }

    @Test
    void deduplicatesRepeatedImagesOnTheSamePage() throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        ImageAssetExtractor extractor = new ImageAssetExtractor(engine("caption"), properties(true, 64), storage);

        List<ImageAsset> assets = extractor.extractFromPdf(
                new Document(null, "dup.pdf", 0, "application/pdf", "x"),
                pdfWithEmbeddedImage(3),
                storage);

        assertEquals(1, assets.size());
    }

    @Test
    void skipsImagesBelowMinPixelSize() throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        ImageAssetExtractor extractor = new ImageAssetExtractor(engine("caption"), properties(true, 64), storage);

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
                new Document(null, "tiny.pdf", 0, "application/pdf", "x"), path, storage);

        assertTrue(assets.isEmpty());
    }

    @Test
    void standaloneUploadCreatesAssetWithCaption() throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        ImageAssetExtractor extractor = new ImageAssetExtractor(engine("ignored"), properties(true, 64), storage);
        Path png = tempDir.resolve("scan.png");
        ImageIO.write(sampleImage(200, 100, Color.WHITE), "png", png.toFile());
        Document document = new Document(null, "scan.png", Files.size(png), "image/png", png.toString());

        List<ImageAsset> assets = extractor.fromStandaloneUpload(
                document, png, storage, "25 days of paid annual leave");

        assertEquals(1, assets.size());
        ImageAsset asset = assets.get(0);
        assertEquals(1, asset.getPage());
        assertEquals(200, asset.getWidth());
        assertEquals(100, asset.getHeight());
        assertEquals("image/png", asset.getContentType());
        assertEquals("25 days of paid annual leave", asset.getOcrText());
        assertTrue(storage.exists(asset.getStoragePath()));
    }

    @Test
    void disabledConfigProducesNoAssets() throws IOException {
        InMemoryStorage storage = new InMemoryStorage();
        ImageAssetExtractor extractor = new ImageAssetExtractor(engine("caption"), properties(false, 64), storage);

        assertTrue(extractor.extractFromPdf(
                new Document(null, "x.pdf", 0, "application/pdf", "x"),
                pdfWithEmbeddedImage(1),
                storage).isEmpty());
    }
}
