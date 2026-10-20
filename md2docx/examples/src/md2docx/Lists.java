package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFNum;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.XmlException;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumLvl;

import java.math.BigInteger;

/**
 * Markdown lists as Word list numbering. Each Markdown list gets its own numbering instance, so an ordered list
 * starts at its own first number instead of continuing the previous list.
 */
final class Lists {

    /** Left indent added per nesting level, in twips; the numbering definitions use the same value. */
    static final int INDENT = 420;

    private static final String[] BULLETS = {"●", "○", "■"};

    /** What a list needs from the fragment that renders it. */
    interface Output {
        /** A new paragraph numbered with {@code numberingId} at {@code level}, 0 being the outermost list. */
        XWPFParagraph numberedParagraph(BigInteger numberingId, int level);

        void inlines(Node parent, XWPFParagraph paragraph);

        /** Renders one block that belongs to a list item at {@code level}. */
        void block(Node block, int level);
    }

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

    /**
     * A numbering instance with an ID above every instance ID a template already uses. POI's
     * {@code addNum(definition)} numbers instances by count, which repeats a template's ID when its IDs have gaps.
     */
    static BigInteger addInstance(XWPFNumbering numbering, BigInteger definition) {
        long next = 1;
        for (XWPFNum existing : numbering.getNums()) {
            BigInteger id = existing.getCTNum().getNumId();
            if (id != null) next = Math.max(next, id.longValue() + 1);
        }
        BigInteger id = BigInteger.valueOf(next);
        numbering.addNum(definition, id);
        return id;
    }

    void render(ListBlock list, int level, Output output) {
        boolean ordered = list instanceof OrderedList;
        BigInteger numberingId = addInstance(numbering, ordered ? decimalDefinition : bulletDefinition);
        if (list instanceof OrderedList orderedList) {
            Integer start = orderedList.getMarkerStartNumber();
            CTNumLvl override = numbering.getNum(numberingId).getCTNum().addNewLvlOverride();
            override.setIlvl(BigInteger.valueOf(Math.min(level, 8)));
            override.addNewStartOverride().setVal(BigInteger.valueOf(start == null ? 1 : Math.max(start, 0)));
        }
        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            Node first = item.getFirstChild();
            String checkbox = null;
            if (first instanceof TaskListItemMarker marker) {
                checkbox = marker.isChecked() ? "☒ " : "☐ ";
                first = first.getNext();
            }
            // the numbered paragraph is the item's first paragraph, or an empty one when the item starts otherwise
            XWPFParagraph paragraph = output.numberedParagraph(numberingId, level);
            if (checkbox != null) Texts.write(paragraph.createRun(), checkbox);
            Node rest = first;
            if (first instanceof Paragraph) {
                output.inlines(first, paragraph);
                rest = first.getNext();
            }
            for (Node block = rest; block != null; block = block.getNext()) output.block(block, level);
        }
    }

    private static XWPFAbstractNum definition(int id, boolean ordered) throws XmlException {
        StringBuilder xml = new StringBuilder("<w:abstractNum ").append(Styles.WORD_NAMESPACE)
                .append(" w:abstractNumId=\"").append(id).append("\"><w:multiLevelType w:val=\"hybridMultilevel\"/>");
        for (int level = 0; level < 9; level++) {
            xml.append("<w:lvl w:ilvl=\"").append(level).append("\"><w:start w:val=\"1\"/><w:numFmt w:val=\"")
               .append(ordered ? "decimal" : "bullet").append("\"/><w:lvlText w:val=\"")
               .append(ordered ? "%" + (level + 1) + "." : BULLETS[level % 3]).append("\"/><w:lvlJc w:val=\"left\"/>")
               .append("<w:pPr><w:ind w:left=\"").append(INDENT * (level + 1)).append("\" w:hanging=\"").append(INDENT).append("\"/></w:pPr>");
            if (!ordered) xml.append("<w:rPr><w:sz w:val=\"16\"/><w:szCs w:val=\"16\"/></w:rPr>");
            xml.append("</w:lvl>");
        }
        return new XWPFAbstractNum(CTAbstractNum.Factory.parse(xml.append("</w:abstractNum>").toString(), Styles.ELEMENT_OPTIONS));
    }
}
