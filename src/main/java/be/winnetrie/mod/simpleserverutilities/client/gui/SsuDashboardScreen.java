package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Bedrock-inspired SSU dashboard. The server remains authoritative: every
 * action still goes through the existing command and permission layers.
 */
public final class SsuDashboardScreen extends Screen {

    private static final int TILE_SIZE = 54;
    private static final int TILE_LABEL_HEIGHT = 23;
    private static final int PROFILE_WIDTH = 112;

    private static final int PANEL_BACKGROUND = 0xE411151B;
    private static final int PANEL_BORDER = 0xFF4B5664;
    private static final int CARD_BACKGROUND = 0xB51B222B;
    private static final int CARD_HOVER = 0xD22B3946;
    private static final int ACCENT = 0xFFFFD75A;
    private static final int TEXT = 0xFFF2F4F7;
    private static final int MUTED = 0xFF9EABB8;
    private static final int GOOD = 0xFF84E39A;
    private static final int WARNING = 0xFFFFB86B;

    private static final Identifier BUTTON = texture("button.png");
    private static final Identifier BUTTON_GLOW = texture("button_glow.png");
    private static final Identifier BUTTON_BACK = texture("button_back.png");
    private static final Identifier BUTTON_BACK_GLOW = texture("button_back_glow.png");
    private static final Identifier PORTRAIT_FRAME = texture("portrait_framework.png");
    private static final Identifier ICON_CLAIM = texture("claim.png");
    private static final Identifier ICON_SETTINGS = texture("cogwheel.png");
    private static final Identifier ICON_MARKET = texture("market.png");
    private static final Identifier ICON_PLAYERS = texture("multiplayer.png");
    private static final Identifier ICON_PORTAL = texture("portal.png");
    private static final Identifier ICON_SHIELD = texture("shield.png");

    private SsuMenuSnapshotPayload snapshot;
    private Page page = Page.HOME;
    private Page previousPage = Page.HOME;
    private int listPage;

    private PlayerSkinWidget playerSkinWidget;
    private EditBox payPlayerBox;
    private EditBox payAmountBox;
    private EditBox ownerShareBox;
    private EditBox playerRefundBox;
    private EditBox adminRefundBox;
    private EditBox adminPlayerBox;
    private EditBox adminRankBox;
    private EditBox adminPermissionBox;
    private EditBox adminValueBox;

    private String draftPayPlayer = "";
    private String draftPayAmount = "";
    private String draftAdminPlayer = "";
    private String draftAdminRank = "";
    private String draftAdminPermission = "";
    private String draftAdminValue = "true";

    public SsuDashboardScreen(SsuMenuSnapshotPayload snapshot) {
        super(Component.translatable("screen.simpleserverutilities.dashboard"));
        this.snapshot = snapshot;
    }

    @Override
    protected void init() {
        Layout layout = layout();
        clearTransientWidgets();

        if (layout.profileVisible() && minecraft.player != null) {
            playerSkinWidget = new PlayerSkinWidget(
                    54,
                    78,
                    minecraft.getEntityModels(),
                    () -> minecraft.player.getSkin()
            );
            playerSkinWidget.setPosition(layout.profileX() + 29, layout.profileY() + 22);
            addRenderableWidget(playerSkinWidget);
        }

        switch (page) {
            case CLAIMS -> addClaimButtons(layout);
            case TRAVEL -> addTravelButtons(layout);
            case ECONOMY -> addEconomyButtons(layout);
            case SETTINGS -> addSettingsButtons(layout);
            case REGIONS -> addRegionButtons(layout);
            case CORE -> addCoreButtons(layout);
            case PERMISSIONS -> addPermissionButtons(layout);
            case HOME, ADMIN -> {
                // These pages use texture-backed tile hitboxes instead of vanilla widgets.
            }
        }
    }

    private void clearTransientWidgets() {
        playerSkinWidget = null;
        payPlayerBox = null;
        payAmountBox = null;
        ownerShareBox = null;
        playerRefundBox = null;
        adminRefundBox = null;
        adminPlayerBox = null;
        adminRankBox = null;
        adminPermissionBox = null;
        adminValueBox = null;
    }

    private void addClaimButtons(Layout layout) {
        List<SsuMenuSnapshotPayload.ClaimSummary> claims = snapshot.claims();
        int rows = rowsPerPage(layout);
        int start = listPage * rows;
        int end = Math.min(claims.size(), start + rows);

        for (int i = start; i < end; i++) {
            SsuMenuSnapshotPayload.ClaimSummary claim = claims.get(i);
            int rowY = layout.listTop() + (i - start) * 25;
            addRenderableWidget(Button.builder(Component.literal("Map"), ignored -> command("claims gui " + claim.name()))
                    .bounds(layout.contentRight() - 112, rowY, 54, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("Show"), ignored -> command("claims show " + claim.name()))
                    .bounds(layout.contentRight() - 55, rowY, 55, 20)
                    .build());
        }

        int footerY = layout.footerY();
        addRenderableWidget(Button.builder(Component.literal("Open claim map"), ignored -> command("claims gui"))
                .bounds(layout.contentX(), footerY, 106, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Hide border"), ignored -> command("claims hide"))
                .bounds(layout.contentX() + 110, footerY, 80, 20)
                .build());
        addPagination(layout.contentX() + 196, footerY, claims.size(), rows);
    }

    private void addTravelButtons(Layout layout) {
        List<SsuMenuSnapshotPayload.LocationSummary> locations = allLocations();
        int rows = rowsPerPage(layout);
        int start = listPage * rows;
        int end = Math.min(locations.size(), start + rows);

        for (int i = start; i < end; i++) {
            SsuMenuSnapshotPayload.LocationSummary location = locations.get(i);
            int rowY = layout.listTop() + (i - start) * 25;
            boolean home = i < snapshot.homes().size();
            String travelCommand = home ? "homes tp " + location.name() : "warps tp " + location.name();
            addRenderableWidget(Button.builder(Component.literal("Teleport"), ignored -> commandAndClose(travelCommand))
                    .bounds(layout.contentRight() - 72, rowY, 72, 20)
                    .build());
        }

        addPagination(layout.contentX(), layout.footerY(), locations.size(), rows);
    }

    private void addEconomyButtons(Layout layout) {
        int left = layout.contentX();
        int top = layout.contentTop();
        int width = layout.contentWidth();

        if (snapshot.economy().enabled() && snapshot.economy().canPay()) {
            int playerWidth = Math.max(100, Math.min(170, width - 190));
            payPlayerBox = new EditBox(font, left, top + 42, playerWidth, 20, Component.literal("Player name"));
            payPlayerBox.setMaxLength(32);
            payPlayerBox.setValue(draftPayPlayer);
            payPlayerBox.setResponder(value -> draftPayPlayer = value);
            addRenderableWidget(payPlayerBox);

            payAmountBox = new EditBox(font, left + playerWidth + 8, top + 42, 82, 20, Component.literal("Amount"));
            payAmountBox.setMaxLength(24);
            payAmountBox.setValue(draftPayAmount);
            payAmountBox.setResponder(value -> draftPayAmount = value);
            addRenderableWidget(payAmountBox);

            addRenderableWidget(Button.builder(Component.literal("Pay"), ignored -> submitPayment())
                    .bounds(left + playerWidth + 96, top + 42, Math.max(54, width - playerWidth - 96), 20)
                    .build());
        }

        int refreshY = top + 70;
        if (snapshot.economy().canAdmin()) {
            int controlGap = 4;
            int controlWidth = Math.max(48, (width - controlGap * 3) / 4);
            ownerShareBox = percentBox(left, top + 84, snapshot.economy().rentOwnerSharePercent(), "Owner share", controlWidth);
            playerRefundBox = percentBox(left + controlWidth + controlGap, top + 84,
                    snapshot.economy().playerCancelRefundPercent(), "Player refund", controlWidth);
            adminRefundBox = percentBox(left + (controlWidth + controlGap) * 2, top + 84,
                    snapshot.economy().adminCancelRefundPercent(), "Admin refund", controlWidth);
            addRenderableWidget(ownerShareBox);
            addRenderableWidget(playerRefundBox);
            addRenderableWidget(adminRefundBox);
            int applyX = left + (controlWidth + controlGap) * 3;
            addRenderableWidget(Button.builder(Component.literal("Apply policy"), ignored -> submitRentPolicy())
                    .bounds(applyX, top + 84, Math.max(48, layout.contentRight() - applyX), 20)
                    .build());
            refreshY = top + 112;
        }

        addRenderableWidget(Button.builder(Component.literal("Refresh wallet"), ignored -> requestRefresh())
                .bounds(left, refreshY, 120, 20)
                .build());
    }

    private EditBox percentBox(int x, int y, int value, String label, int width) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(label));
        box.setMaxLength(3);
        box.setValue(Integer.toString(value));
        return box;
    }

    private void addSettingsButtons(Layout layout) {
        int left = layout.contentX();
        int top = layout.contentTop() + 22;
        int rowStep = 25;
        int gap = 8;
        int columnWidth = Math.max(110, (layout.contentWidth() - gap) / 2);
        SsuMenuSnapshotPayload.UiSettingsSummary settings = snapshot.uiSettings();

        addSettingButton(left, top, columnWidth,
                "Dashboard hints: " + onOff(settings.dashboardHints()),
                this::toggleDashboardHints, true);
        addSettingButton(left + columnWidth + gap, top, columnWidth,
                "Minimap: " + onOff(settings.minimapEnabled()),
                this::toggleMinimap, true);

        addSettingButton(left, top + rowStep, columnWidth,
                "Minimap size: " + settings.minimapSize() + " px",
                this::cycleMinimapSize, true);
        addSettingButton(left + columnWidth + gap, top + rowStep, columnWidth,
                "Shape: " + friendlyEnum(settings.minimapShape()),
                this::cycleMinimapShape, true);

        addSettingButton(left, top + rowStep * 2, columnWidth,
                "Position: " + friendlyEnum(settings.minimapPosition()),
                this::cycleMinimapPosition, true);
        addSettingButton(left + columnWidth + gap, top + rowStep * 2, columnWidth,
                "North-up: " + onOff(settings.minimapNorthUp()),
                this::toggleNorthUp, true);

        addSettingButton(left, top + rowStep * 3, columnWidth,
                "Claim overlay: " + onOff(settings.minimapShowClaims()),
                this::toggleMinimapClaims, true);
        addSettingButton(left + columnWidth + gap, top + rowStep * 3, columnWidth,
                "Region overlay: " + onOff(settings.minimapShowRegions()),
                this::toggleMinimapRegions, true);

        addSettingButton(left, top + rowStep * 4, columnWidth,
                "Claim borders: " + onOff(snapshot.claimBordersVisible()),
                this::toggleClaimBorders, snapshot.canViewClaimBorders());
        addSettingButton(left + columnWidth + gap, top + rowStep * 4, columnWidth,
                "Region borders: " + onOff(snapshot.regionBordersVisible()),
                this::toggleRegionBorders, snapshot.canViewRegionBorders());

        addRenderableWidget(Button.builder(Component.literal("Refresh menu data"), ignored -> requestRefresh())
                .bounds(left, layout.footerY(), 132, 20)
                .build());
    }

    private void addSettingButton(int x, int y, int width, String label, Runnable action, boolean active) {
        Button button = Button.builder(Component.literal(label), ignored -> action.run())
                .bounds(x, y, width, 20)
                .build();
        button.active = active;
        addRenderableWidget(button);
    }

    private void addRegionButtons(Layout layout) {
        List<SsuMenuSnapshotPayload.RegionSummary> regions = snapshot.regions();
        int rows = rowsPerPage(layout);
        int start = listPage * rows;
        int end = Math.min(regions.size(), start + rows);

        for (int i = start; i < end; i++) {
            SsuMenuSnapshotPayload.RegionSummary region = regions.get(i);
            int rowY = layout.listTop() + (i - start) * 25;
            int right = layout.contentRight();

            addRenderableWidget(Button.builder(
                            Component.literal(region.visible() ? "Hide" : "Show"),
                            ignored -> toggleRegion(region.name()))
                    .bounds(right - 55, rowY, 55, 20)
                    .build());

            if (region.rentedByPlayer() && !region.periodText().equals("permanent")) {
                addRenderableWidget(Button.builder(Component.literal("Extend"), ignored -> commandAndClose("regions extend " + region.name()))
                        .bounds(right - 116, rowY, 58, 20)
                        .build());
                addRenderableWidget(Button.builder(Component.literal("Unrent"), ignored -> commandAndClose("regions unrent " + region.name()))
                        .bounds(right - 178, rowY, 59, 20)
                        .build());
            } else if (region.rentedByPlayer()) {
                addRenderableWidget(Button.builder(Component.literal("Unrent"), ignored -> commandAndClose("regions unrent " + region.name()))
                        .bounds(right - 116, rowY, 58, 20)
                        .build());
            } else if (region.rentable() && !region.rented()) {
                addRenderableWidget(Button.builder(Component.literal("Rent"), ignored -> commandAndClose("regions rent " + region.name()))
                        .bounds(right - 116, rowY, 58, 20)
                        .build());
            }
        }

        int footerY = layout.footerY();
        addRenderableWidget(Button.builder(Component.literal("Hide selected"), ignored -> hideAllRegions())
                .bounds(layout.contentX(), footerY, 105, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestRefresh())
                .bounds(layout.contentX() + 109, footerY, 70, 20)
                .build());
        addPagination(layout.contentX() + 186, footerY, regions.size(), rows);
    }

    private void addCoreButtons(Layout layout) {
        int y = layout.contentTop() + 100;
        addRenderableWidget(Button.builder(Component.literal("Refresh status"), ignored -> requestRefresh())
                .bounds(layout.contentX(), y, 120, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Reset counters"), ignored -> {
                    command("ssu core performance reset");
                    requestRefresh();
                })
                .bounds(layout.contentX() + 128, y, 120, 20)
                .build());
    }

    private void addPermissionButtons(Layout layout) {
        int left = layout.contentX();
        int top = layout.contentTop() + 22;
        int width = layout.contentWidth();
        int half = Math.max(104, (width - 8) / 2);

        adminPlayerBox = textBox(left, top, half, "Player", draftAdminPlayer, value -> draftAdminPlayer = value);
        adminRankBox = textBox(left + half + 8, top, width - half - 8, "Rank", draftAdminRank, value -> draftAdminRank = value);
        addRenderableWidget(adminPlayerBox);
        addRenderableWidget(adminRankBox);

        adminPermissionBox = textBox(left, top + 54, half, "Permission key", draftAdminPermission,
                value -> draftAdminPermission = value);
        adminValueBox = textBox(left + half + 8, top + 54, width - half - 8, "Value", draftAdminValue,
                value -> draftAdminValue = value);
        addRenderableWidget(adminPermissionBox);
        addRenderableWidget(adminValueBox);

        int actionGap = 4;
        int actionWidth = Math.max(54, (width - actionGap * 3) / 4);
        addRenderableWidget(Button.builder(Component.literal("View player"), ignored -> adminViewPlayer())
                .bounds(left, top + 26, actionWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Assign rank"), ignored -> adminAssignRank())
                .bounds(left + actionWidth + actionGap, top + 26, actionWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("View rank"), ignored -> adminViewRank())
                .bounds(left + (actionWidth + actionGap) * 2, top + 26, actionWidth, 20)
                .build());
        int listX = left + (actionWidth + actionGap) * 3;
        addRenderableWidget(Button.builder(Component.literal("List ranks"), ignored -> command("ssu rank list"))
                .bounds(listX, top + 26, Math.max(54, layout.contentRight() - listX), 20)
                .build());

        int permissionGap = 6;
        int permissionWidth = Math.max(86, (width - permissionGap) / 2);
        addRenderableWidget(Button.builder(Component.literal("Set player permission"), ignored -> adminSetPermission())
                .bounds(left, top + 80, permissionWidth, 20)
                .build());
        int unsetX = left + permissionWidth + permissionGap;
        addRenderableWidget(Button.builder(Component.literal("Unset permission"), ignored -> adminUnsetPermission())
                .bounds(unsetX, top + 80, Math.max(86, layout.contentRight() - unsetX), 20)
                .build());
    }

    private EditBox textBox(int x, int y, int width, String hint, String value,
                            java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(font, x, y, Math.max(70, width), 20, Component.literal(hint));
        box.setMaxLength(128);
        box.setValue(value == null ? "" : value);
        box.setResponder(responder);
        return box;
    }

    private void addPagination(int left, int y, int itemCount, int rows) {
        int pages = Math.max(1, (itemCount + rows - 1) / rows);
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
        if (target == page) {
            return;
        }
        previousPage = page;
        page = target;
        listPage = 0;
        rebuildWidgets();
    }

    private void goBack() {
        if (page == Page.HOME) {
            onClose();
            return;
        }
        Page destination = previousPage;
        page = destination;
        previousPage = Page.HOME;
        listPage = 0;
        rebuildWidgets();
    }

    private void changeListPage(int delta) {
        listPage = Math.max(0, listPage + delta);
        rebuildWidgets();
    }

    private void submitPayment() {
        String target = clean(draftPayPlayer);
        String amount = clean(draftPayAmount);
        if (target.isBlank() || amount.isBlank()) {
            return;
        }
        command("pay " + target + " " + amount);
        draftPayAmount = "";
        requestRefresh();
    }

    private void submitRentPolicy() {
        Integer owner = parsePercent(ownerShareBox);
        Integer playerRefund = parsePercent(playerRefundBox);
        Integer adminRefund = parsePercent(adminRefundBox);
        if (owner == null || playerRefund == null || adminRefund == null) {
            return;
        }
        command("regions rentconfig ownershare " + owner);
        command("regions rentconfig playerrefund " + playerRefund);
        command("regions rentconfig adminrefund " + adminRefund);
        requestRefresh();
    }

    private void adminViewPlayer() {
        String player = clean(draftAdminPlayer);
        if (!player.isBlank()) {
            command("ssu perm player " + player + " list");
        }
    }

    private void adminAssignRank() {
        String player = clean(draftAdminPlayer);
        String rank = clean(draftAdminRank);
        if (!player.isBlank() && !rank.isBlank()) {
            command("ssu rank assign " + player + " " + rank);
        }
    }

    private void adminViewRank() {
        String rank = clean(draftAdminRank);
        if (!rank.isBlank()) {
            command("ssu rank info " + rank);
        }
    }

    private void adminSetPermission() {
        String player = clean(draftAdminPlayer);
        String key = clean(draftAdminPermission);
        String value = clean(draftAdminValue);
        if (!player.isBlank() && !key.isBlank() && !value.isBlank()) {
            command("ssu perm player " + player + " set " + key + " " + value);
        }
    }

    private void adminUnsetPermission() {
        String player = clean(draftAdminPlayer);
        String key = clean(draftAdminPermission);
        if (!player.isBlank() && !key.isBlank()) {
            command("ssu perm player " + player + " unset " + key);
        }
    }

    private static Integer parsePercent(EditBox box) {
        if (box == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(box.getValue().trim());
            return value < 0 || value > 100 ? null : value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void toggleDashboardHints() {
        boolean value = !snapshot.uiSettings().dashboardHints();
        command("ssu settings hints " + value);
        updateUiSettings(new SsuMenuSnapshotPayload.UiSettingsSummary(
                value,
                snapshot.uiSettings().minimapEnabled(),
                snapshot.uiSettings().minimapSize(),
                snapshot.uiSettings().minimapShape(),
                snapshot.uiSettings().minimapPosition(),
                snapshot.uiSettings().minimapNorthUp(),
                snapshot.uiSettings().minimapShowClaims(),
                snapshot.uiSettings().minimapShowRegions()
        ));
    }

    private void toggleMinimap() {
        boolean value = !snapshot.uiSettings().minimapEnabled();
        command("ssu settings minimap enabled " + value);
        updateUiSettings(copyUi(value, null, null, null, null, null, null));
    }

    private void cycleMinimapSize() {
        int current = snapshot.uiSettings().minimapSize();
        int next = current >= 256 ? 64 : Math.min(256, current + 32);
        command("ssu settings minimap size " + next);
        updateUiSettings(copyUi(null, next, null, null, null, null, null));
    }

    private void cycleMinimapShape() {
        String next = snapshot.uiSettings().minimapShape().equalsIgnoreCase("CIRCLE") ? "RECTANGLE" : "CIRCLE";
        command("ssu settings minimap shape " + next.toLowerCase(Locale.ROOT));
        updateUiSettings(copyUi(null, null, next, null, null, null, null));
    }

    private void cycleMinimapPosition() {
        String current = snapshot.uiSettings().minimapPosition().toUpperCase(Locale.ROOT);
        String next = switch (current) {
            case "TOP_LEFT" -> "TOP_RIGHT";
            case "TOP_RIGHT" -> "BOTTOM_RIGHT";
            case "BOTTOM_RIGHT" -> "BOTTOM_LEFT";
            default -> "TOP_LEFT";
        };
        command("ssu settings minimap position " + next.toLowerCase(Locale.ROOT));
        updateUiSettings(copyUi(null, null, null, next, null, null, null));
    }

    private void toggleNorthUp() {
        boolean value = !snapshot.uiSettings().minimapNorthUp();
        command("ssu settings minimap northup " + value);
        updateUiSettings(copyUi(null, null, null, null, value, null, null));
    }

    private void toggleMinimapClaims() {
        boolean value = !snapshot.uiSettings().minimapShowClaims();
        command("ssu settings minimap claims " + value);
        updateUiSettings(copyUi(null, null, null, null, null, value, null));
    }

    private void toggleMinimapRegions() {
        boolean value = !snapshot.uiSettings().minimapShowRegions();
        command("ssu settings minimap regions " + value);
        updateUiSettings(copyUi(null, null, null, null, null, null, value));
    }

    private SsuMenuSnapshotPayload.UiSettingsSummary copyUi(
            Boolean minimapEnabled,
            Integer size,
            String shape,
            String position,
            Boolean northUp,
            Boolean showClaims,
            Boolean showRegions
    ) {
        SsuMenuSnapshotPayload.UiSettingsSummary current = snapshot.uiSettings();
        return new SsuMenuSnapshotPayload.UiSettingsSummary(
                current.dashboardHints(),
                minimapEnabled == null ? current.minimapEnabled() : minimapEnabled,
                size == null ? current.minimapSize() : size,
                shape == null ? current.minimapShape() : shape,
                position == null ? current.minimapPosition() : position,
                northUp == null ? current.minimapNorthUp() : northUp,
                showClaims == null ? current.minimapShowClaims() : showClaims,
                showRegions == null ? current.minimapShowRegions() : showRegions
        );
    }

    private void updateUiSettings(SsuMenuSnapshotPayload.UiSettingsSummary settings) {
        be.winnetrie.mod.simpleserverutilities.client.minimap.MinimapClientState.applySettings(settings);
        snapshot = new SsuMenuSnapshotPayload(
                snapshot.playerName(), snapshot.primaryRank(), snapshot.settingsAvailable(), settings,
                snapshot.administrator(), snapshot.claimBordersVisible(), snapshot.regionBordersVisible(),
                snapshot.canViewClaimBorders(), snapshot.canViewRegionBorders(),
                snapshot.activeJobs(), snapshot.pendingStorageWrites(), snapshot.core(), snapshot.economy(),
                snapshot.claims(), snapshot.regions(), snapshot.homes(), snapshot.warps()
        );
        rebuildWidgets();
    }

    private void toggleClaimBorders() {
        boolean visible = !snapshot.claimBordersVisible();
        command("ssu borders claims " + (visible ? "on" : "off"));
        snapshot = new SsuMenuSnapshotPayload(
                snapshot.playerName(), snapshot.primaryRank(), snapshot.settingsAvailable(), snapshot.uiSettings(),
                snapshot.administrator(), visible, snapshot.regionBordersVisible(),
                snapshot.canViewClaimBorders(), snapshot.canViewRegionBorders(),
                snapshot.activeJobs(), snapshot.pendingStorageWrites(), snapshot.core(), snapshot.economy(),
                snapshot.claims(), snapshot.regions(), snapshot.homes(), snapshot.warps()
        );
        rebuildWidgets();
    }

    private void toggleRegionBorders() {
        boolean visible = !snapshot.regionBordersVisible();
        command("ssu borders regions " + (visible ? "on" : "off"));
        snapshot = new SsuMenuSnapshotPayload(
                snapshot.playerName(), snapshot.primaryRank(), snapshot.settingsAvailable(), snapshot.uiSettings(),
                snapshot.administrator(), snapshot.claimBordersVisible(), visible,
                snapshot.canViewClaimBorders(), snapshot.canViewRegionBorders(),
                snapshot.activeJobs(), snapshot.pendingStorageWrites(), snapshot.core(), snapshot.economy(),
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
                        region.name(), region.dimension(), region.bounds(), !region.visible(), region.rented(),
                        region.rentable(), region.rentedByPlayer(), region.formattedPrice(), region.periodText(),
                        region.renterName(), region.remainingText()
                ));
            } else {
                updated.add(region);
            }
        }
        replaceRegions(updated);
    }

    private void hideAllRegions() {
        command("regions hide");
        replaceRegions(snapshot.regions().stream()
                .map(region -> new SsuMenuSnapshotPayload.RegionSummary(
                        region.name(), region.dimension(), region.bounds(), false, region.rented(),
                        region.rentable(), region.rentedByPlayer(), region.formattedPrice(), region.periodText(),
                        region.renterName(), region.remainingText()
                ))
                .toList());
    }

    private void replaceRegions(List<SsuMenuSnapshotPayload.RegionSummary> regions) {
        snapshot = new SsuMenuSnapshotPayload(
                snapshot.playerName(), snapshot.primaryRank(), snapshot.settingsAvailable(), snapshot.uiSettings(),
                snapshot.administrator(), snapshot.claimBordersVisible(), snapshot.regionBordersVisible(),
                snapshot.canViewClaimBorders(), snapshot.canViewRegionBorders(),
                snapshot.activeJobs(), snapshot.pendingStorageWrites(), snapshot.core(), snapshot.economy(),
                snapshot.claims(), regions, snapshot.homes(), snapshot.warps()
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.buttonInfo().button() != 0) {
            return false;
        }

        Layout layout = layout();
        double mouseX = event.x();
        double mouseY = event.y();

        if (backBounds(layout).contains(mouseX, mouseY)) {
            playClick();
            goBack();
            return true;
        }

        if (snapshot.settingsAvailable() && settingsBounds(layout).contains(mouseX, mouseY)) {
            playClick();
            openPage(Page.SETTINGS);
            return true;
        }

        if (snapshot.administrator() && adminBounds(layout).contains(mouseX, mouseY)) {
            playClick();
            openPage(Page.ADMIN);
            return true;
        }

        for (Tile tile : tiles(layout)) {
            if (tile.enabled() && tile.bounds().contains(mouseX, mouseY)) {
                playClick();
                openPage(tile.target());
                return true;
            }
        }
        return false;
    }

    private void playClick() {
        AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Layout layout = layout();
        graphics.fill(0, 0, width, height, 0xA5000000);
        graphics.fill(layout.panelX(), layout.panelY(), layout.panelRight(), layout.panelBottom(), PANEL_BACKGROUND);
        graphics.outline(layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), PANEL_BORDER);
        drawHeader(graphics, layout, mouseX, mouseY);
        drawProfile(graphics, layout);
        drawPage(graphics, layout, mouseX, mouseY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (playerSkinWidget != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, PORTRAIT_FRAME,
                    layout.profileX() + 29, layout.profileY() + 22,
                    0, 0, 54, 78, 54, 78);
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        if (layout.profileVisible()) {
            graphics.text(font, "Simple Server Utilities", layout.panelX() + 12, layout.panelY() + 10, ACCENT, true);
        }
        graphics.text(font, page.label(), layout.contentX(), layout.panelY() + 10, TEXT, true);
        graphics.text(font, page.subtitle(), layout.contentX(), layout.panelY() + 25, MUTED, false);

        drawUtilityButton(graphics, settingsBounds(layout), ICON_SETTINGS,
                page == Page.SETTINGS, mouseX, mouseY, snapshot.settingsAvailable());
        if (snapshot.administrator()) {
            drawUtilityButton(graphics, adminBounds(layout), ICON_SHIELD,
                    page == Page.ADMIN || page == Page.PERMISSIONS, mouseX, mouseY, true);
        }
    }

    private void drawUtilityButton(GuiGraphicsExtractor graphics, Rect bounds, Identifier icon,
                                   boolean selected, int mouseX, int mouseY, boolean visible) {
        if (!visible) {
            return;
        }
        boolean hovered = bounds.contains(mouseX, mouseY);
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                selected ? 0xD03A4D5C : hovered ? CARD_HOVER : CARD_BACKGROUND);
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), hovered || selected ? ACCENT : PANEL_BORDER);
        int iconWidth = icon.equals(ICON_SHIELD) ? 16 : 16;
        int iconHeight = icon.equals(ICON_SHIELD) ? 19 : 16;
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon,
                bounds.x() + (bounds.width() - iconWidth) / 2,
                bounds.y() + (bounds.height() - iconHeight) / 2,
                0, 0, iconWidth, iconHeight, iconWidth, iconHeight);
    }

    private void drawProfile(GuiGraphicsExtractor graphics, Layout layout) {
        if (!layout.profileVisible()) {
            return;
        }
        int x = layout.profileX();
        int y = layout.profileY();
        graphics.fill(x, y, x + PROFILE_WIDTH, layout.panelBottom() - 10, CARD_BACKGROUND);
        graphics.outline(x, y, PROFILE_WIDTH, layout.panelBottom() - 10 - y, PANEL_BORDER);

        int nameY = y + 105;
        drawCentered(graphics, snapshot.playerName().isBlank() ? "Player" : snapshot.playerName(),
                x + PROFILE_WIDTH / 2, nameY, TEXT);
        drawCentered(graphics, "Rank: " + (snapshot.primaryRank().isBlank() ? "default" : snapshot.primaryRank()),
                x + PROFILE_WIDTH / 2, nameY + 14, MUTED);
        drawCentered(graphics, snapshot.economy().enabled() ? snapshot.economy().formattedBalance() : "Economy disabled",
                x + PROFILE_WIDTH / 2, nameY + 32, snapshot.economy().enabled() ? GOOD : MUTED);

        if (layout.panelHeight() >= 285) {
            graphics.text(font, "Claims", x + 9, nameY + 55, MUTED, false);
            graphics.text(font, Integer.toString(snapshot.claims().size()), x + PROFILE_WIDTH - 20, nameY + 55, TEXT, false);
            graphics.text(font, "Homes", x + 9, nameY + 69, MUTED, false);
            graphics.text(font, Integer.toString(snapshot.homes().size()), x + PROFILE_WIDTH - 20, nameY + 69, TEXT, false);
            graphics.text(font, "Warps", x + 9, nameY + 83, MUTED, false);
            graphics.text(font, Integer.toString(snapshot.warps().size()), x + PROFILE_WIDTH - 20, nameY + 83, TEXT, false);
        }

        if (snapshot.administrator()) {
            graphics.text(font, "ADMIN", x + 9, layout.panelBottom() - 25, WARNING, true);
        }
    }

    private void drawPage(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        switch (page) {
            case HOME, ADMIN -> drawTiles(graphics, layout, mouseX, mouseY);
            case CLAIMS -> drawClaims(graphics, layout);
            case TRAVEL -> drawLocations(graphics, layout);
            case ECONOMY -> drawEconomy(graphics, layout);
            case SETTINGS -> drawSettings(graphics, layout);
            case REGIONS -> drawRegions(graphics, layout);
            case CORE -> drawCore(graphics, layout);
            case PERMISSIONS -> drawPermissions(graphics, layout);
        }
        drawBackButton(graphics, layout, mouseX, mouseY);
    }

    private void drawTiles(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        List<Tile> tiles = tiles(layout);
        for (Tile tile : tiles) {
            drawTile(graphics, tile, mouseX, mouseY);
        }

        if (page == Page.HOME) {
            graphics.text(font, "Choose a module", layout.contentX(), layout.contentTop(), MUTED, false);
            graphics.text(font,
                    snapshot.regions().stream().filter(SsuMenuSnapshotPayload.RegionSummary::rentedByPlayer).count()
                            + " active rental(s) | " + snapshot.activeJobs() + " server job(s)",
                    layout.contentX(), layout.contentTop() + 16, MUTED, false);
        } else {
            graphics.text(font, "Administrative tools", layout.contentX(), layout.contentTop(), MUTED, false);
            graphics.text(font, "All actions still enforce SSU permissions on the server.",
                    layout.contentX(), layout.contentTop() + 16, MUTED, false);
        }

        if (snapshot.uiSettings().dashboardHints() && (tileColumns(layout) == 4 || layout.panelHeight() >= 300)) {
            for (Tile tile : tiles) {
                if (tile.bounds().contains(mouseX, mouseY)) {
                    graphics.text(font, tile.hint(), layout.contentX(), layout.panelBottom() - 44,
                            tile.enabled() ? MUTED : WARNING, false);
                    break;
                }
            }
        }
    }

    private void drawTile(GuiGraphicsExtractor graphics, Tile tile, int mouseX, int mouseY) {
        Rect bounds = tile.bounds();
        boolean hovered = tile.enabled() && bounds.contains(mouseX, mouseY);
        Identifier texture = hovered ? BUTTON_GLOW : BUTTON;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                bounds.x(), bounds.y(), 0, 0, TILE_SIZE, TILE_SIZE, TILE_SIZE, TILE_SIZE);

        int iconWidth = tile.icon().equals(ICON_PLAYERS) ? 32 : 16;
        int iconHeight = tile.icon().equals(ICON_SHIELD) ? 19 : iconWidth;
        int iconX = bounds.x() + (TILE_SIZE - iconWidth) / 2;
        int iconY = bounds.y() + (TILE_SIZE - iconHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, tile.icon(), iconX, iconY,
                0, 0, iconWidth, iconHeight, iconWidth, iconHeight);

        int labelColor = tile.enabled() ? TEXT : 0xFF6F7780;
        drawCentered(graphics, tile.label(), bounds.x() + TILE_SIZE / 2, bounds.bottom() + 4, labelColor);
    }

    private List<Tile> tiles(Layout layout) {
        if (page != Page.HOME && page != Page.ADMIN) {
            return List.of();
        }
        int columns = tileColumns(layout);
        int available = layout.contentWidth();
        int gap = columns == 1 ? 0 : Math.max(8, (available - columns * TILE_SIZE) / (columns - 1));
        int used = columns * TILE_SIZE + gap * Math.max(0, columns - 1);
        int startX = layout.contentX() + Math.max(0, (available - used) / 2);
        int startY = layout.contentTop() + 48;

        Identifier[] icons;
        String[] labels;
        String[] hints;
        Page[] targets;
        boolean[] enabled;
        if (page == Page.HOME) {
            icons = new Identifier[] { ICON_CLAIM, ICON_PORTAL, ICON_MARKET, ICON_PLAYERS };
            labels = new String[] { "Claims", "Travel", "Wallet", "Regions" };
            hints = new String[] {
                    "View your claims and open the interactive claim map.",
                    "Teleport to your homes and available server warps.",
                    "View your balance, payments and transaction history.",
                    "View rentable regions and your active rentals."
            };
            targets = new Page[] { Page.CLAIMS, Page.TRAVEL, Page.ECONOMY, Page.REGIONS };
            enabled = new boolean[] {
                    true,
                    true,
                    snapshot.economy().enabled(),
                    snapshot.administrator() || !snapshot.regions().isEmpty()
            };
        } else {
            icons = new Identifier[] { ICON_PLAYERS, ICON_MARKET, ICON_CLAIM, ICON_SETTINGS };
            labels = new String[] { "Players", "Economy", "Regions", "Core" };
            hints = new String[] {
                    "Inspect players, assign ranks and manage personal permissions.",
                    "Open wallet and economy administration tools.",
                    "Manage server regions, visibility and rentals.",
                    "Inspect scheduler, storage and performance counters."
            };
            targets = new Page[] { Page.PERMISSIONS, Page.ECONOMY, Page.REGIONS, Page.CORE };
            enabled = new boolean[] { true, snapshot.economy().enabled(), true, true };
        }

        List<Tile> result = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            int column = i % columns;
            int row = i / columns;
            int x = startX + column * (TILE_SIZE + gap);
            int y = startY + row * (TILE_SIZE + TILE_LABEL_HEIGHT + 8);
            result.add(tile(x, y, icons[i], labels[i], hints[i], targets[i], enabled[i]));
        }
        return List.copyOf(result);
    }

    private int tileColumns(Layout layout) {
        return layout.contentWidth() >= TILE_SIZE * 4 + 8 * 3 ? 4 : 2;
    }

    private static Tile tile(int x, int y, Identifier icon, String label, String hint, Page target, boolean enabled) {
        return new Tile(new Rect(x, y, TILE_SIZE, TILE_SIZE + TILE_LABEL_HEIGHT), icon, label, hint, target, enabled);
    }

    private void drawBackButton(GuiGraphicsExtractor graphics, Layout layout, int mouseX, int mouseY) {
        Rect bounds = backBounds(layout);
        boolean hovered = bounds.contains(mouseX, mouseY);
        graphics.blit(RenderPipelines.GUI_TEXTURED, hovered ? BUTTON_BACK_GLOW : BUTTON_BACK,
                bounds.x(), bounds.y(), 0, 0, 54, 20, 54, 20);
        graphics.text(font, page == Page.HOME ? "Close" : "Back", bounds.right() + 6, bounds.y() + 6, MUTED, false);
    }

    private void drawClaims(GuiGraphicsExtractor graphics, Layout layout) {
        int rows = rowsPerPage(layout);
        int start = listPage * rows;
        int end = Math.min(snapshot.claims().size(), start + rows);
        if (start >= end) {
            graphics.text(font, "You do not have any claims yet.", layout.contentX(), layout.listTop() + 5, MUTED, false);
            return;
        }
        for (int i = start; i < end; i++) {
            SsuMenuSnapshotPayload.ClaimSummary claim = snapshot.claims().get(i);
            int y = layout.listTop() + 6 + (i - start) * 25;
            graphics.text(font, claim.name(), layout.contentX(), y, TEXT, false);
            graphics.text(font, claim.chunkCount() + " chunks | " + shortDimension(claim.dimension()),
                    layout.contentX() + 92, y, MUTED, false);
        }
    }

    private void drawLocations(GuiGraphicsExtractor graphics, Layout layout) {
        List<SsuMenuSnapshotPayload.LocationSummary> locations = allLocations();
        int rows = rowsPerPage(layout);
        int start = listPage * rows;
        int end = Math.min(locations.size(), start + rows);
        if (start >= end) {
            graphics.text(font, "No homes or warps are available.", layout.contentX(), layout.listTop() + 5, MUTED, false);
            return;
        }
        for (int i = start; i < end; i++) {
            SsuMenuSnapshotPayload.LocationSummary location = locations.get(i);
            boolean home = i < snapshot.homes().size();
            int y = layout.listTop() + 6 + (i - start) * 25;
            graphics.text(font, (home ? "Home: " : "Warp: ") + location.name(), layout.contentX(), y, TEXT, false);
            graphics.text(font, shortDimension(location.dimension()) + " | " + formatPosition(location),
                    layout.contentX() + 120, y, MUTED, false);
        }
    }

    private void drawEconomy(GuiGraphicsExtractor graphics, Layout layout) {
        SsuMenuSnapshotPayload.EconomySummary economy = snapshot.economy();
        int left = layout.contentX();
        int top = layout.contentTop();
        if (!economy.enabled()) {
            graphics.text(font, "The economy module is unavailable.", left, top + 8, WARNING, false);
            return;
        }

        graphics.text(font, "Balance: " + economy.formattedBalance(), left, top + 4, GOOD, true);
        if (economy.canAdmin()) {
            graphics.text(font, "Accounts: " + economy.accountCount() + " | Total supply: " + economy.formattedTotalSupply(),
                    left, top + 18, MUTED, false);
            graphics.text(font,
                    "Rent: owner " + economy.rentOwnerSharePercent() + "% | player refund "
                            + economy.playerCancelRefundPercent() + "% | admin refund "
                            + economy.adminCancelRefundPercent() + "%",
                    left, top + 70, 0xFFD0B8FF, false);
            graphics.text(font, "Pending rent operations: " + economy.pendingRentOperations(),
                    left, top + 126, economy.pendingRentOperations() == 0 ? MUTED : WARNING, false);
        }

        int historyY = economy.canAdmin() ? top + 145 : top + 92;
        graphics.text(font, "Recent transactions", left, historyY, TEXT, true);
        if (economy.recentTransactions().isEmpty()) {
            graphics.text(font, "No transactions recorded.", left, historyY + 16, MUTED, false);
            return;
        }

        int room = Math.max(1, (layout.footerY() - historyY - 18) / 14);
        int shown = Math.min(room, economy.recentTransactions().size());
        for (int i = 0; i < shown; i++) {
            SsuMenuSnapshotPayload.TransactionSummary transaction = economy.recentTransactions().get(i);
            String sign = transaction.direction().equals("out") ? "-" : transaction.direction().equals("in") ? "+" : "";
            String party = transaction.otherParty().isBlank() ? "" : " | " + transaction.otherParty();
            graphics.text(font, sign + transaction.formattedAmount() + " | " + transaction.type() + party,
                    left, historyY + 16 + i * 14,
                    transaction.direction().equals("out") ? 0xFFFFB0B0 : 0xFFB0FFB8, false);
        }
    }

    private void drawSettings(GuiGraphicsExtractor graphics, Layout layout) {
        graphics.text(font, "Personal settings are stored per player and validated by the server.",
                layout.contentX(), layout.contentTop(), MUTED, false);
        if (layout.panelHeight() >= 270) {
            graphics.text(font, "The minimap options are ready now; the actual HUD renderer is planned for dev3.",
                    layout.contentX(), layout.panelBottom() - 44, 0xFF8593A1, false);
        }
    }

    private void drawRegions(GuiGraphicsExtractor graphics, Layout layout) {
        int rows = rowsPerPage(layout);
        int start = listPage * rows;
        int end = Math.min(snapshot.regions().size(), start + rows);
        if (start >= end) {
            graphics.text(font, "No rentable or managed server regions are available.",
                    layout.contentX(), layout.listTop() + 5, MUTED, false);
            return;
        }
        for (int i = start; i < end; i++) {
            SsuMenuSnapshotPayload.RegionSummary region = snapshot.regions().get(i);
            int y = layout.listTop() + 6 + (i - start) * 25;
            int color = region.rentedByPlayer() ? GOOD : region.visible() ? 0xFFA855F7 : TEXT;
            graphics.text(font, region.name(), layout.contentX(), y, color, false);

            String state;
            if (region.rentedByPlayer()) {
                state = "yours | " + region.remainingText();
            } else if (region.rented()) {
                state = "rented" + (region.renterName().isBlank() ? "" : " by " + region.renterName());
            } else if (region.rentable()) {
                state = region.formattedPrice() + " / " + region.periodText();
            } else {
                state = "not rentable";
            }
            graphics.text(font, state, layout.contentX() + 86, y, MUTED, false);
        }
    }

    private void drawCore(GuiGraphicsExtractor graphics, Layout layout) {
        SsuMenuSnapshotPayload.CoreSummary core = snapshot.core();
        int left = layout.contentX();
        int top = layout.contentTop() + 4;
        graphics.text(font, "Active server jobs: " + snapshot.activeJobs(), left, top, TEXT, false);
        graphics.text(font, "Pending storage writes: " + snapshot.pendingStorageWrites(), left, top + 16, TEXT, false);
        graphics.text(font,
                "Permission cache: " + String.format(Locale.ROOT, "%.1f%%", core.permissionCacheHitPermille() / 10.0D)
                        + " hit | " + core.permissionCacheEntries() + " entries",
                left, top + 38, 0xFFB8D8FF, false);
        graphics.text(font, "Permission checks: " + core.permissionChecks(), left, top + 54, MUTED, false);
        graphics.text(font,
                "Region lookups: " + core.regionLookups() + " | "
                        + String.format(Locale.ROOT, "%.2f", core.averageRegionCandidates()) + " candidates/lookup",
                left, top + 70, 0xFFD7B8FF, false);
        graphics.text(font,
                "Region index: " + core.regionIndexCells() + " cells | " + core.regionIndexReferences() + " references",
                left, top + 86, MUTED, false);
    }

    private void drawPermissions(GuiGraphicsExtractor graphics, Layout layout) {
        graphics.text(font, "Manage one base rank plus personal overrides. Personal permissions have priority.",
                layout.contentX(), layout.contentTop(), MUTED, false);
        graphics.text(font, "Command results appear in chat; all edits are still checked server-side.",
                layout.contentX(), layout.contentTop() + 126, 0xFF8593A1, false);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, int centerX, int y, int color) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private List<SsuMenuSnapshotPayload.LocationSummary> allLocations() {
        List<SsuMenuSnapshotPayload.LocationSummary> locations = new ArrayList<>();
        locations.addAll(snapshot.homes());
        locations.addAll(snapshot.warps());
        return locations;
    }

    private Layout layout() {
        int panelWidth = Math.max(1, Math.min(520, width - 8));
        int panelHeight = Math.max(1, Math.min(330, height - 8));
        int panelX = Math.max(4, (width - panelWidth) / 2);
        int panelY = Math.max(4, (height - panelHeight) / 2);
        boolean profileVisible = panelWidth >= 420 && panelHeight >= 235;
        int profileX = panelX + 10;
        int profileY = panelY + 42;
        int contentX = profileVisible ? profileX + PROFILE_WIDTH + 14 : panelX + 12;
        int contentWidth = Math.max(1, panelX + panelWidth - 12 - contentX);
        return new Layout(panelX, panelY, panelWidth, panelHeight, profileX, profileY,
                contentX, contentWidth, profileVisible);
    }

    private int rowsPerPage(Layout layout) {
        return Math.max(3, Math.min(7, (layout.footerY() - layout.listTop() - 4) / 25));
    }

    private Rect backBounds(Layout layout) {
        return new Rect(layout.panelX() + 12, layout.panelBottom() - 29, 54, 20);
    }

    private Rect settingsBounds(Layout layout) {
        int x = layout.panelRight() - 40;
        return new Rect(x, layout.panelY() + 7, 28, 28);
    }

    private Rect adminBounds(Layout layout) {
        int x = layout.panelRight() - (snapshot.settingsAvailable() ? 74 : 40);
        return new Rect(x, layout.panelY() + 7, 28, 28);
    }

    private static Identifier texture(String file) {
        return Identifier.fromNamespaceAndPath(
                SimpleServerUtilities.MODID,
                "textures/gui/dashboard/" + file
        );
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String friendlyEnum(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String[] parts = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private static String shortDimension(String dimension) {
        int separator = dimension.indexOf(':');
        return separator >= 0 ? dimension.substring(separator + 1) : dimension;
    }

    private static String formatPosition(SsuMenuSnapshotPayload.LocationSummary location) {
        return (int) Math.floor(location.x()) + ", " + (int) Math.floor(location.y()) + ", "
                + (int) Math.floor(location.z());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Page {
        HOME("Dashboard", "Your server tools and personal overview"),
        CLAIMS("Claims & Land", "Your claims, borders and map tools"),
        TRAVEL("Travel", "Homes and available server warps"),
        ECONOMY("Wallet & Economy", "Balance, payments and transaction history"),
        SETTINGS("Settings", "Personal dashboard and minimap preferences"),
        REGIONS("Regions & Rentals", "Rentable regions and server-region controls"),
        ADMIN("Admin Center", "Server administration and diagnostics"),
        PERMISSIONS("Players & Permissions", "Ranks and personal permission overrides"),
        CORE("Core Status", "Storage, scheduler and performance counters");

        private final String label;
        private final String subtitle;

        Page(String label, String subtitle) {
            this.label = label;
            this.subtitle = subtitle;
        }

        String label() {
            return label;
        }

        String subtitle() {
            return subtitle;
        }
    }

    private record Tile(Rect bounds, Identifier icon, String label, String hint, Page target, boolean enabled) {
    }

    private record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    private record Layout(
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int profileX,
            int profileY,
            int contentX,
            int contentWidth,
            boolean profileVisible
    ) {
        int panelRight() {
            return panelX + panelWidth;
        }

        int panelBottom() {
            return panelY + panelHeight;
        }

        int contentRight() {
            return contentX + contentWidth;
        }

        int contentTop() {
            return panelY + 48;
        }

        int listTop() {
            return contentTop() + 24;
        }

        int footerY() {
            return panelBottom() - 29;
        }
    }
}
