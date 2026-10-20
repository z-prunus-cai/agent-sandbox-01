package md2docx;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Opens a .docx or .dotx and keeps everything but its body: styles, numbering, page setup, headers, footers. */
final class Templates {

    private static final String DOCUMENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String TEMPLATE_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml";
    /** Relationships of the main part that only the body can use; they go when the body goes. */
    private static final Set<String> BODY_RELATIONSHIPS = Set.of(
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/oleObject",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/package",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart");

    private Templates() {
    }

    static XWPFDocument open(Path template) throws IOException {
        if (!Files.isRegularFile(template)) throw new NoSuchFileException(template.toString(), null, "template not found");
        XWPFDocument document;
        try (InputStream input = Files.newInputStream(template)) {
            document = new XWPFDocument(input);
        } catch (IOException | RuntimeException notWord) {
            throw new IOException("template is not a .docx or .dotx file: " + template, notWord);
        }
        byte[] withoutBody;
        try (document) {
            String type = document.getPackagePart().getContentType();
            if (!DOCUMENT_TYPE.equals(type) && !TEMPLATE_TYPE.equals(type)) {
                throw new IOException("template is not a .docx or .dotx file: " + template + " (" + type + ")");
            }
            // ECMA-376 §11.3.10: a document's main part has the document content type, a template's the template one
            if (TEMPLATE_TYPE.equals(type)) document.getPackage().replaceContentType(TEMPLATE_TYPE, DOCUMENT_TYPE);
            clearBody(document.getDocument().getBody());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            withoutBody = out.toByteArray();
        }
        try (OPCPackage templatePackage = OPCPackage.open(new ByteArrayInputStream(withoutBody))) {
            removeBodyRelationships(templatePackage);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            templatePackage.save(out);
            return new XWPFDocument(new ByteArrayInputStream(out.toByteArray()));
        } catch (InvalidFormatException invalid) {
            throw new IOException("cannot clean template: " + template, invalid);
        }
    }

    /**
     * Removes every block of the body except the final section properties. POI's removeBodyElement only removes
     * paragraphs and tables, and would leave content controls and other blocks in place.
     */
    private static void clearBody(CTBody body) {
        List<XmlObject> blocks = new ArrayList<>();
        try (XmlCursor cursor = body.newCursor()) {
            for (boolean found = cursor.toFirstChild(); found; found = cursor.toNextSibling()) {
                if (!"sectPr".equals(cursor.getName().getLocalPart())) blocks.add(cursor.getObject());
            }
        }
        for (XmlObject block : blocks) {
            try (XmlCursor cursor = block.newCursor()) {
                cursor.removeXml();
            }
        }
    }

    /** Drops the main part's pictures, links and embedded objects that no longer appear in the cleared body. */
    private static void removeBodyRelationships(OPCPackage templatePackage) throws IOException, InvalidFormatException {
        PackageRelationship main = templatePackage.getRelationshipsByType(PackageRelationshipTypes.CORE_DOCUMENT).getRelationship(0);
        PackagePart document = templatePackage.getPart(main);
        String xml;
        try (InputStream input = document.getInputStream()) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<PackageRelationship> unused = new ArrayList<>();
        for (PackageRelationship relationship : document.getRelationships()) {
            if (BODY_RELATIONSHIPS.contains(relationship.getRelationshipType()) && !xml.contains("\"" + relationship.getId() + "\"")) {
                unused.add(relationship);
            }
        }
        for (PackageRelationship relationship : unused) {
            PackagePart target = relationship.getTargetMode() == TargetMode.INTERNAL ? document.getRelatedPart(relationship) : null;
            document.removeRelationship(relationship.getId());
            if (target != null) templatePackage.removePart(target);
        }
    }
}
