package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import be.winnetrie.mod.simpleserverutilities.kits.KitDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcItemCodec;
import be.winnetrie.mod.simpleserverutilities.network.KitActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.KitDataPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Compact player kit catalogue and administration editor. */
public final class KitScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int ADMIN_W = 528, ADMIN_H = 330;
    private static final int PLAYER_W = 495, PLAYER_H = 300;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE;

    private KitDataPayload data;
    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int selected;
    private long requestId = 1L;
    private boolean creating;

    private EditBox idBox, nameBox, descriptionBox, permissionBox, priceBox, cooldownBox;
    private boolean enabled = true, locked, oneTime;

    public KitScreen(KitDataPayload data, Screen parent) {
        super(Component.literal(data.admin() ? "Kit Administration" : "Kits"));
        this.data = data;
        this.parent = parent;
        parse();
    }

    public void accept(KitDataPayload next) { data = next; parse(); rebuildWidgets(); }

    private void parse() {
        rows.clear();
        try {
            JsonArray array = GSON.fromJson(data.json(), JsonArray.class);
            if (array != null) for (var element : array) {
                JsonObject object = element.getAsJsonObject();
                KitDefinition definition = GSON.fromJson(object.get("definition"), KitDefinition.class);
                if (definition == null) continue;
                definition.normalize();
                rows.add(new Row(definition, object.get("remainingSeconds").getAsLong(), object.get("claimed").getAsBoolean()));
            }
        } catch (RuntimeException ignored) { }
        if (!data.selectedId().isBlank()) for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).definition.id.equalsIgnoreCase(data.selectedId())) { selected = i; creating = false; break; }
        }
        selected = Math.max(0, Math.min(selected, Math.max(0, rows.size() - 1)));
        loadSelected();
    }

    @Override protected void init() {
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        int listW = data.admin() ? 146 : 140;
        int visible = Math.min(data.admin() ? 9 : 8, rows.size());
        for (int i = 0; i < visible; i++) {
            int rowIndex = i;
            Button button = addRenderableWidget(Button.builder(Component.literal(rows.get(i).definition.displayName), ignored -> {
                collect(); selected = rowIndex; creating = false; loadSelected(); rebuildWidgets();
            }).bounds(x + 14, y + 48 + i * 24, listW, 19).build());
            button.active = creating || i != selected;
        }
        if (data.admin()) initAdmin(x, y); else initPlayer(x, y);
        addRenderableWidget(Button.builder(Component.literal("Close"), ignored -> onClose())
                .bounds(x + 14, y + h - 28, 68, 20).build());
    }

    private void initPlayer(int x, int y) {
        if (rows.isEmpty()) return;
        Row row = rows.get(selected);
        String label = row.definition.locked ? "Locked" : row.remainingSeconds > 0 ? "Cooldown " + row.remainingSeconds + "s"
                : row.definition.oneTime && row.claimed ? "Already used" : "Claim kit";
        Button claim = addRenderableWidget(Button.builder(Component.literal(label), ignored -> send("claim", row.definition.id, "{}"))
                .bounds(x + panelWidth() - 126, y + panelHeight() - 28, 112, 20).build());
        claim.active = !row.definition.locked && row.remainingSeconds <= 0 && !(row.definition.oneTime && row.claimed);
    }

    private void initAdmin(int x, int y) {
        KitDefinition d = creating || rows.isEmpty() ? freshDefinition() : rows.get(selected).definition;
        addRenderableWidget(Button.builder(Component.literal("New kit"), ignored -> {
            collect(); creating = true; enabled = true; locked = false; oneTime = false; rebuildWidgets();
        }).bounds(x + 92, y + 12, 68, 20).build());

        int fx = x + 174;
        idBox = box(fx, y + 50, 132, d.id, 64, "Kit ID"); idBox.active = creating || rows.isEmpty();
        nameBox = box(fx + 142, y + 50, 194, d.displayName, 64, "Display name");
        descriptionBox = box(fx, y + 86, 336, d.description, 256, "Description");
        permissionBox = box(fx, y + 122, 336, d.permissionKey, 128, "Permission key");
        priceBox = box(fx, y + 158, 92, Long.toString(d.priceMinor), 14, "Price");
        cooldownBox = box(fx + 102, y + 158, 92, Long.toString(d.cooldownSeconds), 12, "Seconds");

        addRenderableWidget(Button.builder(Component.literal("Enabled: " + onOff(enabled)), ignored -> { enabled = !enabled; rebuildWidgets(); })
                .bounds(fx, y + 194, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Locked: " + onOff(locked)), ignored -> { locked = !locked; rebuildWidgets(); })
                .bounds(fx + 102, y + 194, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("One time: " + onOff(oneTime)), ignored -> { oneTime = !oneTime; rebuildWidgets(); })
                .bounds(fx + 204, y + 194, 106, 20).build());

        Button contents = addRenderableWidget(Button.builder(Component.literal("Edit contents"), ignored -> {
            KitEditorScreen.prepareReturn(this);
            send("admin_open_contents", d.id, "{}");
        }).bounds(fx, y + 226, 110, 20).build());
        contents.active = !creating && !rows.isEmpty();
        addRenderableWidget(Button.builder(Component.literal(creating || rows.isEmpty() ? "Create" : "Save"), ignored -> save())
                .bounds(fx + 118, y + 226, 82, 20).build());
        Button delete = addRenderableWidget(Button.builder(Component.literal("Delete"), ignored -> send("admin_delete", d.id, "{}"))
                .bounds(fx + 208, y + 226, 82, 20).build());
        delete.active = !creating && !rows.isEmpty();
    }

    private void save() {
        collect();
        KitDefinition d = new KitDefinition();
        d.id = idBox == null ? "" : idBox.getValue(); d.displayName = nameBox == null ? "" : nameBox.getValue();
        d.description = descriptionBox == null ? "" : descriptionBox.getValue(); d.permissionKey = permissionBox == null ? "" : permissionBox.getValue();
        d.priceMinor = longValue(priceBox); d.cooldownSeconds = longValue(cooldownBox); d.enabled = enabled; d.locked = locked; d.oneTime = oneTime; d.normalize();
        send(creating || rows.isEmpty() ? "admin_create" : "admin_save", d.id, GSON.toJson(d));
    }

    private void collect() {
        if (!data.admin() || creating || rows.isEmpty()) return;
        KitDefinition d = rows.get(selected).definition;
        if (nameBox != null) d.displayName = nameBox.getValue(); if (descriptionBox != null) d.description = descriptionBox.getValue();
        if (permissionBox != null) d.permissionKey = permissionBox.getValue(); if (priceBox != null) d.priceMinor = longValue(priceBox);
        if (cooldownBox != null) d.cooldownSeconds = longValue(cooldownBox); d.enabled = enabled; d.locked = locked; d.oneTime = oneTime;
    }

    private void loadSelected() {
        if (creating || rows.isEmpty()) { enabled = true; locked = false; oneTime = false; return; }
        KitDefinition d = rows.get(selected).definition; enabled = d.enabled; locked = d.locked; oneTime = d.oneTime;
    }

    private KitDefinition freshDefinition() { KitDefinition d = new KitDefinition(); d.displayName = "New Kit"; d.enabled = enabled; d.locked = locked; d.oneTime = oneTime; return d; }
    private EditBox box(int x, int y, int width, String value, int maximum, String hint) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint)); box.setHint(Component.literal(hint)); box.setMaxLength(maximum); box.setValue(value == null ? "" : value); addRenderableWidget(box); return box;
    }
    private void send(String action, String kitId, String json) { ClientPacketDistributor.sendToServer(new KitActionPayload(action, kitId, json, requestId++)); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top(), w = panelWidth(), h = panelHeight();
        g.fill(0, 0, width, height, 0xA5000000); g.fill(x, y, x + w, y + h, PANEL); g.outline(x, y, w, h, BORDER);
        g.text(font, data.admin() ? "Kit Administration" : "Available Kits", x + 14, y + 14, TEXT, true);
        if (!rows.isEmpty() && !creating) {
            Row row = rows.get(selected); KitDefinition d = row.definition; int dx = data.admin() ? x + 174 : x + 174;
            if (data.admin()) {
                g.text(font, "Kit ID", dx, y + 39, MUTED, false); g.text(font, "Display name", dx + 142, y + 39, MUTED, false);
                g.text(font, "Description", dx, y + 75, MUTED, false); g.text(font, "Permission", dx, y + 111, MUTED, false);
                g.text(font, "Price", dx, y + 147, MUTED, false); g.text(font, "Cooldown (sec)", dx + 102, y + 147, MUTED, false);
                g.text(font, "Contents", dx, y + 250, MUTED, false);
                drawItems(g, row, dx, y + 264, mouseX, mouseY);
            } else {
                g.text(font, d.displayName, dx, y + 52, TEXT, true);
                if (!strip(d.description).isBlank()) g.text(font, strip(d.description), dx, y + 76, MUTED, false);
                g.text(font, "Price: " + d.priceMinor + "  •  Cooldown: " + d.cooldownSeconds + "s  •  One-time: " + onOff(d.oneTime), dx, y + 104, MUTED, false);
                g.text(font, "Contents", dx, y + 138, MUTED, false);
                drawItems(g, row, dx, y + 154, mouseX, mouseY);
            }
        }
        if (!data.notice().isBlank()) g.text(font, trim(data.notice(), 62), x + 92, y + h - 23, data.error() ? 0xFFFF8585 : 0xFF83E39A, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawItems(GuiGraphicsExtractor g, Row row, int startX, int startY, int mouseX, int mouseY) {
        List<ItemStack> items = itemStacks(row.definition);
        for (int i = 0; i < 9; i++) {
            int sx = startX + i * 22, sy = startY;
            g.fill(sx, sy, sx + 20, sy + 20, 0xFF090D12); g.outline(sx, sy, 20, 20, 0xFF586978);
            ItemStack stack = i < items.size() ? items.get(i) : ItemStack.EMPTY;
            if (!stack.isEmpty()) {
                g.item(stack, sx + 2, sy + 2); g.itemDecorations(font, stack, sx + 2, sy + 2);
                if (SsuGuiGeometry.inside(mouseX, mouseY, sx, sy, 20, 20)) g.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }
        }
    }

    private List<ItemStack> itemStacks(KitDefinition d) {
        if (minecraft == null || minecraft.level == null || d == null || d.items == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        for (var encoded : d.items) result.add(NpcItemCodec.decode(minecraft.level.registryAccess(), encoded, "", 1));
        return result;
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private int panelWidth() { return data.admin() ? ADMIN_W : PLAYER_W; }
    private int panelHeight() { return data.admin() ? ADMIN_H : PLAYER_H; }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    private static long longValue(EditBox box) { try { return box == null ? 0L : Long.parseLong(box.getValue().trim()); } catch (RuntimeException ignored) { return 0L; } }
    private static String strip(String value) { return value == null ? "" : value.replaceAll("§[0-9A-FK-ORa-fk-or]", "").replace('\n', ' '); }
    private static String trim(String value, int max) { if (value == null) return ""; return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…"; }
    private record Row(KitDefinition definition, long remainingSeconds, boolean claimed) { }
}
