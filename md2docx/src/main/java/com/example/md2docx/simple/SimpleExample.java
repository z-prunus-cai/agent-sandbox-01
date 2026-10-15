package com.example.md2docx.simple;

import com.example.md2docx.DocSection;
import com.example.md2docx.MarkdownDocxComposer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Minimal usage: two Markdown files plus a sentence, each under its own heading.
 *
 * <pre>
 * # 文档1
 * {doc1.md}      ← its headings move down one level: "# 背景" becomes level 2
 * # 文档2
 * {doc2.md}
 * # 其他
 * 这是另一段话。
 * </pre>
 *
 * Run with {@code ./gradlew runSimple}; writes {@code build/simple.docx}.
 */
public final class SimpleExample {

    public static void main(String[] args) throws IOException {
        String doc1 = Files.readString(Path.of("examples/simple/doc1.md"));
        String doc2 = Files.readString(Path.of("examples/simple/doc2.md"));

        byte[] docx = new MarkdownDocxComposer().compose(List.of(
                DocSection.of("文档1", doc1),
                DocSection.of("文档2", doc2),
                DocSection.of("其他", "这是另一段话。")));

        Path out = Path.of("build/simple.docx");
        Files.createDirectories(out.getParent());
        Files.write(out, docx);
        System.out.println("written " + out.toAbsolutePath());
    }
}
