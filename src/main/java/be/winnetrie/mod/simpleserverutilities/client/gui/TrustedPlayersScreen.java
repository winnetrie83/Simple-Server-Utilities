package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.SsuClaimRolePermissionActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuTrustedPlayersActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuTrustedPlayersDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuTrustedPlayersRequestPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Claim access manager: assignments and per-claim role permission overrides. */
public final class TrustedPlayersScreen extends Screen {
    private static final int PANEL_WIDTH = 600;
    private static final int PANEL_HEIGHT = 350;
    private static final int ROWS = 7;
    private static final int ROW_HEIGHT = 25;
    private static final int PANEL = 0xF012171E;
    private static final int BORDER = 0xFF52606D;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFA5B0BA;
    private static final int GOOD = 0xFF84E39A;
    private static final int ERROR = 0xFFFF8080;
    private static final int ONLINE = 0xFF72E58B;

    private enum Tab { ACCESS, ADD, PERMISSIONS }

    private SsuTrustedPlayersDataPayload data;
    private final Screen parent;
    private Tab tab = Tab.ACCESS;
    private String selectedRole = "member";
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
        if (tab == Tab.ADD) candidateSearch = updated.search();
        page = Math.max(0, Math.min(page, pageCount() - 1));
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();

        addTabButton(x + 14, y + 38, 174, "Claim access (" + data.trusted().size() + ")", Tab.ACCESS);
        addTabButton(x + 194, y + 38, 112, "Add player", Tab.ADD);
        addTabButton(x + 312, y + 38, 180, "Role permissions", Tab.PERMISSIONS);

        if (tab == Tab.PERMISSIONS) initPermissionTab(x, y);
        else initPlayerTab(x, y);

        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestData())
                .bounds(x + PANEL_WIDTH - 166, y + PANEL_HEIGHT - 30, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), ignored -> onClose())
                .bounds(x + PANEL_WIDTH - 88, y + PANEL_HEIGHT - 30, 74, 20).build());
    }

    private void addTabButton(int x, int y, int width, String label, Tab target) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> switchTab(target))
                .bounds(x, y, width, 22).build());
        button.active = tab != target && (target != Tab.ADD || data.canEdit());
    }

    private void initPlayerTab(int x, int y) {
        boolean add = tab == Tab.ADD;
        String searchValue = add ? candidateSearch : trustedSearch;
        searchBox = new EditBox(font, x + 14, y + 70, PANEL_WIDTH - 110, 20, Component.literal("Search players"));
        searchBox.setMaxLength(64);
        searchBox.setValue(searchValue);
        searchBox.setHint(Component.literal(add ? "Search known players…" : "Filter assigned players…"));
        searchBox.setResponder(value -> {
            if (add) candidateSearch = value;
            else trustedSearch = value;
        });
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal(add ? "Search" : "Filter"), ignored -> applySearch())
                .bounds(x + PANEL_WIDTH - 90, y + 70, 76, 20).build());

        List<SsuTrustedPlayersDataPayload.Entry> visible = visiblePlayers();
        int pages = pageCount(visible.size());
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * ROWS;
        int to = Math.min(visible.size(), from + ROWS);
        for (int index = from; index < to; index++) {
            SsuTrustedPlayersDataPayload.Entry entry = visible.get(index);
            int rowY = y + 102 + (index - from) * ROW_HEIGHT;
            Button name = Button.builder(Component.literal(trim(entry.name(), 30)), ignored -> {})
                    .bounds(x + 32, rowY, PANEL_WIDTH - (add ? 156 : 258), 20).build();
            name.active = false;
            addRenderableWidget(name);
            if (!add) {
                String roleLabel = "co_owner".equals(entry.role()) ? "Co-owner" : "Member";
                Button role = Button.builder(Component.literal(roleLabel), ignored -> changeRole(entry))
                        .bounds(x + PANEL_WIDTH - 218, rowY, 104, 20).build();
                role.active = data.canEdit();
                addRenderableWidget(role);
            }
            Button action = Button.builder(Component.literal(add ? "Add member" : "Remove"), ignored -> act(entry))
                    .bounds(x + PANEL_WIDTH - 108, rowY, 94, 20).build();
            action.active = data.canEdit();
            addRenderableWidget(action);
        }
        addRowPagination(x, y, pages);
        if (searchBox != null) setInitialFocus(searchBox);
    }

    private void initPermissionTab(int x, int y) {
        addRoleButton(x + 14, y + 70, 118, "Co-owner", "co_owner");
        addRoleButton(x + 138, y + 70, 104, "Member", "member");
        addRoleButton(x + 248, y + 70, 104, "Visitor", "visitor");

        List<SsuTrustedPlayersDataPayload.RolePermissionEntry> visible = visibleRolePermissions();
        int pages = pageCount(visible.size());
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * ROWS;
        int to = Math.min(visible.size(), from + ROWS);
        for (int index = from; index < to; index++) {
            var entry = visible.get(index);
            int rowY = y + 102 + (index - from) * ROW_HEIGHT;
            Button label = Button.builder(Component.literal(trim(entry.label(), 38)), ignored -> {})
                    .bounds(x + 14, rowY, PANEL_WIDTH - 216, 20).build();
            label.active = false;
            addRenderableWidget(label);
            Button toggle = Button.builder(Component.literal(entry.allowed() ? "ON" : "OFF"), ignored -> togglePermission(entry))
                    .bounds(x + PANEL_WIDTH - 196, rowY, 68, 20).build();
            toggle.active = data.canEdit();
            addRenderableWidget(toggle);
            Button reset = Button.builder(Component.literal(entry.overridden() ? "Use default" : "Default"),
                            ignored -> resetPermission(entry))
                    .bounds(x + PANEL_WIDTH - 122, rowY, 108, 20).build();
            reset.active = data.canEdit() && entry.overridden();
            addRenderableWidget(reset);
        }
        addRowPagination(x, y, pages);
    }

    private void addRoleButton(int x, int y, int width, String label, String role) {
        Button button = addRenderableWidget(Button.builder(Component.literal(label), ignored -> {
                    selectedRole = role;
                    page = 0;
                    rebuildWidgets();
                }).bounds(x, y, width, 20).build());
        button.active = !role.equals(selectedRole);
    }

    private void addRowPagination(int x, int y, int pages) {
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(x + 14, y + PANEL_HEIGHT - 30, 34, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(x + 52, y + PANEL_HEIGHT - 30, 34, 20).build());
        next.active = page + 1 < pages;
    }

    private void switchTab(Tab target) {
        if (target == Tab.ADD && !data.canEdit()) return;
        tab = target;
        page = 0;
        rebuildWidgets();
    }

    private void applySearch() {
        page = 0;
        if (tab == Tab.ADD) requestData();
        else rebuildWidgets();
    }

    private void requestData() {
        long requestId = nextRequestId++;
        ClientPacketDistributor.sendToServer(new SsuTrustedPlayersRequestPayload(
                data.claim(), tab == Tab.ADD ? candidateSearch : "", requestId));
    }

    private void act(SsuTrustedPlayersDataPayload.Entry entry) {
        if (!data.canEdit()) return;
        long requestId = nextRequestId++;
        ClientPacketDistributor.sendToServer(new SsuTrustedPlayersActionPayload(
                data.claim(), tab == Tab.ADD ? "add" : "remove", entry.playerId(), candidateSearch, requestId));
    }

    private void changeRole(SsuTrustedPlayersDataPayload.Entry entry) {
        if (!data.canEdit()) return;
        long requestId = nextRequestId++;
        String action = "co_owner".equals(entry.role()) ? "role_member" : "role_co_owner";
        ClientPacketDistributor.sendToServer(new SsuTrustedPlayersActionPayload(
                data.claim(), action, entry.playerId(), candidateSearch, requestId));
    }

    private void togglePermission(SsuTrustedPlayersDataPayload.RolePermissionEntry entry) {
        if (!data.canEdit()) return;
        ClientPacketDistributor.sendToServer(new SsuClaimRolePermissionActionPayload(
                data.claim(), selectedRole, entry.key(), Boolean.toString(!entry.allowed()), false, nextRequestId++));
    }

    private void resetPermission(SsuTrustedPlayersDataPayload.RolePermissionEntry entry) {
        if (!data.canEdit() || !entry.overridden()) return;
        ClientPacketDistributor.sendToServer(new SsuClaimRolePermissionActionPayload(
                data.claim(), selectedRole, entry.key(), "", true, nextRequestId++));
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
        rebuildWidgets();
    }

    private List<SsuTrustedPlayersDataPayload.Entry> visiblePlayers() {
        if (tab == Tab.ADD) return data.candidates();
        String query = trustedSearch.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) return data.trusted();
        List<SsuTrustedPlayersDataPayload.Entry> result = new ArrayList<>();
        for (SsuTrustedPlayersDataPayload.Entry entry : data.trusted()) {
            if (entry.name().toLowerCase(Locale.ROOT).contains(query)
                    || entry.playerId().toString().contains(query)) result.add(entry);
        }
        return result;
    }

    private List<SsuTrustedPlayersDataPayload.RolePermissionEntry> visibleRolePermissions() {
        return data.rolePermissions().stream().filter(entry -> selectedRole.equals(entry.role())).toList();
    }

    private int pageCount() {
        int size = tab == Tab.PERMISSIONS ? visibleRolePermissions().size() : visiblePlayers().size();
        return pageCount(size);
    }

    private static int pageCount(int size) {
        return Math.max(1, (size + ROWS - 1) / ROWS);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        SsuGuiScale.fullscreenDim(graphics, this, 0xA5000000);
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, PANEL);
        graphics.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, BORDER);
        graphics.text(font, data.title(), x + 14, y + 13, TEXT, true);
        graphics.text(font, tab == Tab.PERMISSIONS
                        ? "Per-claim overrides. Default uses the server-wide Claim roles permission."
                        : data.canEdit() ? "Assign members or co-owners. Only the owner can open Claim Settings."
                        : "Only the claim owner can change access roles.",
                x + 14, y + 26, data.canEdit() ? MUTED : ERROR, false);

        if (tab != Tab.PERMISSIONS) {
            List<SsuTrustedPlayersDataPayload.Entry> visible = visiblePlayers();
            int from = page * ROWS;
            int to = Math.min(visible.size(), from + ROWS);
            for (int index = from; index < to; index++) {
                var entry = visible.get(index);
                int rowY = y + 108 + (index - from) * ROW_HEIGHT;
                graphics.fill(x + 16, rowY, x + 24, rowY + 8, entry.online() ? ONLINE : 0xFF6C7780);
            }
            if (visible.isEmpty()) graphics.centeredText(font,
                    tab == Tab.ADD ? "No available players match this search." : "No assigned claim roles match this filter.",
                    x + PANEL_WIDTH / 2, y + 190, MUTED);
        } else {
            graphics.text(font, "Selected role: " + roleLabel(selectedRole), x + 366, y + 76, GOOD, false);
        }

        graphics.text(font, "Page " + (page + 1) + " / " + pageCount(), x + 94, y + PANEL_HEIGHT - 25, MUTED, false);
        if (!data.notice().isBlank()) graphics.text(font, trim(data.notice(), 78), x + 14, y + PANEL_HEIGHT - 48,
                data.error() ? ERROR : GOOD, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null && parent != null) {
            minecraft.setScreenAndShow(parent);
            if (parent instanceof PropertySettingsScreen settings) settings.refreshFromChild();
        } else super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }
    private int panelX() { return (width - PANEL_WIDTH) / 2; }
    private int panelY() { return (height - PANEL_HEIGHT) / 2; }
    private static String roleLabel(String role) {
        return switch (role) { case "co_owner" -> "Co-owner"; case "visitor" -> "Visitor"; default -> "Member"; };
    }
    private static String trim(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, Math.max(0, maximum - 1)) + "…";
    }
}
