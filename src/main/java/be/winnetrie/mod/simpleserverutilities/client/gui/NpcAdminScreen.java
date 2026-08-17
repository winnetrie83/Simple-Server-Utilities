package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.NpcAdminActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAdminEntry;
import be.winnetrie.mod.simpleserverutilities.network.NpcAdminListPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAdminListRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Compact remote NPC/template manager. */
public final class NpcAdminScreen extends Screen {
    private static final int W = 520, H = 330, ROWS = 6;
    private static final int PANEL = 0xF0161D25, BORDER = 0xFF586978, TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFAAB5BE, ERROR = 0xFFFF8585, GOOD = 0xFF83E39A;

    private String mode = "placements";
    private String query = "";
    private int page;
    private int pageCount = 1;
    private int total;
    private List<NpcAdminEntry> entries = List.of();
    private EditBox search;
    private String notice = "";
    private boolean noticeError;
    private long nextRequestId = 1L;

    public NpcAdminScreen(NpcAdminListPayload payload) {
        super(Component.literal("NPC Manager"));
        accept(payload);
    }

    public void accept(NpcAdminListPayload payload) {
        if (payload == null) return;
        mode = payload.mode(); query = payload.query(); page = payload.page(); pageCount = payload.pageCount();
        total = payload.total(); entries = payload.entries(); notice = payload.notice(); noticeError = payload.error();
        nextRequestId = Math.max(nextRequestId, payload.requestId() + 1L);
        // Minecraft 26.2 assigns Screen#minecraft in the Screen constructor.  Calling
        // rebuildWidgets() from our own constructor therefore initializes one complete
        // widget set while width/height are still their pre-display values.  Gui#setScreen
        // then performs the real init and adds a second, correctly positioned set.  The
        // result is exactly the apparent "second NPC Manager" seen behind the panel: it is
        // the orphaned pre-init button/search set, not a second rendered screen.
        //
        // Only rebuild for later network refreshes after this exact screen is already the
        // active Gui screen.  Initial construction is left to Screen#init(width, height).
        if (minecraft != null && minecraft.gui.screen() == this) rebuildWidgets();
    }

    @Override protected void init() {
        int x = px(), y = py();
        addRenderableWidget(Button.builder(Component.literal("Placements"), b -> switchMode("placements"))
                .bounds(x + 12, y + 30, 86, 18).build()).active = !"placements".equals(mode);
        addRenderableWidget(Button.builder(Component.literal("Templates"), b -> switchMode("templates"))
                .bounds(x + 102, y + 30, 86, 18).build()).active = !"templates".equals(mode);
        addRenderableWidget(Button.builder(Component.literal("Spawning"), b -> switchMode("spawns"))
                .bounds(x + 192, y + 30, 86, 18).build()).active = !"spawns".equals(mode);
        addRenderableWidget(Button.builder(Component.literal("Create new"), b -> action("spawns".equals(mode) ? "create_spawn_profile" : "create_new", ""))
                .bounds(x + W - 104, y + 30, 92, 18).build());
        search = new EditBox(font, x + 12, y + 54, W - 106, 18, Component.literal("Search NPCs"));
        search.setMaxLength(64); search.setValue(query); addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Search"), b -> request(0))
                .bounds(x + W - 88, y + 54, 76, 18).build());

        int rowY = y + 82;
        for (int i = 0; i < entries.size() && i < ROWS; i++) {
            NpcAdminEntry entry = entries.get(i);
            int yy = rowY + i * 34;
            if ("spawns".equals(mode)) addSpawnButtons(entry, x, yy);
            else if (entry.template()) addTemplateButtons(entry, x, yy);
            else addPlacementButtons(entry, x, yy);
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("‹"), b -> request(page - 1))
                .bounds(x + 12, y + H - 26, 28, 18).build()); previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal("›"), b -> request(page + 1))
                .bounds(x + 44, y + H - 26, 28, 18).build()); next.active = page + 1 < pageCount;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(x + W - 84, y + H - 26, 72, 18).build());
        setInitialFocus(search);
    }

    private void addTemplateButtons(NpcAdminEntry entry, int x, int y) {
        addRenderableWidget(Button.builder(Component.literal("Spawn"), b -> action("spawn_template", entry.id()))
                .bounds(x + 330, y + 7, 66, 18).build());
        Button delete = addRenderableWidget(Button.builder(Component.literal("Delete"), b -> action("delete_template", entry.id()))
                .bounds(x + 402, y + 7, 66, 18).build());
        delete.active = entry.placements() == 0;
    }

    private void addSpawnButtons(NpcAdminEntry entry, int x, int y) {
        int bx = x + 330;
        addRenderableWidget(Button.builder(Component.literal("Edit"), b -> action("edit_spawn_profile", entry.id()))
                .bounds(bx, y + 7, 44, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Test"), b -> action("test_spawn_profile", entry.id()))
                .bounds(bx + 48, y + 7, 44, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), b -> action("delete_spawn_profile", entry.id()))
                .bounds(bx + 96, y + 7, 60, 18).build());
    }

    private void addPlacementButtons(NpcAdminEntry entry, int x, int y) {
        int bx = x + 288;
        addRenderableWidget(Button.builder(Component.literal("Edit"), b -> action("edit", entry.id()))
                .bounds(bx, y + 7, 40, 18).build());
        addRenderableWidget(Button.builder(Component.literal("TP"), b -> action("teleport", entry.id()))
                .bounds(bx + 44, y + 7, 28, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Bring"), b -> action("bring", entry.id()))
                .bounds(bx + 76, y + 7, 42, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Copy"), b -> action("copy", entry.id()))
                .bounds(bx + 122, y + 7, 40, 18).build());
        addRenderableWidget(Button.builder(Component.literal(entry.dead() ? "Respawn" : "Delete"),
                b -> action(entry.dead() ? "respawn" : "delete", entry.id()))
                .bounds(bx + 166, y + 7, 54, 18).build());
    }

    private void switchMode(String value) { mode = value; page = 0; request(0); }
    public void refresh() { if (minecraft != null) request(page); }

    private void request(int wantedPage) {
        query = search == null ? query : search.getValue().trim();
        ClientPacketDistributor.sendToServer(new NpcAdminListRequestPayload(mode, query,
                Math.max(0, wantedPage), ROWS, nextRequestId++));
    }
    private void action(String action, String target) {
        ClientPacketDistributor.sendToServer(new NpcAdminActionPayload(action, target, nextRequestId++));
    }
    @Override public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(null);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = px(), y = py();
        SsuGuiScale.fullscreenDim(g, this, 0xA9000000);
        g.fill(x, y, x + W, y + H, PANEL); g.outline(x, y, W, H, BORDER);
        g.text(font, "NPC Manager", x + 12, y + 12, TEXT, true);
        String totalLabel = "templates".equals(mode) ? " templates" : "spawns".equals(mode) ? " spawn profiles" : " placements";
        g.text(font, total + totalLabel, x + 118, y + 13, MUTED, false);
        int rowY = y + 82;
        for (int i = 0; i < entries.size() && i < ROWS; i++) {
            NpcAdminEntry entry = entries.get(i); int yy = rowY + i * 34;
            g.fill(x + 12, yy, x + W - 12, yy + 30, 0x8A0B1015);
            String status = entry.dead() ? "dead" : entry.enabled() ? "active" : "disabled";
            g.text(font, trim(entry.name(), 30), x + 20, yy + 5, TEXT, false);
            if ("spawns".equals(mode)) {
                String source = entry.model();
                String location = "spawner".equals(source)
                        ? shortDim(entry.dimension()) + " @ " + (int) entry.x() + ", " + (int) entry.y() + ", " + (int) entry.z()
                        : shortDim(entry.dimension());
                g.text(font, trim(entry.definitionId(), 20) + " • " + source + " • " + trim(location, 24)
                        + " • " + entry.placements() + " live • " + status, x + 20, yy + 17, MUTED, false);
            } else if (entry.template()) {
                g.text(font, trim(entry.id(), 32) + " • " + entry.placements() + " placed", x + 20, yy + 17, MUTED, false);
            } else {
                g.text(font, trim(entry.definitionId(), 19) + " • " + shortDim(entry.dimension()) + " • "
                        + one(entry.x()) + ", " + one(entry.y()) + ", " + one(entry.z()) + " • " + status,
                        x + 20, yy + 17, MUTED, false);
            }
        }
        g.text(font, "Page " + (page + 1) + "/" + pageCount, x + 82, y + H - 21, MUTED, false);
        if (!notice.isBlank()) g.text(font, trim(notice, 60), x + 190, y + H - 21,
                noticeError ? ERROR : GOOD, false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private static String one(double value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }
    private static String shortDim(String value) {
        if (value == null) return ""; int split = value.indexOf(':'); return split >= 0 ? value.substring(split + 1) : value;
    }
    private static String trim(String value, int maximum) {
        if (value == null) return ""; return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }
}
