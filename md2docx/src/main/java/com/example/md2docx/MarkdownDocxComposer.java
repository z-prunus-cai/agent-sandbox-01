package com.example.md2docx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;

/**
 * Composes several Markdown fragments into one Word document.
 *
 * <pre>{@code
 * ComposeOptions options = ComposeOptions.builder()
 *         .title("系统设计说明书").tableOfContents(3).numberHeadings(true).build();
 * List<DocSection> sections = List.of(
 *         DocSection.fromMarkdown(overviewMd),
 *         DocSection.titled("详细设计")
 *                 .child(DocSection.fromMarkdown(apiMd).baseDir(apiDir))
 *                 .child(DocSection.fromMarkdown(dbMd)));
 * new MarkdownDocxComposer(options).compose(sections, out);
 * }</pre>
 *
 * <p>Instances are immutable and thread-safe; every {@code compose} call builds an independent document.
 */
public final class MarkdownDocxComposer {

    private final ComposeOptions options;

    public MarkdownDocxComposer() {
        this(ComposeOptions.defaults());
    }

    public MarkdownDocxComposer(ComposeOptions options) {
        this.options = Objects.requireNonNull(options);
    }

    /** Writes the composed document to {@code out}. The stream is not closed. */
    public void compose(List<DocSection> sections, OutputStream out) throws IOException {
        Composition composition = new Composition(options);
        try {
            composition.compose(sections);
            composition.doc.write(out);
        } finally {
            composition.doc.close();
        }
    }

    public byte[] compose(List<DocSection> sections) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        compose(sections, out);
        return out.toByteArray();
    }

    /** Single-fragment convenience: converts one Markdown string as-is. */
    public byte[] convert(String markdown) throws IOException {
        return compose(List.of(DocSection.untitled(markdown)));
    }
}
