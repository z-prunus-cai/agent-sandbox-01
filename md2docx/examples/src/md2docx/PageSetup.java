package md2docx;

import org.apache.poi.ooxml.util.POIXMLUnits;
import org.apache.poi.util.Units;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField;

import java.math.BigInteger;

/** Page size and margins, in twips (1/20 point; 1440 twips = 1 inch = 2.54 cm). */
record PageSetup(int textWidthTwips, int textHeightTwips) {

    private static final int A4_WIDTH = 11906;
    private static final int A4_HEIGHT = 16838;
    /** 2.54 cm on every side. */
    private static final int MARGIN = 1440;
    /** Distance from the page edge to the header, 1.5 cm. */
    private static final int HEADER_DISTANCE = 851;
    /** Distance from the page edge to the footer, 1.75 cm. */
    private static final int FOOTER_DISTANCE = 992;
    static final int A4_TEXT_WIDTH = A4_WIDTH - 2 * MARGIN;

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
        margins.setHeader(BigInteger.valueOf(HEADER_DISTANCE));
        margins.setFooter(BigInteger.valueOf(FOOTER_DISTANCE));
        margins.setGutter(BigInteger.ZERO);
    }

    /** The area inside the margins of the document's last section, which tables and images must fit into. */
    static PageSetup of(XWPFDocument document) {
        CTBody body = document.getDocument().getBody();
        CTSectPr section = body.isSetSectPr() ? body.getSectPr() : null;
        int width = A4_WIDTH;
        int height = A4_HEIGHT;
        int left = MARGIN;
        int right = MARGIN;
        int top = MARGIN;
        int bottom = MARGIN;
        if (section != null && section.isSetPgSz()) {
            CTPageSz size = section.getPgSz();
            if (size.isSetW()) width = twips(POIXMLUnits.parseLength(size.xgetW()));
            if (size.isSetH()) height = twips(POIXMLUnits.parseLength(size.xgetH()));
        }
        if (section != null && section.isSetPgMar()) {
            CTPageMar margins = section.getPgMar();
            left = twips(POIXMLUnits.parseLength(margins.xgetLeft()));
            right = twips(POIXMLUnits.parseLength(margins.xgetRight()));
            top = twips(POIXMLUnits.parseLength(margins.xgetTop()));
            bottom = twips(POIXMLUnits.parseLength(margins.xgetBottom()));
        }
        return new PageSetup(Math.max(width - Math.abs(left) - Math.abs(right), MARGIN),
                Math.max(height - Math.abs(top) - Math.abs(bottom), MARGIN));
    }

    /** A centered PAGE field in the default footer, unless the template already has a footer. */
    static void addPageNumbers(XWPFDocument document) {
        if (!document.getFooterList().isEmpty()) return;
        XWPFParagraph paragraph = document.createFooter(HeaderFooterType.DEFAULT).createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        CTSimpleField field = paragraph.getCTP().addNewFldSimple();
        field.setInstr(" PAGE ");
        field.addNewR().addNewT().setStringValue("1");
    }

    /** POI parses lengths with or without a unit, such as "1440" or "2.54cm", into EMU. */
    private static int twips(long emu) {
        return (int) Math.round(emu / (Units.EMU_PER_POINT / 20.0));
    }
}
