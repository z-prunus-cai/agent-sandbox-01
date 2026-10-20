package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Composes documents and reads them back. */
final class TestSupport {

    private TestSupport() {
    }

    static byte[] compose(ComposeOptions options, Section... sections) {
        try {
            return new MarkdownDocxComposer(options).compose(List.of(sections));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] compose(Section... sections) {
        return compose(ComposeOptions.defaults(), sections);
    }

    static XWPFDocument open(byte[] docx) {
        try {
            return new XWPFDocument(new ByteArrayInputStream(docx));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The raw XML of one package part, such as word/document.xml; null when the part does not exist. */
    static String part(byte[] docx, String name) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                if (entry.getName().equals(name)) return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            }
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "style: text" for every body paragraph, with the style ID or "-" when the paragraph has none. */
    static List<String> paragraphs(byte[] docx) {
        try (XWPFDocument document = open(docx)) {
            return document.getParagraphs().stream()
                    .map(paragraph -> (paragraph.getStyle() == null ? "-" : paragraph.getStyle()) + ": " + paragraph.getText())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static XWPFParagraph paragraphWithText(XWPFDocument document, String text) {
        return document.getParagraphs().stream().filter(paragraph -> paragraph.getText().equals(text)).findFirst()
                .orElseThrow(() -> new AssertionError("no paragraph with text " + text));
    }

    static int count(String text, String part) {
        int count = 0;
        for (int index = text.indexOf(part); index >= 0; index = text.indexOf(part, index + 1)) count++;
        return count;
    }
}
