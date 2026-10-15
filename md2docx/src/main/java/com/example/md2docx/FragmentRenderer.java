package com.example.md2docx;

import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblGrid;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblLayoutType;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Renders one Markdown fragment (the body of a {@link DocSection}) into the shared document. */
final class FragmentRenderer {

    private static final Set<String> LINK_SCHEMES = Set.of("http", "https", "mailto", "ftp");
    private static final int LIST_INDENT = 420;
    private static final int QUOTE_INDENT = 360;

    /** Where blocks go: the document body or a footnote. */
    private interface Container {
        XWPFParagraph paragraph();

        XWPFTable table(int rows, int cols);

        List<IBodyElement> elements();

        boolean isBody();
    }

    private record Ctx(Container box, int listLevel, int quoteDepth) {
        Ctx inList(int level) { return new Ctx(box, level, quoteDepth); }

        Ctx inQuote() { return new Ctx(box, listLevel, quoteDepth + 1); }

        int indent() {
            return (listLevel >= 0 ? LIST_INDENT * (listLevel + 1) : 0) + (quoteDepth > 0 ? 240 + QUOTE_INDENT * (quoteDepth - 1) : 0);
        }
    }

    private record Fmt(boolean bold, boolean italic, boolean strike, String url, String anchor) {
        static final Fmt PLAIN = new Fmt(false, false, false, null, null);

        Fmt b() { return new Fmt(true, italic, strike, url, anchor); }

        Fmt i() { return new Fmt(bold, true, strike, url, anchor); }

        Fmt s() { return new Fmt(bold, italic, true, url, anchor); }

        Fmt link(String u) { return new Fmt(bold, italic, strike, u, null); }

        Fmt anchorLink(String a) { return new Fmt(bold, italic, strike, null, a); }
    }

    private final Composition c;
    private final int fragmentId;
    private final Path baseDir;
    private final Node root;
    private final Heading titleNode;
    private final int levelBase;
    private final int minLevel;
    private final Map<String, FootnoteDefinition> footnoteDefs = new HashMap<>();
    private final Container body;
    private int maxImageWidthTwips;

    FragmentRenderer(Composition c, int fragmentId, Path baseDir, Node root, Heading titleNode, int levelBase) {
        this.c = c;
        this.fragmentId = fragmentId;
        this.baseDir = baseDir;
        this.root = root;
        this.titleNode = titleNode;
        this.levelBase = levelBase;
        this.maxImageWidthTwips = c.textWidthTwips;
        int[] min = {Integer.MAX_VALUE};
        if (root != null) {
            root.accept(new AbstractVisitor() {
                @Override
                public void visit(Heading h) {
                    if (h != titleNode) min[0] = Math.min(min[0], h.getLevel());
                }
            });
            for (Node n = root.getFirstChild(); n != null; n = n.getNext()) {
                if (n instanceof FootnoteDefinition fd) footnoteDefs.putIfAbsent(fd.getLabel(), fd);
            }
        }
        this.minLevel = min[0] == Integer.MAX_VALUE ? 1 : min[0];
        this.body = new Container() {
            @Override
            public XWPFParagraph paragraph() {
                List<IBodyElement> els = c.doc.getBodyElements();
                boolean afterTable = !els.isEmpty() && els.get(els.size() - 1) instanceof XWPFTable;
                XWPFParagraph p = c.doc.createParagraph();
                if (afterTable) p.setSpacingBefore(160); // otherwise text sticks to the table's bottom border
                return p;
            }

            @Override public XWPFTable table(int rows, int cols) { return c.doc.createTable(rows, cols); }

            @Override public List<IBodyElement> elements() { return c.doc.getBodyElements(); }

            @Override public boolean isBody() { return true; }
        };
    }

    void sectionHeading(int level, String title, Heading titleNode) {
        heading(new Ctx(body, -1, 0), level, titleNode, title);
    }

    void renderBody() {
        renderBlocks(root, new Ctx(body, -1, 0));
    }

    // ------------------------------------------------------------------ blocks

    private void renderBlocks(Node parent, Ctx ctx) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) renderBlock(n, ctx);
    }

    private void renderBlock(Node n, Ctx ctx) {
        if (n == titleNode || n instanceof FootnoteDefinition || n instanceof LinkReferenceDefinition) {
            return; // title rendered by the section, footnotes rendered where referenced
        }
        if (n instanceof Heading h) {
            int level = Math.max(1, Math.min(9, levelBase + (h.getLevel() - minLevel) + 1));
            heading(ctx, level, h, null);
        } else if (n instanceof Paragraph) {
            XWPFParagraph p = paragraph(ctx, null);
            renderInlines(n, p, Fmt.PLAIN);
            if (isImageOnly(n)) p.setAlignment(ParagraphAlignment.CENTER);
        } else if (n instanceof ListBlock list) {
            renderList(list, ctx);
        } else if (n instanceof BlockQuote) {
            renderBlocks(n, ctx.inQuote());
        } else if (n instanceof FencedCodeBlock || n instanceof IndentedCodeBlock) {
            String code = n instanceof FencedCodeBlock f ? f.getLiteral() : ((IndentedCodeBlock) n).getLiteral();
            renderCode(code, ctx);
        } else if (n instanceof ThematicBreak) {
            XWPFParagraph p = paragraph(ctx, null);
            p.setBorderBottom(Borders.SINGLE);
        } else if (n instanceof TableBlock table) {
            renderTable(table, ctx);
        } else if (n instanceof HtmlBlock html) {
            String text = html.getLiteral().replaceAll("(?s)<!--.*?-->", "").replaceAll("<[^>]+>", "").strip();
            if (!text.isEmpty()) Composition.text(paragraph(ctx, null).createRun(), text);
        } else {
            renderBlocks(n, ctx);
        }
    }

    private void heading(Ctx ctx, int level, Node inlines, String plain) {
        if (!ctx.box.isBody()) { // headings inside footnotes: keep them out of the outline
            XWPFParagraph p = paragraph(ctx, null);
            if (inlines != null) renderInlines(inlines, p, Fmt.PLAIN.b());
            else Composition.text(run(p, Fmt.PLAIN.b()), plain);
            return;
        }
        XWPFParagraph p = ctx.box.paragraph();
        if (p.getCTP().isSetPPr() && p.getCTP().getPPr().isSetSpacing()) p.getCTP().getPPr().unsetSpacing(); // keep style spacing
        p.setStyle(c.styles.id(Styles.Key.heading(level)));
        Composition.Bookmark bm = c.registerHeading(level, inlines != null ? Texts.plainText(inlines) : plain, fragmentId);
        CTBookmark start = p.getCTP().addNewBookmarkStart();
        start.setName(bm.name());
        start.setId(bm.id());
        if (inlines != null) renderInlines(inlines, p, Fmt.PLAIN);
        else Composition.text(run(p, Fmt.PLAIN), plain);
        p.getCTP().addNewBookmarkEnd().setId(bm.id());
    }

    private XWPFParagraph paragraph(Ctx ctx, BigInteger numId) {
        XWPFParagraph p = ctx.box.paragraph();
        if (ctx.box.isBody()) {
            if (ctx.listLevel >= 0) p.setStyle(c.styles.id(Styles.Key.LIST_PARAGRAPH));
            else if (ctx.quoteDepth > 0) p.setStyle(c.styles.id(Styles.Key.QUOTE));
        }
        if (numId != null) {
            p.setNumID(numId);
            p.setNumILvl(BigInteger.valueOf(Math.min(ctx.listLevel, 8)));
        } else if (ctx.indent() > 0) {
            p.setIndentationLeft(ctx.indent());
        }
        return p;
    }

    private void renderList(ListBlock list, Ctx ctx) {
        boolean ordered = list instanceof OrderedList;
        Integer start = ordered ? ((OrderedList) list).getMarkerStartNumber() : null;
        BigInteger numId = c.newListNum(ordered, start == null ? 1 : start);
        Ctx inner = ctx.inList(ctx.listLevel + 1);
        for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            Node first = item.getFirstChild();
            String checkbox = null;
            if (first instanceof TaskListItemMarker marker) {
                checkbox = marker.isChecked() ? "\u2612 " : "\u2610 "; // not U+2611: it has emoji presentation
                first = first.getNext();
            }
            // the numbered paragraph is the item's first paragraph, or an empty one if the item starts with e.g. code
            XWPFParagraph p = paragraph(inner, numId);
            if (checkbox != null) Composition.text(p.createRun(), checkbox);
            Node rest = first;
            if (first instanceof Paragraph) {
                renderInlines(first, p, Fmt.PLAIN);
                rest = first.getNext();
            }
            for (Node n = rest; n != null; n = n.getNext()) renderBlock(n, inner);
        }
    }

    private void renderCode(String code, Ctx ctx) {
        XWPFParagraph p = paragraph(ctx, null);
        p.setStyle(c.styles.id(Styles.Key.SOURCE_CODE));
        if (ctx.indent() > 0) p.setIndentationLeft(ctx.indent() + 96);
        String[] lines = code.replaceAll("\r\n?", "\n").replaceAll("\n$", "").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) p.createRun().addBreak();
            Composition.text(p.createRun(), lines[i].replace("\t", "    "));
        }
    }

    private void renderTable(TableBlock tb, Ctx ctx) {
        List<List<TableCell>> rows = new ArrayList<>();
        for (Node section = tb.getFirstChild(); section != null; section = section.getNext()) {
            for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                List<TableCell> cells = new ArrayList<>();
                for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) cells.add((TableCell) cell);
                rows.add(cells);
            }
        }
        int cols = rows.stream().mapToInt(List::size).max().orElse(0);
        if (cols == 0) return;

        // Markdown has no column widths: weight columns by content (CJK counts double), never narrower than the header
        double[] weight = new double[cols];
        for (int col = 0; col < cols; col++) {
            int header = rows.get(0).size() > col ? Texts.displayWidth(Texts.plainText(rows.get(0).get(col))) : 0;
            weight[col] = Math.min(Math.max(header, 4), 16);
        }
        for (List<TableCell> r : rows) {
            for (int col = 0; col < r.size(); col++) {
                weight[col] = Math.max(weight[col], Math.min(40, Texts.displayWidth(Texts.plainText(r.get(col)))));
            }
        }
        int tableWidth = c.textWidthTwips;
        double total = Arrays.stream(weight).sum();
        int[] widths = new int[cols];
        for (int col = 0; col < cols; col++) widths[col] = (int) (tableWidth * weight[col] / total);

        List<IBodyElement> existing = ctx.box.elements();
        if (!existing.isEmpty() && existing.get(existing.size() - 1) instanceof XWPFTable) {
            ctx.box.paragraph().setSpacingAfter(0); // adjacent tables would be merged by Word
        }
        XWPFTable t = ctx.box.table(rows.size(), cols);
        t.setStyleID(c.styles.id(Styles.Key.TABLE));
        t.setWidth(String.valueOf(tableWidth));
        t.getCTTbl().getTblPr().addNewTblLayout().setType(STTblLayoutType.FIXED);
        CTTblGrid grid = t.getCTTbl().getTblGrid() != null ? t.getCTTbl().getTblGrid() : t.getCTTbl().addNewTblGrid();
        while (grid.sizeOfGridColArray() < cols) grid.addNewGridCol();
        for (int col = 0; col < cols; col++) grid.getGridColArray(col).setW(BigInteger.valueOf(widths[col]));

        int savedMax = maxImageWidthTwips;
        try {
            for (int r = 0; r < rows.size(); r++) {
                XWPFTableRow row = t.getRow(r);
                List<TableCell> cells = rows.get(r);
                boolean header = !cells.isEmpty() && cells.get(0).isHeader();
                if (header) row.setRepeatHeader(true);
                row.setCantSplitRow(true);
                for (int col = 0; col < cols; col++) {
                    XWPFTableCell cell = row.getCell(col);
                    cell.setWidth(String.valueOf(widths[col]));
                    if (header) cell.setColor("E8EEF7");
                    XWPFParagraph p = cell.getParagraphs().get(0);
                    p.setSpacingAfter(0);
                    if (col >= cells.size()) continue;
                    TableCell tc = cells.get(col);
                    if (tc.getAlignment() == TableCell.Alignment.CENTER) p.setAlignment(ParagraphAlignment.CENTER);
                    else if (tc.getAlignment() == TableCell.Alignment.RIGHT) p.setAlignment(ParagraphAlignment.RIGHT);
                    maxImageWidthTwips = Math.max(widths[col] - 200, 200);
                    renderInlines(tc, p, header ? Fmt.PLAIN.b() : Fmt.PLAIN);
                }
            }
        } finally {
            maxImageWidthTwips = savedMax;
        }
    }

    // ------------------------------------------------------------------ inlines

    private void renderInlines(Node parent, XWPFParagraph p, Fmt f) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) renderInline(n, p, f);
    }

    private void renderInline(Node n, XWPFParagraph p, Fmt f) {
        if (n instanceof Text t) {
            Composition.text(run(p, f), t.getLiteral());
        } else if (n instanceof StrongEmphasis) {
            renderInlines(n, p, f.b());
        } else if (n instanceof Emphasis) {
            renderInlines(n, p, f.i());
        } else if (n instanceof Strikethrough) {
            renderInlines(n, p, f.s());
        } else if (n instanceof Code code) {
            XWPFRun r = run(p, f);
            if (f.url == null && f.anchor == null) r.setStyle(c.styles.id(Styles.Key.VERBATIM_CHAR));
            Composition.text(r, code.getLiteral());
        } else if (n instanceof Link link) {
            renderInlines(n, p, linkFormat(f, link.getDestination()));
        } else if (n instanceof SoftLineBreak) {
            if (!betweenCjk(n)) Composition.text(run(p, f), " ");
        } else if (n instanceof HardLineBreak) {
            run(p, f).addBreak();
        } else if (n instanceof HtmlInline html) {
            if (html.getLiteral().matches("(?i)<br\\s*/?>")) run(p, f).addBreak();
        } else if (n instanceof Image image) {
            renderImage(image, p, f);
        } else if (n instanceof FootnoteReference ref) {
            renderFootnoteReference(ref, p);
        } else {
            renderInlines(n, p, f);
        }
    }

    private Fmt linkFormat(Fmt f, String destination) {
        String dest = destination == null ? "" : destination.trim();
        if (dest.startsWith("#") && dest.length() > 1) {
            String fragment = dest.substring(1);
            try {
                fragment = URLDecoder.decode(fragment.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // keep the raw fragment
            }
            return f.anchorLink(fragment.toLowerCase(Locale.ROOT));
        }
        try {
            URI uri = new URI(dest);
            if (uri.getScheme() != null && LINK_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))) return f.link(dest);
        } catch (URISyntaxException ignored) {
            // not a usable URL: render the link text only
        }
        return f; // relative links / javascript: / file: are rendered as plain text
    }

    private XWPFRun run(XWPFParagraph p, Fmt f) {
        XWPFRun r;
        if (f.url != null) {
            r = p.createHyperlinkRun(f.url);
            r.setStyle(c.styles.id(Styles.Key.HYPERLINK));
        } else if (f.anchor != null) {
            r = c.internalLink(p, fragmentId, f.anchor);
            r.setStyle(c.styles.id(Styles.Key.HYPERLINK));
        } else {
            r = p.createRun();
        }
        if (f.bold) r.setBold(true);
        if (f.italic) r.setItalic(true);
        if (f.strike) r.setStrikeThrough(true);
        return r;
    }

    private void renderImage(Image image, XWPFParagraph p, Fmt f) {
        String alt = Texts.plainText(image);
        try {
            byte[] bytes = c.options.imageResolver().resolve(image.getDestination(), baseDir);
            PictureType type = bytes == null ? null : pictureType(bytes);
            int[] size = type == null ? null : pixelSize(bytes);
            if (size != null) {
                double w = size[0] * (double) Units.EMU_PER_PIXEL;
                double h = size[1] * (double) Units.EMU_PER_PIXEL;
                double maxW = (double) maxImageWidthTwips * 635;
                double maxH = c.textHeightTwips * 635 * 0.9;
                double scale = Math.min(1, Math.min(maxW / w, maxH / h));
                XWPFRun r = run(p, f);
                r.addPicture(new ByteArrayInputStream(bytes), type, "image." + type.extension.substring(1),
                        (int) (w * scale), (int) (h * scale));
                if (!alt.isEmpty()) {
                    r.getCTR().getDrawingArray(0).getInlineArray(0).getDocPr().setDescr(Texts.xmlSafe(alt));
                }
                return;
            }
        } catch (Exception e) {
            // unreadable or unsupported image: fall through to the alt text
        }
        Composition.text(run(p, f), "[" + (alt.isEmpty() ? "image" : alt) + "]");
    }

    private static PictureType pictureType(byte[] b) {
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return PictureType.PNG;
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return PictureType.JPEG;
        if (b.length > 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return PictureType.GIF;
        if (b.length > 2 && b[0] == 'B' && b[1] == 'M') return PictureType.BMP;
        return null;
    }

    /** Reads only the image header, not the pixels. */
    private static int[] pixelSize(byte[] bytes) throws java.io.IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                return w > 0 && h > 0 ? new int[]{w, h} : null;
            } finally {
                reader.dispose();
            }
        }
    }

    private void renderFootnoteReference(FootnoteReference ref, XWPFParagraph p) {
        FootnoteDefinition def = footnoteDefs.get(ref.getLabel());
        if (def == null) {
            Composition.text(p.createRun(), "[^" + ref.getLabel() + "]");
            return;
        }
        BigInteger id = c.footnoteId(fragmentId, ref.getLabel(), () -> createFootnote(def));
        XWPFRun r = p.createRun();
        r.setStyle(c.styles.id(Styles.Key.FOOTNOTE_REFERENCE));
        r.getCTR().addNewFootnoteReference().setId(id);
    }

    private BigInteger createFootnote(FootnoteDefinition def) {
        XWPFFootnote fn = c.createFootnote();
        boolean[] first = {true};
        Container box = new Container() {
            @Override
            public XWPFParagraph paragraph() {
                XWPFParagraph p = fn.createParagraph();
                p.setStyle(c.styles.id(Styles.Key.FOOTNOTE_TEXT));
                if (first[0]) {
                    first[0] = false;
                    if (p.getRuns().isEmpty()) fn.ensureFootnoteRef(p); // POI adds the footnote mark to the first paragraph itself
                    p.getRuns().get(0).setStyle(c.styles.id(Styles.Key.FOOTNOTE_REFERENCE));
                    Composition.text(p.createRun(), " ");
                }
                return p;
            }

            @Override public XWPFTable table(int rows, int cols) { return fn.createTable(rows, cols); }

            @Override public List<IBodyElement> elements() { return fn.getBodyElements(); }

            @Override public boolean isBody() { return false; }
        };
        renderBlocks(def, new Ctx(box, -1, 0));
        if (first[0]) box.paragraph();
        return fn.getId();
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isImageOnly(Node paragraph) {
        boolean image = false;
        for (Node n = paragraph.getFirstChild(); n != null; n = n.getNext()) {
            if (n instanceof Image) image = true;
            else if (!(n instanceof Text t && t.getLiteral().isBlank()) && !(n instanceof SoftLineBreak)) return false;
        }
        return image;
    }

    /** Chinese text wrapped across source lines must not get a space inserted at the line break. */
    private static boolean betweenCjk(Node softBreak) {
        Node prev = softBreak.getPrevious(), next = softBreak.getNext();
        String a = prev == null ? "" : Texts.plainText(prev), b = next == null ? "" : Texts.plainText(next);
        return !a.isEmpty() && !b.isEmpty() && Texts.isCjk(a.codePointBefore(a.length())) && Texts.isCjk(b.codePointAt(0));
    }
}
