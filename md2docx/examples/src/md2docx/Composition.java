package md2docx;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/** One compose call: the document being built and everything shared between its fragments. */
final class Composition implements AutoCloseable {

    /** WordprocessingML has nine heading outline levels and nine list levels. */
    static final int MAXIMUM_LEVEL = 9;

    /** commonmark parsers can be shared between threads. */
    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(),
                    FootnotesExtension.create(), AutolinkExtension.create()))
            .build();

    final ComposeOptions options;
    final XWPFDocument document;
    final Styles styles;
    final PageSetup page;
    final Lists lists;
    /** Null when headings are not numbered. */
    final HeadingNumbering headingNumbering;
    final Anchors anchors = new Anchors();
    final Footnotes footnotes;
    private int fragmentCount;
    private boolean topLevelSeen;

    Composition(ComposeOptions options) throws IOException {
        this.options = options;
        if (options.template != null) {
            document = Templates.open(options.template);
        } else {
            document = new XWPFDocument();
            PageSetup.applyA4(document);
        }
        try {
            styles = options.template != null ? Styles.fromTemplate(document, options) : Styles.create(document, options);
            XWPFNumbering numbering = document.createNumbering();
            lists = new Lists(numbering);
            headingNumbering = options.numberHeadings ? HeadingNumbering.attach(numbering, styles) : null;
            page = PageSetup.of(document);
            footnotes = new Footnotes(document, styles);
            if (options.title != null) document.getProperties().getCoreProperties().setTitle(Texts.xmlSafe(options.title));
        } catch (XmlException | RuntimeException failure) {
            document.close();
            throw failure instanceof XmlException ? new IllegalStateException("invalid built-in definition", failure)
                    : (RuntimeException) failure;
        }
    }

    void compose(List<Section> sections) {
        for (Section section : sections) render(section, 1, true);
        anchors.resolve();
        // the front matter is inserted in front of the first body element, so there must be one
        boolean emptyBody = document.getBodyElements().isEmpty();
        if (emptyBody) document.createParagraph();
        FrontMatter.insert(document, options, styles, anchors.headings(), emptyBody);
        if (options.pageNumbers) PageSetup.addPageNumbers(document);
        if (options.tableOfContentsLevels > 0 && options.updateFieldsOnOpen) document.enforceUpdateFields();
    }

    void write(OutputStream out) throws IOException {
        document.write(out);
    }

    @Override
    public void close() throws IOException {
        document.close();
    }

    static XWPFParagraph insertBefore(XWPFDocument document, IBodyElement element) {
        XmlCursor cursor = element instanceof XWPFParagraph paragraph
                ? paragraph.getCTP().newCursor()
                : ((XWPFTable) element).getCTTbl().newCursor();
        try (cursor) {
            return document.insertNewParagraph(cursor);
        }
    }

    private void render(Section section, int depth, boolean topLevel) {
        depth = Math.min(depth, MAXIMUM_LEVEL);
        Node root = section.markdown() == null ? null : PARSER.parse(Texts.xmlSafe(section.markdown()));
        Heading titleNode = section.titleSource() == Section.TitleSource.FIRST_HEADING && root != null
                && root.getFirstChild() instanceof Heading first ? first : null;
        boolean hasTitle = section.title() != null || titleNode != null;

        boolean pageBreak = section.pageBreakBefore() != null
                ? section.pageBreakBefore()
                : topLevel && topLevelSeen && options.pageBreakBetweenTopLevelSections;
        if (topLevel) topLevelSeen = true;

        int start = document.getBodyElements().size();
        Fragment fragment = new Fragment(this, fragmentCount++, section.baseDirectory(), root, titleNode, hasTitle ? depth : depth - 1);
        if (hasTitle) fragment.sectionHeading(depth, section.title());
        fragment.renderBody();
        for (Section child : section.children()) render(child, hasTitle ? depth + 1 : depth, false);
        if (pageBreak && start < document.getBodyElements().size()) pageBreakBefore(document.getBodyElements().get(start));
    }

    /** A paragraph starts a new page itself; a table cannot, so it gets an empty page-breaking paragraph in front. */
    private void pageBreakBefore(IBodyElement element) {
        if (element instanceof XWPFParagraph paragraph) {
            paragraph.setPageBreak(true);
        } else {
            XWPFParagraph paragraph = insertBefore(document, element);
            paragraph.setPageBreak(true);
            paragraph.setSpacingAfter(0);
        }
    }
}
