package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static md2docx.TestSupport.compose;
import static md2docx.TestSupport.open;
import static md2docx.TestSupport.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossFragmentTest {

    @Test
    void sameFootnoteLabelInTwoFragmentsGivesTwoFootnotes() throws Exception {
        byte[] docx = compose(Section.of("A", "a[^1] again[^1]\n\n[^1]: first note"), Section.of("B", "b[^1]\n\n[^1]: second note"));
        try (XWPFDocument document = open(docx)) {
            List<String> notes = document.getFootnotes().stream()
                    .filter(note -> note.getId().intValue() > 0)
                    .map(note -> note.getParagraphs().stream().map(paragraph -> paragraph.getText()).reduce("", String::concat).strip()).toList();
            assertEquals(List.of("first note", "second note"), notes);
        }
        String xml = part(docx, "word/document.xml");
        List<String> references = Pattern.compile("<w:footnoteReference w:id=\"(\\d+)\"/>").matcher(xml).results().map(m -> m.group(1)).toList();
        assertEquals(3, references.size());
        assertEquals(references.get(0), references.get(1));
        assertFalse(references.get(0).equals(references.get(2)));
        String footnotes = part(docx, "word/footnotes.xml");
        assertTrue(footnotes.contains("w:type=\"separator\" w:id=\"-1\""));
        assertTrue(footnotes.contains("w:type=\"continuationSeparator\" w:id=\"0\""));
    }

    @Test
    void referenceWithoutDefinitionStaysText() {
        byte[] docx = compose(Section.untitled("x[^missing]"));
        assertTrue(part(docx, "word/document.xml").contains("[^missing]"));
        assertEquals(null, part(docx, "word/footnotes.xml"));
    }

    @Test
    void everyOrderedListStartsAtItsOwnFirstNumber() {
        byte[] docx = compose(Section.of("A", "1. a\n2. b"), Section.of("B", "3. c\n4. d"), Section.of("C", "1. e"));
        String numbering = part(docx, "word/numbering.xml");
        List<String> starts = Pattern.compile("<w:num w:numId=\"(\\d+)\".*?<w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"(\\d+)\"/>")
                .matcher(numbering).results().map(match -> match.group(1) + ":" + match.group(2)).toList();
        assertEquals(List.of("1:1", "2:3", "3:1"), starts);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Getting Started      | getting-started",
            "API v2.0 (beta)!     | api-v20-beta",
            "接口 设计            | 接口-设计",
            "snake_case-and-dash  | snake_case-and-dash",
            "Ünïcode             | Ünïcode",
            "'  two  spaces'      | --two--spaces",
    })
    void anchorsFollowGitHubRules(String heading, String anchor) {
        assertEquals(anchor, Anchors.anchor(heading));
    }

    @Test
    void linkFindsAHeadingInAnotherFragmentAndPrefersItsOwnFragment() {
        byte[] docx = compose(
                Section.of("A", "# Setup\n\nsee [b](#usage) and [own](#setup)"),
                Section.of("B", "# Setup\n\n# Usage\n\nsee [own](#setup)"));
        String xml = part(docx, "word/document.xml");
        String setupInA = bookmarkOf(xml, "Setup", 0);
        String setupInB = bookmarkOf(xml, "Setup", 1);
        String usage = bookmarkOf(xml, "Usage", 0);
        List<String> anchors = Pattern.compile("<w:hyperlink w:anchor=\"([^\"]+)\"").matcher(xml).results().map(m -> m.group(1)).toList();
        assertEquals(List.of(usage, setupInA, setupInB), anchors);
    }

    @Test
    void repeatedHeadingsGetNumberedAnchorsWithinAFragment() {
        byte[] docx = compose(Section.untitled("# Note\n\n# Note\n\n[second](#note-1)"));
        String xml = part(docx, "word/document.xml");
        assertTrue(xml.contains("<w:hyperlink w:anchor=\"" + bookmarkOf(xml, "Note", 1) + "\""));
    }

    @Test
    void linkWithoutTargetBecomesPlainText() throws Exception {
        byte[] docx = compose(Section.untitled("go [nowhere](#missing) **[bold](#missing)**"));
        String xml = part(docx, "word/document.xml");
        assertFalse(xml.contains("<w:hyperlink"));
        assertFalse(xml.contains("Hyperlink"));
        try (XWPFDocument document = open(docx)) {
            assertEquals("go nowhere bold", document.getParagraphs().get(0).getText());
            assertTrue(document.getParagraphs().get(0).getRuns().stream().anyMatch(run -> run.text().equals("bold") && run.isBold()));
        }
    }

    @Test
    void adjacentTablesFromTwoFragmentsAreKeptApart() throws Exception {
        String table = "| a |\n| - |\n| 1 |";
        byte[] docx = compose(Section.untitled(table), Section.untitled(table));
        try (XWPFDocument document = open(docx)) {
            assertEquals(2, document.getTables().size());
            assertTrue(document.getBodyElements().get(1) instanceof org.apache.poi.xwpf.usermodel.XWPFParagraph);
        }
    }

    /** The bookmark name around the n-th heading with this text. */
    private static String bookmarkOf(String xml, String text, int occurrence) {
        Matcher matcher = Pattern.compile("<w:bookmarkStart w:name=\"([^\"]+)\" w:id=\"\\d+\"/><w:r><w:t>" + Pattern.quote(text) + "</w:t>")
                .matcher(xml);
        for (int i = 0; i <= occurrence; i++) assertTrue(matcher.find(), "heading " + text + " #" + i);
        return matcher.group(1);
    }
}
