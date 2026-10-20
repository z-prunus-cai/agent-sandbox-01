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

    /**
     * @param destination the destination as written in the Markdown, {@code ![alt](destination)}
     * @param baseDirectory the section's {@link Section#baseDirectory(Path)}, or null
     */
    byte[] resolve(String destination, Path baseDirectory) throws IOException;

    /**
     * Reads {@code data:image/...;base64,} values and files inside {@code baseDirectory}. It never opens a network
     * connection and refuses every path that leaves {@code baseDirectory}, so Markdown from an untrusted source
     * can neither read other files nor make the server request an internal address.
     */
    static ImageResolver localFiles() {
        Pattern scheme = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
        return (destination, baseDirectory) -> {
            String value = destination.strip();
            if (value.regionMatches(true, 0, "data:image/", 0, "data:image/".length())) {
                int comma = value.indexOf(',');
                if (comma < 0 || !value.substring(0, comma).toLowerCase(Locale.ROOT).endsWith(";base64")) return null;
                try {
                    return Base64.getMimeDecoder().decode(value.substring(comma + 1));
                } catch (IllegalArgumentException malformed) {
                    return null;
                }
            }
            if (baseDirectory == null || scheme.matcher(value).find() || value.startsWith("/") || value.startsWith("\\")) {
                return null;
            }
            Path root = baseDirectory.toAbsolutePath().normalize();
            Path file;
            try {
                file = root.resolve(value).normalize();
            } catch (InvalidPathException invalid) {
                return null;
            }
            if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
            return Files.readAllBytes(file);
        };
    }
}
