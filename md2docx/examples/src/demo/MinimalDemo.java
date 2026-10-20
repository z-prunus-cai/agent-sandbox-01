package demo;

import md2docx.MarkdownDocxComposer;
import md2docx.Section;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Two Markdown files, each under its own heading, and one more paragraph under a third heading. */
public class MinimalDemo {

    public static void main(String[] args) throws Exception {
        Path input = Path.of(args.length > 0 ? args[0] : "examples/markdown/minimal");
        Path output = Path.of(args.length > 1 ? args[1] : "minimal.docx");
        byte[] document = new MarkdownDocxComposer().compose(List.of(
                Section.of("文档一", Files.readString(input.resolve("first.md"))),
                Section.of("文档二", Files.readString(input.resolve("second.md"))),
                Section.of("其他", "这是另一段话。")));
        Files.write(output, document);
        System.out.println("wrote " + output);
    }
}
