package be.winnetrie.mod.simpleserverutilities.client.hologram;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Dependency-free PNG/JPEG/GIF decoder used by the client hologram image cache.
 *
 * <p>The renderer intentionally converts images to compact colored rectangles.
 * That keeps image holograms compatible with Minecraft 26.2's gizmo renderer
 * without uploading arbitrary remote data into the game's texture atlases.</p>
 */
public final class HologramImageDecoder {
    public static final int MAX_SOURCE_DIMENSION = 4096;
    public static final long MAX_SOURCE_PIXELS = 16_777_216L;
    public static final int MAX_GIF_FRAMES = 180;
    public static final int MAX_RENDER_DIMENSION = 64;
    public static final int MAX_TOTAL_RENDER_RECTANGLES = 262_144;
    private static final int MIN_GIF_DELAY_MILLIS = 20;
    private static final int DEFAULT_GIF_DELAY_MILLIS = 100;

    private HologramImageDecoder() {
    }

    public static ImageAsset decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IOException("The image file is empty.");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IOException("The image stream could not be opened.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image data. Use PNG, GIF or JPG.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                return switch (format) {
                    case "png" -> decodeSingle(reader, "png");
                    case "jpeg", "jpg" -> decodeSingle(reader, "jpg");
                    case "gif" -> decodeGif(reader);
                    default -> throw new IOException("Unsupported image format '" + format
                            + "'. Use PNG, GIF or JPG.");
                };
            } finally {
                reader.dispose();
            }
        }
    }

    private static ImageAsset decodeSingle(ImageReader reader, String format) throws IOException {
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        validateSourceSize(width, height);
        BufferedImage image = reader.read(0);
        if (image == null) throw new IOException("The " + format.toUpperCase(Locale.ROOT) + " could not be decoded.");
        RenderFrame frame = toRenderFrame(image);
        return new ImageAsset(format, width, height, List.of(frame), List.of(0L), 0L);
    }

    private static ImageAsset decodeGif(ImageReader reader) throws IOException {
        GifCanvasInfo canvasInfo = readGifCanvas(reader.getStreamMetadata(), reader.getWidth(0), reader.getHeight(0));
        validateSourceSize(canvasInfo.width(), canvasInfo.height());

        int frameCount;
        try {
            frameCount = reader.getNumImages(true);
        } catch (IOException ignored) {
            frameCount = -1;
        }
        if (frameCount > MAX_GIF_FRAMES) {
            throw new IOException("Animated GIF has too many frames (maximum " + MAX_GIF_FRAMES + ").");
        }

        BufferedImage canvas = new BufferedImage(canvasInfo.width(), canvasInfo.height(), BufferedImage.TYPE_INT_ARGB);
        ArrayList<RenderFrame> frames = new ArrayList<>();
        ArrayList<Long> starts = new ArrayList<>();
        long elapsedNanos = 0L;
        int totalRenderRectangles = 0;

        for (int index = 0; frameCount < 0 || index < frameCount; index++) {
            if (index >= MAX_GIF_FRAMES) {
                throw new IOException("Animated GIF has too many frames (maximum " + MAX_GIF_FRAMES + ").");
            }
            BufferedImage frame;
            try {
                frame = reader.read(index);
            } catch (IndexOutOfBoundsException exception) {
                break;
            } catch (IOException exception) {
                if (frameCount < 0 && index > 0) break;
                throw exception;
            }
            if (frame == null) break;

            GifFrameInfo info = readGifFrame(reader.getImageMetadata(index), frame.getWidth(), frame.getHeight());
            BufferedImage before = "restoreToPrevious".equals(info.disposalMethod()) ? copy(canvas) : null;

            composite(canvas, frame, info.left(), info.top());

            RenderFrame renderedFrame = toRenderFrame(canvas);
            totalRenderRectangles += renderedFrame.rectangles().size();
            if (totalRenderRectangles > MAX_TOTAL_RENDER_RECTANGLES) {
                throw new IOException("Animated GIF is too visually complex for a hologram.");
            }
            starts.add(elapsedNanos);
            frames.add(renderedFrame);
            int delayMillis = Math.max(MIN_GIF_DELAY_MILLIS,
                    info.delayMillis() <= 0 ? DEFAULT_GIF_DELAY_MILLIS : info.delayMillis());
            elapsedNanos += delayMillis * 1_000_000L;

            switch (info.disposalMethod()) {
                case "restoreToBackgroundColor" -> clearArea(canvas, info.left(), info.top(),
                        info.width(), info.height());
                case "restoreToPrevious" -> {
                    if (before != null) canvas = before;
                }
                default -> {
                    // none/doNotDispose: leave the composited frame on the canvas.
                }
            }
        }

        if (frames.isEmpty()) throw new IOException("The GIF contains no readable frames.");
        long duration = frames.size() <= 1 ? 0L : Math.max(elapsedNanos, DEFAULT_GIF_DELAY_MILLIS * 1_000_000L);
        return new ImageAsset("gif", canvasInfo.width(), canvasInfo.height(), frames, starts, duration);
    }

    private static void validateSourceSize(int width, int height) throws IOException {
        if (width <= 0 || height <= 0) throw new IOException("The image has invalid dimensions.");
        if (width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION
                || (long) width * (long) height > MAX_SOURCE_PIXELS) {
            throw new IOException("The image is too large. Maximum decoded size is "
                    + MAX_SOURCE_DIMENSION + "x" + MAX_SOURCE_DIMENSION + ".");
        }
    }

    private static RenderFrame toRenderFrame(BufferedImage source) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        double scale = Math.min(1.0D, Math.min(
                MAX_RENDER_DIMENSION / (double) sourceWidth,
                MAX_RENDER_DIMENSION / (double) sourceHeight));
        int width = Math.max(1, (int) Math.round(sourceWidth * scale));
        int height = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage rendered = width == sourceWidth && height == sourceHeight
                ? copy(source)
                : resize(source, width, height, sourceWidth <= 64 && sourceHeight <= 64);

        int[][] pixels = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y][x] = quantize(rendered.getRGB(x, y));
            }
        }
        return new RenderFrame(width, height, mergeRectangles(pixels));
    }

    /** Greedily merges identical horizontal runs across adjacent rows. */
    private static List<PixelRect> mergeRectangles(int[][] pixels) {
        int height = pixels.length;
        int width = height == 0 ? 0 : pixels[0].length;
        ArrayList<PixelRect> completed = new ArrayList<>();
        Map<RunKey, MutableRect> active = new HashMap<>();

        for (int y = 0; y < height; y++) {
            Map<RunKey, MutableRect> next = new HashMap<>();
            int x = 0;
            while (x < width) {
                int color = pixels[y][x];
                int start = x;
                while (++x < width && pixels[y][x] == color) {
                    // Continue the horizontal run.
                }
                if ((color >>> 24) < 8) continue;

                RunKey key = new RunKey(start, x, color);
                MutableRect rectangle = active.remove(key);
                if (rectangle == null) {
                    rectangle = new MutableRect(start, x, y, y + 1, color);
                } else {
                    rectangle.y1 = y + 1;
                }
                next.put(key, rectangle);
            }
            for (MutableRect rectangle : active.values()) completed.add(rectangle.freeze());
            active = next;
        }
        for (MutableRect rectangle : active.values()) completed.add(rectangle.freeze());
        return List.copyOf(completed);
    }

    private static int quantize(int argb) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha < 8) return 0;
        // Small quantization dramatically reduces the number of billboard rectangles
        // while retaining visually smooth PNG/JPG/GIF images.
        alpha = Math.min(255, ((alpha + 8) / 17) * 17);
        int red = ((argb >>> 16) & 0xFF) & 0xF8;
        int green = ((argb >>> 8) & 0xFF) & 0xF8;
        int blue = (argb & 0xFF) & 0xF8;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static GifCanvasInfo readGifCanvas(IIOMetadata metadata, int fallbackWidth, int fallbackHeight) {
        if (metadata == null) return new GifCanvasInfo(fallbackWidth, fallbackHeight);
        try {
            Node root = metadata.getAsTree("javax_imageio_gif_stream_1.0");
            Node descriptor = child(root, "LogicalScreenDescriptor");
            if (descriptor == null) return new GifCanvasInfo(fallbackWidth, fallbackHeight);
            int width = integerAttribute(descriptor, "logicalScreenWidth", fallbackWidth);
            int height = integerAttribute(descriptor, "logicalScreenHeight", fallbackHeight);
            return new GifCanvasInfo(width, height);
        } catch (Exception ignored) {
            return new GifCanvasInfo(fallbackWidth, fallbackHeight);
        }
    }

    private static GifFrameInfo readGifFrame(IIOMetadata metadata, int fallbackWidth, int fallbackHeight) {
        int left = 0;
        int top = 0;
        int width = fallbackWidth;
        int height = fallbackHeight;
        int delayMillis = DEFAULT_GIF_DELAY_MILLIS;
        String disposal = "none";
        if (metadata != null) {
            try {
                Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");
                Node descriptor = child(root, "ImageDescriptor");
                if (descriptor != null) {
                    left = integerAttribute(descriptor, "imageLeftPosition", 0);
                    top = integerAttribute(descriptor, "imageTopPosition", 0);
                    width = integerAttribute(descriptor, "imageWidth", fallbackWidth);
                    height = integerAttribute(descriptor, "imageHeight", fallbackHeight);
                }
                Node control = child(root, "GraphicControlExtension");
                if (control != null) {
                    delayMillis = integerAttribute(control, "delayTime", 10) * 10;
                    disposal = stringAttribute(control, "disposalMethod", "none");
                }
            } catch (Exception ignored) {
                // Fall back to the decoded frame dimensions and a safe default delay.
            }
        }
        return new GifFrameInfo(left, top, width, height, delayMillis, disposal);
    }

    private static Node child(Node parent, String name) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (name.equals(child.getNodeName())) return child;
        }
        return null;
    }

    private static int integerAttribute(Node node, String name, int fallback) {
        String value = stringAttribute(node, name, null);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String stringAttribute(Node node, String name, String fallback) {
        NamedNodeMap attributes = node == null ? null : node.getAttributes();
        Node attribute = attributes == null ? null : attributes.getNamedItem(name);
        return attribute == null ? fallback : attribute.getNodeValue();
    }

    private static BufferedImage copy(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);
        copy.setRGB(0, 0, width, height, pixels, 0, width);
        return copy;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height, boolean nearest) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int[] sourcePixels = source.getRGB(0, 0, sourceWidth, sourceHeight, null, 0, sourceWidth);
        int[] targetPixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            double sourceY = (y + 0.5D) * sourceHeight / height - 0.5D;
            for (int x = 0; x < width; x++) {
                double sourceX = (x + 0.5D) * sourceWidth / width - 0.5D;
                targetPixels[y * width + x] = nearest
                        ? nearest(sourcePixels, sourceWidth, sourceHeight, sourceX, sourceY)
                        : bilinear(sourcePixels, sourceWidth, sourceHeight, sourceX, sourceY);
            }
        }
        result.setRGB(0, 0, width, height, targetPixels, 0, width);
        return result;
    }

    private static int nearest(int[] pixels, int width, int height, double x, double y) {
        int ix = Math.max(0, Math.min(width - 1, (int) Math.round(x)));
        int iy = Math.max(0, Math.min(height - 1, (int) Math.round(y)));
        return pixels[iy * width + ix];
    }

    private static int bilinear(int[] pixels, int width, int height, double x, double y) {
        int x0 = Math.max(0, Math.min(width - 1, (int) Math.floor(x)));
        int y0 = Math.max(0, Math.min(height - 1, (int) Math.floor(y)));
        int x1 = Math.min(width - 1, x0 + 1);
        int y1 = Math.min(height - 1, y0 + 1);
        double fx = Math.max(0.0D, Math.min(1.0D, x - Math.floor(x)));
        double fy = Math.max(0.0D, Math.min(1.0D, y - Math.floor(y)));
        int top = interpolate(pixels[y0 * width + x0], pixels[y0 * width + x1], fx);
        int bottom = interpolate(pixels[y1 * width + x0], pixels[y1 * width + x1], fx);
        return interpolate(top, bottom, fy);
    }

    private static int interpolate(int first, int second, double amount) {
        double inverse = 1.0D - amount;
        int alpha = (int) Math.round(((first >>> 24) & 0xFF) * inverse + ((second >>> 24) & 0xFF) * amount);
        int red = (int) Math.round(((first >>> 16) & 0xFF) * inverse + ((second >>> 16) & 0xFF) * amount);
        int green = (int) Math.round(((first >>> 8) & 0xFF) * inverse + ((second >>> 8) & 0xFF) * amount);
        int blue = (int) Math.round((first & 0xFF) * inverse + (second & 0xFF) * amount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static void composite(BufferedImage destination, BufferedImage source, int left, int top) {
        int destinationWidth = destination.getWidth();
        int destinationHeight = destination.getHeight();
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        for (int y = 0; y < sourceHeight; y++) {
            int targetY = top + y;
            if (targetY < 0 || targetY >= destinationHeight) continue;
            for (int x = 0; x < sourceWidth; x++) {
                int targetX = left + x;
                if (targetX < 0 || targetX >= destinationWidth) continue;
                int sourceArgb = source.getRGB(x, y);
                int sourceAlpha = (sourceArgb >>> 24) & 0xFF;
                if (sourceAlpha == 0) continue;
                if (sourceAlpha == 255) {
                    destination.setRGB(targetX, targetY, sourceArgb);
                    continue;
                }
                int destinationArgb = destination.getRGB(targetX, targetY);
                destination.setRGB(targetX, targetY, blend(sourceArgb, destinationArgb));
            }
        }
    }

    private static int blend(int source, int destination) {
        int sourceAlpha = (source >>> 24) & 0xFF;
        int destinationAlpha = (destination >>> 24) & 0xFF;
        int inverse = 255 - sourceAlpha;
        int outputAlpha = sourceAlpha + (destinationAlpha * inverse + 127) / 255;
        if (outputAlpha <= 0) return 0;

        int red = blendChannel((source >>> 16) & 0xFF, (destination >>> 16) & 0xFF,
                sourceAlpha, destinationAlpha, inverse, outputAlpha);
        int green = blendChannel((source >>> 8) & 0xFF, (destination >>> 8) & 0xFF,
                sourceAlpha, destinationAlpha, inverse, outputAlpha);
        int blue = blendChannel(source & 0xFF, destination & 0xFF,
                sourceAlpha, destinationAlpha, inverse, outputAlpha);
        return (outputAlpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int blendChannel(int source, int destination, int sourceAlpha,
                                    int destinationAlpha, int inverse, int outputAlpha) {
        int premultiplied = source * sourceAlpha
                + (destination * destinationAlpha * inverse + 127) / 255;
        return Math.max(0, Math.min(255, (premultiplied + outputAlpha / 2) / outputAlpha));
    }

    private static void clearArea(BufferedImage image, int x, int y, int width, int height) {
        int startX = Math.max(0, x);
        int startY = Math.max(0, y);
        int endX = Math.min(image.getWidth(), x + width);
        int endY = Math.min(image.getHeight(), y + height);
        for (int yy = startY; yy < endY; yy++) {
            for (int xx = startX; xx < endX; xx++) image.setRGB(xx, yy, 0);
        }
    }

    public record ImageAsset(
            String format,
            int sourceWidth,
            int sourceHeight,
            List<RenderFrame> frames,
            List<Long> frameStartNanos,
            long durationNanos
    ) {
        public ImageAsset {
            frames = frames == null ? List.of() : List.copyOf(frames);
            frameStartNanos = frameStartNanos == null ? List.of() : List.copyOf(frameStartNanos);
        }

        public boolean animated() {
            return frames.size() > 1 && durationNanos > 0L;
        }

        public RenderFrame frame(long nowNanos, long animationEpochNanos) {
            if (frames.isEmpty()) return null;
            if (!animated()) return frames.getFirst();
            long elapsed = Math.floorMod(nowNanos - animationEpochNanos, durationNanos);
            int selected = 0;
            for (int index = 1; index < frameStartNanos.size(); index++) {
                if (frameStartNanos.get(index) > elapsed) break;
                selected = index;
            }
            return frames.get(Math.min(selected, frames.size() - 1));
        }
    }

    public record RenderFrame(int width, int height, List<PixelRect> rectangles) {
        public RenderFrame {
            rectangles = rectangles == null ? List.of() : List.copyOf(rectangles);
        }
    }

    public record PixelRect(int x0, int x1, int y0, int y1, int argb) {
    }

    private record GifCanvasInfo(int width, int height) {
    }

    private record GifFrameInfo(
            int left,
            int top,
            int width,
            int height,
            int delayMillis,
            String disposalMethod
    ) {
    }

    private record RunKey(int x0, int x1, int argb) {
    }

    private static final class MutableRect {
        private final int x0;
        private final int x1;
        private final int y0;
        private int y1;
        private final int argb;

        private MutableRect(int x0, int x1, int y0, int y1, int argb) {
            this.x0 = x0;
            this.x1 = x1;
            this.y0 = y0;
            this.y1 = y1;
            this.argb = argb;
        }

        private PixelRect freeze() {
            return new PixelRect(x0, x1, y0, y1, argb);
        }
    }
}
