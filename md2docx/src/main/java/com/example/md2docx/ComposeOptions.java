package com.example.md2docx;

import java.nio.file.Path;
import java.util.Objects;

/** Document-level rules applied when composing {@link DocSection}s into one Word document. */
public final class ComposeOptions {

    private final String title;
    private final String subtitle;
    private final int tocLevels;
    private final String tocTitle;
    private final boolean updateFieldsOnOpen;
    private final boolean numberHeadings;
    private final boolean pageBreakBetweenTopLevelSections;
    private final boolean pageBreakAfterFrontMatter;
    private final boolean pageNumbers;
    private final Path template;
    private final ImageResolver imageResolver;
    private final String latinFont;
    private final String eastAsiaFont;
    private final String monoFont;

    private ComposeOptions(Builder b) {
        this.title = b.title;
        this.subtitle = b.subtitle;
        this.tocLevels = b.tocLevels;
        this.tocTitle = b.tocTitle;
        this.updateFieldsOnOpen = b.updateFieldsOnOpen;
        this.numberHeadings = b.numberHeadings;
        this.pageBreakBetweenTopLevelSections = b.pageBreakBetweenTopLevelSections;
        this.pageBreakAfterFrontMatter = b.pageBreakAfterFrontMatter;
        this.pageNumbers = b.pageNumbers;
        this.template = b.template;
        this.imageResolver = b.imageResolver;
        this.latinFont = b.latinFont;
        this.eastAsiaFont = b.eastAsiaFont;
        this.monoFont = b.monoFont;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ComposeOptions defaults() {
        return builder().build();
    }

    String title() { return title; }
    String subtitle() { return subtitle; }
    int tocLevels() { return tocLevels; }
    String tocTitle() { return tocTitle; }
    boolean updateFieldsOnOpen() { return updateFieldsOnOpen; }
    boolean numberHeadings() { return numberHeadings; }
    boolean pageBreakBetweenTopLevelSections() { return pageBreakBetweenTopLevelSections; }
    boolean pageBreakAfterFrontMatter() { return pageBreakAfterFrontMatter; }
    boolean pageNumbers() { return pageNumbers; }
    Path template() { return template; }
    ImageResolver imageResolver() { return imageResolver; }
    String latinFont() { return latinFont; }
    String eastAsiaFont() { return eastAsiaFont; }
    String monoFont() { return monoFont; }

    public static final class Builder {
        private String title;
        private String subtitle;
        private int tocLevels;
        private String tocTitle = "目录";
        private boolean updateFieldsOnOpen = true;
        private boolean numberHeadings;
        private boolean pageBreakBetweenTopLevelSections;
        private boolean pageBreakAfterFrontMatter = true;
        private boolean pageNumbers;
        private Path template;
        private ImageResolver imageResolver = ImageResolver.localFiles();
        private String latinFont = "Calibri";
        private String eastAsiaFont = "Microsoft YaHei";
        private String monoFont = "Consolas";

        private Builder() {
        }

        /** Document title rendered at the top with the "Title" style. */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        /**
         * Inserts a table of contents covering heading levels 1..{@code levels} (0 disables it). The entries are
         * pre-filled so the TOC is readable before fields are updated; Word adds page numbers on update.
         */
        public Builder tableOfContents(int levels) {
            if (levels < 0 || levels > 9) throw new IllegalArgumentException("levels must be 0..9");
            this.tocLevels = levels;
            return this;
        }

        public Builder tocTitle(String tocTitle) {
            this.tocTitle = Objects.requireNonNull(tocTitle);
            return this;
        }

        /**
         * Asks Word to update fields (TOC page numbers) when the file is opened. Word shows a confirmation
         * prompt for this; disable it if users should press F9 themselves.
         */
        public Builder updateFieldsOnOpen(boolean update) {
            this.updateFieldsOnOpen = update;
            return this;
        }

        /** Multi-level heading numbering ("1.", "1.1", "1.1.1"), done by Word numbering, not by literal text. */
        public Builder numberHeadings(boolean numberHeadings) {
            this.numberHeadings = numberHeadings;
            return this;
        }

        /** Starts every top-level section except the first on a new page. */
        public Builder pageBreakBetweenTopLevelSections(boolean pageBreak) {
            this.pageBreakBetweenTopLevelSections = pageBreak;
            return this;
        }

        /** Puts a page break after the title / TOC block (only when there is one). */
        public Builder pageBreakAfterFrontMatter(boolean pageBreak) {
            this.pageBreakAfterFrontMatter = pageBreak;
            return this;
        }

        /** Adds a centered page number footer, unless the template already defines footers. */
        public Builder pageNumbers(boolean pageNumbers) {
            this.pageNumbers = pageNumbers;
            return this;
        }

        /**
         * A .docx/.dotx whose styles, page setup, headers and footers are reused; its body content is discarded.
         * Styles are matched by name ("heading 1", "Quote", ...), so templates saved by a localized Word whose
         * style ids are "1", "2", ... work too. Styles missing from the template are added with defaults.
         */
        public Builder template(Path template) {
            this.template = template;
            return this;
        }

        public Builder imageResolver(ImageResolver imageResolver) {
            this.imageResolver = Objects.requireNonNull(imageResolver);
            return this;
        }

        /** Default fonts used when no template is given (or the template lacks the style). */
        public Builder fonts(String latin, String eastAsia, String mono) {
            this.latinFont = Objects.requireNonNull(latin);
            this.eastAsiaFont = Objects.requireNonNull(eastAsia);
            this.monoFont = Objects.requireNonNull(mono);
            return this;
        }

        public ComposeOptions build() {
            return new ComposeOptions(this);
        }
    }
}
