package com.example.md2docx.example;

import com.example.md2docx.ComposeOptions;
import com.example.md2docx.DocSection;
import com.example.md2docx.MarkdownDocxComposer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds {@code target/example-report.docx} from the Markdown files in {@code examples/report}.
 *
 * <pre>mvn -q compile exec:java                       # default paths
 * mvn -q compile exec:java -Dexec.args="in-dir out.docx"</pre>
 */
public final class ExampleMain {

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "examples/report");
        Path out = Path.of(args.length > 1 ? args[1] : "target/example-report.docx");

        ComposeOptions options = ComposeOptions.builder()
                .title("订单中心技术方案")
                .subtitle("v1.0 · 2026 年 10 月")
                .tableOfContents(3)                       // TOC with heading levels 1-3
                .numberHeadings(true)                     // 1. / 1.1 / 1.1.1
                .pageBreakBetweenTopLevelSections(true)   // every chapter starts on a new page
                .pageNumbers(true)
                .build();

        // The outline decides heading levels; each file keeps whatever heading levels it was written with.
        List<DocSection> sections = List.of(
                // "# 项目概述" becomes chapter 1
                DocSection.fromMarkdown(read(dir, "01-overview.md")),
                // a chapter that only groups other files
                DocSection.titled("技术方案")
                        // "# 总体架构" becomes 2.1; the image path is resolved against dir
                        .child(DocSection.fromMarkdown(read(dir, "02-architecture.md")).baseDir(dir))
                        // "## 接口设计" becomes 2.2 and its "###" headings become 2.2.x
                        .child(DocSection.fromMarkdown(read(dir, "03-api.md"))),
                // explicit title; the file's "##" headings become 3.x
                DocSection.of("风险与计划", read(dir, "04-risks.md")));

        Files.createDirectories(out.toAbsolutePath().getParent());
        try (OutputStream os = Files.newOutputStream(out)) {
            new MarkdownDocxComposer(options).compose(sections, os);
        }
        System.out.println("written " + out.toAbsolutePath());
    }

    private static String read(Path dir, String name) throws IOException {
        return Files.readString(dir.resolve(name));
    }
}
