package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuPageDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuPageRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPlayerProfileDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPlayerProfileRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsRequestPayload;
import be.winnetrie.mod.simpleserverutilities.mixin.PlayerSkinWidgetAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Page-driven dashboard. The shell contains only compact status/counts; lists
 * are requested in bounded pages and every mutation uses a closed typed action.
 */
public final class SsuDashboardScreen extends Screen {

    private static final int PANEL = 0xF012171E;
    private static final int PANEL_BORDER = 0xFF52606D;
    private static final int CARD = 0xD01B232D;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFFA5B0BA;
    private static final int ACCENT = 0xFFFFD75A;
    private static final int GOOD = 0xFF84E39A;
    private static final int WARNING = 0xFFFFB86B;
    private static final int ERROR = 0xFFFF8080;
    private static final int PAGE_SIZE = 6;
    private static final int PERMISSION_PAGE_SIZE = 10;
    private static final int PERMISSION_ROW_HEIGHT = 23;
    private static final int PROFILE_PERMISSION_PAGE_SIZE = 8;
    private static final int PROFILE_PERMISSION_ROW_HEIGHT = 20;
    private static final int DROPDOWN_VISIBLE_ROWS = 8;
    private static final int TILE_SIZE = 54;
    private static final int TILE_LABEL_HEIGHT = 18;
    private static final Identifier BUTTON_TEXTURE = texture("button.png");
    private static final Identifier BUTTON_GLOW_TEXTURE = texture("button_glow.png");
    private static final Identifier BUTTON_BACK_TEXTURE = texture("button_back.png");
    private static final Identifier BUTTON_BACK_GLOW_TEXTURE = texture("button_back_glow.png");
    private static final Identifier PORTRAIT_FRAME = texture("portrait_framework.png");
    private static final Identifier ICON_CLAIM = texture("claim.png");
    private static final Identifier ICON_SETTINGS = texture("cogwheel.png");
    private static final Identifier ICON_MARKET = texture("market.png");
    private static final Identifier ICON_PLAYERS = texture("multiplayer.png");
    private static final Identifier ICON_PORTAL = texture("portal.png");
    private static final Identifier ICON_SHIELD = texture("shield.png");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());

    private SsuMenuSnapshotPayload snapshot;
    private SsuMenuPageDataPayload pageData = SsuMenuPageDataPayload.empty("", 0, PAGE_SIZE, 0, "", false);
    private Page page = Page.HOME;
    private Page previousPage = Page.HOME;
    private int pageIndex;
    private int selectedRow = -1;
    private long nextRequestId = 1L;
    private long latestPageRequest;
    private long latestActionRequest;
    private boolean loading;
    private String notice = "";
    private boolean noticeError;

    private PlayerSkinWidget skin;
    private EditBox searchBox;
    private EditBox payPlayerBox;
    private EditBox payAmountBox;
    private EditBox playerRefundBox;
    private EditBox adminRefundBox;
    private EditBox permissionTargetSearchBox;
    private EditBox permissionSearchBox;
    private EditBox playerProfileSearchBox;
    private final Map<String, EditBox> permissionValueInputs = new HashMap<>();
    private EditBox accountAmountBox;

    private String draftSearch = "";
    private String draftPayPlayer = "";
    private String draftPayAmount = "";
    private String draftPlayerRefund;
    private String draftAdminRefund;
    private SsuPermissionEditorDataPayload permissionData = SsuPermissionEditorDataPayload.empty(
            "player", 0L, "", false);
    private long latestPermissionRequest;
    private boolean permissionLoading;
    private String permissionMode = "player";
    private String selectedPermissionTarget = "";
    private String selectedPermissionLabel = "";
    private String selectedAssignableRank = "";
    private String draftPermissionTargetSearch = "";
    private String draftPermissionSearch = "";
    private boolean permissionModeDropdownOpen;
    private boolean permissionTargetDropdownOpen;
    private boolean permissionRankDropdownOpen;
    private int permissionDropdownScroll;
    private final Map<String, String> permissionDraftValues = new HashMap<>();
    private SsuPlayerProfileDataPayload playerProfileData = SsuPlayerProfileDataPayload.empty(0L, "", false);
    private long latestPlayerProfileRequest;
    private boolean playerProfileLoading;
    private String selectedProfilePlayer = "";
    private String selectedProfileLabel = "";
    private String draftPlayerProfileSearch = "";
    private boolean playerProfileDropdownOpen;
    private int playerProfileDropdownScroll;
    private int playerProfilePermissionPage;
    private String draftAccountAmount = "";
    private String pendingUnrentRegion = "";
    private SettingsCategory settingsCategory = SettingsCategory.GENERAL;

    public SsuDashboardScreen(SsuMenuSnapshotPayload snapshot) {
        super(Component.translatable("screen.simpleserverutilities.dashboard"));
        this.snapshot = snapshot;
        syncPolicyDrafts();
    }

    public void acceptSnapshot(SsuMenuSnapshotPayload updated) {
        if (updated == null) return;
        this.snapshot = updated;
        if (page != Page.ECONOMY || playerRefundBox == null) syncPolicyDrafts();
        rebuildWidgets();
        if (page.hasRemoteData() || page == Page.PLAYER_INFO) requestPage(false);
    }

    public void acceptPageData(SsuMenuPageDataPayload payload) {
        if (payload == null || payload.requestId() < latestPageRequest) return;
        if (!payload.page().equals(page.remoteId())) return;
        latestPageRequest = payload.requestId();
        pageData = payload;
        pageIndex = payload.pageIndex();
        selectedRow = -1;
        loading = false;
        if (!payload.notice().isBlank()) {
            notice = payload.notice();
            noticeError = payload.error();
        }
        rebuildWidgets();
    }

    public void acceptPermissionEditorData(SsuPermissionEditorDataPayload payload) {
        if (payload == null || payload.requestId() < latestPermissionRequest) return;
        if (page != Page.PERMISSIONS || !payload.mode().equals(permissionMode)) return;

        String previousTarget = selectedPermissionTarget;
        latestPermissionRequest = payload.requestId();
        permissionData = payload;
        permissionLoading = false;
        pageIndex = payload.pageIndex();
        selectedPermissionTarget = payload.selectedTarget();
        selectedPermissionLabel = payload.selectedLabel();
        if (!previousTarget.equals(selectedPermissionTarget)) {
            permissionDraftValues.clear();
        }
        for (SsuPermissionEditorDataPayload.PermissionEntry entry : payload.permissions()) {
            permissionDraftValues.putIfAbsent(entry.key(), entry.directValue().isBlank()
                    ? entry.effectiveValue() : entry.directValue());
        }
        if (!payload.notice().isBlank()) {
            notice = payload.notice();
            noticeError = payload.error();
        }
        rebuildWidgets();
    }

    public void acceptPlayerProfileData(SsuPlayerProfileDataPayload payload) {
        if (payload == null || payload.requestId() < latestPlayerProfileRequest) return;
        if (page != Page.PLAYER_INFO) return;
        latestPlayerProfileRequest = payload.requestId();
        playerProfileData = payload;
        playerProfileLoading = false;
        selectedProfilePlayer = payload.selectedPlayer();
        selectedProfileLabel = payload.selectedLabel();
        playerProfilePermissionPage = payload.permissionPageIndex();
        if (!payload.notice().isBlank()) {
            notice = payload.notice();
            noticeError = payload.error();
        }
        rebuildWidgets();
    }

    public void acceptActionResult(SsuMenuActionResultPayload result) {
        if (result == null || result.requestId() < latestActionRequest) return;
        latestActionRequest = result.requestId();
        notice = result.message();
        noticeError = !result.successful();
        if (!result.refreshPage().isBlank() && result.refreshPage().equals(page.remoteId())) {
            if (page == Page.PERMISSIONS) requestPermissionEditor(false);
            else requestPage(false);
        } else {
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        clearReferences();
        Layout l = layout();
        if (l.sidebarVisible() && minecraft.player != null) {
            skin = new PlayerSkinWidget(32, 48, minecraft.getEntityModels(), () -> minecraft.player.getSkin());
            // The transparent opening in portrait_framework.png is x=11..42, y=17..64.
            skin.setPosition(l.sidebarX() + 34, l.panelY() + 71);
            addRenderableWidget(skin);
        }

        switch (page) {
            case HOME -> addHomeButtons(l);
            case ADMIN -> addAdminButtons(l);
            case CLAIMS -> addClaimButtons(l);
            case TRAVEL -> addTravelButtons(l);
            case ECONOMY -> addEconomyButtons(l);
            case REGIONS -> addRegionButtons(l);
            case SETTINGS -> addSettingsButtons(l);
            case PERMISSIONS -> addPermissionButtons(l);
            case PLAYER_INFO -> addPlayerInfoButtons(l);
            case ACCOUNTS -> addAccountButtons(l);
            case JOBS -> addJobButtons(l);
            case RENT_OPERATIONS -> addRentOperationButtons(l);
            case CORE -> addCoreButtons(l);
            case PROFILE -> addProfileButtons(l);
            case MAIL -> { }
        }
    }

    private void clearReferences() {
        skin = null; searchBox = null; payPlayerBox = null; payAmountBox = null;
        playerRefundBox = null; adminRefundBox = null;
        permissionTargetSearchBox = null; permissionSearchBox = null; playerProfileSearchBox = null; permissionValueInputs.clear();
        accountAmountBox = null;
    }

    private void addHomeButtons(Layout l) {
        if (!useTexturedTiles(l)) addModuleGrid(l, homeModules());
    }

    private void addAdminButtons(Layout l) {
        if (!useTexturedTiles(l)) addModuleGrid(l, adminModules());
    }

    private List<Module> homeModules() {
        return List.of(
                new Module("Claims", "Your connected land claims and map tools.", ICON_CLAIM, Page.CLAIMS, true),
                new Module("Travel", "Homes and available server warps.", ICON_PORTAL, Page.TRAVEL, true),
                new Module("Wallet", "Balance, payments and transaction history.", ICON_MARKET, Page.ECONOMY, snapshot.economy().enabled()),
                new Module("Mail", "Inbox, sent mail, items and money attachments.", ICON_MARKET, Page.MAIL, true),
                new Module("Regions", "Server regions, borders and rentals.", ICON_CLAIM, Page.REGIONS,
                        snapshot.core().regionCount() > 0 || snapshot.administrator()),
                new Module("Profile", "Your rank, property and personal settings.", ICON_PLAYERS, Page.PROFILE, true)
        );
    }

    private List<Module> adminModules() {
        return List.of(
                new Module("Player info", "Inspect online and offline player profiles.", ICON_PLAYERS, Page.PLAYER_INFO, snapshot.administrator()),
                new Module("Permissions", "Inspect rank, player and dimension overrides.", ICON_PLAYERS, Page.PERMISSIONS, snapshot.adminAccess().permissions()),
                new Module("Accounts", "Search and adjust economy accounts.", ICON_MARKET, Page.ACCOUNTS, snapshot.economy().canAdmin()),
                new Module("Transactions", "Inspect complete transaction details.", ICON_MARKET, Page.ECONOMY, snapshot.economy().canAdmin()),
                new Module("Rent journal", "Inspect rent reconciliation records.", ICON_PORTAL, Page.RENT_OPERATIONS, snapshot.economy().canAdmin()),
                new Module("Active jobs", "View progress and cancel server jobs.", ICON_SETTINGS, Page.JOBS, snapshot.adminAccess().core()),
                new Module("Core status", "Storage, indexes and migrated modules.", ICON_SETTINGS, Page.CORE, snapshot.adminAccess().core()),
                new Module("Regions", "Open region and rental administration.", ICON_SHIELD, Page.REGIONS, true)
        );
    }

    private void addModuleGrid(Layout l, List<Module> modules) {
        int columns = l.contentWidth() >= 420 ? 3 : 2;
        int gap = 8;
        int w = (l.contentWidth() - gap * (columns - 1)) / columns;
        int startY = l.contentTop() + 35;
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i); int col = i % columns; int row = i / columns;
            Button button = Button.builder(Component.literal(m.label()), ignored -> openPage(m.page()))
                    .bounds(l.contentX() + col * (w + gap), startY + row * 32, w, 24).build();
            button.active = m.enabled(); addRenderableWidget(button);
        }
    }

    private void addListSearch(Layout l) {
        searchBox = box(l.contentX(), l.contentTop() + 24, Math.max(100, l.contentWidth() - 86), "Search", draftSearch,
                v -> draftSearch = v);
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), ignored -> { pageIndex = 0; requestPage(false); })
                .bounds(l.contentRight() - 80, l.contentTop() + 24, 80, 20).build());
    }

    private void addClaimButtons(Layout l) {
        addListSearch(l);
        List<SsuMenuPageDataPayload.ClaimEntry> values = pageData.claims();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i); int y = rowY(l, i); int row = i;
            int right = l.contentRight();
            addRenderableWidget(Button.builder(Component.literal("Details"), ignored -> select(row))
                    .bounds(right - 242, y, 56, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Settings"), ignored -> openPropertySettings("claim", entry.name()))
                    .bounds(right - 182, y, 66, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Map"), ignored -> action("claim_map", entry.name(), "", ""))
                    .bounds(right - 112, y, 52, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Show"), ignored -> action("claim_show", entry.name(), "", ""))
                    .bounds(right - 56, y, 56, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Open map"), ignored -> action("claim_map", "", "", ""))
                .bounds(l.contentX(), l.footerY(), 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Hide border"), ignored -> action("claim_hide", "", "", ""))
                .bounds(l.contentX() + 86, l.footerY(), 88, 20).build());
        addPagination(l, 180);
    }

    private void addTravelButtons(Layout l) {
        addListSearch(l);
        List<SsuMenuPageDataPayload.LocationEntry> values = pageData.locations();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i); int y = rowY(l, i);
            String actionName = switch (entry.kind()) {
                case "home" -> "teleport_home";
                case "spawn" -> "teleport_spawn";
                default -> "teleport_warp";
            };
            addRenderableWidget(Button.builder(Component.literal("Teleport"), ignored ->
                    action(actionName, entry.name(), "", ""))
                    .bounds(l.contentRight() - 74, y, 74, 20).build());
        }
        if (snapshot.adminAccess().spawn()) {
            addRenderableWidget(Button.builder(Component.literal("Set spawn here"), ignored ->
                            action("spawn_set", "", "", ""))
                    .bounds(l.contentX(), l.footerY(), 96, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Clear spawn"), ignored ->
                            action("spawn_clear", "", "", ""))
                    .bounds(l.contentX() + 100, l.footerY(), 84, 20).build());
            addPagination(l, 190);
        } else {
            addPagination(l, 0);
        }
    }

    private void addEconomyButtons(Layout l) {
        int top = l.contentTop() + 22;
        if (snapshot.economy().canPay()) {
            int targetW = Math.max(100, l.contentWidth() / 3);
            payPlayerBox = box(l.contentX(), top, targetW, "Player", draftPayPlayer, v -> draftPayPlayer = v);
            payAmountBox = box(l.contentX() + targetW + 6, top, 90, "Amount", draftPayAmount, v -> draftPayAmount = v);
            addRenderableWidget(payPlayerBox); addRenderableWidget(payAmountBox);
            addRenderableWidget(Button.builder(Component.literal("Pay"), ignored -> submitPayment())
                    .bounds(l.contentX() + targetW + 102, top, 56, 20).build());
        }
        boolean canEditRentPolicy = snapshot.economy().canAdmin() && snapshot.adminAccess().rentPolicy();
        if (canEditRentPolicy) {
            int y = top + 26; int w = 64;
            playerRefundBox = box(l.contentX(), y, w, "Player %", draftPlayerRefund, v -> draftPlayerRefund = v);
            adminRefundBox = box(l.contentX() + w + 6, y, w, "Admin %", draftAdminRefund, v -> draftAdminRefund = v);
            addRenderableWidget(playerRefundBox); addRenderableWidget(adminRefundBox);
            addRenderableWidget(Button.builder(Component.literal("Apply refund policy"), ignored -> submitPolicy())
                    .bounds(l.contentX() + (w + 6) * 2, y, 126, 20).build());
        }
        int listOffset = canEditRentPolicy ? 86 : 60;
        addSearchAt(l, l.contentTop() + listOffset);
        List<SsuMenuPageDataPayload.TransactionEntry> values = pageData.transactions();
        for (int i = 0; i < values.size(); i++) {
            int y = rowY(l, i, listOffset + 26); int row = i;
            addRenderableWidget(Button.builder(Component.literal("Details"), ignored -> select(row))
                    .bounds(l.contentRight() - 62, y, 62, 20).build());
        }
        addPagination(l, 0);
    }

    private void addSearchAt(Layout l, int y) {
        searchBox = box(l.contentX(), y, Math.max(100, l.contentWidth() - 86), "Search", draftSearch, v -> draftSearch = v);
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), ignored -> { pageIndex = 0; requestPage(false); })
                .bounds(l.contentRight() - 80, y, 80, 20).build());
    }

    private void addRegionButtons(Layout l) {
        addListSearch(l);
        List<SsuMenuPageDataPayload.RegionEntry> values = pageData.regions();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i); int y = rowY(l, i); int right = l.contentRight(); int row = i;
            addRenderableWidget(Button.builder(Component.literal("Details"), ignored -> select(row))
                    .bounds(right - 300, y, 56, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Settings"), ignored -> openPropertySettings("region", entry.name()))
                    .bounds(right - 240, y, 66, 20).build());
            addRenderableWidget(Button.builder(Component.literal(entry.visible() ? "Hide" : "Show"), ignored ->
                    action("region_visibility", entry.name(), "", Boolean.toString(!entry.visible())))
                    .bounds(right - 170, y, 54, 20).build());
            String label = entry.rentedByPlayer() ? (entry.periodText().equals("permanent") ? "Unrent" : "Extend")
                    : entry.rentable() && !entry.rented() ? "Rent" : "";
            if (!label.isBlank()) {
                String op = label.equals("Rent") ? "region_rent" : label.equals("Extend") ? "region_extend" : "region_unrent";
                String buttonLabel = op.equals("region_unrent") ? unrentLabel(entry.name()) : label;
                addRenderableWidget(Button.builder(Component.literal(buttonLabel), ignored -> {
                    if (op.equals("region_unrent")) requestUnrent(entry.name()); else action(op, entry.name(), "", "");
                }).bounds(right - 112, y, 54, 20).build());
                if (entry.rentedByPlayer() && label.equals("Extend")) {
                    addRenderableWidget(Button.builder(Component.literal(unrentLabel(entry.name())), ignored -> requestUnrent(entry.name()))
                            .bounds(right - 56, y, 56, 20).build());
                }
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Hide all"), ignored -> action("regions_hide", "", "", ""))
                .bounds(l.contentX(), l.footerY(), 72, 20).build());
        addPagination(l, 78);
    }

    private void addSettingsButtons(Layout l) {
        int categoryX = l.contentX();
        int categoryY = l.contentTop() + 4;
        int categoryWidth = Math.min(96, Math.max(78, l.contentWidth() / 4));
        for (SettingsCategory category : SettingsCategory.values()) {
            Button button = Button.builder(Component.literal(category.label), ignored -> {
                        settingsCategory = category;
                        rebuildWidgets();
                    }).bounds(categoryX, categoryY, categoryWidth, 20).build();
            button.active = settingsCategory != category;
            addRenderableWidget(button);
            categoryY += 24;
        }

        var s = snapshot.uiSettings();
        int x = categoryX + categoryWidth + 8;
        int y = l.contentTop() + 8;
        int available = Math.max(120, l.contentRight() - x);
        int gap = 6;
        int w = available >= 310 ? (available - gap) / 2 : available;
        int secondX = x + w + gap;
        boolean twoColumns = available >= 310;

        switch (settingsCategory) {
            case GENERAL -> addSetting(x, y, w, "Dashboard hints: " + onOff(s.dashboardHints()),
                    "hints", !s.dashboardHints());
            case MINIMAP -> {
                addSetting(x, y, w, "Minimap: " + onOff(s.minimapEnabled()), "minimap_enabled", !s.minimapEnabled());
                addSetting(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w,
                        "Size: " + s.minimapSize(), "minimap_size", s.minimapSize() >= 256 ? 64 : s.minimapSize() + 32);
                int row = twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "Shape: " + s.minimapShape(), "minimap_shape",
                        s.minimapShape().equals("CIRCLE") ? "RECTANGLE" : "CIRCLE");
                String nextPos = switch (s.minimapPosition()) {
                    case "TOP_LEFT" -> "TOP_RIGHT"; case "TOP_RIGHT" -> "BOTTOM_RIGHT";
                    case "BOTTOM_RIGHT" -> "BOTTOM_LEFT"; default -> "TOP_LEFT";
                };
                addSetting(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "Position: " + s.minimapPosition(), "minimap_position", nextPos);
                row += twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "North-up: " + onOff(s.minimapNorthUp()),
                        "minimap_northup", !s.minimapNorthUp());
                addSetting(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "Claim overlay: " + onOff(s.minimapShowClaims()), "minimap_claims", !s.minimapShowClaims());
                row += twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "Region overlay: " + onOff(s.minimapShowRegions()),
                        "minimap_regions", !s.minimapShowRegions());
            }
            case WORLD_MAP -> {
                addSetting(x, y, w, "Claim overlay: " + onOff(s.worldMapShowClaims()),
                        "worldmap_claims", !s.worldMapShowClaims());
                addSetting(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w,
                        "Region overlay: " + onOff(s.worldMapShowRegions()),
                        "worldmap_regions", !s.worldMapShowRegions());
            }
            case BORDERS -> {
                Button claims = Button.builder(Component.literal("Claim borders: " + onOff(snapshot.claimBordersVisible())), ignored ->
                                action("border", "claims", "", Boolean.toString(!snapshot.claimBordersVisible())))
                        .bounds(x, y, w, 20).build();
                claims.active = snapshot.canViewClaimBorders();
                addRenderableWidget(claims);
                Button regions = Button.builder(Component.literal("Region borders: " + onOff(snapshot.regionBordersVisible())), ignored ->
                                action("border", "regions", "", Boolean.toString(!snapshot.regionBordersVisible())))
                        .bounds(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w, 20).build();
                regions.active = snapshot.canViewRegionBorders();
                addRenderableWidget(regions);
                Button clearPins = Button.builder(Component.literal("Clear selected region borders"), ignored ->
                                action("border_regions_clear_pins", "", "", ""))
                        .bounds(x, y + (twoColumns ? 27 : 54), w, 20).build();
                clearPins.active = snapshot.canViewRegionBorders();
                addRenderableWidget(clearPins);
            }
            case MAIL -> {
                addSetting(x, y, w, "Private attachment mail: " + onOff(s.mailAutoDeletePlayerAttachments()),
                        "mail_auto_delete_player", !s.mailAutoDeletePlayerAttachments());
                addSetting(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w,
                        "Server attachment mail: " + onOff(s.mailAutoDeleteSystemAttachments()),
                        "mail_auto_delete_system", !s.mailAutoDeleteSystemAttachments());
                addSetting(x, y + (twoColumns ? 27 : 54), w,
                        "Auction attachment mail: " + onOff(s.mailAutoDeleteAuctionAttachments()),
                        "mail_auto_delete_auction", !s.mailAutoDeleteAuctionAttachments());
            }
        }
    }

    private void addSetting(int x, int y, int w, String label, String key, Object value) {
        addRenderableWidget(Button.builder(Component.literal(label), ignored -> action("setting", key, "", String.valueOf(value)))
                .bounds(x, y, w, 20).build());
    }

    private void addPermissionButtons(Layout l) {
        int y = l.contentTop() + 4;
        Rect mode = permissionModeBounds(l);
        Rect target = permissionTargetBounds(l);
        addRenderableWidget(Button.builder(Component.literal("Filter: " + permissionModeLabel() + " ▾"),
                        ignored -> togglePermissionDropdown("mode"))
                .bounds(mode.x(), mode.y(), mode.width(), mode.height()).build());
        addRenderableWidget(Button.builder(Component.literal((selectedPermissionLabel.isBlank()
                        ? permissionTargetPrompt() : selectedPermissionLabel) + " ▾"),
                        ignored -> togglePermissionDropdown("target"))
                .bounds(target.x(), target.y(), target.width(), target.height()).build());

        permissionTargetSearchBox = box(l.contentX(), y + 24, 166, "Filter " + permissionModeLabel().toLowerCase(java.util.Locale.ROOT),
                draftPermissionTargetSearch, value -> draftPermissionTargetSearch = value);
        addRenderableWidget(permissionTargetSearchBox);
        addRenderableWidget(Button.builder(Component.literal("Filter"), ignored -> {
                    permissionDropdownScroll = 0;
                    requestPermissionEditor(false);
                }).bounds(l.contentX() + 170, y + 24, 52, 20).build());

        if (permissionMode.equals("player") && !selectedPermissionTarget.isBlank()) {
            Rect rank = permissionRankBounds(l);
            addRenderableWidget(Button.builder(Component.literal((selectedAssignableRank.isBlank()
                            ? "Assign rank" : selectedAssignableRank) + " ▾"),
                            ignored -> togglePermissionDropdown("rank"))
                    .bounds(rank.x(), rank.y(), rank.width(), rank.height()).build());
            Button assign = Button.builder(Component.literal("Assign"), ignored -> {
                        if (selectedAssignableRank.isBlank()) {
                            setNotice("Choose a rank first.", true);
                        } else {
                            action("permission_assign_rank", selectedPermissionTarget, "", selectedAssignableRank);
                        }
                    }).bounds(l.contentRight() - 58, y + 24, 58, 20).build();
            assign.active = !selectedAssignableRank.isBlank();
            addRenderableWidget(assign);
        }

        permissionSearchBox = box(l.contentX(), y + 48, Math.max(110, l.contentWidth() - 66),
                "Search permission", draftPermissionSearch, value -> draftPermissionSearch = value);
        addRenderableWidget(permissionSearchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), ignored -> requestPermissionEditor(true))
                .bounds(l.contentRight() - 60, y + 48, 60, 20).build());

        int listTop = permissionListTop(l);
        List<SsuPermissionEditorDataPayload.PermissionEntry> entries = permissionData.permissions();
        for (int i = 0; i < entries.size(); i++) {
            SsuPermissionEditorDataPayload.PermissionEntry entry = entries.get(i);
            int rowY = listTop + i * PERMISSION_ROW_HEIGHT;
            int resetX = l.contentRight() - 22;
            if ("boolean".equals(entry.valueType())) {
                String label = permissionBooleanLabel(entry);
                addRenderableWidget(Button.builder(Component.literal(label), ignored -> toggleBooleanPermission(entry))
                        .bounds(l.contentRight() - 128, rowY + 4, 102, 20).build());
            } else {
                EditBox value = box(l.contentRight() - 168, rowY + 4, 102,
                        "Value", permissionDraftValues.getOrDefault(entry.key(), ""),
                        text -> permissionDraftValues.put(entry.key(), text));
                if ("integer".equals(entry.valueType())) value.setMaxLength(12);
                addRenderableWidget(value);
                permissionValueInputs.put(entry.key(), value);
                addRenderableWidget(Button.builder(Component.literal("Set"), ignored -> setPermissionValue(entry,
                                permissionDraftValues.getOrDefault(entry.key(), "")))
                        .bounds(l.contentRight() - 62, rowY + 4, 36, 20).build());
            }
            Button reset = Button.builder(Component.literal("×"), ignored -> unsetPermissionValue(entry))
                    .bounds(resetX, rowY + 4, 22, 20).build();
            reset.active = !entry.directValue().isBlank();
            addRenderableWidget(reset);
        }
        addPermissionPagination(l);
    }

    private void togglePermissionDropdown(String dropdown) {
        boolean modeOpen = "mode".equals(dropdown) && !permissionModeDropdownOpen;
        boolean targetOpen = "target".equals(dropdown) && !permissionTargetDropdownOpen;
        boolean rankOpen = "rank".equals(dropdown) && !permissionRankDropdownOpen;
        permissionModeDropdownOpen = modeOpen;
        permissionTargetDropdownOpen = targetOpen;
        permissionRankDropdownOpen = rankOpen;
        permissionDropdownScroll = 0;
    }

    private String permissionModeLabel() {
        return switch (permissionMode) {
            case "rank" -> "Ranks";
            case "dimension" -> "Dimensions";
            default -> "Players";
        };
    }

    private String permissionTargetPrompt() {
        return switch (permissionMode) {
            case "rank" -> "Choose rank";
            case "dimension" -> "Choose dimension";
            default -> "Choose player";
        };
    }

    private String permissionBooleanLabel(SsuPermissionEditorDataPayload.PermissionEntry entry) {
        if (entry.directValue().isBlank()) {
            if (entry.effectiveValue().isBlank()) return "Default";
            return "Inherited: " + onOff(Boolean.parseBoolean(entry.effectiveValue()));
        }
        return onOff(Boolean.parseBoolean(entry.directValue()));
    }

    private void toggleBooleanPermission(SsuPermissionEditorDataPayload.PermissionEntry entry) {
        boolean current = !entry.directValue().isBlank()
                ? Boolean.parseBoolean(entry.directValue())
                : Boolean.parseBoolean(entry.effectiveValue());
        setPermissionValue(entry, Boolean.toString(!current));
    }

    private void setPermissionValue(SsuPermissionEditorDataPayload.PermissionEntry entry, String value) {
        if (selectedPermissionTarget.isBlank()) {
            setNotice("Choose a permission target first.", true);
            return;
        }
        String action = switch (permissionMode) {
            case "rank" -> "permission_rank_set";
            case "dimension" -> "permission_dimension_set";
            default -> "permission_player_set";
        };
        action(action, selectedPermissionTarget, entry.key(), value);
    }

    private void unsetPermissionValue(SsuPermissionEditorDataPayload.PermissionEntry entry) {
        if (selectedPermissionTarget.isBlank()) return;
        permissionDraftValues.remove(entry.key());
        String action = switch (permissionMode) {
            case "rank" -> "permission_rank_unset";
            case "dimension" -> "permission_dimension_unset";
            default -> "permission_player_unset";
        };
        action(action, selectedPermissionTarget, entry.key(), "");
    }

    private void addPermissionPagination(Layout l) {
        int pageSize = Math.max(1, permissionData.pageSize());
        int pages = Math.max(1, (permissionData.totalPermissions() + pageSize - 1) / pageSize);
        Button previous = Button.builder(Component.literal("<"), ignored -> {
                    if (pageIndex > 0) {
                        pageIndex--;
                        requestPermissionEditor(false);
                    }
                }).bounds(l.contentX(), l.footerY(), 24, 20).build();
        previous.active = pageIndex > 0;
        addRenderableWidget(previous);
        addRenderableWidget(Button.builder(Component.literal((pageIndex + 1) + " / " + pages), ignored -> {})
                .bounds(l.contentX() + 28, l.footerY(), 64, 20).build());
        Button next = Button.builder(Component.literal(">"), ignored -> {
                    if (pageIndex + 1 < pages) {
                        pageIndex++;
                        requestPermissionEditor(false);
                    }
                }).bounds(l.contentX() + 96, l.footerY(), 24, 20).build();
        next.active = pageIndex + 1 < pages;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestPermissionEditor(false))
                .bounds(l.contentRight() - 68, l.footerY(), 68, 20).build());
    }

    private int permissionListTop(Layout l) {
        return l.contentTop() + 90;
    }

    private int permissionPageSize() {
        Layout l = layout();
        int available = Math.max(PERMISSION_ROW_HEIGHT, l.footerY() - permissionListTop(l) - 2);
        return Math.max(1, Math.min(PERMISSION_PAGE_SIZE, available / PERMISSION_ROW_HEIGHT));
    }

    private Rect permissionModeBounds(Layout l) {
        return new Rect(l.contentX(), l.contentTop() + 4, 108, 20);
    }

    private Rect permissionTargetBounds(Layout l) {
        return new Rect(l.contentX() + 114, l.contentTop() + 4, l.contentWidth() - 114, 20);
    }

    private Rect permissionRankBounds(Layout l) {
        return new Rect(l.contentRight() - 214, l.contentTop() + 28, 150, 20);
    }

    private void addPlayerInfoButtons(Layout l) {
        Rect target = playerProfileTargetBounds(l);
        addRenderableWidget(Button.builder(Component.literal((selectedProfileLabel.isBlank()
                        ? "Choose player" : selectedProfileLabel) + " ▾"),
                        ignored -> {
                            playerProfileDropdownOpen = !playerProfileDropdownOpen;
                            playerProfileDropdownScroll = 0;
                        })
                .bounds(target.x(), target.y(), target.width(), target.height()).build());

        playerProfileSearchBox = box(l.contentX(), l.contentTop() + 28,
                Math.max(110, l.contentWidth() - 66), "Search player name",
                draftPlayerProfileSearch, value -> draftPlayerProfileSearch = value);
        addRenderableWidget(playerProfileSearchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), ignored -> {
                    playerProfileDropdownScroll = 0;
                    playerProfilePermissionPage = 0;
                    selectedProfilePlayer = "";
                    selectedProfileLabel = "";
                    requestPlayerProfile(false);
                }).bounds(l.contentRight() - 60, l.contentTop() + 28, 60, 20).build());

        if (!selectedProfilePlayer.isBlank()) {
            if (snapshot.adminAccess().permissions()) {
                addRenderableWidget(Button.builder(Component.literal("Edit permissions"), ignored ->
                                openPermissionEditorForPlayer(selectedProfilePlayer, selectedProfileLabel))
                        .bounds(l.contentRight() - 104, l.footerY(), 104, 20).build());
            }
            addPlayerProfilePagination(l);
        }
    }

    private void addPlayerProfilePagination(Layout l) {
        int pageSize = Math.max(1, playerProfileData.permissionPageSize());
        int pages = Math.max(1, (playerProfileData.totalPermissions() + pageSize - 1) / pageSize);
        Button previous = Button.builder(Component.literal("<"), ignored -> {
                    if (playerProfilePermissionPage > 0) {
                        playerProfilePermissionPage--;
                        requestPlayerProfile(false);
                    }
                }).bounds(l.contentX(), l.footerY(), 24, 20).build();
        previous.active = playerProfilePermissionPage > 0;
        addRenderableWidget(previous);
        addRenderableWidget(Button.builder(Component.literal((playerProfilePermissionPage + 1) + " / " + pages), ignored -> {})
                .bounds(l.contentX() + 28, l.footerY(), 64, 20).build());
        Button next = Button.builder(Component.literal(">"), ignored -> {
                    if (playerProfilePermissionPage + 1 < pages) {
                        playerProfilePermissionPage++;
                        requestPlayerProfile(false);
                    }
                }).bounds(l.contentX() + 96, l.footerY(), 24, 20).build();
        next.active = playerProfilePermissionPage + 1 < pages;
        addRenderableWidget(next);
    }

    private void openPermissionEditorForPlayer(String playerId, String playerLabel) {
        previousPage = Page.PLAYER_INFO;
        page = Page.PERMISSIONS;
        permissionMode = "player";
        selectedPermissionTarget = playerId;
        selectedPermissionLabel = playerLabel;
        selectedAssignableRank = "";
        draftPermissionTargetSearch = playerLabel;
        draftPermissionSearch = "";
        permissionDraftValues.clear();
        pageIndex = 0;
        playerProfileDropdownOpen = false;
        rebuildWidgets();
        requestPermissionEditor(true);
    }

    private int playerProfilePermissionPageSize() {
        Layout l = layout();
        return l.contentWidth() >= 430 ? PROFILE_PERMISSION_PAGE_SIZE : 4;
    }

    private Rect playerProfileTargetBounds(Layout l) {
        return new Rect(l.contentX(), l.contentTop() + 4, l.contentWidth(), 20);
    }

    private void addAccountButtons(Layout l) {
        addListSearch(l);
        accountAmountBox = box(l.contentX(), l.contentTop() + 49, 100, "Amount", draftAccountAmount,
                v -> draftAccountAmount = v);
        addRenderableWidget(accountAmountBox);
        List<SsuMenuPageDataPayload.AccountEntry> values = pageData.accounts();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i); int y = rowY(l, i, 76); int right = l.contentRight();
            addRenderableWidget(Button.builder(Component.literal("Give"), ignored -> accountAction("economy_give", entry.id()))
                    .bounds(right - 170, y, 52, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Take"), ignored -> accountAction("economy_take", entry.id()))
                    .bounds(right - 114, y, 52, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Set"), ignored -> accountAction("economy_set", entry.id()))
                    .bounds(right - 58, y, 58, 20).build());
        }
        addPagination(l, 106);
    }
    private void addRentOperationButtons(Layout l) { addListSearch(l); for(int i=0;i<pageData.rentOperations().size();i++){
        int row=i; addRenderableWidget(Button.builder(Component.literal("Details"), ignored -> select(row))
                .bounds(l.contentRight()-62,rowY(l,i),62,20).build()); } addPagination(l,0); }
    private void addJobButtons(Layout l) { addListSearch(l); for(int i=0;i<pageData.jobs().size();i++){
        var entry=pageData.jobs().get(i); addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> action("job_cancel",entry.id(),"",""))
                .bounds(l.contentRight()-58,rowY(l,i),58,20).build()); } addPagination(l,0); }
    private void addCoreButtons(Layout l) {
        addRenderableWidget(Button.builder(Component.literal("Refresh shell"), ignored -> action("refresh_shell", "", "", ""))
                .bounds(l.contentX(), l.contentTop() + 122, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset counters"), ignored -> action("core_reset", "", "", ""))
                .bounds(l.contentX() + 106, l.contentTop() + 122, 100, 20).build());
    }
    private void addProfileButtons(Layout l) { }

    private void addPagination(Layout l, int offset) {
        int pages = (int) Math.max(1L, ((long) pageData.totalItems() + PAGE_SIZE - 1L) / PAGE_SIZE);
        Button prev = Button.builder(Component.literal("<"), ignored -> { if(pageIndex>0){pageIndex--;requestPage(false);} })
                .bounds(l.contentX() + offset, l.footerY(), 24, 20).build(); prev.active = pageIndex > 0; addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), ignored -> { if(pageIndex+1<pages){pageIndex++;requestPage(false);} })
                .bounds(l.contentX() + offset + 60, l.footerY(), 24, 20).build(); next.active = pageIndex + 1 < pages; addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestPage(false))
                .bounds(l.contentRight() - 68, l.footerY(), 68, 20).build());
    }

    private EditBox box(int x, int y, int w, String hint, String value, Consumer<String> responder) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setMaxLength(128); box.setValue(value == null ? "" : value); box.setResponder(responder); return box;
    }

    private void accountAction(String action, String accountId) {
        if (draftAccountAmount.isBlank()) {
            setNotice("Enter an amount first.", true);
            return;
        }
        action(action, accountId, "", draftAccountAmount);
    }

    private void submitPayment() {
        if (draftPayPlayer.isBlank() || draftPayAmount.isBlank()) { setNotice("Player and amount are required.", true); return; }
        action("pay", draftPayPlayer, "", draftPayAmount); draftPayAmount = "";
    }
    private void submitPolicy() { action("rent_policy", draftPlayerRefund, "", draftAdminRefund); }
    private void select(int row) { selectedRow = selectedRow == row ? -1 : row; rebuildWidgets(); }

    private String unrentLabel(String region) {
        return pendingUnrentRegion.equalsIgnoreCase(region) ? "Confirm" : "Unrent";
    }

    private void requestUnrent(String region) {
        if (pendingUnrentRegion.equalsIgnoreCase(region)) {
            pendingUnrentRegion = "";
            action("region_unrent", region, "", "");
            return;
        }
        pendingUnrentRegion = region;
        setNotice("Click Confirm again to cancel rental '" + region + "'. A configured reset/refund may run.", true);
    }

    private void openPropertySettings(String kind, String target) {
        ClientPacketDistributor.sendToServer(new SsuPropertySettingsRequestPayload(
                kind, target, nextRequestId++));
    }

    private void action(String action, String target, String secondary, String value) {
        long id = nextRequestId++;
        latestActionRequest = id;
        ClientPacketDistributor.sendToServer(new SsuMenuActionPayload(action, target, secondary, value, id));
    }

    private void requestPage(boolean reset) {
        if (page == Page.PERMISSIONS) {
            requestPermissionEditor(reset);
            return;
        }
        if (page == Page.PLAYER_INFO) {
            requestPlayerProfile(reset);
            return;
        }
        if (!page.hasRemoteData()) return;
        if (reset) pageIndex = 0;
        loading = true;
        long id = nextRequestId++;
        latestPageRequest = id;
        ClientPacketDistributor.sendToServer(new SsuMenuPageRequestPayload(
                page.remoteId(), pageIndex, PAGE_SIZE, draftSearch, id));
    }

    private void requestPlayerProfile(boolean reset) {
        if (page != Page.PLAYER_INFO) return;
        if (reset) playerProfilePermissionPage = 0;
        playerProfileLoading = true;
        long id = nextRequestId++;
        latestPlayerProfileRequest = id;
        ClientPacketDistributor.sendToServer(new SsuPlayerProfileRequestPayload(
                selectedProfilePlayer,
                draftPlayerProfileSearch,
                playerProfilePermissionPage,
                playerProfilePermissionPageSize(),
                id
        ));
    }

    private void requestPermissionEditor(boolean reset) {
        if (page != Page.PERMISSIONS) return;
        if (reset) pageIndex = 0;
        permissionLoading = true;
        long id = nextRequestId++;
        latestPermissionRequest = id;
        ClientPacketDistributor.sendToServer(new SsuPermissionEditorRequestPayload(
                permissionMode,
                selectedPermissionTarget,
                draftPermissionTargetSearch,
                draftPermissionSearch,
                pageIndex,
                permissionPageSize(),
                id
        ));
    }

    private void openPage(Page target) {
        if (target == Page.MAIL) {
            ClientPacketDistributor.sendToServer(new MailActionPayload("open_mailbox", "", "inbox", 0, nextRequestId++));
            return;
        }
        if (target == page) return;
        previousPage = page; page = target; pageIndex = 0; selectedRow = -1; draftSearch = ""; pendingUnrentRegion = "";
        loading = false;
        if (target != Page.PERMISSIONS) permissionLoading = false;
        if (target != Page.PLAYER_INFO) playerProfileLoading = false;
        closePermissionDropdowns();
        closePlayerProfileDropdown();
        pageData = SsuMenuPageDataPayload.empty(target.remoteId(), 0, PAGE_SIZE, 0, "", false);
        rebuildWidgets(); requestPage(false);
    }
    private void goBack() {
        if (page == Page.HOME) { onClose(); return; }
        page = previousPage == page ? Page.HOME : previousPage; previousPage = Page.HOME;
        pageIndex = 0; selectedRow = -1; draftSearch = ""; pendingUnrentRegion = "";
        loading = false;
        if (page != Page.PERMISSIONS) permissionLoading = false;
        if (page != Page.PLAYER_INFO) playerProfileLoading = false;
        closePermissionDropdowns(); closePlayerProfileDropdown(); rebuildWidgets(); requestPage(false);
    }
    private void closePlayerProfileDropdown() {
        playerProfileDropdownOpen = false;
        playerProfileDropdownScroll = 0;
    }

    private void closePermissionDropdowns() {
        permissionModeDropdownOpen = false;
        permissionTargetDropdownOpen = false;
        permissionRankDropdownOpen = false;
        permissionDropdownScroll = 0;
    }

    private void setNotice(String text, boolean error) { notice = text; noticeError = error; rebuildWidgets(); }
    private void syncPolicyDrafts() {
        draftPlayerRefund = Integer.toString(snapshot.economy().playerCancelRefundPercent());
        draftAdminRefund = Integer.toString(snapshot.economy().adminCancelRefundPercent());
    }

    private boolean handlePermissionDropdownClick(double mouseX, double mouseY) {
        if (page != Page.PERMISSIONS
                || (!permissionModeDropdownOpen && !permissionTargetDropdownOpen && !permissionRankDropdownOpen)) {
            return false;
        }
        Layout l = layout();
        if (permissionModeBounds(l).contains(mouseX, mouseY)
                || permissionTargetBounds(l).contains(mouseX, mouseY)
                || permissionRankBounds(l).contains(mouseX, mouseY)) {
            return false;
        }

        if (permissionModeDropdownOpen) {
            Rect list = dropdownListBounds(permissionModeBounds(l), 3);
            if (list.contains(mouseX, mouseY)) {
                int index = (int) ((mouseY - list.y()) / 20.0);
                String nextMode = switch (index) {
                    case 1 -> "rank";
                    case 2 -> "dimension";
                    default -> "player";
                };
                if (!nextMode.equals(permissionMode)) {
                    permissionMode = nextMode;
                    selectedPermissionTarget = "";
                    selectedPermissionLabel = "";
                    selectedAssignableRank = "";
                    permissionDraftValues.clear();
                    pageIndex = 0;
                }
                closePermissionDropdowns();
                requestPermissionEditor(true);
                rebuildWidgets();
                playClick();
                return true;
            }
        } else if (permissionTargetDropdownOpen) {
            List<SsuPermissionEditorDataPayload.TargetEntry> options = visiblePermissionTargets();
            Rect list = dropdownListBounds(permissionTargetBounds(l), Math.max(1, options.size()));
            if (list.contains(mouseX, mouseY) && !options.isEmpty()) {
                int index = (int) ((mouseY - list.y()) / 20.0);
                if (index >= 0 && index < options.size()) {
                    SsuPermissionEditorDataPayload.TargetEntry selected = options.get(index);
                    selectedPermissionTarget = selected.id();
                    selectedPermissionLabel = selected.label();
                    selectedAssignableRank = "";
                    permissionDraftValues.clear();
                    pageIndex = 0;
                    closePermissionDropdowns();
                    requestPermissionEditor(true);
                    rebuildWidgets();
                    playClick();
                    return true;
                }
            }
        } else if (permissionRankDropdownOpen) {
            List<String> options = visiblePermissionRanks();
            Rect list = dropdownListBounds(permissionRankBounds(l), Math.max(1, options.size()));
            if (list.contains(mouseX, mouseY) && !options.isEmpty()) {
                int index = (int) ((mouseY - list.y()) / 20.0);
                if (index >= 0 && index < options.size()) {
                    selectedAssignableRank = options.get(index);
                    closePermissionDropdowns();
                    rebuildWidgets();
                    playClick();
                    return true;
                }
            }
        }
        closePermissionDropdowns();
        return false;
    }

    private boolean handlePlayerProfileDropdownClick(double mouseX, double mouseY) {
        if (page != Page.PLAYER_INFO || !playerProfileDropdownOpen) return false;
        Layout l = layout();
        Rect anchor = playerProfileTargetBounds(l);
        if (anchor.contains(mouseX, mouseY)) return false;
        List<SsuPlayerProfileDataPayload.PlayerEntry> options = visiblePlayerProfileTargets();
        Rect list = dropdownListBounds(anchor, Math.max(1, options.size()));
        if (list.contains(mouseX, mouseY) && !options.isEmpty()) {
            int index = (int) ((mouseY - list.y()) / 20.0);
            if (index >= 0 && index < options.size()) {
                SsuPlayerProfileDataPayload.PlayerEntry selected = options.get(index);
                selectedProfilePlayer = selected.id();
                selectedProfileLabel = selected.label();
                playerProfilePermissionPage = 0;
                closePlayerProfileDropdown();
                requestPlayerProfile(true);
                rebuildWidgets();
                playClick();
                return true;
            }
        }
        closePlayerProfileDropdown();
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == Page.PLAYER_INFO && playerProfileDropdownOpen && scrollY != 0.0) {
            int maximum = Math.max(0, playerProfileData.players().size() - DROPDOWN_VISIBLE_ROWS);
            int next = playerProfileDropdownScroll + (scrollY < 0.0 ? 1 : -1);
            playerProfileDropdownScroll = Math.max(0, Math.min(maximum, next));
            return true;
        }
        if (page == Page.PERMISSIONS && scrollY != 0.0
                && (permissionTargetDropdownOpen || permissionRankDropdownOpen)) {
            int size = permissionTargetDropdownOpen
                    ? permissionData.targets().size() : permissionData.rankOptions().size();
            int maximum = Math.max(0, size - DROPDOWN_VISIBLE_ROWS);
            int next = permissionDropdownScroll + (scrollY < 0.0 ? 1 : -1);
            permissionDropdownScroll = Math.max(0, Math.min(maximum, next));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.buttonInfo().button() == 0
                && (handlePermissionDropdownClick(event.x(), event.y())
                || handlePlayerProfileDropdownClick(event.x(), event.y()))) return true;
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.buttonInfo().button() != 0) return false;

        Layout l = layout();
        if (closeBounds(l).contains(event.x(), event.y())) {
            playClick();
            onClose();
            return true;
        }
        if (snapshot.settingsAvailable() && settingsBounds(l).contains(event.x(), event.y())) {
            playClick();
            openPage(Page.SETTINGS);
            return true;
        }
        if (snapshot.administrator() && adminBounds(l).contains(event.x(), event.y())) {
            playClick();
            openPage(Page.ADMIN);
            return true;
        }
        if ((l.sidebarVisible() || page != Page.HOME) && backBounds(l).contains(event.x(), event.y())) {
            playClick();
            goBack();
            return true;
        }
        if (useTexturedTiles(l)) {
            for (ModuleTile tile : moduleTiles(l)) {
                if (tile.module().enabled() && tile.bounds().contains(event.x(), event.y())) {
                    playClick();
                    openPage(tile.module().page());
                    return true;
                }
            }
        }
        return false;
    }

    private void playClick() {
        AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        Layout l = layout();
        updatePortraitRotation(l, mouseX, mouseY);
        g.fill(0,0,width,height,0xA5000000); g.fill(l.panelX(),l.panelY(),l.panelRight(),l.panelBottom(),PANEL);
        g.outline(l.panelX(),l.panelY(),l.panelWidth(),l.panelHeight(),PANEL_BORDER);
        if (l.sidebarVisible()) g.text(font,"Simple Server Utilities",l.panelX()+11,l.panelY()+11,ACCENT,true);
        int headerX = l.sidebarVisible() ? l.contentX() : l.panelX() + (page == Page.HOME ? 12 : 72);
        g.text(font,page.label(),headerX,l.panelY()+11,TEXT,true);
        g.text(font,page.subtitle(),headerX,l.panelY()+26,MUTED,false);
        drawSidebar(g,l); drawPage(g,l,mouseX,mouseY);
        if (!notice.isBlank()) g.text(font,trim(notice,90),l.contentX(),l.panelBottom()-43,noticeError?ERROR:GOOD,false);
        if (loading || (page == Page.PERMISSIONS && permissionLoading)
                || (page == Page.PLAYER_INFO && playerProfileLoading)) {
            g.text(font,"Loading page…",l.contentRight()-85,l.panelY()+26,WARNING,false);
        }
        super.extractRenderState(g,mouseX,mouseY,partialTick);
        if (skin != null && l.sidebarVisible()) {
            g.blit(RenderPipelines.GUI_TEXTURED, PORTRAIT_FRAME, l.sidebarX() + 23, l.panelY() + 54,
                    0, 0, 54, 78, 54, 78);
        }
        drawCloseButton(g, closeBounds(l), mouseX, mouseY);
        drawUtilityButton(g, settingsBounds(l), ICON_SETTINGS, page == Page.SETTINGS,
                mouseX, mouseY, snapshot.settingsAvailable());
        drawUtilityButton(g, adminBounds(l), ICON_SHIELD,
                page == Page.ADMIN || page == Page.PERMISSIONS || page == Page.PLAYER_INFO,
                mouseX, mouseY, snapshot.administrator());
        if (l.sidebarVisible() || page != Page.HOME) drawBackButton(g, l, mouseX, mouseY);
        if (page == Page.PERMISSIONS) {
            drawPermissionDropdowns(g, l, mouseX, mouseY);
            drawPermissionTooltip(g, l, mouseX, mouseY);
        }
        if (page == Page.PLAYER_INFO) {
            drawPlayerProfileDropdown(g, l, mouseX, mouseY);
        }
    }


    private void updatePortraitRotation(Layout l, int mouseX, int mouseY) {
        if (skin == null || !l.sidebarVisible()) return;
        float centerX = l.sidebarX() + 50.0F;
        float centerY = l.panelY() + 95.0F;
        float yaw = clamp((mouseX - centerX) * 0.55F, -32.0F, 32.0F);
        float pitch = clamp((centerY - mouseY) * 0.32F, -18.0F, 18.0F);
        PlayerSkinWidgetAccessor accessor = (PlayerSkinWidgetAccessor) (Object) skin;
        accessor.ssu$setRotationX(pitch);
        accessor.ssu$setRotationY(yaw);
    }

    private void drawPermissionDropdowns(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY) {
        if (!permissionModeDropdownOpen && !permissionTargetDropdownOpen && !permissionRankDropdownOpen) return;
        g.nextStratum();
        if (permissionModeDropdownOpen) {
            drawDropdown(g, permissionModeBounds(l), List.of("Players", "Ranks", "Dimensions"), mouseX, mouseY);
        } else if (permissionTargetDropdownOpen) {
            List<SsuPermissionEditorDataPayload.TargetEntry> targets = visiblePermissionTargets();
            List<String> labels = targets.stream().map(target -> target.label()
                    + (target.summary().isBlank() ? "" : " — " + target.summary())).toList();
            drawDropdown(g, permissionTargetBounds(l), labels, mouseX, mouseY);
        } else if (permissionRankDropdownOpen) {
            drawDropdown(g, permissionRankBounds(l), visiblePermissionRanks(), mouseX, mouseY);
        }
    }

    private void drawPlayerProfileDropdown(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY) {
        if (!playerProfileDropdownOpen) return;
        g.nextStratum();
        Rect anchor = playerProfileTargetBounds(l);
        List<SsuPlayerProfileDataPayload.PlayerEntry> options = visiblePlayerProfileTargets();
        Rect list = dropdownListBounds(anchor, Math.max(1, options.size()));
        g.fill(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFC151C24);
        g.outline(list.x(), list.y(), list.width(), list.height(), ACCENT);
        if (options.isEmpty()) {
            g.text(font, "No players match this search", list.x() + 5, list.y() + 6, MUTED, false);
            return;
        }
        for (int i = 0; i < options.size(); i++) {
            SsuPlayerProfileDataPayload.PlayerEntry option = options.get(i);
            int y = list.y() + i * 20;
            if (mouseX >= list.x() && mouseX < list.x() + list.width()
                    && mouseY >= y && mouseY < y + 20) {
                g.fill(list.x() + 1, y + 1, list.x() + list.width() - 1, y + 20, 0xD03A4D5C);
            }
            String label = option.label() + " — " + option.summary();
            g.text(font, trim(label, Math.max(10, (list.width() - 10) / 6)),
                    list.x() + 5, y + 6, option.online() ? GOOD : TEXT, false);
        }
        int fullSize = playerProfileData.players().size();
        if (fullSize > DROPDOWN_VISIBLE_ROWS) {
            g.text(font, (playerProfileDropdownScroll + 1) + "–"
                            + Math.min(fullSize, playerProfileDropdownScroll + options.size()) + " / " + fullSize,
                    list.x() + list.width() - 62, list.y() + list.height() - 12, MUTED, false);
        }
    }

    private void drawDropdown(GuiGraphicsExtractor g, Rect anchor, List<String> options, int mouseX, int mouseY) {
        int rows = Math.max(1, options.size());
        Rect list = dropdownListBounds(anchor, rows);
        g.fill(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFC151C24);
        g.outline(list.x(), list.y(), list.width(), list.height(), ACCENT);
        if (options.isEmpty()) {
            g.text(font, "No matches", list.x() + 5, list.y() + 6, MUTED, false);
            return;
        }
        for (int i = 0; i < options.size(); i++) {
            int y = list.y() + i * 20;
            if (mouseX >= list.x() && mouseX < list.x() + list.width()
                    && mouseY >= y && mouseY < y + 20) {
                g.fill(list.x() + 1, y + 1, list.x() + list.width() - 1, y + 20, 0xD03A4D5C);
            }
            g.text(font, trim(options.get(i), Math.max(10, (list.width() - 10) / 6)),
                    list.x() + 5, y + 6, TEXT, false);
        }
        int fullSize = permissionTargetDropdownOpen ? permissionData.targets().size()
                : permissionRankDropdownOpen ? permissionData.rankOptions().size() : options.size();
        if (fullSize > DROPDOWN_VISIBLE_ROWS) {
            g.text(font, (permissionDropdownScroll + 1) + "–"
                            + Math.min(fullSize, permissionDropdownScroll + options.size()) + " / " + fullSize,
                    list.x() + list.width() - 58, list.y() + list.height() - 12, MUTED, false);
        }
    }

    private void drawPermissionTooltip(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY) {
        if (permissionModeDropdownOpen || permissionTargetDropdownOpen || permissionRankDropdownOpen) return;
        for (int i = 0; i < permissionData.permissions().size(); i++) {
            if (!permissionRowBounds(l, i).contains(mouseX, mouseY)) continue;
            SsuPermissionEditorDataPayload.PermissionEntry entry = permissionData.permissions().get(i);
            String valueInfo = "Type: " + entry.valueType();
            if ("integer".equals(entry.valueType())) {
                valueInfo += " (" + entry.minimum() + "–" + entry.maximum() + ")";
            }
            String current = entry.directValue().isBlank()
                    ? "Effective: " + blank(entry.effectiveValue()) + " (" + entry.source() + ")"
                    : "Override: " + entry.directValue() + " | effective: " + blank(entry.effectiveValue());
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.literal(entry.description()),
                    Component.literal(valueInfo),
                    Component.literal("Default: " + blank(entry.defaultValue())),
                    Component.literal(current)
            ), mouseX, mouseY);
            return;
        }
    }

    private Rect permissionRowBounds(Layout l, int index) {
        return new Rect(l.contentX(), permissionListTop(l) + index * PERMISSION_ROW_HEIGHT,
                l.contentWidth(), PERMISSION_ROW_HEIGHT - 2);
    }

    private Rect dropdownListBounds(Rect anchor, int rowCount) {
        int rows = Math.max(1, Math.min(DROPDOWN_VISIBLE_ROWS, rowCount));
        return new Rect(anchor.x(), anchor.y() + anchor.height(), anchor.width(), rows * 20);
    }

    private List<SsuPermissionEditorDataPayload.TargetEntry> visiblePermissionTargets() {
        return slice(permissionData.targets(), permissionDropdownScroll, DROPDOWN_VISIBLE_ROWS);
    }

    private List<String> visiblePermissionRanks() {
        return slice(permissionData.rankOptions(), permissionDropdownScroll, DROPDOWN_VISIBLE_ROWS);
    }

    private List<SsuPlayerProfileDataPayload.PlayerEntry> visiblePlayerProfileTargets() {
        return slice(playerProfileData.players(), playerProfileDropdownScroll, DROPDOWN_VISIBLE_ROWS);
    }

    private static <T> List<T> slice(List<T> values, int from, int maximum) {
        if (values == null || values.isEmpty()) return List.of();
        int start = Math.max(0, Math.min(from, values.size()));
        int end = Math.min(values.size(), start + maximum);
        return List.copyOf(values.subList(start, end));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void drawCloseButton(GuiGraphicsExtractor g, Rect bounds, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        g.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                hovered ? 0xD25B2D35 : CARD);
        g.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), hovered ? 0xFFFF7A86 : PANEL_BORDER);
        String cross = "×";
        g.text(font, cross, bounds.x() + (bounds.width() - font.width(cross)) / 2,
                bounds.y() + 9, hovered ? 0xFFFFFFFF : TEXT, true);
    }

    private void drawUtilityButton(GuiGraphicsExtractor g, Rect bounds, Identifier icon, boolean selected,
                                   int mouseX, int mouseY, boolean visible) {
        if (!visible) return;
        boolean hovered = bounds.contains(mouseX, mouseY);
        g.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                selected ? 0xD03A4D5C : hovered ? 0xD22B3946 : CARD);
        g.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), hovered || selected ? ACCENT : PANEL_BORDER);
        int iconWidth = 16;
        int iconHeight = icon.equals(ICON_SHIELD) ? 19 : 16;
        g.blit(RenderPipelines.GUI_TEXTURED, icon,
                bounds.x() + (bounds.width() - iconWidth) / 2,
                bounds.y() + (bounds.height() - iconHeight) / 2,
                0, 0, iconWidth, iconHeight, iconWidth, iconHeight);
    }

    private void drawBackButton(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY) {
        Rect bounds = backBounds(l);
        boolean hovered = bounds.contains(mouseX, mouseY);
        g.blit(RenderPipelines.GUI_TEXTURED, BUTTON_BACK_TEXTURE,
                bounds.x(), bounds.y(), 0, 0, 54, 20, 54, 20);
        if (hovered) {
            g.blit(RenderPipelines.GUI_TEXTURED, BUTTON_BACK_GLOW_TEXTURE,
                    bounds.x(), bounds.y(), 0, 0, 54, 20, 54, 20);
        }
        if (l.sidebarVisible()) {
            g.text(font, page == Page.HOME ? "Close" : "Back", bounds.x() + 60, bounds.y() + 6, MUTED, false);
        }
    }

    private Rect closeBounds(Layout l) { return new Rect(l.panelRight() - 40, l.panelY() + 7, 28, 28); }
    private Rect settingsBounds(Layout l) { return new Rect(l.panelRight() - 74, l.panelY() + 7, 28, 28); }
    private Rect adminBounds(Layout l) {
        return new Rect(l.panelRight() - (snapshot.settingsAvailable() ? 108 : 74), l.panelY() + 7, 28, 28);
    }
    private Rect backBounds(Layout l) {
        return l.sidebarVisible()
                ? new Rect(l.panelX() + 12, l.panelBottom() - 29, 54, 20)
                : new Rect(l.panelX() + 10, l.panelY() + 7, 54, 20);
    }

    private void drawSidebar(GuiGraphicsExtractor g, Layout l) {
        if (!l.sidebarVisible()) return;
        g.fill(l.sidebarX(),l.panelY()+42,l.sidebarX()+100,l.panelBottom()-36,CARD);
        g.outline(l.sidebarX(),l.panelY()+42,100,l.panelBottom()-78,PANEL_BORDER);
        int y=l.panelY()+138;
        center(g,snapshot.playerName(),l.sidebarX()+50,y,TEXT);
        center(g,"Rank: "+(snapshot.primaryRank().isBlank()?"default":snapshot.primaryRank()),l.sidebarX()+50,y+14,MUTED);
        center(g,snapshot.economy().formattedBalance(),l.sidebarX()+50,y+31,snapshot.economy().enabled()?GOOD:MUTED);
        g.text(font,"Claims: "+snapshot.core().claimCount(),l.sidebarX()+8,y+54,MUTED,false);
        g.text(font,"Homes: "+snapshot.core().homeCount(),l.sidebarX()+8,y+68,MUTED,false);
        g.text(font,"Rentals: "+snapshot.core().activeRentalCount(),l.sidebarX()+8,y+82,MUTED,false);
    }

    private void drawPage(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY) {
        switch(page) {
            case HOME -> drawModuleTiles(g, l, mouseX, mouseY,
                    "Choose a player module",
                    snapshot.core().claimCount()+" claim(s), "+snapshot.core().activeRentalCount()+" active rental(s)");
            case ADMIN -> drawModuleTiles(g, l, mouseX, mouseY,
                    "Paged administration tools",
                    "All changes use typed, server-validated actions.");
            case CLAIMS -> drawClaims(g,l); case TRAVEL -> drawTravel(g,l); case ECONOMY -> drawEconomy(g,l);
            case REGIONS -> drawRegions(g,l); case SETTINGS -> g.text(font,"Personal settings are persisted by the server.",l.contentX(),l.contentTop(),MUTED,false);
            case PERMISSIONS -> drawPermissions(g,l,mouseX,mouseY); case PLAYER_INFO -> drawPlayerInfo(g,l);
            case ACCOUNTS -> drawAccounts(g,l); case JOBS -> drawJobs(g,l);
            case RENT_OPERATIONS -> drawRentOps(g,l); case CORE -> drawCore(g,l); case PROFILE -> drawProfile(g,l);
            case MAIL -> g.text(font,"Opening mailbox…",l.contentX(),l.contentTop(),MUTED,false);
        }
    }

    private void drawModuleTiles(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY, String heading, String subheading) {
        g.text(font, heading, l.contentX(), l.contentTop(), MUTED, false);
        g.text(font, subheading, l.contentX(), l.contentTop() + 15, MUTED, false);
        if (!useTexturedTiles(l)) return;
        for (ModuleTile tile : moduleTiles(l)) {
            Rect b = tile.bounds();
            boolean hovered = tile.module().enabled() && b.contains(mouseX, mouseY);
            g.blit(RenderPipelines.GUI_TEXTURED, BUTTON_TEXTURE, b.x(), b.y(), 0, 0,
                    TILE_SIZE, TILE_SIZE, TILE_SIZE, TILE_SIZE);
            if (hovered) g.blit(RenderPipelines.GUI_TEXTURED, BUTTON_GLOW_TEXTURE, b.x(), b.y(), 0, 0,
                    TILE_SIZE, TILE_SIZE, TILE_SIZE, TILE_SIZE);
            if (!tile.module().enabled()) g.fill(b.x(), b.y(), b.x() + TILE_SIZE, b.y() + TILE_SIZE, 0x85000000);
            int iw = tile.module().icon().equals(ICON_PLAYERS) ? 32 : 16;
            int ih = tile.module().icon().equals(ICON_SHIELD) ? 19 : iw;
            g.blit(RenderPipelines.GUI_TEXTURED, tile.module().icon(), b.x() + (TILE_SIZE - iw) / 2,
                    b.y() + (TILE_SIZE - ih) / 2, 0, 0, iw, ih, iw, ih);
            center(g, tile.module().label(), b.x() + TILE_SIZE / 2, b.y() + TILE_SIZE + 4,
                    tile.module().enabled() ? TEXT : MUTED);
            if (hovered && snapshot.uiSettings().dashboardHints()) {
                g.text(font, trim(tile.module().hint(), 72), l.contentX(), l.footerY() - 16, MUTED, false);
            }
        }
    }

    private boolean useTexturedTiles(Layout l) {
        return (page == Page.HOME || page == Page.ADMIN) && l.contentWidth() >= 330 && l.panelHeight() >= 320;
    }

    private List<ModuleTile> moduleTiles(Layout l) {
        if (page != Page.HOME && page != Page.ADMIN) return List.of();
        List<Module> modules = page == Page.HOME ? homeModules() : adminModules();
        int columns = l.contentWidth() >= 500 ? 4 : 3;
        int gapX = Math.max(10, (l.contentWidth() - columns * TILE_SIZE) / Math.max(1, columns - 1));
        int startX = l.contentX() + Math.max(0, (l.contentWidth() - (columns * TILE_SIZE + gapX * (columns - 1))) / 2);
        int startY = l.contentTop() + 38;
        int rowStep = TILE_SIZE + TILE_LABEL_HEIGHT + 10;
        java.util.ArrayList<ModuleTile> result = new java.util.ArrayList<>(modules.size());
        for (int i = 0; i < modules.size(); i++) {
            int col = i % columns; int row = i / columns;
            result.add(new ModuleTile(new Rect(startX + col * (TILE_SIZE + gapX), startY + row * rowStep,
                    TILE_SIZE, TILE_SIZE + TILE_LABEL_HEIGHT), modules.get(i)));
        }
        return List.copyOf(result);
    }

    private void drawClaims(GuiGraphicsExtractor g,Layout l){if(pageData.claims().isEmpty())empty(g,l,"No claims on this page.");
        for(int i=0;i<pageData.claims().size();i++){var e=pageData.claims().get(i);int y=rowTextY(l,i);
            g.text(font,e.name(),l.contentX(),y,TEXT,false);g.text(font,e.chunkCount()+" chunks | "+shortDim(e.dimension()),l.contentX()+100,y,MUTED,false);}
        if(selectedRow>=0&&selectedRow<pageData.claims().size()){var e=pageData.claims().get(selectedRow);
            detail(g,l,"Claim details",List.of("ID: "+e.id(),"Trusted: "+(e.trustedPlayers().isBlank()?e.trustedCount():e.trustedPlayers()),
                    "Spawn: "+(e.hasSpawn()?e.spawn():"none"),"Flags: "+e.flags()));}}
    private void drawTravel(GuiGraphicsExtractor g,Layout l){if(pageData.locations().isEmpty())empty(g,l,"No server spawn, homes or warps on this page.");
        for(int i=0;i<pageData.locations().size();i++){var e=pageData.locations().get(i);int y=rowTextY(l,i);
            g.text(font,cap(e.kind())+": "+e.name(),l.contentX(),y,TEXT,false);g.text(font,shortDim(e.dimension())+" | "+pos(e.x(),e.y(),e.z()),l.contentX()+130,y,MUTED,false);}}
    private void drawEconomy(GuiGraphicsExtractor g,Layout l){g.text(font,"Balance: "+snapshot.economy().formattedBalance(),l.contentX(),l.contentTop(),GOOD,false);
        int offset=snapshot.economy().canAdmin()&&snapshot.adminAccess().rentPolicy()?112:86;for(int i=0;i<pageData.transactions().size();i++){var e=pageData.transactions().get(i);int y=rowTextY(l,i,offset);
            g.text(font,e.type()+"  "+e.formattedAmount(),l.contentX(),y,TEXT,false);g.text(font,e.status(),l.contentX()+170,y,MUTED,false);}
        if(selectedRow>=0&&selectedRow<pageData.transactions().size()){var e=pageData.transactions().get(selectedRow);detail(g,l,"Transaction",
                List.of("ID: "+e.id(),"From: "+blank(e.source()),"To: "+blank(e.destination()),"Actor: "+blank(e.actor()),"Reason: "+blank(e.reason()),"Failure: "+blank(e.failure())));}}
    private void drawRegions(GuiGraphicsExtractor g,Layout l){if(pageData.regions().isEmpty())empty(g,l,"No regions on this page.");
        for(int i=0;i<pageData.regions().size();i++){var e=pageData.regions().get(i);int y=rowTextY(l,i);g.text(font,e.name(),l.contentX(),y,e.rentedByPlayer()?GOOD:TEXT,false);
            String state=e.rentedByPlayer()?"yours | "+e.remainingText():e.rented()?"rented":e.rentable()?e.formattedPrice()+" / "+e.periodText():"not rentable";
            g.text(font,state,l.contentX()+90,y,MUTED,false);}
        if(selectedRow>=0&&selectedRow<pageData.regions().size()){var e=pageData.regions().get(selectedRow);detail(g,l,"Region details",
                List.of(e.bounds(),"Managers: "+(e.managers().isBlank()?e.managerCount():e.managers()),
                        "Members: "+(e.members().isBlank()?e.memberCount():e.members()),"Flags: "+e.flags(),
                        "Rent: "+e.rentPolicy(),"Priority: "+e.priority()+" | Volume: "+e.volume(),
                        "Spawn: "+(e.hasSpawn()?e.spawn():"none")+" | Snapshot: "+yesNo(e.snapshotAvailable()),
                        "Active job lock: "+yesNo(e.jobLocked())));}}
    private void drawPlayerInfo(GuiGraphicsExtractor g, Layout l) {
        if (!playerProfileData.profile().selected()) {
            g.text(font, "Choose a player from the dropdown or enter an exact name.",
                    l.contentX(), l.contentTop() + 58, MUTED, false);
            return;
        }

        SsuPlayerProfileDataPayload.Profile profile = playerProfileData.profile();
        int top = l.contentTop() + 58;
        int split = l.contentWidth() >= 430 ? l.contentX() + Math.min(250, l.contentWidth() / 2) : l.contentRight();
        int permissionX = split < l.contentRight() ? split + 10 : l.contentX();
        int permissionTop = split < l.contentRight() ? top : top + 150;

        g.text(font, profile.name() + (profile.online() ? "  • online" : "  • offline"),
                l.contentX(), top, profile.online() ? GOOD : ACCENT, true);
        List<String> details = List.of(
                "UUID: " + profile.playerId(),
                "Primary rank: " + blank(profile.primaryRank()),
                "Assigned ranks: " + blank(profile.assignedRanks()),
                "Access: " + blank(profile.adminStatus()),
                "Balance: " + blank(profile.formattedBalance()),
                "Claims: " + profile.claimGroups() + " group(s), " + profile.claimChunks() + " chunk(s)",
                "Homes: " + profile.homes() + " | Rentals: " + profile.rentals(),
                "Rented regions: " + blank(profile.rentalNames()),
                "Dimension: " + blank(profile.dimension()),
                "Position: " + blank(profile.position()),
                "Health: " + blank(profile.healthAndFood()),
                "Personal overrides: " + profile.directOverrides()
        );
        int detailLimit = split < l.contentRight() ? details.size() : Math.min(8, details.size());
        for (int i = 0; i < detailLimit; i++) {
            int maxChars = split < l.contentRight() ? 40 : 74;
            g.text(font, trim(details.get(i), maxChars), l.contentX(), top + 16 + i * 13,
                    i == 0 ? MUTED : TEXT, false);
        }

        g.text(font, "Effective permissions", permissionX, permissionTop, ACCENT, true);
        if (playerProfileData.permissions().isEmpty()) {
            g.text(font, "No permission data available.", permissionX, permissionTop + 16, MUTED, false);
            return;
        }
        for (int i = 0; i < playerProfileData.permissions().size(); i++) {
            SsuPlayerProfileDataPayload.PermissionLine line = playerProfileData.permissions().get(i);
            int y = permissionTop + 16 + i * PROFILE_PERMISSION_ROW_HEIGHT;
            g.text(font, trim(line.key() + " = " + line.value(), split < l.contentRight() ? 44 : 70),
                    permissionX, y, TEXT, false);
            g.text(font, trim(line.source(), 24), permissionX, y + 10, MUTED, false);
        }
    }

    private void drawPermissions(GuiGraphicsExtractor g, Layout l, int mouseX, int mouseY) {
        if (selectedPermissionTarget.isBlank()) {
            g.text(font, "Choose a player, rank or dimension from the lists above.", l.contentX(), permissionListTop(l) + 8, MUTED, false);
            return;
        }
        g.text(font, trim(permissionData.selectedLabel() + " — " + permissionData.targetSummary(), 76),
                l.contentX(), l.contentTop() + 76, ACCENT, false);
        if (permissionData.permissions().isEmpty()) {
            g.text(font, "No permissions match this filter.", l.contentX(), permissionListTop(l) + 8, MUTED, false);
            return;
        }
        for (int i = 0; i < permissionData.permissions().size(); i++) {
            SsuPermissionEditorDataPayload.PermissionEntry entry = permissionData.permissions().get(i);
            int y = permissionListTop(l) + i * PERMISSION_ROW_HEIGHT;
            boolean hovered = permissionRowBounds(l, i).contains(mouseX, mouseY);
            if (hovered) g.fill(l.contentX(), y, l.contentRight(), y + PERMISSION_ROW_HEIGHT - 2, 0x662C3946);
            g.text(font, trim(entry.key(), 58), l.contentX() + 4, y + 7,
                    entry.directValue().isBlank() ? TEXT : GOOD, false);
        }
    }
    private void drawAccounts(GuiGraphicsExtractor g,Layout l){if(pageData.accounts().isEmpty())empty(g,l,"No economy accounts on this page.");
        g.text(font,"Admin amount",l.contentX()+106,l.contentTop()+55,MUTED,false);
        for(int i=0;i<pageData.accounts().size();i++){var e=pageData.accounts().get(i);int y=rowTextY(l,i,76);g.text(font,blank(e.name()),l.contentX(),y,TEXT,false);
            g.text(font,e.formattedBalance()+" | rev "+e.revision(),l.contentX()+130,y,MUTED,false);}}
    private void drawJobs(GuiGraphicsExtractor g,Layout l){if(pageData.jobs().isEmpty())empty(g,l,"No active jobs.");for(int i=0;i<pageData.jobs().size();i++){var e=pageData.jobs().get(i);int y=rowTextY(l,i);
        String progress=e.progress()<0?"unknown":String.format(Locale.ROOT,"%.1f%%",e.progress()*100);g.text(font,trim(e.description(),40),l.contentX(),y,TEXT,false);g.text(font,progress+" | "+e.operations()+" ops",l.contentX()+220,y,MUTED,false);}}
    private void drawRentOps(GuiGraphicsExtractor g,Layout l){if(pageData.rentOperations().isEmpty())empty(g,l,"No rent journal records.");for(int i=0;i<pageData.rentOperations().size();i++){var e=pageData.rentOperations().get(i);int y=rowTextY(l,i);
        g.text(font,e.region()+" | "+e.action(),l.contentX(),y,TEXT,false);g.text(font,e.status(),l.contentX()+170,y,MUTED,false);}
        if(selectedRow>=0&&selectedRow<pageData.rentOperations().size()){var e=pageData.rentOperations().get(selectedRow);detail(g,l,"Rent operation",
                List.of("ID: "+e.id(),"Renter: "+blank(e.renter()),"Gross: "+e.grossAmount(),"Refund: "+e.refundAmount(),"Updated: "+time(e.updatedAt()),"Error: "+blank(e.error())));}}
    private void drawCore(GuiGraphicsExtractor g,Layout l){var c=snapshot.core();int y=l.contentTop()+8;
        String[] lines={"Active jobs: "+snapshot.activeJobs(),"Pending storage writes: "+snapshot.pendingStorageWrites(),
                "Permission checks: "+c.permissionChecks()+" | cache "+String.format(Locale.ROOT,"%.1f%%",c.permissionCacheHitPermille()/10D),
                "Region lookups: "+c.regionLookups()+" | avg candidates "+String.format(Locale.ROOT,"%.2f",c.averageRegionCandidates()),
                "Region index: "+c.regionIndexCells()+" cells | "+c.regionIndexReferences()+" refs",
                "Modules: storage, jobs, transactions, economy, claims, permissions, homes, warps, spawn, regions, menu"};for(int i=0;i<lines.length;i++)g.text(font,lines[i],l.contentX(),y+i*18,i==5?GOOD:TEXT,false);}
    private void drawProfile(GuiGraphicsExtractor g,Layout l){var c=snapshot.core();int y=l.contentTop()+10;
        String[] lines={"Player: "+snapshot.playerName(),"Primary rank: "+snapshot.primaryRank(),"Administrator: "+yesNo(snapshot.administrator()),
                "Claims: "+c.claimCount()+" ("+c.claimedChunkCount()+" chunks)","Homes: "+c.homeCount(),"Warps: "+c.warpCount(),
                "Active rentals: "+c.activeRentalCount(),"Balance: "+snapshot.economy().formattedBalance(),"Minimap: "+onOff(snapshot.uiSettings().minimapEnabled())};
        for(int i=0;i<lines.length;i++)g.text(font,lines[i],l.contentX(),y+i*18,i==0?ACCENT:TEXT,false);}

    private void detail(GuiGraphicsExtractor g,Layout l,String title,List<String> lines){int w=Math.min(360,l.contentWidth());int x=l.contentRight()-w;int bottom=l.panelBottom()-36;int y=Math.max(l.contentTop()+20,bottom-158);
        g.fill(x,y,l.contentRight(),bottom,0xF0202832);g.outline(x,y,w,bottom-y,ACCENT);g.text(font,title,x+7,y+7,ACCENT,true);
        int visible=Math.max(1,Math.min(7,(bottom-y-28)/13));
        for(int i=0;i<Math.min(visible,lines.size());i++)g.text(font,trim(lines.get(i),55),x+7,y+23+i*13,MUTED,false);}
    private void empty(GuiGraphicsExtractor g,Layout l,String text){g.text(font,text,l.contentX(),l.contentTop()+62,MUTED,false);}

    private int rowY(Layout l,int i){return rowY(l,i,58);} private int rowY(Layout l,int i,int offset){return l.contentTop()+offset+i*27;}
    private int rowTextY(Layout l,int i){return rowY(l,i)+6;} private int rowTextY(Layout l,int i,int offset){return rowY(l,i,offset)+6;}
    private Layout layout(){int pw=Math.max(360,Math.min(680,width-8));int ph=Math.max(250,Math.min(390,height-8));int px=(width-pw)/2;int py=(height-ph)/2;
        boolean side=pw>=540;int sx=px+10;int cx=side?sx+112:px+12;int cw=px+pw-12-cx;return new Layout(px,py,pw,ph,sx,cx,cw,side);}
    private void center(GuiGraphicsExtractor g,String s,int x,int y,int color){String v=blank(s);g.text(font,v,x-font.width(v)/2,y,color,false);}
    private static Identifier texture(String file) {
        return Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "textures/gui/dashboard/" + file);
    }
    private static String onOff(boolean v){return v?"ON":"OFF";} private static String yesNo(boolean v){return v?"Yes":"No";}
    private static String blank(String v){return v==null||v.isBlank()?"-":v;} private static String cap(String v){return v==null||v.isBlank()?"":Character.toUpperCase(v.charAt(0))+v.substring(1);}
    private static String shortDim(String v){int i=v.indexOf(':');return i>=0?v.substring(i+1):v;}
    private static String pos(double x,double y,double z){return (int)Math.floor(x)+", "+(int)Math.floor(y)+", "+(int)Math.floor(z);}
    private static String trim(String v,int max){if(v==null)return "";return v.length()<=max?v:v.substring(0,Math.max(0,max-1))+"…";}
    private static String time(long epoch){return epoch<=0?"-":TIME.format(Instant.ofEpochMilli(epoch));}
    @Override public boolean isPauseScreen(){return false;}

    private enum SettingsCategory {
        GENERAL("General"), MINIMAP("Minimap"), WORLD_MAP("World map"), BORDERS("Borders"), MAIL("Mail");
        private final String label;
        SettingsCategory(String label) { this.label = label; }
    }

    private enum Page {
        HOME("Dashboard","Player tools and personal overview",""), PROFILE("Profile","Your server account and property",""),
        MAIL("Mail","Inbox, attachments and sent mail",""),
        CLAIMS("Claims & Land","Owned claims and claim tools","claims"), TRAVEL("Travel","Server spawn, homes and warps","travel"),
        ECONOMY("Wallet & Transactions","Payments and paged transaction history","transactions"),
        REGIONS("Regions & Rentals","Rentals, region details and visibility","regions"), SETTINGS("Settings","Categorised personal settings for every SSU module",""),
        ADMIN("Admin Center","Paged administrative tools",""), PLAYER_INFO("Player Info & Profile","Admin player browser and effective permissions",""),
        PERMISSIONS("Permissions","Rank, player and dimension overrides","permissions"),
        ACCOUNTS("Economy Accounts","Searchable account browser","accounts"),
        JOBS("Active Jobs","Scheduler progress and cancellation","jobs"),
        RENT_OPERATIONS("Rent Journal","Rental reconciliation operations","rent_operations"), CORE("Core Status","Storage, indexing and module migration"," ");
        private final String label,subtitle,remote;Page(String l,String s,String r){label=l;subtitle=s;remote=r.trim();}
        String label(){return label;}String subtitle(){return subtitle;}String remoteId(){return remote;}boolean hasRemoteData(){return !remote.isBlank();}
    }
    private record Module(String label, String hint, Identifier icon, Page page, boolean enabled){}
    private record ModuleTile(Rect bounds, Module module){}
    private record Rect(int x, int y, int width, int height) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    private record Layout(int panelX,int panelY,int panelWidth,int panelHeight,int sidebarX,int contentX,int contentWidth,boolean sidebarVisible){
        int panelRight(){return panelX+panelWidth;}int panelBottom(){return panelY+panelHeight;}int contentRight(){return contentX+contentWidth;}
        int contentTop(){return panelY+47;}int footerY(){return panelBottom()-29;}}
}
