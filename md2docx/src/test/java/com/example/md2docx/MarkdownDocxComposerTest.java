package com.example.md2docx;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFtnEdn;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownDocxComposerTest {

    @TempDir
    Path tmp;

    @Test
    void headingLevelsFollowTheOutlineNotTheSource() throws IOException {
        XWPFDocument doc = compose(ComposeOptions.defaults(), List.of(
                DocSection.fromMarkdown("# A\n\ntext\n\n## A1"),
                DocSection.titled("B")
                        .child(DocSection.fromMarkdown("## B1\n\n### B1a"))
                        .child(DocSection.untitled("# C")),
                DocSection.of("D", "#### D1\n\n##### D1a")));

        assertEquals(List.of("1:A", "2:A1", "1:B", "2:B1", "3:B1a", "2:C", "1:D", "2:D1", "3:D1a"), headings(doc));
    }

    @Test
    void footnoteLabelsAreScopedPerFragment() throws IOException {
        XWPFDocument doc = compose(ComposeOptions.defaults(), List.of(
                DocSection.untitled("first[^1]\n\n[^1]: note from fragment one"),
                DocSection.untitled("second[^1] and again[^1]\n\n[^1]: note from fragment two")));

        List<String> notes = doc.getFootnotes().stream()
                .filter(f -> f.getCTFtnEdn().getType() == null || f.getCTFtnEdn().getType() == STFtnEdn.NORMAL)
                .map(f -> text(f).strip())
                .toList();
        assertEquals(List.of("note from fragment one", "note from fragment two"), notes);
        long refs = doc.getParagraphs().stream().mapToLong(p -> p.getCTP().xmlText().split("footnoteReference", -1).length - 1).sum();
        assertEquals(3, refs);
    }

    @Test
    void everyOrderedListRestartsNumbering() throws IOException {
        XWPFDocument doc = compose(ComposeOptions.defaults(), List.of(
                DocSection.untitled("1. a\n2. b\n\ntext\n\n1. c\n"),
                DocSection.untitled("3. d\n4. e\n")));

        List<BigInteger> numIds = doc.getParagraphs().stream().map(XWPFParagraph::getNumID).filter(id -> id != null).toList();
        Set<BigInteger> distinct = new LinkedHashSet<>(numIds);
        assertEquals(3, distinct.size(), "one numbering instance per list");
        List<Integer> starts = distinct.stream().map(id -> {
            CTNum num = doc.getNumbering().getNum(id).getCTNum();
            return num.getLvlOverrideArray(0).getStartOverride().getVal().intValue();
        }).toList();
        assertEquals(List.of(1, 1, 3), starts);
    }

    @Test
    void anchorLinksResolveAcrossFragments() throws IOException {
        XWPFDocument doc = compose(ComposeOptions.defaults(), List.of(
                DocSection.untitled("see [the API](#接口设计) and [nowhere](#missing)"),
                DocSection.fromMarkdown("# 设计\n\n## 接口设计\n\nbody")));

        String bookmark = bookmarkOf(doc, "接口设计");
        List<String> anchors = new ArrayList<>();
        for (XWPFParagraph p : doc.getParagraphs()) {
            for (CTHyperlink h : p.getCTP().getHyperlinkList()) anchors.add(h.getAnchor());
        }
        assertEquals(2, anchors.size());
        assertEquals(bookmark, anchors.get(0));
        assertNull(anchors.get(1), "unknown anchors stay plain text");
    }

    @Test
    void imagesResolveAgainstSectionBaseDirAndCannotEscapeIt() throws IOException {
        Path dir = Files.createDirectories(tmp.resolve("docs"));
        writePng(dir.resolve("inside.png"), 400, 200);
        writePng(tmp.resolve("outside.png"), 400, 200);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", png);
        String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());

        XWPFDocument doc = compose(ComposeOptions.defaults(), List.of(DocSection.untitled(
                "![in](inside.png)\n\n![out](../outside.png)\n\n![abs](" + tmp.resolve("outside.png").toUri() + ")\n\n![data](" + dataUri + ")")
                .baseDir(dir)));

        assertEquals(2, doc.getAllPictures().size());
        String all = doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
        assertTrue(all.contains("[out]"));
        assertTrue(all.contains("[abs]"));
    }

    @Test
    void wideImagesAreScaledToTheTextWidth() throws IOException {
        writePng(tmp.resolve("wide.png"), 3000, 300);
        XWPFDocument doc = compose(ComposeOptions.defaults(), List.of(DocSection.untitled("![w](wide.png)").baseDir(tmp)));
        long cx = doc.getParagraphs().stream().flatMap(p -> p.getRuns().stream()).flatMap(r -> r.getEmbeddedPictures().stream())
                .mapToLong(p -> p.getCTPicture().getSpPr().getXfrm().getExt().getCx()).max().orElseThrow();
        assertTrue(cx <= (11906 - 2 * 1440) * 635L, "picture wider than the text area: " + cx);
    }

    @Test
    void frontMatterTocAndPageBreaks() throws IOException {
        ComposeOptions options = ComposeOptions.builder()
                .title("Report").subtitle("v1").tableOfContents(2).numberHeadings(true)
                .pageBreakBetweenTopLevelSections(true).pageNumbers(true).build();
        XWPFDocument doc = compose(options, List.of(
                DocSection.of("One", "## One-1\n\n### hidden from toc"),
                DocSection.of("Two", "text")));

        List<XWPFParagraph> ps = doc.getParagraphs();
        assertEquals("Report", ps.get(0).getText());
        assertEquals("v1", ps.get(1).getText());
        String toc = ps.stream().filter(p -> p.getStyle() != null && p.getStyle().startsWith("TOC") && !p.getStyle().equals("TOCHeading"))
                .map(XWPFParagraph::getText).collect(Collectors.joining("|"));
        assertEquals("1. One|1.1 One-1|2. Two", toc);
        assertTrue(ps.stream().anyMatch(p -> p.getCTP().xmlText().contains("TOC \\o \"1-2\"")));
        XWPFParagraph tocHeading = ps.stream().filter(p -> "TOCHeading".equals(p.getStyle())).findFirst().orElseThrow();
        assertEquals(BigInteger.ZERO, tocHeading.getNumID(), "front matter must not pick up heading numbering");

        XWPFParagraph one = headingParagraph(doc, "One");
        XWPFParagraph two = headingParagraph(doc, "Two");
        assertTrue(one.isPageBreak(), "first chapter starts after the front matter page");
        assertTrue(two.isPageBreak());
        assertFalse(doc.getFooterList().isEmpty());
        assertTrue(doc.getSettings().getCTSettings().isSetUpdateFields());
    }

    @Test
    void templateStylesAreMatchedByNameAndBodyIsReplaced() throws IOException {
        Path template = tmp.resolve("template.docx");
        try (XWPFDocument t = new XWPFDocument(); OutputStream os = Files.newOutputStream(template)) {
            // what a Chinese-locale Word writes: style id "1" named "heading 1"
            t.createStyles().setStyles(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles.Factory.parse(
                    "<w:styles " + Styles.W_NS + "><w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"a\"><w:name w:val=\"Normal\"/></w:style>"
                            + "<w:style w:type=\"paragraph\" w:styleId=\"1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"a\"/>"
                            + "<w:pPr><w:outlineLvl w:val=\"0\"/></w:pPr><w:rPr><w:color w:val=\"C00000\"/></w:rPr></w:style></w:styles>",
                    Styles.FRAGMENT));
            t.createParagraph().createRun().setText("template placeholder text");
            t.write(os);
        } catch (org.apache.xmlbeans.XmlException e) {
            throw new IOException(e);
        }

        XWPFDocument doc = compose(ComposeOptions.builder().template(template).build(),
                List.of(DocSection.of("Chapter", "body\n\n> quote")));

        assertEquals("1", headingParagraph(doc, "Chapter").getStyle());
        assertTrue(doc.getParagraphs().stream().noneMatch(p -> p.getText().contains("placeholder")));
        assertNotNull(doc.getStyles().getStyle("Quote"), "missing styles are added");
    }

    @Test
    void hostileOrBrokenInputStillProducesAValidDocument() throws IOException {
        String md = "# T\u0000itle\u0007\n\n```java\nunclosed fence\n\n| a | b |\n|---|\n"
                + "\n\n[js](javascript:alert(1)) [file](file:///etc/passwd) [ok](https://example.com)\n\n<script>x</script>\n\n<!-- c -->";
        XWPFDocument doc = compose(ComposeOptions.builder().title("t\u0001").tableOfContents(3).build(), List.of(
                DocSection.untitled(md), DocSection.untitled(""), DocSection.titled("only title")));

        long externalLinks = doc.getParagraphs().stream().flatMap(p -> p.getCTP().getHyperlinkList().stream())
                .filter(h -> h.getId() != null).count();
        assertEquals(0, externalLinks, "fence swallowed the links");
        XWPFDocument doc2 = compose(ComposeOptions.defaults(), List.of(DocSection.untitled(
                "[js](javascript:alert(1)) [file](file:///etc/passwd) [ok](https://example.com)")));
        long links = doc2.getParagraphs().stream().flatMap(p -> p.getCTP().getHyperlinkList().stream()).filter(h -> h.getId() != null).count();
        assertEquals(1, links, "only http(s)/mailto/ftp links become hyperlinks");
    }

    @Test
    void adjacentTablesAreKeptApart() throws IOException {
        String table = "| a | b |\n|---|---|\n| 1 | 2 |\n";
        XWPFDocument doc = compose(ComposeOptions.defaults(), List.of(DocSection.untitled(table), DocSection.untitled(table)));
        List<IBodyElement> body = doc.getBodyElements();
        for (int i = 1; i < body.size(); i++) {
            assertFalse(body.get(i) instanceof XWPFTable && body.get(i - 1) instanceof XWPFTable, "tables at " + (i - 1) + "," + i);
        }
        assertEquals(2, doc.getTables().size());
    }

    // ------------------------------------------------------------------ helpers

    private static XWPFDocument compose(ComposeOptions options, List<DocSection> sections) throws IOException {
        byte[] bytes = new MarkdownDocxComposer(options).compose(sections);
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    private static List<String> headings(XWPFDocument doc) {
        return doc.getParagraphs().stream()
                .filter(p -> p.getStyle() != null && p.getStyle().startsWith("Heading"))
                .map(p -> p.getStyle().substring("Heading".length()) + ":" + p.getText())
                .toList();
    }

    private static XWPFParagraph headingParagraph(XWPFDocument doc, String text) {
        return doc.getParagraphs().stream()
                .filter(p -> p.getText().equals(text) && !p.getCTP().getBookmarkStartList().isEmpty())
                .findFirst().orElseThrow();
    }

    private static String bookmarkOf(XWPFDocument doc, String headingText) {
        List<CTBookmark> starts = headingParagraph(doc, headingText).getCTP().getBookmarkStartList();
        return starts.get(0).getName();
    }

    private static String text(XWPFFootnote f) {
        return f.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
    }

    private static void writePng(Path file, int w, int h) throws IOException {
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", file.toFile());
    }
}
