package com.example.aidocumentassistant.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfTextExtractor {
    public List<ExtractedText> extract(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ExtractedText> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                if (!text.isBlank()) {
                    pages.add(new ExtractedText(page, text));
                }
            }
            return pages;
        }
    }

    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
