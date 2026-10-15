package com.example.md2docx;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One node of the document outline: an optional title, an optional Markdown body and optional child sections.
 *
 * <p>Heading levels are derived from the position in the tree, not from the Markdown source: a section at depth
 * {@code d} gets a level-{@code d} heading, and the headings inside its Markdown are shifted so that the
 * shallowest one sits directly below the section title. This lets independently written fragments (each starting
 * with {@code #} or {@code ##}) be nested anywhere without manual level fixing.
 */
public final class DocSection {

    private final String title;
    private final String markdown;
    private final boolean titleFromFirstHeading;
    private Path baseDir;
    private Boolean pageBreakBefore;
    private final List<DocSection> children = new ArrayList<>();

    private DocSection(String title, String markdown, boolean titleFromFirstHeading) {
        this.title = title;
        this.markdown = markdown;
        this.titleFromFirstHeading = titleFromFirstHeading;
    }

    /** Section with an explicit title and a Markdown body. */
    public static DocSection of(String title, String markdown) {
        return new DocSection(requireText(title), markdown, false);
    }

    /** Section that only carries a title, typically a chapter grouping {@link #child child} sections. */
    public static DocSection titled(String title) {
        return new DocSection(requireText(title), null, false);
    }

    /**
     * Section whose title is the leading heading of the Markdown (if the first block is a heading).
     * The heading is removed from the body so it is not rendered twice.
     */
    public static DocSection fromMarkdown(String markdown) {
        return new DocSection(null, markdown, true);
    }

    /** Body without a title: the content continues under the parent heading. */
    public static DocSection untitled(String markdown) {
        return new DocSection(null, markdown, false);
    }

    /**
     * Directory that relative image paths in this section are resolved against. Without it, only
     * {@code data:} images are embedded. Child sections do not inherit it.
     */
    public DocSection baseDir(Path baseDir) {
        this.baseDir = baseDir;
        return this;
    }

    /** Overrides {@link ComposeOptions.Builder#pageBreakBetweenTopLevelSections(boolean)} for this section. */
    public DocSection pageBreakBefore(boolean pageBreakBefore) {
        this.pageBreakBefore = pageBreakBefore;
        return this;
    }

    public DocSection child(DocSection child) {
        children.add(child);
        return this;
    }

    public DocSection children(List<DocSection> children) {
        this.children.addAll(children);
        return this;
    }

    String title() { return title; }
    String markdown() { return markdown; }
    boolean titleFromFirstHeading() { return titleFromFirstHeading; }
    Path baseDir() { return baseDir; }
    Boolean pageBreakBeforeOverride() { return pageBreakBefore; }
    List<DocSection> childSections() { return Collections.unmodifiableList(children); }

    private static String requireText(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        return title;
    }
}
