package com.example.md2docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Style definitions and the mapping from logical styles to the style ids actually present in the document. */
final class Styles {

    static final String W_NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";
    static final XmlOptions FRAGMENT = new XmlOptions().setLoadReplaceDocumentElement(null);

    enum Key {
        NORMAL("Normal", "Normal"),
        TITLE("Title", "Title"),
        SUBTITLE("Subtitle", "Subtitle"),
        HEADING_1("Heading1", "heading 1"), HEADING_2("Heading2", "heading 2"), HEADING_3("Heading3", "heading 3"),
        HEADING_4("Heading4", "heading 4"), HEADING_5("Heading5", "heading 5"), HEADING_6("Heading6", "heading 6"),
        HEADING_7("Heading7", "heading 7"), HEADING_8("Heading8", "heading 8"), HEADING_9("Heading9", "heading 9"),
        TOC_HEADING("TOCHeading", "TOC Heading"),
        TOC_1("TOC1", "toc 1"), TOC_2("TOC2", "toc 2"), TOC_3("TOC3", "toc 3"), TOC_4("TOC4", "toc 4"),
        TOC_5("TOC5", "toc 5"), TOC_6("TOC6", "toc 6"), TOC_7("TOC7", "toc 7"), TOC_8("TOC8", "toc 8"),
        TOC_9("TOC9", "toc 9"),
        LIST_PARAGRAPH("ListParagraph", "List Paragraph"),
        QUOTE("Quote", "Quote"),
        SOURCE_CODE("SourceCode", "Source Code"),
        VERBATIM_CHAR("VerbatimChar", "Verbatim Char"),
        HYPERLINK("Hyperlink", "Hyperlink"),
        FOOTNOTE_TEXT("FootnoteText", "footnote text"),
        FOOTNOTE_REFERENCE("FootnoteReference", "footnote reference"),
        TABLE("MarkdownTable", "Markdown Table");

        final String defaultId;
        final String name;

        Key(String defaultId, String name) {
            this.defaultId = defaultId;
            this.name = name;
        }

        static Key heading(int level) { return values()[HEADING_1.ordinal() + level - 1]; }
        static Key toc(int level) { return values()[TOC_1.ordinal() + level - 1]; }
    }

    private final Map<Key, String> ids = new EnumMap<>(Key.class);
    private final XWPFStyles styles;

    private Styles(XWPFStyles styles) {
        this.styles = styles;
    }

    String id(Key key) {
        return ids.get(key);
    }

    CTStyle ct(Key key) {
        XWPFStyle s = styles.getStyle(ids.get(key));
        return s == null ? null : s.getCTStyle();
    }

    /** Installs the default styles (fresh document) or merges missing ones into the template's styles. */
    static Styles install(XWPFDocument doc, ComposeOptions o, boolean fromTemplate) throws IOException {
        try {
            if (!fromTemplate || doc.getStyles() == null) {
                XWPFStyles st = doc.createStyles();
                StringBuilder sb = new StringBuilder("<w:styles ").append(W_NS).append('>').append(docDefaults(o));
                for (Key k : Key.values()) sb.append(definition(k, o));
                st.setStyles(CTStyles.Factory.parse(sb.append("</w:styles>").toString(), FRAGMENT));
                Styles result = new Styles(st);
                for (Key k : Key.values()) result.ids.put(k, k.defaultId);
                return result;
            }
            XWPFStyles st = doc.getStyles();
            Styles result = new Styles(st);
            for (Key k : Key.values()) {
                String found = findByName(st, k.name);
                if (found == null && st.styleExist(k.defaultId)) found = k.defaultId;
                if (found == null) {
                    st.addStyle(new XWPFStyle(CTStyle.Factory.parse(definition(k, o), FRAGMENT), st));
                    found = k.defaultId;
                }
                result.ids.put(k, found);
            }
            return result;
        } catch (XmlException e) {
            throw new IOException("invalid built-in style definition", e);
        }
    }

    private static String findByName(XWPFStyles st, String name) {
        for (XWPFStyle s : st.getStyles()) {
            if (s.getName() != null && s.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return s.getStyleId();
            }
        }
        return null;
    }

    private static String docDefaults(ComposeOptions o) {
        return """
            <w:docDefaults><w:rPrDefault><w:rPr>
              <w:rFonts w:ascii="%1$s" w:hAnsi="%1$s" w:eastAsia="%2$s" w:cs="%1$s"/>
              <w:sz w:val="21"/><w:szCs w:val="21"/><w:lang w:val="en-US" w:eastAsia="zh-CN" w:bidi="ar-SA"/>
            </w:rPr></w:rPrDefault>
            <w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="312" w:lineRule="auto"/></w:pPr></w:pPrDefault>
            </w:docDefaults>""".formatted(xmlAttr(o.latinFont()), xmlAttr(o.eastAsiaFont()));
    }

    private static final int[] HEADING_SIZES = {32, 28, 26, 24, 22, 21, 21, 21, 21};

    private static String definition(Key k, ComposeOptions o) {
        String id = k.defaultId;
        String head = "<w:style " + W_NS + " w:type=\"%s\" w:styleId=\"" + id + "\"%s><w:name w:val=\"" + k.name + "\"/>";
        String mono = xmlAttr(o.monoFont());
        return switch (k) {
            case NORMAL -> head.formatted("paragraph", " w:default=\"1\"") + "<w:qFormat/></w:style>";
            case TITLE -> head.formatted("paragraph", "") + """
                <w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>
                <w:pPr><w:spacing w:before="240" w:after="240"/><w:jc w:val="center"/></w:pPr>
                <w:rPr><w:b/><w:bCs/><w:sz w:val="44"/><w:szCs w:val="44"/></w:rPr></w:style>""";
            case SUBTITLE -> head.formatted("paragraph", "") + """
                <w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>
                <w:pPr><w:spacing w:after="480"/><w:jc w:val="center"/></w:pPr>
                <w:rPr><w:color w:val="595959"/><w:sz w:val="28"/><w:szCs w:val="28"/></w:rPr></w:style>""";
            case HEADING_1, HEADING_2, HEADING_3, HEADING_4, HEADING_5, HEADING_6, HEADING_7, HEADING_8, HEADING_9 -> {
                int lvl = k.ordinal() - Key.HEADING_1.ordinal();
                int size = HEADING_SIZES[lvl];
                yield head.formatted("paragraph", "") + "<w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:uiPriority w:val=\"9\"/><w:qFormat/>"
                        + "<w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before=\"" + (lvl == 0 ? 360 : 240) + "\" w:after=\"120\"/>"
                        + "<w:outlineLvl w:val=\"" + lvl + "\"/></w:pPr>"
                        + "<w:rPr><w:b/><w:bCs/><w:sz w:val=\"" + size + "\"/><w:szCs w:val=\"" + size + "\"/></w:rPr></w:style>";
            }
            case TOC_HEADING -> head.formatted("paragraph", "") + """
                <w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>
                <w:pPr><w:keepNext/><w:spacing w:before="240" w:after="240"/><w:jc w:val="center"/></w:pPr>
                <w:rPr><w:b/><w:bCs/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr></w:style>""";
            case TOC_1, TOC_2, TOC_3, TOC_4, TOC_5, TOC_6, TOC_7, TOC_8, TOC_9 -> {
                int lvl = k.ordinal() - Key.TOC_1.ordinal();
                yield head.formatted("paragraph", "") + "<w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:uiPriority w:val=\"39\"/>"
                        + "<w:pPr><w:tabs><w:tab w:val=\"right\" w:leader=\"dot\" w:pos=\"9016\"/></w:tabs>"
                        + "<w:spacing w:after=\"60\"/><w:ind w:left=\"" + (420 * lvl) + "\"/></w:pPr></w:style>";
            }
            case LIST_PARAGRAPH -> head.formatted("paragraph", "") + """
                <w:basedOn w:val="Normal"/><w:uiPriority w:val="34"/><w:qFormat/>
                <w:pPr><w:spacing w:after="60"/><w:contextualSpacing/></w:pPr></w:style>""";
            case QUOTE -> head.formatted("paragraph", "") + """
                <w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:uiPriority w:val="29"/><w:qFormat/>
                <w:pPr><w:pBdr><w:left w:val="single" w:sz="18" w:space="8" w:color="BFBFBF"/></w:pBdr><w:ind w:left="240"/></w:pPr>
                <w:rPr><w:color w:val="595959"/></w:rPr></w:style>""";
            case SOURCE_CODE -> head.formatted("paragraph", "") + """
                <w:basedOn w:val="Normal"/>
                <w:pPr><w:keepLines/>
                  <w:pBdr><w:top w:val="single" w:sz="4" w:space="4" w:color="D9D9D9"/><w:left w:val="single" w:sz="4" w:space="4" w:color="D9D9D9"/>
                  <w:bottom w:val="single" w:sz="4" w:space="4" w:color="D9D9D9"/><w:right w:val="single" w:sz="4" w:space="4" w:color="D9D9D9"/></w:pBdr>
                  <w:shd w:val="clear" w:color="auto" w:fill="F5F5F5"/><w:spacing w:before="60" w:after="180" w:line="240" w:lineRule="auto"/>
                  <w:ind w:left="96" w:right="96"/><w:jc w:val="left"/></w:pPr>
                <w:rPr><w:rFonts w:ascii="%1$s" w:hAnsi="%1$s" w:cs="%1$s"/><w:noProof/><w:sz w:val="19"/><w:szCs w:val="19"/></w:rPr></w:style>"""
                    .formatted(mono);
            case VERBATIM_CHAR -> head.formatted("character", "") + """
                <w:rPr><w:rFonts w:ascii="%1$s" w:hAnsi="%1$s" w:cs="%1$s"/><w:shd w:val="clear" w:color="auto" w:fill="F2F2F2"/></w:rPr></w:style>"""
                    .formatted(mono);
            case HYPERLINK -> head.formatted("character", "") + """
                <w:uiPriority w:val="99"/><w:rPr><w:color w:val="0563C1"/><w:u w:val="single"/></w:rPr></w:style>""";
            case FOOTNOTE_TEXT -> head.formatted("paragraph", "") + """
                <w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="0" w:line="240" w:lineRule="auto"/></w:pPr>
                <w:rPr><w:sz w:val="18"/><w:szCs w:val="18"/></w:rPr></w:style>""";
            case FOOTNOTE_REFERENCE -> head.formatted("character", "") + """
                <w:rPr><w:vertAlign w:val="superscript"/></w:rPr></w:style>""";
            case TABLE -> head.formatted("table", "") + """
                <w:pPr><w:spacing w:after="0" w:line="264" w:lineRule="auto"/></w:pPr>
                <w:tblPr><w:tblBorders>
                  <w:top w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/><w:left w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/>
                  <w:bottom w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/><w:right w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/>
                  <w:insideH w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="A6A6A6"/>
                </w:tblBorders><w:tblCellMar><w:top w:w="40" w:type="dxa"/><w:left w:w="100" w:type="dxa"/>
                  <w:bottom w:w="40" w:type="dxa"/><w:right w:w="100" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style>""";
        };
    }

    static String xmlAttr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
