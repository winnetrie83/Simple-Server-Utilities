package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.SsuTrustedPlayersActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuTrustedPlayersDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuTrustedPlayersRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Dedicated, searchable trusted-player manager for one claim. */
public final class TrustedPlayersScreen extends Screen {
    private static final int PANEL_WIDTH = 560;
    private static final int PANEL_HEIGHT = 330;
    private static final int ROWS = 7;
    private static final int ROW_HEIGHT = 25;
    private static final int PANEL = 0xF012171E;
    private static final int BORDER = 0xFF52606D;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFA5B0BA;
    private static final int GOOD = 0xFF84E39A;
    private static final int ERROR = 0xFFFF8080;
    private static final int ONLINE = 0xFF72E58B;

    private SsuTrustedPlayersDataPayload data;
    private final Screen parent;
    private boolean addTab;
    private int page;
    private long nextRequestId;
    private EditBox searchBox;
    private String trustedSearch = "";
    private String candidateSearch = "";

    public TrustedPlayersScreen(SsuTrustedPlayersDataPayload data, Screen parent) {
        super(Component.literal(data.title()));
        this.data = data;
        this.parent = parent;
        this.candidateSearch = data.search();
        this.nextRequestId = Math.max(1L, data.requestId() + 1L);
    }

    public void acceptData(SsuTrustedPlayersDataPayload updated) {
        if (!updated.claim().equalsIgnoreCase(data.claim())) return;
        if (updated.requestId() < data.requestId()) return;
        data = updated;
        candidateSearch = updated.search();
        page = Math.max(0, Math.min(page, pageCount() - 1));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();

        Button trustedTab = addRenderableWidget(Button.builder(
                        Component.literal("Trusted players (" + data.trusted().size() + ")"),
                        ignored -> switchTab(false))
                .bounds(x + 14, y + 38, 190, 22).build());
        trustedTab.active = addTab;

        Button addPlayerTab = addRenderableWidget(Button.builder(Component.literal("Add player"), ignored -> switchTab(true))
                .bounds(x + 210, y + 38, 130, 22).build());
        addPlayerTab.active = !addTab && data.canEdit();

        String searchValue = addTab ? candidateSearch : trustedSearch;
        searchBox = new EditBox(font, x + 14, y + 70, PANEL_WIDTH - 110, 20, Component.literal("Search players"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchValue);
        searchBox.setHint(Component.literal(addTab ? "Search known players…" : "Filter trusted players…"));
        searchBox.setResponder(value -> {
            if (addTab) candidateSearch = value;
            else trustedSearch = value;
        });
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal(addTab ? "Search" : "Filter"), ignored -> applySearch())
                .bounds(x + PANEL_WIDTH - 90, y + 70, 76, 20).build());

        List<SsuTrustedPlayersDataPayload.Entry> visible = visibleEntries();
        int pages = pageCount(visible.size());
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * ROWS;
        int to = Math.min(visible.size(), from + ROWS);
        for (int index = from; index < to; index++) {
            SsuTrustedPlayersDataPayload.Entry entry = visible.get(index);
            int row = index - from;
            int rowY = y + 102 + row * ROW_HEIGHT;
            Button name = Button.builder(Component.literal(trim(entry.name(), 36)), ignored -> {})
                    .bounds(x + 32, rowY, PANEL_WIDTH - 142, 20).build();
            name.active = false;
            addRenderableWidget(name);

            String actionLabel = addTab ? "Add" : "Remove";
            Button action = Button.builder(Component.literal(actionLabel), ignored -> act(entry))
                    .bounds(x + PANEL_WIDTH - 102, rowY, 88, 20).build();
            action.active = data.canEdit();
            addRenderableWidget(action);
        }

        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(x + 14, y + PANEL_HEIGHT - 30, 34, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(x + 52, y + PANEL_HEIGHT - 30, 34, 20).build());
        next.active = page + 1 < pages;

        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestData())
                .bounds(x + PANEL_WIDTH - 166, y + PANEL_HEIGHT - 30, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(x + PANEL_WIDTH - 88, y + PANEL_HEIGHT - 30, 74, 20).build());
        setInitialFocus(searchBox);
    }

    private void switchTab(boolean add) {
        if (add && !data.canEdit()) return;
        addTab = add;
        page = 0;
        rebuildWidgets();
    }

    private void applySearch() {
        page = 0;
        if (addTab) requestData();
        else rebuildWidgets();
    }

    private void requestData() {
        long requestId = nextRequestId++;
        ClientPacketDistributor.sendToServer(new SsuTrustedPlayersRequestPayload(
                data.claim(), addTab ? candidateSearch : "", requestId));
    }

    private void act(SsuTrustedPlayersDataPayload.Entry entry) {
        if (!data.canEdit()) return;
        long requestId = nextRequestId++;
        ClientPacketDistributor.sendToServer(new SsuTrustedPlayersActionPayload(
                data.claim(), addTab ? "add" : "remove", entry.playerId(), candidateSearch, requestId));
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
        rebuildWidgets();
    }

    private List<SsuTrustedPlayersDataPayload.Entry> visibleEntries() {
        if (addTab) return data.candidates();
        String query = trustedSearch.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return data.trusted();
        List<SsuTrustedPlayersDataPayload.Entry> result = new ArrayList<>();
        for (SsuTrustedPlayersDataPayload.Entry entry : data.trusted()) {
            if (entry.name().toLowerCase(Locale.ROOT).contains(query)
                    || entry.playerId().toString().contains(query)) {
                result.add(entry);
            }
        }
        return result;
    }

    private int pageCount() {
        return pageCount(visibleEntries().size());
    }

    private static int pageCount(int size) {
        return Math.max(1, (size + ROWS - 1) / ROWS);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        graphics.fill(0, 0, width, height, 0xA5000000);
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL);
        graphics.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        graphics.text(font, data.title(), x + 14, y + 13, TEXT, true);
        graphics.text(font, data.canEdit()
                        ? "Add or remove trusted access without changing claim ownership."
                        : "You do not have permission to change this list.",
                x + 14, y + 26, data.canEdit() ? MUTED : ERROR, false);

        List<SsuTrustedPlayersDataPayload.Entry> visible = visibleEntries();
        int from = page * ROWS;
        int to = Math.min(visible.size(), from + ROWS);
        for (int index = from; index < to; index++) {
            SsuTrustedPlayersDataPayload.Entry entry = visible.get(index);
            int rowY = y + 108 + (index - from) * ROW_HEIGHT;
            graphics.fill(x + 16, rowY, x + 24, rowY + 8, entry.online() ? ONLINE : 0xFF6C7780);
        }

        if (visible.isEmpty()) {
            graphics.centeredText(font,
                    addTab ? "No available players match this search." : "No trusted players match this filter.",
                    x + PANEL_WIDTH / 2, y + 180, MUTED);
        }

        String countText;
        if (addTab && data.candidateTotal() > data.candidates().size()) {
            countText = "Showing " + data.candidates().size() + " of " + data.candidateTotal()
                    + " matches — narrow the search for more.";
        } else {
            countText = visible.size() + (addTab ? " available player(s)" : " trusted player(s)");
        }
        graphics.text(font, countText, x + 14, y + 291, MUTED, false);
        graphics.text(font, "Page " + (page + 1) + " / " + pageCount(visible.size()), x + 94, y + 306, MUTED, false);

        if (!data.notice().isBlank()) {
            graphics.text(font, trim(data.notice(), 70), x + 14, y + 276,
                    data.error() ? ERROR : GOOD, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) {
            minecraft.setScreenAndShow(parent);
            if (parent instanceof PropertySettingsScreen settings) settings.refreshFromChild();
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int panelX() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private static String trim(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, Math.max(0, maximum - 1)) + "…";
    }
}
