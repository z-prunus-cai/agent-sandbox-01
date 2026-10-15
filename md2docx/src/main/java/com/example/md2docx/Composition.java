package com.example.md2docx;

import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFFootnotes;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFtnEdn;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** State of one compose run: the document being built plus everything shared across fragments. */
final class Composition {

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), StrikethroughExtension.create(),
            TaskListItemsExtension.create(), FootnotesExtension.create(), AutolinkExtension.create());
    static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

    private static final String DOCUMENT_CT = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String TEMPLATE_CT = "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml";

    record OutlineEntry(int level, String text, String bookmark, String label) {
    }

    record Bookmark(String name, BigInteger id) {
    }

    private record PendingAnchor(CTHyperlink link, int fragmentId, String slug) {
    }

    final ComposeOptions options;
    final XWPFDocument doc;
    final Styles styles;
    final int textWidthTwips;
    final int textHeightTwips;

    private BigInteger bulletAbstractId;
    private BigInteger decimalAbstractId;
    private final List<OutlineEntry> outline = new ArrayList<>();
    private final int[] headingCounters = new int[9];
    private int bookmarkSeq;
    private int fragmentSeq;
    private boolean topLevelSeen;
    private final Map<String, String> globalAnchors = new HashMap<>();
    private final Map<Integer, Map<String, String>> fragmentAnchors = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> fragmentSlugCounts = new HashMap<>();
    private final List<PendingAnchor> pendingAnchors = new ArrayList<>();
    private final Map<String, BigInteger> footnoteIds = new HashMap<>();

    Composition(ComposeOptions options) throws IOException {
        this.options = options;
        boolean fromTemplate = options.template() != null;
        if (fromTemplate) {
            try (InputStream in = Files.newInputStream(options.template())) {
                doc = new XWPFDocument(in);
            }
            PackagePart main = doc.getPackagePart();
            if (TEMPLATE_CT.equals(main.getContentType())) doc.getPackage().replaceContentType(TEMPLATE_CT, DOCUMENT_CT);
            for (int i = doc.getBodyElements().size() - 1; i >= 0; i--) doc.removeBodyElement(i);
        } else {
            doc = new XWPFDocument();
            setupA4Page(doc.getDocument().getBody());
        }
        CTSectPr sect = doc.getDocument().getBody().isSetSectPr() ? doc.getDocument().getBody().getSectPr() : null;
        textWidthTwips = pageDimension(sect, true);
        textHeightTwips = pageDimension(sect, false);
        styles = Styles.install(doc, options, fromTemplate);
        setupNumbering();
    }

    // ------------------------------------------------------------------ composing

    void compose(List<DocSection> sections) {
        for (DocSection s : sections) renderSection(s, 1, true);
        for (PendingAnchor a : pendingAnchors) {
            Map<String, String> local = fragmentAnchors.get(a.fragmentId);
            String bookmark = local == null ? null : local.get(a.slug);
            if (bookmark == null) bookmark = globalAnchors.get(a.slug);
            if (bookmark != null) a.link.setAnchor(bookmark);
        }
        List<IBodyElement> body = doc.getBodyElements();
        if (body.isEmpty() || body.get(body.size() - 1) instanceof XWPFTable) doc.createParagraph();
        insertFrontMatter();
        if (options.pageNumbers() && doc.getFooterList().isEmpty()) addPageNumberFooter();
        if (options.tocLevels() > 0 && options.updateFieldsOnOpen()) doc.enforceUpdateFields();
    }

    private void renderSection(DocSection s, int depth, boolean topLevel) {
        depth = Math.min(depth, 9);
        Node root = s.markdown() == null ? null : PARSER.parse(Texts.xmlSafe(s.markdown()));
        Heading titleNode = s.titleFromFirstHeading() && root != null && root.getFirstChild() instanceof Heading h ? h : null;
        boolean hasTitle = s.title() != null || titleNode != null;

        boolean pageBreak = s.pageBreakBeforeOverride() != null
                ? s.pageBreakBeforeOverride()
                : topLevel && topLevelSeen && options.pageBreakBetweenTopLevelSections();
        if (topLevel) topLevelSeen = true;

        int start = doc.getBodyElements().size();
        FragmentRenderer fr = new FragmentRenderer(this, fragmentSeq++, s.baseDir(), root, titleNode, hasTitle ? depth : depth - 1);
        if (hasTitle) fr.sectionHeading(depth, s.title(), titleNode);
        if (root != null) fr.renderBody();
        for (DocSection child : s.childSections()) renderSection(child, hasTitle ? depth + 1 : depth, false);
        if (pageBreak && start < doc.getBodyElements().size()) breakBefore(doc.getBodyElements().get(start));
    }

    /** Starts the given body element on a new page without producing an empty line. */
    private void breakBefore(IBodyElement element) {
        if (element instanceof XWPFParagraph p) {
            p.setPageBreak(true);
        } else {
            XWPFParagraph p = insertBefore(element);
            p.setPageBreak(true);
            p.setSpacingAfter(0);
        }
    }

    private XWPFParagraph insertBefore(IBodyElement element) {
        XmlCursor cursor = element instanceof XWPFParagraph p ? p.getCTP().newCursor() : ((XWPFTable) element).getCTTbl().newCursor();
        try {
            return doc.insertNewParagraph(cursor);
        } finally {
            cursor.close();
        }
    }

    // ------------------------------------------------------------------ headings & anchors

    /** Records a heading for the TOC / internal links and returns the bookmark to wrap it in. */
    Bookmark registerHeading(int level, String text, int fragmentId) {
        int seq = ++bookmarkSeq;
        String name = "_md" + seq; // leading underscore: Word treats it as a hidden bookmark
        String label = null;
        if (options.numberHeadings()) {
            headingCounters[level - 1]++;
            for (int i = level; i < headingCounters.length; i++) headingCounters[i] = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < level; i++) sb.append(i == 0 ? "" : ".").append(headingCounters[i]);
            label = level == 1 ? sb + "." : sb.toString();
        }
        outline.add(new OutlineEntry(level, text, name, label));

        String slug = Texts.slug(text);
        int n = fragmentSlugCounts.computeIfAbsent(fragmentId, k -> new HashMap<>()).merge(slug, 1, Integer::sum) - 1;
        String local = n == 0 ? slug : slug + "-" + n;
        fragmentAnchors.computeIfAbsent(fragmentId, k -> new HashMap<>()).put(local, name);
        globalAnchors.putIfAbsent(local, name);
        return new Bookmark(name, BigInteger.valueOf(seq));
    }

    /** Hyperlink to an in-document heading; the target is resolved after all fragments are rendered. */
    XWPFHyperlinkRun internalLink(XWPFParagraph p, int fragmentId, String slug) {
        CTHyperlink link = p.getCTP().addNewHyperlink();
        pendingAnchors.add(new PendingAnchor(link, fragmentId, slug));
        return new XWPFHyperlinkRun(link, link.addNewR(), p);
    }

    /** Creates a footnote; the first call also adds the separator footnotes Word itself always writes. */
    XWPFFootnote createFootnote() {
        if (doc.getFootnotes().isEmpty()) {
            XWPFFootnotes footnotes = doc.createFootnotes();
            try {
                footnotes.addFootnote(CTFtnEdn.Factory.parse(separatorXml(-1, "separator"), Styles.FRAGMENT));
                footnotes.addFootnote(CTFtnEdn.Factory.parse(separatorXml(0, "continuationSeparator"), Styles.FRAGMENT));
            } catch (XmlException e) {
                throw new IllegalStateException(e);
            }
        }
        return doc.createFootnote();
    }

    private static String separatorXml(int id, String type) {
        return "<w:footnote " + Styles.W_NS + " w:type=\"" + type + "\" w:id=\"" + id + "\"><w:p><w:pPr>"
                + "<w:spacing w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr><w:r><w:" + type + "/></w:r></w:p></w:footnote>";
    }

    BigInteger footnoteId(int fragmentId, String label, Supplier<BigInteger> create) {
        String key = fragmentId + "\u0000" + label; // labels are scoped per fragment: two files may both use [^1]
        BigInteger id = footnoteIds.get(key);
        if (id == null) {
            id = create.get();
            footnoteIds.put(key, id);
        }
        return id;
    }

    // ------------------------------------------------------------------ numbering

    private void setupNumbering() throws IOException {
        XWPFNumbering numbering = doc.createNumbering();
        int next = 1; // above any id a template already uses
        for (XWPFAbstractNum existing : numbering.getAbstractNums()) {
            BigInteger id = existing.getCTAbstractNum().getAbstractNumId();
            if (id != null) next = Math.max(next, id.intValue() + 1);
        }
        try {
            bulletAbstractId = numbering.addAbstractNum(abstractNum(next, false));
            decimalAbstractId = numbering.addAbstractNum(abstractNum(next + 1, true));
            if (options.numberHeadings()) {
                BigInteger headingAbstract = numbering.addAbstractNum(headingAbstractNum(next + 2));
                BigInteger numId = numbering.addNum(headingAbstract);
                for (int level = 1; level <= 9; level++) {
                    CTStyle style = styles.ct(Styles.Key.heading(level));
                    if (style == null) continue;
                    CTPPrGeneral ppr = style.isSetPPr() ? style.getPPr() : style.addNewPPr();
                    if (ppr.isSetNumPr()) continue; // template already numbers its headings
                    CTNumPr numPr = ppr.addNewNumPr();
                    numPr.addNewIlvl().setVal(BigInteger.valueOf(level - 1));
                    numPr.addNewNumId().setVal(numId);
                }
            }
        } catch (XmlException e) {
            throw new IOException("invalid built-in numbering definition", e);
        }
    }

    private static XWPFAbstractNum abstractNum(int id, boolean ordered) throws XmlException {
        String[] bullets = {"\u25CF", "\u25CB", "\u25A0"};
        StringBuilder sb = new StringBuilder("<w:abstractNum ").append(Styles.W_NS).append(" w:abstractNumId=\"").append(id)
                .append("\"><w:multiLevelType w:val=\"hybridMultilevel\"/>");
        for (int l = 0; l < 9; l++) {
            sb.append("<w:lvl w:ilvl=\"").append(l).append("\"><w:start w:val=\"1\"/><w:numFmt w:val=\"")
              .append(ordered ? "decimal" : "bullet").append("\"/><w:lvlText w:val=\"")
              .append(ordered ? "%" + (l + 1) + "." : bullets[l % 3]).append("\"/><w:lvlJc w:val=\"left\"/>")
              .append("<w:pPr><w:ind w:left=\"").append(420 * (l + 1)).append("\" w:hanging=\"420\"/></w:pPr>");
            if (!ordered) sb.append("<w:rPr><w:sz w:val=\"16\"/><w:szCs w:val=\"16\"/></w:rPr>");
            sb.append("</w:lvl>");
        }
        return new XWPFAbstractNum(CTAbstractNum.Factory.parse(sb.append("</w:abstractNum>").toString(), Styles.FRAGMENT));
    }

    private XWPFAbstractNum headingAbstractNum(int id) throws XmlException {
        StringBuilder sb = new StringBuilder("<w:abstractNum ").append(Styles.W_NS).append(" w:abstractNumId=\"").append(id)
                .append("\"><w:multiLevelType w:val=\"multilevel\"/>");
        for (int l = 0; l < 9; l++) {
            StringBuilder text = new StringBuilder();
            for (int i = 1; i <= l + 1; i++) text.append(i == 1 ? "" : ".").append('%').append(i);
            if (l == 0) text.append('.');
            sb.append("<w:lvl w:ilvl=\"").append(l).append("\"><w:start w:val=\"1\"/><w:numFmt w:val=\"decimal\"/>")
              .append("<w:pStyle w:val=\"").append(Styles.xmlAttr(styles.id(Styles.Key.heading(l + 1)))).append("\"/>")
              .append("<w:suff w:val=\"space\"/><w:lvlText w:val=\"").append(text).append("\"/><w:lvlJc w:val=\"left\"/>")
              .append("<w:pPr><w:ind w:left=\"0\" w:firstLine=\"0\"/></w:pPr></w:lvl>");
        }
        return new XWPFAbstractNum(CTAbstractNum.Factory.parse(sb.append("</w:abstractNum>").toString(), Styles.FRAGMENT));
    }

    /** A fresh list instance; ordered lists restart at {@code start} instead of continuing a previous list. */
    BigInteger newListNum(boolean ordered, int start) {
        XWPFNumbering numbering = doc.getNumbering();
        BigInteger id = numbering.addNum(ordered ? decimalAbstractId : bulletAbstractId);
        if (ordered) {
            CTNum ct = numbering.getNum(id).getCTNum();
            for (int l = 0; l < 9; l++) {
                CTNumLvl o = ct.addNewLvlOverride();
                o.setIlvl(BigInteger.valueOf(l));
                o.addNewStartOverride().setVal(BigInteger.valueOf(l == 0 ? Math.max(start, 0) : 1));
            }
        }
        return id;
    }

    // ------------------------------------------------------------------ front matter

    private void insertFrontMatter() {
        boolean hasTitle = options.title() != null && !options.title().isBlank();
        boolean hasSubtitle = options.subtitle() != null && !options.subtitle().isBlank();
        boolean hasToc = options.tocLevels() > 0;
        if (!hasTitle && !hasSubtitle && !hasToc) return;

        IBodyElement first = doc.getBodyElements().get(0);
        Supplier<XWPFParagraph> next = () -> insertBefore(first);
        XWPFParagraph last = null;
        if (hasTitle) {
            last = next.get();
            last.setStyle(styles.id(Styles.Key.TITLE));
            suppressNumbering(last);
            text(last.createRun(), options.title());
        }
        if (hasSubtitle) {
            last = next.get();
            last.setStyle(styles.id(Styles.Key.SUBTITLE));
            suppressNumbering(last);
            text(last.createRun(), options.subtitle());
        }
        if (hasToc) last = insertToc(next);

        if (options.pageBreakAfterFrontMatter()) {
            if (first instanceof XWPFParagraph p) p.setPageBreak(true);
            else last.createRun().addBreak(BreakType.PAGE);
        }
    }

    /**
     * A real TOC field (Word regenerates it with page numbers) whose cached result is pre-filled with
     * linked entries, so viewers that do not update fields (WPS, LibreOffice, previews) still show a TOC.
     */
    private XWPFParagraph insertToc(Supplier<XWPFParagraph> next) {
        XWPFParagraph heading = next.get();
        heading.setStyle(styles.id(Styles.Key.TOC_HEADING));
        suppressNumbering(heading);
        text(heading.createRun(), options.tocTitle());

        List<OutlineEntry> entries = outline.stream().filter(e -> e.level <= options.tocLevels()).toList();
        XWPFParagraph p = next.get();
        fieldChar(p, STFldCharType.BEGIN);
        XWPFRun instr = p.createRun();
        CTText t = instr.getCTR().addNewInstrText();
        t.setStringValue(" TOC \\o \"1-" + options.tocLevels() + "\" \\h \\z \\u ");
        t.setSpace(SpaceAttribute.Space.PRESERVE);
        fieldChar(p, STFldCharType.SEPARATE);
        if (entries.isEmpty()) {
            text(p.createRun(), " ");
        }
        for (int i = 0; i < entries.size(); i++) {
            OutlineEntry e = entries.get(i);
            if (i > 0) p = next.get();
            p.setStyle(styles.id(Styles.Key.toc(e.level)));
            CTHyperlink link = p.getCTP().addNewHyperlink();
            link.setAnchor(e.bookmark);
            XWPFHyperlinkRun r = new XWPFHyperlinkRun(link, link.addNewR(), p);
            text(r, e.label == null ? e.text : e.label + " " + e.text);
        }
        fieldChar(p, STFldCharType.END);
        return p;
    }

    /** Templates often base "TOC Heading" / "Title" on "heading 1", which would inherit the heading numbering. */
    private void suppressNumbering(XWPFParagraph p) {
        if (options.numberHeadings()) p.setNumID(BigInteger.ZERO); // numId 0 = "no numbering"
    }

    private static void fieldChar(XWPFParagraph p, STFldCharType.Enum type) {
        p.createRun().getCTR().addNewFldChar().setFldCharType(type);
    }

    private void addPageNumberFooter() {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph p = footer.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        CTSimpleField field = p.getCTP().addNewFldSimple();
        field.setInstr(" PAGE ");
        CTR r = field.addNewR();
        r.addNewT().setStringValue("1");
    }

    // ------------------------------------------------------------------ page setup

    private static void setupA4Page(CTBody body) {
        CTSectPr sect = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageSz size = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
        size.setW(BigInteger.valueOf(11906));
        size.setH(BigInteger.valueOf(16838));
        CTPageMar mar = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
        mar.setTop(BigInteger.valueOf(1440));
        mar.setBottom(BigInteger.valueOf(1440));
        mar.setLeft(BigInteger.valueOf(1440));
        mar.setRight(BigInteger.valueOf(1440));
        mar.setHeader(BigInteger.valueOf(851));
        mar.setFooter(BigInteger.valueOf(992));
        mar.setGutter(BigInteger.ZERO);
    }

    private static int pageDimension(CTSectPr sect, boolean width) {
        int page = width ? 11906 : 16838;
        int m1 = 1440, m2 = 1440;
        if (sect != null && sect.isSetPgSz()) {
            Object v = width ? sect.getPgSz().getW() : sect.getPgSz().getH();
            if (v != null) page = twips(v, page);
        }
        if (sect != null && sect.isSetPgMar()) {
            CTPageMar mar = sect.getPgMar();
            m1 = twips(width ? mar.getLeft() : mar.getTop(), m1);
            m2 = twips(width ? mar.getRight() : mar.getBottom(), m2);
        }
        return Math.max(page - Math.abs(m1) - Math.abs(m2), 1440);
    }

    private static int twips(Object value, int fallback) {
        if (value == null) return fallback;
        try {
            return new java.math.BigDecimal(value.toString()).intValue();
        } catch (NumberFormatException e) {
            return fallback; // measurements with units ("2.5cm") are rare in practice
        }
    }

    static void text(XWPFRun run, String s) {
        run.setText(Texts.xmlSafe(s));
    }
}
