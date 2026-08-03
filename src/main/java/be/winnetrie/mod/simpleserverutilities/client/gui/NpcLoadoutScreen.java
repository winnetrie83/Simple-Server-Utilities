package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.math.BigDecimal;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.NpcLoadoutResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcLoadoutSavePayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcLoadoutMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Compact real-inventory editor for visual equipment and the NPC's only loot table. */
public final class NpcLoadoutScreen extends AbstractContainerScreen<NpcLoadoutMenu> {
    private static final int WIDTH = 360;
    private static final int HEIGHT = 270;
    private static final int PANEL = 0xF0161D25;
    private static final int BORDER = 0xFF586978;
    private static final int SLOT = 0xFF090D12;
    private static final int SLOT_BORDER = 0xFF485865;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int ERROR = 0xFFFF8585;
    private static final int GOOD = 0xFF83E39A;

    private final EditBox[] chanceFields = new EditBox[9];
    private EditBox rollsField;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;
    private boolean saving;

    public NpcLoadoutScreen(NpcLoadoutMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        titleLabelX = -10_000;
        inventoryLabelX = -10_000;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.mode() == NpcLoadoutMenu.MODE_LOOT) {
            for (int i = 0; i < 9; i++) {
                EditBox field = new EditBox(font, leftPos + 13 + i * 36, topPos + 80, 34, 16,
                        Component.literal("Drop chance"));
                field.setMaxLength(6);
                field.setValue(chanceText(menu.chance(i)));
                chanceFields[i] = addRenderableWidget(field);
            }
            rollsField = new EditBox(font, leftPos + 48, topPos + 106, 42, 16, Component.literal("Rolls"));
            rollsField.setMaxLength(3);
            rollsField.setValue(Integer.toString(menu.rolls()));
            addRenderableWidget(rollsField);
        }
        Button save = Button.builder(Component.literal(saving ? "Saving…" : "Save"), ignored -> save())
                .bounds(leftPos + WIDTH - 92, topPos + HEIGHT - 25, 76, 18).build();
        save.active = !saving;
        addRenderableWidget(save);
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> close())
                .bounds(leftPos + 16, topPos + HEIGHT - 25, 76, 18).build());
    }

    private void save() {
        if (saving) return;
        int[] chances = new int[9];
        int rolls = menu.rolls();
        try {
            if (menu.mode() == NpcLoadoutMenu.MODE_LOOT) {
                rolls = parseInt(rollsField == null ? "1" : rollsField.getValue(), 1, 100, "rolls");
                for (int i = 0; i < 9; i++) chances[i] = parseChance(chanceFields[i].getValue());
            } else {
                for (int i = 0; i < 9; i++) chances[i] = menu.chance(i);
            }
        } catch (IllegalArgumentException exception) {
            notice = exception.getMessage(); noticeError = true; return;
        }
        saving = true;
        ClientPacketDistributor.sendToServer(new NpcLoadoutSavePayload(
                menu.containerId, menu.mode(), rolls, chances, nextRequestId++));
        rebuildWidgets();
    }

    private void close() {
        if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
    }

    public void acceptResult(NpcLoadoutResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        notice = payload.message(); noticeError = !payload.successful(); saving = false;
        if (payload.successful()) {
            if (minecraft != null && minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal(payload.message()));
            return;
        }
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xA9000000);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        g.outline(leftPos, topPos, imageWidth, imageHeight, BORDER);
        g.text(font, menu.mode() == NpcLoadoutMenu.MODE_LOOT ? "NPC Loot Table" : "NPC Visual Equipment",
                leftPos + 14, topPos + 11, TEXT, false);
        if (menu.mode() == NpcLoadoutMenu.MODE_LOOT) {
            g.text(font, "Each filled slot rolls independently", leftPos + 14, topPos + 28, MUTED, false);
            g.text(font, "Chance %", leftPos + 14, topPos + 99, MUTED, false);
            g.text(font, "Rolls", leftPos + 14, topPos + 110, MUTED, false);
            for (int i = 0; i < 9; i++) drawSlot(g, leftPos + 16 + i * 36, topPos + 54);
        } else {
            g.text(font, "Main, offhand, head, chest, legs and feet — visual only", leftPos + 14, topPos + 28, MUTED, false);
            String[] labels = {"Main", "Off", "Head", "Chest", "Legs", "Feet"};
            for (int i = 0; i < 6; i++) {
                int x = leftPos + 42 + i * 46;
                drawSlot(g, x, topPos + 54);
                g.text(font, labels[i], x - 3, topPos + 78, MUTED, false);
            }
        }
        drawInventory(g);
        if (!notice.isBlank()) g.text(font, trim(notice, 54), leftPos + 102, topPos + HEIGHT - 20,
                noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawInventory(GuiGraphicsExtractor g) {
        int x = leftPos + NpcLoadoutMenu.PLAYER_INVENTORY_X;
        int y = topPos + NpcLoadoutMenu.PLAYER_INVENTORY_Y;
        g.text(font, "Player inventory", x, y - 13, MUTED, false);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) drawSlot(g, x + column * 18, y + row * 18);
        }
        for (int column = 0; column < 9; column++) drawSlot(g, x + column * 18,
                topPos + NpcLoadoutMenu.PLAYER_HOTBAR_Y);
    }

    private static void drawSlot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT);
        g.outline(x - 1, y - 1, 18, 18, SLOT_BORDER);
    }

    private static int parseChance(String raw) {
        try {
            BigDecimal value = new BigDecimal(raw.trim().replace(',', '.')).stripTrailingZeros();
            if (value.scale() > 2) throw new NumberFormatException();
            int hundredths = value.movePointRight(2).intValueExact();
            if (hundredths < 1 || hundredths > 10_000) throw new NumberFormatException();
            return hundredths;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Chance must be 0.01 to 100.00%.");
        }
    }

    private static int parseInt(String raw, int minimum, int maximum, String label) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Enter valid " + label + " (" + minimum + "-" + maximum + ").");
        }
    }

    private static String chanceText(int hundredths) {
        return String.format(Locale.ROOT, "%.2f", Math.max(1, Math.min(10_000, hundredths)) / 100.0D);
    }

    private static String trim(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }

    @Override public boolean isPauseScreen() { return false; }
}
