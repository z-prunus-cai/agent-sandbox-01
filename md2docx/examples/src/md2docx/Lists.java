package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumLvl;

import java.math.BigInteger;

/**
 * Word list numbering for Markdown lists. Each Markdown list gets its own numbering instance, so an ordered list
 * starts again at its own first number instead of continuing the previous list.
 */
final class Lists {

    private static final String[] BULLETS = {"●", "○", "■"};

    private final XWPFNumbering numbering;
    private final BigInteger bulletDefinition;
    private final BigInteger decimalDefinition;

    Lists(XWPFNumbering numbering) throws XmlException {
        this.numbering = numbering;
        int next = nextDefinitionId(numbering);
        bulletDefinition = numbering.addAbstractNum(definition(next, false));
        decimalDefinition = numbering.addAbstractNum(definition(next + 1, true));
    }

    /** Definition IDs above every ID a template already uses. */
    static int nextDefinitionId(XWPFNumbering numbering) {
        int next = 1;
        for (XWPFAbstractNum existing : numbering.getAbstractNums()) {
            BigInteger id = existing.getCTAbstractNum().getAbstractNumId();
            if (id != null) next = Math.max(next, id.intValue() + 1);
        }
        return next;
    }

    BigInteger newList(boolean ordered, int start) {
        BigInteger id = numbering.addNum(ordered ? decimalDefinition : bulletDefinition);
        if (ordered) {
            CTNum instance = numbering.getNum(id).getCTNum();
            for (int level = 0; level < 9; level++) {
                CTNumLvl override = instance.addNewLvlOverride();
                override.setIlvl(BigInteger.valueOf(level));
                override.addNewStartOverride().setVal(BigInteger.valueOf(level == 0 ? Math.max(start, 0) : 1));
            }
        }
        return id;
    }

    private static XWPFAbstractNum definition(int id, boolean ordered) throws XmlException {
        StringBuilder xml = new StringBuilder("<w:abstractNum ").append(Styles.WORD_NAMESPACE)
                .append(" w:abstractNumId=\"").append(id).append("\"><w:multiLevelType w:val=\"hybridMultilevel\"/>");
        for (int level = 0; level < 9; level++) {
            xml.append("<w:lvl w:ilvl=\"").append(level).append("\"><w:start w:val=\"1\"/><w:numFmt w:val=\"")
               .append(ordered ? "decimal" : "bullet").append("\"/><w:lvlText w:val=\"")
               .append(ordered ? "%" + (level + 1) + "." : BULLETS[level % 3]).append("\"/><w:lvlJc w:val=\"left\"/>")
               .append("<w:pPr><w:ind w:left=\"").append(420 * (level + 1)).append("\" w:hanging=\"420\"/></w:pPr>");
            if (!ordered) xml.append("<w:rPr><w:sz w:val=\"16\"/><w:szCs w:val=\"16\"/></w:rPr>");
            xml.append("</w:lvl>");
        }
        return new XWPFAbstractNum(CTAbstractNum.Factory.parse(xml.append("</w:abstractNum>").toString(), Styles.FRAGMENT));
    }
}
