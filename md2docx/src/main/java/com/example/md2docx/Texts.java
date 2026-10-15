package com.example.md2docx;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;

import java.util.Locale;

final class Texts {

    private Texts() {
    }

    /** Drops characters that are illegal in XML 1.0 (control chars from copy/paste or LLM output corrupt the docx). */
    static String xmlSafe(String s) {
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            boolean ok = cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF)
                    || (cp >= 0xE000 && cp <= 0xFFFD) || (cp >= 0x10000 && cp <= 0x10FFFF);
            if (!ok && sb == null) sb = new StringBuilder(s.substring(0, i));
            if (ok && sb != null) sb.appendCodePoint(cp);
            i += n;
        }
        return sb == null ? s : sb.toString();
    }

    static String plainText(Node node) {
        StringBuilder sb = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override public void visit(Text t) { sb.append(t.getLiteral()); }
            @Override public void visit(Code c) { sb.append(c.getLiteral()); }
            @Override public void visit(SoftLineBreak b) { sb.append(' '); }
            @Override public void visit(HardLineBreak b) { sb.append(' '); }
        });
        return sb.toString().strip();
    }

    /** Width estimate used for table column sizing: CJK / full-width characters count double. */
    static int displayWidth(String s) {
        return s.codePoints().map(cp -> isCjk(cp) ? 2 : 1).sum();
    }

    static boolean isCjk(int cp) {
        Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
        return sc == Character.UnicodeScript.HAN || sc == Character.UnicodeScript.HIRAGANA
                || sc == Character.UnicodeScript.KATAKANA || sc == Character.UnicodeScript.HANGUL
                || (cp >= 0x3000 && cp <= 0x303F) || (cp >= 0xFF00 && cp <= 0xFFEF);
    }

    /** GitHub-style heading anchor: lower-case, punctuation removed, spaces to hyphens (CJK kept). */
    static String slug(String heading) {
        StringBuilder sb = new StringBuilder();
        heading.toLowerCase(Locale.ROOT).strip().codePoints().forEach(cp -> {
            if (Character.isLetterOrDigit(cp) || cp == '-' || cp == '_') sb.appendCodePoint(cp);
            else if (cp == ' ') sb.append('-');
        });
        return sb.toString();
    }
}
