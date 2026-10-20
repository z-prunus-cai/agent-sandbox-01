package md2docx;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.PackageRelationshipTypes;
import org.apache.poi.openxml4j.opc.PackagingURIHelper;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFEndnote;
import org.apache.poi.xwpf.usermodel.XWPFEndnotes;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFFootnotes;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFtnEdn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a .docx or .dotx into a template without body: styles, numbering, page setup, headers and footers stay;
 * the body and everything only the body uses (its footnotes, endnotes, comments, pictures, links, embedded
 * objects) go.
 */
final class Templates {

    private static final String DOCUMENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml";
    private static final String TEMPLATE_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml";
    private static final String RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/";
    /** Relationships of the main part that only the body uses. */
    private static final Set<String> BODY_RELATIONSHIPS = Set.of(
            RELATIONSHIPS + "image", RELATIONSHIPS + "hyperlink", RELATIONSHIPS + "oleObject", RELATIONSHIPS + "package",
            RELATIONSHIPS + "chart", RELATIONSHIPS + "aFChunk", RELATIONSHIPS + "comments",
            RELATIONSHIPS + "diagramData", RELATIONSHIPS + "diagramLayout", RELATIONSHIPS + "diagramQuickStyle",
            RELATIONSHIPS + "diagramColors",
            "http://schemas.microsoft.com/office/2007/relationships/diagramDrawing",
            "http://schemas.microsoft.com/office/2011/relationships/commentsExtended",
            "http://schemas.microsoft.com/office/2016/09/relationships/commentsIds",
            "http://schemas.microsoft.com/office/2018/08/relationships/commentsExtensible",
            "http://schemas.microsoft.com/office/2011/relationships/people");

    private Templates() {
    }

    /** The template without its body, as the bytes of a .docx package; read it once and open it for each document. */
    static byte[] withoutBody(Path template) throws IOException {
        if (!Files.isRegularFile(template)) throw new NoSuchFileException(template.toString(), null, "template not found");
        XWPFDocument document;
        try (InputStream input = Files.newInputStream(template)) {
            document = new XWPFDocument(input);
        } catch (IOException | RuntimeException unreadable) {
            throw new IOException("template is not a .docx or .dotx file: " + template + " (" + unreadable.getMessage() + ")", unreadable);
        }
        byte[] cleared;
        try (document) {
            String type = document.getPackagePart().getContentType();
            if (!DOCUMENT_TYPE.equals(type) && !TEMPLATE_TYPE.equals(type)) {
                throw new IOException("template is not a .docx or .dotx file: " + template + " (" + type + ")");
            }
            // ECMA-376 §11.3.10: a document's main part has the document content type, a template's the template one
            if (TEMPLATE_TYPE.equals(type)) document.getPackage().replaceContentType(TEMPLATE_TYPE, DOCUMENT_TYPE);
            clearBody(document.getDocument().getBody());
            removeNormalNotes(document);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            cleared = out.toByteArray();
        }
        try (OPCPackage templatePackage = OPCPackage.open(new ByteArrayInputStream(cleared))) {
            removeBodyRelationships(templatePackage);
            removeUnreachableParts(templatePackage);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            templatePackage.save(out);
            return out.toByteArray();
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

    /** Footnotes and endnotes of the body; the separator notes, which the settings may list, stay. */
    private static void removeNormalNotes(XWPFDocument document) {
        if (!document.getFootnotes().isEmpty()) {
            XWPFFootnotes footnotes = document.createFootnotes();
            List<XWPFFootnote> list = footnotes.getFootnotesList();
            for (int index = list.size() - 1; index >= 0; index--) {
                if (isNormal(list.get(index).getCTFtnEdn().getType())) footnotes.removeFootnote(index);
            }
        }
        if (!document.getEndnotes().isEmpty()) {
            XWPFEndnotes endnotes = document.createEndnotes();
            List<XWPFEndnote> list = endnotes.getEndnotesList();
            for (int index = list.size() - 1; index >= 0; index--) {
                if (isNormal(list.get(index).getCTFtnEdn().getType())) endnotes.removeEndnote(index);
            }
        }
    }

    private static boolean isNormal(STFtnEdn.Enum type) {
        return type == null || type == STFtnEdn.NORMAL;
    }

    /** Drops the main part's relationships that only the cleared body could use. */
    private static void removeBodyRelationships(OPCPackage templatePackage) throws IOException, InvalidFormatException {
        PackagePart document = templatePackage.getPart(
                templatePackage.getRelationshipsByType(PackageRelationshipTypes.CORE_DOCUMENT).getRelationship(0));
        String xml;
        try (InputStream input = document.getInputStream()) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> unused = new ArrayList<>();
        for (PackageRelationship relationship : document.getRelationships()) {
            if (BODY_RELATIONSHIPS.contains(relationship.getRelationshipType()) && !xml.contains("\"" + relationship.getId() + "\"")) {
                unused.add(relationship.getId());
            }
        }
        unused.forEach(document::removeRelationship);
    }

    /**
     * Deletes every part that no relationship reaches from the package root any more. A part shared with a header,
     * such as one picture used in both the body and a header, stays; a part only the body used goes, together
     * with the parts only it used, such as a chart's embedded workbook.
     */
    private static void removeUnreachableParts(OPCPackage templatePackage) throws InvalidFormatException {
        Set<PackagePartName> reachable = new HashSet<>();
        Deque<PackagePart> queue = new ArrayDeque<>();
        for (PackageRelationship relationship : templatePackage.getRelationships()) {
            if (relationship.getTargetMode() == TargetMode.INTERNAL) {
                PackagePart target = templatePackage.getPart(relationship);
                if (target != null && reachable.add(target.getPartName())) queue.add(target);
            }
        }
        while (!queue.isEmpty()) {
            PackagePart part = queue.remove();
            for (PackageRelationship relationship : part.getRelationships()) {
                if (relationship.getTargetMode() != TargetMode.INTERNAL) continue;
                PackagePart target = part.getRelatedPart(relationship);
                if (target != null && reachable.add(target.getPartName())) queue.add(target);
            }
        }
        for (PackagePart part : new ArrayList<>(templatePackage.getParts())) {
            if (part.isRelationshipPart() || reachable.contains(part.getPartName())) continue;
            PackagePartName relationshipsName = PackagingURIHelper.getRelationshipPartName(part.getPartName());
            templatePackage.removePart(part.getPartName());
            if (templatePackage.containPart(relationshipsName)) templatePackage.removePart(relationshipsName);
        }
    }
}
