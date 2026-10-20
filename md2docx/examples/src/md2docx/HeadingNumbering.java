package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;

import java.math.BigInteger;

/**
 * Numbers headings 1., 1.1, 1.1.1 with a multilevel list attached to the heading styles, so the numbers are not
 * part of the heading text. The same numbers are computed here for the pre-filled table of contents.
 */
final class HeadingNumbering {

    private final boolean labels;
    private final int[] counters = new int[Composition.MAXIMUM_LEVEL];

    private HeadingNumbering(boolean labels) {
        this.labels = labels;
    }

    static HeadingNumbering attach(XWPFNumbering numbering, Styles styles) throws XmlException {
        BigInteger definition = numbering.addAbstractNum(definition(Lists.nextDefinitionId(numbering), styles));
        BigInteger instance = Lists.addInstance(numbering, definition);
        boolean templateNumbering = false;
        for (int level = 1; level <= Composition.MAXIMUM_LEVEL; level++) {
            CTStyle style = styles.definition(Styles.Style.heading(level));
            if (style == null) continue;
            CTPPrGeneral properties = style.isSetPPr() ? style.getPPr() : style.addNewPPr();
            if (properties.isSetNumPr()) {
                templateNumbering = true;
                continue;
            }
            CTNumPr numberingProperties = properties.addNewNumPr();
            numberingProperties.addNewIlvl().setVal(BigInteger.valueOf(level - 1));
            numberingProperties.addNewNumId().setVal(instance);
        }
        // the template's number format is unknown here, so the table of contents gets no numbers
        return new HeadingNumbering(!templateNumbering);
    }

    /** The number of the next heading of this level as the list displays it, or null when it is not known. */
    String next(int level) {
        if (!labels) return null;
        counters[level - 1]++;
        for (int deeper = level; deeper < counters.length; deeper++) counters[deeper] = 0;
        StringBuilder label = new StringBuilder();
        for (int index = 0; index < level; index++) label.append(index == 0 ? "" : ".").append(counters[index]);
        return level == 1 ? label + "." : label.toString();
    }

    /**
     * Title, subtitle and the table of contents heading may be based on "heading 1" in a template and would inherit
     * its number; numbering ID 0 removes numbering from one paragraph.
     */
    static void exclude(XWPFParagraph paragraph) {
        paragraph.setNumID(BigInteger.ZERO);
    }

    private static XWPFAbstractNum definition(int id, Styles styles) throws XmlException {
        StringBuilder xml = new StringBuilder("<w:abstractNum ").append(Styles.WORD_NAMESPACE)
                .append(" w:abstractNumId=\"").append(id).append("\"><w:multiLevelType w:val=\"multilevel\"/>");
        for (int level = 0; level < Composition.MAXIMUM_LEVEL; level++) {
            StringBuilder text = new StringBuilder();
            for (int shown = 1; shown <= level + 1; shown++) text.append(shown == 1 ? "" : ".").append('%').append(shown);
            if (level == 0) text.append('.');
            xml.append("<w:lvl w:ilvl=\"").append(level).append("\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/>")
               .append("<w:pStyle w:val=\"").append(Styles.attribute(styles.id(Styles.Style.heading(level + 1)))).append("\"/>")
               .append("<w:suff w:val=\"space\"/><w:lvlText w:val=\"").append(text).append("\"/><w:lvlJc w:val=\"left\"/>")
               .append("<w:pPr><w:ind w:left=\"0\" w:firstLine=\"0\"/></w:pPr></w:lvl>");
        }
        return new XWPFAbstractNum(CTAbstractNum.Factory.parse(xml.append("</w:abstractNum>").toString(), Styles.ELEMENT_OPTIONS));
    }
}
