package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;

final class Texts {

    private Texts() {
    }

    /** Every string written into the document goes through here. */
    static void write(XWPFRun run, String text) {
        run.setText(xmlSafe(text));
    }

    /** Removes characters XML 1.0 does not allow; left in, each one is written to the document as a "?". */
    static String xmlSafe(String text) {
        StringBuilder kept = null;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            boolean allowed = codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                    || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                    || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                    || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
            if (!allowed && kept == null) kept = new StringBuilder(text.substring(0, i));
            if (allowed && kept != null) kept.appendCodePoint(codePoint);
            i += Character.charCount(codePoint);
        }
        return kept == null ? text : kept.toString();
    }

    static String plainText(Node node) {
        StringBuilder text = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override public void visit(Text node) { text.append(node.getLiteral()); }
            @Override public void visit(Code node) { text.append(node.getLiteral()); }
            @Override public void visit(SoftLineBreak node) { text.append(' '); }
            @Override public void visit(HardLineBreak node) { text.append(' '); }
        });
        return text.toString().strip();
    }

    /** Width estimate for table columns: Chinese, Japanese, Korean and full-width characters count as two. */
    static int displayWidth(String text) {
        return text.codePoints().map(codePoint -> isWide(codePoint) ? 2 : 1).sum();
    }

    static boolean isWide(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
                || (codePoint >= 0x3000 && codePoint <= 0x303F) || (codePoint >= 0xFF00 && codePoint <= 0xFFEF);
    }
}
