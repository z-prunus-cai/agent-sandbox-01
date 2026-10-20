package md2docx;

import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates every WordprocessingML part against the ECMA-376 schemas compiled into poi-ooxml-full, which must be on
 * the test class path: poi-ooxml-lite only holds the schema types POI itself uses, and validating with it alone fails
 * with a SchemaTypeLoaderException.
 */
class SchemaValidationTest {

    private static final String MAIN_SCHEMA_PACKAGE = "org.openxmlformats.schemas.wordprocessingml.x2006.main.";

    @Test
    void fullSchemasAreOnTheClassPath() {
        assertTrue(fullSchemasPresent(), "add poi-ooxml-full-5.5.1.jar to the test class path");
    }

    @Test
    void demoDocumentsAndEveryFeatureValidate() throws Exception {
        Path full = Path.of("examples/markdown/full");
        List<Section> sections = List.of(
                Section.fromMarkdown(Files.readString(full.resolve("01-overview.md"))),
                Section.titled("设计")
                        .child(Section.untitled(Files.readString(full.resolve("02-structure.md"))).baseDirectory(full))
                        .child(Section.fromMarkdown(Files.readString(full.resolve("03-interface.md")))),
                Section.of("附注", Files.readString(full.resolve("04-notes.md"))),
                Section.untitled("x\u0000y [js](javascript:alert(1)) <br> ~~s~~ `c` [^n] [gone](#nowhere)\n\n"
                        + "[^n]: note with **bold**\n\n    | a |\n    | - |\n    | 1 |\n\n    ## heading in a note\n\n"
                        + "empty[^e] and table[^t]\n\n[^e]:\n\n[^t]:\n    | b |\n    | - |\n    | 2 |\n\n"
                        + "> - [ ] task in a quote\n>   ```\n>   code\n>   ```\n\n| t |\n| - |\n| 1 |"));
        ComposeOptions everything = ComposeOptions.builder().title("T").subtitle("S").tableOfContents(3).numberHeadings(true)
                .pageBreakBetweenTopLevelSections(true).pageNumbers(true).build();

        List<String> errors = new ArrayList<>();
        Set<String> parts = new TreeSet<>();
        byte[] allOptions = new MarkdownDocxComposer(everything).compose(sections);
        String footnotes = TestSupport.part(allOptions, "word/footnotes.xml");
        // every footnote, also the empty one and the one starting with a table, starts with its number mark
        assertEquals(TestSupport.count(footnotes, "<w:footnote "), TestSupport.count(footnotes, "<w:footnoteRef/>"));
        java.nio.file.Path template = java.nio.file.Files.createTempFile("template", ".docx");
        java.nio.file.Files.write(template, allOptions);
        ComposeOptions withTemplate = ComposeOptions.builder().template(template).numberHeadings(true).pageNumbers(true).tableOfContents(2).build();
        validate("template", new MarkdownDocxComposer(withTemplate).compose(sections), errors, parts);
        java.nio.file.Files.delete(template);
        int pictures = validate("all options", allOptions, errors, parts)
                + validate("defaults", new MarkdownDocxComposer().compose(sections), errors, parts)
                + validate("empty", new MarkdownDocxComposer(everything).compose(List.of()), errors, parts);
        assertEquals(List.of(), errors);
        // the demo image is in both the "all options" and the "defaults" document
        assertEquals(2, pictures);
        assertEquals(Set.of("word/document.xml", "word/footer1.xml", "word/footnotes.xml", "word/numbering.xml",
                "word/settings.xml", "word/styles.xml"), parts);
    }

    /** @return the number of pictures in the document part */
    private static int validate(String name, byte[] docx, List<String> errors, Set<String> parts) throws Exception {
        int pictures = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                String type = schemaFor(entry.getName());
                if (type == null) continue;
                parts.add(entry.getName());
                SchemaType schema = (SchemaType) Class.forName(MAIN_SCHEMA_PACKAGE + type).getField("type").get(null);
                XmlObject xml = XmlObject.Factory.parse(new ByteArrayInputStream(zip.readAllBytes()), new XmlOptions().setDocumentType(schema));
                List<XmlError> found = new ArrayList<>();
                xml.validate(new XmlOptions().setErrorListener(found));
                for (XmlError error : found) {
                    // XMLBeans does not follow the strict wildcard in a:graphicData; pictures are validated on their own below
                    if (error.getMessage().contains("strict wildcard") && error.getMessage().contains("drawingml/2006/picture")) continue;
                    errors.add(name + " " + entry.getName() + ": " + error);
                }
                if (entry.getName().equals("word/document.xml")) pictures += validatePictures(name, xml, errors);
            }
        }
        return pictures;
    }

    private static int validatePictures(String name, XmlObject document, List<String> errors) throws Exception {
        SchemaType picture = (SchemaType) Class.forName("org.openxmlformats.schemas.drawingml.x2006.picture.PicDocument").getField("type").get(null);
        for (XmlObject element : document.selectPath(
                "declare namespace pic='http://schemas.openxmlformats.org/drawingml/2006/picture' .//pic:pic")) {
            XmlObject standalone = XmlObject.Factory.parse(element.xmlText(new XmlOptions().setSaveOuter()), new XmlOptions().setDocumentType(picture));
            List<XmlError> found = new ArrayList<>();
            standalone.validate(new XmlOptions().setErrorListener(found));
            found.forEach(error -> errors.add(name + " pic:pic: " + error));
        }
        return document.selectPath("declare namespace pic='http://schemas.openxmlformats.org/drawingml/2006/picture' .//pic:pic").length;
    }

    private static String schemaFor(String part) {
        return switch (part) {
            case "word/document.xml" -> "DocumentDocument";
            case "word/styles.xml" -> "StylesDocument";
            case "word/numbering.xml" -> "NumberingDocument";
            case "word/footnotes.xml" -> "FootnotesDocument";
            case "word/settings.xml" -> "SettingsDocument";
            default -> part.matches("word/footer\\d+\\.xml") ? "FtrDocument" : part.matches("word/header\\d+\\.xml") ? "HdrDocument" : null;
        };
    }

    private static boolean fullSchemasPresent() {
        try {
            // CTFtnDocProps is in poi-ooxml-full only
            Class.forName(MAIN_SCHEMA_PACKAGE + "CTFtnDocProps");
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        }
    }
}
