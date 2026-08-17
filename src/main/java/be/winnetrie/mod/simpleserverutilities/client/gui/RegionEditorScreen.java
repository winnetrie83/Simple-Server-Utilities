package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.network.RegionEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionEditorSubmitPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Minimal region-creation GUI opened from the region selection tool.
 * Region configuration deliberately remains in Admin Center -> Regions.
 */
public final class RegionEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 470;
    private static final int PANEL_HEIGHT = 210;
    private static final int PANEL = 0xF0161D25;
    private static final int BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int ERROR = 0xFFFF8585;

    private final RegionEditorOpenPayload selection;
    private final Screen parent;
    private EditBox name;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;

    public RegionEditorScreen(RegionEditorOpenPayload selection) {
        this(selection, null);
    }

    public RegionEditorScreen(RegionEditorOpenPayload selection, Screen parent) {
        super(Component.literal("Create Region"));
        this.selection = selection;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();
        name = new EditBox(font, x + 20, y + 78, PANEL_WIDTH - 40, 22, Component.literal("Unique region name"));
        name.setHint(Component.literal("Unique region name"));
        name.setMaxLength(64);
        addRenderableWidget(name);
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(x + 20, y + PANEL_HEIGHT - 34, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Create region"), ignored -> submit())
                .bounds(x + PANEL_WIDTH - 142, y + PANEL_HEIGHT - 34, 122, 20).build());
        setInitialFocus(name);
    }

    private void submit() {
        String rawName = name.getValue().trim();
        if (!rawName.matches("[A-Za-z0-9._-]{1,64}")) {
            notice = "Use 1-64 letters, numbers, dots, underscores or dashes.";
            noticeError = true;
            return;
        }
        long requestId = nextRequestId++;
        ClientPacketDistributor.sendToServer(new RegionEditorSubmitPayload(
                rawName,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                "0",
                -1,
                true,
                true,
                requestId
        ));
        notice = "Creating region…";
        noticeError = false;
    }

    public void acceptResult(RegionEditorResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        if (payload.successful()) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(
                        payload.message() + " Configure it under Admin Center > Regions > Settings."));
            }
            if (minecraft != null) minecraft.setScreenAndShow(null);
            return;
        }
        notice = payload.message();
        noticeError = true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL);
        g.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        g.text(font, "Create Server Region", x + 20, y + 15, TEXT, true);
        BlockPos p1 = BlockPos.of(selection.point1());
        BlockPos p2 = BlockPos.of(selection.point2());
        g.text(font, "Selection: " + compact(p1) + " → " + compact(p2), x + 20, y + 34, MUTED, false);
        g.text(font, "Dimension: " + shortDimension(selection.dimension()), x + 20, y + 48, MUTED, false);
        g.text(font, "Region name", x + 20, y + 66, MUTED, false);
        g.text(font, "Protection, priority, rent and messages are configured afterwards under", x + 20, y + 112, MUTED, false);
        g.text(font, "Admin Center > Regions > Settings.", x + 20, y + 126, GOOD, false);
        if (!notice.isBlank()) {
            g.text(font, trim(notice, 66), x + 20, y + 148, noticeError ? ERROR : GOOD, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) minecraft.setScreenAndShow(parent);
        else super.onClose();
    }

    private int panelX() { return (width - PANEL_WIDTH) / 2; }
    private int panelY() { return (height - PANEL_HEIGHT) / 2; }
    private static String compact(BlockPos pos) { return pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
    private static String shortDimension(String value) { int i = value.indexOf(':'); return i < 0 ? value : value.substring(i + 1); }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
