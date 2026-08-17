package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.NpcEditorOpenPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Searchable bounded choice window used instead of manually typed shop and faction IDs. */
public final class NpcChoicePickerScreen extends Screen {
    public enum Kind { SHOP, RELATION_FACTION, LOCAL_TEXTURE }

    private static final int W = 430, H = 300, ROWS = 9;
    private final NpcEditorScreen parent;
    private final Kind kind;
    private final List<NpcEditorOpenPayload.Choice> choices;
    private final String selectedId;
    private EditBox search;
    private String searchValue = "";
    private int page;

    public NpcChoicePickerScreen(NpcEditorScreen parent, Kind kind,
            List<NpcEditorOpenPayload.Choice> choices, String selectedId) {
        super(Component.literal(title(kind)));
        this.parent = parent;
        this.kind = kind;
        this.choices = choices == null ? List.of() : List.copyOf(choices);
        this.selectedId = selectedId == null ? "" : selectedId;
    }

    @Override protected void init() {
        int x = px(), y = py();
        search = new EditBox(font, x + 12, y + 34, W - 92, 18, Component.literal("Search"));
        search.setMaxLength(96); search.setValue(searchValue); search.setHint(Component.literal("Search name or ID…"));
        search.setResponder(value -> searchValue = value); addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Find"), button -> { page = 0; rebuildWidgets(); })
                .bounds(x + W - 72, y + 34, 60, 18).build());

        List<NpcEditorOpenPayload.Choice> filtered = filtered();
        int pages = Math.max(1, (filtered.size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * ROWS, to = Math.min(filtered.size(), from + ROWS);
        for (int index = from; index < to; index++) {
            NpcEditorOpenPayload.Choice choice = filtered.get(index);
            int row = index - from;
            Button button = addRenderableWidget(Button.builder(Component.literal(trim(choice.label(), 54)), ignored -> choose(choice.id()))
                    .bounds(x + 12, y + 60 + row * 22, W - 24, 18).build());
            button.active = !choice.id().equals(selectedId);
        }

        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> { page--; rebuildWidgets(); })
                .bounds(x + 12, y + H - 27, 32, 18).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), ignored -> { page++; rebuildWidgets(); })
                .bounds(x + 48, y + H - 27, 32, 18).build());
        next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.literal(kind == Kind.LOCAL_TEXTURE ? "Clear" : "None"), ignored -> choose(""))
                .bounds(x + 88, y + H - 27, 64, 18).build()).active = !selectedId.isBlank();
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(x + W - 76, y + H - 27, 64, 18).build());
        setInitialFocus(search);
    }

    private List<NpcEditorOpenPayload.Choice> filtered() {
        String query = searchValue.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return choices;
        ArrayList<NpcEditorOpenPayload.Choice> result = new ArrayList<>();
        for (NpcEditorOpenPayload.Choice choice : choices) {
            if (choice.id().toLowerCase(Locale.ROOT).contains(query)
                    || choice.label().toLowerCase(Locale.ROOT).contains(query)) result.add(choice);
        }
        return result;
    }

    private void choose(String id) {
        parent.acceptChoice(kind, id);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        SsuGuiScale.fullscreenDim(g, this, 0xA9000000); g.fill(x, y, x + W, y + H, 0xF0161D25);
        g.renderOutline(x, y, W, H, 0xFF586978);
        g.drawString(font, title(kind), x + 12, y + 12, 0xFFF3F5F7, true);
        g.drawString(font, filtered().size() + " option(s)", x + W - 90, y + 13, 0xFFAAB5BE, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static String title(Kind kind) {
        return switch (kind) {
            case SHOP -> "Choose linked shop";
            case RELATION_FACTION -> "Choose target faction";
            case LOCAL_TEXTURE -> "Choose local NPC texture";
        };
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private static String trim(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, maximum - 1) + "…";
    }
}
