package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

final class Texts {

    /** Without extensions; only used to decode character references such as {@code &amp;}. */
    private static final Parser PLAIN_PARSER = Parser.builder().build();

    private Texts() {
    }

    /** Every Markdown-derived string written into a run goes through here. */
    static void write(XWPFRun run, String text) {
        run.setText(xmlSafe(text));
    }

    /** Removes characters XML 1.0 does not allow; left in, each one is written to the document as a "?". */
    static String xmlSafe(String text) {
        StringBuilder kept = null;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            boolean allowed = codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                    || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                    || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                    || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
            if (!allowed && kept == null) kept = new StringBuilder(text.substring(0, index));
            if (allowed && kept != null) kept.appendCodePoint(codePoint);
            index += Character.charCount(codePoint);
        }
        return kept == null ? text : kept.toString();
    }

    /**
     * The text of an HTML block: comments and tags removed, character references decoded. One pass over the input,
     * so a block full of unclosed tags or comments does not take quadratic time.
     */
    static String htmlText(String html) {
        StringBuilder text = new StringBuilder(html.length());
        int nextTagEnd = -1;
        int index = 0;
        while (index < html.length()) {
            if (html.startsWith("<!--", index)) {
                int end = html.indexOf("-->", index + 4);
                if (end < 0) break;
                index = end + 3;
                continue;
            }
            char character = html.charAt(index);
            if (character == '<') {
                if (nextTagEnd < index) nextTagEnd = html.indexOf('>', index);
                if (nextTagEnd < 0) nextTagEnd = html.length();
                if (nextTagEnd < html.length()) {
                    index = nextTagEnd + 1;
                    continue;
                }
            }
            text.append(character);
            index++;
        }
        return decodeCharacterReferences(text.toString().strip());
    }

    /** Lets commonmark decode the references: every other ASCII punctuation character is escaped first. */
    private static String decodeCharacterReferences(String text) {
        if (text.indexOf('&') < 0) return text;
        StringBuilder escaped = new StringBuilder(text.length() * 2);
        text.lines().map(String::strip).forEach(line -> {
            for (int index = 0; index < line.length(); index++) {
                char character = line.charAt(index);
                boolean punctuation = character < 128 && !Character.isLetterOrDigit(character) && !Character.isWhitespace(character);
                if (punctuation && character != '&' && character != '#' && character != ';') escaped.append('\\');
                escaped.append(character);
            }
            escaped.append('\n');
        });
        return plainText(PLAIN_PARSER.parse(escaped.toString()));
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
