package md2docx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/**
 * Composes a tree of Markdown sections into one .docx document. Holds only immutable data, so one instance can be
 * shared between threads.
 */
public final class MarkdownDocxComposer {

    private final ComposeOptions options;
    /** The template without its body, read once; null without a template. */
    private final byte[] template;

    public MarkdownDocxComposer() {
        this.options = ComposeOptions.defaults();
        this.template = null;
    }

    /** Reads and cleans the template, if the options name one; later changes to the template file are not seen. */
    public MarkdownDocxComposer(ComposeOptions options) throws IOException {
        this.options = Objects.requireNonNull(options);
        this.template = options.template == null ? null : Templates.withoutBody(options.template);
    }

    /** Writes the document to {@code out} and leaves the stream open. */
    public void compose(List<Section> sections, OutputStream out) throws IOException {
        try (Composition composition = new Composition(options, template)) {
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
