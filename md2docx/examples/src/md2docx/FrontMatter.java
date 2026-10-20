package md2docx;

import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

import java.util.List;

/** Title, subtitle and table of contents, inserted in front of the body once every heading is known. */
final class FrontMatter {

    private FrontMatter() {
    }

    static void insert(XWPFDocument document, ComposeOptions options, Styles styles, HeadingNumbering numbering,
                       List<Anchors.Heading> headings) {
        boolean title = options.title != null && !options.title.isBlank();
        boolean subtitle = options.subtitle != null && !options.subtitle.isBlank();
        boolean contents = options.tableOfContentsLevels > 0;
        if (!title && !subtitle && !contents) return;

        IBodyElement first = document.getBodyElements().get(0);
        XWPFParagraph last = null;
        if (title) last = textParagraph(document, first, styles.id(Styles.Style.TITLE), options.title, numbering);
        if (subtitle) last = textParagraph(document, first, styles.id(Styles.Style.SUBTITLE), options.subtitle, numbering);
        if (contents) last = tableOfContents(document, first, options, styles, numbering, headings);

        if (options.pageBreakAfterFrontMatter) {
            if (first instanceof XWPFParagraph paragraph) paragraph.setPageBreak(true);
            else last.createRun().addBreak(BreakType.PAGE);
        }
    }

    /**
     * A real TOC field, which Word rebuilds with page numbers. Its stored result is filled with one linked entry
     * per heading, so the contents also show where fields are not updated, such as in previews.
     */
    private static XWPFParagraph tableOfContents(XWPFDocument document, IBodyElement before, ComposeOptions options,
                                                 Styles styles, HeadingNumbering numbering, List<Anchors.Heading> headings) {
        textParagraph(document, before, styles.id(Styles.Style.CONTENTS_HEADING), options.tableOfContentsTitle, numbering);
        List<Anchors.Heading> entries = headings.stream().filter(heading -> heading.level() <= options.tableOfContentsLevels).toList();

        XWPFParagraph paragraph = insertBefore(document, before);
        fieldCharacter(paragraph, STFldCharType.BEGIN);
        CTText instruction = paragraph.createRun().getCTR().addNewInstrText();
        instruction.setStringValue(" TOC \\o \"1-" + options.tableOfContentsLevels + "\" \\h \\z \\u ");
        instruction.setSpace(SpaceAttribute.Space.PRESERVE);
        fieldCharacter(paragraph, STFldCharType.SEPARATE);
        if (entries.isEmpty()) Texts.write(paragraph.createRun(), " ");
        for (int i = 0; i < entries.size(); i++) {
            Anchors.Heading entry = entries.get(i);
            if (i > 0) paragraph = insertBefore(document, before);
            paragraph.setStyle(styles.id(Styles.Style.contents(entry.level())));
            CTHyperlink link = paragraph.getCTP().addNewHyperlink();
            link.setAnchor(entry.bookmark());
            XWPFRun run = new XWPFHyperlinkRun(link, link.addNewR(), paragraph);
            Texts.write(run, entry.number() == null ? entry.text() : entry.number() + " " + entry.text());
        }
        fieldCharacter(paragraph, STFldCharType.END);
        return paragraph;
    }

    private static XWPFParagraph textParagraph(XWPFDocument document, IBodyElement before, String styleId, String text,
                                               HeadingNumbering numbering) {
        XWPFParagraph paragraph = insertBefore(document, before);
        paragraph.setStyle(styleId);
        numbering.exclude(paragraph);
        Texts.write(paragraph.createRun(), text);
        return paragraph;
    }

    private static void fieldCharacter(XWPFParagraph paragraph, STFldCharType.Enum type) {
        paragraph.createRun().getCTR().addNewFldChar().setFldCharType(type);
    }

    static XWPFParagraph insertBefore(XWPFDocument document, IBodyElement element) {
        XmlCursor cursor = element instanceof XWPFParagraph paragraph
                ? paragraph.getCTP().newCursor()
                : ((XWPFTable) element).getCTTbl().newCursor();
        try (cursor) {
            return document.insertNewParagraph(cursor);
        }
    }
}
