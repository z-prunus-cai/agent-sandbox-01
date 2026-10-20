package md2docx;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

/** Loads the bytes of a Markdown image. Returning null makes the document show the image's alternative text. */
@FunctionalInterface
public interface ImageResolver {

    /** Images larger than this are not read. */
    long MAXIMUM_BYTES = 20L * 1024 * 1024;

    /**
     * @param destination the destination as written in the Markdown, {@code ![alt](destination)}
     * @param baseDirectory the section's {@link Section#baseDirectory(Path)}, or null
     */
    byte[] resolve(String destination, Path baseDirectory) throws IOException;

    /**
     * Reads {@code data:image/...;base64,} values and files inside {@code baseDirectory}, up to
     * {@link #MAXIMUM_BYTES}. It never opens a network connection and refuses every path whose real location,
     * after following symbolic links, is outside {@code baseDirectory}, so Markdown from an untrusted source can
     * neither read other files nor make the process open a network connection.
     */
    static ImageResolver localFiles() {
        Pattern scheme = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
        return (destination, baseDirectory) -> {
            String value = destination.strip();
            if (value.regionMatches(true, 0, "data:image/", 0, "data:image/".length())) {
                int comma = value.indexOf(',');
                if (comma < 0 || !value.substring(0, comma).toLowerCase(Locale.ROOT).endsWith(";base64")) return null;
                // base64 holds 3 bytes in every 4 characters
                if ((value.length() - comma) / 4 * 3 > MAXIMUM_BYTES) return null;
                try {
                    return Base64.getMimeDecoder().decode(value.substring(comma + 1));
                } catch (IllegalArgumentException malformed) {
                    return null;
                }
            }
            if (baseDirectory == null || scheme.matcher(value).find() || value.startsWith("/") || value.startsWith("\\")) {
                return null;
            }
            Path root;
            try {
                root = baseDirectory.toRealPath();
            } catch (IOException missing) {
                return null;
            }
            byte[] bytes = read(root, value);
            // commonmark does not decode %XX in destinations; "my%20picture.png" names "my picture.png"
            if (bytes == null && value.contains("%")) {
                try {
                    bytes = read(root, URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8));
                } catch (IllegalArgumentException malformedEscape) {
                    return null;
                }
            }
            return bytes;
        };
    }

    /** The file at {@code relative} under {@code root}, or null when it is missing, outside root or too large. */
    private static byte[] read(Path root, String relative) {
        try {
            Path file = root.resolve(relative).toRealPath();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
            try (InputStream input = Files.newInputStream(file)) {
                // reading at most one byte more than the limit also catches a file that grew after it was checked
                byte[] bytes = input.readNBytes((int) MAXIMUM_BYTES + 1);
                return bytes.length > MAXIMUM_BYTES ? null : bytes;
            }
        } catch (InvalidPathException | IOException missingOrInvalid) {
            return null;
        }
    }
}
