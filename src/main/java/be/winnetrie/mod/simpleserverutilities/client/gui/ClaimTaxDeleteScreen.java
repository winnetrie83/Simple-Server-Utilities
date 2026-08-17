package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimTaxDeleteActionPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Explicit two-step settlement choice shown before a taxed player claim is deleted. */
public final class ClaimTaxDeleteScreen extends Screen {
    private static final int PANEL = 0xF0121720;
    private static final int FRAME = 0xFF64778D;
    private static final int ACCENT = 0xFFFFD66B;
    private static final int MUTED = 0xFFAFBCCB;
    private static final int DANGER = 0xFFFF6B6B;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ClaimMapScreen parent;
    private final ClaimMapDataPayload payload;
    private ClaimTaxDeleteActionPayload.Mode pendingMode;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public ClaimTaxDeleteScreen(ClaimMapScreen parent, ClaimMapDataPayload payload) {
        super(Component.literal("Settle claim tax before deletion"));
        this.parent = parent;
        this.payload = payload;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(500, width - 30);
        panelHeight = 250;
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int buttonY = top + panelHeight - 68;
        int gap = 8;
        int buttonWidth = (panelWidth - 32 - gap) / 2;

        String payLabel = pendingMode == ClaimTaxDeleteActionPayload.Mode.PAY_AND_DELETE
                ? "Confirm payment & delete" : "Pay tax & delete";
        Button pay = Button.builder(Component.literal(payLabel), ignored -> choose(ClaimTaxDeleteActionPayload.Mode.PAY_AND_DELETE))
                .bounds(left + 12, buttonY, buttonWidth, 20).build();
        pay.setTooltip(Tooltip.create(Component.literal("Debit the current full-cycle tax, then delete this claim and its homes.")));
        addRenderableWidget(pay);

        String forfeitLabel = pendingMode == ClaimTaxDeleteActionPayload.Mode.FORFEIT_AND_DELETE
                ? "Confirm permanent forfeiture" : "Forfeit capacity & delete";
        Button forfeit = Button.builder(Component.literal(forfeitLabel), ignored -> choose(ClaimTaxDeleteActionPayload.Mode.FORFEIT_AND_DELETE))
                .bounds(left + 20 + buttonWidth, buttonY, buttonWidth, 20).build();
        forfeit.setTooltip(Tooltip.create(Component.literal("Delete the claim without payment and permanently confiscate its taxable peak.")));
        addRenderableWidget(forfeit);

        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> minecraft.setScreenAndShow(parent))
                .bounds(left + panelWidth / 2 - 70, buttonY + 30, 140, 20).build());
    }

    private void choose(ClaimTaxDeleteActionPayload.Mode mode) {
        if (pendingMode != mode) {
            pendingMode = mode;
            rebuildWidgets();
            return;
        }
        ClientPacketDistributor.sendToServer(new ClaimTaxDeleteActionPayload(
                payload.selectedClaimGroup(), mode, payload.centerChunkX(), payload.centerChunkZ(), payload.radius()));
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        SsuGuiScale.fullscreenDimWhenScaled(graphics, this, 0xA5000000);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        graphics.outline(left, top, panelWidth, panelHeight, FRAME);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int x = left + 16;
        int y = top + 15;
        graphics.centeredText(font, "DELETE TAXED CLAIM", left + panelWidth / 2, y, ACCENT); y += 30;
        graphics.text(font, "Claim: " + payload.selectedClaimGroup(), x, y, 0xFFFFFFFF); y += 18;
        graphics.text(font, "Current chunks: " + payload.selectedClaimChunks(), x, y, MUTED); y += 15;
        graphics.text(font, "Taxable cycle peak: " + payload.selectedClaimTaxPeakChunks(), x, y, 0xFFFFFFFF); y += 15;
        graphics.text(font, "Current full-cycle tax: " + payload.selectedClaimTaxEstimate(), x, y, 0xFFFFFFFF); y += 15;
        if (payload.selectedClaimTaxDueAt() > 0L) {
            graphics.text(font, "Scheduled payment: " + DATE.format(Instant.ofEpochMilli(payload.selectedClaimTaxDueAt())), x, y, MUTED); y += 18;
        }
        graphics.text(font, "Pay: the tax is debited first; the claim and linked homes are then deleted.", x, y, MUTED); y += 15;
        graphics.text(font, "Forfeit: no money is charged, but " + payload.selectedClaimTaxPeakChunks()
                + " claim chunk(s) are permanently removed", x, y, DANGER); y += 13;
        graphics.text(font, "from your capacity. Existing confiscation: " + payload.confiscatedChunks() + " chunk(s).", x, y, DANGER); y += 19;
        graphics.text(font, pendingMode == null
                ? "Choose an option. You must click the same option a second time to confirm."
                : "Click the selected option again to confirm this irreversible action.", x, y, ACCENT);
    }

    @Override
    public void onClose() { minecraft.setScreenAndShow(parent); }
}
