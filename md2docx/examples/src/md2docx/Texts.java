package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class Texts {

    /** Without extensions; only used to decode character references such as {@code &amp;}. */
    private static final Parser PLAIN_PARSER = Parser.builder().build();
    static final Pattern LINE_BREAK_TAG = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern WHITE_SPACE = Pattern.compile("[ \\t\\r\\n\\f]+");
    private static final Pattern CHARACTER_REFERENCE = Pattern.compile("&(?:#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[A-Za-z][A-Za-z0-9]{0,31});");

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
     * The lines of text in an HTML block: comments and tags removed, white space collapsed as a browser does,
     * character references decoded, and a new line only where a {@code <br>} tag stands. One pass over the input,
     * so a block full of unclosed tags or comments does not take quadratic time.
     */
    static List<String> htmlLines(String html) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int nextTagEnd = -1;
        int index = 0;
        while (index < html.length()) {
            if (html.startsWith("<!--", index)) {
                // searching from index + 2 also ends the empty comments <!--> and <!--->
                int end = html.indexOf("-->", index + 2);
                if (end < 0) break;
                index = end + 3;
                line.append(' ');
                continue;
            }
            char character = html.charAt(index);
            if (character == '<' && index + 1 < html.length() && startsTag(html.charAt(index + 1))) {
                if (nextTagEnd < index) nextTagEnd = html.indexOf('>', index);
                if (nextTagEnd < 0) nextTagEnd = html.length();
                if (nextTagEnd < html.length()) {
                    if (LINE_BREAK_TAG.matcher(html.substring(index, nextTagEnd + 1)).matches()) {
                        lines.add(line.toString());
                        line.setLength(0);
                    } else {
                        line.append(' ');
                    }
                    index = nextTagEnd + 1;
                    continue;
                }
            }
            line.append(character);
            index++;
        }
        lines.add(line.toString());
        List<String> result = new ArrayList<>();
        for (String text : lines) result.add(decodeCharacterReferences(WHITE_SPACE.matcher(text).replaceAll(" ").strip()));
        while (!result.isEmpty() && result.getLast().isEmpty()) result.removeLast();
        while (!result.isEmpty() && result.getFirst().isEmpty()) result.removeFirst();
        return result;
    }

    /** A tag name, a closing tag, a comment or declaration, or a processing instruction; "1 < 2" is text. */
    private static boolean startsTag(char next) {
        return Character.isLetter(next) || next == '/' || next == '!' || next == '?';
    }

    /** Each reference such as {@code &amp;} or {@code &#233;} is decoded on its own by commonmark. */
    private static String decodeCharacterReferences(String text) {
        if (text.indexOf('&') < 0) return text;
        return CHARACTER_REFERENCE.matcher(text).replaceAll(match ->
                java.util.regex.Matcher.quoteReplacement(plainText(PLAIN_PARSER.parse(match.group()))));
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
