package com.example.aidocumentassistant.document;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class MarkdownTableExtractor {
    private static final Pattern SEPARATOR_ROW =
            Pattern.compile("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)+\\|?\\s*$");

    public List<TableExtractor.TableData> extract(String content) {
        List<TableExtractor.TableData> tables = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return tables;
        }
        String[] lines = content.split("\n", -1);
        int index = 0;
        while (index < lines.length - 1) {
            if (!looksLikeRow(lines[index]) || !SEPARATOR_ROW.matcher(lines[index + 1]).matches()) {
                index++;
                continue;
            }
            List<String> headerCells = splitCells(lines[index]);
            if (headerCells.size() < 2) {
                index++;
                continue;
            }
            List<List<String>> body = new ArrayList<>();
            int cursor = index + 2;
            while (cursor < lines.length && looksLikeRow(lines[cursor])) {
                List<String> cells = splitCells(lines[cursor]);
                if (cells.size() != headerCells.size()) {
                    break;
                }
                body.add(cells);
                cursor++;
            }
            if (!body.isEmpty()) {
                tables.add(new TableExtractor.TableData(1, toMarkdown(headerCells, body), body.size()));
            }
            index = Math.max(cursor, index + 1);
        }
        return tables;
    }

    private boolean looksLikeRow(String line) {
        return line != null && line.contains("|") && !line.isBlank();
    }

    private List<String> splitCells(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|") && !trimmed.endsWith("\\|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String part : trimmed.split("(?<!\\\\)\\|", -1)) {
            cells.add(part.trim().replace("\\|", "|"));
        }
        while (!cells.isEmpty() && cells.get(cells.size() - 1).isEmpty()) {
            cells.remove(cells.size() - 1);
        }
        return cells;
    }

    private String toMarkdown(List<String> header, List<List<String>> body) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("| ").append(String.join(" | ", header)).append(" |\n");
        markdown.append("|").append(" --- |".repeat(header.size())).append("\n");
        for (List<String> row : body) {
            markdown.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
        return markdown.toString().stripTrailing();
    }
}
