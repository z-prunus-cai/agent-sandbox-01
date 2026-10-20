package md2docx;

import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Renders the body of one section: its Markdown blocks and inline content. */
final class Fragment {

    private static final int LIST_INDENT = 420;
    private static final int QUOTE_INDENT = 360;

    /** Where blocks go: the document body or a footnote. */
    interface Container {
        XWPFParagraph paragraph();

        XWPFTable table(int rows, int columns);

        List<IBodyElement> elements();

        boolean isBody();
    }

    private record Place(Container container, int listLevel, int quoteDepth) {
        Place inList(int level) { return new Place(container, level, quoteDepth); }

        Place inQuote() { return new Place(container, listLevel, quoteDepth + 1); }

        int indent() {
            return (listLevel >= 0 ? LIST_INDENT * (listLevel + 1) : 0) + (quoteDepth > 0 ? 240 + QUOTE_INDENT * (quoteDepth - 1) : 0);
        }
    }

    private record Format(boolean bold, boolean italic, boolean strike, Links.Target link) {
        static final Format PLAIN = new Format(false, false, false, new Links.TextOnly());

        Format withBold() { return new Format(true, italic, strike, link); }

        Format withItalic() { return new Format(bold, true, strike, link); }

        Format withStrike() { return new Format(bold, italic, true, link); }

        Format withLink(Links.Target target) { return new Format(bold, italic, strike, target); }
    }

    private final Composition composition;
    private final int id;
    private final Path baseDirectory;
    private final Node root;
    private final Heading titleNode;
    private final int levelBase;
    private final int shallowestLevel;
    private final Map<String, FootnoteDefinition> footnoteDefinitions = new HashMap<>();
    private final Container body;
    private int imageWidthTwips;

    /**
     * @param levelBase level of the section heading; the shallowest heading inside the Markdown becomes levelBase + 1
     */
    Fragment(Composition composition, int id, Path baseDirectory, Node root, Heading titleNode, int levelBase) {
        this.composition = composition;
        this.id = id;
        this.baseDirectory = baseDirectory;
        this.root = root;
        this.titleNode = titleNode;
        this.levelBase = levelBase;
        this.imageWidthTwips = composition.page.textWidthTwips();
        int[] shallowest = {Integer.MAX_VALUE};
        if (root != null) {
            root.accept(new AbstractVisitor() {
                @Override
                public void visit(Heading heading) {
                    if (heading != titleNode) shallowest[0] = Math.min(shallowest[0], heading.getLevel());
                }
            });
            for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
                if (node instanceof FootnoteDefinition definition) footnoteDefinitions.putIfAbsent(definition.getLabel(), definition);
            }
        }
        this.shallowestLevel = shallowest[0] == Integer.MAX_VALUE ? 1 : shallowest[0];
        this.body = new Container() {
            @Override
            public XWPFParagraph paragraph() {
                List<IBodyElement> elements = composition.document.getBodyElements();
                boolean afterTable = !elements.isEmpty() && elements.get(elements.size() - 1) instanceof XWPFTable;
                XWPFParagraph paragraph = composition.document.createParagraph();
                if (afterTable) paragraph.setSpacingBefore(160);
                return paragraph;
            }

            @Override public XWPFTable table(int rows, int columns) { return composition.document.createTable(rows, columns); }

            @Override public List<IBodyElement> elements() { return composition.document.getBodyElements(); }

            @Override public boolean isBody() { return true; }
        };
    }

    void sectionHeading(int level, String title) {
        heading(new Place(body, -1, 0), level, titleNode, title);
    }

    void renderBody() {
        if (root != null) renderBlocks(root, new Place(body, -1, 0));
    }

    private void renderBlocks(Node parent, Place place) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) renderBlock(node, place);
    }

    private void renderBlock(Node node, Place place) {
        // the title is rendered by the section, footnotes where they are referenced
        if (node == titleNode || node instanceof FootnoteDefinition || node instanceof LinkReferenceDefinition) return;
        if (node instanceof Heading heading) {
            int level = Math.max(1, Math.min(9, levelBase + heading.getLevel() - shallowestLevel + 1));
            heading(place, level, heading, null);
        } else if (node instanceof Paragraph) {
            XWPFParagraph paragraph = paragraph(place, null);
            renderInlines(node, paragraph, Format.PLAIN);
            if (Images.onlyImages(node)) paragraph.setAlignment(ParagraphAlignment.CENTER);
        } else if (node instanceof ListBlock list) {
            renderList(list, place);
        } else if (node instanceof BlockQuote) {
            renderBlocks(node, place.inQuote());
        } else if (node instanceof FencedCodeBlock fenced) {
            renderCode(fenced.getLiteral(), place);
        } else if (node instanceof IndentedCodeBlock indented) {
            renderCode(indented.getLiteral(), place);
        } else if (node instanceof ThematicBreak) {
            paragraph(place, null).setBorderBottom(Borders.SINGLE);
        } else if (node instanceof TableBlock table) {
            Tables.render(table, place.container, composition.styles.id(Styles.Style.TABLE), composition.page.textWidthTwips(),
                    (cell, paragraph, header, widthTwips) -> {
                        int saved = imageWidthTwips;
                        imageWidthTwips = Math.max(widthTwips - 200, 200);
                        renderInlines(cell, paragraph, header ? Format.PLAIN.withBold() : Format.PLAIN);
                        imageWidthTwips = saved;
                    });
        } else if (node instanceof HtmlBlock html) {
            String text = html.getLiteral().replaceAll("(?s)<!--.*?-->", "").replaceAll("<[^>]+>", "").strip();
            if (!text.isEmpty()) Texts.write(paragraph(place, null).createRun(), text);
        } else {
            renderBlocks(node, place);
        }
    }

    private void heading(Place place, int level, Node inlines, String plain) {
        if (!place.container.isBody()) {
            // a heading inside a footnote stays out of the outline and the table of contents
            XWPFParagraph paragraph = paragraph(place, null);
            if (inlines != null) renderInlines(inlines, paragraph, Format.PLAIN.withBold());
            else Texts.write(run(paragraph, Format.PLAIN.withBold()), plain);
            return;
        }
        XWPFParagraph paragraph = place.container.paragraph();
        // spacing from the heading style, not the extra space added after a table
        if (paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetSpacing()) paragraph.getCTP().getPPr().unsetSpacing();
        paragraph.setStyle(composition.styles.id(Styles.Style.heading(level)));
        String text = inlines != null ? Texts.plainText(inlines) : plain;
        composition.anchors.bookmarkHeading(paragraph, level, text, composition.headingNumbering.next(level), id, () -> {
            if (inlines != null) renderInlines(inlines, paragraph, Format.PLAIN);
            else Texts.write(run(paragraph, Format.PLAIN), plain);
        });
    }

    private XWPFParagraph paragraph(Place place, BigInteger numberingId) {
        XWPFParagraph paragraph = place.container.paragraph();
        if (place.container.isBody()) {
            if (place.listLevel >= 0) paragraph.setStyle(composition.styles.id(Styles.Style.LIST_PARAGRAPH));
            else if (place.quoteDepth > 0) paragraph.setStyle(composition.styles.id(Styles.Style.QUOTE));
        }
        if (numberingId != null) {
            paragraph.setNumID(numberingId);
            paragraph.setNumILvl(BigInteger.valueOf(Math.min(place.listLevel, 8)));
        } else if (place.indent() > 0) {
            paragraph.setIndentationLeft(place.indent());
        }
        return paragraph;
    }

    private void renderList(ListBlock list, Place place) {
        boolean ordered = list instanceof OrderedList;
        Integer start = list instanceof OrderedList orderedList ? orderedList.getMarkerStartNumber() : null;
        BigInteger numberingId = composition.lists.newList(ordered, start == null ? 1 : start);
        Place inner = place.inList(place.listLevel + 1);
        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            Node first = item.getFirstChild();
            String checkbox = null;
            if (first instanceof TaskListItemMarker marker) {
                checkbox = marker.isChecked() ? "☒ " : "☐ ";
                first = first.getNext();
            }
            // the numbered paragraph is the item's first paragraph, or an empty one when the item starts otherwise
            XWPFParagraph paragraph = paragraph(inner, numberingId);
            if (checkbox != null) Texts.write(paragraph.createRun(), checkbox);
            Node rest = first;
            if (first instanceof Paragraph) {
                renderInlines(first, paragraph, Format.PLAIN);
                rest = first.getNext();
            }
            for (Node node = rest; node != null; node = node.getNext()) renderBlock(node, inner);
        }
    }

    private void renderCode(String code, Place place) {
        XWPFParagraph paragraph = paragraph(place, null);
        paragraph.setStyle(composition.styles.id(Styles.Style.SOURCE_CODE));
        if (place.indent() > 0) paragraph.setIndentationLeft(place.indent() + 96);
        String[] lines = code.replaceAll("\r\n?", "\n").replaceAll("\n$", "").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) paragraph.createRun().addBreak();
            Texts.write(paragraph.createRun(), lines[i].replace("\t", "    "));
        }
    }

    private void renderInlines(Node parent, XWPFParagraph paragraph, Format format) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) renderInline(node, paragraph, format);
    }

    private void renderInline(Node node, XWPFParagraph paragraph, Format format) {
        if (node instanceof Text text) {
            Texts.write(run(paragraph, format), text.getLiteral());
        } else if (node instanceof StrongEmphasis) {
            renderInlines(node, paragraph, format.withBold());
        } else if (node instanceof Emphasis) {
            renderInlines(node, paragraph, format.withItalic());
        } else if (node instanceof Strikethrough) {
            renderInlines(node, paragraph, format.withStrike());
        } else if (node instanceof Code code) {
            XWPFRun run = run(paragraph, format);
            if (format.link instanceof Links.TextOnly) run.setStyle(composition.styles.id(Styles.Style.VERBATIM_CHARACTER));
            Texts.write(run, code.getLiteral());
        } else if (node instanceof Link link) {
            renderInlines(node, paragraph, format.withLink(Links.target(link.getDestination())));
        } else if (node instanceof SoftLineBreak) {
            if (!betweenWideCharacters(node)) Texts.write(run(paragraph, format), " ");
        } else if (node instanceof HardLineBreak) {
            run(paragraph, format).addBreak();
        } else if (node instanceof HtmlInline html) {
            if (html.getLiteral().matches("(?i)<br\\s*/?>")) run(paragraph, format).addBreak();
        } else if (node instanceof Image image) {
            XWPFRun run = run(paragraph, format);
            if (!Images.add(run, composition.options.imageResolver, image, baseDirectory, imageWidthTwips, composition.page.textHeightTwips())) {
                String alternative = Texts.plainText(image);
                Texts.write(run, "[" + (alternative.isEmpty() ? "image" : alternative) + "]");
            }
        } else if (node instanceof FootnoteReference reference) {
            renderFootnoteReference(reference, paragraph);
        } else {
            renderInlines(node, paragraph, format);
        }
    }

    private XWPFRun run(XWPFParagraph paragraph, Format format) {
        XWPFRun run = switch (format.link) {
            case Links.External external -> paragraph.createHyperlinkRun(external.url());
            case Links.Internal internal -> composition.anchors.link(paragraph, id, internal.anchor());
            case Links.TextOnly textOnly -> paragraph.createRun();
        };
        if (!(format.link instanceof Links.TextOnly)) run.setStyle(composition.styles.id(Styles.Style.HYPERLINK));
        if (format.bold) run.setBold(true);
        if (format.italic) run.setItalic(true);
        if (format.strike) run.setStrikeThrough(true);
        return run;
    }

    private void renderFootnoteReference(FootnoteReference reference, XWPFParagraph paragraph) {
        FootnoteDefinition definition = footnoteDefinitions.get(reference.getLabel());
        if (definition == null) {
            Texts.write(paragraph.createRun(), "[^" + reference.getLabel() + "]");
            return;
        }
        BigInteger footnoteId = composition.footnotes.idFor(id, reference.getLabel(), () -> renderFootnote(definition));
        XWPFRun run = paragraph.createRun();
        run.setStyle(composition.styles.id(Styles.Style.FOOTNOTE_REFERENCE));
        run.getCTR().addNewFootnoteReference().setId(footnoteId);
    }

    private BigInteger renderFootnote(FootnoteDefinition definition) {
        XWPFFootnote footnote = composition.footnotes.create();
        boolean[] first = {true};
        Container container = new Container() {
            @Override
            public XWPFParagraph paragraph() {
                XWPFParagraph paragraph = footnote.createParagraph();
                paragraph.setStyle(composition.styles.id(Styles.Style.FOOTNOTE_TEXT));
                if (first[0]) {
                    first[0] = false;
                    // the footnote's own number goes in front of its first paragraph
                    if (paragraph.getRuns().isEmpty()) footnote.ensureFootnoteRef(paragraph);
                    paragraph.getRuns().get(0).setStyle(composition.styles.id(Styles.Style.FOOTNOTE_REFERENCE));
                    Texts.write(paragraph.createRun(), " ");
                }
                return paragraph;
            }

            @Override public XWPFTable table(int rows, int columns) { return footnote.createTable(rows, columns); }

            @Override public List<IBodyElement> elements() { return footnote.getBodyElements(); }

            @Override public boolean isBody() { return false; }
        };
        renderBlocks(definition, new Place(container, -1, 0));
        if (first[0]) container.paragraph();
        return footnote.getId();
    }

    /** A source line break between two Chinese characters must not become a space. */
    private static boolean betweenWideCharacters(Node softBreak) {
        Node previous = softBreak.getPrevious();
        Node next = softBreak.getNext();
        String before = previous == null ? "" : Texts.plainText(previous);
        String after = next == null ? "" : Texts.plainText(next);
        return !before.isEmpty() && !after.isEmpty()
                && Texts.isWide(before.codePointBefore(before.length())) && Texts.isWide(after.codePointAt(0));
    }
}
