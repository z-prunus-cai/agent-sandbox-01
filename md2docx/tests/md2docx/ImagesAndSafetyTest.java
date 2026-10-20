package md2docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static md2docx.TestSupport.compose;
import static md2docx.TestSupport.open;
import static md2docx.TestSupport.paragraphs;
import static md2docx.TestSupport.part;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagesAndSafetyTest {

    @TempDir
    Path directory;

    static byte[] image(String format, int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    @Test
    void relativeImageIsReadFromTheSectionDirectoryAndCentredWhenAlone() throws Exception {
        Files.createDirectories(directory.resolve("images"));
        Files.write(directory.resolve("images/a.png"), image("png", 40, 20));
        byte[] docx = compose(Section.untitled("![Alt text](images/a.png)").baseDirectory(directory));
        try (XWPFDocument document = open(docx)) {
            XWPFParagraph paragraph = document.getParagraphs().get(0);
            assertEquals("CENTER", paragraph.getAlignment().name());
            XWPFPicture picture = paragraph.getRuns().get(0).getEmbeddedPictures().get(0);
            assertEquals(40 * 9525, picture.getCTPicture().getSpPr().getXfrm().getExt().getCx());
        }
        assertTrue(part(docx, "word/document.xml").contains("descr=\"Alt text\""));
    }

    @Test
    void everySupportedFormatIsEmbedded() throws Exception {
        for (String format : List.of("png", "jpg", "gif", "bmp")) {
            Files.write(directory.resolve("a." + format), image(format, 10, 10));
            byte[] docx = compose(Section.untitled("![x](a." + format + ")").baseDirectory(directory));
            try (XWPFDocument document = open(docx)) {
                assertEquals(1, document.getAllPictures().size(), format);
            }
        }
    }

    @Test
    void wideImageIsScaledDownToTheTextWidthKeepingItsProportions() throws Exception {
        Files.write(directory.resolve("wide.png"), image("png", 3000, 300));
        byte[] docx = compose(Section.untitled("![](wide.png)").baseDirectory(directory));
        try (XWPFDocument document = open(docx)) {
            var extent = document.getParagraphs().get(0).getRuns().get(0).getEmbeddedPictures().get(0)
                    .getCTPicture().getSpPr().getXfrm().getExt();
            long textWidthEmu = (11906 - 2 * 1440) * 635L;
            assertEquals(textWidthEmu, extent.getCx(), 1);
            assertEquals(extent.getCx() / 10.0, extent.getCy(), 2);
        }
    }

    @Test
    void imageInATableCellFitsTheColumn() throws Exception {
        Files.write(directory.resolve("wide.png"), image("png", 3000, 300));
        byte[] docx = compose(Section.untitled("| a | b |\n| - | - |\n| ![](wide.png) | text |").baseDirectory(directory));
        try (XWPFDocument document = open(docx)) {
            var cell = document.getTables().get(0).getRow(1).getCell(0);
            long cellWidthEmu = Long.parseLong(cell.getCTTc().getTcPr().getTcW().getW().toString()) * 635L;
            long imageWidth = cell.getParagraphs().get(0).getRuns().get(0).getEmbeddedPictures().get(0)
                    .getCTPicture().getSpPr().getXfrm().getExt().getCx();
            assertTrue(imageWidth < cellWidthEmu, imageWidth + " < " + cellWidthEmu);
        }
    }

    @Test
    void dataUriImageIsEmbedded() throws Exception {
        String data = Base64.getEncoder().encodeToString(image("png", 5, 5));
        byte[] docx = compose(Section.untitled("![d](data:image/png;base64," + data + ")"));
        try (XWPFDocument document = open(docx)) {
            assertEquals(1, document.getAllPictures().size());
        }
    }

    @Test
    void unusableImagesShowTheirAlternativeText() throws Exception {
        Files.writeString(directory.resolve("text.png"), "not an image");
        Files.write(directory.getParent().resolve("outside.png"), image("png", 5, 5));
        byte[] docx = compose(Section.untitled("""
                ![missing](none.png)

                ![fake](text.png)

                ![outside](../outside.png)

                ![broken](data:image/png;base64,***)

                ![](none.png)
                """).baseDirectory(directory));
        try (XWPFDocument document = open(docx)) {
            assertEquals(0, document.getAllPictures().size());
        }
        assertEquals(List.of("-: [missing]", "-: [fake]", "-: [outside]", "-: [broken]", "-: [image]"), paragraphs(docx));
    }

    @Test
    void localFilesRefusesEverythingOutsideTheDirectory() throws Exception {
        ImageResolver resolver = ImageResolver.localFiles();
        byte[] png = image("png", 5, 5);
        Files.createDirectories(directory.resolve("root/sub"));
        Files.write(directory.resolve("root/sub/in.png"), png);
        Files.write(directory.resolve("out.png"), png);
        Path root = directory.resolve("root");

        assertArrayEquals(png, resolver.resolve("sub/in.png", root));
        assertArrayEquals(png, resolver.resolve("./sub/../sub/in.png", root));
        assertNull(resolver.resolve("../out.png", root));
        assertNull(resolver.resolve("sub/../../out.png", root));
        assertNull(resolver.resolve(directory.resolve("out.png").toString(), root));
        assertNull(resolver.resolve("file://" + directory.resolve("out.png"), root));
        assertNull(resolver.resolve("sub/in.png", null));
        assertNull(resolver.resolve("sub", root));
        assertNull(resolver.resolve("data:text/plain;base64,aGk=", root));
        assertNull(resolver.resolve("data:image/png,raw", root));
    }

    @Test
    void localFilesNeverOpensANetworkConnection() throws Exception {
        AtomicBoolean connected = new AtomicBoolean();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread.ofVirtual().start(() -> {
                try (Socket ignored = server.accept()) {
                    connected.set(true);
                } catch (IOException closed) {
                    // the server is closed at the end of the test
                }
            });
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/a.png";
            assertNull(ImageResolver.localFiles().resolve(url, directory));
            compose(Section.untitled("![x](" + url + ")").baseDirectory(directory));
            Thread.sleep(200);
        }
        assertFalse(connected.get());
    }

    @Test
    void onlyWebAndMailLinksBecomeHyperlinks() throws Exception {
        byte[] docx = compose(Section.untitled("""
                [h](http://example.org/a) [s](https://example.org) [m](mailto:a@example.org) [f](ftp://example.org/x)
                [j](javascript:alert(1)) [file](file:///etc/passwd) [rel](other.md) [data](data:text/html,x)
                https://example.org/auto
                """));
        String relations = part(docx, "word/_rels/document.xml.rels");
        for (String target : List.of("http://example.org/a", "https://example.org", "mailto:a@example.org",
                "ftp://example.org/x", "https://example.org/auto")) {
            assertTrue(relations.contains("Target=\"" + target + "\""), target);
        }
        for (String refused : List.of("javascript", "file:", "other.md", "data:")) {
            assertFalse(relations.contains(refused), refused);
        }
        try (XWPFDocument document = open(docx)) {
            assertTrue(document.getParagraphs().get(0).getText().contains("j file rel data"));
        }
    }

    @Test
    void charactersXmlDoesNotAllowAreRemoved() throws Exception {
        byte[] docx = compose(Section.of("T\u0001itle", "a\u0000b\u000Bc￾\uD800d 😀"));
        try (XWPFDocument document = open(docx)) {
            assertEquals("Title", document.getParagraphs().get(0).getText());
            assertEquals("abcd 😀", document.getParagraphs().get(1).getText());
        }
    }

    @Test
    void customResolverReplacesTheDefault() throws Exception {
        byte[] png = image("png", 5, 5);
        ComposeOptions options = ComposeOptions.builder().imageResolver((destination, base) -> destination.equals("id:1") ? png : null).build();
        byte[] docx = compose(options, Section.untitled("![a](id:1) ![b](id:2)"));
        try (XWPFDocument document = open(docx)) {
            assertEquals(1, document.getAllPictures().size());
            assertTrue(document.getParagraphs().get(0).getText().contains("[b]"));
        }
    }
}
