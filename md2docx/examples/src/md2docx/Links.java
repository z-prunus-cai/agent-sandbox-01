package md2docx;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Decides what a Markdown link destination becomes in Word. */
final class Links {

    sealed interface Target permits External, Internal, TextOnly {
    }

    record External(String url) implements Target {
    }

    record Internal(String anchor) implements Target {
    }

    record TextOnly() implements Target {
    }

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "mailto", "ftp");

    private Links() {
    }

    /** {@code #anchor} links to a heading; http, https, mailto and ftp become hyperlinks; everything else is text. */
    static Target target(String destination) {
        String trimmed = destination == null ? "" : destination.strip();
        if (trimmed.startsWith("#") && trimmed.length() > 1) {
            String fragment = trimmed.substring(1);
            try {
                // '+' is a literal plus in a URL fragment, not an encoded space
                fragment = URLDecoder.decode(fragment.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException malformedEscape) {
                // keep the fragment as written
            }
            return new Internal(fragment);
        }
        try {
            String scheme = new URI(trimmed).getScheme();
            if (scheme != null && ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) return new External(trimmed);
        } catch (URISyntaxException notAUrl) {
            // falls through to text
        }
        return new TextOnly();
    }
}
