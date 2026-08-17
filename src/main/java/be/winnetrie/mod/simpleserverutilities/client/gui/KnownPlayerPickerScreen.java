package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;
import java.util.function.Consumer;

import be.winnetrie.mod.simpleserverutilities.network.SsuMenuPageDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuPageRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-paged picker for every player identity SSU currently knows about. */
public final class KnownPlayerPickerScreen extends Screen {
    private static final int W = 360, H = 300, PAGE_SIZE = 7;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7, MUTED = 0xFFAAB5BE;
    private static final int ERROR = 0xFFFF8585;
    private final Screen parent;
    private final Consumer<String> selection;
    private final String description;
    private SsuMenuPageDataPayload data = SsuMenuPageDataPayload.empty("known_players", 0, PAGE_SIZE, 0L, "", false);
    private EditBox search;
    private String query = "";
    private int page;
    private long nextRequest = 1L;
    private long pendingRequest;
    private boolean loading;
    private boolean requestedOnce;

    public KnownPlayerPickerScreen(Screen parent, Consumer<String> selection) {
        this(parent, selection, "Select a player.");
    }

    public KnownPlayerPickerScreen(Screen parent, Consumer<String> selection, String description) {
        super(Component.literal("Choose player"));
        this.parent = parent;
        this.selection = selection;
        this.description = description == null || description.isBlank() ? "Select a player." : description;
    }

    public void accept(SsuMenuPageDataPayload payload) {
        if (payload == null || !"known_players".equals(payload.page()) || payload.requestId() < pendingRequest) return;
        data = payload;
        page = payload.pageIndex();
        loading = false;
        requestedOnce = true;
        rebuildWidgets();
    }

    @Override protected void init() {
        int x = left(), y = top();
        search = new EditBox(font, x + 16, y + 42, W - 104, 20, Component.literal("Search player"));
        search.setMaxLength(64);
        search.setValue(query);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Search"), b -> {
            query = search.getValue();
            requestPage(0);
        }).bounds(x + W - 80, y + 42, 64, 20).build());

        List<SsuMenuPageDataPayload.AccountEntry> entries = data.accounts();
        for (int row = 0; row < Math.min(PAGE_SIZE, entries.size()); row++) {
            var account = entries.get(row);
            String label = account.name().isBlank() ? account.id() : account.name();
            addRenderableWidget(Button.builder(Component.literal(label), b -> choose(account))
                    .bounds(x + 16, y + 76 + row * 25, W - 32, 20).build());
        }

        Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> requestPage(Math.max(0, page - 1)))
                .bounds(x + 16, y + H - 34, 28, 20).build());
        prev.active = !loading && page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> requestPage(Math.min(pages() - 1, page + 1)))
                .bounds(x + 52, y + H - 34, 28, 20).build());
        next.active = !loading && page + 1 < pages();
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(x + W - 76, y + H - 34, 60, 20).build());

        if (!requestedOnce && !loading) requestPage(0);
    }

    private void requestPage(int targetPage) {
        if (search != null) query = search.getValue();
        page = Math.max(0, targetPage);
        loading = true;
        requestedOnce = true;
        pendingRequest = nextRequest++;
        PacketDistributor.sendToServer(new SsuMenuPageRequestPayload(
                "known_players", page, PAGE_SIZE, query, pendingRequest));
        rebuildWidgets();
    }

    private void choose(SsuMenuPageDataPayload.AccountEntry account) {
        String value = account.name().isBlank() ? account.id() : account.name();
        selection.accept(value);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private int pages() { return Math.max(1, (data.totalItems() + PAGE_SIZE - 1) / PAGE_SIZE); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000);
        g.fill(x, y, x + W, y + H, PANEL);
        g.renderOutline(x, y, W, H, BORDER);
        g.drawString(font, "Choose known player", x + 16, y + 16, TEXT, true);
        String status = loading ? "Loading players…" : data.error() ? data.notice()
                : data.totalItems() == 0 ? "No matching players." : description;
        g.drawString(font, status, x + 16, y + 28, data.error() ? ERROR : MUTED, false);
        g.drawCenteredString(font, "Page " + (page + 1) + "/" + pages(), x + W / 2, y + H - 29, MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
}
