package com.example.aidocumentassistant.document;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {
    private static final int MAX_CHARS = 1200;
    private static final int OVERLAP_CHARS = 160;

    public List<ChunkText> split(List<ExtractedText> pages) {
        List<ChunkText> chunks = new ArrayList<>();
        for (ExtractedText page : pages) {
            int start = 0;
            while (start < page.text().length()) {
                int end = Math.min(page.text().length(), start + MAX_CHARS);
                int adjustedEnd = adjustEnd(page.text(), start, end);
                String text = page.text().substring(start, adjustedEnd).trim();
                if (!text.isBlank()) {
                    chunks.add(new ChunkText(page.page(), text));
                }
                if (adjustedEnd >= page.text().length()) {
                    break;
                }
                start = Math.max(adjustedEnd - OVERLAP_CHARS, start + 1);
            }
        }
        return chunks;
    }

    private int adjustEnd(String text, int start, int end) {
        if (end >= text.length()) {
            return text.length();
        }
        int sentenceEnd = Math.max(text.lastIndexOf(". ", end), Math.max(text.lastIndexOf("? ", end), text.lastIndexOf("! ", end)));
        if (sentenceEnd > start + MAX_CHARS / 2) {
            return sentenceEnd + 1;
        }
        int wordEnd = text.lastIndexOf(' ', end);
        if (wordEnd > start + MAX_CHARS / 2) {
            return wordEnd;
        }
        return end;
    }

    public record ChunkText(int page, String text) {
    }
}
