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

/** Initial region creation/settings GUI opened by the SSU region admin tool. */
public final class RegionEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 500;
    private static final int PANEL_HEIGHT = 330;
    private static final int PANEL = 0xF0161D25;
    private static final int BORDER = 0xFF586978;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE;
    private static final int GOOD = 0xFF83E39A;
    private static final int ERROR = 0xFFFF8585;

    private final RegionEditorOpenPayload selection;
    private EditBox name;
    private EditBox priority;
    private EditBox rentPrice;
    private EditBox rentDays;
    private boolean allowBreak;
    private boolean allowPlace;
    private boolean allowInteract;
    private boolean allowPvp;
    private boolean allowExplosions;
    private boolean allowPistons;
    private boolean allowWater;
    private boolean allowLava;
    private boolean allowRedstone = true;
    private boolean allowHoppers;
    private boolean allowFireSpread;
    private boolean rentable;
    private boolean resetOnExpire = true;
    private boolean resetOnUnrent = true;
    private long nextRequestId = 1L;
    private String notice = "";
    private boolean noticeError;

    private Button breakButton;
    private Button placeButton;
    private Button interactButton;
    private Button pvpButton;
    private Button explosionsButton;
    private Button pistonsButton;
    private Button waterButton;
    private Button lavaButton;
    private Button redstoneButton;
    private Button hoppersButton;
    private Button fireButton;
    private Button rentableButton;
    private Button resetExpireButton;
    private Button resetUnrentButton;

    public RegionEditorScreen(RegionEditorOpenPayload selection) {
        super(Component.literal("Create Region"));
        this.selection = selection;
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();
        name = field(x + 16, y + 42, 210, "Unique region name", 64, "");
        priority = field(x + 238, y + 42, 90, "Priority", 12, "0");

        int startX = x + 16;
        int startY = y + 94;
        int w = 112;
        int gap = 5;
        breakButton = toggle(startX, startY, w, () -> allowBreak = !allowBreak);
        placeButton = toggle(startX + (w + gap), startY, w, () -> allowPlace = !allowPlace);
        interactButton = toggle(startX + 2 * (w + gap), startY, w, () -> allowInteract = !allowInteract);
        pvpButton = toggle(startX + 3 * (w + gap), startY, w, () -> allowPvp = !allowPvp);

        explosionsButton = toggle(startX, startY + 24, w, () -> allowExplosions = !allowExplosions);
        pistonsButton = toggle(startX + (w + gap), startY + 24, w, () -> allowPistons = !allowPistons);
        waterButton = toggle(startX + 2 * (w + gap), startY + 24, w, () -> allowWater = !allowWater);
        lavaButton = toggle(startX + 3 * (w + gap), startY + 24, w, () -> allowLava = !allowLava);

        redstoneButton = toggle(startX, startY + 48, w, () -> allowRedstone = !allowRedstone);
        hoppersButton = toggle(startX + (w + gap), startY + 48, w, () -> allowHoppers = !allowHoppers);
        fireButton = toggle(startX + 2 * (w + gap), startY + 48, w, () -> allowFireSpread = !allowFireSpread);
        rentableButton = toggle(startX + 3 * (w + gap), startY + 48, w, () -> rentable = !rentable);

        resetExpireButton = toggle(startX, startY + 82, 150, () -> resetOnExpire = !resetOnExpire);
        resetUnrentButton = toggle(startX + 156, startY + 82, 150, () -> resetOnUnrent = !resetOnUnrent);
        rentPrice = field(startX, startY + 118, 150, "Rent price", 64, "0");
        rentDays = field(startX + 156, startY + 118, 150, "Days (-1 = permanent)", 8, "-1");

        addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> onClose())
                .bounds(x + 16, y + 292, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Create region"), ignored -> submit())
                .bounds(x + PANEL_WIDTH - 136, y + 292, 120, 20).build());
        updateLabels();
        setInitialFocus(name);
    }

    private EditBox field(int x, int y, int width, String hint, int maximum, String value) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(maximum);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private Button toggle(int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.empty(), ignored -> {
            action.run();
            updateLabels();
        }).bounds(x, y, width, 20).build());
    }

    private void updateLabels() {
        if (breakButton == null) return;
        breakButton.setMessage(label("Break", allowBreak));
        placeButton.setMessage(label("Place", allowPlace));
        interactButton.setMessage(label("Interact", allowInteract));
        pvpButton.setMessage(label("PvP", allowPvp));
        explosionsButton.setMessage(label("Explosions", allowExplosions));
        pistonsButton.setMessage(label("Pistons", allowPistons));
        waterButton.setMessage(label("Water flow", allowWater));
        lavaButton.setMessage(label("Lava flow", allowLava));
        redstoneButton.setMessage(label("Redstone", allowRedstone));
        hoppersButton.setMessage(label("Hoppers", allowHoppers));
        fireButton.setMessage(label("Fire spread", allowFireSpread));
        rentableButton.setMessage(label("Rentable", rentable));
        resetExpireButton.setMessage(label("Reset on expire", resetOnExpire));
        resetUnrentButton.setMessage(label("Reset on cancel", resetOnUnrent));
        rentPrice.setEditable(rentable);
        rentDays.setEditable(rentable);
    }

    private static Component label(String name, boolean value) {
        return Component.literal(name + ": " + (value ? "ON" : "OFF"));
    }

    private void submit() {
        try {
            String rawName = name.getValue().trim();
            if (!rawName.matches("[A-Za-z0-9._-]{1,64}")) {
                throw new IllegalArgumentException("Use 1-64 letters, numbers, dots, underscores or dashes.");
            }
            int parsedPriority = parseInt(priority.getValue(), -1_000_000, 1_000_000, "priority");
            int parsedDays = rentable ? parseInt(rentDays.getValue(), -1, 365_000, "rent period") : -1;
            if (rentable && parsedDays == 0) throw new IllegalArgumentException("Rent days must be -1 or at least 1.");
            long requestId = nextRequestId++;
            ClientPacketDistributor.sendToServer(new RegionEditorSubmitPayload(
                    rawName, parsedPriority, allowBreak, allowPlace, allowInteract, allowPvp,
                    allowExplosions, allowPistons, allowWater, allowLava, allowRedstone,
                    allowHoppers, allowFireSpread, rentable, rentPrice.getValue(), parsedDays,
                    resetOnExpire, resetOnUnrent, requestId
            ));
            notice = "Saving…";
            noticeError = false;
        } catch (IllegalArgumentException exception) {
            notice = exception.getMessage();
            noticeError = true;
        }
    }

    public void acceptResult(RegionEditorResultPayload payload) {
        if (payload == null) return;
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        if (payload.successful()) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal(payload.message()));
            }
            onClose();
            return;
        }
        notice = payload.message();
        noticeError = true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        g.fill(0, 0, width, height, 0xA5000000);
        g.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL);
        g.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        g.text(font, "Create Server Region", x + 16, y + 12, TEXT, true);
        BlockPos p1 = BlockPos.of(selection.point1());
        BlockPos p2 = BlockPos.of(selection.point2());
        g.text(font, "Selection: " + compact(p1) + " → " + compact(p2) + " | " + shortDimension(selection.dimension()),
                x + 16, y + 24, MUTED, false);
        g.text(font, "Name", x + 16, y + 32, MUTED, false);
        g.text(font, "Priority", x + 238, y + 32, MUTED, false);
        g.text(font, "Initial protection flags", x + 16, y + 82, MUTED, false);
        g.text(font, "Rental settings", x + 16, y + 202, MUTED, false);
        if (!notice.isBlank()) {
            g.text(font, trim(notice, 70), x + 108, y + 298, noticeError ? ERROR : GOOD, false);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private int panelX() { return (width - PANEL_WIDTH) / 2; }
    private int panelY() { return (height - PANEL_HEIGHT) / 2; }
    private static String compact(BlockPos pos) { return pos.getX() + "," + pos.getY() + "," + pos.getZ(); }
    private static String shortDimension(String value) { int i = value.indexOf(':'); return i < 0 ? value : value.substring(i + 1); }
    private static String trim(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }

    private static int parseInt(String raw, int min, int max, String label) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid " + label + ".");
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
