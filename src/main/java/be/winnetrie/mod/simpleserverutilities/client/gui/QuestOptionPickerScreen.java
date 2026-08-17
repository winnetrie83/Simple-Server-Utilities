package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Reusable searchable text-option picker for guided quest editing. */
public final class QuestOptionPickerScreen extends Screen {
    private static final int W = 360, H = 286, ROWS = 8;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7,
            MUTED = 0xFFAAB5BE, SELECTED = 0xFFFFC857;

    private final Screen parent;
    private final String titleText;
    private final List<Option> options;
    private final String selected;
    private final Consumer<String> callback;
    private EditBox search;
    private String query = "";
    private int page;

    public QuestOptionPickerScreen(Screen parent, String title, List<String> values, String selected,
                                   Consumer<String> callback) {
        super(Component.literal(title == null || title.isBlank() ? "Choose option" : title));
        this.parent = parent;
        this.titleText = title == null || title.isBlank() ? "Choose option" : title;
        this.selected = selected == null ? "" : selected;
        this.callback = callback;
        ArrayList<Option> loaded = new ArrayList<>();
        if (values != null) for (String value : values) {
            if (value == null || value.isBlank()) continue;
            loaded.add(new Option(value, friendly(value)));
        }
        this.options = List.copyOf(loaded);
    }

    @Override protected void init() {
        int x = left(), y = top();
        search = new EditBox(font, x + 14, y + 38, W - 92, 18, Component.literal("Search"));
        search.setHint(Component.literal("Search options…"));
        search.setValue(query);
        search.setResponder(v -> query = v);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Find"), b -> { page = 0; rebuildWidgets(); })
                .bounds(x + W - 70, y + 38, 56, 18).build());

        List<Option> filtered = filtered();
        int pages = Math.max(1, (filtered.size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * ROWS, to = Math.min(filtered.size(), from + ROWS);
        for (int i = from; i < to; i++) {
            Option option = filtered.get(i);
            String suffix = option.value().equals(selected) ? "  ✓" : "";
            Button row = addRenderableWidget(Button.builder(Component.literal(option.label() + suffix), b -> choose(option.value()))
                    .bounds(x + 14, y + 68 + (i - from) * 22, W - 28, 18).build());
            row.active = !option.value().equals(selected);
        }

        Button prev = addRenderableWidget(Button.builder(Component.literal("‹"), b -> { page--; rebuildWidgets(); })
                .bounds(x + 14, y + H - 28, 32, 18).build());
        prev.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> { page++; rebuildWidgets(); })
                .bounds(x + 50, y + H - 28, 32, 18).build());
        next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(x + W - 78, y + H - 28, 64, 18).build());
        setInitialFocus(search);
    }

    private List<Option> filtered() {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isBlank()) return options;
        ArrayList<Option> out = new ArrayList<>();
        for (Option option : options) if (option.value().toLowerCase(Locale.ROOT).contains(q)
                || option.label().toLowerCase(Locale.ROOT).contains(q)) out.add(option);
        return out;
    }

    private void choose(String value) {
        callback.accept(value);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        int x = left(), y = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.renderOutline(x, y, W, H, BORDER);
        g.drawString(font, titleText, x + 14, y + 14, TEXT, true);
        g.drawString(font, filtered().size() + " option(s)", x + 92, y + H - 23, MUTED, false);
        if (!selected.isBlank()) g.drawString(font, "Current: " + trim(friendly(selected), 28), x + 175, y + H - 23, SELECTED, false);
        super.render(g, mx, my, pt);
    }

    private int left() { return Math.max(4, (width - W) / 2); }
    private int top() { return Math.max(4, (height - H) / 2); }
    private static String friendly(String raw) {
        String[] parts = raw.replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
    private static String trim(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 1) + "…"; }
    private record Option(String value, String label) {}
}
