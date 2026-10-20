package md2docx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/** Composes a tree of Markdown sections into one .docx document. Holds no state; safe to share between threads. */
public final class MarkdownDocxComposer {

    private final ComposeOptions options;

    public MarkdownDocxComposer() {
        this(ComposeOptions.defaults());
    }

    public MarkdownDocxComposer(ComposeOptions options) {
        this.options = Objects.requireNonNull(options);
    }

    /** Writes the document to {@code out} and leaves the stream open. */
    public void compose(List<Section> sections, OutputStream out) throws IOException {
        try (Composition composition = new Composition(options)) {
            composition.compose(sections);
            composition.write(out);
        }
    }

    public byte[] compose(List<Section> sections) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        compose(sections, out);
        return out.toByteArray();
    }
}
