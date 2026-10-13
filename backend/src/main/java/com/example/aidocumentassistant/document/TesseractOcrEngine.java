package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.config.AppProperties;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TesseractOcrEngine implements OcrEngine {
    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);

    private final AppProperties.Ocr config;
    private final AtomicBoolean broken = new AtomicBoolean(false);
    private final Object lock = new Object();
    private Tesseract tesseract;

    public TesseractOcrEngine(AppProperties properties) {
        this.config = properties.ocr();
    }

    @Override
    public boolean enabled() {
        return config != null && config.enabled() && !broken.get();
    }

    @Override
    public String recognize(BufferedImage image) {
        if (!enabled() || image == null) {
            return "";
        }
        synchronized (lock) {
            try {
                if (tesseract == null) {
                    tesseract = createTesseract();
                }
                return tesseract.doOCR(image);
            } catch (UnsatisfiedLinkError | RuntimeException | NoClassDefFoundError | TesseractException exception) {
                broken.set(true);
                log.warn("OCR disabled after failure: {}", exception.getMessage());
                return "";
            }
        }
    }

    private Tesseract createTesseract() {
        if (config.tessdataDir() != null && !config.tessdataDir().isBlank()) {
            Path tessdata = Path.of(config.tessdataDir());
            if (!Files.isDirectory(tessdata)) {
                throw new IllegalStateException("Tessdata directory not found: " + tessdata);
            }
        }
        Tesseract instance = new Tesseract();
        if (config.tessdataDir() != null && !config.tessdataDir().isBlank()) {
            instance.setDatapath(config.tessdataDir());
        }
        instance.setLanguage(config.language());
        return instance;
    }
}
