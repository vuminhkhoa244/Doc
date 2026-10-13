package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TesseractOcrEngineTest {

    private static AppProperties properties(boolean enabled, String tessdataDir) {
        return new AppProperties(
                null,
                null,
                null,
                null,
                null,
                new AppProperties.Ocr(enabled, tessdataDir, "vie+eng", 300, 50, 10)
        );
    }

    @Test
    void disabledByConfig() {
        TesseractOcrEngine engine = new TesseractOcrEngine(properties(false, "/nonexistent"));

        assertFalse(engine.enabled());
        assertEquals("", engine.recognize(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)));
    }

    @Test
    void marksUnavailableWhenTessdataMissing() {
        TesseractOcrEngine engine = new TesseractOcrEngine(properties(true, "/nonexistent-tessdata-dir"));

        assertEquals("", engine.recognize(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)));
        assertFalse(engine.enabled());
        assertEquals("", engine.recognize(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)));
    }
}
