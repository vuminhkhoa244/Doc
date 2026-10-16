package com.example.aidocumentassistant.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.contentstream.operator.graphics.AppendRectangleToPath;
import org.apache.pdfbox.contentstream.operator.graphics.CloseAndStrokePath;
import org.apache.pdfbox.contentstream.operator.graphics.CloseFillEvenOddAndStrokePath;
import org.apache.pdfbox.contentstream.operator.graphics.CloseFillNonZeroAndStrokePath;
import org.apache.pdfbox.contentstream.operator.graphics.CurveTo;
import org.apache.pdfbox.contentstream.operator.graphics.FillEvenOddAndStrokePath;
import org.apache.pdfbox.contentstream.operator.graphics.FillEvenOddRule;
import org.apache.pdfbox.contentstream.operator.graphics.FillNonZeroAndStrokePath;
import org.apache.pdfbox.contentstream.operator.graphics.FillNonZeroRule;
import org.apache.pdfbox.contentstream.operator.graphics.LineTo;
import org.apache.pdfbox.contentstream.operator.graphics.MoveTo;
import org.apache.pdfbox.contentstream.operator.graphics.StrokePath;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class PdfRuledTableExtractor {
    private static final float LINE_TOLERANCE = 1.5f;
    private static final float MIN_CELL_SIZE = 12f;
    private static final float MAX_CELL_SIZE = 300f;
    private static final float COVERAGE_RATIO = 0.85f;
    private static final int MAX_TABLES_PER_PAGE = 20;

    public List<TableExtractor.TableData> extract(Path path) throws IOException {
        List<TableExtractor.TableData> tables = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(path.toFile())) {
            for (int pageIndex = 0; pageIndex < pdf.getNumberOfPages(); pageIndex++) {
                PDPage page = pdf.getPage(pageIndex);
                List<Segment> segments = collectSegments(page);
                if (segments.isEmpty()) {
                    continue;
                }
                List<CharPos> chars = collectChars(pdf, pageIndex);
                if (chars.isEmpty()) {
                    continue;
                }
                tables.addAll(detectTables(pageIndex + 1, segments, chars));
                if (tables.size() >= MAX_TABLES_PER_PAGE) {
                    break;
                }
            }
        }
        return tables;
    }

    private List<Segment> collectSegments(PDPage page) throws IOException {
        SegmentCollector collector = new SegmentCollector(page);
        collector.processPage(page);
        return collector.segments();
    }

    private List<CharPos> collectChars(PDDocument pdf, int pageIndex) throws IOException {
        PageCharCollector collector = new PageCharCollector();
        collector.setSortByPosition(true);
        collector.setStartPage(pageIndex + 1);
        collector.setEndPage(pageIndex + 1);
        collector.getText(pdf);
        return collector.chars();
    }

    private List<TableExtractor.TableData> detectTables(int page, List<Segment> segments, List<CharPos> chars) {
        List<Float> horizontalLines = mergeLines(segments, true);
        List<Float> verticalLines = mergeLines(segments, false);
        if (horizontalLines.size() < 2 || verticalLines.size() < 2) {
            return List.of();
        }
        horizontalLines.sort(Comparator.reverseOrder());
        verticalLines.sort(Comparator.naturalOrder());

        List<float[]> rectangles = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int topIndex = 0; topIndex < horizontalLines.size() - 1; topIndex++) {
            float top = horizontalLines.get(topIndex);
            for (int bottomIndex = topIndex + 1; bottomIndex < horizontalLines.size(); bottomIndex++) {
                float bottom = horizontalLines.get(bottomIndex);
                if (top - bottom < MIN_CELL_SIZE) {
                    continue;
                }
                if (bottomIndex > topIndex + 1 && horizontalLines.get(bottomIndex - 1) - bottom > MAX_CELL_SIZE) {
                    break;
                }
                for (int leftIndex = 0; leftIndex < verticalLines.size() - 1; leftIndex++) {
                    float left = verticalLines.get(leftIndex);
                    for (int rightIndex = leftIndex + 1; rightIndex < verticalLines.size(); rightIndex++) {
                        float right = verticalLines.get(rightIndex);
                        if (right - left < MIN_CELL_SIZE) {
                            continue;
                        }
                        if (rightIndex > leftIndex + 1 && right - verticalLines.get(rightIndex - 1) > MAX_CELL_SIZE) {
                            break;
                        }
                        if (!hasCoverage(segments, true, top, left, right)
                                || !hasCoverage(segments, true, bottom, left, right)
                                || !hasCoverage(segments, false, left, bottom, top)
                                || !hasCoverage(segments, false, right, bottom, top)) {
                            continue;
                        }
                        String key = Math.round(top) + ":" + Math.round(bottom) + ":" + Math.round(left) + ":" + Math.round(right);
                        if (seen.add(key)) {
                            rectangles.add(new float[]{top, bottom, left, right});
                        }
                    }
                }
            }
        }

        List<float[]> maximal = new ArrayList<>();
        for (float[] candidate : rectangles) {
            boolean contained = false;
            for (float[] other : rectangles) {
                if (other != candidate && contains(other, candidate)) {
                    contained = true;
                    break;
                }
            }
            if (!contained) {
                maximal.add(candidate);
            }
        }
        maximal.sort(Comparator.comparingDouble(r -> -r[0]));

        List<TableExtractor.TableData> tables = new ArrayList<>();
        for (float[] rect : maximal) {
            TableExtractor.TableData table = buildTable(page, rect, horizontalLines, verticalLines, chars);
            if (table != null) {
                tables.add(table);
            }
        }
        return tables;
    }

    private boolean hasCoverage(List<Segment> segments, boolean horizontal, float line, float from, float to) {
        float needed = to - from;
        if (needed <= 0) {
            return false;
        }
        float covered = 0;
        for (Segment segment : segments) {
            if (horizontal != segment.horizontal()) {
                continue;
            }
            float position = horizontal ? segment.y1() : segment.x1();
            if (Math.abs(position - line) > LINE_TOLERANCE) {
                continue;
            }
            float start = horizontal ? Math.min(segment.x1(), segment.x2()) : Math.min(segment.y1(), segment.y2());
            float end = horizontal ? Math.max(segment.x1(), segment.x2()) : Math.max(segment.y1(), segment.y2());
            float overlap = Math.min(end, to) - Math.max(start, from);
            if (overlap > 0) {
                covered += overlap;
            }
        }
        return covered >= needed * COVERAGE_RATIO;
    }

    private boolean contains(float[] outer, float[] inner) {
        return inner[0] <= outer[0] + LINE_TOLERANCE && inner[1] >= outer[1] - LINE_TOLERANCE
                && inner[2] >= outer[2] - LINE_TOLERANCE && inner[3] <= outer[3] + LINE_TOLERANCE
                && !(inner[0] == outer[0] && inner[1] == outer[1] && inner[2] == outer[2] && inner[3] == outer[3]);
    }

    private List<Float> mergeLines(List<Segment> segments, boolean horizontal) {
        List<float[]> clusters = new ArrayList<>();
        for (Segment segment : segments) {
            if (segment.horizontal() != horizontal) {
                continue;
            }
            if (Math.abs(horizontal ? segment.y1() - segment.y2() : segment.x1() - segment.x2()) >= 0.6f) {
                continue;
            }
            float position = horizontal ? segment.y1() : segment.x1();
            boolean added = false;
            for (float[] cluster : clusters) {
                if (Math.abs(cluster[0] / cluster[1] - position) <= LINE_TOLERANCE) {
                    cluster[0] += position;
                    cluster[1]++;
                    added = true;
                    break;
                }
            }
            if (!added) {
                clusters.add(new float[]{position, 1});
            }
        }
        List<Float> merged = new ArrayList<>();
        for (float[] cluster : clusters) {
            merged.add(cluster[0] / cluster[1]);
        }
        return merged;
    }

    private TableExtractor.TableData buildTable(
            int page,
            float[] rect,
            List<Float> horizontalLines,
            List<Float> verticalLines,
            List<CharPos> chars
    ) {
        float top = rect[0];
        float bottom = rect[1];
        float left = rect[2];
        float right = rect[3];

        List<Float> rowLines = new ArrayList<>();
        rowLines.add(top);
        for (float line : horizontalLines) {
            if (line < top - LINE_TOLERANCE && line > bottom + LINE_TOLERANCE) {
                rowLines.add(line);
            }
        }
        rowLines.add(bottom);
        rowLines.sort(Comparator.reverseOrder());

        List<Float> columnLines = new ArrayList<>();
        columnLines.add(left);
        for (float line : verticalLines) {
            if (line > left + LINE_TOLERANCE && line < right - LINE_TOLERANCE) {
                columnLines.add(line);
            }
        }
        columnLines.add(right);
        columnLines.sort(Comparator.naturalOrder());

        int rowCount = rowLines.size() - 1;
        int columnCount = columnLines.size() - 1;
        if (rowCount < 2 || columnCount < 2) {
            return null;
        }

        List<CharPos>[][] cellChars = new List[rowCount][columnCount];
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                cellChars[rowIndex][columnIndex] = new ArrayList<>();
            }
        }
        int filled = 0;
        for (CharPos charPos : chars) {
            float centerX = charPos.x() + charPos.width() / 2f;
            float y = charPos.y();
            if (centerX < left || centerX > right || y < bottom - 1 || y > top + 1) {
                continue;
            }
            int rowIndex = -1;
            for (int index = 0; index < rowCount; index++) {
                if (y <= rowLines.get(index) + 1 && y > rowLines.get(index + 1) - 1) {
                    rowIndex = index;
                    break;
                }
            }
            int columnIndex = -1;
            for (int index = 0; index < columnCount; index++) {
                if (centerX >= columnLines.get(index) - 1 && centerX <= columnLines.get(index + 1) + 1) {
                    columnIndex = index;
                    break;
                }
            }
            if (rowIndex < 0 || columnIndex < 0) {
                continue;
            }
            if (cellChars[rowIndex][columnIndex].isEmpty()) {
                filled++;
            }
            cellChars[rowIndex][columnIndex].add(charPos);
        }

        if (filled < Math.max(4, (rowCount * columnCount) / 3)) {
            return null;
        }

        String[][] cells = new String[rowCount][columnCount];
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                cells[rowIndex][columnIndex] = renderCell(cellChars[rowIndex][columnIndex]);
            }
        }

        StringBuilder markdown = new StringBuilder();
        markdown.append("| ").append(row(cells, 0, columnCount)).append(" |\n");
        markdown.append("|").append(" --- |".repeat(columnCount)).append("\n");
        for (int rowIndex = 1; rowIndex < rowCount; rowIndex++) {
            markdown.append("| ").append(row(cells, rowIndex, columnCount)).append(" |\n");
        }
        return new TableExtractor.TableData(page, markdown.toString().stripTrailing(), rowCount);
    }

    private String renderCell(List<CharPos> cellChars) {
        if (cellChars.isEmpty()) {
            return "";
        }
        cellChars.sort(Comparator.comparingDouble(CharPos::y).reversed().thenComparingDouble(CharPos::x));
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        float lineY = Float.NaN;
        float previousEnd = Float.NaN;
        for (CharPos charPos : cellChars) {
            if (!Float.isNaN(lineY) && Math.abs(charPos.y() - lineY) > 2.5f) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
                previousEnd = Float.NaN;
            }
            lineY = charPos.y();
            if (!Float.isNaN(previousEnd) && charPos.x() - previousEnd > Math.max(charPos.width(), 1f) * 0.5f) {
                currentLine.append(' ');
            }
            currentLine.append(charPos.unicode());
            previousEnd = charPos.x() + charPos.width();
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return String.join("<br>", lines).replaceAll("\\s+", " ").trim();
    }

    private String row(String[][] cells, int rowIndex, int columnCount) {
        List<String> parts = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            String value = cells[rowIndex][columnIndex] == null ? "" : cells[rowIndex][columnIndex];
            parts.add(value.replace("|", "\\|"));
        }
        return String.join(" | ", parts);
    }

    private record Segment(float x1, float y1, float x2, float y2, boolean horizontal) {
    }

    private record CharPos(float x, float y, float width, String unicode) {
    }

    private static final class SegmentCollector extends PDFGraphicsStreamEngine {
        private final List<Segment> segments = new ArrayList<>();
        private float currentX;
        private float currentY;
        private float startX;
        private float startY;

        SegmentCollector(PDPage page) {
            super(page);
            addOperator(new AppendRectangleToPath(this));
            addOperator(new CloseAndStrokePath(this));
            addOperator(new CloseFillEvenOddAndStrokePath(this));
            addOperator(new CloseFillNonZeroAndStrokePath(this));
            addOperator(new CurveTo(this));
            addOperator(new FillEvenOddAndStrokePath(this));
            addOperator(new FillEvenOddRule(this));
            addOperator(new FillNonZeroAndStrokePath(this));
            addOperator(new FillNonZeroRule(this));
            addOperator(new LineTo(this));
            addOperator(new MoveTo(this));
            addOperator(new StrokePath(this));
            addOperator(new Concatenate(this));
            addOperator(new Save(this));
            addOperator(new Restore(this));
        }

        List<Segment> segments() {
            return segments;
        }

        @Override
        public void appendRectangle(Point2D p1, Point2D p2, Point2D p3, Point2D p4) {
            float x1 = (float) p1.getX();
            float y1 = (float) p1.getY();
            float x3 = (float) p3.getX();
            float y3 = (float) p3.getY();
            float left = Math.min(x1, x3);
            float right = Math.max(x1, x3);
            float bottom = Math.min(y1, y3);
            float top = Math.max(y1, y3);
            addSegment(left, top, right, top);
            addSegment(right, top, right, bottom);
            addSegment(right, bottom, left, bottom);
            addSegment(left, bottom, left, top);
        }

        @Override
        public void drawImage(org.apache.pdfbox.pdmodel.graphics.image.PDImage image) {
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void moveTo(float x, float y) {
            currentX = x;
            currentY = y;
            startX = x;
            startY = y;
        }

        @Override
        public void lineTo(float x, float y) {
            addSegment(currentX, currentY, x, y);
            currentX = x;
            currentY = y;
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            currentX = x3;
            currentY = y3;
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Float(currentX, currentY);
        }

        @Override
        public void closePath() {
            addSegment(currentX, currentY, startX, startY);
            currentX = startX;
            currentY = startY;
        }

        @Override
        public void endPath() {
        }

        @Override
        public void strokePath() {
        }

        @Override
        public void fillPath(int windingRule) {
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
        }

        @Override
        public void shadingFill(org.apache.pdfbox.cos.COSName shadingName) {
        }

        private void addSegment(float x1, float y1, float x2, float y2) {
            boolean horizontal = Math.abs(y1 - y2) < 0.6f;
            boolean vertical = Math.abs(x1 - x2) < 0.6f;
            if (!horizontal && !vertical) {
                return;
            }
            segments.add(new Segment(x1, y1, x2, y2, horizontal));
        }
    }

    private static final class PageCharCollector extends PDFTextStripper {
        private final List<CharPos> chars = new ArrayList<>();
        private float pageHeight = 841.89f;

        PageCharCollector() throws IOException {
        }

        List<CharPos> chars() {
            return chars;
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            chars.clear();
            pageHeight = page.getMediaBox().getHeight();
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            for (TextPosition position : positions) {
                String unicode = position.getUnicode();
                if (unicode == null || unicode.isBlank()) {
                    continue;
                }
                chars.add(new CharPos(
                        position.getX(),
                        pageHeight - position.getY(),
                        position.getWidth(),
                        unicode
                ));
            }
        }
    }
}
