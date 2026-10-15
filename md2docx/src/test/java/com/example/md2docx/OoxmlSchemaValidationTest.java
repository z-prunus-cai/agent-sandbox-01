package com.example.md2docx;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Validates every WordprocessingML part of generated documents against the ECMA-376 schemas.
 * Needs the complete schema classes, which the default poi-ooxml-lite jar does not contain:
 * <pre>mvn test -Pschema-validation</pre>
 */
class OoxmlSchemaValidationTest {

    private static final String MAIN = "org.openxmlformats.schemas.wordprocessingml.x2006.main.";

    @Test
    void generatedDocumentsConformToTheSchema() throws Exception {
        assumeTrue(fullSchemasPresent(), "run with -Pschema-validation (needs poi-ooxml-full)");

        Path dir = Path.of("examples/report");
        List<DocSection> sections = List.of(
                DocSection.fromMarkdown(Files.readString(dir.resolve("01-overview.md"))),
                DocSection.titled("技术方案")
                        .child(DocSection.fromMarkdown(Files.readString(dir.resolve("02-architecture.md"))).baseDir(dir))
                        .child(DocSection.fromMarkdown(Files.readString(dir.resolve("03-api.md")))),
                DocSection.of("风险与计划", Files.readString(dir.resolve("04-risks.md"))),
                DocSection.untitled("x\u0000y [js](javascript:alert(1)) <br> ~~s~~ `c` [^n]\n\n[^n]: n with table\n\n"
                        + "    | a |\n\n> - [ ] task in quote\n>   ```\n>   code\n>   ```\n"));
        ComposeOptions full = ComposeOptions.builder().title("T").subtitle("S").tableOfContents(3).numberHeadings(true)
                .pageBreakBetweenTopLevelSections(true).pageNumbers(true).build();

        List<String> errors = new ArrayList<>();
        validate("full-options", new MarkdownDocxComposer(full).compose(sections), errors);
        validate("defaults", new MarkdownDocxComposer().compose(sections), errors);
        assertEquals(List.of(), errors);
    }

    private static void validate(String name, byte[] docx, List<String> errors) throws Exception {
        int parts = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                String type = schemaFor(e.getName());
                if (type == null) continue;
                parts++;
                SchemaType st = (SchemaType) Class.forName(MAIN + type).getField("type").get(null);
                XmlObject xml = XmlObject.Factory.parse(new ByteArrayInputStream(zip.readAllBytes()), new XmlOptions().setDocumentType(st));
                List<XmlError> found = new ArrayList<>();
                xml.validate(new XmlOptions().setErrorListener(found));
                for (XmlError err : found) {
                    // XMLBeans cannot resolve the strict wildcard inside a:graphicData; pictures are validated below instead
                    if (err.getMessage().contains("strict wildcard") && err.getMessage().contains("drawingml/2006/picture")) continue;
                    errors.add(name + " " + e.getName() + ": " + err);
                }
                if (e.getName().equals("word/document.xml")) validatePictures(name, xml, errors);
            }
        }
        assertTrue(parts >= 4, name + ": expected document, styles, numbering and settings parts");
    }

    private static void validatePictures(String name, XmlObject document, List<String> errors) throws Exception {
        SchemaType pic = (SchemaType) Class.forName("org.openxmlformats.schemas.drawingml.x2006.picture.PicDocument").getField("type").get(null);
        for (XmlObject p : document.selectPath("declare namespace pic='http://schemas.openxmlformats.org/drawingml/2006/picture' .//pic:pic")) {
            XmlObject standalone = XmlObject.Factory.parse(p.xmlText(new XmlOptions().setSaveOuter()), new XmlOptions().setDocumentType(pic));
            List<XmlError> found = new ArrayList<>();
            standalone.validate(new XmlOptions().setErrorListener(found));
            found.forEach(err -> errors.add(name + " pic:pic: " + err));
        }
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
            Class.forName(MAIN + "CTFtnDocProps"); // not part of poi-ooxml-lite
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
