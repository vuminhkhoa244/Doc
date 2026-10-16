package com.example.aidocumentassistant.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class ImageTextExtractor {
    private static final Logger log = LoggerFactory.getLogger(ImageTextExtractor.class);

    private final OcrEngine ocrEngine;

    public ImageTextExtractor(OcrEngine ocrEngine) {
        this.ocrEngine = ocrEngine;
    }

    public List<ExtractedText> extract(Path path) throws IOException {
        if (!ocrEngine.enabled()) {
            log.warn("OCR unavailable; cannot extract text from {}", path.getFileName());
            return List.of();
        }

        List<ExtractedText> pages = new ArrayList<>();
        int pageNumber = 1;
        for (BufferedImage frame : readFrames(path)) {
            String text = normalize(ocrEngine.recognize(frame));
            if (!text.isBlank()) {
                pages.add(new ExtractedText(pageNumber, text));
            }
            pageNumber++;
        }
        return pages;
    }

    private List<BufferedImage> readFrames(Path path) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(path.toFile())) {
            if (stream == null) {
                throw new IOException("Unsupported image format: " + path.getFileName());
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format: " + path.getFileName());
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int frames = 1;
                try {
                    frames = reader.getNumImages(true);
                } catch (IOException exception) {
                    frames = 1;
                }
                List<BufferedImage> images = new ArrayList<>(frames);
                for (int index = 0; index < frames; index++) {
                    images.add(reader.read(index));
                }
                return images;
            } finally {
                reader.dispose();
            }
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("\\r\\n?", "\n")
                .replaceAll("[ \\t\\u000B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
