package com.example.aidocumentassistant.document;

import java.awt.image.BufferedImage;

public interface OcrEngine {
    boolean enabled();

    String recognize(BufferedImage image);
}
