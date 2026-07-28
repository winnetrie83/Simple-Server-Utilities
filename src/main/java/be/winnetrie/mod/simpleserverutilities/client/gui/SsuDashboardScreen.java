package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SsuDashboardScreen extends Screen {

    private static final int PANEL_WIDTH = 360;
    private static final int ROWS_PER_PAGE = 7;

    private SsuMenuSnapshotPayload snapshot;
    private Page page = Page.HOME;
    private int listPage;

    public SsuDashboardScreen(SsuMenuSnapshotPayload snapshot) {
        super(Component.translatable("screen.simpleserverutilities.dashboard"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        int left = Math.max(12, (width - PANEL_WIDTH) / 2);
        int top = 32;

        addTopNavigation(left, top);

        switch (page) {
            case HOME -> addHomeButtons(left, top + 34);
            case CLAIMS -> addClaimButtons(left, top + 34);
            case TRAVEL -> addTravelButtons(left, top + 34);
            case SETTINGS -> addSettingsButtons(left, top + 34);
            case REGIONS -> addRegionButtons(left, top + 34);
            case CORE -> addCoreButtons(left, top + 34);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 78, height - 30, 78, 20)
                .build());
    }

    private void addTopNavigation(int left, int top) {
        int buttonWidth = 66;
        addPageButton(Page.HOME, left, top, buttonWidth);
        addPageButton(Page.CLAIMS, left + 70, top, buttonWidth);
        addPageButton(Page.TRAVEL, left + 140, top, buttonWidth);
        addPageButton(Page.SETTINGS, left + 210, top, buttonWidth);

        if (snapshot.administrator()) {
            addPageButton(Page.REGIONS, left + 280, top, 80);
        }
    }

    private void addPageButton(Page target, int x, int y, int width) {
        Button button = Button.builder(target.label(), ignored -> openPage(target))
                .bounds(x, y, width, 20)
                .build();
        button.active = page != target;
        addRenderableWidget(button);
    }

    private void addHomeButtons(int left, int top) {
        addCardButton(left, top, "Claims & Land", snapshot.claims().size() + " claim(s)", Page.CLAIMS);
        addCardButton(left + 184, top, "Homes & Warps",
                snapshot.homes().size() + " home(s), " + snapshot.warps().size() + " warp(s)", Page.TRAVEL);
        addCardButton(left, top + 62, "Player Settings", "Personal SSU preferences", Page.SETTINGS);

        if (snapshot.administrator()) {
            addCardButton(left + 184, top + 62, "Administration",
                    snapshot.regions().size() + " region(s)", Page.REGIONS);
            addCardButton(left, top + 124, "Core Status",
                    snapshot.activeJobs() + " job(s), " + snapshot.pendingStorageWrites() + " pending write(s)", Page.CORE);
        }
    }

    private void addCardButton(int x, int y, String title, String subtitle, Page target) {
        addRenderableWidget(Button.builder(Component.literal(title), ignored -> openPage(target))
                .bounds(x, y, 176, 38)
                .build());
        // Subtitle is drawn by extractRenderState so the card stays compatible with vanilla widgets.
    }

    private void addClaimButtons(int left, int top) {
        List<SsuMenuSnapshotPayload.ClaimSummary> claims = snapshot.claims();
        int start = listPage * ROWS_PER_PAGE;
        int end = Math.min(claims.size(), start + ROWS_PER_PAGE);

        for (int i = start; i < end; i++) {
            SsuMenuSnapshotPayload.ClaimSummary claim = claims.get(i);
            int rowY = top + (i - start) * 25;
            addRenderableWidget(Button.builder(Component.literal("Map"), ignored -> command("claims gui " + claim.name()))
                    .bounds(left + PANEL_WIDTH - 112, rowY, 54, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Show"), ignored -> command("claims show " + claim.name()))
                    .bounds(left + PANEL_WIDTH - 55, rowY, 55, 20)
                    .build());
        }

        addRenderableWidget(Button.builder(Component.literal("Open claim map"), ignored -> command("claims gui"))
                .bounds(left, height - 30, 105, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Hide border"), ignored -> command("claims hide"))
                .bounds(left + 109, height - 30, 78, 20)
                .build());
        addPagination(left + 194, height - 30, claims.size());
    }

    private void addTravelButtons(int left, int top) {
        List<SsuMenuSnapshotPayload.LocationSummary> locations = new ArrayList<>();
        locations.addAll(snapshot.homes());
        locations.addAll(snapshot.warps());
        int start = listPage * ROWS_PER_PAGE;
        int end = Math.min(locations.size(), start + ROWS_PER_PAGE);

        for (int i = start; i < end; i++) {
            SsuMenuSnapshotPayload.LocationSummary location = locations.get(i);
            int rowY = top + (i - start) * 25;
            boolean home = i < snapshot.homes().size();
            String command = home ? "homes tp " + location.name() : "warps tp " + location.name();
            addRenderableWidget(Button.builder(Component.literal("Teleport"), ignored -> commandAndClose(command))
                    .bounds(left + PANEL_WIDTH - 70, rowY, 70, 20)
                    .build());
        }

        addPagination(left, height - 30, locations.size());
    }

    private void addSettingsButtons(int left, int top) {
        Button claims = Button.builder(
                        Component.literal("Claim borders: " + onOff(snapshot.claimBordersVisible())),
                        ignored -> toggleClaimBorders())
                .bounds(left, top, PANEL_WIDTH, 20)
                .build();
        claims.active = snapshot.canViewClaimBorders();
        addRenderableWidget(claims);

        Button regions = Button.builder(
                        Component.literal("Nearby region borders: " + onOff(snapshot.regionBordersVisible())),
                        ignored -> toggleRegionBorders())
                .bounds(left, top + 28, PANEL_WIDTH, 20)
                .build();
        regions.active = snapshot.canViewRegionBorders();
        addRenderableWidget(regions);

        addRenderableWidget(Button.builder(Component.literal("Refresh menu data"), ignored -> requestRefresh())
                .bounds(left, top + 70, 140, 20)
                .build());
    }

    private void addRegionButtons(int left, int top) {
        List<SsuMenuSnapshotPayload.RegionSummary> regions = snapshot.regions();
        int start = listPage * ROWS_PER_PAGE;
        int end = Math.min(regions.size(), start + ROWS_PER_PAGE);

        for (int i = start; i < end; i++) {
            SsuMenuSnapshotPayload.RegionSummary region = regions.get(i);
            int rowY = top + (i - start) * 25;
            String label = region.visible() ? "Hide" : "Show";
            addRenderableWidget(Button.builder(Component.literal(label), ignored -> toggleRegion(region.name()))
                    .bounds(left + PANEL_WIDTH - 55, rowY, 55, 20)
                    .build());
        }

        addRenderableWidget(Button.builder(Component.literal("Hide all selected"), ignored -> hideAllRegions())
                .bounds(left, height - 30, 120, 20)
                .build());
        addPagination(left + 128, height - 30, regions.size());
    }

    private void addCoreButtons(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("Refresh status"), ignored -> requestRefresh())
                .bounds(left, top + 62, 120, 20)
                .build());
    }

    private void addPagination(int left, int y, int itemCount) {
        int pages = Math.max(1, (itemCount + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        Button previous = Button.builder(Component.literal("<"), ignored -> changeListPage(-1))
                .bounds(left, y, 24, 20)
                .build();
        previous.active = listPage > 0;
        addRenderableWidget(previous);

        Button next = Button.builder(Component.literal(">"), ignored -> changeListPage(1))
                .bounds(left + 62, y, 24, 20)
                .build();
        next.active = listPage + 1 < pages;
        addRenderableWidget(next);
    }

    private void openPage(Page target) {
        page = target;
        listPage = 0;
        rebuildWidgets();
    }

    private void changeListPage(int delta) {
        listPage = Math.max(0, listPage + delta);
        rebuildWidgets();
    }

    private void toggleClaimBorders() {
        boolean visible = !snapshot.claimBordersVisible();
        command("ssu borders claims " + (visible ? "on" : "off"));
        snapshot = new SsuMenuSnapshotPayload(
                snapshot.administrator(), visible, snapshot.regionBordersVisible(),
                snapshot.canViewClaimBorders(), snapshot.canViewRegionBorders(),
                snapshot.activeJobs(), snapshot.pendingStorageWrites(),
                snapshot.claims(), snapshot.regions(), snapshot.homes(), snapshot.warps()
        );
        rebuildWidgets();
    }

    private void toggleRegionBorders() {
        boolean visible = !snapshot.regionBordersVisible();
        command("ssu borders regions " + (visible ? "on" : "off"));
        snapshot = new SsuMenuSnapshotPayload(
                snapshot.administrator(), snapshot.claimBordersVisible(), visible,
                snapshot.canViewClaimBorders(), snapshot.canViewRegionBorders(),
                snapshot.activeJobs(), snapshot.pendingStorageWrites(),
                snapshot.claims(), snapshot.regions(), snapshot.homes(), snapshot.warps()
        );
        rebuildWidgets();
    }

    private void toggleRegion(String name) {
        List<SsuMenuSnapshotPayload.RegionSummary> updated = new ArrayList<>(snapshot.regions().size());
        for (SsuMenuSnapshotPayload.RegionSummary region : snapshot.regions()) {
            if (region.name().equalsIgnoreCase(name)) {
                command("regions " + (region.visible() ? "hide " : "show ") + region.name());
                updated.add(new SsuMenuSnapshotPayload.RegionSummary(
                        region.name(), region.dimension(), region.bounds(), !region.visible(), region.rented()
                ));
            } else {
                updated.add(region);
            }
        }
        snapshot = new SsuMenuSnapshotPayload(
                snapshot.administrator(), snapshot.claimBordersVisible(), snapshot.regionBordersVisible(),
                snapshot.canViewClaimBorders(), snapshot.canViewRegionBorders(),
                snapshot.activeJobs(), snapshot.pendingStorageWrites(),
                snapshot.claims(), updated, snapshot.homes(), snapshot.warps()
        );
        rebuildWidgets();
    }

    private void hideAllRegions() {
        command("regions hide");
        List<SsuMenuSnapshotPayload.RegionSummary> updated = snapshot.regions().stream()
                .map(region -> new SsuMenuSnapshotPayload.RegionSummary(
                        region.name(), region.dimension(), region.bounds(), false, region.rented()
                ))
                .toList();
        snapshot = new SsuMenuSnapshotPayload(
                snapshot.administrator(), snapshot.claimBordersVisible(), snapshot.regionBordersVisible(),
                snapshot.canViewClaimBorders(), snapshot.canViewRegionBorders(),
                snapshot.activeJobs(), snapshot.pendingStorageWrites(),
                snapshot.claims(), updated, snapshot.homes(), snapshot.warps()
        );
        rebuildWidgets();
    }

    private void requestRefresh() {
        command("ssu menu");
    }

    private void command(String command) {
        if (minecraft.player != null) {
            minecraft.player.connection.sendUnattendedCommand(command, this);
        }
    }

    private void commandAndClose(String command) {
        if (minecraft.player != null) {
            minecraft.player.connection.sendUnattendedCommand(command, null);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = Math.max(12, (width - PANEL_WIDTH) / 2);
        graphics.fill(left - 8, 16, left + PANEL_WIDTH + 8, height - 38, 0xB0101010);
        graphics.text(font, "Simple Server Utilities", left, 18, 0xFFFFFFFF);
        graphics.text(font, page.subtitle(), left, 58, 0xFFB0B0B0);

        switch (page) {
            case HOME -> drawHomeDetails(graphics, left, 66);
            case CLAIMS -> drawClaims(graphics, left, 66);
            case TRAVEL -> drawLocations(graphics, left, 66);
            case SETTINGS -> drawSettings(graphics, left, 126);
            case REGIONS -> drawRegions(graphics, left, 66);
            case CORE -> drawCore(graphics, left, 92);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawHomeDetails(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.text(font, snapshot.claims().size() + " claim(s)", left + 8, top + 42, 0xFF909090);
        graphics.text(font, snapshot.homes().size() + " home(s), " + snapshot.warps().size() + " warp(s)", left + 192, top + 42, 0xFF909090);
        graphics.text(font, "Personal SSU preferences", left + 8, top + 104, 0xFF909090);

        if (snapshot.administrator()) {
            graphics.text(font, snapshot.regions().size() + " region(s)", left + 192, top + 104, 0xFF909090);
            graphics.text(font,
                    snapshot.activeJobs() + " job(s), " + snapshot.pendingStorageWrites() + " pending write(s)",
                    left + 8,
                    top + 166,
                    0xFF909090
            );
        }
    }

    private void drawClaims(GuiGraphicsExtractor graphics, int left, int top) {
        int start = listPage * ROWS_PER_PAGE;
        int end = Math.min(snapshot.claims().size(), start + ROWS_PER_PAGE);
        if (start >= end) {
            graphics.text(font, "You do not have any claims yet.", left, top + 24, 0xFFB0B0B0);
            return;
        }
        for (int i = start; i < end; i++) {
            var claim = snapshot.claims().get(i);
            int y = top + 34 + (i - start) * 25;
            graphics.text(font, claim.name(), left, y, 0xFFFFFFFF);
            graphics.text(font, claim.chunkCount() + " chunks | " + shortDimension(claim.dimension()), left + 100, y, 0xFF9A9A9A);
        }
    }

    private void drawLocations(GuiGraphicsExtractor graphics, int left, int top) {
        List<SsuMenuSnapshotPayload.LocationSummary> locations = new ArrayList<>();
        locations.addAll(snapshot.homes());
        locations.addAll(snapshot.warps());
        int start = listPage * ROWS_PER_PAGE;
        int end = Math.min(locations.size(), start + ROWS_PER_PAGE);
        if (start >= end) {
            graphics.text(font, "No homes or warps are available.", left, top + 24, 0xFFB0B0B0);
            return;
        }
        for (int i = start; i < end; i++) {
            var location = locations.get(i);
            boolean home = i < snapshot.homes().size();
            int y = top + 34 + (i - start) * 25;
            graphics.text(font, (home ? "Home: " : "Warp: ") + location.name(), left, y, 0xFFFFFFFF);
            graphics.text(font, shortDimension(location.dimension()) + " | " + formatPosition(location), left + 120, y, 0xFF9A9A9A);
        }
    }

    private void drawSettings(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.text(font, "These settings are stored separately for every player.", left, top, 0xFFB0B0B0);
        graphics.text(font, "Claim borders use a low, translucent ribbon near your current height.", left, top + 14, 0xFF909090);
        graphics.text(font, "Individually selected admin regions remain visible until you hide them.", left, top + 28, 0xFF909090);
    }

    private void drawRegions(GuiGraphicsExtractor graphics, int left, int top) {
        int start = listPage * ROWS_PER_PAGE;
        int end = Math.min(snapshot.regions().size(), start + ROWS_PER_PAGE);
        if (start >= end) {
            graphics.text(font, "No server regions are configured.", left, top + 24, 0xFFB0B0B0);
            return;
        }
        for (int i = start; i < end; i++) {
            var region = snapshot.regions().get(i);
            int y = top + 34 + (i - start) * 25;
            int color = region.visible() ? 0xFFA855F7 : 0xFFFFFFFF;
            graphics.text(font, region.name(), left, y, color);
            String state = region.visible() ? "visible" : "hidden";
            graphics.text(font, shortDimension(region.dimension()) + " | " + state + (region.rented() ? " | rented" : ""), left + 100, y, 0xFF9A9A9A);
        }
    }

    private void drawCore(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.text(font, "Active server jobs: " + snapshot.activeJobs(), left, top, 0xFFFFFFFF);
        graphics.text(font, "Pending storage writes: " + snapshot.pendingStorageWrites(), left, top + 16, 0xFFFFFFFF);
        graphics.text(font, "More performance and module administration pages will reuse this GUI Core.", left, top + 42, 0xFF909090);
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String shortDimension(String dimension) {
        int separator = dimension.indexOf(':');
        return separator >= 0 ? dimension.substring(separator + 1) : dimension;
    }

    private static String formatPosition(SsuMenuSnapshotPayload.LocationSummary location) {
        return (int) Math.floor(location.x()) + ", " + (int) Math.floor(location.y()) + ", " + (int) Math.floor(location.z());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Page {
        HOME("Home", "Player dashboard"),
        CLAIMS("Claims", "Your claims and land tools"),
        TRAVEL("Travel", "Homes and server warps"),
        SETTINGS("Settings", "Personal module settings"),
        REGIONS("Regions", "Server region administration"),
        CORE("Core", "Storage and scheduler status");

        private final Component label;
        private final String subtitle;

        Page(String label, String subtitle) {
            this.label = Component.literal(label);
            this.subtitle = subtitle;
        }

        Component label() {
            return label;
        }

        String subtitle() {
            return subtitle;
        }
    }
}
