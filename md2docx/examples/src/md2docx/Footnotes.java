package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFFootnotes;
import org.apache.xmlbeans.XmlException;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFtnEdn;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Word footnotes for Markdown footnotes. Labels belong to their fragment: two fragments that both use
 * {@code [^1]} get two footnotes, and Word numbers all footnotes of the document in order.
 */
final class Footnotes {

    private record Key(int fragment, String label) {
    }

    private final XWPFDocument document;
    private final Map<Key, BigInteger> ids = new HashMap<>();

    Footnotes(XWPFDocument document) {
        this.document = document;
    }

    /** The footnote for this label in this fragment; {@code create} runs only for the first reference. */
    BigInteger idFor(int fragment, String label, Supplier<BigInteger> create) {
        return ids.computeIfAbsent(new Key(fragment, label), key -> create.get());
    }

    /** The first footnote of a document also adds the two separator footnotes that Word always writes. */
    XWPFFootnote create() {
        if (document.getFootnotes().isEmpty()) {
            XWPFFootnotes footnotes = document.createFootnotes();
            try {
                footnotes.addFootnote(CTFtnEdn.Factory.parse(separator(-1, "separator"), Styles.FRAGMENT));
                footnotes.addFootnote(CTFtnEdn.Factory.parse(separator(0, "continuationSeparator"), Styles.FRAGMENT));
            } catch (XmlException e) {
                throw new IllegalStateException(e);
            }
        }
        return document.createFootnote();
    }

    private static String separator(int id, String type) {
        return "<w:footnote " + Styles.WORD_NAMESPACE + " w:type=\"" + type + "\" w:id=\"" + id + "\"><w:p><w:pPr>"
                + "<w:spacing w:after=\"0\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr><w:r><w:" + type + "/></w:r></w:p></w:footnote>";
    }
}
