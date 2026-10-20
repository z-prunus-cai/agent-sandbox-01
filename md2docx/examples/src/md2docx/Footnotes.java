package md2docx;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.DefinitionMap;
import org.commonmark.node.Node;

import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Markdown footnotes as Word footnotes. A definition belongs to its fragment: two fragments that both use
 * {@code [^1]} get two footnotes. Footnotes are shown numbered in order of appearance, whatever their IDs.
 */
final class Footnotes {

    /** Renders the blocks of a footnote definition into the footnote. */
    interface Output {
        void blocks(Node parent, Container container);
    }

    private final XWPFDocument document;
    private final Styles styles;
    private final Map<FootnoteDefinition, BigInteger> ids = new IdentityHashMap<>();

    Footnotes(XWPFDocument document, Styles styles) {
        this.document = document;
        this.styles = styles;
    }

    /**
     * Every definition in the fragment, also those nested in quotes or lists, looked up the way commonmark resolves
     * references: labels are case-insensitive and normalized.
     */
    static DefinitionMap<FootnoteDefinition> definitions(Node root) {
        DefinitionMap<FootnoteDefinition> definitions = new DefinitionMap<>(FootnoteDefinition.class);
        if (root == null) return definitions;
        root.accept(new AbstractVisitor() {
            @Override
            public void visit(CustomBlock block) {
                if (block instanceof FootnoteDefinition definition) definitions.putIfAbsent(definition.getLabel(), definition);
                visitChildren(block);
            }
        });
        return definitions;
    }

    /**
     * A reference to the footnote of its definition. The footnote is created and registered before its body is
     * rendered, so a footnote that refers to itself or to another footnote reuses the existing one.
     */
    void reference(FootnoteReference reference, DefinitionMap<FootnoteDefinition> definitions, XWPFParagraph paragraph,
                   Output output) {
        FootnoteDefinition definition = definitions.get(reference.getLabel());
        if (definition == null) {
            Texts.write(paragraph.createRun(), "[^" + reference.getLabel() + "]");
            return;
        }
        BigInteger id = ids.get(definition);
        if (id == null) {
            XWPFFootnote footnote = document.createFootnote();
            id = footnote.getId();
            ids.put(definition, id);
            FootnoteBody body = new FootnoteBody(footnote);
            output.blocks(definition, body);
            body.finish();
        }
        XWPFRun run = paragraph.createRun();
        run.setStyle(styles.id(Styles.Style.FOOTNOTE_REFERENCE));
        run.getCTR().addNewFootnoteReference().setId(id);
    }

    /** The body of one footnote; it always starts with a paragraph that holds the footnote's own number mark. */
    private final class FootnoteBody implements Container {
        private final XWPFFootnote footnote;
        private boolean started;

        FootnoteBody(XWPFFootnote footnote) {
            this.footnote = footnote;
        }

        @Override
        public XWPFParagraph paragraph() {
            XWPFParagraph paragraph = footnote.createParagraph();
            paragraph.setStyle(styles.id(Styles.Style.FOOTNOTE_TEXT));
            if (!started) {
                started = true;
                if (paragraph.getRuns().isEmpty()) footnote.ensureFootnoteRef(paragraph);
                paragraph.getRuns().get(0).setStyle(styles.id(Styles.Style.FOOTNOTE_REFERENCE));
                Texts.write(paragraph.createRun(), " ");
            }
            return paragraph;
        }

        @Override
        public XWPFTable table(int rows, int columns) {
            if (!started) paragraph();
            return footnote.createTable(rows, columns);
        }

        @Override public List<IBodyElement> elements() { return footnote.getBodyElements(); }

        @Override public boolean isBody() { return false; }

        void finish() {
            if (!started) paragraph();
        }
    }
}
