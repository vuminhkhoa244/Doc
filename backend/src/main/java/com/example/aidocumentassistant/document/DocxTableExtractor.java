package com.example.aidocumentassistant.document;

import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class DocxTableExtractor {

    public List<TableExtractor.TableData> extract(Path path) throws IOException {
        List<TableExtractor.TableData> tables = new ArrayList<>();
        try (InputStream input = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(input)) {
            for (XWPFTable table : document.getTables()) {
                List<List<String>> rows = new ArrayList<>();
                table.getRows().forEach(row -> {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        cells.add(escapeCell(cell.getText().replaceAll("\\s+", " ").trim()));
                    }
                    rows.add(cells);
                });
                if (rows.size() < 2 || rows.get(0).size() < 2) {
                    continue;
                }
                for (List<String> row : rows) {
                    while (row.size() < rows.get(0).size()) {
                        row.add("");
                    }
                }
                tables.add(new TableExtractor.TableData(1, toMarkdown(rows), rows.size() - 1));
            }
        }
        return tables;
    }

    private String escapeCell(String text) {
        return text.replace("|", "\\|");
    }

    private String toMarkdown(List<List<String>> rows) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("| ").append(String.join(" | ", rows.get(0))).append(" |\n");
        markdown.append("|").append(" --- |".repeat(rows.get(0).size())).append("\n");
        for (int index = 1; index < rows.size(); index++) {
            markdown.append("| ").append(String.join(" | ", rows.get(index))).append(" |\n");
        }
        return markdown.toString().stripTrailing();
    }
}
