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
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

/** Inline images: PNG, JPEG, GIF and BMP, scaled down to fit the available width and 90 % of the text height. */
final class Images {

    private static final int EMU_PER_TWIP = Units.EMU_PER_POINT / 20;

    private Images() {
    }

    /**
     * Adds the picture to {@code run}; when it cannot be loaded, is not a supported format, or {@code embed} is
     * false, writes {@code [alternative text]} instead.
     */
    static void render(XWPFRun run, Image image, boolean embed, ImageResolver resolver, Path baseDirectory,
                       int maximumWidthTwips, int maximumHeightTwips) {
        String alternative = Texts.xmlSafe(Texts.plainText(image));
        if (!embed || !add(run, image, alternative, resolver, baseDirectory, maximumWidthTwips, maximumHeightTwips)) {
            Texts.write(run, "[" + (alternative.isEmpty() ? "image" : alternative) + "]");
        }
    }

    private static boolean add(XWPFRun run, Image image, String alternative, ImageResolver resolver, Path baseDirectory,
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
            // POI writes this "file name" into both alternative-text attributes of the picture
            run.addPicture(new ByteArrayInputStream(bytes), type, alternative, (int) (width * scale), (int) (height * scale));
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

    /** Reads the size from the image header without decoding the pixels, and without a temporary file. */
    private static int[] pixelSize(byte[] bytes) throws IOException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
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
