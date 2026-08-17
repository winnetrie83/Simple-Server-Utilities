package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionToolOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** First, deliberately small action menu opened by right-clicking the region tool. */
public final class RegionSelectionToolScreen extends Screen {
    private static final int WIDTH = 430;
    private static final int HEIGHT = 244;
    private static final int PANEL = 0xF0161D25;
    private static final int BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int ERROR = 0xFFFF8585;
    private final RegionSelectionToolOpenPayload selection;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;

    public RegionSelectionToolScreen(RegionSelectionToolOpenPayload selection) {
        super(Component.literal("Region Selection"));
        this.selection = selection;
    }

    @Override
    protected void init() {
        int x = left();
        int y = top();
        int buttonX = x + 24;
        int buttonWidth = WIDTH - 48;
        Button create = addRenderableWidget(Button.builder(Component.literal("Create server region"), ignored -> {
                    ClientPacketDistributor.sendToServer(new RegionSetupRequestPayload("create", "", nextRequestId++));
                    notice = "Opening full region setup…";
                    noticeError = false;
                }).bounds(buttonX, y + 76, buttonWidth, 24).build());
        create.active = selection.canCreateRegion();
        Button edit = addRenderableWidget(Button.builder(Component.literal("Edit selected blocks"), ignored -> {
                    if (minecraft != null) minecraft.setScreenAndShow(new RegionSelectionEditScreen(selection, this));
                }).bounds(buttonX, y + 106, buttonWidth, 24).build());
        edit.active = selection.canEditBlocks() && selection.volume() <= selection.maxEditableVolume();
        addRenderableWidget(Button.builder(Component.literal("Clear selection"), ignored -> {
                    long requestId = nextRequestId++;
                    ClientPacketDistributor.sendToServer(new RegionSelectionActionPayload(
                            "clear_selection", "", List.of(), List.of(), requestId));
                    notice = "Clearing selection…";
                    noticeError = false;
                }).bounds(buttonX, y + 136, buttonWidth, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + WIDTH - 94, y + HEIGHT - 32, 70, 20).build());
    }

    public void acceptResult(RegionSelectionActionResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        notice = payload.message();
        noticeError = !payload.successful();
        if (payload.selectionCleared()) onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left();
        int y = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
        g.outline(x, y, WIDTH, HEIGHT, BORDER);
        g.text(font, "Region Selection", x + 18, y + 14, TEXT, true);
        BlockPos p1 = BlockPos.of(selection.point1());
        BlockPos p2 = BlockPos.of(selection.point2());
        g.text(font, compact(p1) + " → " + compact(p2), x + 18, y + 34, MUTED, false);
        g.text(font, selection.volume() + " block(s) · " + shortDimension(selection.dimension()),
                x + 18, y + 48, MUTED, false);
        g.text(font, "Choose what you want to do with the current region selection.", x + 18, y + 62, MUTED, false);
        g.text(font, "Minigames use the separate Minigame Setup Tool.", x + 18, y + 164, MUTED, false);
        boolean editTooLarge = selection.volume() > selection.maxEditableVolume();
        if (!selection.canCreateRegion() || !selection.canEditBlocks() || editTooLarge) {
            String access;
            if (editTooLarge && selection.canEditBlocks()) {
                access = "Block editing limit: " + selection.maxEditableVolume() + " blocks.";
            } else if (!selection.canCreateRegion() && !selection.canEditBlocks()) {
                access = "Create and block-edit permissions are unavailable.";
            } else if (!selection.canCreateRegion()) {
                access = "Region creation permission is unavailable.";
            } else {
                access = "Selection block-edit permission is unavailable.";
            }
            g.text(font, access, x + 18, y + 176, ERROR, false);
        }
        if (!notice.isBlank()) g.text(font, trim(notice, 58), x + 18, y + 192, noticeError ? ERROR : MUTED, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private int left() { return (width - WIDTH) / 2; }
    private int top() { return (height - HEIGHT) / 2; }
    private static String compact(BlockPos pos) { return pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
    private static String shortDimension(String raw) {
        int separator = raw.indexOf(':');
        return separator >= 0 ? raw.substring(separator + 1) : raw;
    }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…"; }
    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
