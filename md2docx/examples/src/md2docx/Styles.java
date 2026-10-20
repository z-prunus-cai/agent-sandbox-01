package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** The styles the composer uses, and the style ID each one has in this document. */
final class Styles {

    static final String WORD_NAMESPACE = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"";
    /** Parses a fragment whose root element is a WordprocessingML element, such as {@code <w:style>}. */
    static final XmlOptions FRAGMENT = new XmlOptions().setLoadReplaceDocumentElement(null);

    enum Style {
        NORMAL("Normal", "Normal"),
        TITLE("Title", "Title"),
        SUBTITLE("Subtitle", "Subtitle"),
        HEADING_1("Heading1", "heading 1"), HEADING_2("Heading2", "heading 2"), HEADING_3("Heading3", "heading 3"),
        HEADING_4("Heading4", "heading 4"), HEADING_5("Heading5", "heading 5"), HEADING_6("Heading6", "heading 6"),
        HEADING_7("Heading7", "heading 7"), HEADING_8("Heading8", "heading 8"), HEADING_9("Heading9", "heading 9"),
        CONTENTS_HEADING("TOCHeading", "TOC Heading"),
        CONTENTS_1("TOC1", "toc 1"), CONTENTS_2("TOC2", "toc 2"), CONTENTS_3("TOC3", "toc 3"),
        CONTENTS_4("TOC4", "toc 4"), CONTENTS_5("TOC5", "toc 5"), CONTENTS_6("TOC6", "toc 6"),
        CONTENTS_7("TOC7", "toc 7"), CONTENTS_8("TOC8", "toc 8"), CONTENTS_9("TOC9", "toc 9"),
        LIST_PARAGRAPH("ListParagraph", "List Paragraph"),
        QUOTE("Quote", "Quote"),
        SOURCE_CODE("SourceCode", "Source Code"),
        VERBATIM_CHARACTER("VerbatimChar", "Verbatim Char"),
        HYPERLINK("Hyperlink", "Hyperlink"),
        FOOTNOTE_TEXT("FootnoteText", "footnote text"),
        FOOTNOTE_REFERENCE("FootnoteReference", "footnote reference"),
        TABLE("MarkdownTable", "Markdown Table");

        final String defaultId;
        final String name;

        Style(String defaultId, String name) {
            this.defaultId = defaultId;
            this.name = name;
        }

        static Style heading(int level) { return values()[HEADING_1.ordinal() + level - 1]; }
        static Style contents(int level) { return values()[CONTENTS_1.ordinal() + level - 1]; }
    }

    private static final int[] HEADING_SIZES = {32, 28, 26, 24, 22, 21, 21, 21, 21};

    private final Map<Style, String> ids = new EnumMap<>(Style.class);
    private final XWPFStyles styles;

    private Styles(XWPFStyles styles) {
        this.styles = styles;
    }

    String id(Style style) {
        return ids.get(style);
    }

    CTStyle definition(Style style) {
        XWPFStyle found = styles.getStyle(ids.get(style));
        return found == null ? null : found.getCTStyle();
    }

    /** Writes the built-in styles into a new document. */
    static Styles create(XWPFDocument document, ComposeOptions options) throws XmlException {
        XWPFStyles target = document.createStyles();
        StringBuilder xml = new StringBuilder("<w:styles ").append(WORD_NAMESPACE).append('>').append(documentDefaults(options));
        for (Style style : Style.values()) xml.append(definitionXml(style, options));
        target.setStyles(CTStyles.Factory.parse(xml.append("</w:styles>").toString(), FRAGMENT));
        Styles result = new Styles(target);
        for (Style style : Style.values()) result.ids.put(style, style.defaultId);
        return result;
    }

    /**
     * Finds each style in a template by its name, not its ID. The ID only links paragraphs to the style inside one
     * document and can be any string (a template may call "heading 1" simply "1"). Styles the template lacks are
     * added.
     */
    static Styles fromTemplate(XWPFDocument document, ComposeOptions options) throws XmlException {
        if (document.getStyles() == null) return create(document, options);
        XWPFStyles target = document.getStyles();
        Styles result = new Styles(target);
        for (Style style : Style.values()) {
            String id = findByName(target, style.name);
            if (id == null && target.styleExist(style.defaultId)) id = style.defaultId;
            if (id == null) {
                target.addStyle(new XWPFStyle(CTStyle.Factory.parse(definitionXml(style, options), FRAGMENT), target));
                id = style.defaultId;
            }
            result.ids.put(style, id);
        }
        return result;
    }

    private static String findByName(XWPFStyles styles, String name) {
        for (XWPFStyle style : styles.getStyles()) {
            if (style.getName() != null && style.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return style.getStyleId();
            }
        }
        return null;
    }

    private static String documentDefaults(ComposeOptions options) {
        return """
            <w:docDefaults><w:rPrDefault><w:rPr>
              <w:rFonts w:ascii="%1$s" w:hAnsi="%1$s" w:eastAsia="%2$s" w:cs="%1$s"/>
              <w:sz w:val="21"/><w:szCs w:val="21"/><w:lang w:val="en-US" w:eastAsia="zh-CN" w:bidi="ar-SA"/>
            </w:rPr></w:rPrDefault>
            <w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="312" w:lineRule="auto"/></w:pPr></w:pPrDefault>
            </w:docDefaults>""".formatted(attribute(options.latinFont), attribute(options.eastAsianFont));
    }

    private static String definitionXml(Style style, ComposeOptions options) {
        String head = "<w:style " + WORD_NAMESPACE + " w:type=\"%s\" w:styleId=\"" + style.defaultId + "\"%s>"
                + "<w:name w:val=\"" + style.name + "\"/>";
        String monospace = attribute(options.monospaceFont);
        return switch (style) {
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
                int level = style.ordinal() - Style.HEADING_1.ordinal();
                int size = HEADING_SIZES[level];
                yield head.formatted("paragraph", "")
                        + "<w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:uiPriority w:val=\"9\"/><w:qFormat/>"
                        + "<w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before=\"" + (level == 0 ? 360 : 240) + "\" w:after=\"120\"/>"
                        + "<w:outlineLvl w:val=\"" + level + "\"/></w:pPr>"
                        + "<w:rPr><w:b/><w:bCs/><w:sz w:val=\"" + size + "\"/><w:szCs w:val=\"" + size + "\"/></w:rPr></w:style>";
            }
            case CONTENTS_HEADING -> head.formatted("paragraph", "") + """
                <w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/>
                <w:pPr><w:keepNext/><w:spacing w:before="240" w:after="240"/><w:jc w:val="center"/></w:pPr>
                <w:rPr><w:b/><w:bCs/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr></w:style>""";
            case CONTENTS_1, CONTENTS_2, CONTENTS_3, CONTENTS_4, CONTENTS_5, CONTENTS_6, CONTENTS_7, CONTENTS_8, CONTENTS_9 -> {
                int level = style.ordinal() - Style.CONTENTS_1.ordinal();
                yield head.formatted("paragraph", "") + "<w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:uiPriority w:val=\"39\"/>"
                        + "<w:pPr><w:tabs><w:tab w:val=\"right\" w:leader=\"dot\" w:pos=\"9016\"/></w:tabs>"
                        + "<w:spacing w:after=\"60\"/><w:ind w:left=\"" + (420 * level) + "\"/></w:pPr></w:style>";
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
                    .formatted(monospace);
            case VERBATIM_CHARACTER -> head.formatted("character", "") + """
                <w:rPr><w:rFonts w:ascii="%1$s" w:hAnsi="%1$s" w:cs="%1$s"/><w:shd w:val="clear" w:color="auto" w:fill="F2F2F2"/></w:rPr></w:style>"""
                    .formatted(monospace);
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

    static String attribute(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
