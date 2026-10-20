package md2docx;

import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.DefinitionMap;
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
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Renders the body of one section: its Markdown blocks and inline content. Each feature lives in its own class. */
final class Fragment {

    private static final Pattern CARRIAGE_RETURN = Pattern.compile("\r\n?");
    /** Space above a paragraph that follows a table, in twips; without it the text touches the table border. */
    private static final int SPACE_AFTER_TABLE = 160;

    private record Place(Container container, int listLevel, int quoteDepth) {
        Place inList(int level) { return new Place(container, level, quoteDepth); }

        Place inQuote() { return new Place(container, listLevel, quoteDepth + 1); }

        int indent() {
            return (listLevel >= 0 ? Lists.INDENT * (listLevel + 1) : 0) + Styles.QUOTE_INDENT * quoteDepth;
        }
    }

    /** One Markdown link: all its runs go into the same Word hyperlink, created with the first run. */
    private static final class OpenLink {
        final Links.Target target;
        CTHyperlink element;

        OpenLink(Links.Target target) {
            this.target = target;
        }
    }

    private record Format(boolean bold, boolean italic, boolean strike, OpenLink link) {
        static final Format PLAIN = new Format(false, false, false, null);

        Format withBold() { return new Format(true, italic, strike, link); }

        Format withItalic() { return new Format(bold, true, strike, link); }

        Format withStrike() { return new Format(bold, italic, true, link); }

        Format withLink(Links.Target target) {
            return new Format(bold, italic, strike, target instanceof Links.TextOnly ? null : new OpenLink(target));
        }
    }

    private final Composition composition;
    private final int id;
    private final Path baseDirectory;
    private final Node root;
    private final Heading titleNode;
    private final int levelBase;
    private final int shallowestLevel;
    private final DefinitionMap<FootnoteDefinition> footnoteDefinitions;
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
        this.shallowestLevel = shallowestHeading(root, titleNode);
        this.footnoteDefinitions = Footnotes.definitions(root);
        this.body = new Container() {
            @Override
            public XWPFParagraph paragraph() {
                List<IBodyElement> elements = composition.document.getBodyElements();
                boolean afterTable = !elements.isEmpty() && elements.get(elements.size() - 1) instanceof XWPFTable;
                XWPFParagraph paragraph = composition.document.createParagraph();
                if (afterTable) paragraph.setSpacingBefore(SPACE_AFTER_TABLE);
                return paragraph;
            }

            @Override public XWPFTable table(int rows, int columns) { return composition.document.createTable(rows, columns); }

            @Override public List<IBodyElement> elements() { return composition.document.getBodyElements(); }

            @Override public boolean isBody() { return true; }
        };
    }

    /** Headings inside footnote definitions do not count: they never become document headings. */
    private static int shallowestHeading(Node root, Heading titleNode) {
        int[] shallowest = {Integer.MAX_VALUE};
        if (root != null) {
            root.accept(new AbstractVisitor() {
                @Override
                public void visit(Heading heading) {
                    if (heading != titleNode) shallowest[0] = Math.min(shallowest[0], heading.getLevel());
                }

                @Override
                public void visit(CustomBlock block) {
                    if (!(block instanceof FootnoteDefinition)) visitChildren(block);
                }
            });
        }
        return shallowest[0] == Integer.MAX_VALUE ? 1 : shallowest[0];
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
            int level = Math.max(1, Math.min(Composition.MAXIMUM_LEVEL, levelBase + heading.getLevel() - shallowestLevel + 1));
            heading(place, level, heading, null);
        } else if (node instanceof Paragraph) {
            XWPFParagraph paragraph = paragraph(place);
            renderInlines(node, paragraph, Format.PLAIN);
            if (Images.onlyImages(node)) paragraph.setAlignment(ParagraphAlignment.CENTER);
        } else if (node instanceof ListBlock list) {
            composition.lists.render(list, place.listLevel + 1, new Lists.Output() {
                @Override
                public XWPFParagraph numberedParagraph(BigInteger numberingId, int level) {
                    XWPFParagraph paragraph = place.container.paragraph();
                    if (place.container.isBody()) paragraph.setStyle(composition.styles.id(Styles.Style.LIST_PARAGRAPH));
                    paragraph.setNumID(numberingId);
                    paragraph.setNumILvl(BigInteger.valueOf(Math.min(level, Composition.MAXIMUM_LEVEL - 1)));
                    return paragraph;
                }

                @Override public void inlines(Node parent, XWPFParagraph paragraph) { renderInlines(parent, paragraph, Format.PLAIN); }

                @Override public void block(Node block, int level) { renderBlock(block, place.inList(level)); }
            });
        } else if (node instanceof BlockQuote) {
            renderBlocks(node, place.inQuote());
        } else if (node instanceof FencedCodeBlock fenced) {
            renderCode(fenced.getLiteral(), place);
        } else if (node instanceof IndentedCodeBlock indented) {
            renderCode(indented.getLiteral(), place);
        } else if (node instanceof ThematicBreak) {
            paragraph(place).setBorderBottom(Borders.SINGLE);
        } else if (node instanceof TableBlock table) {
            Tables.render(table, place.container, composition.styles.id(Styles.Style.TABLE), composition.page.textWidthTwips(),
                    (cell, paragraph, header, widthTwips) -> {
                        int saved = imageWidthTwips;
                        imageWidthTwips = Math.max(widthTwips - Styles.CELL_MARGINS, Styles.CELL_MARGINS);
                        try {
                            renderInlines(cell, paragraph, header ? Format.PLAIN.withBold() : Format.PLAIN);
                        } finally {
                            imageWidthTwips = saved;
                        }
                    });
        } else if (node instanceof HtmlBlock html) {
            List<String> lines = Texts.htmlLines(html.getLiteral());
            if (!lines.isEmpty()) {
                XWPFRun run = paragraph(place).createRun();
                for (int line = 0; line < lines.size(); line++) {
                    if (line > 0) run.addBreak();
                    run.setText(Texts.xmlSafe(lines.get(line)), line);
                }
            }
        } else {
            renderBlocks(node, place);
        }
    }

    private void heading(Place place, int level, Node inlines, String plain) {
        if (!place.container.isBody()) {
            // a heading inside a footnote stays out of the outline and the table of contents
            XWPFParagraph paragraph = paragraph(place);
            if (inlines != null) renderInlines(inlines, paragraph, Format.PLAIN.withBold());
            else Texts.write(run(paragraph, Format.PLAIN.withBold()), plain);
            return;
        }
        XWPFParagraph paragraph = place.container.paragraph();
        // spacing from the heading style, not the extra space added after a table
        if (paragraph.getCTP().isSetPPr() && paragraph.getCTP().getPPr().isSetSpacing()) paragraph.getCTP().getPPr().unsetSpacing();
        paragraph.setStyle(composition.styles.id(Styles.Style.heading(level)));
        String text = inlines != null ? Texts.plainText(inlines) : plain;
        String number = composition.headingNumbering == null ? null : composition.headingNumbering.next(level);
        composition.anchors.bookmarkHeading(paragraph, level, text, number, id, () -> {
            if (inlines != null) renderInlines(inlines, paragraph, Format.PLAIN);
            else Texts.write(run(paragraph, Format.PLAIN), plain);
        });
    }

    private XWPFParagraph paragraph(Place place) {
        XWPFParagraph paragraph = place.container.paragraph();
        if (place.container.isBody()) {
            if (place.listLevel >= 0) paragraph.setStyle(composition.styles.id(Styles.Style.LIST_PARAGRAPH));
            else if (place.quoteDepth > 0) paragraph.setStyle(composition.styles.id(Styles.Style.QUOTE));
        }
        if (place.indent() > 0) paragraph.setIndentationLeft(place.indent());
        return paragraph;
    }

    private void renderCode(String code, Place place) {
        XWPFParagraph paragraph = paragraph(place);
        paragraph.setStyle(composition.styles.id(Styles.Style.SOURCE_CODE));
        if (place.indent() > 0) paragraph.setIndentationLeft(place.indent() + Styles.CODE_INDENT);
        String normalized = CARRIAGE_RETURN.matcher(code).replaceAll("\n");
        if (normalized.endsWith("\n")) normalized = normalized.substring(0, normalized.length() - 1);
        String[] lines = normalized.split("\n", -1);
        XWPFRun run = paragraph.createRun();
        for (int line = 0; line < lines.length; line++) {
            if (line > 0) run.addBreak();
            run.setText(Texts.xmlSafe(lines[line].replace("\t", "    ")), line);
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
            if (format.link == null) run.setStyle(composition.styles.id(Styles.Style.VERBATIM_CHARACTER));
            Texts.write(run, code.getLiteral());
        } else if (node instanceof Link link) {
            renderInlines(node, paragraph, format.withLink(Links.target(link.getDestination())));
        } else if (node instanceof SoftLineBreak) {
            if (!betweenWideCharacters(node)) Texts.write(run(paragraph, format), " ");
        } else if (node instanceof HardLineBreak) {
            run(paragraph, format).addBreak();
        } else if (node instanceof HtmlInline html) {
            if (Texts.LINE_BREAK_TAG.matcher(html.getLiteral()).matches()) run(paragraph, format).addBreak();
        } else if (node instanceof Image image) {
            // POI adds a picture's relationship to the document part, so a picture inside a footnote would be broken
            boolean embed = paragraph.getPart() == composition.document;
            Images.render(run(paragraph, format), image, embed, composition.options.imageResolver, baseDirectory,
                    imageWidthTwips, composition.page.textHeightTwips());
        } else if (node instanceof FootnoteReference reference) {
            composition.footnotes.reference(reference, footnoteDefinitions, paragraph,
                    (definition, container) -> renderBlocks(definition, new Place(container, -1, 0)));
        } else {
            renderInlines(node, paragraph, format);
        }
    }

    private XWPFRun run(XWPFParagraph paragraph, Format format) {
        XWPFRun run;
        OpenLink link = format.link;
        if (link == null) {
            run = paragraph.createRun();
        } else if (link.element != null) {
            run = new XWPFHyperlinkRun(link.element, link.element.addNewR(), paragraph);
        } else {
            XWPFHyperlinkRun first = switch (link.target) {
                case Links.External external -> paragraph.createHyperlinkRun(external.address());
                case Links.Internal internal -> composition.anchors.link(paragraph, id, internal.anchor());
                case Links.TextOnly textOnly -> throw new IllegalStateException("text-only links have no hyperlink");
            };
            link.element = first.getCTHyperlink();
            run = first;
        }
        if (link != null) run.setStyle(composition.styles.id(Styles.Style.HYPERLINK));
        if (format.bold) run.setBold(true);
        if (format.italic) run.setItalic(true);
        if (format.strike) run.setStrikeThrough(true);
        return run;
    }

    /** A source line break between two wide characters (Chinese, Japanese, Korean, full-width) must not become a space. */
    private static boolean betweenWideCharacters(Node softBreak) {
        Node previous = softBreak.getPrevious();
        Node next = softBreak.getNext();
        String before = previous == null ? "" : Texts.plainText(previous);
        String after = next == null ? "" : Texts.plainText(next);
        return !before.isEmpty() && !after.isEmpty()
                && Texts.isWide(before.codePointBefore(before.length())) && Texts.isWide(after.codePointAt(0));
    }
}
