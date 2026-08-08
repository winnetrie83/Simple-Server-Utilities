package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small paged player picker used by the Player report ticket flow. */
public final class SupportPlayerPickerScreen extends Screen {
    private static final int W = 430, H = 360;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE;
    private static final int PAGE_SIZE = 9;

    private final Screen parent;
    private final List<Target> allTargets;
    private final BiConsumer<String, String> selection;
    private List<Target> filtered = List.of();
    private EditBox search;
    private String query = "";
    private int page;

    public SupportPlayerPickerScreen(Screen parent, JsonArray targets, BiConsumer<String, String> selection) {
        super(Component.literal("Choose player"));
        this.parent = parent;
        this.selection = selection;
        ArrayList<Target> parsed = new ArrayList<>();
        if (targets != null) {
            for (JsonElement element : targets) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String id = string(object, "id");
                String name = string(object, "name");
                if (!id.isBlank() && !name.isBlank()) parsed.add(new Target(id, name, bool(object, "online")));
            }
        }
        this.allTargets = List.copyOf(parsed);
        applyFilter();
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        search = new EditBox(font, x + 16, y + 46, 292, 20, Component.literal("Search player"));
        search.setMaxLength(64);
        search.setValue(query);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Search"), button -> {
            query = search.getValue();
            page = 0;
            applyFilter();
            rebuildWidgets();
        }).bounds(x + 316, y + 46, 74, 20).build());

        int start = page * PAGE_SIZE;
        for (int row = 0; row < PAGE_SIZE; row++) {
            int index = start + row;
            if (index >= filtered.size()) break;
            Target target = filtered.get(index);
            String label = (target.online ? "● " : "") + target.name;
            addRenderableWidget(Button.builder(Component.literal(label), button -> choose(target))
                    .bounds(x + 16, y + 82 + row * 25, W - 32, 20).build());
        }

        int pages = pages();
        Button previous = addRenderableWidget(Button.builder(Component.literal("< Previous"), button -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(x + 16, y + H - 48, 92, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("Next >"), button -> {
            page = Math.min(pages - 1, page + 1);
            rebuildWidgets();
        }).bounds(x + 116, y + H - 48, 82, 20).build());
        next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(x + W - 86, y + H - 48, 70, 20).build());
    }

    private void choose(Target target) {
        selection.accept(target.id, target.name);
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    private void applyFilter() {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filtered = allTargets.stream()
                .filter(target -> needle.isBlank() || target.name.toLowerCase(Locale.ROOT).contains(needle)
                        || target.id.toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        page = Math.max(0, Math.min(page, pages() - 1));
    }

    private int pages() {
        return Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        graphics.fill(0, 0, width, height, 0xA5000000);
        graphics.fill(x, y, x + W, y + H, PANEL);
        graphics.outline(x, y, W, H, BORDER);
        graphics.text(font, "Choose player to report", x + 16, y + 16, TEXT, true);
        graphics.text(font, filtered.isEmpty() ? "No matching known players." : "Online players are marked with ●.",
                x + 16, y + 30, MUTED, false);
        graphics.centeredText(font, "Page " + (page + 1) + " / " + pages(), x + W / 2, y + H - 43, MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    private static String string(JsonObject object, String key) {
        try { return object.has(key) ? object.get(key).getAsString() : ""; }
        catch (Exception ignored) { return ""; }
    }

    private static boolean bool(JsonObject object, String key) {
        try { return object.has(key) && object.get(key).getAsBoolean(); }
        catch (Exception ignored) { return false; }
    }

    private record Target(String id, String name, boolean online) { }
}
