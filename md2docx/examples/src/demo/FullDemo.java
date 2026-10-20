package demo;

import md2docx.ComposeOptions;
import md2docx.MarkdownDocxComposer;
import md2docx.Section;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Every option: title, contents, heading numbers, page breaks, page numbers, a grouping section and images. */
public class FullDemo {

    public static void main(String[] args) throws Exception {
        Path input = Path.of(args.length > 0 ? args[0] : "examples/markdown/full");
        Path output = Path.of(args.length > 1 ? args[1] : "full.docx");
        ComposeOptions.Builder options = ComposeOptions.builder()
                .title("示例文档")
                .subtitle("由四个 Markdown 文件拼接")
                .tableOfContents(3)
                .numberHeadings(true)
                .pageBreakBetweenTopLevelSections(true)
                .pageNumbers(true);
        if (args.length > 2) options.template(Path.of(args[2]));

        List<Section> sections = List.of(
                Section.fromMarkdown(Files.readString(input.resolve("01-overview.md"))),
                Section.titled("设计")
                        .child(Section.untitled(Files.readString(input.resolve("02-structure.md"))).baseDirectory(input))
                        .child(Section.fromMarkdown(Files.readString(input.resolve("03-interface.md")))),
                Section.of("附注", Files.readString(input.resolve("04-notes.md"))));
        try (OutputStream out = Files.newOutputStream(output)) {
            new MarkdownDocxComposer(options.build()).compose(sections, out);
        }
        System.out.println("wrote " + output);
    }
}
