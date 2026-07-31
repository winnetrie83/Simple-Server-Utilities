package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;
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
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 450;
    private static final int PANEL = 0xF0161D25;
    private static final int BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int ERROR = 0xFFFF8585;
    private static final int DEFAULT_BACKGROUND_ALPHA = 0xA0;

    private static final List<ColorPreset> MINECRAFT_COLORS = List.of(
            new ColorPreset("Black", 0x000000),
            new ColorPreset("Dark Blue", 0x0000AA),
            new ColorPreset("Dark Green", 0x00AA00),
            new ColorPreset("Dark Aqua", 0x00AAAA),
            new ColorPreset("Dark Red", 0xAA0000),
            new ColorPreset("Dark Purple", 0xAA00AA),
            new ColorPreset("Gold", 0xFFAA00),
            new ColorPreset("Gray", 0xAAAAAA),
            new ColorPreset("Dark Gray", 0x555555),
            new ColorPreset("Blue", 0x5555FF),
            new ColorPreset("Green", 0x55FF55),
            new ColorPreset("Aqua", 0x55FFFF),
            new ColorPreset("Red", 0xFF5555),
            new ColorPreset("Light Purple", 0xFF55FF),
            new ColorPreset("Yellow", 0xFFFF55),
            new ColorPreset("White", 0xFFFFFF)
    );

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
    private EditBox backgroundColor;
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
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();
        int right = x + 372;

        id = field(x + 16, y + 36, 196, "Unique ID", 64, initial.id());
        typeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleType())
                .bounds(x + 220, y + 36, 132, 20).build());

        coordinateX = field(x + 16, y + 72, 102, "X", 24, formatDouble(initial.x()));
        coordinateY = field(x + 126, y + 72, 102, "Y", 24, formatDouble(initial.y()));
        coordinateZ = field(x + 236, y + 72, 116, "Z", 24, formatDouble(initial.z()));

        text = MultiLineEditBox.builder().setX(x + 16).setY(y + 116)
                .setPlaceholder(Component.literal("Visible text / scoreboard title"))
                .setShowBackground(true).setShowDecorations(true)
                .build(font, 336, 126, Component.literal("Text"));
        text.setCharacterLimit(HologramRichText.MAX_VISIBLE_CHARACTERS + HologramRichText.MAX_LINES);
        String migratedText = HologramRichText.migrateWholeTextStyles(
                initial.text(), initial.bold(), initial.italic(), initial.underlined(), initial.strikethrough());
        richDocument = new HologramRichTextDocument(HologramRichText.normalize(migratedText));
        text.setValue(richDocument.plainText());
        text.setLineLimit(HologramRichText.MAX_LINES);
        text.setValueListener(this::onTextChanged);
        addRenderableWidget(text);

        addRenderableWidget(Button.builder(Component.literal("B"), ignored -> applySelectionFormat('l'))
                .bounds(x + 16, y + 248, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("I"), ignored -> applySelectionFormat('o'))
                .bounds(x + 44, y + 248, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("U"), ignored -> applySelectionFormat('n'))
                .bounds(x + 72, y + 248, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("S"), ignored -> applySelectionFormat('m'))
                .bounds(x + 100, y + 248, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Color ▾"),
                        ignored -> openPalette(PaletteTarget.SELECTION_TEXT))
                .bounds(x + 132, y + 248, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear style"), ignored -> clearSelectionFormatting())
                .bounds(x + 220, y + 248, 94, 20).build());

        source = field(x + 16, y + 302, 336, "Website URL or direct PNG/GIF/JPG source", 2048,
                initial.urlOrImageSource());
        objective = field(x + 16, y + 340, 336, "Objective or ssu:stat-id", 64, initial.objective());

        backgroundColor = field(right, y + 116, 78, "AARRGGBB", 8,
                String.format(Locale.ROOT, "%08X", initial.backgroundColor()));
        addRenderableWidget(Button.builder(Component.literal("Presets ▾"),
                        ignored -> openPalette(PaletteTarget.BACKGROUND))
                .bounds(right + 84, y + 116, 88, 20).build());

        scale = field(right, y + 154, 78, "Scale", 12, Float.toString(initial.scale()));
        viewDistance = field(right + 84, y + 154, 88, "Range", 12, formatDouble(initial.viewDistance()));
        imageWidth = field(right, y + 192, 78, "Width", 12, Float.toString(initial.imageWidth()));
        imageHeight = field(right + 84, y + 192, 88, "Height", 12, Float.toString(initial.imageHeight()));
        maxLines = field(right, y + 230, 78, "Rows", 8, Integer.toString(initial.maxLines()));
        interval = field(right + 84, y + 230, 88, "Seconds", 12,
                formatSeconds(initial.updateIntervalTicks()));
        modeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            scoreboardMode = scoreboardMode == HologramScoreboardMode.TOP
                    ? HologramScoreboardMode.SELF : HologramScoreboardMode.TOP;
            updateLabels();
        }).bounds(right, y + 268, 172, 20).build());
        seeThroughButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            seeThrough = !seeThrough;
            updateLabels();
        }).bounds(right, y + 304, 172, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(x + 16, y + 402, 86, 20).build());
        if (initial.editing()) {
            deleteButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> requestDelete())
                    .bounds(x + 110, y + 402, 132, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal(initial.editing() ? "Save changes" : "Create hologram"),
                        ignored -> submit(false))
                .bounds(x + 398, y + 402, 146, 20).build());

        RichTextEditBoxRenderer.register(text, () -> richDocument, this::currentEditorTextColor,
                Component.literal("Visible text / scoreboard title"));
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
            int parsedBackground = parseBackgroundColor(backgroundColor.getValue());
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
        int cellWidth = 99;
        int cellHeight = 24;
        int gap = 4;
        for (int index = 0; index < MINECRAFT_COLORS.size(); index++) {
            int column = index % 4;
            int row = index / 4;
            int left = x + column * (cellWidth + gap);
            int top = y + row * (cellHeight + gap);
            if (inside(mouseX, mouseY, left, top, cellWidth, cellHeight)) {
                applyPreset(index, MINECRAFT_COLORS.get(index).rgb());
                paletteTarget = PaletteTarget.NONE;
                return;
            }
        }
        if (paletteTarget == PaletteTarget.BACKGROUND
                && inside(mouseX, mouseY, x, y + 112, 408, 20)) {
            backgroundColor.setValue("00000000");
        }
        paletteTarget = PaletteTarget.NONE;
    }

    private void applyPreset(int paletteIndex, int rgb) {
        if (paletteTarget == PaletteTarget.SELECTION_TEXT) {
            applySelectionColor(paletteIndex);
            return;
        }
        int alpha = currentBackgroundAlpha();
        backgroundColor.setValue(String.format(Locale.ROOT, "%08X", (alpha << 24) | (rgb & 0xFFFFFF)));
    }

    private int currentBackgroundAlpha() {
        try {
            String hex = cleanHex(backgroundColor.getValue());
            if (hex.length() == 8) {
                int alpha = (int) (Long.parseUnsignedLong(hex, 16) >>> 24);
                if (alpha > 0) return alpha;
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_BACKGROUND_ALPHA;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Capture the selection every frame before a toolbar button receives focus.
        // MultiLineEditBox may temporarily collapse its live selection on focus change.
        rememberCurrentSelection();
        int x = panelX();
        int y = panelY();
        int right = x + 372;
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL);
        g.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        g.text(font, initial.editing() ? "Edit Floating Hologram" : "Create Floating Hologram",
                x + 16, y + 12, TEXT, true);
        g.text(font, "Dimension: " + shortDimension(initial.dimension()), x + 372, y + 14, MUTED, false);
        g.text(font, "Coordinates (editable)", x + 16, y + 61, MUTED, false);
        g.text(font, "X", x + 18, y + 64, MUTED, false);
        g.text(font, "Y", x + 128, y + 64, MUTED, false);
        g.text(font, "Z", x + 238, y + 64, MUTED, false);
        g.text(font, "Text / title — automatic new line after 40 visible characters",
                x + 16, y + 104, MUTED, false);
        g.text(font, "Select text, then apply B / I / U / S or one of the 16 colors",
                x + 16, y + 274, MUTED, false);
        g.text(font, type == HologramType.LINK ? "Website URL"
                        : type == HologramType.IMAGE ? "Direct PNG/GIF/JPG URL or resource ID" : "Source (not used)",
                x + 16, y + 290, MUTED, false);
        g.text(font, "Scoreboard objective or ssu:<stat-id>", x + 16, y + 328, MUTED, false);
        g.text(font, "Text tokens: {{stat:id}} = your value, {{rank:id}} = your rank",
                x + 16, y + 374, MUTED, false);
        g.text(font, "One shared background", right, y + 104, MUTED, false);
        g.text(font, "Scale / range", right, y + 142, MUTED, false);
        g.text(font, "Image W/H", right, y + 180, MUTED, false);
        int scoreboardLabelColor = type == HologramType.SCOREBOARD ? MUTED : 0xFF68737C;
        g.text(font, "Score rows", right, y + 218, scoreboardLabelColor, false);
        g.text(font, "Refresh sec", right + 84, y + 218, scoreboardLabelColor, false);
        if (!notice.isBlank()) {
            g.text(font, trim(notice, 82), x + 16, y + 431, noticeError ? ERROR : GOOD, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (paletteTarget != PaletteTarget.NONE) drawPalette(g);
    }

    private void drawPalette(GuiGraphicsExtractor g) {
        int x = paletteX();
        int y = paletteY();
        int paletteHeight = paletteTarget == PaletteTarget.BACKGROUND ? 140 : 112;
        g.fill(x - 8, y - 24, x + 416, y + paletteHeight, 0xFC10161D);
        g.outline(x - 8, y - 24, 424, paletteHeight + 24, BORDER);
        String title = switch (paletteTarget) {
            case SELECTION_TEXT -> "Apply Minecraft color to selected text";
            case BACKGROUND -> "Minecraft background colors";
            case NONE -> "";
        };
        g.text(font, title, x, y - 17, TEXT, true);

        int cellWidth = 99;
        int cellHeight = 24;
        int gap = 4;
        for (int index = 0; index < MINECRAFT_COLORS.size(); index++) {
            ColorPreset preset = MINECRAFT_COLORS.get(index);
            int column = index % 4;
            int row = index / 4;
            int left = x + column * (cellWidth + gap);
            int top = y + row * (cellHeight + gap);
            int swatch = 0xFF000000 | preset.rgb();
            g.fill(left, top, left + cellWidth, top + cellHeight, swatch);
            g.outline(left, top, cellWidth, cellHeight, 0xFFB6C0C8);
            g.text(font, preset.name(), left + 6, top + 8, contrastColor(preset.rgb()), true);
        }
        if (paletteTarget == PaletteTarget.BACKGROUND) {
            int top = y + 112;
            g.fill(x, top, x + 408, top + 20, 0xFF202A33);
            g.outline(x, top, 408, 20, 0xFFB6C0C8);
            g.text(font, "No background (transparent)", x + 114, top + 6, TEXT, false);
        }
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
    private int paletteX() { return panelX() + 76; }
    private int paletteY() { return panelY() + 150; }
    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
    private static String shortDimension(String value) {
        int i = value.indexOf(':');
        return i < 0 ? value : value.substring(i + 1);
    }
    private static String formatDouble(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
    private static String formatSeconds(int ticks) {
        double seconds = Math.max(10, ticks) / 20.0D;
        return formatDouble(seconds);
    }
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
    private static int contrastColor(int rgb) {
        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return red * 299 + green * 587 + blue * 114 >= 140_000 ? 0xFF101010 : 0xFFFFFFFF;
    }

    private static int parseBackgroundColor(String raw) {
        try {
            String hex = cleanHex(raw);
            if (hex.isBlank() || "none".equalsIgnoreCase(raw.trim())
                    || "transparent".equalsIgnoreCase(raw.trim())) return 0;
            if (hex.length() != 6 && hex.length() != 8) throw new NumberFormatException();
            long value = Long.parseUnsignedLong(hex, 16);
            return hex.length() == 6
                    ? (int) ((long) DEFAULT_BACKGROUND_ALPHA << 24 | value)
                    : (int) value;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Background must be RRGGBB, AARRGGBB, or 00000000 for none.");
        }
    }

    private static String cleanHex(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
        return value;
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

    private record ColorPreset(String name, int rgb) {
    }
}
