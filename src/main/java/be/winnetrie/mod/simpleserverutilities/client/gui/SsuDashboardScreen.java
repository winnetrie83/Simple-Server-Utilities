package be.winnetrie.mod.simpleserverutilities.client.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.settings.MinecraftColorPalette;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuDimensionManagerRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestBookRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.TitleManagerRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.RankDisplayRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.DungeonLobbyRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuPageDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuPageRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPlayerProfileDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPlayerProfileRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.PlayerManagementRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.PlayerUiSettingUpdatePayload;
import be.winnetrie.mod.simpleserverutilities.network.KitRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MineRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.OnboardingAdminRequestPayload;
import be.winnetrie.mod.simpleserverutilities.time.GameCalendar;
import be.winnetrie.mod.simpleserverutilities.mixin.PlayerSkinWidgetAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Page-driven dashboard. The shell contains only compact status/counts; lists
 * are requested in bounded pages and every mutation uses a closed typed action.
 */
public final class SsuDashboardScreen extends Screen implements SsuFixedLogicalCanvas {
    private static String pendingHomesClaimName = "";

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
    private static final ResourceLocation BUTTON_TEXTURE = texture("button.png");
    private static final ResourceLocation BUTTON_HOVER_TEXTURE = texture("button_hover.png");
    private static final ResourceLocation BUTTON_BACK_TEXTURE = texture("button_back.png");
    private static final ResourceLocation BUTTON_BACK_HOVER_TEXTURE = texture("button_back_hover.png");
    private static final ResourceLocation PORTRAIT_FRAME = texture("portrait_framework.png");
    private static final ResourceLocation ICON_CLAIM = texture("claim_land.png");
    private static final ResourceLocation ICON_TRAVEL = texture("travel.png");
    private static final ResourceLocation ICON_WALLET = texture("wallet.png");
    private static final ResourceLocation ICON_MAIL = texture("mail.png");
    private static final ResourceLocation ICON_MINIGAMES = texture("games.png");
    private static final ResourceLocation ICON_ACHIEVEMENTS = texture("achievements.png");
    private static final ResourceLocation ICON_COSMETICS = texture("cosmetics.png");
    private static final ResourceLocation ICON_QUESTBOOK = texture("questbook.png");
    private static final ResourceLocation ICON_SETTINGS = texture("cogwheel.png");
    private static final ResourceLocation ICON_MARKET = texture("market.png");
    private static final ResourceLocation ICON_PLAYERS = texture("multiplayer.png");
    private static final ResourceLocation ICON_PORTAL = texture("portal.png");
    private static final ResourceLocation ICON_SHIELD = texture("shield.png");
    private static final ResourceLocation ICON_TICKET = texture("ticket.png");
    private static final ResourceLocation ICON_KITS = texture("kits.png");
    private static final ResourceLocation ICON_MINES = texture("mines.png");
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
    private EditBox economyHistoryLimitBox;
    private EditBox transactionPlayerBox;
    private EditBox auctionTaxBox;
    private EditBox permissionTargetSearchBox;
    private EditBox permissionSearchBox;
    private EditBox playerProfileSearchBox;
    private final Map<String, EditBox> permissionValueInputs = new HashMap<>();
    private EditBox accountAmountBox;
    private EditBox homeNameBox;
    private EditBox warpNameBox;
    private EditBox warpRentalPriceBox;
    private EditBox warpRentalDaysBox;
    private EditBox claimTaxRateBox;
    private EditBox claimTaxIntervalBox;
    private EditBox claimTaxReminderBox;
    private EditBox claimTaxDimensionBox;
    private EditBox claimTaxMultiplierBox;
    private EditBox rankNameBox;
    private EditBox rankRenameBox;
    private EditBox rankPlayerBox;
    private EditBox rankPriorityBox;
    private EditBox rankInheritanceBox;
    private EditBox regionPlayerRefundBox;
    private EditBox regionAdminRefundBox;
    private EditBox regionDaysBox;
    private EditBox regionFillBox;
    private EditBox regionCoordinatesBox;
    private EditBox miningLeafRangeBox;
    private EditBox miningTreeMaxBox;
    private EditBox miningVeinMaxBox;
    private EditBox miningBlockIdBox;
    private EditBox maintenanceHexBox;
    private EditBox maintenanceBuybackBox;

    private String draftSearch = "";
    private String draftPayPlayer = "";
    private String draftPayAmount = "";
    private String draftEconomyHistoryLimit;
    private String selectedTransactionPlayerId = "";
    private String selectedTransactionPlayerLabel = "";
    private String draftTransactionPlayer = "";
    private boolean transactionPlayerDropdownOpen;
    private int transactionPlayerDropdownScroll;
    private String draftAuctionTax = "5";
    private SsuPermissionEditorDataPayload permissionData = SsuPermissionEditorDataPayload.empty(
            "player", 0L, "", false);
    private long latestPermissionRequest;
    private boolean permissionLoading;
    private String permissionMode = "player";
    private String selectedPermissionTarget = "";
    private String selectedPermissionDimension = "";
    private String selectedPermissionLabel = "";
    private String selectedAssignableRank = "";
    private String draftPermissionTargetSearch = "";
    private String draftPermissionSearch = "";
    private boolean permissionModeDropdownOpen;
    private boolean permissionTargetDropdownOpen;
    private boolean permissionDimensionDropdownOpen;
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
    private String draftHomeName = "";
    private String homesClaimName = "";
    private String draftWarpName = "";
    private String draftWarpRentalPrice = "100";
    private String draftWarpRentalDays = "30";
    private String draftClaimTaxRate = "0";
    private String draftClaimTaxInterval = "168";
    private String draftClaimTaxReminder = "24";
    private String draftClaimTaxDimension = "minecraft:overworld";
    private String draftClaimTaxMultiplier = "1";
    private String travelFilter = "all";
    private String draftRankName = "";
    private String draftRankRename = "";
    private String draftRankPlayer = "";
    private String draftRankPriority = "0";
    private String draftRankInheritance = "";
    private String draftRegionPlayerRefund = "0";
    private String draftRegionAdminRefund = "100";
    private String draftRegionDays = "1";
    private String draftRegionFill = "minecraft:stone";
    private String draftRegionCoordinates = "0 64 0";
    private String draftMiningLeafRange = "3";
    private String draftMiningTreeMax = "256";
    private String draftMiningVeinMax = "64";
    private String draftMiningBlockId = "minecraft:oak_log";
    private String draftMaintenanceHex = "#42F56C";
    private String draftMaintenanceBuyback = "30";
    private String pendingUnrentRegion = "";
    private String pendingDeleteHologram = "";
    private String pendingDeleteHome = "";
    private String pendingDeleteWarp = "";
    private String pendingSetPlayerWarp = "";
    private String pendingDeleteAdminClaim = "";
    private String pendingDeleteRank = "";
    private String pendingDeleteRegion = "";
    private String pendingResetRegion = "";
    private String pendingClearRegion = "";
    private String pendingRedefineRegion = "";
    private boolean pendingResetAllBorderColors;
    private boolean pendingEnableClaimTax;
    private String pendingResetStatistic = "";
    private String pendingDeleteStatistic = "";
    private final java.util.ArrayList<SettingsTooltip> settingsTooltips = new java.util.ArrayList<>();
    private SettingsCategory settingsCategory = SettingsCategory.GENERAL;
    private int adminToolScroll;
    private int adminModuleScroll;
    private int moduleSettingsScroll;
    private boolean requestInitialRemotePage;

    public SsuDashboardScreen(SsuMenuSnapshotPayload snapshot) {
        super(Component.translatable("screen.simpleserverutilities.dashboard"));
        this.snapshot = snapshot;
        String pendingClaim = pendingHomesClaimName;
        pendingHomesClaimName = "";
        if (!pendingClaim.isBlank()) {
            page = Page.HOMES;
            previousPage = Page.CLAIMS;
            homesClaimName = pendingClaim;
            requestInitialRemotePage = true;
        }
        syncEconomyDrafts();
    }

    public static void queueHomesForClaim(String claimName) {
        pendingHomesClaimName = claimName == null ? "" : claimName.trim();
    }

    public void acceptSnapshot(SsuMenuSnapshotPayload updated) {
        if (updated == null) return;
        this.snapshot = updated;
        if (economyHistoryLimitBox == null) syncEconomyDrafts();
        rebuildWidgets();
        if (page.hasRemoteData() || page == Page.PLAYER_INFO) requestPage(false);
    }

    public void acceptPageData(SsuMenuPageDataPayload payload) {
        if (payload == null || payload.requestId() < latestPageRequest) return;
        if (!payload.page().equals(page.remoteId())) return;
        latestPageRequest = payload.requestId();
        pageData = payload;
        if (page == Page.UTILITY_MINING_ADMIN) syncMiningDrafts(payload);
        if (page == Page.MAINTENANCE) syncMaintenanceDrafts(payload);
        if (page == Page.AUCTION_TAX) syncAuctionTaxDraft(payload);
        if (page == Page.CLAIM_TAX) syncClaimTaxDrafts(payload);
        if (page == Page.WARP_RENTAL) syncWarpRentalDrafts(payload);
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
        String previousDimension = selectedPermissionDimension;
        latestPermissionRequest = payload.requestId();
        permissionData = payload;
        permissionLoading = false;
        pageIndex = payload.pageIndex();
        selectedPermissionTarget = payload.selectedTarget();
        selectedPermissionDimension = payload.selectedDimension();
        selectedPermissionLabel = payload.selectedLabel();
        if (!previousTarget.equals(selectedPermissionTarget)
                || !previousDimension.equals(selectedPermissionDimension)) {
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
            // In 1.21.1 PlayerSkinWidget is rendered through the same GuiGraphics pose
            // as the rest of the Screen. Keep it in logical SSU coordinates and let
            // ScreenSsuGuiScaleMixin apply the scale exactly once. Pre-scaling these
            // values caused the portrait to be scaled twice and pulled toward screen centre.
            skin = new PlayerSkinWidget(
                    32,
                    48,
                    minecraft.getEntityModels(),
                    () -> minecraft.player.getSkin());
            // The transparent opening in portrait_framework.png is x=11..42, y=17..64.
            skin.setPosition(l.sidebarX() + 34, l.panelY() + 71);
            addRenderableWidget(skin);

            Rect profile = profileBounds(l);
            Button profileButton = Button.builder(Component.literal("Profile"), ignored -> openPage(Page.PROFILE))
                    .bounds(profile.x(), profile.y(), profile.width(), profile.height()).build();
            profileButton.active = page != Page.PROFILE;
            addRenderableWidget(profileButton);
        }

        switch (page) {
            case HOME -> addHomeButtons(l);
            case ADMIN -> addAdminButtons(l);
            case MODULE_SETTINGS -> addModuleSettingsButtons(l);
            case ADMIN_TOOLS -> addAdminToolButtons(l);
            case HOLOGRAMS -> addHologramButtons(l);
            case STATISTICS -> addStatisticButtons(l);
            case CLAIMS -> addClaimButtons(l);
            case HOMES -> addHomesButtons(l);
            case TRAVEL -> addTravelButtons(l);
            case TRAVEL_ADMIN -> addAdminTravelButtons(l);
            case MY_WARPS -> addPlayerWarpButtons(l);
            case ADMIN_CLAIMS -> addAdminClaimButtons(l);
            case RANKS -> addRankButtons(l);
            case WALLET -> addWalletButtons(l);
            case ECONOMICS -> addEconomicsButtons(l);
            case TRANSACTIONS -> addTransactionsButtons(l);
            case AUCTION_TAX -> addAuctionTaxButtons(l);
            case CLAIM_TAX -> addClaimTaxButtons(l);
            case WARP_RENTAL -> addWarpRentalButtons(l);
            case REGIONS -> addRegionButtons(l);
            case REGION_ADMIN -> addRegionAdminButtons(l);
            case UTILITY_MINING_ADMIN -> addUtilityMiningAdminButtons(l);
            case MAINTENANCE -> addMaintenanceButtons(l);
            case SETTINGS -> addSettingsButtons(l);
            case PERMISSIONS -> addPermissionButtons(l);
            case PLAYER_INFO -> addPlayerInfoButtons(l);
            case ACCOUNTS -> addAccountButtons(l);
            case JOBS -> addJobButtons(l);
            case RENT_OPERATIONS -> addRentOperationButtons(l);
            case CORE -> addCoreButtons(l);
            case PROFILE -> addProfileButtons(l);
            case MAIL, AUCTION_HOUSE, QUESTS, ACHIEVEMENTS, ACHIEVEMENTS_ADMIN, COSMETICS, MINIGAMES, MINIGAME_ADMIN, DUNGEONS, KITS, KIT_ADMIN, MINES, MINE_ADMIN, JAIL_ADMIN, ONBOARDING_ADMIN -> { }
        }
        if (requestInitialRemotePage) {
            requestInitialRemotePage = false;
            requestPage(false);
        }
    }

    private void clearReferences() {
        skin = null; searchBox = null; payPlayerBox = null; payAmountBox = null;
        economyHistoryLimitBox = null; transactionPlayerBox = null; auctionTaxBox = null;
        permissionTargetSearchBox = null; permissionSearchBox = null; playerProfileSearchBox = null; permissionValueInputs.clear();
        accountAmountBox = null; homeNameBox = null; warpNameBox = null; warpRentalPriceBox = null; warpRentalDaysBox = null; claimTaxRateBox = null; claimTaxIntervalBox = null; claimTaxReminderBox = null; claimTaxDimensionBox = null; claimTaxMultiplierBox = null; rankNameBox = null; rankRenameBox = null; rankPlayerBox = null; rankPriorityBox = null; rankInheritanceBox = null; regionPlayerRefundBox = null; regionAdminRefundBox = null; regionDaysBox = null; regionFillBox = null; regionCoordinatesBox = null; miningLeafRangeBox = null; miningTreeMaxBox = null; miningVeinMaxBox = null; miningBlockIdBox = null; maintenanceHexBox = null; maintenanceBuybackBox = null; settingsTooltips.clear();
    }

    private void addHomeButtons(Layout l) {
        if (!useTexturedTiles(l)) addModuleGrid(l, homeModules(l), 4);
    }

    private void addAdminButtons(Layout l) {
        if (!useTexturedTiles(l)) addAdminModuleGrid(l, adminModules());
    }

    private void addEconomicsButtons(Layout l) {
        addModuleGrid(l, economicsModules());
    }

    private boolean moduleActive(String id, boolean fallback) {
        var state = snapshot.moduleSettings().state(id);
        return state == null ? fallback : state.active();
    }

    private List<Module> homeModules(Layout l) {
        java.util.ArrayList<Module> modules = new java.util.ArrayList<>();
        // Player-facing dashboards only advertise features that are effectively active.
        // Administrators still see every module and its ON/OFF/BLOCKED state in Module Settings.
        if (moduleActive("claims", snapshot.moduleSettings().claims())) {
            modules.add(new Module("Claims & Land", "Your connected land claims, claim settings, homes and map tools.", ICON_CLAIM, Page.CLAIMS, true));
        }
        modules.add(new Module("Travel", "All available homes, warps and server destinations.", ICON_TRAVEL, Page.TRAVEL, true));
        if (moduleActive("warps", snapshot.moduleSettings().warps())) {
            modules.add(new Module("My Warps", "Rent, place and control the visibility of your personal warps.", ICON_PORTAL, Page.MY_WARPS, true));
        }
        if (moduleActive("economy", snapshot.economy().enabled())) {
            modules.add(new Module("Wallet", "Balance, payments and your transaction history.", ICON_WALLET, Page.WALLET, snapshot.economy().enabled()));
        }
        if (moduleActive("mail", snapshot.moduleSettings().mail())) {
            modules.add(new Module("Mail", "Inbox, sent mail, items and money attachments.", ICON_MAIL, Page.MAIL, true));
        }
        if (moduleActive("kits", true)) {
            modules.add(new Module("Kits", "View and claim kits available to your permissions.", ICON_KITS, Page.KITS, true));
        }
        if (moduleActive("mines", true)) {
            modules.add(new Module("Mines", "View resettable server mines available to your permissions.", ICON_MINES, Page.MINES, true));
        }
        modules.add(new Module("Support", "Create and follow help, bug and player-report tickets.", ICON_TICKET, Page.SUPPORT, true));
        if (snapshot.auctionHouseDashboardVisible()) {
            modules.add(new Module("Auction House", "Browse, buy and sell player-listed items.", ICON_MARKET, Page.AUCTION_HOUSE, true));
        }
        if (moduleActive("quests", snapshot.moduleSettings().quests())
                && ("menu".equalsIgnoreCase(snapshot.moduleSettings().effectiveQuestAccessMode())
                    || "both".equalsIgnoreCase(snapshot.moduleSettings().effectiveQuestAccessMode()))) {
            modules.add(new Module("Questbook", "Available, active and completed quests.", ICON_QUESTBOOK, Page.QUESTS, true));
        }
        if (moduleActive("achievements", snapshot.moduleSettings().achievements())) {
            modules.add(new Module("Achievements", "Browse earned and unearned achievements and compare progress.", ICON_ACHIEVEMENTS, Page.ACHIEVEMENTS, true));
        }
        modules.add(new Module("Cosmetics", "Cosmetic unlocks and customization. Coming soon.", ICON_COSMETICS, Page.COSMETICS, true));
        if (moduleActive("minigames", snapshot.moduleSettings().minigames())) {
            modules.add(new Module("Minigames", "Queues, arenas and active matches.", ICON_MINIGAMES, Page.MINIGAMES, true));
        }
        if (moduleActive("dungeons", snapshot.moduleSettings().dungeons())) {
            modules.add(new Module("Dungeons", "Parties, stages, checkpoints and customized dungeon runs.", ICON_SHIELD, Page.DUNGEONS, true));
        }
        // Compact layouts have no portrait sidebar, so Profile remains available as a normal tile there.
        if (!l.sidebarVisible()) {
            modules.add(new Module("Profile", "Your rank, property and personal settings.", ICON_PLAYERS, Page.PROFILE, true));
        }
        return List.copyOf(modules);
    }

    private List<Module> adminModules() {
        return List.of(
                new Module("Player info", "Inspect online and offline player profiles.", ICON_PLAYERS, Page.PLAYER_INFO, snapshot.administrator()),
                new Module("Player claims", "Inspect, teleport to and safely remove player claims.", ICON_CLAIM, Page.ADMIN_CLAIMS,
                        snapshot.administrator() && moduleActive("claims", snapshot.moduleSettings().claims())),
                new Module("Travel management", "Create, move, delete and test server warps and spawn.", ICON_PORTAL, Page.TRAVEL_ADMIN,
                        snapshot.administrator() && (moduleActive("warps", snapshot.moduleSettings().warps()) || moduleActive("spawn", true))),
                new Module("Permissions", "Edit global and per-dimension rank/player permissions.", ICON_PLAYERS, Page.PERMISSIONS, snapshot.adminAccess().permissions()),
                new Module("Ranks", "Create, rename, default and safely remove permission ranks.", ICON_PLAYERS, Page.RANKS, snapshot.adminAccess().permissions()),
                new Module("Dimensions", "Create and configure custom server dimensions.", ICON_PORTAL, Page.DIMENSIONS, snapshot.administrator() && moduleActive("dimensions", true)),
                new Module("Onboarding & spawns", "Configure server/lobby spawn, rules and first-join flow.", ICON_PORTAL, Page.ONBOARDING_ADMIN, snapshot.administrator() && moduleActive("onboarding", true)),
                new Module("Kit administration", "Create compact permission-aware player kits.", ICON_MARKET, Page.KIT_ADMIN, snapshot.administrator() && moduleActive("kits", true)),
                new Module("Mine administration", "Create and manage dedicated resettable mining areas.", ICON_SETTINGS, Page.MINE_ADMIN, snapshot.administrator() && moduleActive("mines", true)),
                new Module("Jail administration", "Create nested jail facilities, work areas and solitude cells.", ICON_SHIELD, Page.JAIL_ADMIN, snapshot.administrator() && moduleActive("jails", true)),
                new Module("Server operations", "Backups, scheduler, maintenance, moderation, reports, health and world management.", ICON_SETTINGS, Page.SERVER_OPERATIONS, snapshot.administrator() && moduleActive("server_operations", true)),
                new Module("Economics", "Accounts, transactions, taxes and economy journals.", ICON_MARKET, Page.ECONOMICS,
                        snapshot.economy().canAdmin()),
                new Module("Active jobs", "View progress and cancel server jobs.", ICON_SETTINGS, Page.JOBS, snapshot.adminAccess().core()),
                new Module("Core status", "Storage, indexes and migrated modules.", ICON_SETTINGS, Page.CORE, snapshot.adminAccess().core()),
                new Module("Module settings", "Enable modules and configure world render distances.", ICON_SETTINGS, Page.MODULE_SETTINGS, snapshot.administrator()),
                new Module("Utility Mining", "Configure Treecapitator and Veinminer block rules.", ICON_SETTINGS, Page.UTILITY_MINING_ADMIN,
                        snapshot.administrator() && moduleActive("utility_mining", snapshot.moduleSettings().treecapitator() || snapshot.moduleSettings().veinminer())),
                new Module("Minigames", "Configure game modes, arenas, rewards and live matches.", ICON_MINIGAMES, Page.MINIGAME_ADMIN,
                        snapshot.administrator() && moduleActive("minigames", snapshot.moduleSettings().minigames())),
                new Module("Admin tools", "Get purpose-built world editing and setup tools.", ICON_SETTINGS, Page.ADMIN_TOOLS, snapshot.administrator()),
                new Module("Holograms", "Edit, teleport to and delete floating text from anywhere.", ICON_SETTINGS, Page.HOLOGRAMS,
                        snapshot.administrator() && moduleActive("holograms", snapshot.moduleSettings().holograms())),
                new Module("Statistics", "Create event counters and publish personal values or leaderboards.", ICON_PLAYERS, Page.STATISTICS,
                        snapshot.administrator() && moduleActive("statistics", snapshot.moduleSettings().statistics())),
                new Module("Achievements", "Create, edit, inspect and reset custom achievements.", ICON_ACHIEVEMENTS, Page.ACHIEVEMENTS_ADMIN,
                        snapshot.administrator() && moduleActive("achievements", snapshot.moduleSettings().achievements())),
                new Module("Regions", "Open server-region details, visibility and settings.", ICON_SHIELD, Page.REGIONS, moduleActive("regions", snapshot.moduleSettings().regions())),
                new Module("Maintenance", "Reload SSU, refresh runtime content and manage visualization defaults.", ICON_SETTINGS, Page.MAINTENANCE,
                        snapshot.administrator())
        );
    }

    private List<Module> economicsModules() {
        return List.of(
                new Module("Accounts", "Search players and adjust economy balances.", ICON_MARKET, Page.ACCOUNTS,
                        snapshot.economy().canAdmin()),
                new Module("Transactions", "Filter and inspect the complete transaction journal.", ICON_MARKET, Page.TRANSACTIONS,
                        snapshot.economy().canAdmin()),
                new Module("Auction House tax", "Configure the tax withheld from completed player sales.", ICON_MARKET, Page.AUCTION_TAX,
                        snapshot.economy().canAdmin()),
                new Module("Player Claim tax", "Configure recurring per-chunk claim taxation and dimension multipliers.", ICON_CLAIM, Page.CLAIM_TAX,
                        snapshot.economy().canAdmin()),
                new Module("Player Warp rentals", "Configure the prepaid price and duration for player-rented warps.", ICON_PORTAL, Page.WARP_RENTAL,
                        snapshot.economy().canAdmin()),
                new Module("Rent journal", "Inspect rental reconciliation and refund operations.", ICON_PORTAL, Page.RENT_OPERATIONS,
                        snapshot.economy().canAdmin())
        );
    }

    private List<AdminTool> adminTools() {
        return List.of(
                new AdminTool("Region Tool", "Left-click a block for Point 1, right-click a block for Point 2, and right-click the air to open Region settings.", "region"),
                new AdminTool("World Edit Tool", "Left-click sets point 1; right-click a block sets point 2; right-click air opens the full editor. The World Edit key (default W) opens compact in-world move/transform controls.", "world_edit"),
                new AdminTool("Hologram Tool", "Right-click to create one block ahead. Right-click an existing hologram with the tool to edit or delete it.", "hologram"),
                new AdminTool("NPC Tool", "Right-click to create/edit. Sneak-right-click an NPC to copy and elsewhere to paste a linked placement.", "npc"),
                new AdminTool("Ability Library", "Create reusable combat abilities once and assign them to any number of NPC templates.", "abilities"),
                new AdminTool("Shop Manager", "Create and edit shared NPC shops and inspect every linked NPC.", "shops"),
                new AdminTool("Item Price Catalog", "Edit what players pay and receive for every vanilla and modded item.", "item_prices"),
                new AdminTool("Quest Editor", "Create and edit quest prerequisites, objectives, rewards and lifecycle settings.", "quest"),
                new AdminTool("Minigame Setup Tool", "Left-click performs the selected in-world setup action; right-click opens its action and arena menu.", "minigame"),
                new AdminTool("Mine Setup Tool", "Left-click point 1 and point 2; right-click opens Mine Administration.", "mine"),
                new AdminTool("Jail Setup Tool", "Select Jail or Task Area bounds in-world; right-click air opens Jail Administration.", "jail"),
                new AdminTool("Dungeon Editor", "Create region arenas, checkpoints, ordered stages, lives and rewards.", "dungeon")
        );
    }

    private void addAdminToolButtons(Layout l) {
        List<AdminTool> tools = adminTools();
        int visible = adminToolVisibleRows(l);
        int maximum = Math.max(0, tools.size() - visible);
        adminToolScroll = Math.max(0, Math.min(maximum, adminToolScroll));
        int rowStart = l.contentTop() + 34;
        int rowStep = 46;
        for (int local = 0; local < visible; local++) {
            int index = adminToolScroll + local;
            if (index >= tools.size()) break;
            AdminTool tool = tools.get(index);
            int y = rowStart + local * rowStep;
            Button getTool = Button.builder(Component.literal(("quest".equals(tool.id())
                            || "dungeon".equals(tool.id()) || "shops".equals(tool.id()) || "item_prices".equals(tool.id()) || "abilities".equals(tool.id()))
                            ? "Open Editor" : "Get Tool"), ignored -> action("admin_tool_get", tool.id(), "", ""))
                    .bounds(l.contentRight() - 84, y + 10, 84, 20).build();
            getTool.active = !((("region".equals(tool.id()) || "world_edit".equals(tool.id())) && !moduleActive("regions", snapshot.moduleSettings().regions()))
                    || ("hologram".equals(tool.id()) && !moduleActive("holograms", snapshot.moduleSettings().holograms()))
                    || ("npc".equals(tool.id()) && !moduleActive("npcs", snapshot.moduleSettings().npcs()))
                    || ("abilities".equals(tool.id()) && !moduleActive("npcs", snapshot.moduleSettings().npcs()))
                    || ("shops".equals(tool.id()) && !moduleActive("npc_shops", true))
                    || ("item_prices".equals(tool.id()) && !moduleActive("npc_shops", true))
                    || ("quest".equals(tool.id()) && !moduleActive("quests", snapshot.moduleSettings().quests()))
                    || ("minigame".equals(tool.id()) && !moduleActive("minigames", snapshot.moduleSettings().minigames()))
                    || ("dungeon".equals(tool.id()) && !moduleActive("dungeons", snapshot.moduleSettings().dungeons())));
            addRenderableWidget(getTool);
        }
        Button up = addRenderableWidget(Button.builder(Component.literal("▲"), ignored -> {
            adminToolScroll = Math.max(0, adminToolScroll - 1); rebuildWidgets();
        }).bounds(l.contentRight() - 52, l.contentTop(), 24, 20).build());
        up.active = adminToolScroll > 0;
        Button down = addRenderableWidget(Button.builder(Component.literal("▼"), ignored -> {
            adminToolScroll = Math.min(maximum, adminToolScroll + 1); rebuildWidgets();
        }).bounds(l.contentRight() - 26, l.contentTop(), 24, 20).build());
        down.active = adminToolScroll < maximum;

        int footerY = l.footerY() - 24;
        Button manageHolograms = Button.builder(Component.literal("Manage holograms"), ignored -> openPage(Page.HOLOGRAMS))
                .bounds(l.contentX(), footerY, 132, 20).build();
        manageHolograms.active = moduleActive("holograms", snapshot.moduleSettings().holograms());
        addRenderableWidget(manageHolograms);
        addRenderableWidget(Button.builder(Component.literal("Module settings"), ignored -> openPage(Page.MODULE_SETTINGS))
                .bounds(l.contentRight() - 132, footerY, 132, 20).build());
    }

    private int adminToolVisibleRows(Layout l) {
        int usable = Math.max(46, l.footerY() - 30 - (l.contentTop() + 34));
        return Math.max(1, usable / 46);
    }

    private void addModuleSettingsButtons(Layout l) {
        var settings = snapshot.moduleSettings();
        List<ModuleSwitch> switches = moduleSwitches(settings);
        int columns = moduleSettingsColumns(l);
        int gap = 6;
        int buttonWidth = (l.contentWidth() - gap * (columns - 1)) / columns;
        int top = l.contentTop() + 34;
        int rowStep = 21;
        int visibleRows = moduleSettingsVisibleRows(l);
        int totalRows = (switches.size() + columns - 1) / columns;
        int maximum = Math.max(0, totalRows - visibleRows);
        moduleSettingsScroll = Math.max(0, Math.min(maximum, moduleSettingsScroll));

        for (int localRow = 0; localRow < visibleRows; localRow++) {
            int sourceRow = moduleSettingsScroll + localRow;
            for (int col = 0; col < columns; col++) {
                int i = sourceRow * columns + col;
                if (i >= switches.size()) break;
                ModuleSwitch value = switches.get(i);
                int x = l.contentX() + col * (buttonWidth + gap);
                int y = top + localRow * rowStep;
                String status = value.active() ? "ON" : value.configured() ? "BLOCKED" : "OFF";
                Button button = Button.builder(Component.literal(value.label() + ": " + status), ignored ->
                                action("module_toggle", value.key(), "", Boolean.toString(!value.configured())))
                        .bounds(x, y, buttonWidth, 18).build();
                addRenderableWidget(button);
                java.util.ArrayList<Component> tip = new java.util.ArrayList<>();
                if (value.configured() && !value.active() && !value.reason().isBlank()) tip.add(Component.literal(value.reason()));
                if (!value.required().isEmpty()) tip.add(Component.literal("Requires: " + String.join(", ", value.required())));
                if (!value.optional().isEmpty()) tip.add(Component.literal("Optional: " + String.join(", ", value.optional())));
                if (!value.integrations().isEmpty()) tip.add(Component.literal("Integrations: " + String.join(", ", value.integrations())));
                if (!tip.isEmpty()) settingsTooltips.add(new SettingsTooltip(new Rect(x, y, buttonWidth, 18), List.copyOf(tip)));
            }
        }

        if (maximum > 0) {
            Button up = addRenderableWidget(Button.builder(Component.literal("▲"), ignored -> {
                moduleSettingsScroll = Math.max(0, moduleSettingsScroll - 1); rebuildWidgets();
            }).bounds(l.contentRight() - 44, l.contentTop(), 20, 18).build());
            up.active = moduleSettingsScroll > 0;
            Button down = addRenderableWidget(Button.builder(Component.literal("▼"), ignored -> {
                moduleSettingsScroll = Math.min(maximum, moduleSettingsScroll + 1); rebuildWidgets();
            }).bounds(l.contentRight() - 22, l.contentTop(), 20, 18).build());
            down.active = moduleSettingsScroll < maximum;
        }

        int questModeY = moduleSettingsOptionsTop(l);
        String configuredRaw = settings.questAccessMode() == null ? "menu" : settings.questAccessMode().toLowerCase(java.util.Locale.ROOT);
        String configuredMode = switch (configuredRaw) { case "npc" -> "NPCs"; case "both" -> "Both"; default -> "SSU Menu"; };
        String effectiveRaw = settings.effectiveQuestAccessMode() == null ? configuredRaw : settings.effectiveQuestAccessMode().toLowerCase(java.util.Locale.ROOT);
        String effectiveSuffix = configuredRaw.equals(effectiveRaw) ? "" : " (effective: " + ("npc".equals(effectiveRaw) ? "NPCs" : "both".equals(effectiveRaw) ? "Both" : "SSU Menu") + ")";
        String nextMode = switch (configuredRaw) { case "menu" -> "npc"; case "npc" -> "both"; default -> "menu"; };
        Button questMode = Button.builder(Component.literal("Quest access: " + configuredMode + effectiveSuffix), ignored ->
                        action("quest_access_mode", "", "", nextMode))
                .bounds(l.contentX(), questModeY, Math.min(300, l.contentWidth()), 18).build();
        var questState = settings.state("quests");
        questMode.active = questState != null ? questState.active() : settings.quests();
        addRenderableWidget(questMode);

        int distanceTop = questModeY + 24;
        addDistanceButtons(l, distanceTop, "holograms", settings.hologramRenderDistance(), 8);
        addDistanceButtons(l, distanceTop + 24, "claim_borders", settings.claimBorderRenderDistance(), 16);
        addDistanceButtons(l, distanceTop + 48, "region_borders", settings.regionBorderRenderDistance(), 16);
    }

    private List<ModuleSwitch> moduleSwitches(SsuMenuSnapshotPayload.ModuleSettingsSummary settings) {
        return List.of(
                moduleSwitch(settings, "Economy", "economy", "economy", true),
                moduleSwitch(settings, "Permissions", "permissions", "permissions", settings.permissions()),
                moduleSwitch(settings, "Player Claims", "claims", "claims", settings.claims()),
                moduleSwitch(settings, "Server Regions", "regions", "regions", settings.regions()),
                moduleSwitch(settings, "Teleport", "teleport", "teleport", true),
                moduleSwitch(settings, "Server Spawn", "spawn", "spawn", true),
                moduleSwitch(settings, "Homes", "homes", "homes", settings.homes()),
                moduleSwitch(settings, "Warps", "warps", "warps", settings.warps()),
                moduleSwitch(settings, "Dimensions", "dimensions", "dimensions", true),
                moduleSwitch(settings, "Visualization", "visualization", "visualization", true),
                moduleSwitch(settings, "Map Markers", "map_markers", "map_markers", true),
                moduleSwitch(settings, "Mail", "mail", "mail", settings.mail()),
                moduleSwitch(settings, "Auction House", "auction_house", "auction_house", settings.auctionHouse()),
                moduleSwitch(settings, "NPC Core", "npcs", "npcs", settings.npcs()),
                moduleSwitch(settings, "NPC Shops", "npc_shops", "npc_shops", true),
                moduleSwitch(settings, "Quest Core", "quests", "quests", settings.quests()),
                moduleSwitch(settings, "Minigames", "minigames", "minigames", settings.minigames()),
                moduleSwitch(settings, "Dungeons", "dungeons", "dungeons", settings.dungeons()),
                moduleSwitch(settings, "Mines", "mines", "mines", true),
                moduleSwitch(settings, "Jails", "jails", "jails", true),
                moduleSwitch(settings, "Moderation", "moderation", "moderation", true),
                moduleSwitch(settings, "Kits", "kits", "kits", true),
                moduleSwitch(settings, "Onboarding", "onboarding", "onboarding", true),
                moduleSwitch(settings, "Server Ops", "server_operations", "server_operations", true),
                moduleSwitch(settings, "Holograms", "holograms", "holograms", settings.holograms()),
                moduleSwitch(settings, "Statistics", "statistics", "statistics", settings.statistics()),
                moduleSwitch(settings, "Community Stats", "community_statistics", "community_statistics", false),
                moduleSwitch(settings, "Achievements", "achievements", "achievements", settings.achievements()),
                moduleSwitch(settings, "Block Info", "block_information", "block_information", settings.blockInformation()),
                moduleSwitch(settings, "Identity & Titles", "identity", "identity", true),
                moduleSwitch(settings, "Treecapitator", "treecapitator", "utility_mining", settings.treecapitator()),
                moduleSwitch(settings, "Veinminer", "veinminer", "utility_mining", settings.veinminer()),
                new ModuleSwitch("Crop Harvest", "crop_harvesting", settings.cropHarvesting(), settings.cropHarvesting(), "", List.of(), List.of(), List.of()),
                new ModuleSwitch("Remote Images", "remote_hologram_images", settings.remoteHologramImages(), settings.remoteHologramImages(), "", List.of(), List.of(), List.of())
        );
    }

    private ModuleSwitch moduleSwitch(SsuMenuSnapshotPayload.ModuleSettingsSummary settings, String label, String key,
                                      String moduleId, boolean configuredFallback) {
        var state = settings.state(moduleId);
        if (state == null) return new ModuleSwitch(label, key, configuredFallback, configuredFallback, "", List.of(), List.of(), List.of());
        boolean configured = ("treecapitator".equals(key) || "veinminer".equals(key)) ? configuredFallback : state.configuredEnabled();
        boolean active = configured && state.active();
        return new ModuleSwitch(label, key, configured, active, state.disabledReason(),
                state.requiredDependencies(), state.optionalDependencies(), state.integrationDependencies());
    }

    private int moduleSettingsColumns(Layout l) { return l.contentWidth() >= 560 ? 4 : l.contentWidth() >= 390 ? 3 : 2; }
    private int moduleSettingsVisibleRows(Layout l) {
        int top = l.contentTop() + 34;
        int listBottom = l.footerY() - 104;
        return Math.max(2, (listBottom - top) / 21);
    }
    private int moduleSettingsOptionsTop(Layout l) { return l.footerY() - 101; }
    private int moduleSettingsMaximumScroll(Layout l) {
        int rows = (moduleSwitches(snapshot.moduleSettings()).size() + moduleSettingsColumns(l) - 1) / moduleSettingsColumns(l);
        return Math.max(0, rows - moduleSettingsVisibleRows(l));
    }

    private void addDistanceButtons(Layout l, int y, String key, int current, int minimum) {
        int right = l.contentRight();
        addRenderableWidget(Button.builder(Component.literal("-16"), ignored ->
                        action("render_distance", key, "", Integer.toString(Math.max(minimum, current - 16))))
                .bounds(right - 90, y, 42, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+16"), ignored ->
                        action("render_distance", key, "", Integer.toString(Math.min(512, current + 16))))
                .bounds(right - 44, y, 44, 20).build());
    }

    private void addHologramButtons(Layout l) {
        addListSearch(l);
        List<SsuMenuPageDataPayload.LocationEntry> values = pageData.locations();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i);
            int y = rowY(l, i);
            int right = l.contentRight();
            addRenderableWidget(Button.builder(Component.literal("Edit"), ignored ->
                            action("hologram_edit", entry.name(), "", ""))
                    .bounds(right - 256, y, 44, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Move here"), ignored ->
                            action("hologram_move_here", entry.name(), "", ""))
                    .bounds(right - 208, y, 70, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Teleport"), ignored ->
                            action("hologram_teleport", entry.name(), "", ""))
                    .bounds(right - 134, y, 68, 20).build());
            addRenderableWidget(Button.builder(Component.literal(hologramDeleteLabel(entry.name())), ignored ->
                            requestHologramDelete(entry.name()))
                    .bounds(right - 62, y, 62, 20).build());
        }
        addPagination(l, 0);
    }

    private String hologramDeleteLabel(String id) {
        return pendingDeleteHologram.equalsIgnoreCase(id) ? "Confirm" : "Delete";
    }

    private void requestHologramDelete(String id) {
        if (pendingDeleteHologram.equalsIgnoreCase(id)) {
            pendingDeleteHologram = "";
            action("hologram_delete", id, "", "");
            return;
        }
        pendingDeleteHologram = id;
        setNotice("Click Confirm again to permanently delete hologram '" + id + "'.", true);
    }

    private void addStatisticButtons(Layout l) {
        addListSearch(l);
        addRenderableWidget(Button.builder(Component.literal("Create"), ignored ->
                        action("statistic_edit", "", "", ""))
                .bounds(l.contentX(), l.footerY(), 68, 20).build());
        List<SsuMenuPageDataPayload.StatisticEntry> values = pageData.statistics();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i);
            int y = rowY(l, i);
            int right = l.contentRight();
            addRenderableWidget(Button.builder(Component.literal("Edit"), ignored ->
                            action("statistic_edit", entry.id(), "", ""))
                    .bounds(right - 260, y, 42, 20).build());
            addRenderableWidget(Button.builder(Component.literal(entry.enabled() ? "Pause" : "Resume"), ignored ->
                            action("statistic_toggle", entry.id(), "", Boolean.toString(!entry.enabled())))
                    .bounds(right - 214, y, 58, 20).build());
            addRenderableWidget(Button.builder(Component.literal(statisticResetLabel(entry.id())), ignored ->
                            requestStatisticReset(entry.id()))
                    .bounds(right - 152, y, 58, 20).build());
            addRenderableWidget(Button.builder(Component.literal(statisticDeleteLabel(entry.id())), ignored ->
                            requestStatisticDelete(entry.id()))
                    .bounds(right - 90, y, 58, 20).build());
        }
        addPagination(l, 76);
    }

    private String statisticResetLabel(String id) {
        return pendingResetStatistic.equalsIgnoreCase(id) ? "Confirm" : "Reset";
    }

    private String statisticDeleteLabel(String id) {
        return pendingDeleteStatistic.equalsIgnoreCase(id) ? "Confirm" : "Delete";
    }

    private void requestStatisticReset(String id) {
        if (pendingResetStatistic.equalsIgnoreCase(id)) {
            pendingResetStatistic = "";
            action("statistic_reset", id, "", "");
            return;
        }
        pendingResetStatistic = id;
        pendingDeleteStatistic = "";
        setNotice("Click Confirm again to reset statistic '" + id + "' for every player.", true);
    }

    private void requestStatisticDelete(String id) {
        if (pendingDeleteStatistic.equalsIgnoreCase(id)) {
            pendingDeleteStatistic = "";
            action("statistic_delete", id, "", "");
            return;
        }
        pendingDeleteStatistic = id;
        pendingResetStatistic = "";
        setNotice("Click Confirm again to permanently delete statistic '" + id + "' and its values.", true);
    }

    private void addModuleGrid(Layout l, List<Module> modules) {
        int columns = l.contentWidth() >= 420 ? 3 : 2;
        addModuleGrid(l, modules, columns);
    }

    /**
     * Fixed-column variant used by the Dashboard. The SSU GUI scale is
     * responsible for adapting the complete screen to smaller displays; the
     * Dashboard itself must keep its four-column design so modules never gain
     * an extra row and overflow the panel merely because Minecraft exposes a
     * smaller logical GUI width.
     */
    private void addModuleGrid(Layout l, List<Module> modules, int requestedColumns) {
        int columns = Math.max(1, requestedColumns);
        int gap = 8;
        int w = Math.max(1, (l.contentWidth() - gap * (columns - 1)) / columns);
        int startY = l.contentTop() + 35;
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i); int col = i % columns; int row = i / columns;
            Button button = Button.builder(Component.literal(m.label()), ignored -> openPage(m.page()))
                    .bounds(l.contentX() + col * (w + gap), startY + row * 32, w, 24).build();
            button.active = m.enabled(); addRenderableWidget(button);
        }
    }

    private void addAdminModuleGrid(Layout l, List<Module> modules) {
        int columns = l.contentWidth() >= 420 ? 3 : 2;
        int gap = 8;
        int width = (l.contentWidth() - gap * (columns - 1)) / columns;
        int startY = l.contentTop() + 35;
        int visibleRows = Math.max(1, (l.footerY() - startY - 4) / 32);
        int totalRows = (modules.size() + columns - 1) / columns;
        int maximumScroll = Math.max(0, totalRows - visibleRows);
        adminModuleScroll = Math.max(0, Math.min(maximumScroll, adminModuleScroll));
        int first = adminModuleScroll * columns;
        int last = Math.min(modules.size(), first + visibleRows * columns);
        for (int index = first; index < last; index++) {
            Module module = modules.get(index);
            int local = index - first;
            int column = local % columns;
            int row = local / columns;
            Button button = Button.builder(Component.literal(module.label()), ignored -> openPage(module.page()))
                    .bounds(l.contentX() + column * (width + gap), startY + row * 32, width, 24).build();
            button.active = module.enabled();
            addRenderableWidget(button);
        }
        if (maximumScroll > 0) {
            Button up = Button.builder(Component.literal("Up"), ignored -> {
                        adminModuleScroll = Math.max(0, adminModuleScroll - 1);
                        rebuildWidgets();
                    }).bounds(l.contentRight() - 104, l.footerY(), 50, 20).build();
            Button down = Button.builder(Component.literal("Down"), ignored -> {
                        adminModuleScroll = Math.min(maximumScroll, adminModuleScroll + 1);
                        rebuildWidgets();
                    }).bounds(l.contentRight() - 50, l.footerY(), 50, 20).build();
            up.active = adminModuleScroll > 0;
            down.active = adminModuleScroll < maximumScroll;
            addRenderableWidget(up);
            addRenderableWidget(down);
        }
    }

    private int adminModuleMaximumScroll(Layout l) {
        List<Module> modules = adminModules();
        int columns = l.contentWidth() >= 420 ? 3 : 2;
        int startY = l.contentTop() + 35;
        int visibleRows = Math.max(1, (l.footerY() - startY - 4) / 32);
        int totalRows = (modules.size() + columns - 1) / columns;
        return Math.max(0, totalRows - visibleRows);
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
            Button border = Button.builder(Component.literal(entry.borderVisible() ? "Hide" : "Show"), ignored ->
                            action("claim_visibility", entry.name(), "", Boolean.toString(!entry.borderVisible())))
                    .bounds(right - 56, y, 56, 20).build();
            border.active = snapshot.canViewClaimBorders();
            addRenderableWidget(border);
        }
        int mapW = l.contentWidth() < 500 ? 72 : 88;
        addRenderableWidget(Button.builder(Component.literal("Open map"), ignored -> action("claim_map", "", "", ""))
                .bounds(l.contentX(), l.footerY(), mapW, 20).build());
        if (l.contentWidth() < 500) addCompactPageControls(l, l.footerY(), mapW + 4);
        else addPagination(l, mapW + 4);
    }

    private void addHomesButtons(Layout l) {
        boolean canSetHere = homeCapability("set_here");
        boolean canTeleport = homeCapability("teleport");
        boolean canDelete = homeCapability("delete");
        homeNameBox = box(l.contentX(), l.contentTop() + 24, Math.max(90, l.contentWidth() - 168),
                "Home name", draftHomeName, value -> draftHomeName = value);
        homeNameBox.active = canSetHere;
        addRenderableWidget(homeNameBox);
        Button save = Button.builder(Component.literal("Save here"), ignored -> {
                    if (draftHomeName.isBlank()) setNotice("Enter a home name first.", true);
                    else action("home_set", draftHomeName, homesClaimName, "");
                }).bounds(l.contentRight() - 162, l.contentTop() + 24, 78, 20).build();
        save.active = canSetHere;
        addRenderableWidget(save);
        addRenderableWidget(Button.builder(Component.literal("Cancel TP"), ignored -> action("teleport_cancel", "homes", "", ""))
                .bounds(l.contentRight() - 80, l.contentTop() + 24, 80, 20).build());
        List<SsuMenuPageDataPayload.LocationEntry> values = pageData.locations();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i); int y = rowY(l, i);
            Button teleport = Button.builder(Component.literal("Teleport"), ignored -> action("teleport_home", entry.name(), homesClaimName, ""))
                    .bounds(l.contentRight() - 148, y, 72, 20).build();
            teleport.active = canTeleport;
            addRenderableWidget(teleport);
            Button delete = Button.builder(Component.literal(deleteHomeLabel(entry.name())), ignored -> requestDeleteHome(entry.name()))
                    .bounds(l.contentRight() - 72, y, 72, 20).build();
            delete.active = canDelete;
            addRenderableWidget(delete);
        }
        addPagination(l, 0);
    }

    private boolean homeCapability(String key) {
        return pageData.permissions().stream()
                .filter(entry -> "homes".equals(entry.owner()) && key.equals(entry.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value)
                .map(Boolean::parseBoolean)
                .findFirst().orElse(false);
    }

    private void addTravelButtons(Layout l) {
        addTravelFilterButtons(l, false);
        addTravelSearch(l, l.contentTop() + 48);
        List<SsuMenuPageDataPayload.LocationEntry> values = pageData.locations();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i);
            int y = rowY(l, i, 82);
            String actionName = switch (entry.kind()) {
                case "home" -> "teleport_home";
                case "warp" -> "teleport_warp";
                default -> "teleport_spawn";
            };
            String secondary = switch (entry.kind()) {
                case "warp", "spawn" -> "travel";
                case "home" -> entry.ownerId().isBlank() ? "" : entry.ownerId() + "|" + entry.claimId();
                default -> "";
            };
            String value = "home".equals(entry.kind()) ? "travel" : "";
            addRenderableWidget(Button.builder(Component.literal("Teleport"), ignored ->
                            action(actionName, entry.name(), secondary, value))
                    .bounds(l.contentRight() - 74, y, 74, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Cancel teleport"), ignored -> action("teleport_cancel", "travel", "", ""))
                .bounds(l.contentX(), l.footerY(), 104, 20).build());
        addPagination(l, 110);
    }

    private void addPlayerWarpButtons(Layout l) {
        boolean canRentPermission = Boolean.parseBoolean(pageValue("player_warps", "can_rent", "false"));
        boolean economyEnabled = Boolean.parseBoolean(pageValue("player_warps", "economy_enabled", "false"));
        boolean canRent = canRentPermission && economyEnabled;
        boolean canUse = Boolean.parseBoolean(pageValue("player_warps", "can_use", "false"));
        int maximum = parseInt(pageValue("player_warps", "maximum", "0"), 0);
        int current = parseInt(pageValue("player_warps", "count", "0"), 0);
        boolean canCreate = canRent && maximum > 0 && current < maximum;
        int y = l.contentTop() + 42;
        int fieldWidth = Math.max(100, l.contentWidth() - 112);
        warpNameBox = box(l.contentX(), y, fieldWidth, "Warp name", draftWarpName, value -> draftWarpName = value);
        warpNameBox.setMaxLength(32);
        warpNameBox.active = canCreate;
        addRenderableWidget(warpNameBox);
        String normalizedDraftWarp = draftWarpName.trim();
        boolean confirmingSet = !normalizedDraftWarp.isBlank() && pendingSetPlayerWarp.equalsIgnoreCase(normalizedDraftWarp);
        Button set = Button.builder(Component.literal(confirmingSet ? "Confirm" : "Rent new"), ignored -> {
                    String name = draftWarpName.trim();
                    if (name.isBlank()) { setNotice("Enter a warp name first.", true); return; }
                    if (!pendingSetPlayerWarp.equalsIgnoreCase(name)) {
                        pendingSetPlayerWarp = name;
                        String price = pageValue("player_warps", "price", "the configured rent");
                        setNotice("Confirm renting '" + name + "'. The first period is prepaid at " + price + ".", false);
                        return;
                    }
                    pendingSetPlayerWarp = "";
                    action("player_warp_set", name, "", "");
                }).bounds(l.contentRight() - 106, y, 106, 20).build();
        set.active = canCreate;
        addRenderableWidget(set);

        List<SsuMenuPageDataPayload.LocationEntry> values = pageData.locations();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i);
            int rowY = rowY(l, i, 86);
            int right = l.contentRight();
            addRenderableWidget(Button.builder(Component.literal("Move here"), ignored ->
                            action("player_warp_move", entry.name(), "", ""))
                    .bounds(right - 280, rowY, 58, 20).build());
            Button teleport = Button.builder(Component.literal("Teleport"), ignored ->
                            action("teleport_warp", entry.name(), "player_warps", ""))
                    .bounds(right - 218, rowY, 64, 20).build();
            teleport.active = canUse;
            addRenderableWidget(teleport);
            boolean isPublic = "public".equals(entry.kind());
            addRenderableWidget(Button.builder(Component.literal(isPublic ? "Make private" : "Make public"), ignored ->
                            action("player_warp_visibility", entry.name(), "", Boolean.toString(!isPublic)))
                    .bounds(right - 150, rowY, 80, 20).build());
            Button delete = Button.builder(Component.literal(deleteWarpLabel(entry.name())), ignored -> requestDeleteWarp(entry.name()))
                    .bounds(right - 66, rowY, 66, 20).build();
            addRenderableWidget(delete);
        }
        addRenderableWidget(Button.builder(Component.literal("Open Travel"), ignored -> openPage(Page.TRAVEL))
                .bounds(l.contentX(), l.footerY(), 82, 20).build());
        addPagination(l, 88);
    }

    private void addAdminTravelButtons(Layout l) {
        addTravelFilterButtons(l, true);
        boolean compact = l.contentWidth() < 500;
        int controlsY = l.contentTop() + 48;
        boolean canSetWarp = travelAdminCapability("warp_set");
        boolean canDeleteWarp = travelAdminCapability("warp_delete");
        boolean canAdminSpawn = travelAdminCapability("spawn_admin");
        boolean canTeleportWarp = travelAdminCapability("warp_teleport");
        boolean canTeleportSpawn = travelAdminCapability("spawn_teleport");

        if (compact) {
            int nameWidth = Math.max(90, l.contentWidth() - 94);
            warpNameBox = box(l.contentX(), controlsY, nameWidth, "Warp name", draftWarpName, value -> draftWarpName = value);
            warpNameBox.active = canSetWarp;
            addRenderableWidget(warpNameBox);
            Button setWarp = Button.builder(Component.literal("Set / move"), ignored -> setWarpFromAdmin())
                    .bounds(l.contentRight() - 90, controlsY, 90, 20).build();
            setWarp.active = canSetWarp;
            addRenderableWidget(setWarp);
            Button setSpawn = Button.builder(Component.literal("Set spawn"), ignored -> action("spawn_set", "", "travel_admin", ""))
                    .bounds(l.contentX(), controlsY + 24, (l.contentWidth() - 4) / 2, 20).build();
            setSpawn.active = canAdminSpawn;
            addRenderableWidget(setSpawn);
            Button clearSpawn = Button.builder(Component.literal("Clear spawn"), ignored -> action("spawn_clear", "", "travel_admin", ""))
                    .bounds(l.contentX() + (l.contentWidth() + 4) / 2, controlsY + 24, (l.contentWidth() - 4) / 2, 20).build();
            clearSpawn.active = canAdminSpawn;
            addRenderableWidget(clearSpawn);
            addTravelSearch(l, controlsY + 48);
        } else {
            int setW = 84, spawnW = 72, clearW = 82, gap = 4;
            int nameW = Math.max(90, l.contentWidth() - setW - spawnW - clearW - gap * 3);
            int x = l.contentX();
            warpNameBox = box(x, controlsY, nameW, "Warp name", draftWarpName, value -> draftWarpName = value);
            warpNameBox.active = canSetWarp;
            addRenderableWidget(warpNameBox);
            x += nameW + gap;
            Button setWarp = Button.builder(Component.literal("Set / move"), ignored -> setWarpFromAdmin())
                    .bounds(x, controlsY, setW, 20).build();
            setWarp.active = canSetWarp;
            addRenderableWidget(setWarp);
            x += setW + gap;
            Button setSpawn = Button.builder(Component.literal("Set spawn"), ignored -> action("spawn_set", "", "travel_admin", ""))
                    .bounds(x, controlsY, spawnW, 20).build();
            setSpawn.active = canAdminSpawn;
            addRenderableWidget(setSpawn);
            x += spawnW + gap;
            Button clearSpawn = Button.builder(Component.literal("Clear spawn"), ignored -> action("spawn_clear", "", "travel_admin", ""))
                    .bounds(x, controlsY, clearW, 20).build();
            clearSpawn.active = canAdminSpawn;
            addRenderableWidget(clearSpawn);
            addTravelSearch(l, controlsY + 24);
        }

        int rowOffset = compact ? 130 : 106;
        List<SsuMenuPageDataPayload.LocationEntry> values = pageData.locations();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i);
            int y = rowY(l, i, rowOffset);
            int right = l.contentRight();
            String actionName = "spawn".equals(entry.kind()) ? "teleport_spawn" : "teleport_warp";
            Button teleport = Button.builder(Component.literal("Teleport"), ignored ->
                            action(actionName, entry.name(), "travel_admin", ""))
                    .bounds(right - ("warp".equals(entry.kind()) ? 148 : 74), y, 74, 20).build();
            teleport.active = "warp".equals(entry.kind()) ? canTeleportWarp : canTeleportSpawn;
            addRenderableWidget(teleport);
            if ("warp".equals(entry.kind())) {
                Button delete = Button.builder(Component.literal(deleteWarpLabel(entry.name())), ignored -> requestDeleteWarp(entry.name()))
                        .bounds(right - 70, y, 70, 20).build();
                delete.active = canDeleteWarp;
                addRenderableWidget(delete);
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Cancel teleport"), ignored -> action("teleport_cancel", "travel_admin", "", ""))
                .bounds(l.contentX(), l.footerY(), 104, 20).build());
        addPagination(l, 110);
    }

    private void addTravelFilterButtons(Layout l, boolean admin) {
        String[] filters = admin ? new String[]{"all", "warp", "spawn"} : new String[]{"all", "home", "warp", "other"};
        String[] labels = admin ? new String[]{"All", "Warps", "Spawn"} : new String[]{"All", "Homes", "Warps", "Other"};
        int gap = 4;
        int width = Math.max(54, Math.min(76, (l.contentWidth() - gap * (filters.length - 1)) / filters.length));
        int x = l.contentX();
        int y = l.contentTop() + 24;
        for (int i = 0; i < filters.length; i++) {
            String filter = filters[i];
            Button button = Button.builder(Component.literal(labels[i]), ignored -> {
                        travelFilter = filter;
                        pageIndex = 0;
                        requestPage(false);
                    }).bounds(x, y, width, 20).build();
            button.active = !travelFilter.equals(filter);
            addRenderableWidget(button);
            x += width + gap;
        }
    }

    private void addTravelSearch(Layout l, int y) {
        searchBox = box(l.contentX(), y, Math.max(100, l.contentWidth() - 86), "Search destinations", draftSearch,
                value -> draftSearch = value);
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), ignored -> { pageIndex = 0; requestPage(false); })
                .bounds(l.contentRight() - 80, y, 80, 20).build());
    }

    private boolean travelAdminCapability(String key) {
        return pageData.permissions().stream()
                .filter(entry -> "travel_admin".equals(entry.owner()) && key.equals(entry.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value)
                .map(Boolean::parseBoolean)
                .findFirst().orElse(false);
    }

    private void setWarpFromAdmin() {
        if (draftWarpName.isBlank()) {
            setNotice("Enter a warp name first.", true);
            return;
        }
        action("warp_set", draftWarpName, "travel_admin", "");
    }

    private void addAdminClaimButtons(Layout l) {
        addListSearch(l);
        List<SsuMenuPageDataPayload.ClaimEntry> values = pageData.claims();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i); int y = rowY(l, i); int row = i; int right = l.contentRight();
            addRenderableWidget(Button.builder(Component.literal("Details"), ignored -> select(row))
                    .bounds(right - 210, y, 56, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Teleport"), ignored -> action("admin_claim_teleport", entry.id(), "", ""))
                    .bounds(right - 150, y, 72, 20).build());
            addRenderableWidget(Button.builder(Component.literal(deleteAdminClaimLabel(entry.id())), ignored -> requestDeleteAdminClaim(entry))
                    .bounds(right - 74, y, 74, 20).build());
        }
        addPagination(l, 0);
    }

    private void addRankButtons(Layout l) {
        int top = l.contentTop() + 24;
        int third = Math.max(84, (l.contentWidth() - 174) / 3);
        rankNameBox = box(l.contentX(), top, third, "New rank", draftRankName, value -> draftRankName = value);
        addRenderableWidget(rankNameBox);
        addRenderableWidget(Button.builder(Component.literal("Create"), ignored -> {
                    if (draftRankName.isBlank()) setNotice("Enter a rank name first.", true);
                    else action("rank_create", draftRankName, "", "");
                }).bounds(l.contentX() + third + 4, top, 54, 20).build());
        rankPlayerBox = box(l.contentX() + third + 62, top, third, "Player name / UUID", draftRankPlayer, value -> draftRankPlayer = value);
        addRenderableWidget(rankPlayerBox);
        addRenderableWidget(Button.builder(Component.literal("Reset rank"), ignored -> {
                    if (draftRankPlayer.isBlank()) setNotice("Enter a player name or UUID first.", true);
                    else action("rank_reset_player", draftRankPlayer, "", "");
                }).bounds(l.contentX() + third * 2 + 66, top, 82, 20).build());
        int renameWidth = Math.max(160, (l.contentWidth() - 88) / 2);
        rankRenameBox = box(l.contentX(), top + 25, renameWidth,
                "New rank name — then click Rename below", draftRankRename, value -> draftRankRename = value);
        addRenderableWidget(rankRenameBox);
        int secondaryX = l.contentX() + renameWidth + 6;
        addRenderableWidget(Button.builder(Component.literal("Title manager"), ignored ->
                        PacketDistributor.sendToServer(new TitleManagerRequestPayload(true, nextRequestId++)))
                .bounds(secondaryX, top + 25, 102, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestPage(false))
                .bounds(secondaryX + 108, top + 25, 82, 20).build());

        if (selectedRow >= 0 && selectedRow < pageData.permissions().size()) {
            var selected = pageData.permissions().get(selectedRow);
            int advancedY = top + 50;
            int priorityWidth = 76;
            rankPriorityBox = box(l.contentX(), advancedY, priorityWidth, "Priority", draftRankPriority,
                    value -> draftRankPriority = value);
            rankPriorityBox.setMaxLength(8);
            addRenderableWidget(rankPriorityBox);
            addRenderableWidget(Button.builder(Component.literal("Set priority"), ignored ->
                            action("rank_priority", selected.owner(), "", draftRankPriority))
                    .bounds(l.contentX() + priorityWidth + 4, advancedY, 72, 20).build());
            int inheritX = l.contentX() + priorityWidth + 82;
            int inheritWidth = Math.max(90, l.contentRight() - inheritX - 126);
            rankInheritanceBox = box(inheritX, advancedY, inheritWidth, "Parent rank", draftRankInheritance,
                    value -> draftRankInheritance = value);
            addRenderableWidget(rankInheritanceBox);
            addRenderableWidget(Button.builder(Component.literal("Inherit"), ignored -> {
                        if (draftRankInheritance.isBlank()) setNotice("Enter a parent rank first.", true);
                        else action("rank_inherit", selected.owner(), "", draftRankInheritance);
                    }).bounds(l.contentRight() - 122, advancedY, 58, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Remove"), ignored -> {
                        if (draftRankInheritance.isBlank()) setNotice("Enter a parent rank first.", true);
                        else action("rank_uninherit", selected.owner(), "", draftRankInheritance);
                    }).bounds(l.contentRight() - 60, advancedY, 60, 20).build());
        }

        List<SsuMenuPageDataPayload.PermissionEntry> values = pageData.permissions();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i); int y = rowY(l, i, 110); int right = l.contentRight(); int row = i;
            addRenderableWidget(Button.builder(Component.literal("Manage"), ignored -> {
                        selectedRow = row;
                        draftRankPriority = entry.value();
                        draftRankInheritance = "";
                        rebuildWidgets();
                    }).bounds(right - 350, y, 58, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Prefix"), ignored ->
                            PacketDistributor.sendToServer(new RankDisplayRequestPayload(entry.owner())))
                    .bounds(right - 288, y, 58, 20).build());
            Button makeDefault = Button.builder(Component.literal("Default"), ignored -> action("rank_default", entry.owner(), "", ""))
                    .bounds(right - 226, y, 58, 20).build();
            makeDefault.active = !"default".equals(entry.key());
            addRenderableWidget(makeDefault);
            addRenderableWidget(Button.builder(Component.literal("Rename"), ignored -> {
                        if (draftRankRename.isBlank()) setNotice("Enter the new rank name above first.", true);
                        else action("rank_rename", entry.owner(), draftRankRename, "");
                    }).bounds(right - 164, y, 68, 20).build());
            Button delete = Button.builder(Component.literal(deleteRankLabel(entry.owner())), ignored -> requestDeleteRank(entry.owner()))
                    .bounds(right - 92, y, 92, 20).build();
            delete.active = !"default".equals(entry.key()) && !"admin".equalsIgnoreCase(entry.owner());
            addRenderableWidget(delete);
        }
        addPagination(l, 0);
    }

    private void addWalletButtons(Layout l) {
        int top = l.contentTop() + 28;
        if (snapshot.economy().canPay()) {
            int playerW = Math.max(104, Math.min(138, l.contentWidth() / 3));
            int pickerW = 28;
            int amountW = 72;
            int gap = 5;
            payPlayerBox = box(l.contentX(), top, playerW, "Player", draftPayPlayer, value -> draftPayPlayer = value);
            payAmountBox = box(l.contentX() + playerW + pickerW + gap * 2, top, amountW, "Amount", draftPayAmount,
                    value -> draftPayAmount = value);
            addRenderableWidget(payPlayerBox);
            addRenderableWidget(Button.builder(Component.literal("…"), ignored -> minecraft.setScreen(
                            new KnownPlayerPickerScreen(this, value -> {
                                draftPayPlayer = value;
                                rebuildWidgets();
                            })))
                    .bounds(l.contentX() + playerW + gap, top, pickerW, 20).build());
            addRenderableWidget(payAmountBox);
            addRenderableWidget(Button.builder(Component.literal("Pay"), ignored -> submitPayment())
                    .bounds(l.contentX() + playerW + pickerW + amountW + gap * 3, top, 50, 20).build());
        }
        int searchY = l.contentTop() + 58;
        int searchW = Math.max(90, l.contentWidth() / 3);
        searchBox = box(l.contentX(), searchY, searchW, "Search", draftSearch, v -> draftSearch = v);
        addRenderableWidget(searchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), ignored -> { pageIndex = 0; requestPage(false); })
                .bounds(l.contentX() + searchW + 6, searchY, 62, 20).build());
        addTransactionDetailButtons(l, 88);
        addPagination(l, 0);
    }

    private void addTransactionsButtons(Layout l) {
        int top = l.contentTop() + 22;
        Rect dropdown = transactionPlayerBounds(l);
        addRenderableWidget(Button.builder(Component.literal(transactionPlayerButtonLabel()),
                        ignored -> {
                            transactionPlayerDropdownOpen = !transactionPlayerDropdownOpen;
                            transactionPlayerDropdownScroll = 0;
                        }).bounds(dropdown.x(), dropdown.y(), dropdown.width(), dropdown.height()).build());

        int applyWidth = 52;
        int manualX = dropdown.x() + dropdown.width() + 6;
        int manualWidth = Math.max(90, l.contentRight() - manualX - applyWidth - 6);
        transactionPlayerBox = box(manualX, top, manualWidth, "Exact player name / UUID",
                draftTransactionPlayer, value -> draftTransactionPlayer = value);
        addRenderableWidget(transactionPlayerBox);
        addRenderableWidget(Button.builder(Component.literal("Use"), ignored -> {
                    pageIndex = 0;
                    requestPage(false);
                }).bounds(l.contentRight() - applyWidth, top, applyWidth, 20).build());

        addSearchAt(l, top + 26);

        int settingsY = top + 52;
        economyHistoryLimitBox = box(l.contentRight() - 122, settingsY, 62, "History",
                draftEconomyHistoryLimit, value -> draftEconomyHistoryLimit = value);
        addRenderableWidget(economyHistoryLimitBox);
        addRenderableWidget(Button.builder(Component.literal("Apply"), ignored -> submitEconomyHistoryLimit())
                .bounds(l.contentRight() - 54, settingsY, 54, 20).build());

        addTransactionDetailButtons(l, 108);
        addPagination(l, 0);
    }

    private void addTransactionDetailButtons(Layout l, int rowOffset) {
        List<SsuMenuPageDataPayload.TransactionEntry> values = pageData.transactions();
        for (int i = 0; i < values.size(); i++) {
            int y = rowY(l, i, rowOffset);
            int row = i;
            addRenderableWidget(Button.builder(Component.literal("Details"), ignored -> select(row))
                    .bounds(l.contentRight() - 62, y, 62, 20).build());
        }
    }

    private void addAuctionTaxButtons(Layout l) {
        int y = l.contentTop() + 46;
        int fieldWidth = Math.min(120, Math.max(80, l.contentWidth() - 88));
        auctionTaxBox = box(l.contentX(), y, fieldWidth, "Tax percentage", draftAuctionTax,
                value -> draftAuctionTax = value);
        auctionTaxBox.setMaxLength(8);
        addRenderableWidget(auctionTaxBox);
        addRenderableWidget(Button.builder(Component.literal("Apply"), ignored ->
                        action("auction_tax_set", "", "", draftAuctionTax))
                .bounds(l.contentX() + fieldWidth + 6, y, 70, 20).build());
    }

    private void addClaimTaxButtons(Layout l) {
        boolean enabled = Boolean.parseBoolean(pageValue("claim_tax", "enabled", "false"));
        int top = l.contentTop() + 24;
        int gap = 4;
        int toggleW = 80;
        int applyW = 62;
        int available = l.contentWidth() - toggleW - applyW - gap * 4;
        int fieldW = Math.max(54, available / 3);
        Button toggle = Button.builder(Component.literal(enabled ? "Disable" : pendingEnableClaimTax ? "Confirm" : "Enable"), ignored -> {
            if (enabled) { pendingEnableClaimTax = false; action("claim_tax_toggle", "", "", "false"); return; }
            if (!pendingEnableClaimTax) {
                pendingEnableClaimTax = true;
                setNotice("Enabling claim tax can permanently delete every claim and linked home of players who cannot pay, then confiscate the taxed peak chunks from their future claim capacity. Click Confirm to enable it.", true);
                rebuildWidgets();
                return;
            }
            pendingEnableClaimTax = false;
            action("claim_tax_toggle", "", "", "true");
        }).bounds(l.contentX(), top, toggleW, 20).build();
        addRenderableWidget(toggle);
        int x = l.contentX() + toggleW + gap;
        claimTaxRateBox = box(x, top, fieldW, "Rate/chunk", draftClaimTaxRate, value -> draftClaimTaxRate = value);
        addRenderableWidget(claimTaxRateBox); x += fieldW + gap;
        claimTaxIntervalBox = box(x, top, fieldW, "Interval h", draftClaimTaxInterval, value -> draftClaimTaxInterval = value);
        addRenderableWidget(claimTaxIntervalBox); x += fieldW + gap;
        claimTaxReminderBox = box(x, top, fieldW, "Reminder h", draftClaimTaxReminder, value -> draftClaimTaxReminder = value);
        addRenderableWidget(claimTaxReminderBox);
        addRenderableWidget(Button.builder(Component.literal("Apply"), ignored ->
                        action("claim_tax_settings", draftClaimTaxRate, draftClaimTaxInterval, draftClaimTaxReminder))
                .bounds(l.contentRight() - applyW, top, applyW, 20).build());

        int dimY = top + 28;
        int multiplierW = 64, saveW = 82;
        int dimensionW = Math.max(120, l.contentWidth() - multiplierW - saveW - gap * 2);
        claimTaxDimensionBox = box(l.contentX(), dimY, dimensionW, "namespace:dimension", draftClaimTaxDimension, value -> draftClaimTaxDimension = value);
        claimTaxDimensionBox.setMaxLength(128);
        addRenderableWidget(claimTaxDimensionBox);
        claimTaxMultiplierBox = box(l.contentX() + dimensionW + gap, dimY, multiplierW, "Multiplier", draftClaimTaxMultiplier, value -> draftClaimTaxMultiplier = value);
        addRenderableWidget(claimTaxMultiplierBox);
        addRenderableWidget(Button.builder(Component.literal("Add / update"), ignored ->
                        action("claim_tax_dimension", draftClaimTaxDimension, "", draftClaimTaxMultiplier))
                .bounds(l.contentRight() - saveW, dimY, saveW, 20).build());

        List<SsuMenuPageDataPayload.PermissionEntry> dimensions = pageData.permissions().stream()
                .filter(entry -> "dimension".equals(entry.kind())).toList();
        for (int i = 0; i < dimensions.size() && i < 7; i++) {
            var entry = dimensions.get(i);
            int rowY = l.contentTop() + 86 + i * 25;
            boolean vanilla = entry.key().equals("minecraft:overworld") || entry.key().equals("minecraft:the_nether") || entry.key().equals("minecraft:the_end");
            Button edit = Button.builder(Component.literal("Edit"), ignored -> {
                        draftClaimTaxDimension = entry.key();
                        draftClaimTaxMultiplier = entry.value();
                        rebuildWidgets();
                    }).bounds(l.contentRight() - (vanilla ? 54 : 112), rowY, 50, 20).build();
            addRenderableWidget(edit);
            if (!vanilla) addRenderableWidget(Button.builder(Component.literal("Remove"), ignored ->
                            action("claim_tax_dimension_remove", entry.key(), "", ""))
                    .bounds(l.contentRight() - 56, rowY, 56, 20).build());
        }
        addPagination(l, 0);
    }

    private void addWarpRentalButtons(Layout l) {
        int y = l.contentTop() + 50;
        int applyW = 70, gap = 6;
        int fieldW = Math.max(90, (l.contentWidth() - applyW - gap * 2) / 2);
        warpRentalPriceBox = box(l.contentX(), y, fieldW, "Price", draftWarpRentalPrice, value -> draftWarpRentalPrice = value);
        addRenderableWidget(warpRentalPriceBox);
        warpRentalDaysBox = box(l.contentX() + fieldW + gap, y, fieldW, "Days", draftWarpRentalDays, value -> draftWarpRentalDays = value);
        addRenderableWidget(warpRentalDaysBox);
        addRenderableWidget(Button.builder(Component.literal("Apply"), ignored ->
                        action("warp_rental_settings", draftWarpRentalPrice, "", draftWarpRentalDays))
                .bounds(l.contentRight() - applyW, y, applyW, 20).build());
    }

    private void syncClaimTaxDrafts(SsuMenuPageDataPayload payload) {
        draftClaimTaxRate = payloadValue(payload, "claim_tax", "rate", draftClaimTaxRate);
        draftClaimTaxInterval = payloadValue(payload, "claim_tax", "interval_hours", draftClaimTaxInterval);
        draftClaimTaxReminder = payloadValue(payload, "claim_tax", "reminder_hours", draftClaimTaxReminder);
    }

    private void syncWarpRentalDrafts(SsuMenuPageDataPayload payload) {
        draftWarpRentalPrice = payloadValue(payload, "warp_rental", "price", draftWarpRentalPrice);
        draftWarpRentalDays = payloadValue(payload, "warp_rental", "days", draftWarpRentalDays);
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
                    .bounds(right - 184, y, 56, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Settings"), ignored -> openPropertySettings("region", entry.name()))
                    .bounds(right - 124, y, 66, 20).build());
            if (snapshot.administrator()) {
                addRenderableWidget(Button.builder(Component.literal(entry.visible() ? "Disable" : "Show"), ignored ->
                        action("region_visibility", entry.name(), "", Boolean.toString(!entry.visible())))
                        .bounds(right - 54, y, 54, 20).build());
            }
        }
        if (snapshot.administrator()) {
            addRenderableWidget(Button.builder(Component.literal("Disable all"), ignored -> action("regions_hide", "", "", ""))
                    .bounds(l.contentX(), l.footerY(), 72, 20).build());
        }
        addPagination(l, snapshot.administrator() ? 78 : 0);
    }

    private void addUtilityMiningAdminButtons(Layout l) {
        int top = l.contentTop() + 24;
        boolean compact = l.contentWidth() < 430;
        if (!compact) {
            miningLeafRangeBox = box(l.contentX(), top, 54, "Leaf", draftMiningLeafRange, value -> draftMiningLeafRange = value);
            addRenderableWidget(miningLeafRangeBox);
            addRenderableWidget(Button.builder(Component.literal("Apply leaf range"), ignored ->
                            action("utility_mining_setting", "leaf_range", "", draftMiningLeafRange))
                    .bounds(l.contentX() + 58, top, 102, 20).build());
            boolean breakLeaves = miningValue("tree", "break_leaves", "false").equalsIgnoreCase("true");
            addRenderableWidget(Button.builder(Component.literal("Natural leaves: " + onOff(breakLeaves)), ignored ->
                            action("utility_mining_setting", "break_leaves", "", Boolean.toString(!breakLeaves)))
                    .bounds(l.contentX() + 164, top, 118, 20).build());
            miningTreeMaxBox = box(l.contentX() + 286, top, 58, "Tree max", draftMiningTreeMax, value -> draftMiningTreeMax = value);
            addRenderableWidget(miningTreeMaxBox);
            addRenderableWidget(Button.builder(Component.literal("Apply"), ignored ->
                            action("utility_mining_setting", "tree_default_max", "", draftMiningTreeMax))
                    .bounds(l.contentX() + 348, top, Math.max(44, l.contentRight() - l.contentX() - 348), 20).build());

            int second = top + 25;
            miningVeinMaxBox = box(l.contentX(), second, 58, "Vein max", draftMiningVeinMax, value -> draftMiningVeinMax = value);
            addRenderableWidget(miningVeinMaxBox);
            addRenderableWidget(Button.builder(Component.literal("Apply vein max"), ignored ->
                            action("utility_mining_setting", "vein_default_max", "", draftMiningVeinMax))
                    .bounds(l.contentX() + 62, second, 98, 20).build());
            miningBlockIdBox = box(l.contentX() + 164, second, Math.max(130, l.contentWidth() - 164),
                    "Block id used by Add/Remove", draftMiningBlockId, value -> draftMiningBlockId = value);
            addRenderableWidget(miningBlockIdBox);
        } else {
            int leafBoxW = 46, leafApplyW = 82, gap = 4;
            miningLeafRangeBox = box(l.contentX(), top, leafBoxW, "Leaf", draftMiningLeafRange, value -> draftMiningLeafRange = value);
            addRenderableWidget(miningLeafRangeBox);
            addRenderableWidget(Button.builder(Component.literal("Apply range"), ignored ->
                            action("utility_mining_setting", "leaf_range", "", draftMiningLeafRange))
                    .bounds(l.contentX() + leafBoxW + gap, top, leafApplyW, 20).build());
            boolean breakLeaves = miningValue("tree", "break_leaves", "false").equalsIgnoreCase("true");
            int leavesX = l.contentX() + leafBoxW + leafApplyW + gap * 2;
            addRenderableWidget(Button.builder(Component.literal("Leaves: " + onOff(breakLeaves)), ignored ->
                            action("utility_mining_setting", "break_leaves", "", Boolean.toString(!breakLeaves)))
                    .bounds(leavesX, top, Math.max(72, l.contentRight() - leavesX), 20).build());

            int second = top + 25;
            int fieldW = 52, buttonW = 54;
            miningTreeMaxBox = box(l.contentX(), second, fieldW, "Tree", draftMiningTreeMax, value -> draftMiningTreeMax = value);
            addRenderableWidget(miningTreeMaxBox);
            addRenderableWidget(Button.builder(Component.literal("Apply"), ignored ->
                            action("utility_mining_setting", "tree_default_max", "", draftMiningTreeMax))
                    .bounds(l.contentX() + fieldW + gap, second, buttonW, 20).build());
            int veinX = l.contentX() + fieldW + buttonW + gap * 2;
            miningVeinMaxBox = box(veinX, second, fieldW, "Vein", draftMiningVeinMax, value -> draftMiningVeinMax = value);
            addRenderableWidget(miningVeinMaxBox);
            addRenderableWidget(Button.builder(Component.literal("Apply"), ignored ->
                            action("utility_mining_setting", "vein_default_max", "", draftMiningVeinMax))
                    .bounds(veinX + fieldW + gap, second, buttonW, 20).build());

            int third = top + 50;
            miningBlockIdBox = box(l.contentX(), third, l.contentWidth(),
                    "Block id used by Add/Remove", draftMiningBlockId, value -> draftMiningBlockId = value);
            addRenderableWidget(miningBlockIdBox);
        }

        int listOffset = compact ? 106 : 92;
        List<SsuMenuPageDataPayload.PermissionEntry> lists = pageData.permissions().stream()
                .filter(entry -> "list".equals(entry.kind())).toList();
        for (int i = 0; i < lists.size(); i++) {
            var entry = lists.get(i); int y = rowY(l, i, listOffset); int right = l.contentRight();
            addRenderableWidget(Button.builder(Component.literal("Add"), ignored ->
                            action("utility_mining_list", entry.key(), "add", draftMiningBlockId))
                    .bounds(right - 144, y, 42, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Remove"), ignored ->
                            action("utility_mining_list", entry.key(), "remove", draftMiningBlockId))
                    .bounds(right - 98, y, 54, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Clear"), ignored ->
                            action("utility_mining_list", entry.key(), "clear", ""))
                    .bounds(right - 40, y, 40, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestPage(false))
                .bounds(l.contentRight() - 68, l.footerY(), 68, 20).build());
    }

    private String pageValue(String owner, String key, String fallback) {
        return pageData.permissions().stream().filter(entry -> owner.equals(entry.owner()) && key.equals(entry.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value).findFirst().orElse(fallback);
    }

    private static String payloadValue(SsuMenuPageDataPayload payload, String owner, String key, String fallback) {
        return payload.permissions().stream().filter(entry -> owner.equals(entry.owner()) && key.equals(entry.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value).findFirst().orElse(fallback);
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
    }

    private String miningValue(String owner, String key, String fallback) {
        return pageData.permissions().stream().filter(entry -> owner.equals(entry.owner()) && key.equals(entry.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value).findFirst().orElse(fallback);
    }

    private void syncMiningDrafts(SsuMenuPageDataPayload payload) {
        draftMiningLeafRange = payload.permissions().stream().filter(v -> "tree".equals(v.owner()) && "leaf_range".equals(v.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value).findFirst().orElse(draftMiningLeafRange);
        draftMiningTreeMax = payload.permissions().stream().filter(v -> "tree".equals(v.owner()) && "default_max".equals(v.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value).findFirst().orElse(draftMiningTreeMax);
        draftMiningVeinMax = payload.permissions().stream().filter(v -> "vein".equals(v.owner()) && "default_max".equals(v.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value).findFirst().orElse(draftMiningVeinMax);
    }

    private void syncMaintenanceDrafts(SsuMenuPageDataPayload payload) {
        draftMaintenanceBuyback = payload.permissions().stream()
                .filter(v -> "buyback_minutes".equals(v.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value)
                .findFirst().orElse(draftMaintenanceBuyback);
    }

    private void addMaintenanceButtons(Layout l) {
        int top = l.contentTop() + 24;
        int buttonWidth = Math.max(70, (l.contentWidth() - 12) / 4);
        int x = l.contentX();
        addRenderableWidget(Button.builder(Component.literal("Reload SSU"), ignored -> action("maintenance_reload", "", "", ""))
                .bounds(x, top, buttonWidth, 20).build()); x += buttonWidth + 4;
        addRenderableWidget(Button.builder(Component.literal("Borders"), ignored -> action("maintenance_border_refresh", "", "", ""))
                .bounds(x, top, buttonWidth, 20).build()); x += buttonWidth + 4;
        addRenderableWidget(Button.builder(Component.literal("Holograms"), ignored -> action("maintenance_hologram_refresh", "", "", ""))
                .bounds(x, top, buttonWidth, 20).build()); x += buttonWidth + 4;
        addRenderableWidget(Button.builder(Component.literal("NPCs"), ignored -> action("maintenance_npc_refresh", "", "", ""))
                .bounds(x, top, Math.max(40, l.contentRight() - x), 20).build());

        int settingsY = top + 25;
        maintenanceHexBox = box(l.contentX(), settingsY, 96, "#RRGGBB", draftMaintenanceHex, value -> draftMaintenanceHex = value);
        addRenderableWidget(maintenanceHexBox);
        maintenanceBuybackBox = box(l.contentX() + 104, settingsY, 90, "Buyback min.", draftMaintenanceBuyback, value -> draftMaintenanceBuyback = value);
        addRenderableWidget(maintenanceBuybackBox);
        addRenderableWidget(Button.builder(Component.literal("Apply buyback"), ignored ->
                        action("maintenance_buyback_minutes", "", "", draftMaintenanceBuyback))
                .bounds(l.contentX() + 198, settingsY, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal(pendingResetAllBorderColors ? "Confirm reset all" : "Reset all colors"), ignored -> {
                    if (pendingResetAllBorderColors) {
                        pendingResetAllBorderColors = false;
                        action("maintenance_border_reset_all", "", "", "");
                    } else {
                        pendingResetAllBorderColors = true;
                        setNotice("Click Confirm reset all again to restore every border category to its default color.", true);
                        rebuildWidgets();
                    }
                }).bounds(l.contentX(), l.footerY(), 126, 20).build());

        List<SsuMenuPageDataPayload.PermissionEntry> colors = pageData.permissions().stream()
                .filter(v -> "color".equals(v.kind())).toList();
        for (int i = 0; i < colors.size(); i++) {
            var entry = colors.get(i); int y = rowY(l, i, 82); int right = l.contentRight();
            addRenderableWidget(Button.builder(Component.literal("Set"), ignored ->
                            action("maintenance_border_color", entry.key(), "", draftMaintenanceHex))
                    .bounds(right - 104, y, 46, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Default"), ignored ->
                            action("maintenance_border_reset", entry.key(), "", ""))
                    .bounds(right - 54, y, 54, 20).build());
        }
        addPagination(l, 130);
    }

    private void addRegionAdminButtons(Layout l) {
        addListSearch(l);
        int toolsY = l.contentTop() + 49;
        boolean compact = l.contentWidth() < 440;
        int x = l.contentX();
        addRenderableWidget(Button.builder(Component.literal("P1 here"), ignored -> action("region_selection_point1", "", "", ""))
                .bounds(x, toolsY, 54, 20).build()); x += 58;
        addRenderableWidget(Button.builder(Component.literal("P2 here"), ignored -> action("region_selection_point2", "", "", ""))
                .bounds(x, toolsY, 54, 20).build()); x += 58;
        addRenderableWidget(Button.builder(Component.literal("Clear sel."), ignored -> action("region_selection_clear", "", "", ""))
                .bounds(x, toolsY, 62, 20).build()); x += 66;
        addRenderableWidget(Button.builder(Component.literal("Unbind"), ignored -> action("region_selection_unbind", "", "", ""))
                .bounds(x, toolsY, 54, 20).build()); x += 58;

        int coordinateY = compact ? toolsY + 24 : toolsY;
        if (compact) {
            addCompactPageControls(l, toolsY, x - l.contentX());
            x = l.contentX();
        }
        int coordinateWidth = Math.max(92, l.contentRight() - x - 88);
        regionCoordinatesBox = box(x, coordinateY, coordinateWidth, "x y z", draftRegionCoordinates, value -> draftRegionCoordinates = value);
        addRenderableWidget(regionCoordinatesBox); x += coordinateWidth + 4;
        addRenderableWidget(Button.builder(Component.literal("Set P1"), ignored -> action("region_selection_coordinates", "1", "", draftRegionCoordinates))
                .bounds(x, coordinateY, 40, 20).build()); x += 44;
        addRenderableWidget(Button.builder(Component.literal("Set P2"), ignored -> action("region_selection_coordinates", "2", "", draftRegionCoordinates))
                .bounds(x, coordinateY, 40, 20).build());

        int secondY = compact ? toolsY + 48 : toolsY + 24;
        int reserved = compact ? 156 : 330;
        int fillWidth = Math.max(76, l.contentWidth() - reserved);
        regionFillBox = box(l.contentX(), secondY, fillWidth, "Block ids", draftRegionFill, value -> draftRegionFill = value);
        addRenderableWidget(regionFillBox);
        addRenderableWidget(Button.builder(Component.literal("Fill"), ignored -> action("region_selection_fill", "", "", draftRegionFill))
                .bounds(l.contentX() + fillWidth + 4, secondY, 46, 20).build());
        boolean renting = pageData.permissions().stream().filter(v -> "renting".equals(v.key()))
                .findFirst().map(v -> Boolean.parseBoolean(v.value())).orElse(true);
        addRenderableWidget(Button.builder(Component.literal("Renting: " + (renting ? "ON" : "PAUSED")), ignored ->
                        action("region_renting_toggle", "", "", Boolean.toString(!renting)))
                .bounds(l.contentRight() - 102, secondY, 102, 20).build());
        if (!compact) addSmallPageControls(l, secondY, fillWidth + 54);

        List<SsuMenuPageDataPayload.RegionEntry> values = pageData.regions();
        for (int i = 0; i < values.size(); i++) {
            var entry = values.get(i); int y = rowY(l, i, compact ? 130 : 105); int row = i; int right = l.contentRight();
            addRenderableWidget(Button.builder(Component.literal(selectedRow == row ? "Selected" : "Select"), ignored -> select(row))
                    .bounds(right - 198, y, 58, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Teleport"), ignored -> action("region_admin_teleport", entry.name(), "", ""))
                    .bounds(right - 136, y, 66, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Settings"), ignored -> openPropertySettings("region", entry.name()))
                    .bounds(right - 66, y, 66, 20).build());
        }

        if (selectedRow >= 0 && selectedRow < values.size()) {
            var selected = values.get(selectedRow); String region = selected.name();
            int y1 = l.footerY() - 24; int y2 = l.footerY();
            int w = Math.max(52, (l.contentWidth() - 12) / 5); int gap = 3;
            addRenderableWidget(Button.builder(Component.literal("Snapshot"), ignored -> action("region_admin_snapshot", region, "", ""))
                    .bounds(l.contentX(), y1, w, 20).build());
            addRenderableWidget(Button.builder(Component.literal(regionConfirmLabel(pendingResetRegion, region, "Reset")), ignored -> requestRegionReset(region))
                    .bounds(l.contentX() + (w + gap), y1, w, 20).build());
            addRenderableWidget(Button.builder(Component.literal(regionConfirmLabel(pendingRedefineRegion, region, "Redefine")), ignored -> requestRegionRedefine(region))
                    .bounds(l.contentX() + 2 * (w + gap), y1, w, 20).build());
            addRenderableWidget(Button.builder(Component.literal(regionConfirmLabel(pendingClearRegion, region, "Clear")), ignored -> requestRegionClear(region))
                    .bounds(l.contentX() + 3 * (w + gap), y1, w, 20).build());
            addRenderableWidget(Button.builder(Component.literal(regionConfirmLabel(pendingDeleteRegion, region, "Delete")), ignored -> requestRegionDelete(region))
                    .bounds(l.contentX() + 4 * (w + gap), y1, w, 20).build());

            regionDaysBox = box(l.contentX(), y2, 48, "Days", draftRegionDays, value -> draftRegionDays = value);
            addRenderableWidget(regionDaysBox);
            addRenderableWidget(Button.builder(Component.literal("Add time"), ignored -> action("region_admin_add_time", region, "", draftRegionDays))
                    .bounds(l.contentX() + 52, y2, 66, 20).build());
            boolean paused = "paused".equalsIgnoreCase(selected.remainingText());
            addRenderableWidget(Button.builder(Component.literal(paused ? "Resume rent" : "Pause rent"), ignored ->
                            action("region_admin_pause", region, "", Boolean.toString(!paused)))
                    .bounds(l.contentX() + 122, y2, 82, 20).build());
            if (selected.rented()) {
                addRenderableWidget(Button.builder(Component.literal(unrentLabel(region)), ignored -> requestUnrent(region))
                        .bounds(l.contentX() + 208, y2, 74, 20).build());
            }
        }
    }

    private void addCompactPageControls(Layout l, int y, int offset) {
        int pages = Math.max(1, (pageData.totalItems() + Math.max(1, pageData.pageSize()) - 1) / Math.max(1, pageData.pageSize()));
        int x = l.contentX() + offset;
        Button prev = Button.builder(Component.literal("<"), ignored -> { if (pageIndex > 0) { pageIndex--; requestPage(false); } })
                .bounds(x, y, 20, 20).build(); prev.active = pageIndex > 0; addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), ignored -> { if (pageIndex + 1 < pages) { pageIndex++; requestPage(false); } })
                .bounds(x + 22, y, 20, 20).build(); next.active = pageIndex + 1 < pages; addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestPage(false))
                .bounds(x + 44, y, Math.max(44, l.contentRight() - (x + 44)), 20).build());
    }

    private void addSmallPageControls(Layout l, int y, int offset) {
        int pages = Math.max(1, (pageData.totalItems() + Math.max(1, pageData.pageSize()) - 1) / Math.max(1, pageData.pageSize()));
        Button prev = Button.builder(Component.literal("<"), ignored -> { if (pageIndex > 0) { pageIndex--; requestPage(false); } })
                .bounds(l.contentX() + offset, y, 22, 20).build(); prev.active = pageIndex > 0; addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), ignored -> { if (pageIndex + 1 < pages) { pageIndex++; requestPage(false); } })
                .bounds(l.contentX() + offset + 26, y, 22, 20).build(); next.active = pageIndex + 1 < pages; addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Refresh"), ignored -> requestPage(false))
                .bounds(l.contentX() + offset + 52, y, 58, 20).build());
    }

    private void addSettingsButtons(Layout l) {
        int categoryX = l.contentX();
        int headerOffset = settingsCategory == SettingsCategory.BORDERS ? 38 : 24;
        int categoryY = l.contentTop() + headerOffset;
        int categoryWidth = Math.min(96, Math.max(78, l.contentWidth() / 4));
        int categoryStep = l.panelHeight() < 310 ? 20 : 24;
        for (SettingsCategory category : SettingsCategory.values()) {
            Button button = Button.builder(Component.literal(category.label), ignored -> {
                        settingsCategory = category;
                        rebuildWidgets();
                    }).bounds(categoryX, categoryY, categoryWidth, 20).build();
            button.active = settingsCategory != category;
            addRenderableWidget(button);
            categoryY += categoryStep;
        }

        var s = snapshot.uiSettings();
        int x = categoryX + categoryWidth + 8;
        int y = l.contentTop() + headerOffset + 4;
        int available = Math.max(120, l.contentRight() - x);
        int gap = 6;
        int w = available >= 310 ? (available - gap) / 2 : available;
        int secondX = x + w + gap;
        boolean twoColumns = available >= 310;

        switch (settingsCategory) {
            case INTERFACE -> {
                int currentScale = SsuGuiScale.percent();
                int buttonWidth = Math.max(58, Math.min(86, (w - 8) / 2));
                Button smaller = Button.builder(Component.literal("Smaller"), ignored -> {
                            SsuGuiScale.setPercent(SsuGuiScale.smallerPercent());
                            rebuildWidgets();
                        })
                        .bounds(x, y + 27, buttonWidth, 20).build();
                smaller.active = currentScale > SsuGuiScale.MIN_PERCENT;
                addRenderableWidget(smaller);
                Button larger = Button.builder(Component.literal("Larger"), ignored -> {
                            SsuGuiScale.setPercent(SsuGuiScale.largerPercent());
                            rebuildWidgets();
                        })
                        .bounds(x + buttonWidth + 6, y + 27, buttonWidth, 20).build();
                larger.active = currentScale < SsuGuiScale.MAX_PERCENT;
                addRenderableWidget(larger);
                addRenderableWidget(Button.builder(Component.literal("SSU GUI scale: " + currentScale + "%"), ignored -> {
                            SsuGuiScale.setPercent(100);
                            rebuildWidgets();
                        })
                        .bounds(x, y, Math.min(w, buttonWidth * 2 + 6), 20).build());
                settingsTooltips.add(new SettingsTooltip(new Rect(x, y, Math.min(w, buttonWidth * 2 + 6), 20), List.of(
                        Component.literal("Scales only SSU screens; Minecraft's GUI Scale is unchanged."),
                        Component.literal("100% is the original/current SSU size. Click this value to reset to 100%."))));
            }
            case GENERAL -> {
                addSetting(x, y, w, "Dashboard hints: " + onOff(s.dashboardHints()),
                        "hints", !s.dashboardHints());
                Button blockInfo = Button.builder(Component.literal("Block information: " + onOff(s.blockInformationEnabled())), ignored ->
                                action("setting", "block_information_enabled", "", Boolean.toString(!s.blockInformationEnabled())))
                        .bounds(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w, 20).build();
                blockInfo.active = moduleActive("block_information", snapshot.moduleSettings().blockInformation());
                addRenderableWidget(blockInfo);
                if (s.blockInformationDebugAllowed()) {
                    int debugY = y + (twoColumns ? 27 : 54);
                    Button debug = Button.builder(Component.literal("Block info debug: " + onOff(s.blockInformationDebugEnabled())), ignored ->
                                    action("setting", "block_information_debug", "", Boolean.toString(!s.blockInformationDebugEnabled())))
                            .bounds(x, debugY, w, 20).build();
                    debug.active = moduleActive("block_information", snapshot.moduleSettings().blockInformation()) && s.blockInformationEnabled();
                    addRenderableWidget(debug);
                }
            }
            case IDENTITY -> {
                addSettingWithTooltip(x, y, w, "Visible title: " + onOff(s.titleVisible()),
                        "title_visible", !s.titleVisible(),
                        "Shows your selected title as a separate line above your player name.");
                addSettingWithTooltip(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w,
                        "Visible rank prefix: " + onOff(s.rankVisible()),
                        "rank_visible", !s.rankVisible(),
                        "Shows your styled rank before your overhead player name.",
                        "The rank prefix remains visible in chat even when this is OFF.");
                int row = twoColumns ? 27 : 54;
                addRenderableWidget(Button.builder(Component.literal("Choose player title"), ignored ->
                                PacketDistributor.sendToServer(new TitleManagerRequestPayload(false, nextRequestId++)))
                        .bounds(x, y + row, w, 20).build());
            }
            case COMBAT -> {
                addSettingWithTooltip(x, y, w, "Damage indicators: " + onOff(s.damageIndicatorsEnabled()),
                        "damage_indicators_enabled", !s.damageIndicatorsEnabled(),
                        "Shows red damage and green healing values around affected entities.",
                        "This setting also requires ssu.damage_indicators.use.");
                addSetting(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w,
                        "Indicator style: " + indicatorStyleLabel(s.damageIndicatorStyle()),
                        "damage_indicator_style", nextIndicatorStyle(s.damageIndicatorStyle()));

                int entityRow = twoColumns ? 27 : 54;
                addSettingWithTooltip(x, y + entityRow, w,
                        "Entity Insight: " + onOff(s.entityInsightEnabled()),
                        "entity_insight_enabled", !s.entityInsightEnabled(),
                        "Shows colored living-entity nametags for the nearest entities in range.",
                        "Green = friendly, yellow = neutral, red = hostile.",
                        "This setting also requires ssu.entity_insight.use.");
                addSettingWithTooltip(twoColumns ? secondX : x, y + entityRow + (twoColumns ? 0 : 27), w,
                        "Show health: " + onOff(s.entityInsightShowHealth()),
                        "entity_insight_health", !s.entityInsightShowHealth(),
                        "Adds current/max HP after the entity name.");

                int sliderRow = entityRow + (twoColumns ? 27 : 54);
                IntSettingSlider rangeSlider = new IntSettingSlider(x, y + sliderRow, w,
                        "Insight range", "entity_insight_range", 0, 32, s.entityInsightRange(), " blocks");
                rangeSlider.active = s.entityInsightEnabled();
                addRenderableWidget(rangeSlider);
                IntSettingSlider countSlider = new IntSettingSlider(twoColumns ? secondX : x,
                        y + sliderRow + (twoColumns ? 0 : 27), w,
                        "Max entities", "entity_insight_max_entities", 1, 50, s.entityInsightMaxEntities(), "");
                countSlider.active = s.entityInsightEnabled();
                addRenderableWidget(countSlider);
            }
            case MINIMAP -> {
                addSetting(x, y, w, "Minimap: " + onOff(s.minimapEnabled()), "minimap_enabled", !s.minimapEnabled());
                addSetting(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w,
                        "Size: " + s.minimapSize(), "minimap_size", s.minimapSize() >= 256 ? 64 : s.minimapSize() + 32);
                int row = twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "Shape: " + s.minimapShape(), "minimap_shape",
                        s.minimapShape().equals("CIRCLE") ? "RECTANGLE" : "CIRCLE");
                addSettingWithTooltip(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "Frame: " + (s.minimapTexturedFrame() ? "TEXTURED" : "CLASSIC"),
                        "minimap_frame", !s.minimapTexturedFrame(),
                        "Choose between SSU's original minimap border and the supplied custom textured frame.",
                        "Square and round minimaps automatically use their matching texture.");
                row += twoColumns ? 27 : 54;
                String nextPos = switch (s.minimapPosition()) {
                    case "TOP_LEFT" -> "TOP_RIGHT"; case "TOP_RIGHT" -> "BOTTOM_RIGHT";
                    case "BOTTOM_RIGHT" -> "BOTTOM_LEFT"; default -> "TOP_LEFT";
                };
                addSetting(x, y + row, w, "Position: " + s.minimapPosition(), "minimap_position", nextPos);
                addSetting(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "North-up: " + onOff(s.minimapNorthUp()), "minimap_northup", !s.minimapNorthUp());
                row += twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "Claim overlay: " + onOff(s.minimapShowClaims()), "minimap_claims", !s.minimapShowClaims());
                addSetting(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "Region overlay: " + onOff(s.minimapShowRegions()), "minimap_regions", !s.minimapShowRegions());
                row += twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "Marker overlay: " + onOff(s.minimapShowMarkers()), "minimap_markers", !s.minimapShowMarkers());
                addSetting(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "Day & time below map: " + onOff(s.minimapShowCalendar()), "minimap_calendar", !s.minimapShowCalendar());
            }
            case WORLD_MAP -> {
                addSetting(x, y, w, "Claim overlay: " + onOff(s.worldMapShowClaims()),
                        "worldmap_claims", !s.worldMapShowClaims());
                addSetting(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w,
                        "Region overlay: " + onOff(s.worldMapShowRegions()),
                        "worldmap_regions", !s.worldMapShowRegions());
                int row = twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "Map marker layer: " + onOff(s.worldMapShowMarkers()),
                        "worldmap_markers", !s.worldMapShowMarkers());
                addSetting(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "World marker icons: " + onOff(s.worldMarkersVisible()),
                        "world_markers", !s.worldMarkersVisible());
                row += twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "Marker beams: " + onOff(s.markerBeamsVisible()),
                        "marker_beams", !s.markerBeamsVisible());
                int nextDistance = s.markerBeamDistance() >= 512 ? 32 : Math.min(512, s.markerBeamDistance() + 32);
                addSetting(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "Beam distance: " + s.markerBeamDistance(),
                        "marker_beam_distance", nextDistance);
                row += twoColumns ? 27 : 54;
                addSetting(x, y + row, w,
                        "Live terrain: " + s.mapLiveUpdateRadiusChunks() + " chunks",
                        "map_live_update_radius", nextMapLiveUpdateRadius(s.mapLiveUpdateRadiusChunks()));
            }
            case UTILITY_MINING -> {
                addSetting(x, y, w, "Treecapitator: " + onOff(s.treecapitatorEnabled()),
                        "treecapitator_enabled", !s.treecapitatorEnabled());
                addSetting(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w,
                        "Activation: " + activationLabel(s.treecapitatorActivation()),
                        "treecapitator_activation", nextActivation(s.treecapitatorActivation()));
                int row = twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "Tree outline: " + miningColorName(s.treecapitatorOutlineColor()),
                        "treecapitator_color", colorHex(nextMiningColor(s.treecapitatorOutlineColor())));
                addSetting(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "Tree glow: " + s.treecapitatorOutlineBrightness() + "%",
                        "treecapitator_brightness", nextBrightness(s.treecapitatorOutlineBrightness()));
                row += twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "Veinminer: " + onOff(s.veinminerEnabled()),
                        "veinminer_enabled", !s.veinminerEnabled());
                addSetting(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "Activation: " + activationLabel(s.veinminerActivation()),
                        "veinminer_activation", nextActivation(s.veinminerActivation()));
                row += twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "Vein outline: " + miningColorName(s.veinminerOutlineColor()),
                        "veinminer_color", colorHex(nextMiningColor(s.veinminerOutlineColor())));
                addSetting(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "Vein glow: " + s.veinminerOutlineBrightness() + "%",
                        "veinminer_brightness", nextBrightness(s.veinminerOutlineBrightness()));
                row += twoColumns ? 27 : 54;
                addSetting(x, y + row, w, "Tree mining info: " + onOff(s.treecapitatorInfoEnabled()),
                        "treecapitator_info", !s.treecapitatorInfoEnabled());
                addSetting(twoColumns ? secondX : x, y + row + (twoColumns ? 0 : 27), w,
                        "Vein mining info: " + onOff(s.veinminerInfoEnabled()),
                        "veinminer_info", !s.veinminerInfoEnabled());
            }
            case BORDERS -> {
                Button claims = Button.builder(Component.literal("Enable claim borders: " + onOff(snapshot.claimBordersVisible())), ignored ->
                                action("border", "claims", "", Boolean.toString(!snapshot.claimBordersVisible())))
                        .bounds(x, y, w, 20).build();
                claims.active = snapshot.canViewClaimBorders();
                addRenderableWidget(claims);
                Button otherClaims = Button.builder(Component.literal("Show other claims: " + onOff(snapshot.showOtherClaims())), ignored ->
                                action("border", "other_claims", "", Boolean.toString(!snapshot.showOtherClaims())))
                        .bounds(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w, 20).build();
                otherClaims.active = snapshot.canViewClaimBorders();
                addRenderableWidget(otherClaims);
                int regionRow = twoColumns ? 27 : 54;
                Button regions = Button.builder(Component.literal("Region borders: " + onOff(snapshot.regionBordersVisible())), ignored ->
                                action("border", "regions", "", Boolean.toString(!snapshot.regionBordersVisible())))
                        .bounds(x, y + regionRow, w, 20).build();
                regions.active = snapshot.canViewRegionBorders();
                addRenderableWidget(regions);
                Button gameBorder = Button.builder(Component.literal("Minigame border: " + onOff(snapshot.minigameGameBorderVisible())), ignored ->
                                action("border", "minigame_game", "", Boolean.toString(!snapshot.minigameGameBorderVisible())))
                        .bounds(twoColumns ? secondX : x, y + (twoColumns ? 27 : 81), w, 20).build();
                gameBorder.active = moduleActive("minigames", snapshot.moduleSettings().minigames());
                addRenderableWidget(gameBorder);
                Button spectatorBorder = Button.builder(Component.literal("Spectator border: " + onOff(snapshot.minigameSpectatorBorderVisible())), ignored ->
                                action("border", "minigame_spectator", "", Boolean.toString(!snapshot.minigameSpectatorBorderVisible())))
                        .bounds(x, y + (twoColumns ? 54 : 108), w, 20).build();
                spectatorBorder.active = moduleActive("minigames", snapshot.moduleSettings().minigames());
                addRenderableWidget(spectatorBorder);
            }
            case MAIL -> {
                addSettingWithTooltip(x, y, w,
                        "Claimed player mail: " + keepDelete(s.mailAutoDeletePlayerAttachments()),
                        "mail_auto_delete_player", !s.mailAutoDeletePlayerAttachments(),
                        "Mail with attachments sent by another player.",
                        "DELETE removes it after all items and money are claimed.",
                        "KEEP leaves it in your inbox until normal deletion or expiry.");
                addSettingWithTooltip(twoColumns ? secondX : x, y + (twoColumns ? 0 : 27), w,
                        "Claimed server mail: " + keepDelete(s.mailAutoDeleteSystemAttachments()),
                        "mail_auto_delete_system", !s.mailAutoDeleteSystemAttachments(),
                        "Mail with attachments from server or recovery systems.",
                        "DELETE removes it after all items and money are claimed.",
                        "KEEP leaves it in your inbox until normal deletion or expiry.");
                addSettingWithTooltip(x, y + (twoColumns ? 27 : 54), w,
                        "Claimed auction mail: " + keepDelete(s.mailAutoDeleteAuctionAttachments()),
                        "mail_auto_delete_auction", !s.mailAutoDeleteAuctionAttachments(),
                        "Mail with attachments created by auction deliveries.",
                        "DELETE removes it after all items and money are claimed.",
                        "KEEP leaves it in your inbox until normal deletion or expiry.");
            }
        }
    }

    private final class IntSettingSlider extends AbstractSliderButton {
        private final String label;
        private final String key;
        private final int min;
        private final int max;
        private final String suffix;
        private int lastSent;

        private IntSettingSlider(int x, int y, int width, String label, String key,
                                 int min, int max, int current, String suffix) {
            super(x, y, width, 20, Component.empty(), normalized(min, max, current));
            this.label = label;
            this.key = key;
            this.min = min;
            this.max = max;
            this.suffix = suffix == null ? "" : suffix;
            this.lastSent = clamp(current);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (label != null) setMessage(Component.literal(label + ": " + currentValue() + suffix));
        }

        @Override
        protected void applyValue() {
            if (key == null) return;
            int current = currentValue();
            if (current == lastSent) return;
            lastSent = current;
            PacketDistributor.sendToServer(new PlayerUiSettingUpdatePayload(key, Integer.toString(current)));
        }

        private int currentValue() {
            return clamp((int) Math.round(min + value * (max - min)));
        }

        private int clamp(int candidate) {
            return Math.max(min, Math.min(max, candidate));
        }

        private static double normalized(int min, int max, int current) {
            if (max <= min) return 0.0D;
            int safe = Math.max(min, Math.min(max, current));
            return (double) (safe - min) / (double) (max - min);
        }
    }

    private void addSetting(int x, int y, int w, String label, String key, Object value) {
        addRenderableWidget(Button.builder(Component.literal(label), ignored -> action("setting", key, "", String.valueOf(value)))
                .bounds(x, y, w, 20).build());
    }

    private void addSettingWithTooltip(int x, int y, int w, String label, String key, Object value, String... lines) {
        addSetting(x, y, w, label, key, value);
        settingsTooltips.add(new SettingsTooltip(new Rect(x, y, w, 20),
                java.util.Arrays.stream(lines).<Component>map(Component::literal).toList()));
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
                            ? "Choose rank" : selectedAssignableRank) + " ▾"),
                            ignored -> togglePermissionDropdown("rank"))
                    .bounds(rank.x(), rank.y(), rank.width(), rank.height()).build());
            Button add = Button.builder(Component.literal("Add"), ignored -> {
                        if (selectedAssignableRank.isBlank()) setNotice("Choose a rank first.", true);
                        else action("permission_add_rank", selectedPermissionTarget, "", selectedAssignableRank);
                    }).bounds(l.contentRight() - 110, y + 24, 52, 20).build();
            add.active = !selectedAssignableRank.isBlank();
            addRenderableWidget(add);
            Button remove = Button.builder(Component.literal("Remove"), ignored -> {
                        if (selectedAssignableRank.isBlank()) setNotice("Choose a rank first.", true);
                        else action("permission_remove_rank", selectedPermissionTarget, "", selectedAssignableRank);
                    }).bounds(l.contentRight() - 54, y + 24, 54, 20).build();
            remove.active = !selectedAssignableRank.isBlank();
            addRenderableWidget(remove);
        }

        int permissionSearchX;
        if (!"claim_role".equals(permissionMode)) {
            Rect dimension = permissionDimensionBounds(l);
            addRenderableWidget(Button.builder(Component.literal("Dimension: " + selectedPermissionDimensionLabel() + " ▾"),
                            ignored -> togglePermissionDropdown("dimension"))
                    .bounds(dimension.x(), dimension.y(), dimension.width(), dimension.height()).build());
            permissionSearchX = dimension.x() + dimension.width() + 6;
        } else {
            permissionSearchX = l.contentX();
        }
        permissionSearchBox = box(permissionSearchX, y + 48,
                Math.max(90, l.contentRight() - permissionSearchX - 66),
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
            int checkX = l.contentRight() - 68;
            boolean immutableClaimOwner = "claim_role".equals(permissionMode) && "owner".equals(selectedPermissionTarget);
            if ("boolean".equals(entry.valueType())) {
                String label = permissionBooleanLabel(entry);
                Button toggle = Button.builder(Component.literal(label), ignored -> toggleBooleanPermission(entry))
                        .bounds(l.contentRight() - 174, rowY + 4, 102, 20).build();
                toggle.active = !immutableClaimOwner;
                addRenderableWidget(toggle);
            } else {
                EditBox value = box(l.contentRight() - 214, rowY + 4, 102,
                        "Value", permissionDraftValues.getOrDefault(entry.key(), ""),
                        text -> permissionDraftValues.put(entry.key(), text));
                if ("integer".equals(entry.valueType())) value.setMaxLength(12);
                addRenderableWidget(value);
                permissionValueInputs.put(entry.key(), value);
                addRenderableWidget(Button.builder(Component.literal("Set"), ignored -> setPermissionValue(entry,
                                permissionDraftValues.getOrDefault(entry.key(), "")))
                        .bounds(l.contentRight() - 108, rowY + 4, 36, 20).build());
            }
            Button check = Button.builder(Component.literal("Check"), ignored ->
                            action("permission_check", selectedPermissionTarget, entry.key(), ""))
                    .bounds(checkX, rowY + 4, 42, 20).build();
            check.active = "player".equals(permissionMode) && !selectedPermissionTarget.isBlank();
            addRenderableWidget(check);
            Button reset = Button.builder(Component.literal("×"), ignored -> unsetPermissionValue(entry))
                    .bounds(resetX, rowY + 4, 22, 20).build();
            reset.active = !immutableClaimOwner && !entry.directValue().isBlank();
            addRenderableWidget(reset);
        }
        addPermissionPagination(l);
    }

    private void togglePermissionDropdown(String dropdown) {
        boolean modeOpen = "mode".equals(dropdown) && !permissionModeDropdownOpen;
        boolean targetOpen = "target".equals(dropdown) && !permissionTargetDropdownOpen;
        boolean dimensionOpen = "dimension".equals(dropdown) && !permissionDimensionDropdownOpen;
        boolean rankOpen = "rank".equals(dropdown) && !permissionRankDropdownOpen;
        permissionModeDropdownOpen = modeOpen;
        permissionTargetDropdownOpen = targetOpen;
        permissionDimensionDropdownOpen = dimensionOpen;
        permissionRankDropdownOpen = rankOpen;
        permissionDropdownScroll = 0;
    }

    private String permissionModeLabel() {
        return switch (permissionMode) {
            case "rank" -> "Ranks";
            case "claim_role" -> "Claim roles";
            default -> "Players";
        };
    }

    private String permissionTargetPrompt() {
        return switch (permissionMode) {
            case "rank" -> "Choose rank";
            case "claim_role" -> "Choose role";
            default -> "Choose player";
        };
    }

    private String selectedPermissionDimensionLabel() {
        if (selectedPermissionDimension.isBlank()) return "All dimensions";
        return permissionData.dimensions().stream()
                .filter(entry -> entry.id().equals(selectedPermissionDimension))
                .map(SsuPermissionEditorDataPayload.TargetEntry::label)
                .findFirst().orElse(selectedPermissionDimension);
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
        boolean dimensionScoped = !"claim_role".equals(permissionMode) && !selectedPermissionDimension.isBlank();
        String action = switch (permissionMode) {
            case "rank" -> dimensionScoped ? "permission_rank_dimension_set" : "permission_rank_set";
            case "claim_role" -> "permission_claim_context_set";
            default -> dimensionScoped ? "permission_player_dimension_set" : "permission_player_set";
        };
        String sentValue = dimensionScoped ? selectedPermissionDimension + "\n" + value : value;
        action(action, selectedPermissionTarget, entry.key(), sentValue);
    }

    private void unsetPermissionValue(SsuPermissionEditorDataPayload.PermissionEntry entry) {
        if (selectedPermissionTarget.isBlank()) return;
        permissionDraftValues.remove(entry.key());
        boolean dimensionScoped = !"claim_role".equals(permissionMode) && !selectedPermissionDimension.isBlank();
        String action = switch (permissionMode) {
            case "rank" -> dimensionScoped ? "permission_rank_dimension_unset" : "permission_rank_unset";
            case "claim_role" -> "permission_claim_context_unset";
            default -> dimensionScoped ? "permission_player_dimension_unset" : "permission_player_unset";
        };
        action(action, selectedPermissionTarget, entry.key(), dimensionScoped ? selectedPermissionDimension : "");
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

    private Rect permissionDimensionBounds(Layout l) {
        return new Rect(l.contentX(), l.contentTop() + 52, Math.min(220, Math.max(140, l.contentWidth() / 3)), 20);
    }

    private Rect permissionRankBounds(Layout l) {
        return new Rect(l.contentRight() - 268, l.contentTop() + 28, 150, 20);
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
            addRenderableWidget(Button.builder(Component.literal("Manage"), ignored ->
                            PacketDistributor.sendToServer(new PlayerManagementRequestPayload(selectedProfilePlayer, nextRequestId++)))
                    .bounds(l.contentRight() - 82, l.footerY(), 82, 20).build());
            if (snapshot.adminAccess().permissions()) {
                addRenderableWidget(Button.builder(Component.literal("Permissions"), ignored ->
                                openPermissionEditorForPlayer(selectedProfilePlayer, selectedProfileLabel))
                        .bounds(l.contentRight() - 172, l.footerY(), 86, 20).build());
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
    private void addRentOperationButtons(Layout l) {
        int top = l.contentTop() + 2;
        int half = Math.max(150, (l.contentWidth() - 8) / 2);
        regionPlayerRefundBox = box(l.contentX(), top + 18, 54, "0-100", draftRegionPlayerRefund,
                value -> draftRegionPlayerRefund = value);
        regionPlayerRefundBox.setMaxLength(3);
        addRenderableWidget(regionPlayerRefundBox);
        addRenderableWidget(Button.builder(Component.literal("Set player refund"), ignored ->
                        action("region_rent_refund", "player", "", draftRegionPlayerRefund))
                .bounds(l.contentX() + 58, top + 18, half - 58, 20).build());
        int adminX = l.contentX() + half + 8;
        regionAdminRefundBox = box(adminX, top + 18, 54, "0-100", draftRegionAdminRefund,
                value -> draftRegionAdminRefund = value);
        regionAdminRefundBox.setMaxLength(3);
        addRenderableWidget(regionAdminRefundBox);
        addRenderableWidget(Button.builder(Component.literal("Set admin refund"), ignored ->
                        action("region_rent_refund", "admin", "", draftRegionAdminRefund))
                .bounds(adminX + 58, top + 18, Math.max(72, l.contentRight() - adminX - 58), 20).build());
        addSearchAt(l, top + 44);
        for (int i = 0; i < pageData.rentOperations().size(); i++) {
            int row = i;
            addRenderableWidget(Button.builder(Component.literal("Details"), ignored -> select(row))
                    .bounds(l.contentRight() - 62, rowY(l, i, 110), 62, 20).build());
        }
        addPagination(l, 0);
    }
    private void addJobButtons(Layout l) { addListSearch(l); for(int i=0;i<pageData.jobs().size();i++){
        var entry=pageData.jobs().get(i); addRenderableWidget(Button.builder(Component.literal("Cancel"), ignored -> action("job_cancel",entry.id(),"",""))
                .bounds(l.contentRight()-58,rowY(l,i),58,20).build()); } addPagination(l,0); }
    private void addCoreButtons(Layout l) {
        addRenderableWidget(Button.builder(Component.literal("Refresh shell"), ignored -> action("refresh_shell", "", "", ""))
                .bounds(l.contentX(), l.contentTop() + 122, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset counters"), ignored -> action("core_reset", "", "", ""))
                .bounds(l.contentX() + 106, l.contentTop() + 122, 100, 20).build());
    }
    private void addProfileButtons(Layout l) {
        String selected = "Selected title: " + blank(snapshot.selectedTitle());
        int textWidth = font.width(selected);
        int buttonX = Math.min(l.contentRight() - 96, l.contentX() + textWidth + 18);
        addRenderableWidget(Button.builder(Component.literal("Choose title"), ignored ->
                        PacketDistributor.sendToServer(new TitleManagerRequestPayload(false, nextRequestId++)))
                .bounds(buttonX, l.contentTop() + 24, 92, 20).build());
    }

    private void addPagination(Layout l, int offset) {
        int size = Math.max(1, pageData.pageSize());
        int pages = (int) Math.max(1L, ((long) pageData.totalItems() + size - 1L) / size);
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
    private void submitEconomyHistoryLimit() {
        action("economy_history_limit", "", "", draftEconomyHistoryLimit);
    }
    private void select(int row) { selectedRow = selectedRow == row ? -1 : row; rebuildWidgets(); }

    private String deleteHomeLabel(String home) { return pendingDeleteHome.equalsIgnoreCase(home) ? "Confirm" : "Delete"; }
    private void requestDeleteHome(String home) {
        if (pendingDeleteHome.equalsIgnoreCase(home)) { pendingDeleteHome = ""; action("home_delete", home, homesClaimName, ""); return; }
        pendingDeleteHome = home; pendingDeleteWarp = "";
        setNotice("Click Confirm again to permanently delete home '" + home + "'.", true);
    }
    private String deleteWarpLabel(String warp) { return pendingDeleteWarp.equalsIgnoreCase(warp) ? "Confirm" : "Delete"; }
    private void requestDeleteWarp(String warp) {
        if (pendingDeleteWarp.equalsIgnoreCase(warp)) { pendingDeleteWarp = ""; action(page == Page.MY_WARPS ? "player_warp_delete" : "warp_delete", warp, page.remoteId(), ""); return; }
        pendingDeleteWarp = warp; pendingDeleteHome = ""; pendingSetPlayerWarp = "";
        setNotice("Click Confirm again to permanently delete warp '" + warp + "'.", true);
    }
    private String deleteAdminClaimLabel(String claimId) { return pendingDeleteAdminClaim.equals(claimId) ? "Confirm" : "Delete"; }
    private void requestDeleteAdminClaim(SsuMenuPageDataPayload.ClaimEntry entry) {
        if (pendingDeleteAdminClaim.equals(entry.id())) { pendingDeleteAdminClaim = ""; action("admin_claim_delete", entry.id(), "", ""); return; }
        pendingDeleteAdminClaim = entry.id();
        setNotice("Click Confirm again to permanently delete claim '" + entry.name() + "'.", true);
    }

    private String regionConfirmLabel(String pending, String region, String normal) {
        return pending.equalsIgnoreCase(region) ? "Confirm" : normal;
    }
    private void requestRegionReset(String region) {
        if (pendingResetRegion.equalsIgnoreCase(region)) { pendingResetRegion = ""; action("region_admin_reset", region, "", ""); return; }
        clearRegionConfirmations(); pendingResetRegion = region;
        setNotice("Click Confirm again to restore region '" + region + "' from its saved snapshot.", true);
    }
    private void requestRegionRedefine(String region) {
        if (pendingRedefineRegion.equalsIgnoreCase(region)) { pendingRedefineRegion = ""; action("region_admin_redefine", region, "", ""); return; }
        clearRegionConfirmations(); pendingRedefineRegion = region;
        setNotice("Click Confirm again to replace the bounds of '" + region + "' with your current selection.", true);
    }
    private void requestRegionClear(String region) {
        if (pendingClearRegion.equalsIgnoreCase(region)) { pendingClearRegion = ""; action("region_admin_clear", region, "", ""); return; }
        clearRegionConfirmations(); pendingClearRegion = region;
        setNotice("Click Confirm again to remove every block inside region '" + region + "'.", true);
    }
    private void requestRegionDelete(String region) {
        if (pendingDeleteRegion.equalsIgnoreCase(region)) { pendingDeleteRegion = ""; action("region_admin_delete", region, "", ""); return; }
        clearRegionConfirmations(); pendingDeleteRegion = region;
        setNotice("Click Confirm again to permanently delete region '" + region + "'. Its snapshot will be archived first.", true);
    }
    private void clearRegionConfirmations() {
        pendingDeleteRegion = ""; pendingResetRegion = ""; pendingClearRegion = ""; pendingRedefineRegion = "";
    }

    private String deleteRankLabel(String rank) { return pendingDeleteRank.equalsIgnoreCase(rank) ? "Confirm" : "Delete"; }
    private void requestDeleteRank(String rank) {
        if (pendingDeleteRank.equalsIgnoreCase(rank)) { pendingDeleteRank = ""; action("rank_delete", rank, "", ""); return; }
        pendingDeleteRank = rank;
        setNotice("Click Confirm again to delete rank '" + rank + "'. Assigned players will fall back to the default rank.", true);
    }

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
        PacketDistributor.sendToServer(new SsuPropertySettingsRequestPayload(
                kind, target, nextRequestId++));
    }

    private void action(String action, String target, String secondary, String value) {
        long id = nextRequestId++;
        latestActionRequest = id;
        PacketDistributor.sendToServer(new SsuMenuActionPayload(action, target, secondary, value, id));
    }

    public void refreshCurrentPage() {
        if (page.hasRemoteData()) requestPage(false);
        else rebuildWidgets();
    }

    public void refreshRemotePage() {
        if (page.hasRemoteData()) requestPage(false);
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
        String query = page == Page.HOMES ? homesClaimName
                : page == Page.TRAVEL || page == Page.TRAVEL_ADMIN ? travelFilter + "|" + draftSearch
                : page == Page.TRANSACTIONS ? transactionRequestQuery()
                : draftSearch;
        PacketDistributor.sendToServer(new SsuMenuPageRequestPayload(
                page.remoteId(), pageIndex, pageRequestSize(), query, id));
    }

    private int pageRequestSize() {
        Layout l = layout();
        if (page == Page.RANKS) return Math.min(4, rowsThatFit(l, 110));
        if (page == Page.TRAVEL) return Math.min(PAGE_SIZE, rowsThatFit(l, 82));
        if (page == Page.TRAVEL_ADMIN) return Math.min(PAGE_SIZE, rowsThatFit(l, l.contentWidth() < 500 ? 130 : 106));
        if (page == Page.MY_WARPS) return Math.min(PAGE_SIZE, rowsThatFit(l, 86));
        if (page == Page.CLAIM_TAX) return Math.min(7, rowsThatFit(l, 86));
        if (page == Page.WALLET) return Math.min(PAGE_SIZE, rowsThatFit(l, 78));
        if (page == Page.TRANSACTIONS) return Math.min(PAGE_SIZE, rowsThatFit(l, 108));
        if (page == Page.REGION_ADMIN) return Math.min(4, rowsThatFit(l, l.contentWidth() < 440 ? 130 : 105));
        if (page == Page.UTILITY_MINING_ADMIN) return 10;
        if (page == Page.MAINTENANCE) return Math.min(5, rowsThatFit(l, 82));
        if (page == Page.RENT_OPERATIONS) return Math.min(PAGE_SIZE, rowsThatFit(l, 110));
        return Math.min(PAGE_SIZE, rowsThatFit(l, 58));
    }

    private int rowsThatFit(Layout l, int offset) {
        return Math.max(1, (l.footerY() - (l.contentTop() + offset) - 2) / 27);
    }

    private void requestPlayerProfile(boolean reset) {
        if (page != Page.PLAYER_INFO) return;
        if (reset) playerProfilePermissionPage = 0;
        playerProfileLoading = true;
        long id = nextRequestId++;
        latestPlayerProfileRequest = id;
        PacketDistributor.sendToServer(new SsuPlayerProfileRequestPayload(
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
        PacketDistributor.sendToServer(new SsuPermissionEditorRequestPayload(
                permissionMode,
                selectedPermissionTarget,
                selectedPermissionDimension,
                draftPermissionTargetSearch,
                draftPermissionSearch,
                pageIndex,
                permissionPageSize(),
                id
        ));
    }

    public void openHomesForClaim(String claimName) {
        String selectedClaim = claimName == null ? "" : claimName.trim();
        if (selectedClaim.isBlank()) {
            setNotice("Choose a claim before managing homes.", true);
            return;
        }
        previousPage = Page.CLAIMS;
        page = Page.HOMES;
        homesClaimName = selectedClaim;
        pageIndex = 0;
        selectedRow = -1;
        draftHomeName = "";
        pendingDeleteHome = "";
        loading = false;
        pageData = SsuMenuPageDataPayload.empty(Page.HOMES.remoteId(), 0, PAGE_SIZE, 0, "", false);
        rebuildWidgets();
        requestPage(false);
    }

    private void openPage(Page target) {
        if (target == Page.MAIL) {
            PacketDistributor.sendToServer(new MailActionPayload("open_mailbox", "", "inbox", 0, nextRequestId++));
            return;
        }
        if (target == Page.AUCTION_HOUSE) {
            action("auction_open", "", "", "");
            return;
        }
        if (target == Page.QUESTS) {
            PacketDistributor.sendToServer(new QuestBookRequestPayload("open", "", "menu", 0, nextRequestId++));
            return;
        }
        if (target == Page.ACHIEVEMENTS || target == Page.ACHIEVEMENTS_ADMIN) {
            PacketDistributor.sendToServer(new be.winnetrie.mod.simpleserverutilities.network.AchievementMenuRequestPayload(
                    target == Page.ACHIEVEMENTS_ADMIN ? "open_admin" : "open", "", "", "all", 0, nextRequestId++));
            return;
        }
        if (target == Page.MINIGAMES) {
            PacketDistributor.sendToServer(new MinigameLobbyRequestPayload("open", "", nextRequestId++));
            return;
        }
        if (target == Page.MINIGAME_ADMIN) {
            PacketDistributor.sendToServer(new MinigameLobbyRequestPayload("open_admin", "", nextRequestId++));
            return;
        }
        if (target == Page.DUNGEONS) {
            PacketDistributor.sendToServer(new DungeonLobbyRequestPayload("open", "", nextRequestId++));
            return;
        }
        if (target == Page.DIMENSIONS) {
            PacketDistributor.sendToServer(new SsuDimensionManagerRequestPayload("", nextRequestId++));
            return;
        }
        if (target == Page.KITS) {
            PacketDistributor.sendToServer(new KitRequestPayload(false, "", nextRequestId++));
            return;
        }
        if (target == Page.KIT_ADMIN) {
            PacketDistributor.sendToServer(new KitRequestPayload(true, "", nextRequestId++));
            return;
        }
        if (target == Page.MINES) {
            PacketDistributor.sendToServer(new MineRequestPayload(false, "", nextRequestId++));
            return;
        }
        if (target == Page.MINE_ADMIN) {
            PacketDistributor.sendToServer(new MineRequestPayload(true, "", nextRequestId++));
            return;
        }
        if (target == Page.JAIL_ADMIN) {
            PacketDistributor.sendToServer(new be.winnetrie.mod.simpleserverutilities.network.JailAdminRequestPayload("", nextRequestId++));
            return;
        }
        if (target == Page.SUPPORT) {
            PacketDistributor.sendToServer(new be.winnetrie.mod.simpleserverutilities.network.ServerOperationsRequestPayload(false, nextRequestId++));
            return;
        }
        if (target == Page.SERVER_OPERATIONS) {
            PacketDistributor.sendToServer(new be.winnetrie.mod.simpleserverutilities.network.ServerOperationsRequestPayload(true, nextRequestId++));
            return;
        }
        if (target == Page.ONBOARDING_ADMIN) {
            PacketDistributor.sendToServer(new OnboardingAdminRequestPayload(nextRequestId++));
            return;
        }
        if (target == page) return;
        if (target != Page.HOMES) homesClaimName = "";
        if (target == Page.TRAVEL || target == Page.TRAVEL_ADMIN) travelFilter = "all";
        if (target == Page.TRANSACTIONS) {
            selectedTransactionPlayerId = "";
            selectedTransactionPlayerLabel = "";
            draftTransactionPlayer = "";
        }
        previousPage = page; page = target; pageIndex = 0; if (target == Page.ADMIN_TOOLS) adminToolScroll = 0; if (target == Page.ADMIN) adminModuleScroll = 0; if (target == Page.MODULE_SETTINGS) moduleSettingsScroll = 0; selectedRow = -1; draftSearch = ""; pendingUnrentRegion = ""; pendingDeleteHologram = ""; pendingDeleteHome = ""; pendingDeleteWarp = ""; pendingSetPlayerWarp = ""; pendingDeleteAdminClaim = ""; pendingDeleteRank = ""; clearRegionConfirmations(); pendingResetAllBorderColors = false; pendingEnableClaimTax = false; pendingResetStatistic = ""; pendingDeleteStatistic = "";
        loading = false;
        if (target != Page.PERMISSIONS) permissionLoading = false;
        if (target != Page.PLAYER_INFO) playerProfileLoading = false;
        closePermissionDropdowns();
        closePlayerProfileDropdown();
        closeTransactionPlayerDropdown();
        pageData = SsuMenuPageDataPayload.empty(target.remoteId(), 0, PAGE_SIZE, 0, "", false);
        rebuildWidgets(); requestPage(false);
    }
    private void goBack() {
        if (page == Page.HOME) { onClose(); return; }
        if (page == Page.HOMES) homesClaimName = "";
        if (isEconomicsChild(page)) {
            page = Page.ECONOMICS;
            previousPage = Page.ADMIN;
        } else if (page == Page.ECONOMICS) {
            page = Page.ADMIN;
            previousPage = Page.HOME;
        } else {
            page = previousPage == page ? Page.HOME : previousPage;
            previousPage = Page.HOME;
        }
        pageIndex = 0; selectedRow = -1; draftSearch = ""; pendingUnrentRegion = ""; pendingDeleteHologram = ""; pendingDeleteHome = ""; pendingDeleteWarp = ""; pendingSetPlayerWarp = ""; pendingDeleteAdminClaim = ""; pendingDeleteRank = ""; clearRegionConfirmations(); pendingResetAllBorderColors = false; pendingEnableClaimTax = false; pendingResetStatistic = ""; pendingDeleteStatistic = "";
        loading = false;
        if (page != Page.PERMISSIONS) permissionLoading = false;
        if (page != Page.PLAYER_INFO) playerProfileLoading = false;
        closePermissionDropdowns(); closePlayerProfileDropdown(); closeTransactionPlayerDropdown();
        pageData = SsuMenuPageDataPayload.empty(page.remoteId(), 0, PAGE_SIZE, 0, "", false);
        rebuildWidgets(); requestPage(false);
    }
    private void closePlayerProfileDropdown() {
        playerProfileDropdownOpen = false;
        playerProfileDropdownScroll = 0;
    }

    private void closeTransactionPlayerDropdown() {
        transactionPlayerDropdownOpen = false;
        transactionPlayerDropdownScroll = 0;
    }

    private static boolean isEconomicsChild(Page page) {
        return page == Page.ACCOUNTS || page == Page.TRANSACTIONS || page == Page.AUCTION_TAX
                || page == Page.CLAIM_TAX || page == Page.WARP_RENTAL || page == Page.RENT_OPERATIONS;
    }

    private String transactionRequestQuery() {
        String manual = safeQueryPart(draftTransactionPlayer);
        String selected = manual.isBlank() ? safeQueryPart(selectedTransactionPlayerId) : "";
        return selected + "|" + manual + "|" + safeQueryPart(draftSearch);
    }

    private static String safeQueryPart(String value) {
        return value == null ? "" : value.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private Rect transactionPlayerBounds(Layout l) {
        int width = Math.min(180, Math.max(112, l.contentWidth() / 3));
        return new Rect(l.contentX(), l.contentTop() + 22, width, 20);
    }

    private String transactionPlayerButtonLabel() {
        return selectedTransactionPlayerId.isBlank() ? "All players ▾"
                : trim(selectedTransactionPlayerLabel.isBlank() ? selectedTransactionPlayerId
                : selectedTransactionPlayerLabel, 24) + " ▾";
    }

    private List<SsuMenuPageDataPayload.AccountEntry> transactionPlayerOptions() {
        ArrayList<SsuMenuPageDataPayload.AccountEntry> options = new ArrayList<>();
        options.add(new SsuMenuPageDataPayload.AccountEntry("", "All players", "", 0L, 0L, 0L));
        options.addAll(pageData.accounts());
        return List.copyOf(options);
    }

    private List<SsuMenuPageDataPayload.AccountEntry> visibleTransactionPlayers() {
        return slice(transactionPlayerOptions(), transactionPlayerDropdownScroll, DROPDOWN_VISIBLE_ROWS);
    }

    private void closePermissionDropdowns() {
        permissionModeDropdownOpen = false;
        permissionTargetDropdownOpen = false;
        permissionDimensionDropdownOpen = false;
        permissionRankDropdownOpen = false;
        permissionDropdownScroll = 0;
    }

    private void setNotice(String text, boolean error) { notice = text; noticeError = error; rebuildWidgets(); }
    private void syncEconomyDrafts() {
        draftEconomyHistoryLimit = Integer.toString(snapshot.economy().transactionHistoryLimit());
        draftRegionPlayerRefund = Integer.toString(snapshot.economy().playerCancelRefundPercent());
        draftRegionAdminRefund = Integer.toString(snapshot.economy().adminCancelRefundPercent());
    }

    private void syncAuctionTaxDraft(SsuMenuPageDataPayload payload) {
        payload.permissions().stream()
                .filter(entry -> "auction_house_tax".equals(entry.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value)
                .findFirst()
                .ifPresent(value -> draftAuctionTax = value);
    }

    private boolean handlePermissionDropdownClick(double mouseX, double mouseY) {
        if (page != Page.PERMISSIONS
                || (!permissionModeDropdownOpen && !permissionTargetDropdownOpen
                && !permissionDimensionDropdownOpen && !permissionRankDropdownOpen)) {
            return false;
        }
        Layout l = layout();
        Rect activeAnchor = permissionModeDropdownOpen ? permissionModeBounds(l)
                : permissionTargetDropdownOpen ? permissionTargetBounds(l)
                : permissionDimensionDropdownOpen ? permissionDimensionBounds(l)
                : permissionRankBounds(l);
        // Only the anchor that opened this dropdown may receive the click so it can toggle closed.
        // Every other click is modal: it is handled by the dropdown or consumed after closing it.
        if (activeAnchor.contains(mouseX, mouseY)) return false;

        if (permissionModeDropdownOpen) {
            Rect list = dropdownListBounds(permissionModeBounds(l), 3);
            if (list.contains(mouseX, mouseY)) {
                int index = (int) ((mouseY - list.y()) / 20.0);
                String nextMode = index == 1 ? "rank" : index == 2 ? "claim_role" : "player";
                if (!nextMode.equals(permissionMode)) {
                    permissionMode = nextMode;
                    selectedPermissionTarget = "";
                    selectedPermissionLabel = "";
                    selectedPermissionDimension = "";
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
        } else if (permissionDimensionDropdownOpen) {
            List<SsuPermissionEditorDataPayload.TargetEntry> options = visiblePermissionDimensions();
            Rect list = dropdownListBounds(permissionDimensionBounds(l), Math.max(1, options.size()));
            if (list.contains(mouseX, mouseY) && !options.isEmpty()) {
                int index = (int) ((mouseY - list.y()) / 20.0);
                if (index >= 0 && index < options.size()) {
                    selectedPermissionDimension = options.get(index).id();
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
        rebuildWidgets();
        return true;
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
        rebuildWidgets();
        return true;
    }

    private boolean handleTransactionPlayerDropdownClick(double mouseX, double mouseY) {
        if (page != Page.TRANSACTIONS || !transactionPlayerDropdownOpen) return false;
        Layout l = layout();
        Rect anchor = transactionPlayerBounds(l);
        if (anchor.contains(mouseX, mouseY)) return false;
        List<SsuMenuPageDataPayload.AccountEntry> options = visibleTransactionPlayers();
        Rect list = dropdownListBounds(anchor, Math.max(1, options.size()));
        if (list.contains(mouseX, mouseY) && !options.isEmpty()) {
            int index = (int) ((mouseY - list.y()) / 20.0);
            if (index >= 0 && index < options.size()) {
                SsuMenuPageDataPayload.AccountEntry selected = options.get(index);
                selectedTransactionPlayerId = selected.id();
                selectedTransactionPlayerLabel = selected.name();
                draftTransactionPlayer = "";
                pageIndex = 0;
                closeTransactionPlayerDropdown();
                requestPage(false);
                rebuildWidgets();
                playClick();
                return true;
            }
        }
        closeTransactionPlayerDropdown();
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == Page.ADMIN_TOOLS && scrollY != 0.0) {
            int maximum = Math.max(0, adminTools().size() - adminToolVisibleRows(layout()));
            int next = adminToolScroll + (scrollY < 0.0 ? 1 : -1);
            int bounded = Math.max(0, Math.min(maximum, next));
            if (bounded != adminToolScroll) { adminToolScroll = bounded; rebuildWidgets(); }
            return true;
        }
        if (page == Page.MODULE_SETTINGS && scrollY != 0.0) {
            int maximum = moduleSettingsMaximumScroll(layout());
            int next = moduleSettingsScroll + (scrollY < 0.0 ? 1 : -1);
            int bounded = Math.max(0, Math.min(maximum, next));
            if (bounded != moduleSettingsScroll) { moduleSettingsScroll = bounded; rebuildWidgets(); }
            return true;
        }
        if (page == Page.ADMIN && !useTexturedTiles(layout()) && scrollY != 0.0) {
            int maximum = adminModuleMaximumScroll(layout());
            int next = adminModuleScroll + (scrollY < 0.0 ? 1 : -1);
            int bounded = Math.max(0, Math.min(maximum, next));
            if (bounded != adminModuleScroll) { adminModuleScroll = bounded; rebuildWidgets(); }
            return true;
        }
        if (page == Page.PLAYER_INFO && playerProfileDropdownOpen && scrollY != 0.0) {
            int maximum = Math.max(0, playerProfileData.players().size() - DROPDOWN_VISIBLE_ROWS);
            int next = playerProfileDropdownScroll + (scrollY < 0.0 ? 1 : -1);
            playerProfileDropdownScroll = Math.max(0, Math.min(maximum, next));
            return true;
        }
        if (page == Page.TRANSACTIONS && transactionPlayerDropdownOpen && scrollY != 0.0) {
            int maximum = Math.max(0, transactionPlayerOptions().size() - DROPDOWN_VISIBLE_ROWS);
            int next = transactionPlayerDropdownScroll + (scrollY < 0.0 ? 1 : -1);
            transactionPlayerDropdownScroll = Math.max(0, Math.min(maximum, next));
            return true;
        }
        if (page == Page.PERMISSIONS && scrollY != 0.0
                && (permissionTargetDropdownOpen || permissionDimensionDropdownOpen || permissionRankDropdownOpen)) {
            int size = permissionTargetDropdownOpen ? permissionData.targets().size()
                    : permissionDimensionDropdownOpen ? permissionData.dimensions().size()
                    : permissionData.rankOptions().size();
            int maximum = Math.max(0, size - DROPDOWN_VISIBLE_ROWS);
            int next = permissionDropdownScroll + (scrollY < 0.0 ? 1 : -1);
            permissionDropdownScroll = Math.max(0, Math.min(maximum, next));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0
                && (handlePermissionDropdownClick(mouseX, mouseY)
                || handlePlayerProfileDropdownClick(mouseX, mouseY)
                || handleTransactionPlayerDropdownClick(mouseX, mouseY))) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        Layout l = layout();
        if (closeBounds(l).contains(mouseX, mouseY)) {
            playClick();
            onClose();
            return true;
        }
        if (snapshot.settingsAvailable() && settingsBounds(l).contains(mouseX, mouseY)) {
            playClick();
            openPage(Page.SETTINGS);
            return true;
        }
        if (snapshot.administrator() && adminBounds(l).contains(mouseX, mouseY)) {
            playClick();
            openPage(Page.ADMIN);
            return true;
        }
        if (page != Page.HOME && backBounds(l).contains(mouseX, mouseY)) {
            playClick();
            goBack();
            return true;
        }
        if (useTexturedTiles(l)) {
            for (ModuleTile tile : moduleTiles(l)) {
                if (tile.module().enabled() && tile.bounds().contains(mouseX, mouseY)) {
                    playClick();
                    openPage(tile.module().page());
                    return true;
                }
            }
        }
        return false;
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The dashboard draws its own flat dim layer. Screen#renderBackground in
        // Minecraft 1.21.1 additionally applies the vanilla world blur, and is
        // invoked again by super.render(). Suppress that blur for this screen.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Layout l = layout();
        updatePortraitRotation(l, mouseX, mouseY);
        SsuGuiScale.fullscreenDim(g, this, 0xA5000000); g.fill(l.panelX(),l.panelY(),l.panelRight(),l.panelBottom(),PANEL);
        g.renderOutline(l.panelX(),l.panelY(),l.panelWidth(),l.panelHeight(),PANEL_BORDER);
        if (l.sidebarVisible()) g.drawString(font,"Simple Server Utilities",l.panelX()+11,l.panelY()+13,ACCENT,true);
        drawPageTitle(g, l);
        drawDashboardCalendar(g, l);
        drawSidebar(g,l); drawPage(g,l,mouseX,mouseY);
        if (!notice.isBlank()) g.drawString(font,trim(notice,90),l.contentX(),l.panelBottom()-43,noticeError?ERROR:GOOD,false);
        if (loading || (page == Page.PERMISSIONS && permissionLoading)
                || (page == Page.PLAYER_INFO && playerProfileLoading)) {
            g.drawString(font,"Loading page…",l.contentRight()-85,l.panelY()+38,WARNING,false);
        }
        super.render(g,mouseX,mouseY,partialTick);
        if (skin != null && l.sidebarVisible()) {
            g.blit(PORTRAIT_FRAME, l.sidebarX() + 23, l.panelY() + 54,
                    0, 0, 54, 78, 54, 78);
        }
        drawCloseButton(g, closeBounds(l), mouseX, mouseY);
        drawUtilityButton(g, settingsBounds(l), ICON_SETTINGS, page == Page.SETTINGS,
                mouseX, mouseY, snapshot.settingsAvailable());
        drawUtilityButton(g, adminBounds(l), ICON_SHIELD,
                page == Page.ADMIN || page == Page.MODULE_SETTINGS || page == Page.ADMIN_TOOLS || page == Page.MINIGAME_ADMIN || page == Page.MINE_ADMIN || page == Page.JAIL_ADMIN
                        || page == Page.HOLOGRAMS || page == Page.PERMISSIONS || page == Page.PLAYER_INFO
                        || page == Page.ECONOMICS || page == Page.TRANSACTIONS || page == Page.AUCTION_TAX
                        || page == Page.CLAIM_TAX || page == Page.ACCOUNTS || page == Page.RENT_OPERATIONS,
                mouseX, mouseY, snapshot.administrator());
        if (page != Page.HOME) drawBackButton(g, l, mouseX, mouseY);
        if (page == Page.PERMISSIONS) {
            drawPermissionDropdowns(g, l, mouseX, mouseY);
            drawPermissionTooltip(g, l, mouseX, mouseY);
        }
        if (page == Page.PLAYER_INFO) {
            drawPlayerProfileDropdown(g, l, mouseX, mouseY);
        }
        if (page == Page.TRANSACTIONS) {
            drawTransactionPlayerDropdown(g, l, mouseX, mouseY);
        }
        if (page == Page.SETTINGS) drawSettingsTooltip(g, mouseX, mouseY);
    }

    private void drawPageTitle(GuiGraphics g, Layout l) {
        String title = page.label();
        int left = l.sidebarVisible() ? l.contentX() : l.panelX() + 12;
        int right = Math.min(closeBounds(l).x() - 8, l.contentRight());
        int available = Math.max(1, right - left);
        int titleWidth = Math.max(1, font.width(title));
        float scale = Math.min(1.35F, available / (float) titleWidth);
        int centerX = left + available / 2;
        g.pose().pushPose();
        g.pose().translate(centerX, l.panelY() + 10, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, title, -titleWidth / 2, 0, TEXT, true);
        g.pose().popPose();
    }

    private void drawDashboardCalendar(GuiGraphics g, Layout l) {
        if (minecraft == null || minecraft.level == null) return;
        String calendar = GameCalendar.fromClockTime(minecraft.level.getDayTime()).displayText();
        int left = l.sidebarVisible() ? l.contentX() : l.panelX() + 72;
        int right = Math.min(closeBounds(l).x() - 8, l.contentRight());
        int centerX = left + Math.max(1, right - left) / 2;
        g.drawCenteredString(font, calendar, centerX, l.panelY() + 29, MUTED);
    }

    private void drawSettingsTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (SettingsTooltip tooltip : settingsTooltips) {
            if (!tooltip.bounds().contains(mouseX, mouseY)) continue;
            g.renderComponentTooltip(font, tooltip.lines(), mouseX, mouseY);
            return;
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

    private void drawPermissionDropdowns(GuiGraphics g, Layout l, int mouseX, int mouseY) {
        if (!permissionModeDropdownOpen && !permissionTargetDropdownOpen
                && !permissionDimensionDropdownOpen && !permissionRankDropdownOpen) return;
        if (permissionModeDropdownOpen) {
            drawDropdown(g, permissionModeBounds(l), List.of("Players", "Ranks", "Claim roles"), mouseX, mouseY);
        } else if (permissionTargetDropdownOpen) {
            List<SsuPermissionEditorDataPayload.TargetEntry> targets = visiblePermissionTargets();
            List<String> labels = targets.stream().map(target -> target.label()
                    + (target.summary().isBlank() ? "" : " — " + target.summary())).toList();
            drawDropdown(g, permissionTargetBounds(l), labels, mouseX, mouseY);
        } else if (permissionDimensionDropdownOpen) {
            List<String> labels = visiblePermissionDimensions().stream().map(entry -> entry.label()
                    + (entry.summary().isBlank() ? "" : " — " + entry.summary())).toList();
            drawDropdown(g, permissionDimensionBounds(l), labels, mouseX, mouseY);
        } else if (permissionRankDropdownOpen) {
            drawDropdown(g, permissionRankBounds(l), visiblePermissionRanks(), mouseX, mouseY);
        }
    }

    private void drawPlayerProfileDropdown(GuiGraphics g, Layout l, int mouseX, int mouseY) {
        if (!playerProfileDropdownOpen) return;
        Rect anchor = playerProfileTargetBounds(l);
        List<SsuPlayerProfileDataPayload.PlayerEntry> options = visiblePlayerProfileTargets();
        Rect list = dropdownListBounds(anchor, Math.max(1, options.size()));
        g.fill(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFC151C24);
        g.renderOutline(list.x(), list.y(), list.width(), list.height(), ACCENT);
        if (options.isEmpty()) {
            g.drawString(font, "No players match this search", list.x() + 5, list.y() + 6, MUTED, false);
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
            g.drawString(font, trim(label, Math.max(10, (list.width() - 10) / 6)),
                    list.x() + 5, y + 6, option.online() ? GOOD : TEXT, false);
        }
        int fullSize = playerProfileData.players().size();
        if (fullSize > DROPDOWN_VISIBLE_ROWS) {
            g.drawString(font, (playerProfileDropdownScroll + 1) + "–"
                            + Math.min(fullSize, playerProfileDropdownScroll + options.size()) + " / " + fullSize,
                    list.x() + list.width() - 62, list.y() + list.height() - 12, MUTED, false);
        }
    }

    private void drawTransactionPlayerDropdown(GuiGraphics g, Layout l, int mouseX, int mouseY) {
        if (!transactionPlayerDropdownOpen) return;
        Rect anchor = transactionPlayerBounds(l);
        List<SsuMenuPageDataPayload.AccountEntry> options = visibleTransactionPlayers();
        Rect list = dropdownListBounds(anchor, Math.max(1, options.size()));
        g.fill(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFC151C24);
        g.renderOutline(list.x(), list.y(), list.width(), list.height(), ACCENT);
        for (int i = 0; i < options.size(); i++) {
            var option = options.get(i);
            int y = list.y() + i * 20;
            if (mouseX >= list.x() && mouseX < list.x() + list.width()
                    && mouseY >= y && mouseY < y + 20) {
                g.fill(list.x() + 1, y + 1, list.x() + list.width() - 1, y + 20, 0xD03A4D5C);
            }
            String label = option.id().isBlank() ? "All players"
                    : option.name() + " — " + option.formattedBalance();
            g.drawString(font, trim(label, Math.max(10, (list.width() - 10) / 6)),
                    list.x() + 5, y + 6, option.id().isBlank() ? ACCENT : TEXT, false);
        }
        int fullSize = transactionPlayerOptions().size();
        if (fullSize > DROPDOWN_VISIBLE_ROWS) {
            g.drawString(font, (transactionPlayerDropdownScroll + 1) + "–"
                            + Math.min(fullSize, transactionPlayerDropdownScroll + options.size()) + " / " + fullSize,
                    list.x() + list.width() - 62, list.y() + list.height() - 12, MUTED, false);
        }
    }

    private void drawDropdown(GuiGraphics g, Rect anchor, List<String> options, int mouseX, int mouseY) {
        int rows = Math.max(1, options.size());
        Rect list = dropdownListBounds(anchor, rows);
        g.fill(list.x(), list.y(), list.x() + list.width(), list.y() + list.height(), 0xFC151C24);
        g.renderOutline(list.x(), list.y(), list.width(), list.height(), ACCENT);
        if (options.isEmpty()) {
            g.drawString(font, "No matches", list.x() + 5, list.y() + 6, MUTED, false);
            return;
        }
        for (int i = 0; i < options.size(); i++) {
            int y = list.y() + i * 20;
            if (mouseX >= list.x() && mouseX < list.x() + list.width()
                    && mouseY >= y && mouseY < y + 20) {
                g.fill(list.x() + 1, y + 1, list.x() + list.width() - 1, y + 20, 0xD03A4D5C);
            }
            g.drawString(font, trim(options.get(i), Math.max(10, (list.width() - 10) / 6)),
                    list.x() + 5, y + 6, TEXT, false);
        }
        int fullSize = permissionTargetDropdownOpen ? permissionData.targets().size()
                : permissionDimensionDropdownOpen ? permissionData.dimensions().size()
                : permissionRankDropdownOpen ? permissionData.rankOptions().size() : options.size();
        if (fullSize > DROPDOWN_VISIBLE_ROWS) {
            g.drawString(font, (permissionDropdownScroll + 1) + "–"
                            + Math.min(fullSize, permissionDropdownScroll + options.size()) + " / " + fullSize,
                    list.x() + list.width() - 58, list.y() + list.height() - 12, MUTED, false);
        }
    }

    private void drawPermissionTooltip(GuiGraphics g, Layout l, int mouseX, int mouseY) {
        if (permissionModeDropdownOpen || permissionTargetDropdownOpen
                || permissionDimensionDropdownOpen || permissionRankDropdownOpen) return;
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
            g.renderComponentTooltip(font, List.of(
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

    private List<SsuPermissionEditorDataPayload.TargetEntry> visiblePermissionDimensions() {
        return slice(permissionData.dimensions(), permissionDropdownScroll, DROPDOWN_VISIBLE_ROWS);
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

    private void drawCloseButton(GuiGraphics g, Rect bounds, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        g.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                hovered ? 0xD25B2D35 : CARD);
        g.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), hovered ? 0xFFFF7A86 : PANEL_BORDER);
        String cross = "×";
        g.drawString(font, cross, bounds.x() + (bounds.width() - font.width(cross)) / 2,
                bounds.y() + 9, hovered ? 0xFFFFFFFF : TEXT, true);
    }

    private void drawUtilityButton(GuiGraphics g, Rect bounds, ResourceLocation icon, boolean selected,
                                   int mouseX, int mouseY, boolean visible) {
        if (!visible) return;
        boolean hovered = bounds.contains(mouseX, mouseY);
        g.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                selected ? 0xD03A4D5C : hovered ? 0xD22B3946 : CARD);
        g.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), hovered || selected ? ACCENT : PANEL_BORDER);
        int iconWidth = 16;
        int iconHeight = icon.equals(ICON_SHIELD) ? 19 : 16;
        g.blit(icon,
                bounds.x() + (bounds.width() - iconWidth) / 2,
                bounds.y() + (bounds.height() - iconHeight) / 2,
                0, 0, iconWidth, iconHeight, iconWidth, iconHeight);
    }

    private void drawBackButton(GuiGraphics g, Layout l, int mouseX, int mouseY) {
        Rect bounds = backBounds(l);
        boolean hovered = bounds.contains(mouseX, mouseY);
        g.blit(hovered ? BUTTON_BACK_HOVER_TEXTURE : BUTTON_BACK_TEXTURE,
                bounds.x(), bounds.y(), 0, 0, 54, 20, 54, 20);
    }

    private Rect closeBounds(Layout l) { return new Rect(l.panelRight() - 40, l.panelY() + 7, 28, 28); }
    private Rect settingsBounds(Layout l) { return new Rect(l.panelRight() - 74, l.panelY() + 7, 28, 28); }
    private Rect adminBounds(Layout l) {
        return new Rect(l.panelRight() - (snapshot.settingsAvailable() ? 108 : 74), l.panelY() + 7, 28, 28);
    }
    private Rect backBounds(Layout l) {
        return l.sidebarVisible()
                ? new Rect(l.sidebarX() + (100 - 54) / 2, l.panelBottom() - 29, 54, 20)
                : new Rect(l.panelX() + 10, l.panelY() + 7, 54, 20);
    }

    private Rect profileBounds(Layout l) {
        int y = l.panelY() + (snapshot.economy().enabled() ? 187 : 170);
        return new Rect(l.sidebarX() + 8, y, 84, 20);
    }

    private void drawSidebar(GuiGraphics g, Layout l) {
        if (!l.sidebarVisible()) return;
        g.fill(l.sidebarX(),l.panelY()+42,l.sidebarX()+100,l.panelBottom()-36,CARD);
        g.renderOutline(l.sidebarX(),l.panelY()+42,100,l.panelHeight()-78,PANEL_BORDER);
        int y=l.panelY()+140;
        center(g,snapshot.playerName(),l.sidebarX()+50,y,TEXT);
        center(g,"Rank: "+(snapshot.primaryRank().isBlank()?"default":snapshot.primaryRank()),l.sidebarX()+50,y+15,MUTED);
        if (snapshot.economy().enabled()) {
            center(g,snapshot.economy().formattedBalance(),l.sidebarX()+50,y+32,GOOD);
        }
    }

    private void drawPage(GuiGraphics g, Layout l, int mouseX, int mouseY) {
        switch(page) {
            case HOME -> drawModuleTiles(g, l, mouseX, mouseY);
            case ADMIN -> drawModuleTiles(g, l, mouseX, mouseY);
            case MODULE_SETTINGS -> drawModuleSettings(g, l);
            case ADMIN_TOOLS -> drawAdminTools(g, l, mouseX, mouseY);
            case HOLOGRAMS -> drawHolograms(g, l);
            case STATISTICS -> drawStatistics(g, l);
            case CLAIMS -> drawClaims(g,l); case HOMES -> drawHomes(g,l); case TRAVEL -> drawTravel(g,l,false);
            case MY_WARPS -> drawPlayerWarps(g,l);
            case TRAVEL_ADMIN -> drawTravel(g,l,true); case ADMIN_CLAIMS -> drawAdminClaims(g,l); case RANKS -> drawRanks(g,l);
            case WALLET -> drawWallet(g,l); case ECONOMICS -> drawEconomics(g,l); case TRANSACTIONS -> drawTransactions(g,l);
            case AUCTION_TAX -> drawAuctionTax(g,l); case CLAIM_TAX -> drawClaimTax(g,l); case WARP_RENTAL -> drawWarpRental(g,l);
            case REGIONS -> drawRegions(g,l); case REGION_ADMIN -> drawRegionAdmin(g,l); case UTILITY_MINING_ADMIN -> drawUtilityMiningAdmin(g,l); case MAINTENANCE -> drawMaintenance(g,l); case SETTINGS -> drawSettingsIntro(g, l);
            case PERMISSIONS -> drawPermissions(g,l,mouseX,mouseY); case PLAYER_INFO -> drawPlayerInfo(g,l);
            case ACCOUNTS -> drawAccounts(g,l); case JOBS -> drawJobs(g,l);
            case RENT_OPERATIONS -> drawRentOps(g,l); case CORE -> drawCore(g,l); case PROFILE -> drawProfile(g,l);
            case MAIL -> g.drawString(font,"Opening mailbox…",l.contentX(),l.contentTop(),MUTED,false);
            case AUCTION_HOUSE -> g.drawString(font,"Opening Auction House…",l.contentX(),l.contentTop(),MUTED,false);
            case QUESTS -> g.drawString(font,"Opening Questbook…",l.contentX(),l.contentTop(),MUTED,false);
            case ACHIEVEMENTS, ACHIEVEMENTS_ADMIN -> g.drawString(font,"Opening Achievements…",l.contentX(),l.contentTop(),MUTED,false);
            case COSMETICS -> {
                g.drawString(font, "Cosmetics", l.contentX(), l.contentTop(), TEXT, true);
                g.drawString(font, "Cosmetic customization is coming in a future SSU build.", l.contentX(), l.contentTop() + 20, MUTED, false);
            }
            case MINIGAMES -> g.drawString(font,"Opening Minigame Lobby…",l.contentX(),l.contentTop(),MUTED,false);
            case MINIGAME_ADMIN -> g.drawString(font,"Opening Minigame Administration…",l.contentX(),l.contentTop(),MUTED,false);
            case DUNGEONS -> g.drawString(font,"Opening Dungeon Lobby…",l.contentX(),l.contentTop(),MUTED,false);
            case KITS -> g.drawString(font,"Opening Kits…",l.contentX(),l.contentTop(),MUTED,false);
            case KIT_ADMIN -> g.drawString(font,"Opening Kit Administration…",l.contentX(),l.contentTop(),MUTED,false);
            case MINES -> g.drawString(font,"Opening Mines…",l.contentX(),l.contentTop(),MUTED,false);
            case MINE_ADMIN -> g.drawString(font,"Opening Mine Administration…",l.contentX(),l.contentTop(),MUTED,false);
            case JAIL_ADMIN -> g.drawString(font,"Opening Jail Administration…",l.contentX(),l.contentTop(),MUTED,false);
            case ONBOARDING_ADMIN -> g.drawString(font,"Opening Onboarding & Spawns…",l.contentX(),l.contentTop(),MUTED,false);
        }
    }

    private void drawModuleTiles(GuiGraphics g, Layout l, int mouseX, int mouseY) {
        if (!useTexturedTiles(l)) return;
        for (ModuleTile tile : moduleTiles(l)) {
            Rect b = tile.bounds();
            boolean hovered = tile.module().enabled() && b.contains(mouseX, mouseY);
            // 1.21.1's legacy GUI blit path does not reliably alpha-blend a
            // second translucent texture over an already drawn GUI texture. Use a
            // precomposited hover texture so the original button remains visible
            // underneath the intended glow.
            g.blit(hovered ? BUTTON_HOVER_TEXTURE : BUTTON_TEXTURE, b.x(), b.y(), 0, 0,
                    TILE_SIZE, TILE_SIZE, TILE_SIZE, TILE_SIZE);
            if (!tile.module().enabled()) g.fill(b.x(), b.y(), b.x() + TILE_SIZE, b.y() + TILE_SIZE, 0x85000000);
            drawModuleTileIcon(g, tile.module().icon(), b);
            center(g, tile.module().label(), b.x() + TILE_SIZE / 2, b.y() + TILE_SIZE + 4,
                    tile.module().enabled() ? TEXT : MUTED);
            if (hovered && snapshot.uiSettings().dashboardHints()) {
                g.renderComponentTooltip(font,
                        List.of(Component.literal(tile.module().hint())), mouseX, mouseY);
            }
        }
    }

    private void drawModuleTileIcon(GuiGraphics g, ResourceLocation icon, Rect bounds) {
        int sourceWidth = icon.equals(ICON_PLAYERS) ? 32 : 16;
        int sourceHeight = icon.equals(ICON_SHIELD) ? 19 : sourceWidth;
        float scale = sourceWidth >= 32 ? 1.0F : 2.0F;
        int renderedWidth = Math.round(sourceWidth * scale);
        int renderedHeight = Math.round(sourceHeight * scale);
        int x = bounds.x() + (TILE_SIZE - renderedWidth) / 2;
        int y = bounds.y() + (TILE_SIZE - renderedHeight) / 2;
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.blit(icon, 0, 0, 0, 0,
                sourceWidth, sourceHeight, sourceWidth, sourceHeight);
        g.pose().popPose();
    }

    private boolean useTexturedTiles(Layout l) {
        if ((page != Page.HOME && page != Page.ADMIN) || l.contentWidth() < 330 || l.panelHeight() < 320) return false;
        if (page == Page.ADMIN && adminModules().size() > 16) return false;
        return true;
    }

    private List<ModuleTile> moduleTiles(Layout l) {
        if (page != Page.HOME && page != Page.ADMIN) return List.of();
        List<Module> modules = page == Page.HOME ? homeModules(l) : adminModules();
        // HOME/ADMIN tile pages deliberately keep the canonical four-column
        // layout at every logical screen width. Smaller physical displays are
        // handled by SsuGuiScale, not by reflowing the Dashboard into 3 columns.
        int columns = 4;
        int gapX = Math.max(10, (l.contentWidth() - columns * TILE_SIZE) / Math.max(1, columns - 1));
        int startX = l.contentX() + Math.max(0, (l.contentWidth() - (columns * TILE_SIZE + gapX * (columns - 1))) / 2);
        int startY = l.contentTop() + 10;
        int rowStep = TILE_SIZE + TILE_LABEL_HEIGHT + 10;
        java.util.ArrayList<ModuleTile> result = new java.util.ArrayList<>(modules.size());
        for (int i = 0; i < modules.size(); i++) {
            int col = i % columns; int row = i / columns;
            result.add(new ModuleTile(new Rect(startX + col * (TILE_SIZE + gapX), startY + row * rowStep,
                    TILE_SIZE, TILE_SIZE + TILE_LABEL_HEIGHT), modules.get(i)));
        }
        return List.copyOf(result);
    }

    private void drawSettingsIntro(GuiGraphics g, Layout l) {
        if (settingsCategory == SettingsCategory.INTERFACE) {
            g.drawString(font, "SSU scale changes only SSU screens; Minecraft GUI Scale stays untouched.",
                    l.contentX(), l.contentTop() + 4, TEXT, false);
            return;
        }
        if (settingsCategory == SettingsCategory.BORDERS) {
            g.drawString(font, "Enable claim borders is the master permission for in-world claim outlines.",
                    l.contentX(), l.contentTop() + 2, TEXT, false);
            g.drawString(font, "Use Show/Hide on your claims; Show other claims controls land owned by others.",
                    l.contentX(), l.contentTop() + 16, MUTED, false);
            return;
        }
        g.drawString(font, "Choose a category, then click a setting to change it.",
                l.contentX(), l.contentTop() + 4, TEXT, false);
    }

    private void drawModuleSettings(GuiGraphics g, Layout l) {
        var settings = snapshot.moduleSettings();
        int blocked = (int) settings.moduleStates().stream().filter(v -> v.configuredEnabled() && !v.active()).count();
        int maximum = moduleSettingsMaximumScroll(l);
        String introPrimary = blocked == 0
                ? "Modules run independently; required dependencies suspend dependents automatically."
                : blocked + " configured module(s) are BLOCKED by required dependencies.";
        String introSecondary = blocked == 0
                ? "Core services stay active; disabled feature data is preserved."
                : "Configured choices are preserved and resume automatically when requirements return.";
        int firstLineWidth = Math.max(80, l.contentWidth() - (maximum > 0 ? 142 : 0));
        g.drawString(font, fitToWidth(introPrimary, firstLineWidth), l.contentX(), l.contentTop(), MUTED, false);
        g.drawString(font, fitToWidth(introSecondary, l.contentWidth()), l.contentX(), l.contentTop() + 11, MUTED, false);
        if (maximum > 0) {
            int columns = moduleSettingsColumns(l);
            int first = moduleSettingsScroll * columns + 1;
            int last = Math.min(moduleSwitches(settings).size(), (moduleSettingsScroll + moduleSettingsVisibleRows(l)) * columns);
            String range = first + "–" + last + " / " + moduleSwitches(settings).size();
            g.drawString(font, range, l.contentRight() - font.width(range) - 50, l.contentTop() + 5, MUTED, false);
        }
        int questModeY = moduleSettingsOptionsTop(l);
        g.drawString(font, "Quest entry can use the SSU menu, NPC integration, or both when those modules are active.",
                l.contentX(), questModeY + 20, MUTED, false);
        int distanceTop = questModeY + 24;
        g.drawString(font, "Hologram render/load distance: " + settings.hologramRenderDistance() + " blocks",
                l.contentX(), distanceTop + 5, TEXT, false);
        g.drawString(font, "Claim border render distance: " + settings.claimBorderRenderDistance() + " blocks",
                l.contentX(), distanceTop + 29, TEXT, false);
        g.drawString(font, "Region border render distance: " + settings.regionBorderRenderDistance() + " blocks",
                l.contentX(), distanceTop + 53, TEXT, false);
    }

    private void drawAdminTools(GuiGraphics g, Layout l, int mouseX, int mouseY) {
        g.drawString(font, "Scroll to browse all specialised admin tools.", l.contentX(), l.contentTop(), MUTED, false);
        List<AdminTool> tools = adminTools();
        int visible = adminToolVisibleRows(l);
        int maximum = Math.max(0, tools.size() - visible);
        adminToolScroll = Math.max(0, Math.min(maximum, adminToolScroll));
        int rowStart = l.contentTop() + 34;
        int rowStep = 46;
        int rowHeight = 40;
        for (int local = 0; local < visible; local++) {
            int index = adminToolScroll + local;
            if (index >= tools.size()) break;
            AdminTool tool = tools.get(index);
            int y = rowStart + local * rowStep;
            Rect row = new Rect(l.contentX(), y, l.contentWidth(), rowHeight);
            boolean hovered = row.contains(mouseX, mouseY);
            g.fill(row.x(), row.y(), row.x() + row.width(), row.y() + row.height(), hovered ? 0xD02A3743 : CARD);
            g.renderOutline(row.x(), row.y(), row.width(), row.height(), hovered ? ACCENT : PANEL_BORDER);
            g.drawString(font, tool.label(), row.x() + 8, row.y() + 6, TEXT, true);
            g.drawString(font, trim(tool.hint(), 62), row.x() + 8, row.y() + 22, MUTED, false);
            if (hovered) g.renderComponentTooltip(font, List.of(Component.literal(tool.hint())), mouseX, mouseY);
        }
        if (maximum > 0) g.drawString(font, (adminToolScroll + 1) + "–" + Math.min(tools.size(), adminToolScroll + visible)
                + " / " + tools.size(), l.contentRight() - 118, l.contentTop() + 6, MUTED, false);
    }

    private void drawHolograms(GuiGraphics g, Layout l) {
        if (pageData.locations().isEmpty()) empty(g, l, "No holograms on this page.");
        int availableTextWidth = Math.max(42, l.contentWidth() - 198);
        int labelCharacters = Math.max(6, availableTextWidth / 6);
        for (int i = 0; i < pageData.locations().size(); i++) {
            var entry = pageData.locations().get(i);
            int y = rowTextY(l, i);
            String label = entry.name() + " [" + entry.kind() + "]";
            if (availableTextWidth >= 230) {
                label += " | " + shortDim(entry.dimension()) + " @ " + pos(entry.x(), entry.y(), entry.z());
            }
            g.drawString(font, trim(label, labelCharacters), l.contentX(), y, TEXT, false);
        }
    }

    private void drawStatistics(GuiGraphics g, Layout l) {
        if (pageData.statistics().isEmpty()) empty(g, l, "No custom statistics on this page.");
        int textWidth = Math.max(100, l.contentWidth() - 272);
        int chars = Math.max(12, textWidth / 6);
        for (int i = 0; i < pageData.statistics().size(); i++) {
            var entry = pageData.statistics().get(i);
            int y = rowTextY(l, i);
            String primary = entry.displayName() + " [" + entry.id() + "]";
            g.drawString(font, trim(primary, chars), l.contentX(), y, entry.enabled() ? TEXT : MUTED, false);
            String target = "*".equals(entry.target()) ? "all" : entry.target();
            g.drawString(font, trim(entry.eventType().toLowerCase(Locale.ROOT).replace('_', ' ') + " • " + target
                    + " • " + entry.playerCount() + " players • total " + entry.formattedTotal(), chars + 14),
                    l.contentX(), y + 11, MUTED, false);
        }
    }

    private void drawClaims(GuiGraphics g,Layout l){if(pageData.claims().isEmpty())empty(g,l,"No claims on this page.");
        for(int i=0;i<pageData.claims().size();i++){var e=pageData.claims().get(i);int y=rowTextY(l,i);
            g.drawString(font,e.name(),l.contentX(),y,TEXT,false);g.drawString(font,e.chunkCount()+" chunks | "+shortDim(e.dimension()),l.contentX()+100,y,MUTED,false);}
        if(selectedRow>=0&&selectedRow<pageData.claims().size()){var e=pageData.claims().get(selectedRow);
            detail(g,l,"Claim details",List.of("ID: "+e.id(),"Trusted: "+(e.trustedPlayers().isBlank()?e.trustedCount():e.trustedPlayers()),
                    "Flags: "+e.flags()));}}
    private void drawHomes(GuiGraphics g,Layout l){
        boolean canSetHere=homeCapability("set_here");
        g.drawString(font,"Claim: "+blank(homesClaimName)+" | Total homes: "+snapshot.core().homeCount()+" / "+snapshot.core().maxHomes(),
                l.contentX(),l.contentTop()+2,MUTED,false);
        if(!canSetHere)g.drawString(font,"Stand inside this claim and ensure your home-set permission is allowed to save a home.",
                l.contentX(),l.contentTop()+14,WARNING,false);
        if(pageData.locations().isEmpty())empty(g,l,"No homes are linked to this claim. Stand inside it, enter a name and choose Save here.");
        for(int i=0;i<pageData.locations().size();i++){var e=pageData.locations().get(i);int y=rowTextY(l,i);
            g.drawString(font,e.name(),l.contentX(),y,TEXT,false);g.drawString(font,shortDim(e.dimension())+" | "+pos(e.x(),e.y(),e.z()),l.contentX()+100,y,MUTED,false);}}
    private void drawAdminClaims(GuiGraphics g,Layout l){if(pageData.claims().isEmpty())empty(g,l,"No player claims on this page.");
        for(int i=0;i<pageData.claims().size();i++){var e=pageData.claims().get(i);int y=rowTextY(l,i);
            g.drawString(font,trim(e.name(),34),l.contentX(),y,TEXT,false);g.drawString(font,e.chunkCount()+" chunks | "+shortDim(e.dimension()),l.contentX()+170,y,MUTED,false);}
        if(selectedRow>=0&&selectedRow<pageData.claims().size()){var e=pageData.claims().get(selectedRow);
            detail(g,l,"Administrative claim details",List.of("Claim ID: "+e.id(),"Owner / claim: "+e.name(),"Trusted: "+(e.trustedPlayers().isBlank()?e.trustedCount():e.trustedPlayers()),"Flags: "+e.flags()));}}
    private void drawPlayerWarps(GuiGraphics g, Layout l) {
        boolean canRentPermission = Boolean.parseBoolean(pageValue("player_warps", "can_rent", "false"));
        boolean economyEnabled = Boolean.parseBoolean(pageValue("player_warps", "economy_enabled", "false"));
        boolean canRent = canRentPermission && economyEnabled;
        String price = pageValue("player_warps", "price", "-");
        String period = pageValue("player_warps", "period", "-");
        String count = pageValue("player_warps", "count", "0");
        String maximum = pageValue("player_warps", "maximum", "0");
        int currentCount = parseInt(count, 0);
        int maximumCount = parseInt(maximum, 0);
        boolean canCreate = canRent && maximumCount > 0 && currentCount < maximumCount;
        g.drawString(font, "Rented warps: " + count + " / " + maximum + " | " + price + " every " + period,
                l.contentX(), l.contentTop() + 2, canCreate ? GOOD : WARNING, false);
        String availability = canCreate
                ? "New rentals are prepaid. Use Move here on an existing warp without changing its paid period."
                : !canRentPermission ? "Your rank does not currently allow new player-warp rentals. Existing rentals remain manageable."
                : !economyEnabled ? "Economy is disabled, so new rentals are unavailable. Existing rentals remain manageable."
                : "You reached your rented-warp limit. Existing rentals can still be moved, hidden or deleted.";
        g.drawString(font, availability, l.contentX(), l.contentTop() + 15, canCreate ? MUTED : WARNING, false);
        if (pageData.locations().isEmpty()) {
            g.drawString(font, canRent ? "You do not rent any warps yet." : "No rented warps are available.",
                    l.contentX(), l.contentTop() + 92, MUTED, false);
        }
        for (int i = 0; i < pageData.locations().size(); i++) {
            var entry = pageData.locations().get(i);
            int y = rowTextY(l, i, 86);
            String paidUntil = pageData.permissions().stream()
                    .filter(meta -> "warp".equals(meta.kind()) && meta.owner().equalsIgnoreCase(entry.name()) && "paid_until".equals(meta.key()))
                    .map(SsuMenuPageDataPayload.PermissionEntry::value).findFirst().orElse("0");
            long timestamp; try { timestamp = Long.parseLong(paidUntil); } catch (Exception ignored) { timestamp = 0L; }
            g.drawString(font, trim(entry.name(), 20), l.contentX(), y, TEXT, false);
            if (l.contentWidth() >= 500) {
                g.drawString(font, cap(entry.kind()) + " | paid until " + time(timestamp), l.contentX() + 104, y, MUTED, false);
            }
        }
    }

    private void drawTravel(GuiGraphics g,Layout l,boolean admin){
        if(pageData.locations().isEmpty())empty(g,l,admin?"No server warps or spawn match this filter.":"No available travel destinations match this filter.");
        int offset=admin?(l.contentWidth()<500?130:106):82;
        for(int i=0;i<pageData.locations().size();i++){var e=pageData.locations().get(i);int y=rowTextY(l,i,offset);
            String shared = "home".equals(e.kind()) && !e.ownerId().isBlank() ? " [shared]" : "";
            g.drawString(font,cap(e.kind())+": "+e.name()+shared,l.contentX(),y,TEXT,false);
            if(admin)g.drawString(font,shortDim(e.dimension())+" | "+pos(e.x(),e.y(),e.z()),l.contentX()+130,y,MUTED,false);}}
    private void drawRanks(GuiGraphics g, Layout l) {
        if (pageData.permissions().isEmpty()) empty(g, l, "No ranks on this page.");
        String help = selectedRow >= 0 ? "Advanced: set priority or add/remove one inherited parent rank."
                : "Use Manage to edit priority and inheritance; Rename uses the field above.";
        g.drawString(font, help, l.contentX(), l.contentTop() + 98, MUTED, false);
        for (int i = 0; i < pageData.permissions().size(); i++) {
            var e = pageData.permissions().get(i); int y = rowTextY(l, i, 110);
            g.drawString(font, e.owner(), l.contentX(), y, selectedRow == i ? ACCENT : "default".equals(e.key()) ? GOOD : TEXT, false);
            g.drawString(font, ("default".equals(e.key()) ? "server default • " : "") + "priority " + e.value() + " • " + e.source(),
                    l.contentX() + 100, y, MUTED, false);
        }
    }
    private void drawWallet(GuiGraphics g, Layout l) {
        g.drawString(font, "Balance: " + snapshot.economy().formattedBalance(), l.contentX(), l.contentTop(), GOOD, false);
        if (snapshot.economy().canPay()) {
            int playerW = Math.max(104, Math.min(138, l.contentWidth() / 3));
            int pickerW = 28, gap = 5;
            g.drawString(font, "Player", l.contentX(), l.contentTop() + 16, MUTED, false);
            g.drawString(font, "Amount", l.contentX() + playerW + pickerW + gap * 2, l.contentTop() + 16, MUTED, false);
        }
        drawTransactionRows(g, l, 88);
    }

    private void drawEconomics(GuiGraphics g, Layout l) {
        g.drawString(font, "Choose an economy section. These pages are only available to authorized administrators.",
                l.contentX(), l.contentTop() + 4, MUTED, false);
    }

    private void drawTransactions(GuiGraphics g, Layout l) {
        String filter = !draftTransactionPlayer.isBlank() ? "Exact player: " + draftTransactionPlayer
                : selectedTransactionPlayerId.isBlank() ? "All players"
                : "Selected player: " + blank(selectedTransactionPlayerLabel);
        int filterCharacters = Math.max(12, (l.contentWidth() - 132) / 6);
        g.drawString(font, trim(filter, filterCharacters), l.contentX(), l.contentTop() + 80, MUTED, false);
        drawTransactionRows(g, l, 108);
    }

    private void drawAuctionTax(GuiGraphics g, Layout l) {
        g.drawString(font, "Auction House sale tax", l.contentX(), l.contentTop() + 4, ACCENT, true);
        g.drawString(font, "This percentage is withheld from the seller when a purchase is completed.",
                l.contentX(), l.contentTop() + 19, MUTED, false);
        g.drawString(font, "Players can see the active tax while selling, but only economy administrators can change it here.",
                l.contentX(), l.contentTop() + 31, MUTED, false);
    }

    private void drawClaimTax(GuiGraphics g, Layout l) {
        boolean enabled = Boolean.parseBoolean(pageValue("claim_tax", "enabled", "false"));
        boolean safetyHalt = Boolean.parseBoolean(pageValue("claim_tax", "safety_halt", "false"));
        long next; try { next = Long.parseLong(pageValue("claim_tax", "next_charge", "0")); } catch (Exception ignored) { next = 0L; }
        g.drawString(font, "Recurring Player Claim tax: " + (enabled ? "ENABLED" : "DISABLED")
                        + " | earliest due " + time(next),
                l.contentX(), l.contentTop() + 2, enabled ? WARNING : MUTED, true);
        g.drawString(font, "Each claim has its own cycle. Money uses its recorded peak and dimension multiplier.",
                l.contentX(), l.contentTop() + 14, MUTED, false);
        if (safetyHalt) {
            g.drawString(font, "SAFETY HALT: tax enforcement and claim mutations are fail-closed; inspect the server log.",
                    l.contentX(), l.contentTop() + 26, ERROR, true);
        }
        List<SsuMenuPageDataPayload.PermissionEntry> dimensions = pageData.permissions().stream()
                .filter(entry -> "dimension".equals(entry.kind())).toList();
        for (int i = 0; i < dimensions.size() && i < 7; i++) {
            var entry = dimensions.get(i);
            int y = l.contentTop() + 92 + i * 25;
            g.drawString(font, trim(entry.key(), 31), l.contentX(), y, TEXT, false);
            g.drawString(font, "x" + entry.value(), l.contentX() + 190, y, ACCENT, false);
        }
        g.drawString(font, trim("WARNING: failed payment removes all claims/homes and permanently confiscates the exact taxed peak chunks.",
                        Math.max(28, l.contentWidth() / 6)),
                l.contentX(), l.footerY() - 13, ERROR, false);
    }

    private void drawWarpRental(GuiGraphics g, Layout l) {
        String active = pageValue("warp_rental", "active", "0");
        g.drawString(font, "Player Warp rentals", l.contentX(), l.contentTop() + 5, ACCENT, true);
        g.drawString(font, "Players with ssu.warps.rent prepay this amount. Renewal is charged automatically at expiry.",
                l.contentX(), l.contentTop() + 20, MUTED, false);
        g.drawString(font, "If renewal cannot be paid, the warp is deleted and its name becomes available again.",
                l.contentX(), l.contentTop() + 33, WARNING, false);
        g.drawString(font, "Active player rentals: " + active, l.contentX(), l.contentTop() + 82, GOOD, false);
    }

    private void drawTransactionRows(GuiGraphics g, Layout l, int offset) {
        if (pageData.transactions().isEmpty()) {
            g.drawString(font, page == Page.TRANSACTIONS ? "No transactions match the selected player and search."
                    : "No transactions match this search.", l.contentX(), l.contentTop() + offset + 6, MUTED, false);
        }
        for (int i = 0; i < pageData.transactions().size(); i++) {
            var entry = pageData.transactions().get(i);
            int y = rowTextY(l, i, offset);
            g.drawString(font, trim(entry.type() + "  " + entry.formattedAmount(), 28), l.contentX(), y, TEXT, false);
            g.drawString(font, entry.status(), l.contentX() + 170, y, MUTED, false);
        }
        if (selectedRow >= 0 && selectedRow < pageData.transactions().size()) {
            var entry = pageData.transactions().get(selectedRow);
            detail(g, l, "Transaction", List.of(
                    "ID: " + entry.id(),
                    "From: " + blank(entry.source()),
                    "To: " + blank(entry.destination()),
                    "Actor: " + blank(entry.actor()),
                    "Module: " + blank(entry.module()),
                    "Reason: " + blank(entry.reason()),
                    "Failure: " + blank(entry.failure())
            ));
        }
    }
    private void drawRegions(GuiGraphics g,Layout l){if(pageData.regions().isEmpty())empty(g,l,"No regions on this page.");
        for(int i=0;i<pageData.regions().size();i++){var e=pageData.regions().get(i);int y=rowTextY(l,i);g.drawString(font,e.name(),l.contentX(),y,e.rentedByPlayer()?GOOD:TEXT,false);
            String state=e.rentedByPlayer()?"yours | "+e.remainingText():e.rented()?"rented":e.rentable()?e.formattedPrice()+" / "+e.periodText():"not rentable";
            g.drawString(font,state,l.contentX()+90,y,MUTED,false);}
        if(selectedRow>=0&&selectedRow<pageData.regions().size()){var e=pageData.regions().get(selectedRow);detail(g,l,"Region details",
                List.of(e.bounds(),"Managers: "+(e.managers().isBlank()?e.managerCount():e.managers()),
                        "Members: "+(e.members().isBlank()?e.memberCount():e.members()),"Flags: "+e.flags(),
                        "Rent: "+e.rentPolicy(),"Priority: "+e.priority()+" | Volume: "+e.volume(),
                        "Spawn: "+(e.hasSpawn()?e.spawn():"none")+" | Snapshot: "+yesNo(e.snapshotAvailable()),
                        "Active job lock: "+yesNo(e.jobLocked())));}}
    private void drawUtilityMiningAdmin(GuiGraphics g,Layout l){
        List<SsuMenuPageDataPayload.PermissionEntry> lists=pageData.permissions().stream().filter(e->"list".equals(e.kind())).toList();
        for(int i=0;i<lists.size();i++){var e=lists.get(i);int y=rowTextY(l,i,l.contentWidth()<430?106:92);
            g.drawString(font,cap(e.key().replace('_',' ')),l.contentX(),y,TEXT,false);
            g.drawString(font,trim(e.value(),48),l.contentX()+120,y,MUTED,false);}
        g.drawString(font,"Add and Remove use the block id field above. Clear empties the selected list immediately.",l.contentX(),l.footerY()-18,MUTED,false);
    }

    private void drawMaintenance(GuiGraphics g, Layout l) {
        int jobs = pageData.permissions().stream().filter(v -> "jobs".equals(v.key()))
                .map(v -> parseDisplayInt(v.value())).findFirst().orElse(snapshot.activeJobs());
        long pending = pageData.permissions().stream().filter(v -> "storage_pending".equals(v.key()))
                .map(v -> parseDisplayLong(v.value())).findFirst().orElse((long) snapshot.pendingStorageWrites());
        g.drawString(font, "Runtime: " + jobs + " active job(s) • " + pending + " pending storage record(s)",
                l.contentX(), l.contentTop() + 4, jobs > 0 ? WARNING : MUTED, false);
        String questState = pageData.permissions().stream().filter(v -> "quests".equals(v.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value).findFirst().orElse("unavailable");
        String minigameState = pageData.permissions().stream().filter(v -> "minigames".equals(v.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value).findFirst().orElse("unavailable");
        String dungeonState = pageData.permissions().stream().filter(v -> "dungeons".equals(v.key()))
                .map(SsuMenuPageDataPayload.PermissionEntry::value).findFirst().orElse("unavailable");
        g.drawString(font, trim("Quests: " + questState + " • Minigames: " + minigameState + " • Dungeons: " + dungeonState, 108),
                l.contentX(), l.contentTop() + 14, MUTED, false);
        List<SsuMenuPageDataPayload.PermissionEntry> colors = pageData.permissions().stream()
                .filter(v -> "color".equals(v.kind())).toList();
        if (colors.isEmpty()) empty(g, l, "No border color categories on this page.");
        for (int i = 0; i < colors.size(); i++) {
            var entry = colors.get(i); int y = rowTextY(l, i, 82);
            g.drawString(font, cap(entry.key().replace('_', ' ')), l.contentX(), y, TEXT, false);
            g.drawString(font, entry.value() + " • " + entry.source(), l.contentX() + 150, y, MUTED, false);
        }
        g.drawString(font, "Set uses the shared RGB field. Reload is blocked while long-running jobs are active.",
                l.contentX(), l.footerY() - 18, MUTED, false);
    }

    private static int parseDisplayInt(String raw) {
        try { return Integer.parseInt(raw); } catch (RuntimeException ignored) { return 0; }
    }

    private static long parseDisplayLong(String raw) {
        try { return Long.parseLong(raw); } catch (RuntimeException ignored) { return 0L; }
    }

    private void drawRegionAdmin(GuiGraphics g,Layout l){if(pageData.regions().isEmpty())empty(g,l,"No regions on this page.");
        for(int i=0;i<pageData.regions().size();i++){var e=pageData.regions().get(i);int y=rowTextY(l,i,l.contentWidth()<440?130:105);
            g.drawString(font,e.name(),l.contentX(),y,selectedRow==i?ACCENT:TEXT,false);
            String state=e.rented()?"rented by "+blank(e.renterName())+("paused".equalsIgnoreCase(e.remainingText())?" • paused":""):"available";
            g.drawString(font,trim(shortDim(e.dimension())+" • "+e.volume()+" blocks • "+state,42),l.contentX()+100,y,MUTED,false);}
        if(selectedRow<0)g.drawString(font,"Select a region to show safe snapshot, reset, redefine, clear and rental controls.",l.contentX(),l.footerY()-18,MUTED,false);}

    private void drawPlayerInfo(GuiGraphics g, Layout l) {
        if (!playerProfileData.profile().selected()) {
            g.drawString(font, "Choose a player from the dropdown or enter an exact name.",
                    l.contentX(), l.contentTop() + 58, MUTED, false);
            return;
        }

        SsuPlayerProfileDataPayload.Profile profile = playerProfileData.profile();
        int top = l.contentTop() + 58;
        int split = l.contentWidth() >= 430 ? l.contentX() + Math.min(250, l.contentWidth() / 2) : l.contentRight();
        int permissionX = split < l.contentRight() ? split + 10 : l.contentX();
        int permissionTop = split < l.contentRight() ? top : top + 150;

        g.drawString(font, profile.name() + (profile.online() ? "  • online" : "  • offline"),
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
            g.drawString(font, trim(details.get(i), maxChars), l.contentX(), top + 16 + i * 13,
                    i == 0 ? MUTED : TEXT, false);
        }

        g.drawString(font, "Effective permissions", permissionX, permissionTop, ACCENT, true);
        if (playerProfileData.permissions().isEmpty()) {
            g.drawString(font, "No permission data available.", permissionX, permissionTop + 16, MUTED, false);
            return;
        }
        for (int i = 0; i < playerProfileData.permissions().size(); i++) {
            SsuPlayerProfileDataPayload.PermissionLine line = playerProfileData.permissions().get(i);
            int y = permissionTop + 16 + i * PROFILE_PERMISSION_ROW_HEIGHT;
            g.drawString(font, trim(line.key() + " = " + line.value(), split < l.contentRight() ? 44 : 70),
                    permissionX, y, TEXT, false);
            g.drawString(font, trim(line.source(), split < l.contentRight() ? 44 : 70), permissionX, y + 10, MUTED, false);
        }
    }

    private void drawPermissions(GuiGraphics g, Layout l, int mouseX, int mouseY) {
        if (selectedPermissionTarget.isBlank()) {
            g.drawString(font, "Choose a player, rank or claim role from the lists above.", l.contentX(), permissionListTop(l) + 8, MUTED, false);
            return;
        }
        g.drawString(font, trim(permissionData.selectedLabel() + " — " + permissionData.targetSummary(), 76),
                l.contentX(), l.contentTop() + 76, ACCENT, false);
        if (permissionData.permissions().isEmpty()) {
            g.drawString(font, "No permissions match this filter.", l.contentX(), permissionListTop(l) + 8, MUTED, false);
            return;
        }
        for (int i = 0; i < permissionData.permissions().size(); i++) {
            SsuPermissionEditorDataPayload.PermissionEntry entry = permissionData.permissions().get(i);
            int y = permissionListTop(l) + i * PERMISSION_ROW_HEIGHT;
            boolean hovered = permissionRowBounds(l, i).contains(mouseX, mouseY);
            if (hovered) g.fill(l.contentX(), y, l.contentRight(), y + PERMISSION_ROW_HEIGHT - 2, 0x662C3946);
            g.drawString(font, trim(entry.key(), 58), l.contentX() + 4, y + 7,
                    entry.directValue().isBlank() ? TEXT : GOOD, false);
        }
    }
    private void drawAccounts(GuiGraphics g,Layout l){if(pageData.accounts().isEmpty())empty(g,l,"No economy accounts on this page.");
        g.drawString(font,"Admin amount",l.contentX()+106,l.contentTop()+55,MUTED,false);
        for(int i=0;i<pageData.accounts().size();i++){var e=pageData.accounts().get(i);int y=rowTextY(l,i,76);g.drawString(font,blank(e.name()),l.contentX(),y,TEXT,false);
            g.drawString(font,e.formattedBalance()+" | rev "+e.revision(),l.contentX()+130,y,MUTED,false);}}
    private void drawJobs(GuiGraphics g,Layout l){if(pageData.jobs().isEmpty())empty(g,l,"No active jobs.");for(int i=0;i<pageData.jobs().size();i++){var e=pageData.jobs().get(i);int y=rowTextY(l,i);
        String progress=e.progress()<0?"unknown":String.format(Locale.ROOT,"%.1f%%",e.progress()*100);g.drawString(font,trim(e.description(),40),l.contentX(),y,TEXT,false);g.drawString(font,progress+" | "+e.operations()+" ops",l.contentX()+220,y,MUTED,false);}}
    private void drawRentOps(GuiGraphics g, Layout l) {
        g.drawString(font, "Cancellation refunds: player " + snapshot.economy().playerCancelRefundPercent()
                + "% • administrator " + snapshot.economy().adminCancelRefundPercent() + "%",
                l.contentX(), l.contentTop() + 3, ACCENT, false);
        if (pageData.rentOperations().isEmpty()) {
            g.drawString(font, "No rent journal records.", l.contentX(), l.contentTop() + 116, MUTED, false);
        }
        for (int i = 0; i < pageData.rentOperations().size(); i++) {
            var e = pageData.rentOperations().get(i); int y = rowTextY(l, i, 110);
            g.drawString(font, e.region() + " | " + e.action(), l.contentX(), y, TEXT, false);
            g.drawString(font, e.status(), l.contentX() + 170, y, MUTED, false);
        }
        if (selectedRow >= 0 && selectedRow < pageData.rentOperations().size()) {
            var e = pageData.rentOperations().get(selectedRow);
            detail(g, l, "Rent operation", List.of("ID: " + e.id(), "Renter: " + blank(e.renter()),
                    "Gross: " + e.grossAmount(), "Refund: " + e.refundAmount(), "Updated: " + time(e.updatedAt()),
                    "Error: " + blank(e.error())));
        }
    }
    private void drawCore(GuiGraphics g,Layout l){var c=snapshot.core();int y=l.contentTop()+8;
        String[] lines={"Active jobs: "+snapshot.activeJobs(),"Pending storage writes: "+snapshot.pendingStorageWrites(),
                "Permission checks: "+c.permissionChecks()+" | cache "+String.format(Locale.ROOT,"%.1f%%",c.permissionCacheHitPermille()/10D),
                "Region lookups: "+c.regionLookups()+" | avg candidates "+String.format(Locale.ROOT,"%.2f",c.averageRegionCandidates()),
                "Region index: "+c.regionIndexCells()+" cells | "+c.regionIndexReferences()+" refs",
                "Modules: storage, jobs, transactions, economy, claims, permissions, homes, warps, spawn, regions, mines, menu"};for(int i=0;i<lines.length;i++)g.drawString(font,lines[i],l.contentX(),y+i*18,i==5?GOOD:TEXT,false);}
    private void drawProfile(GuiGraphics g,Layout l){var c=snapshot.core();int y=l.contentTop()+8;
        g.drawString(font,"Player: "+snapshot.playerName(),l.contentX(),y,ACCENT,false);
        g.drawString(font,"Selected title: "+blank(snapshot.selectedTitle()),l.contentX(),y+18,snapshot.selectedTitleColor(),false);
        String[] lines={"Primary rank: "+snapshot.primaryRank(),"Administrator: "+yesNo(snapshot.administrator()),
                "Claims: "+c.claimCount()+" ("+c.claimedChunkCount()+" chunks)","Homes: "+c.homeCount(),"Warps: "+c.warpCount(),
                "Active rentals: "+c.activeRentalCount(),"Balance: "+snapshot.economy().formattedBalance()};
        for(int i=0;i<lines.length;i++)g.drawString(font,lines[i],l.contentX(),y+48+i*18,TEXT,false);}

    private void detail(GuiGraphics g,Layout l,String title,List<String> lines){
        boolean transactionLike = page == Page.WALLET || page == Page.TRANSACTIONS;
        int reservedRight = transactionLike ? 70 : 0;
        int usable = Math.max(160, l.contentWidth() - reservedRight);
        int w = Math.min(transactionLike ? 300 : 360, usable);
        int x = l.contentX() + Math.max(0, (usable - w) / 2);
        int bottom = l.panelBottom() - 38;
        int y = Math.max(l.contentTop() + (transactionLike ? 92 : 20), bottom - 148);
        g.fill(x,y,x+w,bottom,0xF0202832);g.renderOutline(x,y,w,bottom-y,ACCENT);g.drawString(font,title,x+7,y+7,ACCENT,true);
        int visible=Math.max(1,Math.min(7,(bottom-y-28)/13));
        for(int i=0;i<Math.min(visible,lines.size());i++)g.drawString(font,trim(lines.get(i),Math.max(24,(w-14)/6)),x+7,y+23+i*13,MUTED,false);}
    private void empty(GuiGraphics g,Layout l,String text){g.drawString(font,text,l.contentX(),l.contentTop()+62,MUTED,false);}

    private int rowY(Layout l,int i){return rowY(l,i,58);} private int rowY(Layout l,int i,int offset){return l.contentTop()+offset+i*27;}
    private int rowTextY(Layout l,int i){return rowY(l,i)+6;} private int rowTextY(Layout l,int i,int offset){return rowY(l,i,offset)+6;}
    private boolean compactDashboardPage() { return page == Page.WALLET || page == Page.PROFILE; }

    @Override
    public int ssuLogicalCanvasWidth() { return compactDashboardPage() ? 544 : 680; }

    @Override
    public int ssuLogicalCanvasHeight() { return compactDashboardPage() ? 312 : 390; }

    private Layout layout(){int pw=ssuLogicalCanvasWidth();int ph=ssuLogicalCanvasHeight();int px=(width-pw)/2;int py=(height-ph)/2;
        // The Dashboard uses one canonical logical layout on every display.
        // SsuGuiScale automatically fits this canvas when Minecraft exposes a
        // smaller logical viewport, so the sidebar/columns never reflow.
        boolean side=true;int sx=px+10;int cx=sx+112;int cw=px+pw-12-cx;return new Layout(px,py,pw,ph,sx,cx,cw,side);}
    private static String activationLabel(String value) { return "KEYBIND".equals(value) ? "Keybind" : "Crouch"; }
    private static String nextActivation(String value) { return "KEYBIND".equals(value) ? "SNEAK" : "KEYBIND"; }
    private static int nextBrightness(int value) { return value >= 100 ? 25 : Math.min(100, value + 15); }
    private static String nextIndicatorStyle(String value) {
        return switch (value == null ? "FLOATING" : value.toUpperCase(Locale.ROOT)) {
            case "FLOATING" -> "HEARTS";
            case "HEARTS" -> "COMPACT";
            case "COMPACT" -> "POP";
            case "POP" -> "BURST";
            case "BURST" -> "DROP";
            default -> "FLOATING";
        };
    }
    private static String indicatorStyleLabel(String value) {
        return switch (value == null ? "FLOATING" : value.toUpperCase(Locale.ROOT)) {
            case "HEARTS" -> "Hearts";
            case "COMPACT" -> "Compact";
            case "POP" -> "Pop";
            case "BURST" -> "Burst";
            case "DROP" -> "Drop";
            default -> "Floating";
        };
    }
    private static int nextMapLiveUpdateRadius(int value) {
        int[] values = {1, 2, 4, 6, 8, 12, 16, 24, 32};
        for (int candidate : values) if (candidate > value) return candidate;
        return values[0];
    }
    private static String colorHex(int color) { return String.format(java.util.Locale.ROOT, "#%06X", color & 0xFFFFFF); }
    private static String miningColorName(int color) {
        return MinecraftColorPalette.name(color);
    }
    private static int nextMiningColor(int current) {
        return MinecraftColorPalette.next(current);
    }

    private void center(GuiGraphics g,String s,int x,int y,int color){String v=blank(s);g.drawString(font,v,x-font.width(v)/2,y,color,false);}
    private static ResourceLocation texture(String file) {
        return ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "textures/gui/dashboard/" + file);
    }
    private static String onOff(boolean v){return v?"ON":"OFF";} private static String keepDelete(boolean v){return v?"DELETE":"KEEP";} private static String yesNo(boolean v){return v?"Yes":"No";}
    private static String blank(String v){return v==null||v.isBlank()?"-":v;} private static String cap(String v){return v==null||v.isBlank()?"":Character.toUpperCase(v.charAt(0))+v.substring(1);}
    private static String shortDim(String v){int i=v.indexOf(':');return i>=0?v.substring(i+1):v;}
    private static String pos(double x,double y,double z){return (int)Math.floor(x)+", "+(int)Math.floor(y)+", "+(int)Math.floor(z);}
    private static String trim(String v,int max){if(v==null)return "";return v.length()<=max?v:v.substring(0,Math.max(0,max-1))+"…";}
    private String fitToWidth(String value, int maxWidth) {
        if (value == null || value.isEmpty() || maxWidth <= 0) return "";
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "…";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + ellipsis) > maxWidth) end--;
        return end <= 0 ? ellipsis : value.substring(0, end).stripTrailing() + ellipsis;
    }
    private static String time(long epoch){return epoch<=0?"-":TIME.format(Instant.ofEpochMilli(epoch));}
    @Override public boolean isPauseScreen(){return false;}

    private enum SettingsCategory {
        INTERFACE("Interface"), GENERAL("General"), IDENTITY("Identity"), COMBAT("Combat"), MINIMAP("Minimap"), WORLD_MAP("World map"), UTILITY_MINING("Mining"), BORDERS("Borders"), MAIL("Mail");
        private final String label;
        SettingsCategory(String label) { this.label = label; }
    }

    private enum Page {
        HOME("Dashboard","Player tools and personal overview",""), PROFILE("Profile","Your server account and property",""),
        MAIL("Mail","Inbox, attachments and sent mail",""),
        AUCTION_HOUSE("Auction House","Browse, buy and sell player auctions",""),
        QUESTS("Questbook","Available, active and completed quests",""),
        ACHIEVEMENTS("Achievements","Earned and unearned achievements",""),
        ACHIEVEMENTS_ADMIN("Achievement Administration","Create, edit and reset achievements",""),
        COSMETICS("Cosmetics","Cosmetic unlocks and customization - coming soon",""),
        MINIGAMES("Minigames","Queues, arenas and active matches",""),
        MINIGAME_ADMIN("Minigame Administration","Modes, arenas, setup and live-match control",""),
        DUNGEONS("Customized Dungeons","Parties, stages, checkpoints and dungeon runs",""),
        CLAIMS("Claims & Land","Owned claims, border visibility and claim-linked homes","claims"), HOMES("Homes","Personal teleport locations linked to one claim","homes"),
        TRAVEL("Travel","Homes, warps and server destinations","travel"), MY_WARPS("My Warps","Rent and manage personal warps","player_warps"), TRAVEL_ADMIN("Travel Management","Server warp and spawn administration","travel_admin"), ADMIN_CLAIMS("Player Claims","Administrative claim inspection and recovery","admin_claims"),
        RANKS("Rank Management","Create, rename and maintain permission ranks","ranks"),
        WALLET("Wallet & Transactions","Payments and your personal transaction history","wallet_transactions"),
        ECONOMICS("Economics","Accounts, transactions, taxes and economy journals",""),
        TRANSACTIONS("Transactions","Filter and inspect the complete transaction journal","transactions"),
        AUCTION_TAX("Auction House Tax","Tax withheld from completed Auction House sales","auction_tax"),
        CLAIM_TAX("Player Claim Tax","Recurring per-chunk taxation and dimension multipliers","claim_tax"),
        WARP_RENTAL("Player Warp Rentals","Prepaid rental pricing and renewal period","warp_rental"),
        REGIONS("Regions","Server-region details, visibility and settings","regions"),
        REGION_ADMIN("Region Maintenance","Snapshots, recovery, selection and rental administration","region_admin"),
        UTILITY_MINING_ADMIN("Utility Mining","Server rules for Treecapitator and Veinminer","utility_mining_admin"), MAINTENANCE("Maintenance","Reload, refresh and visualization defaults","maintenance"), SETTINGS("Settings","Personal settings",""),
        ADMIN("Admin Center","Paged administrative tools",""), DIMENSIONS("Dimensions","Create and configure custom dimensions",""),
        ONBOARDING_ADMIN("Onboarding & Spawns","First-join rules, introduction and dimension-aware destinations",""),
        KITS("Kits","Available kits and cooldowns",""), KIT_ADMIN("Kit Administration","Create and edit player kits",""), MINES("Mines","Available resettable mines",""), MINE_ADMIN("Mine Administration","Dedicated mine setup and reset controls",""), JAIL_ADMIN("Jail Administration","Nested prison facilities, cells and work areas",""), SUPPORT("Support & Reports","Create and follow support tickets",""), SERVER_OPERATIONS("Server Operations","Backups, scheduler, moderation, health and world tools",""), MODULE_SETTINGS("Module Settings","Global module switches and render distances",""),
        ADMIN_TOOLS("Admin Tools","Purpose-built setup and editing tools",""),
        HOLOGRAMS("Holograms","Remote floating-text and hologram administration","holograms"),
        STATISTICS("Player Statistics","Custom counters, storage and Floating Text sources","statistics"), PLAYER_INFO("Player Info & Profile","Admin player browser and effective permissions",""),
        PERMISSIONS("Permissions","Global and per-dimension rank/player permissions","permissions"),
        ACCOUNTS("Economy Accounts","Searchable account browser","accounts"),
        JOBS("Active Jobs","Scheduler progress and cancellation","jobs"),
        RENT_OPERATIONS("Rent Journal","Rental reconciliation operations","rent_operations"), CORE("Core Status","Storage, indexing and module migration"," ");
        private final String label,subtitle,remote;Page(String l,String s,String r){label=l;subtitle=s;remote=r.trim();}
        String label(){return label;}String subtitle(){return subtitle;}String remoteId(){return remote;}boolean hasRemoteData(){return !remote.isBlank();}
    }
    private record Module(String label, String hint, ResourceLocation icon, Page page, boolean enabled){}
    private record AdminTool(String label, String hint, String id){}
    private record ModuleSwitch(String label, String key, boolean configured, boolean active, String reason,
                                List<String> required, List<String> optional, List<String> integrations){}
    private record ModuleTile(Rect bounds, Module module){}
    private record SettingsTooltip(Rect bounds, List<Component> lines){}
    private record Rect(int x, int y, int width, int height) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    private record Layout(int panelX,int panelY,int panelWidth,int panelHeight,int sidebarX,int contentX,int contentWidth,boolean sidebarVisible){
        int panelRight(){return panelX+panelWidth;}int panelBottom(){return panelY+panelHeight;}int contentRight(){return contentX+contentWidth;}
        int contentTop(){return panelY+47;}int footerY(){return panelBottom()-29;}}
}
