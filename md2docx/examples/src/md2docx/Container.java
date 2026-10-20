package md2docx;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.util.List;

/** Where blocks go: the document body or a footnote. */
interface Container {

    XWPFParagraph paragraph();

    XWPFTable table(int rows, int columns);

    List<IBodyElement> elements();

    boolean isBody();
}
