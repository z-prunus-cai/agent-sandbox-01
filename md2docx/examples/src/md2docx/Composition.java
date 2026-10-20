package md2docx;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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

    /** Thread-safe; commonmark parsers can be shared. */
    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(),
                    FootnotesExtension.create(), AutolinkExtension.create()))
            .build();

    final ComposeOptions options;
    final XWPFDocument document;
    final Styles styles;
    final PageSetup page;
    final Lists lists;
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
            headingNumbering = options.numberHeadings ? HeadingNumbering.attach(numbering, styles) : HeadingNumbering.disabled();
        } catch (XmlException e) {
            document.close();
            throw new IllegalStateException("built-in style or numbering definition is invalid", e);
        }
        page = PageSetup.of(document);
        footnotes = new Footnotes(document);
    }

    void compose(List<Section> sections) {
        for (Section section : sections) render(section, 1, true);
        anchors.resolve();
        // the front matter is inserted in front of the first body element, so there must be one
        if (document.getBodyElements().isEmpty()) document.createParagraph();
        FrontMatter.insert(document, options, styles, headingNumbering, anchors.headings());
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

    private void render(Section section, int depth, boolean topLevel) {
        depth = Math.min(depth, 9);
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

    /** Starts the element on a new page without adding an empty line in front of it. */
    private void pageBreakBefore(IBodyElement element) {
        if (element instanceof XWPFParagraph paragraph) {
            paragraph.setPageBreak(true);
        } else {
            XWPFParagraph paragraph = FrontMatter.insertBefore(document, element);
            paragraph.setPageBreak(true);
            paragraph.setSpacingAfter(0);
        }
    }
}
