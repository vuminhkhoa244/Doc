package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.config.AppProperties;
import com.example.aidocumentassistant.storage.Storage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class ImageAssetExtractor {
    private static final Logger log = LoggerFactory.getLogger(ImageAssetExtractor.class);

    private final OcrEngine ocrEngine;
    private final AppProperties properties;
    private final Storage storage;

    public ImageAssetExtractor(OcrEngine ocrEngine, AppProperties properties, Storage storage) {
        this.ocrEngine = ocrEngine;
        this.properties = properties;
        this.storage = storage;
    }

    public List<ImageAsset> extractFromPdf(Document document, Path pdfPath, Storage storage) {
        AppProperties.Images config = properties.images();
        if (config == null || !config.enabled()) {
            return List.of();
        }
        List<ImageAsset> assets = new ArrayList<>();
        try {
            try (PDDocument pdf = Loader.loadPDF(pdfPath.toFile())) {
                Set<String> seenHashes = new LinkedHashSet<>();
                for (int pageIndex = 0; pageIndex < pdf.getNumberOfPages(); pageIndex++) {
                    PDPage page = pdf.getPage(pageIndex);
                    for (org.apache.pdfbox.cos.COSName name : page.getResources().getXObjectNames()) {
                        if (assets.size() >= config.maxPerDocument()) {
                            return assets;
                        }
                        PDXObject xObject;
                        try {
                            xObject = page.getResources().getXObject(name);
                        } catch (IOException exception) {
                            continue;
                        }
                        if (!(xObject instanceof PDImageXObject image)) {
                            continue;
                        }
                        ImageAsset asset = extractOne(document, image, pageIndex + 1, storage, config, seenHashes);
                        if (asset != null) {
                            assets.add(asset);
                        }
                    }
                }
            }
        } catch (IOException exception) {
            log.warn("Embedded image extraction failed for {}: {}", document.getFilename(), exception.getMessage());
        }
        return assets;
    }

    public List<ImageAsset> fromStandaloneUpload(Document document, Path file, Storage storage, String ocrText) {
        AppProperties.Images config = properties.images();
        if (config == null || !config.enabled()) {
            return List.of();
        }
        try {
            int width = 0;
            int height = 0;
            try {
                BufferedImage image = ImageIO.read(file.toFile());
                if (image != null) {
                    width = image.getWidth();
                    height = image.getHeight();
                }
            } catch (IOException ignored) {
            }
            String key = "assets/" + UUID.randomUUID() + extensionFor(document.getContentType());
            byte[] bytes = java.nio.file.Files.readAllBytes(file);
            storage.store(key, bytes, document.getContentType());
            return List.of(new ImageAsset(
                    document,
                    1,
                    key,
                    document.getContentType(),
                    width,
                    height,
                    bytes.length,
                    sha256(bytes),
                    truncate(ocrText)
            ));
        } catch (IOException exception) {
            log.warn("Standalone image asset failed for {}: {}", document.getFilename(), exception.getMessage());
            return List.of();
        }
    }

    private ImageAsset extractOne(
            Document document,
            PDImageXObject image,
            int page,
            Storage storage,
            AppProperties.Images config,
            Set<String> seenHashes
    ) {
        try {
            if (image.getWidth() < config.minPixelSize() || image.getHeight() < config.minPixelSize()) {
                return null;
            }
            byte[] rawStream;
            try (InputStream stream = image.getCOSObject().createInputStream()) {
                rawStream = stream.readAllBytes();
            }
            String hash = sha256(rawStream);
            if (!seenHashes.add(hash)) {
                return null;
            }
            BufferedImage buffered = image.getImage();
            if (buffered == null) {
                return null;
            }
            String key = "assets/" + UUID.randomUUID() + ".png";
            ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
            ImageIO.write(toRgb(buffered), "png", pngBytes);
            byte[] bytes = pngBytes.toByteArray();
            storage.store(key, bytes, "image/png");
            String caption = "";
            if (ocrEngine.enabled()) {
                caption = normalizeCaption(ocrEngine.recognize(buffered));
            }
            return new ImageAsset(
                    document,
                    page,
                    key,
                    "image/png",
                    buffered.getWidth(),
                    buffered.getHeight(),
                    bytes.length,
                    hash,
                    truncate(caption)
            );
        } catch (Exception exception) {
            log.debug("Skipping embedded image on page {}: {}", page, exception.getMessage());
            return null;
        }
    }

    private BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    private String normalizeCaption(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\r\\n?", "\n").replaceAll("[ \\t\\u000B\\f]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String truncate(String text) {
        if (text == null || text.length() <= 4000) {
            return text;
        }
        return text.substring(0, 4000);
    }

    private String extensionFor(String contentType) {
        if (contentType == null) {
            return ".bin";
        }
        return switch (contentType.toLowerCase(java.util.Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/tiff" -> ".tiff";
            case "image/bmp" -> ".bmp";
            default -> ".bin";
        };
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
