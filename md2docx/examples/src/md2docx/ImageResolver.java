package md2docx;

import java.io.IOException;
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
     * neither read other files nor make the server request an internal address.
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
            try {
                Path root = baseDirectory.toRealPath();
                Path file = root.resolve(value).toRealPath();
                if (!file.startsWith(root) || !Files.isRegularFile(file) || Files.size(file) > MAXIMUM_BYTES) return null;
                return Files.readAllBytes(file);
            } catch (InvalidPathException | IOException missingOrInvalid) {
                return null;
            }
        };
    }
}
