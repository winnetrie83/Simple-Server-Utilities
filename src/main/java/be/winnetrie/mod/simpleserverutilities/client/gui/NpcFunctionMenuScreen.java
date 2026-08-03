package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.network.NpcFunctionMenuPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcFunctionUsePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Compact generated menu for an NPC with one or more configured functions. */
public final class NpcFunctionMenuScreen extends Screen {
    private static final int W = 330, ROW = 24;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE;
    private final NpcFunctionMenuPayload data;
    private final Screen parent;

    public NpcFunctionMenuScreen(NpcFunctionMenuPayload data, Screen parent) {
        super(Component.literal(data.npcName()));
        this.data = data;
        this.parent = parent;
    }

    @Override protected void init() {
        int x = (width - W) / 2, y = top();
        for (int i = 0; i < data.entries().size(); i++) {
            NpcFunctionMenuPayload.Entry entry = data.entries().get(i);
            Button button = addRenderableWidget(Button.builder(Component.literal(entry.label()), b -> use(entry))
                    .bounds(x + 16, y + 50 + i * ROW, W - 32, 20).build());
            button.active = entry.available();
            if (!entry.reason().isBlank()) button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(entry.reason())));
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + W - 82, y + heightBox() - 28, 66, 18).build());
    }

    private void use(NpcFunctionMenuPayload.Entry entry) {
        if (!entry.available()) return;
        ClientPacketDistributor.sendToServer(new NpcFunctionUsePayload(data.instanceId(), entry.id()));
        onClose();
    }

    @Override public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = (width - W) / 2, y = top();
        g.fill(0, 0, width, height, 0xA9000000);
        g.fill(x, y, x + W, y + heightBox(), PANEL); g.outline(x, y, W, heightBox(), BORDER);
        g.text(font, data.npcName(), x + 16, y + 14, TEXT, true);
        if (!data.roleLabel().isBlank()) g.text(font, data.roleLabel(), x + 16, y + 29, MUTED, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private int heightBox() { return 86 + Math.max(1, data.entries().size()) * ROW; }
    private int top() { return (height - heightBox()) / 2; }
}
