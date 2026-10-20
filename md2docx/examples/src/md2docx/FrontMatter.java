package md2docx;

import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Title, subtitle and table of contents, inserted in front of the body once every heading is known. */
final class FrontMatter {

    private FrontMatter() {
    }

    /**
     * @param emptyBody true when the body holds only the placeholder paragraph, which then needs no page break
     */
    static void insert(XWPFDocument document, ComposeOptions options, Styles styles, List<Anchors.Heading> headings,
                       boolean emptyBody) {
        boolean title = options.title != null && !options.title.isBlank();
        boolean subtitle = options.subtitle != null && !options.subtitle.isBlank();
        boolean contents = options.tableOfContentsLevels > 0;
        if (!title && !subtitle && !contents) return;

        // POI's insertion walks the earlier siblings of the new paragraph, so the paragraphs are inserted from the
        // last to the first, each directly at the start of the body: inserting in reading order is quadratic
        List<Consumer<XWPFParagraph>> paragraphs = new ArrayList<>();
        if (title) paragraphs.add(paragraph -> text(paragraph, styles.id(Styles.Style.TITLE), options.title, options));
        if (subtitle) paragraphs.add(paragraph -> text(paragraph, styles.id(Styles.Style.SUBTITLE), options.subtitle, options));
        if (contents) tableOfContents(paragraphs, options, styles, headings);
        if (options.pageBreakAfterFrontMatter && !emptyBody) {
            IBodyElement first = document.getBodyElements().get(0);
            if (first instanceof XWPFParagraph paragraph) {
                paragraph.setPageBreak(true);
            } else {
                Consumer<XWPFParagraph> last = paragraphs.removeLast();
                paragraphs.add(paragraph -> {
                    last.accept(paragraph);
                    paragraph.createRun().addBreak(BreakType.PAGE);
                });
            }
        }
        IBodyElement next = document.getBodyElements().get(0);
        for (int index = paragraphs.size() - 1; index >= 0; index--) {
            XWPFParagraph paragraph = Composition.insertBefore(document, next);
            paragraphs.get(index).accept(paragraph);
            next = paragraph;
        }
    }

    /**
     * A real TOC field, whose result is rebuilt with page numbers when fields are updated. Its stored result
     * holds one linked entry per heading, so readers that do not update fields still show the contents.
     */
    private static void tableOfContents(List<Consumer<XWPFParagraph>> paragraphs, ComposeOptions options, Styles styles,
                                        List<Anchors.Heading> headings) {
        paragraphs.add(paragraph -> text(paragraph, styles.id(Styles.Style.CONTENTS_HEADING), options.tableOfContentsTitle, options));
        List<Anchors.Heading> entries = headings.stream().filter(heading -> heading.level() <= options.tableOfContentsLevels).toList();
        int count = Math.max(entries.size(), 1);
        for (int index = 0; index < count; index++) {
            Anchors.Heading entry = entries.isEmpty() ? null : entries.get(index);
            boolean first = index == 0;
            boolean last = index == count - 1;
            paragraphs.add(paragraph -> {
                if (first) {
                    fieldCharacter(paragraph, STFldCharType.BEGIN);
                    CTText instruction = paragraph.createRun().getCTR().addNewInstrText();
                    instruction.setStringValue(" TOC \\o \"1-" + options.tableOfContentsLevels + "\" \\h \\z \\u ");
                    instruction.setSpace(SpaceAttribute.Space.PRESERVE);
                    fieldCharacter(paragraph, STFldCharType.SEPARATE);
                }
                if (entry == null) {
                    Texts.write(paragraph.createRun(), " ");
                } else {
                    paragraph.setStyle(styles.id(Styles.Style.contents(entry.level())));
                    CTHyperlink link = paragraph.getCTP().addNewHyperlink();
                    link.setAnchor(entry.bookmark());
                    Texts.write(new XWPFHyperlinkRun(link, link.addNewR(), paragraph),
                            entry.number() == null ? entry.text() : entry.number() + " " + entry.text());
                }
                if (last) fieldCharacter(paragraph, STFldCharType.END);
            });
        }
    }

    private static void text(XWPFParagraph paragraph, String styleId, String text, ComposeOptions options) {
        paragraph.setStyle(styleId);
        if (options.numberHeadings) HeadingNumbering.exclude(paragraph);
        Texts.write(paragraph.createRun(), text);
    }

    private static void fieldCharacter(XWPFParagraph paragraph, STFldCharType.Enum type) {
        paragraph.createRun().getCTR().addNewFldChar().setFldCharType(type);
    }
}
