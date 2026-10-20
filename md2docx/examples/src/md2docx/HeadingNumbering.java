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
 * Numbers headings 1., 1.1, 1.1.1 with a Word multilevel list attached to the heading styles, so Word keeps the
 * numbers right when chapters are added or removed later. The same numbers are computed here for the
 * pre-filled table of contents.
 */
final class HeadingNumbering {

    private final boolean enabled;
    private final int[] counters = new int[9];

    private HeadingNumbering(boolean enabled) {
        this.enabled = enabled;
    }

    static HeadingNumbering disabled() {
        return new HeadingNumbering(false);
    }

    static HeadingNumbering attach(XWPFNumbering numbering, Styles styles) throws XmlException {
        BigInteger definition = numbering.addAbstractNum(definition(Lists.nextDefinitionId(numbering), styles));
        BigInteger instance = numbering.addNum(definition);
        for (int level = 1; level <= 9; level++) {
            CTStyle style = styles.definition(Styles.Style.heading(level));
            if (style == null) continue;
            CTPPrGeneral properties = style.isSetPPr() ? style.getPPr() : style.addNewPPr();
            // a template whose heading styles already carry numbering keeps its own
            if (properties.isSetNumPr()) continue;
            CTNumPr numberingProperties = properties.addNewNumPr();
            numberingProperties.addNewIlvl().setVal(BigInteger.valueOf(level - 1));
            numberingProperties.addNewNumId().setVal(instance);
        }
        return new HeadingNumbering(true);
    }

    /** The number Word will show in front of the next heading of this level, or null when numbering is off. */
    String next(int level) {
        if (!enabled) return null;
        counters[level - 1]++;
        for (int deeper = level; deeper < counters.length; deeper++) counters[deeper] = 0;
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < level; i++) label.append(i == 0 ? "" : ".").append(counters[i]);
        return level == 1 ? label + "." : label.toString();
    }

    /**
     * Title, subtitle and the table of contents heading are often based on "heading 1" in templates and would
     * inherit its number; numbering ID 0 switches numbering off for one paragraph.
     */
    void exclude(XWPFParagraph paragraph) {
        if (enabled) paragraph.setNumID(BigInteger.ZERO);
    }

    private static XWPFAbstractNum definition(int id, Styles styles) throws XmlException {
        StringBuilder xml = new StringBuilder("<w:abstractNum ").append(Styles.WORD_NAMESPACE)
                .append(" w:abstractNumId=\"").append(id).append("\"><w:multiLevelType w:val=\"multilevel\"/>");
        for (int level = 0; level < 9; level++) {
            StringBuilder text = new StringBuilder();
            for (int i = 1; i <= level + 1; i++) text.append(i == 1 ? "" : ".").append('%').append(i);
            if (level == 0) text.append('.');
            xml.append("<w:lvl w:ilvl=\"").append(level).append("\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/>")
               .append("<w:pStyle w:val=\"").append(Styles.attribute(styles.id(Styles.Style.heading(level + 1)))).append("\"/>")
               .append("<w:suff w:val=\"space\"/><w:lvlText w:val=\"").append(text).append("\"/><w:lvlJc w:val=\"left\"/>")
               .append("<w:pPr><w:ind w:left=\"0\" w:firstLine=\"0\"/></w:pPr></w:lvl>");
        }
        return new XWPFAbstractNum(CTAbstractNum.Factory.parse(xml.append("</w:abstractNum>").toString(), Styles.FRAGMENT));
    }
}
