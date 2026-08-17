package be.winnetrie.mod.simpleserverutilities.client.hologram;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import be.winnetrie.mod.simpleserverutilities.client.render.SsuDebugGizmos;
import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramImageCache.ImageView;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramImageDecoder.PixelRect;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramImageDecoder.RenderFrame;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichTextDocument;
import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextDocument.CharacterStyle;
import be.winnetrie.mod.simpleserverutilities.richtext.SsuRichTextDocument.Segment;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramType;
import be.winnetrie.mod.simpleserverutilities.network.HologramSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

/** Client renderer for synchronized floating text, links, scoreboards and image billboards. */
public final class HologramRenderer implements net.minecraft.client.renderer.debug.DebugRenderer.SimpleDebugRenderer {
    /** Current legacy scale 8 visually becomes the new, useful baseline scale 1. */
    static final float TEXT_SCALE_PER_UNIT = 0.20F;
    static final double LINE_SPACING_PER_UNIT = 0.24D;
    static final double TEXT_HALF_HEIGHT_PER_UNIT = 0.11D;
    /** Half of the approximate rendered world width of one Minecraft font pixel. */
    static final double TEXT_WIDTH_PER_FONT_PIXEL = 0.00675D;

    private static final double BACKGROUND_HORIZONTAL_PADDING = 0.060D;
    private static final double BACKGROUND_VERTICAL_PADDING = 0.045D;
    private static final double BACKGROUND_DEPTH_OFFSET_PER_SCALE = 0.020D;
    private static final double BOLD_OFFSET_PIXELS = 0.85D;
    private static final double DECORATION_DEPTH_OFFSET_PER_SCALE = 0.0020D;
    private static final double UNDERLINE_OFFSET_PER_SCALE = -0.092D;
    private static final double STRIKETHROUGH_OFFSET_PER_SCALE = -0.008D;
    private static final double DECORATION_THICKNESS_PER_SCALE = 0.012D;
    private static final double IMAGE_DEPTH_OFFSET = 0.002D;
    private static final int MAX_IMAGE_RECTANGLES_PER_FRAME = 16_384;

    private final Minecraft minecraft;

    public HologramRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource,
                       double camX, double camY, double camZ) {
        SsuDebugGizmos.begin(minecraft, poseStack, bufferSource, camX, camY, camZ);
        Frustum frustum = null; // 1.21.1 DebugRenderer does not pass its render frustum to child renderers.
        float partialTicks = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        if (minecraft.player == null || minecraft.level == null) return;
        String dimension = minecraft.level.dimension().location().toString();
        Vec3 player = minecraft.player.position();
        var camera = minecraft.gameRenderer.getMainCamera();
        var forwardVector = camera.getLookVector();
        var leftVector = camera.getLeftVector();
        var upVector = camera.getUpVector();
        Vec3 cameraForward = new Vec3(forwardVector.x(), forwardVector.y(), forwardVector.z()).normalize();
        Vec3 cameraHorizontal = new Vec3(leftVector.x(), leftVector.y(), leftVector.z()).normalize();
        Vec3 cameraUp = new Vec3(upVector.x(), upVector.y(), upVector.z()).normalize();

        int remainingImageRectangles = MAX_IMAGE_RECTANGLES_PER_FRAME;
        for (HologramSyncPayload.Entry entry : HologramClientState.entries()) {
            if (!dimension.equals(entry.dimension())) continue;
            Vec3 position = new Vec3(entry.x(), entry.y(), entry.z());
            if (player.distanceToSqr(position) > entry.viewDistance() * entry.viewDistance()) continue;
            if (entry.type() == HologramType.IMAGE) {
                remainingImageRectangles -= renderImage(entry, position, cameraForward,
                        cameraHorizontal, cameraUp, Math.max(0, remainingImageRectangles));
            } else {
                renderEntry(entry, position, cameraForward, cameraHorizontal, cameraUp);
            }
        }
    }

    private int renderImage(
            HologramSyncPayload.Entry entry,
            Vec3 position,
            Vec3 cameraForward,
            Vec3 cameraHorizontal,
            Vec3 cameraUp,
            int rectangleBudget
    ) {
        ImageView view = HologramImageCache.getOrRequest(minecraft, entry.imageSource());
        if (view.asset() == null) {
            int color = view.status() == HologramImageCache.Status.ERROR ? 0xFFFF5555 : 0xFFFFFF55;
            String label = view.status() == HologramImageCache.Status.ERROR
                    ? "[Image unavailable: " + view.message() + "]"
                    : "[Loading image...]";
            var placeholder = SsuDebugGizmos.billboardText(label, position,
                    SsuDebugGizmos.TextStyle.forColorAndCentered(color)
                            .withScale(Math.max(0.12F, TEXT_SCALE_PER_UNIT * entry.scale() * 0.65F)));
            if (entry.seeThrough()) placeholder.setAlwaysOnTop();
            return 0;
        }

        RenderFrame frame = view.asset().frame(System.nanoTime(), view.animationEpochNanos());
        if (frame == null) return 0;
        if (rectangleBudget <= 0 || frame.rectangles().size() > rectangleBudget) {
            var limited = SsuDebugGizmos.billboardText("[Image render limit reached]", position,
                    SsuDebugGizmos.TextStyle.forColorAndCentered(0xFFFFAA00)
                            .withScale(Math.max(0.12F, TEXT_SCALE_PER_UNIT * entry.scale() * 0.65F)));
            if (entry.seeThrough()) limited.setAlwaysOnTop();
            return Math.max(0, rectangleBudget);
        }

        double worldWidth = Math.max(0.1D, entry.imageWidth() * entry.scale());
        double worldHeight = Math.max(0.1D, entry.imageHeight() * entry.scale());
        Vec3 planeCenter = position.subtract(cameraForward.scale(IMAGE_DEPTH_OFFSET));
        int emitted = 0;

        for (PixelRect rectangle : frame.rectangles()) {
            if (emitted >= rectangleBudget) break;
            double pixelCenterX = (rectangle.x0() + rectangle.x1()) * 0.5D / frame.width();
            double pixelCenterY = (rectangle.y0() + rectangle.y1()) * 0.5D / frame.height();
            double horizontalOffset = (0.5D - pixelCenterX) * worldWidth;
            double verticalOffset = (0.5D - pixelCenterY) * worldHeight;
            double halfWidth = (rectangle.x1() - rectangle.x0()) * worldWidth / frame.width() * 0.5D;
            double halfHeight = (rectangle.y1() - rectangle.y0()) * worldHeight / frame.height() * 0.5D;

            Vec3 center = planeCenter
                    .add(cameraHorizontal.scale(horizontalOffset))
                    .add(cameraUp.scale(verticalOffset));
            Vec3 horizontal = cameraHorizontal.scale(halfWidth);
            Vec3 vertical = cameraUp.scale(halfHeight);
            var pixel = SsuDebugGizmos.rect(
                    center.subtract(horizontal).add(vertical),
                    center.add(horizontal).add(vertical),
                    center.add(horizontal).subtract(vertical),
                    center.subtract(horizontal).subtract(vertical),
                    SsuDebugGizmos.FillStyle.fill(rectangle.argb()));
            if (entry.seeThrough()) pixel.setAlwaysOnTop();
            emitted++;
        }
        return emitted;
    }

    private void renderEntry(
            HologramSyncPayload.Entry entry,
            Vec3 position,
            Vec3 cameraForward,
            Vec3 cameraHorizontal,
            Vec3 cameraUp
    ) {
        List<String> visibleLines = visibleLines(entry);
        float textScale = TEXT_SCALE_PER_UNIT * entry.scale();
        double spacing = LINE_SPACING_PER_UNIT * entry.scale();

        drawBackground(entry, visibleLines, position, spacing, cameraForward, cameraHorizontal, cameraUp);

        for (int index = 0; index < visibleLines.size(); index++) {
            String encodedLine = decorate(visibleLines.get(index), entry);
            // All lines use the same camera billboard plane as the background.
            Vec3 linePosition = position.subtract(cameraUp.scale(index * spacing));
            renderRichLine(entry, encodedLine, linePosition,
                    cameraForward, cameraHorizontal, cameraUp, textScale);
        }
    }

    private void renderRichLine(
            HologramSyncPayload.Entry entry,
            String encodedLine,
            Vec3 lineCenter,
            Vec3 cameraForward,
            Vec3 cameraHorizontal,
            Vec3 cameraUp,
            float textScale
    ) {
        HologramRichTextDocument document = new HologramRichTextDocument(encodedLine);
        List<Segment> segments = document.segments(0, document.plainText().length());
        if (segments.isEmpty()) {
            var empty = SsuDebugGizmos.billboardText("", lineCenter,
                    SsuDebugGizmos.TextStyle.forColorAndCentered(normalizeColor(entry.color())).withScale(textScale));
            if (entry.seeThrough()) empty.setAlwaysOnTop();
            return;
        }

        ArrayList<RenderedSegment> rendered = new ArrayList<>(segments.size());
        double totalPixels = 0.0D;
        for (Segment segment : segments) {
            String visualText = visualText(segment);
            int width = minecraft.font.width(visualText);
            // Bold is simulated with a sub-pixel duplicate, so reserve its small overhang.
            double layoutWidth = width + (segment.style().bold() ? BOLD_OFFSET_PIXELS : 0.0D);
            rendered.add(new RenderedSegment(segment, visualText, width, layoutWidth));
            totalPixels += layoutWidth;
        }

        double consumedPixels = 0.0D;
        for (RenderedSegment renderedSegment : rendered) {
            Segment segment = renderedSegment.segment();
            if (segment.text().isEmpty()) continue;

            double centerOffsetPixels = totalPixels * 0.5D
                    - consumedPixels - renderedSegment.layoutWidthPixels() * 0.5D;
            Vec3 segmentCenter = lineCenter.add(cameraHorizontal.scale(
                    centerOffsetPixels * TEXT_WIDTH_PER_FONT_PIXEL * 2.0D * entry.scale()));
            int segmentColor = resolveColor(segment.style(), entry.color());

            drawText(entry, renderedSegment.visualText(), segmentCenter,
                    cameraHorizontal, textScale, segmentColor, segment.style().bold());
            drawDecorations(entry, segment.style(), segmentCenter, renderedSegment.widthPixels(),
                    cameraForward, cameraHorizontal, cameraUp, segmentColor);
            consumedPixels += renderedSegment.layoutWidthPixels();
        }
    }

    private void drawText(
            HologramSyncPayload.Entry entry,
            String value,
            Vec3 center,
            Vec3 cameraHorizontal,
            float scale,
            int color,
            boolean bold
    ) {
        var primary = SsuDebugGizmos.billboardText(value, center,
                SsuDebugGizmos.TextStyle.forColorAndCentered(color).withScale(scale));
        if (entry.seeThrough()) primary.setAlwaysOnTop();

        if (bold) {
            // TextGizmo accepts plain strings only. A sub-pixel duplicate reproduces
            // Minecraft's bold overdraw without storing or exposing formatting codes.
            Vec3 duplicateCenter = center.subtract(cameraHorizontal.scale(
                    BOLD_OFFSET_PIXELS * TEXT_WIDTH_PER_FONT_PIXEL * 2.0D * entry.scale()));
            var duplicate = SsuDebugGizmos.billboardText(value, duplicateCenter,
                    SsuDebugGizmos.TextStyle.forColorAndCentered(color).withScale(scale));
            if (entry.seeThrough()) duplicate.setAlwaysOnTop();
        }
    }

    private void drawDecorations(
            HologramSyncPayload.Entry entry,
            CharacterStyle style,
            Vec3 center,
            int widthPixels,
            Vec3 cameraForward,
            Vec3 cameraHorizontal,
            Vec3 cameraUp,
            int color
    ) {
        if (widthPixels <= 0) return;
        if (style.underlined()) {
            drawDecoration(entry, center, widthPixels, UNDERLINE_OFFSET_PER_SCALE,
                    cameraForward, cameraHorizontal, cameraUp, color);
        }
        if (style.strikethrough()) {
            drawDecoration(entry, center, widthPixels, STRIKETHROUGH_OFFSET_PER_SCALE,
                    cameraForward, cameraHorizontal, cameraUp, color);
        }
    }

    private void drawDecoration(
            HologramSyncPayload.Entry entry,
            Vec3 segmentCenter,
            int widthPixels,
            double verticalOffsetPerScale,
            Vec3 cameraForward,
            Vec3 cameraHorizontal,
            Vec3 cameraUp,
            int color
    ) {
        double unitScale = entry.scale();
        double halfWidth = Math.max(0.01D,
                widthPixels * TEXT_WIDTH_PER_FONT_PIXEL * unitScale);
        double halfThickness = DECORATION_THICKNESS_PER_SCALE * unitScale * 0.5D;
        Vec3 center = segmentCenter
                .add(cameraUp.scale(verticalOffsetPerScale * unitScale))
                .subtract(cameraForward.scale(DECORATION_DEPTH_OFFSET_PER_SCALE * unitScale));
        Vec3 horizontal = cameraHorizontal.scale(halfWidth);
        Vec3 vertical = cameraUp.scale(halfThickness);
        var decoration = SsuDebugGizmos.rect(
                center.subtract(horizontal).add(vertical),
                center.add(horizontal).add(vertical),
                center.add(horizontal).subtract(vertical),
                center.subtract(horizontal).subtract(vertical),
                SsuDebugGizmos.FillStyle.fill(color));
        if (entry.seeThrough()) decoration.setAlwaysOnTop();
    }

    private List<String> visibleLines(HologramSyncPayload.Entry entry) {
        List<String> source = entry.lines().isEmpty() ? List.of("") : entry.lines();
        List<String> visible = new ArrayList<>(source);
        if (entry.type() == HologramType.LINK && !visible.isEmpty()) {
            int last = visible.size() - 1;
            visible.set(last, visible.get(last) + "  §7[Right-click]");
        }
        return visible;
    }

    private void drawBackground(
            HologramSyncPayload.Entry entry,
            List<String> visibleLines,
            Vec3 firstLinePosition,
            double spacing,
            Vec3 cameraForward,
            Vec3 cameraHorizontal,
            Vec3 cameraUp
    ) {
        if ((entry.backgroundColor() >>> 24) == 0) return;

        double unitScale = entry.scale();
        int maximumWidth = 0;
        for (String line : visibleLines) {
            maximumWidth = Math.max(maximumWidth,
                    styledWidthPixels(minecraft, decorate(line, entry), entry.color()));
        }
        double halfWidth = Math.max(0.14D,
                maximumWidth * TEXT_WIDTH_PER_FONT_PIXEL * unitScale)
                + BACKGROUND_HORIZONTAL_PADDING * unitScale;

        int lineCount = Math.max(1, visibleLines.size());
        double halfTextBlockHeight = ((lineCount - 1) * spacing) * 0.5D
                + TEXT_HALF_HEIGHT_PER_UNIT * unitScale;
        double halfHeight = Math.max(0.10D, halfTextBlockHeight)
                + BACKGROUND_VERTICAL_PADDING * unitScale;

        Vec3 blockCenter = firstLinePosition.subtract(cameraUp.scale(((lineCount - 1) * spacing) * 0.5D));
        Vec3 center = blockCenter.add(cameraForward.scale(BACKGROUND_DEPTH_OFFSET_PER_SCALE * unitScale));
        Vec3 horizontal = cameraHorizontal.scale(halfWidth);
        Vec3 vertical = cameraUp.scale(halfHeight);
        var background = SsuDebugGizmos.rect(
                center.subtract(horizontal).add(vertical),
                center.add(horizontal).add(vertical),
                center.add(horizontal).subtract(vertical),
                center.subtract(horizontal).subtract(vertical),
                SsuDebugGizmos.FillStyle.fill(entry.backgroundColor()));
        if (entry.seeThrough()) background.setAlwaysOnTop();
    }

    static int styledWidthPixels(Minecraft minecraft, String encoded, int baseColor) {
        HologramRichTextDocument document = new HologramRichTextDocument(encoded);
        int width = 0;
        for (Segment segment : document.segments(0, document.plainText().length())) {
            String visual = visualText(segment);
            width += minecraft.font.width(visual);
            if (segment.style().bold()) width += 1;
        }
        return width;
    }

    static MutableComponent styledComponent(String encoded, int baseColor) {
        HologramRichTextDocument document = new HologramRichTextDocument(encoded);
        MutableComponent result = Component.empty();
        for (Segment segment : document.segments(0, document.plainText().length())) {
            result.append(component(segment, baseColor));
        }
        return result;
    }

    private static MutableComponent component(Segment segment, int baseColor) {
        CharacterStyle richStyle = segment.style();
        int color = resolveColor(richStyle, baseColor);
        return Component.literal(segment.text()).withStyle(style -> style
                .withColor(color & 0xFFFFFF)
                .withBold(richStyle.bold())
                .withItalic(richStyle.italic())
                .withUnderlined(richStyle.underlined())
                .withStrikethrough(richStyle.strikethrough()));
    }

    private static int resolveColor(CharacterStyle style, int baseColor) {
        if (style.colorIndex() < 0) return normalizeColor(baseColor);
        int alpha = baseColor & 0xFF000000;
        if (alpha == 0) alpha = 0xFF000000;
        return alpha | HologramRichText.minecraftColorRgb(style.colorIndex());
    }

    private static int normalizeColor(int value) {
        return (value >>> 24) == 0 ? 0xFF000000 | value : value;
    }

    /**
     * TextGizmo has no italic style component, so the display-only text uses the
     * Unicode mathematical italic glyphs for ordinary Latin letters. Stored text,
     * editing, selection, copying and server data all retain the original characters.
     */
    private static String visualText(Segment segment) {
        if (!segment.style().italic() || segment.text().isEmpty()) return segment.text();
        StringBuilder result = new StringBuilder(segment.text().length() * 2);
        segment.text().codePoints().forEach(codePoint -> result.appendCodePoint(italicCodePoint(codePoint)));
        return result.toString();
    }

    private static int italicCodePoint(int codePoint) {
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return 0x1D434 + (codePoint - 'A');
        }
        if (codePoint >= 'a' && codePoint <= 'g') {
            return 0x1D44E + (codePoint - 'a');
        }
        if (codePoint == 'h') return 0x210E;
        if (codePoint >= 'i' && codePoint <= 'z') {
            return 0x1D456 + (codePoint - 'i');
        }
        return codePoint;
    }

    private static String decorate(String value, HologramSyncPayload.Entry entry) {
        StringBuilder prefix = new StringBuilder();
        if (entry.bold()) prefix.append("§l");
        if (entry.italic()) prefix.append("§o");
        if (entry.underlined()) prefix.append("§n");
        if (entry.strikethrough()) prefix.append("§m");
        return prefix + (value == null ? "" : value) + "§r";
    }

    private record RenderedSegment(
            Segment segment,
            String visualText,
            int widthPixels,
            double layoutWidthPixels
    ) {
    }
}
