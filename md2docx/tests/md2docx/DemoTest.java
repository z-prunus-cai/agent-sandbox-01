package md2docx;

import demo.FullDemo;
import demo.MinimalDemo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static md2docx.TestSupport.paragraphs;
import static md2docx.TestSupport.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The outcomes of the two demos that the brief quotes. */
class DemoTest {

    @TempDir
    Path directory;

    @Test
    void minimalDemoPutsEachFileUnderItsHeadingAndLinksAcrossFiles() throws Exception {
        Path output = directory.resolve("minimal.docx");
        MinimalDemo.main(new String[] {"examples/markdown/minimal", output.toString()});
        byte[] docx = Files.readAllBytes(output);
        assertEquals(List.of("Heading1: 文档一", "Heading2: 背景", "-: 第一个文件的正文。", "Heading3: 目标",
                "ListParagraph: 第一项", "ListParagraph: 第二项", "Heading1: 文档二", "Heading2: 用法", "-: 第二个文件的正文，见背景。",
                "Heading1: 其他", "-: 这是另一段话。"), paragraphs(docx));
        String xml = part(docx, "word/document.xml");
        Matcher background = Pattern.compile("<w:bookmarkStart w:name=\"([^\"]+)\" w:id=\"\\d+\"/><w:r><w:t>背景</w:t>").matcher(xml);
        assertTrue(background.find());
        assertTrue(xml.contains("<w:hyperlink w:anchor=\"" + background.group(1) + "\">"));
    }

    @Test
    void fullDemoPlacesEveryFragmentAsTheBriefDescribes() throws Exception {
        Path output = directory.resolve("full.docx");
        FullDemo.main(new String[] {"examples/markdown/full", output.toString()});
        byte[] docx = Files.readAllBytes(output);
        List<String> headings = paragraphs(docx).stream().filter(line -> line.startsWith("Heading")).toList();
        assertEquals(List.of("Heading1: 概述", "Heading2: 范围", "Heading2: 术语", "Heading1: 设计", "Heading2: 结构",
                "Heading3: 处理顺序", "Heading2: 接口", "Heading1: 附注", "Heading2: 说明"), headings);
        assertEquals(2, Pattern.compile("<w:footnote ").matcher(part(docx, "word/footnotes.xml")).results().count());
        assertTrue(part(docx, "word/numbering.xml").contains("<w:startOverride w:val=\"3\"/>"));
        assertTrue(part(docx, "word/document.xml").contains("<pic:pic"));
        assertTrue(paragraphs(docx).contains("Title: 示例文档"));
    }
}
