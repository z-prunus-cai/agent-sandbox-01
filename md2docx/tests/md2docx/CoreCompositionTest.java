package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static md2docx.TestSupport.compose;
import static md2docx.TestSupport.open;
import static md2docx.TestSupport.paragraphWithText;
import static md2docx.TestSupport.paragraphs;
import static md2docx.TestSupport.part;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreCompositionTest {

    @Test
    void fragmentHeadingsMoveBelowTheSectionHeadingKeepingTheirRelativeLevels() {
        byte[] docx = compose(Section.of("A", "## two\n\n#### four\n\ntext"), Section.of("B", "# one"));
        assertEquals(List.of("Heading1: A", "Heading2: two", "Heading4: four", "-: text", "Heading1: B", "Heading2: one"),
                paragraphs(docx));
    }

    @Test
    void fromMarkdownUsesTheLeadingHeadingAsTitleAndRemovesIt() {
        byte[] docx = compose(Section.fromMarkdown("# Title\n\n## Inner\n\nbody"));
        assertEquals(List.of("Heading1: Title", "Heading2: Inner", "-: body"), paragraphs(docx));
    }

    @Test
    void fromMarkdownWithoutLeadingHeadingHasNoTitle() {
        byte[] docx = compose(Section.fromMarkdown("body\n\n# Later"));
        assertEquals(List.of("-: body", "Heading1: Later"), paragraphs(docx));
    }

    @Test
    void titledSectionGroupsChildrenOneLevelDeeper() {
        byte[] docx = compose(Section.titled("Group").child(Section.of("Child", "# inner")).child(Section.titled("Second")));
        assertEquals(List.of("Heading1: Group", "Heading2: Child", "Heading3: inner", "Heading2: Second"), paragraphs(docx));
    }

    @Test
    void untitledSectionContinuesUnderTheParentAndItsHeadingsAreSiblingsOfSectionHeadings() {
        byte[] docx = compose(Section.titled("Group").child(Section.untitled("intro\n\n## Part")).child(Section.titled("Next")));
        assertEquals(List.of("Heading1: Group", "-: intro", "Heading2: Part", "Heading2: Next"), paragraphs(docx));
    }

    @Test
    void headingsDeeperThanNineStayAtNine() {
        Section deepest = Section.titled("9");
        Section root = deepest;
        for (int level = 8; level >= 1; level--) root = Section.titled(String.valueOf(level)).child(root);
        deepest.child(Section.of("10", "# 11"));
        List<String> result = paragraphs(compose(root));
        assertEquals("Heading9: 9", result.get(8));
        assertEquals("Heading9: 10", result.get(9));
        assertEquals("Heading9: 11", result.get(10));
    }

    @Test
    void blankTitleIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Section.of(" ", "x"));
        assertThrows(IllegalArgumentException.class, () -> Section.titled(null));
    }

    @Test
    void inlineFormattingBecomesRunProperties() throws Exception {
        byte[] docx = compose(Section.untitled("**bold** *italic* ~~strike~~ `code`"));
        try (XWPFDocument document = open(docx)) {
            List<XWPFRun> runs = document.getParagraphs().get(0).getRuns();
            assertTrue(runs.stream().anyMatch(run -> run.text().equals("bold") && run.isBold()));
            assertTrue(runs.stream().anyMatch(run -> run.text().equals("italic") && run.isItalic()));
            assertTrue(runs.stream().anyMatch(run -> run.text().equals("strike") && run.isStrikeThrough()));
            assertTrue(runs.stream().anyMatch(run -> run.text().equals("code") && "VerbatimChar".equals(run.getStyle())));
            assertFalse(runs.stream().anyMatch(run -> run.text().equals("bold") && run.isItalic()));
        }
    }

    @Test
    void listsUseWordNumberingAndNestedItemsUseTheNextLevel() throws Exception {
        byte[] docx = compose(Section.untitled("- a\n  - b\n\n1. one"));
        String xml = part(docx, "word/document.xml");
        assertTrue(xml.contains("<w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr></w:pPr><w:r><w:t>a</w:t>"));
        assertTrue(xml.contains("<w:ilvl w:val=\"1\"/><w:numId w:val=\"2\"/></w:numPr></w:pPr><w:r><w:t>b</w:t>"));
        assertTrue(xml.contains("<w:ilvl w:val=\"0\"/><w:numId w:val=\"3\"/></w:numPr></w:pPr><w:r><w:t>one</w:t>"));
        try (XWPFDocument document = open(docx)) {
            assertEquals("bullet", paragraphWithText(document, "a").getNumFmt());
            assertEquals("decimal", paragraphWithText(document, "one").getNumFmt());
        }
    }

    @Test
    void taskListItemsGetBallotBoxes() {
        List<String> result = paragraphs(compose(Section.untitled("- [x] done\n- [ ] open")));
        assertEquals(List.of("ListParagraph: ☒ done", "ListParagraph: ☐ open"), result);
    }

    @Test
    void codeBlockKeepsIndentationAndEmptyLines() throws Exception {
        byte[] docx = compose(Section.untitled("```\nif (x) {\n\n    y();\n}\n```"));
        try (XWPFDocument document = open(docx)) {
            XWPFParagraph code = document.getParagraphs().get(0);
            assertEquals("SourceCode", code.getStyle());
            assertEquals("if (x) {\n\n    y();\n}", code.getText());
        }
        assertTrue(part(docx, "word/document.xml").contains("xml:space=\"preserve\">    y();"));
    }

    @Test
    void quotesNestAndThematicBreakIsABottomBorder() throws Exception {
        byte[] docx = compose(Section.untitled("> outer\n>\n> > inner\n\n---"));
        try (XWPFDocument document = open(docx)) {
            XWPFParagraph outer = paragraphWithText(document, "outer");
            XWPFParagraph inner = paragraphWithText(document, "inner");
            assertEquals("Quote", outer.getStyle());
            assertTrue(inner.getIndentationLeft() > outer.getIndentationLeft());
        }
        assertTrue(part(docx, "word/document.xml").contains("<w:pBdr><w:bottom w:val=\"single\""));
    }

    @Test
    void tableKeepsAlignmentRepeatsTheHeaderAndDoesNotSplitRows() throws Exception {
        byte[] docx = compose(Section.untitled("| left | center | right |\n| :-- | :-: | --: |\n| a | b | c |"));
        try (XWPFDocument document = open(docx)) {
            XWPFTable table = document.getTables().get(0);
            assertTrue(table.getRow(0).isRepeatHeader());
            assertFalse(table.getRow(1).isRepeatHeader());
            assertTrue(table.getRow(1).isCantSplitRow());
            assertEquals("CENTER", table.getRow(1).getCell(1).getParagraphs().get(0).getAlignment().name());
            assertEquals("RIGHT", table.getRow(1).getCell(2).getParagraphs().get(0).getAlignment().name());
            assertTrue(table.getRow(0).getCell(0).getParagraphs().get(0).getRuns().get(0).isBold());
        }
    }

    @Test
    void columnWidthsFollowContentWithWideCharactersCountingDouble() {
        int[] widths = TestSupport.open(compose(Section.untitled("| a | b |\n| - | - |\n| 一二三四五六七八 | abcdefgh |")))
                .getTables().get(0).getCTTbl().getTblGrid().getGridColList().stream()
                .mapToInt(column -> ((java.math.BigInteger) column.getW()).intValue()).toArray();
        assertEquals(2, widths.length);
        assertEquals(2.0, widths[0] / (double) widths[1], 0.01);
    }

    @Test
    void breakTagsBecomeLineBreaksAndOtherHtmlKeepsOnlyText() {
        byte[] docx = compose(Section.untitled("a<br>b <span>c</span>\n\n<div>block <b>text</b></div>"));
        String xml = part(docx, "word/document.xml");
        assertTrue(xml.contains("<w:br/>"));
        List<String> result = paragraphs(docx);
        assertEquals("-: a\nb c", result.get(0));
        assertEquals("-: block text", result.get(1));
        assertFalse(xml.contains("span"));
    }

    @Test
    void sourceLineBreakBetweenChineseCharactersAddsNoSpace() {
        List<String> result = paragraphs(compose(Section.untitled("中文\n文字\n\nlatin\ntext")));
        assertEquals(List.of("-: 中文文字", "-: latin text"), result);
    }

    @Test
    void emptyInputGivesAValidDocument() throws Exception {
        byte[] docx = compose();
        try (XWPFDocument document = open(docx)) {
            assertEquals(1, document.getBodyElements().size());
        }
        assertEquals(List.of("-: "), paragraphs(compose(Section.untitled(""))));
    }

    @Test
    void oneComposerGivesTheSameDocumentToConcurrentCallers() throws Exception {
        MarkdownDocxComposer composer = new MarkdownDocxComposer(ComposeOptions.builder().tableOfContents(2).build());
        List<Section> sections = List.of(Section.of("A", "x[^1]\n\n[^1]: n\n\n## B\n\n[link](#b)"));
        byte[] expected = composer.compose(sections);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<byte[]>> results = new ArrayList<>();
            for (int i = 0; i < 32; i++) results.add(pool.submit(() -> composer.compose(sections)));
            for (Future<byte[]> result : results) {
                assertArrayEquals(stripTimestamps(expected), stripTimestamps(result.get()));
            }
        } finally {
            pool.shutdown();
        }
    }

    /** The ZIP entries carry modification times; compare the document part instead. */
    private static byte[] stripTimestamps(byte[] docx) {
        return (part(docx, "word/document.xml") + part(docx, "word/footnotes.xml") + part(docx, "word/numbering.xml")).getBytes();
    }
}
