package md2docx;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.node.Node;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** GFM tables: column widths from content, header row repeated on every page, rows never split across pages. */
final class Tables {

    /** Renders the inline content of one cell into its paragraph. */
    interface CellRenderer {
        void render(TableCell cell, XWPFParagraph paragraph, boolean header, int widthTwips);
    }

    private Tables() {
    }

    static void render(TableBlock table, Container container, String styleId, int widthTwips, CellRenderer cells) {
        List<List<TableCell>> rows = new ArrayList<>();
        for (Node part = table.getFirstChild(); part != null; part = part.getNext()) {
            for (Node row = part.getFirstChild(); row != null; row = row.getNext()) {
                List<TableCell> rowCells = new ArrayList<>();
                for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) rowCells.add((TableCell) cell);
                rows.add(rowCells);
            }
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        if (columns == 0) return;
        int[] widths = columnWidths(rows, columns, widthTwips);

        List<IBodyElement> before = container.elements();
        if (!before.isEmpty() && before.get(before.size() - 1) instanceof XWPFTable) {
            // two tables that touch are read as one table, by LibreOffice for example
            container.paragraph().setSpacingAfter(0);
        }
        XWPFTable result = container.table(rows.size(), columns);
        result.setStyleID(styleId);
        // POI gives a new table direct borders, which would hide the borders of the table style
        result.removeBorders();
        result.setWidth(String.valueOf(widthTwips));
        result.getCTTbl().getTblPr().addNewTblLayout().setType(STTblLayoutType.FIXED);
        CTTblGrid grid = result.getCTTbl().getTblGrid() != null ? result.getCTTbl().getTblGrid() : result.getCTTbl().addNewTblGrid();
        while (grid.sizeOfGridColArray() < columns) grid.addNewGridCol();
        for (int column = 0; column < columns; column++) grid.getGridColArray(column).setW(BigInteger.valueOf(widths[column]));

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            XWPFTableRow row = result.getRow(rowIndex);
            List<TableCell> rowCells = rows.get(rowIndex);
            boolean header = !rowCells.isEmpty() && rowCells.get(0).isHeader();
            if (header) row.setRepeatHeader(true);
            row.setCantSplitRow(true);
            for (int column = 0; column < columns; column++) {
                XWPFTableCell cell = row.getCell(column);
                cell.setWidth(String.valueOf(widths[column]));
                if (header) cell.setColor("E8EEF7");
                XWPFParagraph paragraph = cell.getParagraphs().get(0);
                paragraph.setSpacingAfter(0);
                if (column >= rowCells.size()) continue;
                TableCell source = rowCells.get(column);
                if (source.getAlignment() == TableCell.Alignment.CENTER) paragraph.setAlignment(ParagraphAlignment.CENTER);
                else if (source.getAlignment() == TableCell.Alignment.RIGHT) paragraph.setAlignment(ParagraphAlignment.RIGHT);
                cells.render(source, paragraph, header, widths[column]);
            }
        }
    }

    /**
     * Markdown has no column widths. Each column is weighted by its header (at least 4, at most 16 characters) and by
     * its longest body cell (at most 40), with wide characters counting double; the table spans the text width.
     */
    static int[] columnWidths(List<List<TableCell>> rows, int columns, int widthTwips) {
        double[] weights = new double[columns];
        for (int column = 0; column < columns; column++) {
            int header = rows.get(0).size() > column ? Texts.displayWidth(Texts.plainText(rows.get(0).get(column))) : 0;
            weights[column] = Math.min(Math.max(header, 4), 16);
        }
        for (List<TableCell> row : rows.subList(1, rows.size())) {
            for (int column = 0; column < row.size(); column++) {
                weights[column] = Math.max(weights[column], Math.min(40, Texts.displayWidth(Texts.plainText(row.get(column)))));
            }
        }
        double total = Arrays.stream(weights).sum();
        int[] widths = new int[columns];
        for (int column = 0; column < columns; column++) widths[column] = (int) (widthTwips * weights[column] / total);
        return widths;
    }
}
