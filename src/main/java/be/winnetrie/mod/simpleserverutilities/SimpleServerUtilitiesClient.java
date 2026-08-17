package be.winnetrie.mod.simpleserverutilities;

import com.mojang.blaze3d.platform.InputConstants;
import be.winnetrie.mod.simpleserverutilities.client.identity.DamageIndicatorClientState;
import be.winnetrie.mod.simpleserverutilities.client.identity.DamageIndicatorRenderer;
import be.winnetrie.mod.simpleserverutilities.client.identity.IdentityClientEvents;
import be.winnetrie.mod.simpleserverutilities.client.identity.EntityInsightClientEvents;
import be.winnetrie.mod.simpleserverutilities.client.identity.EntityInsightClientState;
import be.winnetrie.mod.simpleserverutilities.client.identity.PlayerIdentityClientState;
import be.winnetrie.mod.simpleserverutilities.client.identity.PlayerTitleRenderer;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramClientEvents;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramImageCache;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramClientState;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramRenderer;
import be.winnetrie.mod.simpleserverutilities.client.npc.NpcLabelClientState;
import be.winnetrie.mod.simpleserverutilities.client.npc.NpcCustomTextureClientState;
import be.winnetrie.mod.simpleserverutilities.client.npc.NpcLabelRenderer;
import be.winnetrie.mod.simpleserverutilities.client.gui.SsuGuiScaleInputEvents;
import be.winnetrie.mod.simpleserverutilities.client.gui.ManagedDimensionScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.ClaimMapScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.SsuDashboardScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.KnownPlayerPickerScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.PropertySettingsScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.TrustedPlayersScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionPermissionScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionSetupScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionSnapshotPreviewScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionSelectionToolScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionSelectionEditScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.WorldEditCompactOverlayScreen;
import be.winnetrie.mod.simpleserverutilities.client.region.RegionSelectionClientStorage;
import be.winnetrie.mod.simpleserverutilities.client.region.RegionSnapshotPreviewClientState;
import be.winnetrie.mod.simpleserverutilities.client.region.RegionSnapshotPreviewRenderer;
import be.winnetrie.mod.simpleserverutilities.client.gui.WorldMapScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MailScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MailComposeScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.AuctionHouseScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.AuctionSellScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.HologramEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcQuestWorkflowScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcLoadoutScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcAdminScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcSpawnProfileEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcDialogueScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcDialogueEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcFunctionMenuScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcShopScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcShopAdminScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcAbilityLibraryScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcAbilityWorkshopScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcShopEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcItemPriceCatalogScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.QuestBookScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.QuestEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.AchievementMenuScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.AchievementEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameLobbyScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameAdminScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameSetupToolScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameSetupCreateScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameSelectionCreateScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameResultsScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameProfileScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameMatchOverviewScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameValidationScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameDiagnosticsScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.TitleManagerScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RankDisplayEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.minigame.MinigameHudClientState;
import be.winnetrie.mod.simpleserverutilities.client.minigame.KingOfTheHillVisualClientState;
import be.winnetrie.mod.simpleserverutilities.client.minigame.KingOfTheHillVisualRenderer;
import be.winnetrie.mod.simpleserverutilities.client.minigame.CaptureTheFlagClientState;
import be.winnetrie.mod.simpleserverutilities.client.minigame.DominationClientState;
import be.winnetrie.mod.simpleserverutilities.client.minigame.DominationRenderer;
import be.winnetrie.mod.simpleserverutilities.client.minigame.MinigameCastBarClientState;
import be.winnetrie.mod.simpleserverutilities.client.minigame.MinigameSetupVisualClientState;
import be.winnetrie.mod.simpleserverutilities.client.minigame.MinigameKillFeedClientState;
import be.winnetrie.mod.simpleserverutilities.client.minigame.MinigameSetupVisualRenderer;
import be.winnetrie.mod.simpleserverutilities.client.gui.DungeonLobbyScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.DungeonEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.StatisticEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.blockinfo.BlockInformationClientState;
import be.winnetrie.mod.simpleserverutilities.client.map.AerialMapAtlas;
import be.winnetrie.mod.simpleserverutilities.client.mapmarker.MapMarkerClientState;
import be.winnetrie.mod.simpleserverutilities.client.mapmarker.MapMarkerRenderer;
import be.winnetrie.mod.simpleserverutilities.client.minimap.MinimapClientState;
import be.winnetrie.mod.simpleserverutilities.client.visualization.BorderVisualizationClientState;
import be.winnetrie.mod.simpleserverutilities.client.visualization.ClaimRegionBorderRenderer;
import be.winnetrie.mod.simpleserverutilities.client.utilitymining.UtilityMiningClientState;
import be.winnetrie.mod.simpleserverutilities.client.utilitymining.UtilityMiningOutlineRenderer;
import be.winnetrie.mod.simpleserverutilities.network.ClientPayloadRouter;
import be.winnetrie.mod.simpleserverutilities.network.BorderVisualizationPayload;
import be.winnetrie.mod.simpleserverutilities.network.EntityInsightPayload;
import be.winnetrie.mod.simpleserverutilities.network.BlockInformationContentPayload;
import be.winnetrie.mod.simpleserverutilities.network.BlockInformationStatePayload;
import be.winnetrie.mod.simpleserverutilities.network.StatisticEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.StatisticEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.HologramSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcLabelSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcTextureSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcQuestWorkflowOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcLoadoutResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAdminListPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcSpawnProfileEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcSpawnProfileEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueViewPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcFunctionMenuPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopAdminDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityLibraryDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAbilityEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcItemPriceCatalogDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestBookDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.AchievementMenuDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.AchievementEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.AchievementEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameHudPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameKothVisualPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameCastBarPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameDominationVisualPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameCtfVisualPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSelectionCreateResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupToolOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupVisualPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameResultsPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameProfilePayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameMatchOverviewPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameMatchOverviewRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameKillFeedPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSpectatorActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameValidationPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameDiagnosticsPayload;
import be.winnetrie.mod.simpleserverutilities.network.TitleManagerDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.RankDisplayDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.PlayerIdentitySyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.DamageIndicatorPayload;
import be.winnetrie.mod.simpleserverutilities.network.DungeonLobbyDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.DungeonEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.DungeonEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSetupOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSnapshotPreviewPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionToolOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionSelectionClientTemplatePayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinimapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailComposeResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailRecipientSuggestionsPayload;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.AuctionHouseActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuDimensionManagerDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuPageDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPlayerProfileDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuTrustedPlayersDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.UtilityMiningActivationPayload;
import be.winnetrie.mod.simpleserverutilities.network.UtilityMiningPreviewPayload;
import be.winnetrie.mod.simpleserverutilities.network.UtilityMiningPreviewRequestPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import be.winnetrie.mod.simpleserverutilities.mail.ModMailMenus;
import be.winnetrie.mod.simpleserverutilities.auction.ModAuctionMenus;
import be.winnetrie.mod.simpleserverutilities.npc.ModNpcMenus;
import be.winnetrie.mod.simpleserverutilities.npc.ModNpcEntities;
import be.winnetrie.mod.simpleserverutilities.client.npc.SsuPlayerNpcModel;
import be.winnetrie.mod.simpleserverutilities.client.npc.SsuPlayerNpcRenderer;

@Mod(value = SimpleServerUtilities.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = SimpleServerUtilities.MODID, value = Dist.CLIENT)
public class SimpleServerUtilitiesClient {

    private static final String SSU_CATEGORY = "key.categories.simpleserverutilities";
    private static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.simpleserverutilities.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            SSU_CATEGORY
    );
    private static final KeyMapping OPEN_WORLD_MAP = new KeyMapping(
            "key.simpleserverutilities.open_world_map",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            SSU_CATEGORY
    );
    private static final KeyMapping WORLD_EDIT_COMPACT = new KeyMapping(
            "key.simpleserverutilities.world_edit_compact",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_W,
            SSU_CATEGORY
    );
    private static final KeyMapping ACTIVATE_TREECAPITATOR = new KeyMapping(
            "key.simpleserverutilities.activate_treecapitator",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            SSU_CATEGORY
    );
    private static final KeyMapping ACTIVATE_VEINMINER = new KeyMapping(
            "key.simpleserverutilities.activate_veinminer",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            SSU_CATEGORY
    );
    private static final KeyMapping TOGGLE_MINIGAME_HUD = new KeyMapping(
            "key.simpleserverutilities.toggle_minigame_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            SSU_CATEGORY
    );
    private static final KeyMapping SPECTATE_PREVIOUS = new KeyMapping(
            "key.simpleserverutilities.minigame_spectate_previous",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_COMMA,
            SSU_CATEGORY
    );
    private static final KeyMapping SPECTATE_NEXT = new KeyMapping(
            "key.simpleserverutilities.minigame_spectate_next",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PERIOD,
            SSU_CATEGORY
    );

    private static int utilityMiningTick;
    private static long minigameOverviewRequestId = 1L;
    private static boolean lastTreeHeld;
    private static boolean lastVeinHeld;

    public SimpleServerUtilitiesClient(ModContainer container) {
        registerClientPayloadHandlers();
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(SimpleServerUtilitiesClient::onClientLogout);
        NeoForge.EVENT_BUS.addListener(SimpleServerUtilitiesClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(SimpleServerUtilitiesClient::onRenderLevelStage);
        NeoForge.EVENT_BUS.register(HologramClientEvents.class);
        NeoForge.EVENT_BUS.register(IdentityClientEvents.class);
        NeoForge.EVENT_BUS.register(EntityInsightClientEvents.class);
        NeoForge.EVENT_BUS.register(SsuGuiScaleInputEvents.class);
    }

    @SubscribeEvent
    static void onRegisterNpcLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SsuPlayerNpcModel.LAYER, SsuPlayerNpcModel::createBodyLayer);
    }

    @SubscribeEvent
    static void onRegisterNpcEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModNpcEntities.PLAYER_NPC.get(), SsuPlayerNpcRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(OPEN_WORLD_MAP);
        event.register(WORLD_EDIT_COMPACT);
        event.register(ACTIVATE_TREECAPITATOR);
        event.register(ACTIVATE_VEINMINER);
        event.register(TOGGLE_MINIGAME_HUD);
        event.register(SPECTATE_PREVIOUS);
        event.register(SPECTATE_NEXT);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minimap"),
                MinimapClientState::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "block_information"),
                BlockInformationClientState::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "utility_mining_info"),
                UtilityMiningClientState::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_hud"),
                MinigameHudClientState::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_cast_bar"),
                MinigameCastBarClientState::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_kill_feed"),
                MinigameKillFeedClientState::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "region_snapshot_preview_controls"),
                RegionSnapshotPreviewClientState::render
        );
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMailMenus.MAIL_COMPOSE.get(), MailComposeScreen::new);
        event.register(ModAuctionMenus.AUCTION_SELL.get(), AuctionSellScreen::new);
        event.register(ModNpcMenus.NPC_LOADOUT.get(), NpcLoadoutScreen::new);
        event.register(be.winnetrie.mod.simpleserverutilities.kits.ModKitMenus.KIT_EDITOR.get(),
                be.winnetrie.mod.simpleserverutilities.client.gui.KitEditorScreen::new);
        event.register(be.winnetrie.mod.simpleserverutilities.moderation.ModModerationMenus.PLAYER_INVENTORY.get(),
                be.winnetrie.mod.simpleserverutilities.client.gui.PlayerInventoryAdminScreen::new);
    }

    private static void registerClientPayloadHandlers() {
        ClientPayloadRouter.register(ClaimMapDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof ClaimMapScreen screen) {
                        screen.acceptSnapshot(payload);
                    } else {
                        minecraft.setScreen(new ClaimMapScreen(payload, minecraft.screen));
                    }
                })
        );

        ClientPayloadRouter.register(BorderVisualizationPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> BorderVisualizationClientState.apply(payload))
        );

        ClientPayloadRouter.register(BlockInformationStatePayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> BlockInformationClientState.apply(payload))
        );

        ClientPayloadRouter.register(BlockInformationContentPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> BlockInformationClientState.applyContent(payload))
        );

        ClientPayloadRouter.register(SsuMenuSnapshotPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    MinimapClientState.applySettings(payload.uiSettings());
                    MapMarkerClientState.applySettings(payload.uiSettings());
                    AerialMapAtlas.setLiveUpdateRadiusChunks(payload.uiSettings().mapLiveUpdateRadiusChunks());
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof SsuDashboardScreen screen) {
                        screen.acceptSnapshot(payload);
                    } else {
                        minecraft.setScreen(new SsuDashboardScreen(payload));
                    }
                })
        );

        ClientPayloadRouter.register(SsuMenuPageDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof SsuDashboardScreen screen) {
                        screen.acceptPageData(payload);
                    } else if (minecraft.screen instanceof KnownPlayerPickerScreen picker) {
                        picker.accept(payload);
                    }
                })
        );

        ClientPayloadRouter.register(SsuMenuActionResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof SsuDashboardScreen screen) {
                        screen.acceptActionResult(payload);
                    } else if (minecraft.screen instanceof RegionPermissionScreen screen) {
                        screen.acceptActionResult(payload);
                    }
                })
        );

        ClientPayloadRouter.register(SsuPermissionEditorDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if ("region".equals(payload.mode())) {
                        if (minecraft.screen instanceof RegionPermissionScreen screen) {
                            screen.acceptData(payload);
                        } else {
                            minecraft.setScreen(new RegionPermissionScreen(payload, minecraft.screen));
                        }
                    } else if (minecraft.screen instanceof SsuDashboardScreen screen) {
                        screen.acceptPermissionEditorData(payload);
                    }
                })
        );

        ClientPayloadRouter.register(SsuDimensionManagerDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof ManagedDimensionScreen screen) {
                        screen.acceptData(payload);
                    } else {
                        minecraft.setScreen(new ManagedDimensionScreen(payload, minecraft.screen));
                    }
                })
        );

        ClientPayloadRouter.register(SsuPlayerProfileDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof SsuDashboardScreen screen) {
                        screen.acceptPlayerProfileData(payload);
                    }
                })
        );

        ClientPayloadRouter.register(SsuPropertySettingsDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof PropertySettingsScreen screen) {
                        screen.acceptData(payload);
                    } else {
                        minecraft.setScreen(new PropertySettingsScreen(payload, minecraft.screen));
                    }
                })
        );

        ClientPayloadRouter.register(SsuTrustedPlayersDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof TrustedPlayersScreen screen) {
                        screen.acceptData(payload);
                    } else if (minecraft.screen instanceof PropertySettingsScreen parent) {
                        minecraft.setScreen(new TrustedPlayersScreen(payload, parent));
                    }
                })
        );

        ClientPayloadRouter.register(AuctionHouseDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof AuctionHouseScreen screen) {
                        screen.acceptData(payload);
                    } else {
                        minecraft.setScreen(new AuctionHouseScreen(payload));
                    }
                })
        );

        ClientPayloadRouter.register(AuctionHouseActionResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof AuctionHouseScreen screen) {
                        screen.acceptResult(payload);
                    } else if (minecraft.screen instanceof AuctionSellScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        ClientPayloadRouter.register(MailDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof MailScreen screen) {
                        screen.acceptData(payload);
                    } else {
                        minecraft.setScreen(new MailScreen(payload));
                    }
                })
        );

        ClientPayloadRouter.register(MailComposeResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof MailComposeScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        ClientPayloadRouter.register(MailRecipientSuggestionsPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof MailComposeScreen screen) {
                        screen.acceptSuggestions(payload);
                    }
                })
        );

        ClientPayloadRouter.register(MinimapDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MinimapClientState.apply(payload))
        );

        ClientPayloadRouter.register(MapMarkerSyncPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MapMarkerClientState.apply(payload))
        );

        ClientPayloadRouter.register(MapMarkerActionResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    MapMarkerClientState.applyResult(payload);
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.MapMarkerEditorScreen screen) {
                        screen.acceptResult(payload);
                    } else if (minecraft.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.MapMarkerManagementScreen screen) {
                        screen.acceptResult(payload);
                    } else if (minecraft.screen instanceof WorldMapScreen screen) {
                        screen.acceptMarkerResult(payload);
                    }
                })
        );

        ClientPayloadRouter.register(WorldMapDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof WorldMapScreen screen) {
                        screen.acceptSnapshot(payload);
                    } else {
                        minecraft.setScreen(new WorldMapScreen(payload));
                    }
                })
        );

        ClientPayloadRouter.register(UtilityMiningPreviewPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> UtilityMiningClientState.apply(payload))
        );

        ClientPayloadRouter.register(HologramSyncPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> HologramClientState.apply(payload))
        );

        ClientPayloadRouter.register(HologramEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new HologramEditorScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(HologramEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof HologramEditorScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        ClientPayloadRouter.register(EntityInsightPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> EntityInsightClientState.apply(payload))
        );

        ClientPayloadRouter.register(NpcLabelSyncPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> NpcLabelClientState.apply(payload))
        );

        ClientPayloadRouter.register(NpcTextureSyncPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> NpcCustomTextureClientState.apply(payload))
        );

        ClientPayloadRouter.register(NpcEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new NpcEditorScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(NpcEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcEditorScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        ClientPayloadRouter.register(NpcQuestWorkflowOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcQuestWorkflowScreen screen) screen.accept(payload);
                    else minecraft.setScreen(new NpcQuestWorkflowScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(NpcLoadoutResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcLoadoutScreen screen) screen.acceptResult(payload);
                })
        );

        ClientPayloadRouter.register(NpcAdminListPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcAdminScreen screen) {
                        // Duplicate/out-of-order list responses update the one manager that is
                        // already visible; never create a second manager layer.
                        screen.accept(payload);
                    } else {
                        // This is a top-level world screen, not a child overlay. In 26.2 the active
                        // screen lives on Gui and Gui#setScreen clears NeoForge background layers.
                        // Using the replacement path here prevents a stale manager from remaining
                        // underneath the newly opened manager when the NPC tool is used in the air.
                        minecraft.setScreen(new NpcAdminScreen(payload));
                    }
                })
        );

        ClientPayloadRouter.register(NpcSpawnProfileEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new NpcSpawnProfileEditorScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(NpcSpawnProfileEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcSpawnProfileEditorScreen screen) screen.acceptResult(payload);
                })
        );


        ClientPayloadRouter.register(NpcFunctionMenuPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new NpcFunctionMenuScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(NpcShopDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcShopScreen screen) {
                        screen.acceptData(payload);
                    } else {
                        minecraft.setScreen(new NpcShopScreen(payload));
                    }
                })
        );

        ClientPayloadRouter.register(NpcShopAdminDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcShopAdminScreen screen) {
                        screen.accept(payload);
                    } else if (minecraft.screen instanceof NpcShopEditorScreen screen) {
                        screen.acceptManagerData(payload);
                    } else {
                        minecraft.setScreen(new NpcShopAdminScreen(payload, minecraft.screen));
                    }
                })
        );

        ClientPayloadRouter.register(NpcShopEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcShopEditorScreen screen) {
                        screen.acceptOpen(payload);
                    } else {
                        minecraft.setScreen(new NpcShopEditorScreen(payload, minecraft.screen));
                    }
                })
        );

        ClientPayloadRouter.register(NpcShopEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcShopEditorScreen screen) {
                        screen.acceptResult(payload);
                    } else if (minecraft.screen instanceof NpcShopAdminScreen screen) {
                        screen.acceptEditorResult(payload);
                    }
                })
        );

        ClientPayloadRouter.register(NpcAbilityLibraryDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcAbilityLibraryScreen screen) screen.accept(payload);
                    else minecraft.setScreen(new NpcAbilityLibraryScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(NpcAbilityEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new NpcAbilityWorkshopScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(NpcAbilityEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcAbilityWorkshopScreen screen) screen.acceptResult(payload);
                    else if (minecraft.screen instanceof NpcAbilityLibraryScreen screen) screen.acceptEditorResult(payload);
                })
        );

        ClientPayloadRouter.register(NpcItemPriceCatalogDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcItemPriceCatalogScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreen(new NpcItemPriceCatalogScreen(payload, minecraft.screen));
                    }
                })
        );

        ClientPayloadRouter.register(NpcDialogueViewPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcDialogueScreen screen) {
                        screen.accept(payload);
                    } else if (!payload.closed()) {
                        minecraft.setScreen(new NpcDialogueScreen(payload, minecraft.screen));
                    }
                })
        );

        ClientPayloadRouter.register(NpcDialogueEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new NpcDialogueEditorScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(NpcDialogueEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof NpcDialogueEditorScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        ClientPayloadRouter.register(StatisticEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new StatisticEditorScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(StatisticEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof StatisticEditorScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        ClientPayloadRouter.register(QuestBookDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof QuestBookScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreen(new QuestBookScreen(payload, minecraft.screen));
                    }
                })
        );

        ClientPayloadRouter.register(QuestEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new QuestEditorScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(QuestEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof QuestEditorScreen screen) screen.acceptResult(payload);
                })
        );

        ClientPayloadRouter.register(AchievementMenuDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof AchievementMenuScreen screen) screen.accept(payload);
                    else minecraft.setScreen(new AchievementMenuScreen(payload, minecraft.screen));
                })
        );
        ClientPayloadRouter.register(AchievementEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new AchievementEditorScreen(payload, minecraft.screen));
                })
        );
        ClientPayloadRouter.register(AchievementEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof AchievementEditorScreen screen) screen.acceptResult(payload);
                })
        );


        ClientPayloadRouter.register(MinigameLobbyDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (payload.adminView()) {
                        if (minecraft.screen instanceof MinigameAdminScreen screen) {
                            screen.accept(payload);
                        } else {
                            minecraft.setScreen(new MinigameAdminScreen(payload, minecraft.screen));
                        }
                    } else if (minecraft.screen instanceof MinigameLobbyScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreen(new MinigameLobbyScreen(payload, minecraft.screen));
                    }
                })
        );

        ClientPayloadRouter.register(MinigameEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof MinigameEditorScreen screen) {
                        screen.acceptOpen(payload);
                    } else {
                        minecraft.setScreen(MinigameEditorScreen.create(payload, minecraft.screen));
                    }
                })
        );

        ClientPayloadRouter.register(MinigameEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof MinigameEditorScreen screen) screen.accept(payload);
                })
        );

        ClientPayloadRouter.register(MinigameHudPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MinigameHudClientState.apply(payload))
        );

        ClientPayloadRouter.register(MinigameKothVisualPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> KingOfTheHillVisualClientState.apply(payload))
        );

        ClientPayloadRouter.register(MinigameCastBarPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MinigameCastBarClientState.apply(payload))
        );

        ClientPayloadRouter.register(MinigameDominationVisualPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> DominationClientState.apply(payload))
        );

        ClientPayloadRouter.register(MinigameCtfVisualPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> CaptureTheFlagClientState.apply(payload))
        );

        ClientPayloadRouter.register(MinigameSelectionCreateResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof MinigameSelectionCreateScreen screen) screen.accept(payload);
                    else if (minecraft.screen instanceof MinigameSetupCreateScreen screen) screen.accept(payload);
                })
        );

        ClientPayloadRouter.register(MinigameSetupVisualPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MinigameSetupVisualClientState.apply(payload))
        );

        ClientPayloadRouter.register(MinigameSetupToolOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof MinigameSetupToolScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreen(new MinigameSetupToolScreen(payload));
                    }
                })
        );

        ClientPayloadRouter.register(MinigameResultsPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (!payload.visible()) {
                        if (minecraft.screen instanceof MinigameResultsScreen) minecraft.setScreen(null);
                    } else if (minecraft.screen instanceof MinigameResultsScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreen(new MinigameResultsScreen(payload));
                    }
                })
        );

        ClientPayloadRouter.register(MinigameProfilePayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof MinigameProfileScreen screen) screen.accept(payload);
                    else minecraft.setScreen(new MinigameProfileScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(MinigameMatchOverviewPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minigameOverviewRequestId = Math.max(minigameOverviewRequestId, payload.requestId() + 1L);
                    if (!payload.active()) {
                        if (minecraft.screen instanceof MinigameMatchOverviewScreen) {
                            minecraft.setScreen(null);
                        }
                        if (!payload.notice().isBlank() && minecraft.player != null) {
                            minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(payload.notice()));
                        }
                        if (payload.openDashboardFallback() && minecraft.player != null) {
                            minecraft.player.connection.sendCommand("ssu menu");
                        }
                    } else if (minecraft.screen instanceof MinigameMatchOverviewScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreen(new MinigameMatchOverviewScreen(payload));
                    }
                })
        );

        ClientPayloadRouter.register(MinigameKillFeedPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MinigameKillFeedClientState.add(payload))
        );

        ClientPayloadRouter.register(MinigameValidationPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new MinigameValidationScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(MinigameDiagnosticsPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof MinigameDiagnosticsScreen screen) screen.accept(payload);
                    else minecraft.setScreen(new MinigameDiagnosticsScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(DungeonLobbyDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof DungeonLobbyScreen screen) screen.accept(payload);
                    else minecraft.setScreen(new DungeonLobbyScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(DungeonEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new DungeonEditorScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(DungeonEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof DungeonEditorScreen screen) screen.accept(payload);
                })
        );

        ClientPayloadRouter.register(RegionSetupOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof RegionSelectionEditScreen worldEdit
                            && "SELECT".equalsIgnoreCase(payload.mode())) {
                        worldEdit.acceptSetupContext(payload);
                    } else if (minecraft.screen instanceof RegionSetupScreen screen) screen.accept(payload);
                    else minecraft.setScreen(new RegionSetupScreen(payload));
                })
        );

        ClientPayloadRouter.register(RegionSnapshotPreviewPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    boolean wasActive = RegionSnapshotPreviewClientState.active();
                    RegionSnapshotPreviewClientState.accept(payload);
                    if (!payload.active()) {
                        if (minecraft.screen instanceof RegionSnapshotPreviewScreen) minecraft.setScreen(null);
                        return;
                    }
                    if (!wasActive) {
                        RegionSnapshotPreviewClientState.exitFreeMode();
                        minecraft.setScreen(new RegionSnapshotPreviewScreen());
                    } else if (minecraft.screen instanceof RegionSnapshotPreviewScreen screen) {
                        screen.accept(payload);
                    }
                })
        );

        ClientPayloadRouter.register(RegionEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> Minecraft.getInstance().setScreen(new RegionEditorScreen(payload)))
        );

        ClientPayloadRouter.register(RegionEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof RegionEditorScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );


        ClientPayloadRouter.register(RegionSelectionToolOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new RegionSelectionEditScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(RegionSelectionActionResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof RegionSelectionEditScreen screen) screen.acceptResult(payload);
                    else if (minecraft.screen instanceof WorldEditCompactOverlayScreen screen) screen.acceptResult(payload);
                    else if (minecraft.screen instanceof RegionSelectionToolScreen screen) screen.acceptResult(payload);
                    else if (minecraft.screen instanceof RegionSetupScreen screen) screen.acceptSelectionResult(payload);
                })
        );


        ClientPayloadRouter.register(TitleManagerDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof TitleManagerScreen screen) screen.accept(payload);
                    else minecraft.setScreen(new TitleManagerScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(RankDisplayDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof RankDisplayEditorScreen screen) screen.accept(payload);
                    else minecraft.setScreen(new RankDisplayEditorScreen(payload, minecraft.screen));
                })
        );

        ClientPayloadRouter.register(PlayerIdentitySyncPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> PlayerIdentityClientState.apply(payload))
        );

        ClientPayloadRouter.register(DamageIndicatorPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> DamageIndicatorClientState.add(payload))
        );

        ClientPayloadRouter.register(RegionSelectionClientTemplatePayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    try {
                        RegionSelectionClientStorage.save(payload.name(), payload.data());
                        if (minecraft.player != null) minecraft.player.sendSystemMessage(
                                net.minecraft.network.chat.Component.literal("Saved client region template '" + payload.name() + "'."));
                    } catch (java.io.IOException | IllegalArgumentException exception) {
                        if (minecraft.player != null) minecraft.player.sendSystemMessage(
                                net.minecraft.network.chat.Component.literal("Client region template could not be saved: " + exception.getMessage()));
                    }
                })
        );

        ClientPayloadRouter.register(be.winnetrie.mod.simpleserverutilities.network.OnboardingStatePayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.OnboardingScreen screen) screen.accept(payload);
                    else if (!"complete".equals(payload.stage())) m.setScreen(new be.winnetrie.mod.simpleserverutilities.client.gui.OnboardingScreen(payload));
                }));
        ClientPayloadRouter.register(be.winnetrie.mod.simpleserverutilities.network.OnboardingAdminDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.OnboardingAdminScreen screen) screen.accept(payload);
                    else m.setScreen(new be.winnetrie.mod.simpleserverutilities.client.gui.OnboardingAdminScreen(payload,m.screen));
                }));
        ClientPayloadRouter.register(be.winnetrie.mod.simpleserverutilities.network.PlayerManagementDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.PlayerManagementScreen screen) screen.accept(payload);
                    else if (m.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.JailPunishmentScreen screen) screen.accept(payload);
                    else m.setScreen(new be.winnetrie.mod.simpleserverutilities.client.gui.PlayerManagementScreen(payload,m.screen));
                }));
        ClientPayloadRouter.register(be.winnetrie.mod.simpleserverutilities.network.JailDashboardPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.JailDashboardScreen screen) screen.accept(payload);
                    else m.setScreen(new be.winnetrie.mod.simpleserverutilities.client.gui.JailDashboardScreen(payload));
                }));
        ClientPayloadRouter.register(be.winnetrie.mod.simpleserverutilities.network.KitDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.KitScreen screen) screen.accept(payload);
                    else m.setScreen(new be.winnetrie.mod.simpleserverutilities.client.gui.KitScreen(payload,m.screen));
                }));
        ClientPayloadRouter.register(be.winnetrie.mod.simpleserverutilities.network.KitContentsResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {Minecraft m=Minecraft.getInstance();if(m.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.KitEditorScreen screen)screen.accept(payload);}));
        ClientPayloadRouter.register(be.winnetrie.mod.simpleserverutilities.network.JailAdminDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.JailAdministrationScreen screen) screen.accept(payload);
                    else if (m.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.JailPrisonerOverviewScreen screen) screen.accept(payload);
                    else m.setScreen(new be.winnetrie.mod.simpleserverutilities.client.gui.JailAdministrationScreen(payload,m.screen));
                }));
        ClientPayloadRouter.register(be.winnetrie.mod.simpleserverutilities.network.MineDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.MineScreen screen) screen.accept(payload);
                    else m.setScreen(new be.winnetrie.mod.simpleserverutilities.client.gui.MineScreen(payload,m.screen));
                }));
        ClientPayloadRouter.register(be.winnetrie.mod.simpleserverutilities.network.ServerOperationsDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.screen instanceof be.winnetrie.mod.simpleserverutilities.client.gui.ServerOperationsScreen screen) screen.accept(payload);
                    else m.setScreen(new be.winnetrie.mod.simpleserverutilities.client.gui.ServerOperationsScreen(payload,m.screen));
                }));
    }

    @SubscribeEvent
    static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> HologramImageCache.clear());
    }

    private static java.util.List<DebugRenderer.SimpleDebugRenderer> worldRenderers;

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (worldRenderers == null) {
            worldRenderers = java.util.List.of(
                    new ClaimRegionBorderRenderer(minecraft),
                    new UtilityMiningOutlineRenderer(minecraft),
                    new HologramRenderer(minecraft),
                    new NpcLabelRenderer(minecraft),
                    new MapMarkerRenderer(minecraft),
                    new DominationRenderer(minecraft),
                    new MinigameSetupVisualRenderer(minecraft),
                    new KingOfTheHillVisualRenderer(minecraft),
                    new PlayerTitleRenderer(minecraft),
                    new DamageIndicatorRenderer(minecraft),
                    new RegionSnapshotPreviewRenderer(minecraft)
            );
        }
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Vec3 camera = event.getCamera().getPosition();
        for (DebugRenderer.SimpleDebugRenderer renderer : worldRenderers) {
            renderer.render(event.getPoseStack(), buffers, camera.x, camera.y, camera.z);
        }
        buffers.endBatch();
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        AerialMapAtlas.tick();
        MinimapClientState.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (RegionSnapshotPreviewClientState.tick(minecraft)) {
            MinigameKillFeedClientState.tick();
            DamageIndicatorClientState.tick();
            return;
        }
        while (WORLD_EDIT_COMPACT.consumeClick()) {
            if (minecraft.player != null && minecraft.level != null
                    && isHoldingWorldEditTool(minecraft)
                    && (minecraft.screen == null || minecraft.screen instanceof WorldEditCompactOverlayScreen)) {
                if (minecraft.screen instanceof WorldEditCompactOverlayScreen) minecraft.setScreen(null);
                else minecraft.setScreen(new WorldEditCompactOverlayScreen());
            }
        }
        tickUtilityMining(minecraft);
        while (OPEN_MENU.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(new MinigameMatchOverviewRequestPayload(
                        "open", minigameOverviewRequestId++));
            }
        }
        while (OPEN_WORLD_MAP.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(new WorldMapRequestPayload(
                        minecraft.player.chunkPosition().x,
                        minecraft.player.chunkPosition().z,
                        8
                ));
            }
        }
        while (TOGGLE_MINIGAME_HUD.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null
                    && MinigameHudClientState.isServerVisible()) {
                String mode = MinigameHudClientState.cycleDisplayMode();
                minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Minigame scoreboard: " + mode + "."));
            }
        }
        MinigameKillFeedClientState.tick();
        DamageIndicatorClientState.tick();
        while (SPECTATE_PREVIOUS.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(new MinigameSpectatorActionPayload("previous"));
            }
        }
        while (SPECTATE_NEXT.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(new MinigameSpectatorActionPayload("next"));
            }
        }
    }

    private static boolean isHoldingWorldEditTool(Minecraft minecraft) {
        if (minecraft.player == null) return false;
        var stack = minecraft.player.getMainHandItem();
        return !stack.isEmpty() && stack.is(Items.GOLDEN_AXE)
                && "SSU World Edit Tool".equals(stack.getHoverName().getString());
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BorderVisualizationClientState.clear();
        BlockInformationClientState.clear();
        MinimapClientState.clear();
        AerialMapAtlas.clear();
        UtilityMiningClientState.clear();
        HologramClientState.clear();
        NpcLabelClientState.clear();
        NpcCustomTextureClientState.clear();
        MapMarkerClientState.clear();
        MinigameHudClientState.clear();
        KingOfTheHillVisualClientState.clear();
        CaptureTheFlagClientState.clear();
        DominationClientState.clear();
        MinigameCastBarClientState.clear();
        MinigameKillFeedClientState.clear();
        PlayerIdentityClientState.clear();
        DamageIndicatorClientState.clear();
        EntityInsightClientState.clear();
        RegionSnapshotPreviewClientState.clear();
        utilityMiningTick = 0;
        lastTreeHeld = false;
        lastVeinHeld = false;
        minigameOverviewRequestId = 1L;
    }

    private static void tickUtilityMining(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            UtilityMiningClientState.clear();
            return;
        }

        utilityMiningTick++;
        boolean treeHeld = ACTIVATE_TREECAPITATOR.isDown();
        boolean veinHeld = ACTIVATE_VEINMINER.isDown();
        if (treeHeld != lastTreeHeld || veinHeld != lastVeinHeld
                || ((treeHeld || veinHeld) && utilityMiningTick % 20 == 0)) {
            PacketDistributor.sendToServer(new UtilityMiningActivationPayload(treeHeld, veinHeld));
            lastTreeHeld = treeHeld;
            lastVeinHeld = veinHeld;
        }

        if (minecraft.screen == null && utilityMiningTick % 4 == 0
                && minecraft.hitResult instanceof BlockHitResult blockHit) {
            PacketDistributor.sendToServer(UtilityMiningPreviewRequestPayload.at(blockHit.getBlockPos()));
        }
    }
}
