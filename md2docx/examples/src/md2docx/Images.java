package md2docx;

import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/** Inline images: PNG, JPEG, GIF and BMP, scaled down to fit the available width and 90 % of the text height. */
final class Images {

    private static final int EMU_PER_TWIP = Units.EMU_PER_POINT / 20;

    private Images() {
    }

    /** Adds the picture to {@code run}, or returns false when it cannot be loaded or is not a supported format. */
    static boolean add(XWPFRun run, ImageResolver resolver, Image image, Path baseDirectory,
                       int maximumWidthTwips, int maximumHeightTwips) {
        try {
            byte[] bytes = resolver.resolve(image.getDestination(), baseDirectory);
            PictureType type = bytes == null ? null : type(bytes);
            int[] pixels = type == null ? null : pixelSize(bytes);
            if (pixels == null) return false;
            double width = pixels[0] * (double) Units.EMU_PER_PIXEL;
            double height = pixels[1] * (double) Units.EMU_PER_PIXEL;
            double scale = Math.min(1, Math.min(maximumWidthTwips * (double) EMU_PER_TWIP / width,
                    maximumHeightTwips * 0.9 * EMU_PER_TWIP / height));
            run.addPicture(new ByteArrayInputStream(bytes), type, "image" + type.extension,
                    (int) (width * scale), (int) (height * scale));
            String alternative = Texts.plainText(image);
            if (!alternative.isEmpty()) {
                run.getCTR().getDrawingArray(0).getInlineArray(0).getDocPr().setDescr(Texts.xmlSafe(alternative));
            }
            return true;
        } catch (Exception unreadable) {
            return false;
        }
    }

    /** True when the paragraph holds images and nothing else but white space. */
    static boolean onlyImages(Node paragraph) {
        boolean image = false;
        for (Node child = paragraph.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Image) image = true;
            else if (!(child instanceof Text text && text.getLiteral().isBlank()) && !(child instanceof SoftLineBreak)) return false;
        }
        return image;
    }

    /** Detects the format from the first bytes; the file name and the Markdown are not trusted for this. */
    private static PictureType type(byte[] bytes) {
        if (bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return PictureType.PNG;
        if (bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) return PictureType.JPEG;
        if (bytes.length > 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return PictureType.GIF;
        if (bytes.length > 2 && bytes[0] == 'B' && bytes[1] == 'M') return PictureType.BMP;
        return null;
    }

    /** Reads the size from the image header without decoding the pixels. */
    private static int[] pixelSize(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width > 0 && height > 0 ? new int[] {width, height} : null;
            } finally {
                reader.dispose();
            }
        }
    }
}
