package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static md2docx.TestSupport.compose;
import static md2docx.TestSupport.count;
import static md2docx.TestSupport.open;
import static md2docx.TestSupport.paragraphs;
import static md2docx.TestSupport.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentLayoutTest {

    private static final Section[] SECTIONS = {
            Section.of("One", "## One A\n\n### One A x\n\n#### deep"),
            Section.of("Two", "text"),
    };

    @Test
    void defaultsAddNothingAroundTheBody() {
        byte[] docx = compose(SECTIONS);
        assertEquals(List.of("Heading1: One", "Heading2: One A", "Heading3: One A x", "Heading4: deep", "Heading1: Two", "-: text"),
                paragraphs(docx));
        String xml = part(docx, "word/document.xml");
        assertFalse(xml.contains("pageBreakBefore"));
        assertFalse(xml.contains("w:type=\"page\""));
        assertFalse(xml.contains("<w:numPr>"));
        assertNull(part(docx, "word/footer1.xml"));
        assertFalse(part(docx, "word/settings.xml").contains("updateFields"));
        assertFalse(part(docx, "word/styles.xml").contains("<w:numPr>"));
    }

    @Test
    void newDocumentIsA4WithInchMarginsAndTheDefaultFonts() {
        byte[] docx = compose(SECTIONS);
        assertTrue(part(docx, "word/document.xml").contains("<w:pgSz w:w=\"11906\" w:h=\"16838\"/>"));
        assertTrue(part(docx, "word/document.xml").contains("w:top=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:right=\"1440\""));
        String styles = part(docx, "word/styles.xml");
        assertTrue(styles.contains("w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Microsoft YaHei\""));
        assertTrue(styles.contains("w:ascii=\"Consolas\""));
    }

    @Test
    void fontsCanBeChanged() {
        byte[] docx = compose(ComposeOptions.builder().fonts("Arial", "SimSun", "Courier New").build(), SECTIONS);
        String styles = part(docx, "word/styles.xml");
        assertTrue(styles.contains("w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:eastAsia=\"SimSun\""));
        assertTrue(TestSupport.styleXml(docx, "SourceCode").contains("w:ascii=\"Courier New\""));
        assertTrue(TestSupport.styleXml(docx, "VerbatimChar").contains("w:ascii=\"Courier New\""));
    }

    @Test
    void titleSubtitleAndContentsComeFirstThenTheBodyOnANewPage() {
        byte[] docx = compose(ComposeOptions.builder().title("Doc").subtitle("Sub").tableOfContents(2).build(), SECTIONS);
        List<String> result = paragraphs(docx);
        assertEquals(List.of("Title: Doc", "Subtitle: Sub", "TOCHeading: 目录", "TOC1: One", "TOC2: One A", "TOC1: Two", "Heading1: One"),
                result.subList(0, 7));
        String xml = part(docx, "word/document.xml");
        assertTrue(xml.contains("<w:pageBreakBefore w:val=\"on\"/></w:pPr><w:bookmarkStart w:name=\"_Heading1\""));
    }

    @Test
    void contentsIsATocFieldWithPrefilledLinkedEntries() {
        byte[] docx = compose(ComposeOptions.builder().tableOfContents(3).tableOfContentsTitle("Contents").build(), SECTIONS);
        String xml = part(docx, "word/document.xml");
        assertTrue(xml.contains("<w:instrText xml:space=\"preserve\"> TOC \\o \"1-3\" \\h \\z \\u </w:instrText>"));
        assertEquals(1, count(xml, "w:fldCharType=\"begin\""));
        assertEquals(1, count(xml, "w:fldCharType=\"separate\""));
        assertEquals(1, count(xml, "w:fldCharType=\"end\""));
        List<String> anchors = Pattern.compile("<w:hyperlink w:anchor=\"([^\"]+)\"").matcher(xml).results().map(m -> m.group(1)).toList();
        assertEquals(List.of("_Heading1", "_Heading2", "_Heading3", "_Heading5"), anchors);
        assertTrue(paragraphs(docx).contains("TOCHeading: Contents"));
        assertTrue(part(docx, "word/settings.xml").contains("<w:updateFields w:val=\"on\"/>"));
    }

    @Test
    void contentsWithoutUpdateOnOpenLeavesTheSettingOut() {
        byte[] docx = compose(ComposeOptions.builder().tableOfContents(1).updateFieldsOnOpen(false).build(), SECTIONS);
        assertFalse(part(docx, "word/settings.xml").contains("updateFields"));
        assertTrue(part(docx, "word/document.xml").contains(" TOC \\o \"1-1\""));
    }

    @Test
    void contentsLevelsAreLimitedToNine() {
        assertThrows(IllegalArgumentException.class, () -> ComposeOptions.builder().tableOfContents(10));
        assertThrows(IllegalArgumentException.class, () -> ComposeOptions.builder().tableOfContents(-1));
    }

    @Test
    void headingNumbersAreAWordListOnTheHeadingStylesAndAppearInTheContents() {
        byte[] docx = compose(ComposeOptions.builder().title("Doc").subtitle("Sub").numberHeadings(true).tableOfContents(4).build(),
                SECTIONS[0], SECTIONS[1], Section.of("Three", "## Three A"));
        String styles = part(docx, "word/styles.xml");
        for (int level = 1; level <= 9; level++) {
            assertTrue(Pattern.compile("w:styleId=\"Heading" + level + "\">.*?<w:numPr><w:ilvl w:val=\"" + (level - 1) + "\"/>")
                    .matcher(styles).find(), "heading " + level);
        }
        String numbering = part(docx, "word/numbering.xml");
        assertTrue(numbering.contains("<w:multiLevelType w:val=\"multilevel\"/>"));
        assertTrue(numbering.contains("<w:lvlText w:val=\"%1.%2.%3\"/>"));
        assertTrue(numbering.contains("<w:lvlText w:val=\"%1.\"/>"));
        assertTrue(numbering.contains("<w:pStyle w:val=\"Heading2\"/>"));
        assertEquals(List.of("TOC1: 1. One", "TOC2: 1.1 One A", "TOC3: 1.1.1 One A x", "TOC4: 1.1.1.1 deep", "TOC1: 2. Two",
                        "TOC1: 3. Three", "TOC2: 3.1 Three A"),
                paragraphs(docx).stream().filter(line -> line.startsWith("TOC") && !line.startsWith("TOCHeading")).toList());
        // the numbers come from the list, not from the heading text
        assertTrue(paragraphs(docx).contains("Heading1: One"));
        String xml = part(docx, "word/document.xml");
        for (String style : List.of("Title", "Subtitle", "TOCHeading")) {
            assertTrue(xml.contains("<w:pStyle w:val=\"" + style + "\"/><w:numPr><w:numId w:val=\"0\"/></w:numPr>"), style);
        }
        assertTrue(TestSupport.styleXml(docx, "Heading2").contains("<w:outlineLvl w:val=\"1\"/>"));
    }

    @Test
    void topLevelSectionsAfterTheFirstStartOnANewPageUnlessASectionSaysOtherwise() {
        byte[] docx = compose(ComposeOptions.builder().pageBreakBetweenTopLevelSections(true).build(),
                Section.of("A", "a"), Section.of("B", "b"), Section.of("C", "c").pageBreakBefore(false),
                Section.titled("D").child(Section.of("E", "e")));
        String xml = part(docx, "word/document.xml");
        List<String> broken = Pattern.compile("<w:pageBreakBefore w:val=\"on\"/></w:pPr><w:bookmarkStart w:name=\"[^\"]+\" w:id=\"\\d+\"/><w:r><w:t>(\\w)</w:t>")
                .matcher(xml).results().map(m -> m.group(1)).toList();
        assertEquals(List.of("B", "D"), broken);
    }

    @Test
    void singleSectionCanAskForAPageBreakWithoutTheOption() {
        byte[] docx = compose(Section.of("A", "a"), Section.of("B", "b").pageBreakBefore(true));
        assertEquals(1, count(part(docx, "word/document.xml"), "<w:pageBreakBefore w:val=\"on\"/>"));
    }

    @Test
    void sectionStartingWithATableGetsAnEmptyBreakingParagraphInFront() throws Exception {
        byte[] docx = compose(ComposeOptions.builder().pageBreakBetweenTopLevelSections(true).build(),
                Section.untitled("a"), Section.untitled("| x |\n| - |\n| 1 |"));
        try (XWPFDocument document = open(docx)) {
            assertEquals("", document.getParagraphs().get(1).getText());
            assertTrue(document.getParagraphs().get(1).isPageBreak());
        }
    }

    @Test
    void frontMatterPageBreakCanBeSwitchedOff() {
        byte[] docx = compose(ComposeOptions.builder().title("Doc").pageBreakAfterFrontMatter(false).build(), SECTIONS);
        assertFalse(part(docx, "word/document.xml").contains("pageBreakBefore"));
    }

    @Test
    void pageNumbersAreACenteredPageFieldInTheFooter() {
        byte[] docx = compose(ComposeOptions.builder().pageNumbers(true).build(), SECTIONS);
        String footer = part(docx, "word/footer1.xml");
        assertTrue(footer.contains("<w:jc w:val=\"center\"/>"));
        assertTrue(footer.contains("<w:fldSimple w:instr=\" PAGE \">"));
        assertTrue(part(docx, "word/document.xml").contains("<w:footerReference w:type=\"default\""));
    }

    @Test
    void titleWithoutBodyAddsNoPageBreak() {
        byte[] docx = compose(ComposeOptions.builder().title("Only").build());
        assertFalse(part(docx, "word/document.xml").contains("pageBreakBefore"));
        assertEquals(List.of("Title: Only", "-: "), paragraphs(docx));
    }

    @Test
    void titleIsAlsoTheDocumentPropertyTitle() {
        byte[] docx = compose(ComposeOptions.builder().title("Doc").build(), SECTIONS);
        assertTrue(part(docx, "docProps/core.xml").contains("<dc:title>Doc</dc:title>"));
    }

    @Test
    void tableUsesTheBordersOfItsStyle() {
        String xml = part(compose(Section.untitled("| a |\n| - |\n| 1 |")), "word/document.xml");
        assertTrue(xml.contains("<w:tblStyle w:val=\"MarkdownTable\"/>"));
        assertFalse(xml.contains("<w:tblBorders>"));
    }

    @Test
    void headerTextCountsAtMostSixteenCharactersForTheColumnWidth() {
        int[] widths = TestSupport.open(compose(Section.untitled("| " + "h".repeat(30) + " | b |\n| - | - |\n| x | y |")))
                .getTables().get(0).getCTTbl().getTblGrid().getGridColList().stream()
                .mapToInt(column -> ((java.math.BigInteger) column.getW()).intValue()).toArray();
        assertEquals(16.0 / 4, widths[0] / (double) widths[1], 0.01);
    }

    @Test
    void contentsForManyHeadingsTakesLinearTime() {
        StringBuilder markdown = new StringBuilder();
        for (int heading = 0; heading < 16_000; heading++) markdown.append("## H").append(heading).append("\n\n");
        ComposeOptions contents = ComposeOptions.builder().tableOfContents(2).build();
        long start = System.nanoTime();
        byte[] docx = compose(contents, Section.of("A", markdown.toString()));
        long millis = (System.nanoTime() - start) / 1_000_000;
        // the previous, quadratic version needed 8 s for this input
        assertTrue(millis < 4_000, millis + " ms");
        List<String> result = paragraphs(docx);
        assertEquals("TOC2: H0", result.get(2));
        assertEquals("TOC2: H15999", result.get(16_001));
        assertEquals("Heading1: A", result.get(16_002));
    }

    @Test
    void bodyStartingWithATableStartsOnANewPageAfterTheFrontMatter() {
        String xml = part(compose(ComposeOptions.builder().title("T").build(), Section.untitled("| a |\n| - |\n| 1 |")), "word/document.xml");
        assertTrue(xml.contains("<w:t>T</w:t></w:r><w:r><w:br w:type=\"page\"/></w:r></w:p><w:tbl>"), xml);
    }
}
