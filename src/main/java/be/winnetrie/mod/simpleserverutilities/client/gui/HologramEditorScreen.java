package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichTextDocument;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichTextDocument.Format;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramScoreboardMode;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramType;
import be.winnetrie.mod.simpleserverutilities.mixin.MultiLineEditBoxAccessor;
import be.winnetrie.mod.simpleserverutilities.mixin.MultilineTextFieldAccessor;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorSubmitPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Custom creation and rich-text editing GUI for persistent SSU holograms. */
public final class HologramEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 448;
    private static final int PANEL_HEIGHT = 360;
    private static final int PANEL = 0xF0161D25;
    private static final int BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int ERROR = 0xFFFF8585;
    private static final int DEFAULT_BACKGROUND_ALPHA = 0xA0;


    private final HologramEditorOpenPayload initial;
    private final Screen parent;
    private HologramType type;
    private HologramScoreboardMode scoreboardMode;
    private boolean seeThrough;
    private boolean deleteArmed;
    private boolean rewrappingText;
    /** Last visible non-empty selection, retained while a toolbar/palette control has focus. */
    private int rememberedSelectionStart = -1;
    private int rememberedSelectionEnd = -1;
    private PaletteTarget paletteTarget = PaletteTarget.NONE;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;

    private EditBox id;
    private EditBox coordinateX;
    private EditBox coordinateY;
    private EditBox coordinateZ;
    private MultiLineEditBox text;
    private HologramRichTextDocument richDocument;
    private EditBox source;
    private int backgroundArgb;
    private EditBox scale;
    private EditBox viewDistance;
    private EditBox objective;
    private EditBox imageWidth;
    private EditBox imageHeight;
    private EditBox maxLines;
    private EditBox interval;
    private Button typeButton;
    private Button modeButton;
    private Button seeThroughButton;
    private Button deleteButton;

    public HologramEditorScreen(HologramEditorOpenPayload initial, Screen parent) {
        super(Component.literal(initial.editing() ? "Edit Hologram" : "Create Hologram"));
        this.initial = initial;
        this.parent = parent;
        this.type = initial.hologramType();
        this.scoreboardMode = initial.scoreboardMode();
        this.seeThrough = initial.seeThrough();
        this.backgroundArgb = initial.backgroundColor();
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();
        int right = x + 304;

        id = field(x + 14, y + 30, 158, "Unique ID", 64, initial.id());
        typeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleType())
                .bounds(x + 178, y + 30, 108, 20).build());

        coordinateX = field(x + 14, y + 62, 82, "X", 16, formatDouble(initial.x()));
        coordinateY = field(x + 102, y + 62, 82, "Y", 16, formatDouble(initial.y()));
        coordinateZ = field(x + 190, y + 62, 96, "Z", 16, formatDouble(initial.z()));

        text = MultiLineEditBox.builder().setX(x + 14).setY(y + 100)
                .setPlaceholder(Component.literal("Visible text / scoreboard title"))
                .setShowBackground(true).setShowDecorations(true)
                .build(font, 272, 96, Component.literal("Text"));
        text.setCharacterLimit(HologramRichText.MAX_VISIBLE_CHARACTERS + HologramRichText.MAX_LINES);
        String migratedText = HologramRichText.migrateWholeTextStyles(
                initial.text(), initial.bold(), initial.italic(), initial.underlined(), initial.strikethrough());
        richDocument = new HologramRichTextDocument(HologramRichText.normalize(migratedText));
        text.setValue(richDocument.plainText());
        text.setLineLimit(HologramRichText.MAX_LINES);
        text.setValueListener(this::onTextChanged);
        addRenderableWidget(text);

        int toolbarY = y + 202;
        addRenderableWidget(Button.builder(Component.literal("B"), ignored -> applySelectionFormat('l')).bounds(x + 14, toolbarY, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("I"), ignored -> applySelectionFormat('o')).bounds(x + 38, toolbarY, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("U"), ignored -> applySelectionFormat('n')).bounds(x + 62, toolbarY, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("S"), ignored -> applySelectionFormat('m')).bounds(x + 86, toolbarY, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Clear"), ignored -> clearSelectionFormatting()).bounds(x + 110, toolbarY, 48, 18).build());
        for (int index = 0; index < 16; index++) {
            int colorIndex = index;
            addRenderableWidget(RichTextPalette.button(x + 164 + index * 8, toolbarY + 4, 7, index,
                    ignored -> applySelectionColor(colorIndex)));
        }

        source = field(x + 14, y + 230, 272, "Website URL / image source", 2048, initial.urlOrImageSource());
        objective = field(x + 14, y + 262, 272, "Objective or ssu:stat-id", 64, initial.objective());

        addRenderableWidget(Button.builder(Component.literal("Background"), ignored -> openPalette(PaletteTarget.BACKGROUND))
                .bounds(right, y + 100, 126, 20).build());
        scale = field(right, y + 140, 54, "Scale", 12, Float.toString(initial.scale()));
        viewDistance = field(right + 62, y + 140, 64, "Range", 12, formatDouble(initial.viewDistance()));
        imageWidth = field(right, y + 174, 54, "Width", 12, Float.toString(initial.imageWidth()));
        imageHeight = field(right + 62, y + 174, 64, "Height", 12, Float.toString(initial.imageHeight()));
        maxLines = field(right, y + 208, 54, "Rows", 8, Integer.toString(initial.maxLines()));
        interval = field(right + 62, y + 208, 64, "Seconds", 12, formatSeconds(initial.updateIntervalTicks()));
        modeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            scoreboardMode = scoreboardMode == HologramScoreboardMode.TOP ? HologramScoreboardMode.SELF : HologramScoreboardMode.TOP;
            updateLabels();
        }).bounds(right, y + 244, 126, 20).build());
        seeThroughButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            seeThrough = !seeThrough;
            updateLabels();
        }).bounds(right, y + 270, 126, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose()).bounds(x + 14, y + 326, 70, 20).build());
        if (initial.editing()) {
            deleteButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> requestDelete()).bounds(x + 90, y + 326, 104, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal(initial.editing() ? "Save changes" : "Create hologram"), ignored -> submit(false))
                .bounds(x + PANEL_WIDTH - 124, y + 326, 110, 20).build());

        RichTextEditBoxRenderer.register(text, () -> richDocument, this::currentEditorTextColor,
                RichTextPalette::argb, Component.literal("Visible text / scoreboard title"));
        updateLabels();
        setInitialFocus(id);
    }

    private EditBox field(int x, int y, int width, String hint, int maximum, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(maximum);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private void cycleType() {
        type = switch (type) {
            case TEXT -> HologramType.LINK;
            case LINK -> HologramType.IMAGE;
            case IMAGE -> HologramType.SCOREBOARD;
            case SCOREBOARD -> HologramType.TEXT;
        };
        updateLabels();
    }

    private void openPalette(PaletteTarget target) {
        if (target == PaletteTarget.SELECTION_TEXT && selectedRange() == null) return;
        paletteTarget = paletteTarget == target ? PaletteTarget.NONE : target;
    }

    private void updateLabels() {
        if (typeButton == null) return;
        typeButton.setMessage(Component.literal("Type: " + type.name()));
        modeButton.setMessage(Component.literal("Score mode: " + scoreboardMode.name()));
        seeThroughButton.setMessage(Component.literal("Always visible: " + onOff(seeThrough)));
        if (deleteButton != null) {
            deleteButton.setMessage(Component.literal(deleteArmed ? "Confirm delete" : "Delete hologram"));
        }
        source.setEditable(type == HologramType.LINK || type == HologramType.IMAGE);
        objective.setEditable(type == HologramType.SCOREBOARD);
        modeButton.active = type == HologramType.SCOREBOARD;
        imageWidth.setEditable(type == HologramType.IMAGE);
        imageHeight.setEditable(type == HologramType.IMAGE);
        maxLines.setEditable(type == HologramType.SCOREBOARD);
        interval.setEditable(type == HologramType.SCOREBOARD);
    }

    private void onTextChanged(String value) {
        if (rewrappingText || text == null || richDocument == null) return;
        rememberedSelectionStart = -1;
        rememberedSelectionEnd = -1;
        MultilineTextField textField = textField();
        MultilineTextFieldAccessor cursorAccess = cursorAccess(textField);
        richDocument.updatePlainText(value);
        HologramRichText.WrappedText wrapped = HologramRichText.wrapPlainEditorText(
                value, cursorAccess.ssu$getCursor(), cursorAccess.ssu$getSelectCursor());
        if (wrapped.text().equals(value)) return;
        rewrappingText = true;
        richDocument.updatePlainText(wrapped.text());
        text.setValue(wrapped.text());
        cursorAccess.ssu$setCursor(wrapped.cursor());
        cursorAccess.ssu$setSelectCursor(wrapped.selectionCursor());
        rewrappingText = false;
    }

    private void applySelectionFormat(char formatCode) {
        Format format = switch (formatCode) {
            case 'l' -> Format.BOLD;
            case 'o' -> Format.ITALIC;
            case 'n' -> Format.UNDERLINED;
            case 'm' -> Format.STRIKETHROUGH;
            default -> null;
        };
        if (format == null) return;
        int[] range = selectedRange();
        if (range == null) return;
        richDocument.toggle(range[0], range[1], format);
        formattingAppliedNotice(range);
    }

    private void applySelectionColor(int paletteIndex) {
        int[] range = selectedRange();
        if (range == null) return;
        richDocument.setColor(range[0], range[1], paletteIndex);
        formattingAppliedNotice(range);
    }

    private void clearSelectionFormatting() {
        int[] range = selectedRange();
        if (range == null) return;
        richDocument.clear(range[0], range[1]);
        formattingAppliedNotice(range);
    }

    private int[] selectedRange() {
        rememberCurrentSelection();
        int length = richDocument == null ? 0 : richDocument.plainText().length();
        if (rememberedSelectionStart < 0 || rememberedSelectionEnd <= rememberedSelectionStart
                || rememberedSelectionEnd > length) {
            selectionRequiredNotice();
            return null;
        }
        return new int[] {rememberedSelectionStart, rememberedSelectionEnd};
    }

    private void rememberCurrentSelection() {
        if (text == null || richDocument == null) return;
        MultilineTextFieldAccessor access = cursorAccess(textField());
        int cursor = Math.max(0, Math.min(richDocument.plainText().length(), access.ssu$getCursor()));
        int anchor = Math.max(0, Math.min(richDocument.plainText().length(), access.ssu$getSelectCursor()));
        if (cursor != anchor) {
            rememberedSelectionStart = Math.min(cursor, anchor);
            rememberedSelectionEnd = Math.max(cursor, anchor);
        }
    }

    private void restoreSelection(int[] range) {
        if (range == null || text == null) return;
        // Focus first, then restore the endpoints: a widget focus transition is
        // allowed to update its cursor state, but must not erase our retained range.
        setFocused(text);
        MultilineTextFieldAccessor access = cursorAccess(textField());
        access.ssu$setSelectCursor(range[0]);
        access.ssu$setCursor(range[1]);
        rememberedSelectionStart = range[0];
        rememberedSelectionEnd = range[1];
    }

    private void formattingAppliedNotice(int[] range) {
        restoreSelection(range);
        notice = "Formatting applied to the selected text.";
        noticeError = false;
    }

    private MultilineTextField textField() {
        return ((MultiLineEditBoxAccessor) (Object) text).ssu$getTextField();
    }

    private static MultilineTextFieldAccessor cursorAccess(MultilineTextField field) {
        return (MultilineTextFieldAccessor) (Object) field;
    }

    private void selectionRequiredNotice() {
        notice = "Select part of the text first, then choose a style or color.";
        noticeError = true;
        setFocused(text);
    }

    private void requestDelete() {
        if (!deleteArmed) {
            deleteArmed = true;
            notice = "Click Confirm delete to permanently remove this hologram.";
            noticeError = true;
            updateLabels();
            return;
        }
        submit(true);
    }

    private void submit(boolean deleteRequested) {
        paletteTarget = PaletteTarget.NONE;
        if (deleteRequested) {
            long request = nextRequestId++;
            ClientPacketDistributor.sendToServer(new HologramEditorSubmitPayload(
                    initial.originalId(), true, initial.id(), initial.hologramType(),
                    initial.x(), initial.y(), initial.z(), initial.text(), initial.color(),
                    initial.backgroundColor(), initial.scale(), false, false, false, false,
                    false, initial.seeThrough(), initial.viewDistance(), initial.urlOrImageSource(),
                    initial.imageWidth(), initial.imageHeight(), initial.objective(), initial.scoreboardMode(),
                    initial.maxLines(), initial.updateIntervalTicks(), request
            ));
            notice = "Deleting…";
            noticeError = false;
            return;
        }
        try {
            int parsedColor = initial.color();
            int parsedBackground = backgroundArgb;
            double parsedX = parseDouble(coordinateX.getValue(), -30_000_000.0D, 30_000_000.0D, "X coordinate");
            double parsedY = parseDouble(coordinateY.getValue(), -4_096.0D, 4_096.0D, "Y coordinate");
            double parsedZ = parseDouble(coordinateZ.getValue(), -30_000_000.0D, 30_000_000.0D, "Z coordinate");
            float parsedScale = parseFloat(scale.getValue(), 1.0F, 8.0F, "scale");
            double parsedRange = parseDouble(viewDistance.getValue(), 4.0D, 512.0D, "view distance");
            float parsedWidth = parseFloat(imageWidth.getValue(), 0.1F, 32.0F, "image width");
            float parsedHeight = parseFloat(imageHeight.getValue(), 0.1F, 32.0F, "image height");
            int parsedLines = parseInt(maxLines.getValue(), 1, 64, "maximum score rows");
            double parsedRefreshSeconds = parseDouble(interval.getValue(), 0.5D, 3_600.0D,
                    "scoreboard refresh interval in seconds");
            int parsedInterval = Math.max(10, Math.min(72_000,
                    (int) Math.round(parsedRefreshSeconds * 20.0D)));
            long request = nextRequestId++;
            ClientPacketDistributor.sendToServer(new HologramEditorSubmitPayload(
                    initial.originalId(), false, id.getValue(), type, parsedX, parsedY, parsedZ,
                    richDocument.encode(), parsedColor, parsedBackground, parsedScale,
                    false, false, false, false, false, seeThrough, parsedRange, source.getValue(),
                    parsedWidth, parsedHeight, objective.getValue(), scoreboardMode, parsedLines, parsedInterval, request
            ));
            notice = "Saving…";
            noticeError = false;
        } catch (IllegalArgumentException exception) {
            notice = exception.getMessage();
            noticeError = true;
        }
    }

    public void acceptResult(HologramEditorResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        if (payload.successful()) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(payload.message()));
            }
            if (parent instanceof SsuDashboardScreen dashboard) dashboard.refreshCurrentPage();
            onClose();
            return;
        }
        notice = payload.message();
        noticeError = true;
        deleteArmed = false;
        updateLabels();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (paletteTarget != PaletteTarget.NONE) {
            if (event.buttonInfo().button() == 0) handlePaletteClick(event.x(), event.y());
            else paletteTarget = PaletteTarget.NONE;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void handlePaletteClick(double mouseX, double mouseY) {
        int x = paletteX();
        int y = paletteY();
        int cell = 18;
        int gap = 3;
        for (int index = 0; index < 16; index++) {
            int column = index % 8;
            int row = index / 8;
            int left = x + column * (cell + gap);
            int top = y + row * (cell + gap);
            if (SsuGuiGeometry.inside(mouseX, mouseY, left, top, cell, cell)) {
                applyPreset(index, RichTextPalette.rgb(index));
                paletteTarget = PaletteTarget.NONE;
                return;
            }
        }
        if (paletteTarget == PaletteTarget.BACKGROUND
                && SsuGuiGeometry.inside(mouseX, mouseY, x, y + 46, 165, 18)) {
            backgroundArgb = 0;
        }
        paletteTarget = PaletteTarget.NONE;
    }

    private void applyPreset(int paletteIndex, int rgb) {
        if (paletteTarget == PaletteTarget.SELECTION_TEXT) {
            applySelectionColor(paletteIndex);
            return;
        }
        int alpha = currentBackgroundAlpha();
        backgroundArgb = (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private int currentBackgroundAlpha() {
        int alpha = backgroundArgb >>> 24 & 0xFF;
        return alpha > 0 ? alpha : DEFAULT_BACKGROUND_ALPHA;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        rememberCurrentSelection();
        int x = panelX();
        int y = panelY();
        int right = x + 304;
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL);
        g.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        g.text(font, initial.editing() ? "Edit Floating Hologram" : "Create Floating Hologram", x + 14, y + 11, TEXT, true);
        g.text(font, "Dimension: " + shortDimension(initial.dimension()), right, y + 12, MUTED, false);
        g.text(font, "Coordinates", x + 14, y + 51, MUTED, false);
        g.text(font, "Text / title", x + 14, y + 89, MUTED, false);
        g.text(font, "Source", x + 14, y + 220, MUTED, false);
        g.text(font, "Scoreboard objective", x + 14, y + 252, MUTED, false);
        g.text(font, "{{stat:id}} value • {{rank:id}} rank", x + 14, y + 287, MUTED, false);
        g.text(font, "Background", right, y + 89, MUTED, false);
        g.text(font, "Scale / range", right, y + 130, MUTED, false);
        g.text(font, "Image W/H", right, y + 164, MUTED, false);
        int scoreboardLabelColor = type == HologramType.SCOREBOARD ? MUTED : 0xFF68737C;
        g.text(font, "Rows / refresh", right, y + 198, scoreboardLabelColor, false);
        if (!notice.isBlank()) g.text(font, trim(notice, 66), x + 14, y + 311, noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (paletteTarget != PaletteTarget.NONE) drawPalette(g, mouseX, mouseY);
    }

    private void drawPalette(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = paletteX();
        int y = paletteY();
        g.fill(x - 6, y - 22, x + 171, y + 70, 0xFC10161D);
        g.outline(x - 6, y - 22, 177, 92, BORDER);
        g.text(font, "Background color", x, y - 16, TEXT, true);
        int cell = 18, gap = 3;
        for (int index = 0; index < 16; index++) {
            int left = x + (index % 8) * (cell + gap);
            int top = y + (index / 8) * (cell + gap);
            g.fill(left, top, left + cell, top + cell, RichTextPalette.argb(index));
            g.outline(left, top, cell, cell, 0xFFB6C0C8);
            if (SsuGuiGeometry.inside(mouseX, mouseY, left, top, cell, cell)) {
                int paletteIndex = index;
                g.setComponentTooltipForNextFrame(font,
                        java.util.List.of(Component.literal(RichTextPalette.name(paletteIndex)).withStyle(style -> style.withColor(RichTextPalette.rgb(paletteIndex)))),
                        mouseX, mouseY);
            }
        }
        g.fill(x, y + 46, x + 165, y + 64, 0xFF202A33);
        g.outline(x, y + 46, 165, 18, 0xFFB6C0C8);
        g.text(font, "Transparent", x + 52, y + 51, TEXT, false);
    }

    private int currentEditorTextColor() {
        // Kept only as a backwards-compatible fallback for older holograms that
        // predate per-selection colors. New color choices are stored inline.
        return initial.color();
    }

    @Override
    public void removed() {
        RichTextEditBoxRenderer.unregister(text);
        super.removed();
    }

    @Override
    public void onClose() {
        if (paletteTarget != PaletteTarget.NONE) {
            paletteTarget = PaletteTarget.NONE;
            return;
        }
        if (parent != null && minecraft != null) minecraft.setScreenAndShow(parent);
        else super.onClose();
    }

    private int panelX() { return (width - PANEL_WIDTH) / 2; }
    private int panelY() { return (height - PANEL_HEIGHT) / 2; }
    private int paletteX() { return panelX() + 268; }
    private int paletteY() { return panelY() + 122; }
    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
    private static String shortDimension(String value) {
        int i = value.indexOf(':');
        return i < 0 ? value : value.substring(i + 1);
    }
    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
    private static String formatSeconds(int ticks) {
        double seconds = Math.max(10, ticks) / 20.0D;
        return formatDouble(seconds);
    }

    private static float parseFloat(String raw, float min, float max, String label) {
        try {
            float value = Float.parseFloat(raw);
            if (!Float.isFinite(value) || value < min || value > max) throw new Exception();
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid " + label + ".");
        }
    }

    private static double parseDouble(String raw, double min, double max, String label) {
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value) || value < min || value > max) throw new Exception();
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid " + label + ".");
        }
    }

    private static int parseInt(String raw, int min, int max, String label) {
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) throw new Exception();
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid " + label + ".");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum PaletteTarget {
        NONE,
        SELECTION_TEXT,
        BACKGROUND
    }

}
