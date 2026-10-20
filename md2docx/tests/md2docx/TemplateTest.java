package md2docx;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static md2docx.TestSupport.compose;
import static md2docx.TestSupport.paragraphs;
import static md2docx.TestSupport.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateTest {

    private static final String DOCUMENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String TEMPLATE_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml";

    @TempDir
    Path directory;

    /**
     * A template whose heading styles are named "heading 1" and "heading 2" but have the IDs "1" and "2", a Letter
     * page with 1-inch margins, a footer, and body text that must not survive.
     */
    private Path template(String fileName, boolean numberedHeadings) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFStyles styles = document.createStyles();
            String numbering = numberedHeadings ? "<w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"7\"/></w:numPr></w:pPr>" : "";
            styles.setStyles(CTStyles.Factory.parse("""
                    <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:style w:type="paragraph" w:default="1" w:styleId="a"><w:name w:val="Normal"/>
                        <w:rPr><w:rFonts w:eastAsia="KaiTi"/></w:rPr></w:style>
                      <w:style w:type="paragraph" w:styleId="1"><w:name w:val="heading 1"/><w:basedOn w:val="a"/>%s
                        <w:rPr><w:color w:val="C00000"/></w:rPr></w:style>
                      <w:style w:type="paragraph" w:styleId="2"><w:name w:val="heading 2"/><w:basedOn w:val="a"/></w:style>
                    </w:styles>""".formatted(numbering), Styles.ELEMENT_OPTIONS));
            var section = document.getDocument().getBody().addNewSectPr();
            section.addNewPgSz().setW(java.math.BigInteger.valueOf(12240));
            section.getPgSz().setH(java.math.BigInteger.valueOf(15840));
            var margins = section.addNewPgMar();
            margins.setLeft(java.math.BigInteger.valueOf(1440));
            margins.setRight(java.math.BigInteger.valueOf(1440));
            margins.setTop(java.math.BigInteger.valueOf(1440));
            margins.setBottom(java.math.BigInteger.valueOf(1440));
            document.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("template footer");
            document.createParagraph().createRun().setText("template body");
            Path file = directory.resolve(fileName);
            try (OutputStream out = Files.newOutputStream(file)) {
                document.write(out);
            }
            return file;
        }
    }

    private static ComposeOptions.Builder options(Path template) {
        return ComposeOptions.builder().template(template);
    }

    @Test
    void templateStylesAreFoundByNameAndItsBodyIsDropped() throws Exception {
        byte[] docx = compose(options(template("t.docx", false)).build(), Section.of("A", "## B\n\ntext"));
        assertEquals(List.of("1: A", "2: B", "-: text"), paragraphs(docx));
        String styles = part(docx, "word/styles.xml");
        assertTrue(styles.contains("KaiTi"));
        assertTrue(styles.contains("w:styleId=\"Heading3\""), "missing heading 3 is added");
        assertTrue(styles.contains("w:styleId=\"SourceCode\""), "missing code style is added");
        assertFalse(part(docx, "word/document.xml").contains("template body"));
    }

    @Test
    void templatePageSetupAndFooterAreKeptAndNoSecondFooterIsAdded() throws Exception {
        byte[] docx = compose(options(template("t.docx", false)).pageNumbers(true).build(), Section.of("A", "a"));
        assertTrue(part(docx, "word/document.xml").contains("<w:pgSz w:w=\"12240\" w:h=\"15840\"/>"));
        assertTrue(part(docx, "word/footer1.xml").contains("template footer"));
        assertEquals(null, part(docx, "word/footer2.xml"));
    }

    @Test
    void tablesUseTheTemplateTextWidth() throws Exception {
        byte[] docx = compose(options(template("t.docx", false)).build(), Section.untitled("| a |\n| - |\n| b |"));
        assertTrue(part(docx, "word/document.xml").contains("<w:tblW w:w=\"" + (12240 - 2 * 1440) + "\""));
    }

    @Test
    void headingNumbersAreNotAddedWhenTheTemplateHeadingAlreadyHasNumbering() throws Exception {
        byte[] docx = compose(options(template("t.docx", true)).numberHeadings(true).build(), Section.of("A", "## B"));
        String styles = part(docx, "word/styles.xml");
        assertTrue(styles.contains("<w:numId w:val=\"7\"/>"));
        assertEquals(1, TestSupport.count(styles.substring(styles.indexOf("w:styleId=\"1\""), styles.indexOf("w:styleId=\"2\"")), "<w:numPr>"));
        assertTrue(styles.substring(styles.indexOf("w:styleId=\"2\"")).contains("<w:numPr><w:ilvl w:val=\"1\"/>"));
    }

    @Test
    void dotxTemplateBecomesADocument() throws Exception {
        Path template = template("t.dotx", false);
        try (OPCPackage templatePackage = OPCPackage.open(template.toFile())) {
            templatePackage.replaceContentType(DOCUMENT_TYPE, TEMPLATE_TYPE);
        }
        assertTrue(part(Files.readAllBytes(template), "[Content_Types].xml").contains(TEMPLATE_TYPE));
        byte[] docx = compose(options(template).build(), Section.of("A", "a"));
        String types = part(docx, "[Content_Types].xml");
        assertTrue(types.contains(DOCUMENT_TYPE));
        assertFalse(types.contains(TEMPLATE_TYPE));
    }

    @Test
    void missingTemplateIsReported() {
        Path missing = directory.resolve("none.docx");
        NoSuchFileException error = assertThrows(NoSuchFileException.class,
                () -> new MarkdownDocxComposer(options(missing).build()).compose(List.of(Section.of("A", "a"))));
        assertTrue(error.getMessage().contains("none.docx"));
    }

    @Test
    void fileThatIsNotWordIsReported() throws Exception {
        Path text = Files.writeString(directory.resolve("plain.docx"), "not a zip");
        IOException error = assertThrows(IOException.class,
                () -> new MarkdownDocxComposer(options(text).build()).compose(List.of(Section.of("A", "a"))));
        assertTrue(error.getMessage().contains("not a .docx or .dotx"));
    }

    private Path templateWithBodyExtras() throws Exception {
        Path file = template("extras.docx", false);
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file))) {
            document.getDocument().getBody().addNewSdt().addNewSdtContent().addNewP().addNewR().addNewT().setStringValue("template control");
            document.createParagraph().createRun().addPicture(new java.io.ByteArrayInputStream(ImagesAndSafetyTest.image("png", 5, 5)),
                    org.apache.poi.common.usermodel.PictureType.PNG, "picture", 9525, 9525);
            try (OutputStream out = Files.newOutputStream(file)) {
                document.write(out);
            }
        }
        return file;
    }

    @Test
    void contentControlsAndPicturesOfTheTemplateBodyAreDropped() throws Exception {
        byte[] docx = compose(options(templateWithBodyExtras()).build(), Section.of("A", "a"));
        assertFalse(part(docx, "word/document.xml").contains("template control"));
        assertFalse(part(docx, "word/document.xml").contains("<w:sdt>"));
        try (XWPFDocument document = TestSupport.open(docx)) {
            assertEquals(0, document.getAllPictures().size());
        }
        assertTrue(part(docx, "word/footer1.xml").contains("template footer"));
    }

    @Test
    void macroEnabledFileIsRefused() throws Exception {
        Path template = template("macro.docx", false);
        try (OPCPackage templatePackage = OPCPackage.open(template.toFile())) {
            templatePackage.replaceContentType(DOCUMENT_TYPE, "application/vnd.ms-word.document.macroEnabled.main+xml");
        }
        IOException error = assertThrows(IOException.class,
                () -> new MarkdownDocxComposer(options(template).build()).compose(List.of(Section.of("A", "a"))));
        assertTrue(error.getMessage().contains("not a .docx or .dotx"));
    }

    @Test
    void listNumbersDoNotReuseTheTemplateNumberingIds() throws Exception {
        Path file = template("numbered.docx", false);
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file))) {
            var numbering = document.createNumbering();
            numbering.addNum(java.math.BigInteger.ONE, java.math.BigInteger.ONE);
            numbering.addNum(java.math.BigInteger.ONE, java.math.BigInteger.valueOf(5));
            try (OutputStream out = Files.newOutputStream(file)) {
                document.write(out);
            }
        }
        byte[] docx = compose(options(file).numberHeadings(true).build(), Section.untitled("1. a\n\n- b\n\n3. c\n\n- d"));
        List<String> ids = java.util.regex.Pattern.compile("<w:num w:numId=\"(\\d+)\"").matcher(part(docx, "word/numbering.xml"))
                .results().map(match -> match.group(1)).toList();
        assertEquals(ids.size(), new java.util.HashSet<>(ids).size(), ids.toString());
        assertTrue(ids.containsAll(List.of("1", "5")));
    }

    @Test
    void contentsHasNoNumbersWhenTheTemplateNumbersItsHeadings() throws Exception {
        byte[] docx = compose(options(template("t.docx", true)).numberHeadings(true).tableOfContents(1).build(), Section.of("A", "a"));
        assertTrue(paragraphs(docx).stream().anyMatch(line -> line.endsWith(": A") && line.startsWith("TOC")), paragraphs(docx).toString());
        assertFalse(paragraphs(docx).stream().anyMatch(line -> line.contains("1. A")));
    }

    @Test
    void numberingDefinitionsDoNotReuseTheTemplateDefinitionIds() throws Exception {
        Path file = template("definitions.docx", false);
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file))) {
            var numbering = document.createNumbering();
            for (int id : new int[] {0, 5}) {
                numbering.addAbstractNum(new org.apache.poi.xwpf.usermodel.XWPFAbstractNum(
                        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum.Factory.parse(
                                "<w:abstractNum xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" w:abstractNumId=\"" + id
                                        + "\"><w:lvl w:ilvl=\"0\"><w:numFmt w:val=\"upperRoman\"/></w:lvl></w:abstractNum>", Styles.ELEMENT_OPTIONS)));
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                document.write(out);
            }
        }
        byte[] docx = compose(options(file).numberHeadings(true).build(), Section.untitled("- a\n\n1. b"));
        String numbering = part(docx, "word/numbering.xml");
        List<String> ids = java.util.regex.Pattern.compile("<w:abstractNum w:abstractNumId=\"(\\d+)\"").matcher(numbering)
                .results().map(match -> match.group(1)).toList();
        assertEquals(ids.size(), new java.util.HashSet<>(ids).size(), ids.toString());
        assertTrue(ids.containsAll(List.of("0", "5")));
        assertEquals(2, TestSupport.count(numbering, "upperRoman"));
    }

    @Test
    void styleNamesMatchIgnoringCaseAndTemplatesWithoutStylesWork() throws Exception {
        Path file = directory.resolve("case.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createStyles().setStyles(CTStyles.Factory.parse("""
                    <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:style w:type="paragraph" w:styleId="T1"><w:name w:val="Heading 1"/></w:style>
                    </w:styles>""", Styles.ELEMENT_OPTIONS));
            try (OutputStream out = Files.newOutputStream(file)) {
                document.write(out);
            }
        }
        assertEquals(List.of("T1: A"), paragraphs(compose(options(file).build(), Section.titled("A"))));

        Path plain = directory.resolve("plain.docx");
        try (XWPFDocument document = new XWPFDocument(); OutputStream out = Files.newOutputStream(plain)) {
            document.write(out);
        }
        assertEquals(null, part(Files.readAllBytes(plain), "word/styles.xml"));
        assertEquals(List.of("Heading1: A"), paragraphs(compose(options(plain).build(), Section.titled("A"))));
    }
}
