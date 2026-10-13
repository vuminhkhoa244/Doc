package com.example.aidocumentassistant.document;

import com.example.aidocumentassistant.config.AppProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfTextExtractor {
    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    private final OcrEngine ocrEngine;
    private final AppProperties.Ocr ocrConfig;

    public PdfTextExtractor(OcrEngine ocrEngine, AppProperties properties) {
        this.ocrEngine = ocrEngine;
        this.ocrConfig = properties.ocr();
    }

    public List<ExtractedText> extract(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            List<ExtractedText> pages = new ArrayList<>();
            int ocrPagesDone = 0;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));

                if (text.length() < ocrConfig.sparseMinChars() && shouldOcr(ocrPagesDone)) {
                    ocrPagesDone++;
                    String ocrText = normalize(ocrPage(renderer, page - 1));
                    if (ocrText.length() > text.length()) {
                        text = ocrText;
                    }
                }

                if (!text.isBlank()) {
                    pages.add(new ExtractedText(page, text));
                }
            }
            if (ocrPagesDone > 0) {
                log.info("OCR completed for {} page(s) of {}", ocrPagesDone, path.getFileName());
            }
            return pages;
        }
    }

    private boolean shouldOcr(int ocrPagesDone) {
        return ocrEngine.enabled() && ocrPagesDone < ocrConfig.maxPages();
    }

    private String ocrPage(PDFRenderer renderer, int zeroBasedPage) {
        try {
            BufferedImage image = renderer.renderImageWithDPI(zeroBasedPage, ocrConfig.dpi(), ImageType.RGB);
            return ocrEngine.recognize(image);
        } catch (IOException | RuntimeException exception) {
            log.warn("OCR failed for page {}: {}", zeroBasedPage + 1, exception.getMessage());
            return "";
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
