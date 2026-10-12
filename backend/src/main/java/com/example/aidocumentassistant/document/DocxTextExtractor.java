package com.example.aidocumentassistant.document;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class DocxTextExtractor {
    public List<ExtractedText> extract(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = normalize(extractor.getText());
            return text.isBlank() ? List.of() : List.of(new ExtractedText(1, text));
        }
    }

    private String normalize(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
