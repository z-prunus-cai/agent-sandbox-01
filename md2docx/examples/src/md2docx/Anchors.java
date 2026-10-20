package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bookmarks every heading and resolves {@code [text](#heading)} links. The anchor of a heading is computed the way
 * GitHub computes it for one Markdown file, so links that work on GitHub keep working: a link is first looked up
 * in its own fragment, then in the whole document. A link without a target becomes plain text.
 */
final class Anchors {

    record Heading(int level, String text, String bookmark, String number) {
    }

    private record PendingLink(CTHyperlink link, int fragment, String anchor) {
    }

    private static final Pattern NOT_WORD_DASH_OR_SPACE = Pattern.compile("[^\\p{L}\\p{M}\\p{Nd}\\p{Pc}\\- ]");

    private final List<Heading> headings = new ArrayList<>();
    private final Map<Integer, Map<String, Integer>> anchorCounts = new HashMap<>();
    private final Map<Integer, Map<String, String>> bookmarksByFragment = new HashMap<>();
    private final Map<String, String> firstBookmarkByAnchor = new HashMap<>();
    private final List<PendingLink> pendingLinks = new ArrayList<>();

    /** GitHub's rule: ASCII lower case, drop everything but letters, marks, digits, connectors, '-' and ' ', then ' ' to '-'. */
    static String anchor(String headingText) {
        return NOT_WORD_DASH_OR_SPACE.matcher(asciiLowerCase(headingText)).replaceAll("").replace(' ', '-');
    }

    private static String asciiLowerCase(String text) {
        StringBuilder lower = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> lower.appendCodePoint(codePoint >= 'A' && codePoint <= 'Z' ? codePoint + ('a' - 'A') : codePoint));
        return lower.toString();
    }

    /** Wraps the content added by {@code body} in a bookmark and records the heading for links and the contents. */
    void bookmarkHeading(XWPFParagraph paragraph, int level, String text, String number, int fragment, Runnable body) {
        int sequence = headings.size() + 1;
        String bookmark = "_Heading" + sequence;
        headings.add(new Heading(level, text, bookmark, number));

        String base = anchor(text);
        int seen = anchorCounts.computeIfAbsent(fragment, key -> new HashMap<>()).merge(base, 1, Integer::sum) - 1;
        String anchor = seen == 0 ? base : base + "-" + seen;
        bookmarksByFragment.computeIfAbsent(fragment, key -> new HashMap<>()).put(anchor, bookmark);
        firstBookmarkByAnchor.putIfAbsent(anchor, bookmark);

        CTP paragraphElement = paragraph.getCTP();
        var start = paragraphElement.addNewBookmarkStart();
        start.setName(bookmark);
        start.setId(BigInteger.valueOf(sequence));
        body.run();
        paragraphElement.addNewBookmarkEnd().setId(BigInteger.valueOf(sequence));
    }

    /** A hyperlink run whose target is filled in by {@link #resolve()} once every heading is known. */
    XWPFHyperlinkRun link(XWPFParagraph paragraph, int fragment, String anchor) {
        CTHyperlink link = paragraph.getCTP().addNewHyperlink();
        pendingLinks.add(new PendingLink(link, fragment, asciiLowerCase(anchor)));
        return new XWPFHyperlinkRun(link, link.addNewR(), paragraph);
    }

    void resolve() {
        for (PendingLink pending : pendingLinks) {
            String bookmark = bookmarksByFragment.getOrDefault(pending.fragment, Map.of()).get(pending.anchor);
            if (bookmark == null) bookmark = firstBookmarkByAnchor.get(pending.anchor);
            if (bookmark != null) pending.link.setAnchor(bookmark);
            else unwrap(pending.link);
        }
    }

    /** Replaces a hyperlink that has no target by its runs, without the hyperlink character style. */
    private static void unwrap(CTHyperlink link) {
        for (CTR run : link.getRList()) {
            while (run.isSetRPr() && run.getRPr().sizeOfRStyleArray() > 0) run.getRPr().removeRStyle(0);
        }
        try (XmlCursor position = link.newCursor(); XmlCursor child = link.newCursor()) {
            while (child.toFirstChild()) child.moveXml(position);
            position.removeXml();
        }
    }

    List<Heading> headings() {
        return headings;
    }
}
