package be.winnetrie.mod.simpleserverutilities.client.gui;

import be.winnetrie.mod.simpleserverutilities.minigame.MinigameGameType;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSelectionCreatePayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSelectionCreateResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupToolOpenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** New-game wizard reached only after the Setup Tool selected two arena corners. */
public final class MinigameSetupCreateScreen extends Screen {
    private static final int W = 430, H = 238;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7,
            MUTED = 0xFFAAB5BE, GOOD = 0xFF83E39A, ERROR = 0xFFFF8585;
    private final MinigameSetupToolOpenPayload selection;
    private final Screen parent;
    private EditBox id, name, minimum, maximum;
    private MinigameGameType type = MinigameGameType.SPLEEF;
    private Button typeButton;
    private long nextRequestId = 1L;
    private boolean awaiting;
    private String notice = "";
    private boolean error;

    public MinigameSetupCreateScreen(MinigameSetupToolOpenPayload selection, Screen parent) {
        super(Component.literal("Create Minigame"));
        this.selection = selection; this.parent = parent;
    }

    @Override protected void init() {
        int x = left(), y = top();
        id = field(x + 20, y + 58, 185, 64, "Internal ID", id == null ? "new_spleef" : id.getValue());
        name = field(x + 215, y + 58, 195, 128, "Display name", name == null ? "New Spleef" : name.getValue());
        minimum = field(x + 20, y + 106, 90, 3, "Min players", minimum == null ? "2" : minimum.getValue());
        maximum = field(x + 120, y + 106, 90, 3, "Max players", maximum == null ? "8" : maximum.getValue());
        typeButton = addRenderableWidget(Button.builder(Component.empty(), ignored -> cycleType())
                .bounds(x + 220, y + 106, 190, 20).build());
        updateType();
        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(x + 20, y + H - 32, 70, 20).build());
        Button create = addRenderableWidget(Button.builder(Component.literal("Create managed arena"), ignored -> submit())
                .bounds(x + W - 150, y + H - 32, 130, 20).build());
        create.active = selection.hasSelection() && !awaiting;
    }

    private EditBox field(int x, int y, int w, int max, String hint, String value) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setHint(Component.literal(hint)); box.setMaxLength(max); box.setValue(value); addRenderableWidget(box); return box;
    }

    private void cycleType() {
        type = switch (type) {
            case SPLEEF -> MinigameGameType.CAPTURE_THE_FLAG;
            case CAPTURE_THE_FLAG -> MinigameGameType.DOMINATION;
            case DOMINATION -> MinigameGameType.KING_OF_THE_HILL;
            case KING_OF_THE_HILL -> MinigameGameType.BLOCK_PARTY;
            default -> MinigameGameType.SPLEEF;
        };
        String suffix = switch (type) {
            case CAPTURE_THE_FLAG -> "capture_the_flag";
            case DOMINATION -> "domination";
            case KING_OF_THE_HILL -> "king_of_the_hill";
            case BLOCK_PARTY -> "block_party";
            default -> "spleef";
        };
        id.setValue("new_" + suffix);
        name.setValue("New " + type.label());
        maximum.setValue(type == MinigameGameType.DOMINATION ? "20" : type == MinigameGameType.BLOCK_PARTY ? "12" : "8");
        updateType();
    }

    private void updateType() { if (typeButton != null) typeButton.setMessage(Component.literal("Game type: " + type.label())); }

    private void submit() {
        try {
            int min = Integer.parseInt(minimum.getValue().trim()), max = Integer.parseInt(maximum.getValue().trim());
            int limit = type == MinigameGameType.SPLEEF ? 16 : type == MinigameGameType.BLOCK_PARTY ? 32 : 64;
            if (min < 2 || max < min || max > limit) throw new NumberFormatException();
            awaiting = true; notice = "Creating region and verified reset snapshot…"; error = false;
            PacketDistributor.sendToServer(new MinigameSelectionCreatePayload(id.getValue(), name.getValue(),
                    type.id(), min, max, nextRequestId++));
            rebuildWidgets();
        } catch (RuntimeException exception) {
            notice = "Use a valid player range for this game type."; error = true;
        }
    }

    public void accept(MinigameSelectionCreateResultPayload result) {
        if (result == null) return;
        awaiting = false; nextRequestId = Math.max(nextRequestId, result.requestId() + 1L);
        notice = result.message(); error = !result.successful();
        if (result.successful()) {
            if (minecraft != null) minecraft.setScreen(parent);
        } else rebuildWidgets();
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000); g.fill(x, y, x + W, y + H, PANEL); g.renderOutline(x, y, W, H, BORDER);
        g.drawString(font, "Create a managed minigame arena", x + 20, y + 14, TEXT, true);
        g.drawString(font, selection.selectionVolume() + " selected blocks in " + shortDimension(selection.selectionDimension()), x + 20, y + 32, MUTED, false);
        g.drawString(font, "Internal ID", x + 20, y + 47, MUTED, false); g.drawString(font, "Display name", x + 215, y + 47, MUTED, false);
        g.drawString(font, "Minimum", x + 20, y + 95, MUTED, false); g.drawString(font, "Maximum", x + 120, y + 95, MUTED, false);
        g.drawString(font, "The game starts disabled. Use its editor and Setup Tool before enabling it.", x + 20, y + 148, MUTED, false);
        if (!notice.isBlank()) g.drawString(font, trim(notice, 62), x + 20, y + 168, error ? ERROR : GOOD, false);
        super.render(g, mouseX, mouseY, partialTick);
    }
    private int left() { return (width - W) / 2; } private int top() { return (height - H) / 2; }
    private static String shortDimension(String raw) { int i = raw.indexOf(':'); return i >= 0 ? raw.substring(i + 1) : raw; }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
}
