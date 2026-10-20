package md2docx;

import java.nio.file.Path;
import java.util.Objects;

/** Document-level options. Everything is off or empty by default, except where a setter says otherwise. */
public final class ComposeOptions {

    final String title;
    final String subtitle;
    final int tableOfContentsLevels;
    final String tableOfContentsTitle;
    final boolean updateFieldsOnOpen;
    final boolean numberHeadings;
    final boolean pageBreakBetweenTopLevelSections;
    final boolean pageBreakAfterFrontMatter;
    final boolean pageNumbers;
    final Path template;
    final ImageResolver imageResolver;
    final String latinFont;
    final String eastAsianFont;
    final String monospaceFont;

    private ComposeOptions(Builder builder) {
        title = builder.title;
        subtitle = builder.subtitle;
        tableOfContentsLevels = builder.tableOfContentsLevels;
        tableOfContentsTitle = builder.tableOfContentsTitle;
        updateFieldsOnOpen = builder.updateFieldsOnOpen;
        numberHeadings = builder.numberHeadings;
        pageBreakBetweenTopLevelSections = builder.pageBreakBetweenTopLevelSections;
        pageBreakAfterFrontMatter = builder.pageBreakAfterFrontMatter;
        pageNumbers = builder.pageNumbers;
        template = builder.template;
        imageResolver = builder.imageResolver;
        latinFont = builder.latinFont;
        eastAsianFont = builder.eastAsianFont;
        monospaceFont = builder.monospaceFont;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ComposeOptions defaults() {
        return builder().build();
    }

    public static final class Builder {
        private String title;
        private String subtitle;
        private int tableOfContentsLevels;
        private String tableOfContentsTitle = "目录";
        private boolean updateFieldsOnOpen = true;
        private boolean numberHeadings;
        private boolean pageBreakBetweenTopLevelSections;
        private boolean pageBreakAfterFrontMatter = true;
        private boolean pageNumbers;
        private Path template;
        private ImageResolver imageResolver = ImageResolver.localFiles();
        private String latinFont = "Calibri";
        private String eastAsianFont = "Microsoft YaHei";
        private String monospaceFont = "Consolas";

        private Builder() {
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        /** A table of contents with heading levels 1 to {@code levels}; 0 leaves it out. */
        public Builder tableOfContents(int levels) {
            if (levels < 0 || levels > 9) throw new IllegalArgumentException("levels must be between 0 and 9");
            this.tableOfContentsLevels = levels;
            return this;
        }

        /** Default: 目录. */
        public Builder tableOfContentsTitle(String title) {
            this.tableOfContentsTitle = Objects.requireNonNull(title);
            return this;
        }

        /** Default: on. Writes w:updateFields, which asks the reading application to recalculate fields on open (ECMA-376 §17.15.1.90). */
        public Builder updateFieldsOnOpen(boolean update) {
            this.updateFieldsOnOpen = update;
            return this;
        }

        public Builder numberHeadings(boolean numberHeadings) {
            this.numberHeadings = numberHeadings;
            return this;
        }

        public Builder pageBreakBetweenTopLevelSections(boolean pageBreak) {
            this.pageBreakBetweenTopLevelSections = pageBreak;
            return this;
        }

        /** Default: on. Only applies when there is a title, a subtitle or a table of contents. */
        public Builder pageBreakAfterFrontMatter(boolean pageBreak) {
            this.pageBreakAfterFrontMatter = pageBreak;
            return this;
        }

        public Builder pageNumbers(boolean pageNumbers) {
            this.pageNumbers = pageNumbers;
            return this;
        }

        /** A .docx or .dotx whose styles, page setup, headers and footers are reused; its body is dropped. */
        public Builder template(Path template) {
            this.template = template;
            return this;
        }

        /** Default: {@link ImageResolver#localFiles()}. */
        public Builder imageResolver(ImageResolver imageResolver) {
            this.imageResolver = Objects.requireNonNull(imageResolver);
            return this;
        }

        /** Fonts used without a template. Default: Calibri, Microsoft YaHei, Consolas. */
        public Builder fonts(String latin, String eastAsian, String monospace) {
            this.latinFont = Objects.requireNonNull(latin);
            this.eastAsianFont = Objects.requireNonNull(eastAsian);
            this.monospaceFont = Objects.requireNonNull(monospace);
            return this;
        }

        public ComposeOptions build() {
            return new ComposeOptions(this);
        }
    }
}
