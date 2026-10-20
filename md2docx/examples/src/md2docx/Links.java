package md2docx;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/** Decides what a Markdown link destination becomes in Word. */
final class Links {

    sealed interface Target permits External, Internal, TextOnly {
    }

    record External(String address) implements Target {
    }

    record Internal(String anchor) implements Target {
    }

    record TextOnly() implements Target {
    }

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "mailto", "ftp");
    /** RFC 3986 unreserved and reserved characters. */
    private static final String URI_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/?#[]@!$&'()*+,;=";

    private Links() {
    }

    /** {@code #anchor} links to a heading; http, https, mailto and ftp become hyperlinks; everything else is text. */
    static Target target(String destination) {
        String trimmed = destination == null ? "" : destination.strip();
        // commonmark decodes character references after the source was filtered, so check again
        if (!Texts.xmlSafe(trimmed).equals(trimmed)) return new TextOnly();
        if (trimmed.startsWith("#") && trimmed.length() > 1) {
            String fragment = trimmed.substring(1);
            try {
                // '+' is a literal plus in a URL fragment, not an encoded space
                fragment = URLDecoder.decode(fragment.replace("+", "%2B"), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException keptAsWritten) {
            }
            return new Internal(fragment);
        }
        String address = percentEncode(trimmed);
        try {
            String scheme = new URI(address).getScheme();
            if (scheme != null && ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) return new External(address);
        } catch (URISyntaxException notAnAddress) {
        }
        return new TextOnly();
    }

    /**
     * Encodes, as UTF-8 {@code %XX}, every character that RFC 3986 does not allow in a URI, such as the space in a
     * destination written in angle brackets or the "|" and "{" that pages put in query strings. Existing
     * {@code %XX} escapes stay.
     */
    static String percentEncode(String destination) {
        StringBuilder encoded = new StringBuilder(destination.length());
        for (int index = 0; index < destination.length(); index++) {
            char character = destination.charAt(index);
            boolean escape = character == '%' && index + 2 < destination.length()
                    && HexFormat.isHexDigit(destination.charAt(index + 1)) && HexFormat.isHexDigit(destination.charAt(index + 2));
            if (escape || URI_CHARACTERS.indexOf(character) >= 0) {
                encoded.append(character);
            } else {
                int end = Character.isHighSurrogate(character) && index + 1 < destination.length() ? index + 2 : index + 1;
                for (byte value : destination.substring(index, end).getBytes(StandardCharsets.UTF_8)) {
                    encoded.append('%').append(HexFormat.of().withUpperCase().toHexDigits(value));
                }
                index = end - 1;
            }
        }
        return encoded.toString();
    }
}
