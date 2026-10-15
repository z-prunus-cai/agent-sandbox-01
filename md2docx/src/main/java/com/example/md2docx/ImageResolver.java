package com.example.md2docx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;

/**
 * Loads the bytes of an image referenced from Markdown ({@code ![alt](destination)}).
 * Returning {@code null} renders the alt text instead of the image.
 */
@FunctionalInterface
public interface ImageResolver {

    /**
     * @param destination the raw destination from the Markdown source
     * @param baseDir     the section's {@link DocSection#baseDir(Path) base directory}, may be {@code null}
     */
    byte[] resolve(String destination, Path baseDir) throws IOException;

    /**
     * Default resolver: base64 {@code data:image/...} URIs, plus relative files inside {@code baseDir}.
     * It never touches the network and refuses paths that escape {@code baseDir} (e.g. {@code ../../etc/x.png}),
     * so Markdown from untrusted sources cannot make the server read arbitrary files or fetch internal URLs.
     */
    static ImageResolver localFiles() {
        return (destination, baseDir) -> {
            String dest = destination.trim();
            if (dest.regionMatches(true, 0, "data:image/", 0, 11)) {
                int comma = dest.indexOf(',');
                if (comma < 0 || !dest.substring(0, comma).toLowerCase(Locale.ROOT).endsWith(";base64")) return null;
                try {
                    return Base64.getMimeDecoder().decode(dest.substring(comma + 1));
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
            if (baseDir == null || dest.matches("(?i)^[a-z][a-z0-9+.-]*:.*") || dest.startsWith("/") || dest.startsWith("\\")) {
                return null;
            }
            Path root = baseDir.toAbsolutePath().normalize();
            Path file;
            try {
                file = root.resolve(dest).normalize();
            } catch (java.nio.file.InvalidPathException e) {
                return null;
            }
            if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
            return Files.readAllBytes(file);
        };
    }
}
