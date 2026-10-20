package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** Opens a .docx or .dotx and keeps everything but its body: styles, numbering, page setup, headers, footers. */
final class Templates {

    private static final String DOCUMENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String TEMPLATE_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml";

    private Templates() {
    }

    static XWPFDocument open(Path template) throws IOException {
        if (!Files.isRegularFile(template)) throw new NoSuchFileException(template.toString(), null, "template not found");
        XWPFDocument document;
        try (InputStream input = Files.newInputStream(template)) {
            document = new XWPFDocument(input);
        } catch (RuntimeException | IOException notWord) {
            throw new IOException("template is not a .docx or .dotx file: " + template, notWord);
        }
        // a .dotx saved as .docx is refused by Word unless its main part type changes too
        if (TEMPLATE_TYPE.equals(document.getPackagePart().getContentType())) {
            try {
                document.getPackage().replaceContentType(TEMPLATE_TYPE, DOCUMENT_TYPE);
            } catch (RuntimeException failed) {
                document.close();
                throw new IOException("cannot convert template content type: " + template, failed);
            }
        }
        for (int i = document.getBodyElements().size() - 1; i >= 0; i--) document.removeBodyElement(i);
        return document;
    }
}
