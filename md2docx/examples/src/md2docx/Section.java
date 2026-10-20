package md2docx;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One node of the chapter tree: an optional title, an optional Markdown body, an optional image directory and
 * child sections. A section at depth d gets a level-d heading; the headings inside its Markdown move so that the
 * shallowest one sits one level below the section heading.
 */
public final class Section {

    enum TitleSource { GIVEN, FIRST_HEADING, NONE }

    private final String title;
    private final String markdown;
    private final TitleSource titleSource;
    private final List<Section> children = new ArrayList<>();
    private Path baseDirectory;
    private Boolean pageBreakBefore;

    private Section(String title, String markdown, TitleSource titleSource) {
        this.title = title;
        this.markdown = markdown;
        this.titleSource = titleSource;
    }

    /** A section with the given title and Markdown body. */
    public static Section of(String title, String markdown) {
        return new Section(requireText(title), markdown, TitleSource.GIVEN);
    }

    /** A section with a title only, usually holding child sections. */
    public static Section titled(String title) {
        return new Section(requireText(title), null, TitleSource.GIVEN);
    }

    /** Takes the title from the heading the Markdown starts with, and removes that heading from the body. */
    public static Section fromMarkdown(String markdown) {
        return new Section(null, markdown, TitleSource.FIRST_HEADING);
    }

    /** A body without a title: it continues under the parent heading. */
    public static Section untitled(String markdown) {
        return new Section(null, markdown, TitleSource.NONE);
    }

    /** Directory that relative image paths in this section resolve against. Child sections do not inherit it. */
    public Section baseDirectory(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
        return this;
    }

    /** Overrides {@link ComposeOptions.Builder#pageBreakBetweenTopLevelSections} for this section. */
    public Section pageBreakBefore(boolean pageBreakBefore) {
        this.pageBreakBefore = pageBreakBefore;
        return this;
    }

    public Section child(Section child) {
        children.add(child);
        return this;
    }

    String title() { return title; }
    String markdown() { return markdown; }
    TitleSource titleSource() { return titleSource; }
    Path baseDirectory() { return baseDirectory; }
    Boolean pageBreakBefore() { return pageBreakBefore; }
    List<Section> children() { return children; }

    private static String requireText(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        return title;
    }
}
