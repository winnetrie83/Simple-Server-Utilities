package be.winnetrie.mod.simpleserverutilities;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import be.winnetrie.mod.simpleserverutilities.auction.AuctionHouseEvents;
import be.winnetrie.mod.simpleserverutilities.auction.AuctionHouseManager;
import be.winnetrie.mod.simpleserverutilities.auction.AuctionHouseModule;
import be.winnetrie.mod.simpleserverutilities.auction.ModAuctionMenus;
import be.winnetrie.mod.simpleserverutilities.claim.player.PlayerClaimManager;
import be.winnetrie.mod.simpleserverutilities.claim.tax.PlayerClaimTaxEvents;
import be.winnetrie.mod.simpleserverutilities.claim.tax.PlayerClaimTaxManager;
import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimModule;
import be.winnetrie.mod.simpleserverutilities.claim.player.ClaimPresenceEvents;
import be.winnetrie.mod.simpleserverutilities.blockinfo.BlockInformationEvents;
import be.winnetrie.mod.simpleserverutilities.blockinfo.BlockInformationModule;
import be.winnetrie.mod.simpleserverutilities.content.ContentActionEngine;
import be.winnetrie.mod.simpleserverutilities.content.ContentConditionEngine;
import be.winnetrie.mod.simpleserverutilities.content.ContentCoreEvents;
import be.winnetrie.mod.simpleserverutilities.content.ContentCoreModule;
import be.winnetrie.mod.simpleserverutilities.content.ContentDependencyValidator;
import be.winnetrie.mod.simpleserverutilities.content.ContentEventBus;
import be.winnetrie.mod.simpleserverutilities.content.ContentProgressionManager;
import be.winnetrie.mod.simpleserverutilities.core.SsuCore;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobEvents;
import be.winnetrie.mod.simpleserverutilities.core.job.SsuJobScheduler;
import be.winnetrie.mod.simpleserverutilities.core.job.JobSchedulerModule;
import be.winnetrie.mod.simpleserverutilities.core.performance.SsuPerformanceMonitor;
import be.winnetrie.mod.simpleserverutilities.core.performance.PerformanceModule;
import be.winnetrie.mod.simpleserverutilities.core.storage.BatchedStorageService;
import be.winnetrie.mod.simpleserverutilities.core.storage.StorageModule;
import be.winnetrie.mod.simpleserverutilities.core.transaction.SsuTransactionManager;
import be.winnetrie.mod.simpleserverutilities.core.transaction.TransactionModule;
import be.winnetrie.mod.simpleserverutilities.cropharvesting.CropsHarvestingEvents;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyManager;
import be.winnetrie.mod.simpleserverutilities.economy.EconomyModule;
import be.winnetrie.mod.simpleserverutilities.command.SSUCommands;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramEvents;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramManager;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramModule;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramToolManager;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramToolEvents;
import be.winnetrie.mod.simpleserverutilities.home.PlayerHomeManager;
import be.winnetrie.mod.simpleserverutilities.identity.DamageIndicatorEvents;
import be.winnetrie.mod.simpleserverutilities.identity.IdentityEvents;
import be.winnetrie.mod.simpleserverutilities.identity.IdentityModule;
import be.winnetrie.mod.simpleserverutilities.identity.PlayerIdentityManager;
import be.winnetrie.mod.simpleserverutilities.home.HomeModule;
import be.winnetrie.mod.simpleserverutilities.menu.SsuMenuService;
import be.winnetrie.mod.simpleserverutilities.mail.MailEvents;
import be.winnetrie.mod.simpleserverutilities.mail.MailManager;
import be.winnetrie.mod.simpleserverutilities.mail.MailModule;
import be.winnetrie.mod.simpleserverutilities.mail.ModMailMenus;
import be.winnetrie.mod.simpleserverutilities.mapmarker.MapMarkerEvents;
import be.winnetrie.mod.simpleserverutilities.mapmarker.MapMarkerManager;
import be.winnetrie.mod.simpleserverutilities.mapmarker.MapMarkerModule;
import be.winnetrie.mod.simpleserverutilities.menu.MenuModule;
import be.winnetrie.mod.simpleserverutilities.network.ModNetworking;
import be.winnetrie.mod.simpleserverutilities.npc.ModNpcMenus;
import be.winnetrie.mod.simpleserverutilities.npc.NpcEvents;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueManager;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueService;
import be.winnetrie.mod.simpleserverutilities.npc.NpcFunctionService;
import be.winnetrie.mod.simpleserverutilities.npc.NpcServiceRegistry;
import be.winnetrie.mod.simpleserverutilities.npc.NpcManager;
import be.winnetrie.mod.simpleserverutilities.npc.NpcModule;
import be.winnetrie.mod.simpleserverutilities.npc.NpcToolManager;
import be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopManager;
import be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopModule;
import be.winnetrie.mod.simpleserverutilities.quest.QuestGameplayEvents;
import be.winnetrie.mod.simpleserverutilities.quest.QuestManager;
import be.winnetrie.mod.simpleserverutilities.quest.QuestModule;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameEvents;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameManager;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameSetupToolEvents;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameSetupToolManager;
import be.winnetrie.mod.simpleserverutilities.minigame.MinigameModule;
import be.winnetrie.mod.simpleserverutilities.mine.MineEvents;
import be.winnetrie.mod.simpleserverutilities.mine.MineManager;
import be.winnetrie.mod.simpleserverutilities.mine.MineModule;
import be.winnetrie.mod.simpleserverutilities.mine.MineSetupToolManager;
import be.winnetrie.mod.simpleserverutilities.jail.JailEvents;
import be.winnetrie.mod.simpleserverutilities.jail.JailManager;
import be.winnetrie.mod.simpleserverutilities.jail.JailModule;
import be.winnetrie.mod.simpleserverutilities.jail.JailSetupToolManager;
import be.winnetrie.mod.simpleserverutilities.dungeon.DungeonEvents;
import be.winnetrie.mod.simpleserverutilities.dungeon.DungeonManager;
import be.winnetrie.mod.simpleserverutilities.dungeon.DungeonModule;
import be.winnetrie.mod.simpleserverutilities.dimension.ManagedDimensionManager;
import be.winnetrie.mod.simpleserverutilities.dimension.DimensionModule;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionManager;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionModule;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionPlayerEvents;
import be.winnetrie.mod.simpleserverutilities.protection.ClaimProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.EntityProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.ExplosionProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.FireProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.FluidProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.PistonProtectionEvents;
import be.winnetrie.mod.simpleserverutilities.protection.RedstoneProtectionEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import be.winnetrie.mod.simpleserverutilities.region.RegionInteractionEvents;
import be.winnetrie.mod.simpleserverutilities.region.RegionModule;
import be.winnetrie.mod.simpleserverutilities.region.RegionManager;
import be.winnetrie.mod.simpleserverutilities.region.RegionRentJournalManager;
import be.winnetrie.mod.simpleserverutilities.region.RegionRentEvents;
import be.winnetrie.mod.simpleserverutilities.region.RegionSelectionToolManager;
import be.winnetrie.mod.simpleserverutilities.region.RegionSnapshotManager;
import be.winnetrie.mod.simpleserverutilities.settings.PlayerUiPreferencesManager;
import be.winnetrie.mod.simpleserverutilities.settings.UiPreferencesModule;
import be.winnetrie.mod.simpleserverutilities.spawn.ServerSpawnManager;
import be.winnetrie.mod.simpleserverutilities.spawn.SpawnModule;
import be.winnetrie.mod.simpleserverutilities.serverops.ServerOperationsManager;
import be.winnetrie.mod.simpleserverutilities.serverops.ServerOperationsModule;
import be.winnetrie.mod.simpleserverutilities.serverops.ServerOperationsEvents;
import be.winnetrie.mod.simpleserverutilities.statistics.PlayerStatisticsManager;
import be.winnetrie.mod.simpleserverutilities.statistics.StatisticsEvents;
import be.winnetrie.mod.simpleserverutilities.statistics.StatisticsModule;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportEvents;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportModule;
import be.winnetrie.mod.simpleserverutilities.teleport.TeleportManager;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderVisualizationEvents;
import be.winnetrie.mod.simpleserverutilities.visualization.VisualizationModule;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderVisualizationService;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderVisualizationSettingsManager;
import be.winnetrie.mod.simpleserverutilities.warp.WarpManager;
import be.winnetrie.mod.simpleserverutilities.warp.WarpRentalEvents;
import be.winnetrie.mod.simpleserverutilities.warp.WarpModule;
import be.winnetrie.mod.simpleserverutilities.utilitymining.UtilityMiningEvents;
import be.winnetrie.mod.simpleserverutilities.utilitymining.UtilityMiningManager;
import be.winnetrie.mod.simpleserverutilities.utilitymining.UtilityMiningModule;
import be.winnetrie.mod.simpleserverutilities.utilitymining.PlacedTreeBlockTracker;

@Mod(SimpleServerUtilities.MODID)
public class SimpleServerUtilities {

    public static final String MODID = "simpleserverutilities";
    public static final Logger LOGGER = LogUtils.getLogger();

    /*
     * Canonical subsystem ownership lives in these single manager instances.
     * SsuModule implementations are dependency-aware lifecycle/service adapters
     * around these managers; they never hold a second copy of module state.
     */
    public static final SsuCore CORE = new SsuCore();
    public static final BatchedStorageService STORAGE = new BatchedStorageService();
    public static final SsuJobScheduler JOBS = new SsuJobScheduler();
    public static final SsuPerformanceMonitor PERFORMANCE = new SsuPerformanceMonitor();
    public static final SsuTransactionManager TRANSACTIONS = new SsuTransactionManager();
    public static final ContentProgressionManager CONTENT_PROGRESS = new ContentProgressionManager();
    public static final ContentConditionEngine CONTENT_CONDITIONS = new ContentConditionEngine(CONTENT_PROGRESS);
    public static final ContentActionEngine CONTENT_ACTIONS = new ContentActionEngine(CONTENT_PROGRESS, TRANSACTIONS);
    public static final ContentEventBus CONTENT_EVENTS = new ContentEventBus();
    public static final ContentDependencyValidator CONTENT_DEPENDENCIES = new ContentDependencyValidator();
    public static final EconomyManager ECONOMY = new EconomyManager();
    public static final MailManager MAIL = new MailManager();
    public static final AuctionHouseManager AUCTION_HOUSE = new AuctionHouseManager();

    public static final PlayerClaimManager PLAYER_CLAIMS = new PlayerClaimManager();
    public static final PlayerClaimTaxManager CLAIM_TAX = new PlayerClaimTaxManager();
    public static final ManagedDimensionManager DIMENSIONS = new ManagedDimensionManager();
    public static final RegionManager REGIONS = new RegionManager();
    public static final RegionRentJournalManager REGION_RENT_JOURNAL = new RegionRentJournalManager();
    public static final RegionSnapshotManager REGION_SNAPSHOTS = new RegionSnapshotManager();
    public static final RegionSelectionToolManager REGION_SELECTION_TOOLS = new RegionSelectionToolManager();
    public static final PlayerHomeManager HOMES = new PlayerHomeManager();
    public static final WarpManager WARPS = new WarpManager();
    public static final ServerSpawnManager SERVER_SPAWN = new ServerSpawnManager();
    public static final PermissionManager PERMISSIONS = new PermissionManager();
    public static final TeleportManager TELEPORTS = new TeleportManager();
    public static final BorderVisualizationSettingsManager BORDER_SETTINGS = new BorderVisualizationSettingsManager();
    public static final BorderVisualizationService BORDER_VISUALIZATIONS = new BorderVisualizationService();
    public static final SsuMenuService MENUS = new SsuMenuService();
    public static final PlayerUiPreferencesManager UI_PREFERENCES = new PlayerUiPreferencesManager();
    public static final PlayerIdentityManager IDENTITY = new PlayerIdentityManager();
    public static final UtilityMiningManager UTILITY_MINING = new UtilityMiningManager();
    public static final PlacedTreeBlockTracker TREE_PLACEMENTS = new PlacedTreeBlockTracker();
    public static final HologramManager HOLOGRAMS = new HologramManager();
    public static final HologramToolManager HOLOGRAM_TOOLS = new HologramToolManager();
    public static final PlayerStatisticsManager STATISTICS = new PlayerStatisticsManager();
    public static final MapMarkerManager MAP_MARKERS = new MapMarkerManager();
    public static final NpcManager NPCS = new NpcManager();
    public static final NpcToolManager NPC_TOOLS = new NpcToolManager();
    public static final NpcDialogueManager NPC_DIALOGUE_DEFINITIONS = new NpcDialogueManager();
    public static final NpcServiceRegistry NPC_SERVICES = new NpcServiceRegistry();
    public static final NpcDialogueService NPC_DIALOGUES = new NpcDialogueService(NPC_DIALOGUE_DEFINITIONS, NPC_SERVICES);
    public static final NpcFunctionService NPC_FUNCTIONS = new NpcFunctionService();
    public static final NpcShopManager NPC_SHOPS = new NpcShopManager();
    public static final QuestManager QUESTS = new QuestManager();
    public static final MinigameManager MINIGAMES = new MinigameManager();
    public static final MinigameSetupToolManager MINIGAME_SETUP_TOOLS = new MinigameSetupToolManager();
    public static final MineManager MINES = new MineManager();
    public static final MineSetupToolManager MINE_SETUP_TOOLS = new MineSetupToolManager();
    public static final JailManager JAILS = new JailManager();
    public static final JailSetupToolManager JAIL_SETUP_TOOLS = new JailSetupToolManager();
    public static final DungeonManager DUNGEONS = new DungeonManager();
    public static final be.winnetrie.mod.simpleserverutilities.onboarding.OnboardingManager ONBOARDING = new be.winnetrie.mod.simpleserverutilities.onboarding.OnboardingManager();
    public static final be.winnetrie.mod.simpleserverutilities.moderation.ModerationManager MODERATION = new be.winnetrie.mod.simpleserverutilities.moderation.ModerationManager();
    public static final be.winnetrie.mod.simpleserverutilities.kits.KitManager KITS = new be.winnetrie.mod.simpleserverutilities.kits.KitManager();
    public static final ServerOperationsManager SERVER_OPERATIONS = new ServerOperationsManager();

    public SimpleServerUtilities(IEventBus modEventBus, ModContainer modContainer) {
        ModMailMenus.MENU_TYPES.register(modEventBus);
        ModAuctionMenus.MENU_TYPES.register(modEventBus);
        ModNpcMenus.MENU_TYPES.register(modEventBus);
        be.winnetrie.mod.simpleserverutilities.kits.ModKitMenus.MENU_TYPES.register(modEventBus);
        be.winnetrie.mod.simpleserverutilities.moderation.ModModerationMenus.MENU_TYPES.register(modEventBus);
        CORE.modules().register(new StorageModule(STORAGE));
        CORE.modules().register(new JobSchedulerModule(JOBS));
        CORE.modules().register(new PerformanceModule(PERFORMANCE));
        CORE.modules().register(new TransactionModule(TRANSACTIONS));
        CORE.modules().register(new ContentCoreModule(
                CONTENT_PROGRESS, CONTENT_CONDITIONS, CONTENT_ACTIONS, CONTENT_EVENTS, CONTENT_DEPENDENCIES));
        CORE.modules().register(new EconomyModule(ECONOMY));
        CORE.modules().register(new ClaimModule(PLAYER_CLAIMS, CLAIM_TAX));
        CORE.modules().register(new PermissionModule(PERMISSIONS));
        CORE.modules().register(new DimensionModule(DIMENSIONS));
        CORE.modules().register(new MailModule(MAIL));
        CORE.modules().register(new AuctionHouseModule(AUCTION_HOUSE));
        CORE.modules().register(new HomeModule(HOMES));
        CORE.modules().register(new WarpModule(WARPS));
        CORE.modules().register(new UiPreferencesModule(UI_PREFERENCES));
        CORE.modules().register(new RegionModule(REGIONS, REGION_SNAPSHOTS, REGION_RENT_JOURNAL, REGION_SELECTION_TOOLS));
        CORE.modules().register(new TeleportModule(TELEPORTS));
        CORE.modules().register(new SpawnModule(SERVER_SPAWN));
        CORE.modules().register(new be.winnetrie.mod.simpleserverutilities.onboarding.OnboardingModule(ONBOARDING));
        CORE.modules().register(new JailModule(JAILS, JAIL_SETUP_TOOLS));
        CORE.modules().register(new be.winnetrie.mod.simpleserverutilities.moderation.ModerationModule(MODERATION));
        CORE.modules().register(new be.winnetrie.mod.simpleserverutilities.kits.KitModule(KITS));
        CORE.modules().register(new ServerOperationsModule(SERVER_OPERATIONS));
        CORE.modules().register(new VisualizationModule(BORDER_SETTINGS, BORDER_VISUALIZATIONS));
        CORE.modules().register(new MenuModule(MENUS));
        CORE.modules().register(new UtilityMiningModule(UTILITY_MINING, TREE_PLACEMENTS));
        CORE.modules().register(new HologramModule(HOLOGRAMS, HOLOGRAM_TOOLS));
        CORE.modules().register(new StatisticsModule(STATISTICS));
        CORE.modules().register(new MapMarkerModule(MAP_MARKERS));
        CORE.modules().register(new NpcModule(NPCS, NPC_TOOLS, NPC_DIALOGUE_DEFINITIONS, NPC_DIALOGUES));
        CORE.modules().register(new NpcShopModule(NPC_SHOPS));
        CORE.modules().register(new QuestModule(QUESTS));
        CORE.modules().register(new MinigameModule(MINIGAMES));
        CORE.modules().register(new MineModule(MINES, MINE_SETUP_TOOLS));
        CORE.modules().register(new IdentityModule(IDENTITY));
        CORE.modules().register(new DungeonModule(DUNGEONS));
        CORE.modules().register(new BlockInformationModule());
        CORE.initialize();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(ModNetworking::register);

        NeoForge.EVENT_BUS.register(this);

        NeoForge.EVENT_BUS.register(ClaimProtectionEvents.class);
        NeoForge.EVENT_BUS.register(ClaimPresenceEvents.class);
        NeoForge.EVENT_BUS.register(PlayerClaimTaxEvents.class);
        NeoForge.EVENT_BUS.register(WarpRentalEvents.class);
        NeoForge.EVENT_BUS.register(PistonProtectionEvents.class);
        NeoForge.EVENT_BUS.register(RedstoneProtectionEvents.class);
        NeoForge.EVENT_BUS.register(ExplosionProtectionEvents.class);
        NeoForge.EVENT_BUS.register(EntityProtectionEvents.class);
        NeoForge.EVENT_BUS.register(FluidProtectionEvents.class);
        NeoForge.EVENT_BUS.register(FireProtectionEvents.class);
        NeoForge.EVENT_BUS.register(TeleportEvents.class);
        NeoForge.EVENT_BUS.register(SsuJobEvents.class);
        NeoForge.EVENT_BUS.register(ContentCoreEvents.class);
        NeoForge.EVENT_BUS.register(BorderVisualizationEvents.class);
        NeoForge.EVENT_BUS.register(RegionRentEvents.class);
        NeoForge.EVENT_BUS.register(RegionInteractionEvents.class);
        NeoForge.EVENT_BUS.register(PermissionPlayerEvents.class);
        NeoForge.EVENT_BUS.register(MailEvents.class);
        NeoForge.EVENT_BUS.register(AuctionHouseEvents.class);
        NeoForge.EVENT_BUS.register(CropsHarvestingEvents.class);
        NeoForge.EVENT_BUS.register(UtilityMiningEvents.class);
        NeoForge.EVENT_BUS.register(HologramEvents.class);
        NeoForge.EVENT_BUS.register(HologramToolEvents.class);
        NeoForge.EVENT_BUS.register(StatisticsEvents.class);
        NeoForge.EVENT_BUS.register(BlockInformationEvents.class);
        NeoForge.EVENT_BUS.register(MapMarkerEvents.class);
        NeoForge.EVENT_BUS.register(NpcEvents.class);
        NeoForge.EVENT_BUS.register(QuestGameplayEvents.class);
        NeoForge.EVENT_BUS.register(MinigameEvents.class);
        NeoForge.EVENT_BUS.register(MinigameSetupToolEvents.class);
        NeoForge.EVENT_BUS.register(MineEvents.class);
        NeoForge.EVENT_BUS.register(JailEvents.class);
        NeoForge.EVENT_BUS.register(IdentityEvents.class);
        NeoForge.EVENT_BUS.register(DamageIndicatorEvents.class);
        NeoForge.EVENT_BUS.register(DungeonEvents.class);
        NeoForge.EVENT_BUS.register(be.winnetrie.mod.simpleserverutilities.spawn.SpawnEvents.class);
        NeoForge.EVENT_BUS.register(be.winnetrie.mod.simpleserverutilities.moderation.ModerationEvents.class);
        NeoForge.EVENT_BUS.register(be.winnetrie.mod.simpleserverutilities.onboarding.OnboardingEvents.class);
        NeoForge.EVENT_BUS.register(ServerOperationsEvents.class);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Simple Server Utilities loaded");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        CORE.onServerStarting(event.getServer());
        LOGGER.info("Simple Server Utilities server starting");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        CORE.beforeServerStopping(event.getServer());
        CORE.onServerStopping(event.getServer());
        LOGGER.info("Simple Server Utilities server stopping");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SSUCommands.register(event.getDispatcher());
    }
}
