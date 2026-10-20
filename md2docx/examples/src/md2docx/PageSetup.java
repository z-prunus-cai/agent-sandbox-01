package md2docx;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField;

import java.math.BigDecimal;
import java.math.BigInteger;

/** Page size and margins, in twips (1/20 point; 1440 twips = 1 inch = 2.54 cm). */
record PageSetup(int textWidthTwips, int textHeightTwips) {

    private static final int A4_WIDTH = 11906;
    private static final int A4_HEIGHT = 16838;
    private static final int MARGIN = 1440;

    /** A4 with 2.54 cm margins, for a document without a template. */
    static void applyA4(XWPFDocument document) {
        CTBody body = document.getDocument().getBody();
        CTSectPr section = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageSz size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
        size.setW(BigInteger.valueOf(A4_WIDTH));
        size.setH(BigInteger.valueOf(A4_HEIGHT));
        CTPageMar margins = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(MARGIN));
        margins.setBottom(BigInteger.valueOf(MARGIN));
        margins.setLeft(BigInteger.valueOf(MARGIN));
        margins.setRight(BigInteger.valueOf(MARGIN));
        margins.setHeader(BigInteger.valueOf(851));
        margins.setFooter(BigInteger.valueOf(992));
        margins.setGutter(BigInteger.ZERO);
    }

    /** The area inside the margins of the document's last section, which tables and images must fit into. */
    static PageSetup of(XWPFDocument document) {
        CTBody body = document.getDocument().getBody();
        CTSectPr section = body.isSetSectPr() ? body.getSectPr() : null;
        return new PageSetup(textSize(section, true), textSize(section, false));
    }

    /** A centred PAGE field in the default footer, unless the template already has a footer. */
    static void addPageNumbers(XWPFDocument document) {
        if (!document.getFooterList().isEmpty()) return;
        XWPFParagraph paragraph = document.createFooter(HeaderFooterType.DEFAULT).createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        CTSimpleField field = paragraph.getCTP().addNewFldSimple();
        field.setInstr(" PAGE ");
        field.addNewR().addNewT().setStringValue("1");
    }

    private static int textSize(CTSectPr section, boolean horizontal) {
        int page = horizontal ? A4_WIDTH : A4_HEIGHT;
        int first = MARGIN;
        int second = MARGIN;
        if (section != null && section.isSetPgSz()) {
            page = twips(horizontal ? section.getPgSz().getW() : section.getPgSz().getH(), page);
        }
        if (section != null && section.isSetPgMar()) {
            CTPageMar margins = section.getPgMar();
            first = twips(horizontal ? margins.getLeft() : margins.getTop(), first);
            second = twips(horizontal ? margins.getRight() : margins.getBottom(), second);
        }
        return Math.max(page - Math.abs(first) - Math.abs(second), 1440);
    }

    /** Sizes are plain twips in practice; values with a unit such as "2.5cm" fall back to the default. */
    private static int twips(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return new BigDecimal(value.toString()).intValue();
        } catch (NumberFormatException withUnit) {
            return fallback;
        }
    }
}
