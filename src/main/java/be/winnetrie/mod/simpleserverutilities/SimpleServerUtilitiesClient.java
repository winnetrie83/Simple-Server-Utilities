package be.winnetrie.mod.simpleserverutilities;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.common.reflect.TypeToken;
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
import be.winnetrie.mod.simpleserverutilities.client.npc.NpcTextureRenderState;
import be.winnetrie.mod.simpleserverutilities.client.npc.NpcLabelRenderer;
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
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
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

    private static final KeyMapping.Category SSU_CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "general")
    );
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
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(SimpleServerUtilitiesClient::onClientLogout);
        NeoForge.EVENT_BUS.addListener(SimpleServerUtilitiesClient::onClientTick);
        NeoForge.EVENT_BUS.register(HologramClientEvents.class);
        NeoForge.EVENT_BUS.register(IdentityClientEvents.class);
        NeoForge.EVENT_BUS.register(EntityInsightClientEvents.class);
        NeoForge.EVENT_BUS.addListener(RegionSnapshotPreviewRenderer::onSubmitCustomGeometry);
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
    static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {},
                (entity, state) -> state.setRenderData(
                        NpcTextureRenderState.CUSTOM_TEXTURE,
                        NpcCustomTextureClientState.textureForEntity(entity.getId())
                )
        );
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(SSU_CATEGORY);
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
                Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minimap"),
                MinimapClientState::render
        );
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "block_information"),
                BlockInformationClientState::render
        );
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "utility_mining_info"),
                UtilityMiningClientState::render
        );
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_hud"),
                MinigameHudClientState::render
        );
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_cast_bar"),
                MinigameCastBarClientState::render
        );
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_kill_feed"),
                MinigameKillFeedClientState::render
        );
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "region_snapshot_preview_controls"),
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

    @SubscribeEvent
    static void onRegisterClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ClaimMapDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof ClaimMapScreen screen) {
                        screen.acceptSnapshot(payload);
                    } else {
                        minecraft.setScreenAndShow(new ClaimMapScreen(payload, minecraft.gui.screen()));
                    }
                })
        );

        event.register(BorderVisualizationPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> BorderVisualizationClientState.apply(payload))
        );

        event.register(BlockInformationStatePayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> BlockInformationClientState.apply(payload))
        );

        event.register(BlockInformationContentPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> BlockInformationClientState.applyContent(payload))
        );

        event.register(SsuMenuSnapshotPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    MinimapClientState.applySettings(payload.uiSettings());
                    MapMarkerClientState.applySettings(payload.uiSettings());
                    AerialMapAtlas.setLiveUpdateRadiusChunks(payload.uiSettings().mapLiveUpdateRadiusChunks());
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof SsuDashboardScreen screen) {
                        screen.acceptSnapshot(payload);
                    } else {
                        minecraft.setScreenAndShow(new SsuDashboardScreen(payload));
                    }
                })
        );

        event.register(SsuMenuPageDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof SsuDashboardScreen screen) {
                        screen.acceptPageData(payload);
                    } else if (minecraft.gui.screen() instanceof KnownPlayerPickerScreen picker) {
                        picker.accept(payload);
                    }
                })
        );

        event.register(SsuMenuActionResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof SsuDashboardScreen screen) {
                        screen.acceptActionResult(payload);
                    } else if (minecraft.gui.screen() instanceof RegionPermissionScreen screen) {
                        screen.acceptActionResult(payload);
                    }
                })
        );

        event.register(SsuPermissionEditorDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if ("region".equals(payload.mode())) {
                        if (minecraft.gui.screen() instanceof RegionPermissionScreen screen) {
                            screen.acceptData(payload);
                        } else {
                            minecraft.setScreenAndShow(new RegionPermissionScreen(payload, minecraft.gui.screen()));
                        }
                    } else if (minecraft.gui.screen() instanceof SsuDashboardScreen screen) {
                        screen.acceptPermissionEditorData(payload);
                    }
                })
        );

        event.register(SsuDimensionManagerDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof ManagedDimensionScreen screen) {
                        screen.acceptData(payload);
                    } else {
                        minecraft.setScreenAndShow(new ManagedDimensionScreen(payload, minecraft.gui.screen()));
                    }
                })
        );

        event.register(SsuPlayerProfileDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof SsuDashboardScreen screen) {
                        screen.acceptPlayerProfileData(payload);
                    }
                })
        );

        event.register(SsuPropertySettingsDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof PropertySettingsScreen screen) {
                        screen.acceptData(payload);
                    } else {
                        minecraft.setScreenAndShow(new PropertySettingsScreen(payload, minecraft.gui.screen()));
                    }
                })
        );

        event.register(SsuTrustedPlayersDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof TrustedPlayersScreen screen) {
                        screen.acceptData(payload);
                    } else if (minecraft.gui.screen() instanceof PropertySettingsScreen parent) {
                        minecraft.setScreenAndShow(new TrustedPlayersScreen(payload, parent));
                    }
                })
        );

        event.register(AuctionHouseDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof AuctionHouseScreen screen) {
                        screen.acceptData(payload);
                    } else {
                        minecraft.setScreenAndShow(new AuctionHouseScreen(payload));
                    }
                })
        );

        event.register(AuctionHouseActionResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof AuctionHouseScreen screen) {
                        screen.acceptResult(payload);
                    } else if (minecraft.gui.screen() instanceof AuctionSellScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        event.register(MailDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof MailScreen screen) {
                        screen.acceptData(payload);
                    } else {
                        minecraft.setScreenAndShow(new MailScreen(payload));
                    }
                })
        );

        event.register(MailComposeResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof MailComposeScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        event.register(MailRecipientSuggestionsPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof MailComposeScreen screen) {
                        screen.acceptSuggestions(payload);
                    }
                })
        );

        event.register(MinimapDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MinimapClientState.apply(payload))
        );

        event.register(MapMarkerSyncPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MapMarkerClientState.apply(payload))
        );

        event.register(MapMarkerActionResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    MapMarkerClientState.applyResult(payload);
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.MapMarkerEditorScreen screen) {
                        screen.acceptResult(payload);
                    } else if (minecraft.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.MapMarkerManagementScreen screen) {
                        screen.acceptResult(payload);
                    } else if (minecraft.gui.screen() instanceof WorldMapScreen screen) {
                        screen.acceptMarkerResult(payload);
                    }
                })
        );

        event.register(WorldMapDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof WorldMapScreen screen) {
                        screen.acceptSnapshot(payload);
                    } else {
                        minecraft.setScreenAndShow(new WorldMapScreen(payload));
                    }
                })
        );

        event.register(UtilityMiningPreviewPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> UtilityMiningClientState.apply(payload))
        );

        event.register(HologramSyncPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> HologramClientState.apply(payload))
        );

        event.register(HologramEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new HologramEditorScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(HologramEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof HologramEditorScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        event.register(EntityInsightPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> EntityInsightClientState.apply(payload))
        );

        event.register(NpcLabelSyncPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> NpcLabelClientState.apply(payload))
        );

        event.register(NpcTextureSyncPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> NpcCustomTextureClientState.apply(payload))
        );

        event.register(NpcEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new NpcEditorScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(NpcEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcEditorScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        event.register(NpcQuestWorkflowOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcQuestWorkflowScreen screen) screen.accept(payload);
                    else minecraft.setScreenAndShow(new NpcQuestWorkflowScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(NpcLoadoutResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcLoadoutScreen screen) screen.acceptResult(payload);
                })
        );

        event.register(NpcAdminListPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcAdminScreen screen) {
                        // Duplicate/out-of-order list responses update the one manager that is
                        // already visible; never create a second manager layer.
                        screen.accept(payload);
                    } else {
                        // This is a top-level world screen, not a child overlay. In 26.2 the active
                        // screen lives on Gui and Gui#setScreen clears NeoForge background layers.
                        // Using the replacement path here prevents a stale manager from remaining
                        // underneath the newly opened manager when the NPC tool is used in the air.
                        minecraft.gui.setScreen(new NpcAdminScreen(payload));
                    }
                })
        );

        event.register(NpcSpawnProfileEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new NpcSpawnProfileEditorScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(NpcSpawnProfileEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcSpawnProfileEditorScreen screen) screen.acceptResult(payload);
                })
        );


        event.register(NpcFunctionMenuPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new NpcFunctionMenuScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(NpcShopDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcShopScreen screen) {
                        screen.acceptData(payload);
                    } else {
                        minecraft.setScreenAndShow(new NpcShopScreen(payload));
                    }
                })
        );

        event.register(NpcShopAdminDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcShopAdminScreen screen) {
                        screen.accept(payload);
                    } else if (minecraft.gui.screen() instanceof NpcShopEditorScreen screen) {
                        screen.acceptManagerData(payload);
                    } else {
                        minecraft.setScreenAndShow(new NpcShopAdminScreen(payload, minecraft.gui.screen()));
                    }
                })
        );

        event.register(NpcShopEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcShopEditorScreen screen) {
                        screen.acceptOpen(payload);
                    } else {
                        minecraft.setScreenAndShow(new NpcShopEditorScreen(payload, minecraft.gui.screen()));
                    }
                })
        );

        event.register(NpcShopEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcShopEditorScreen screen) {
                        screen.acceptResult(payload);
                    } else if (minecraft.gui.screen() instanceof NpcShopAdminScreen screen) {
                        screen.acceptEditorResult(payload);
                    }
                })
        );

        event.register(NpcAbilityLibraryDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcAbilityLibraryScreen screen) screen.accept(payload);
                    else minecraft.setScreenAndShow(new NpcAbilityLibraryScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(NpcAbilityEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new NpcAbilityWorkshopScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(NpcAbilityEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcAbilityWorkshopScreen screen) screen.acceptResult(payload);
                    else if (minecraft.gui.screen() instanceof NpcAbilityLibraryScreen screen) screen.acceptEditorResult(payload);
                })
        );

        event.register(NpcItemPriceCatalogDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcItemPriceCatalogScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreenAndShow(new NpcItemPriceCatalogScreen(payload, minecraft.gui.screen()));
                    }
                })
        );

        event.register(NpcDialogueViewPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcDialogueScreen screen) {
                        screen.accept(payload);
                    } else if (!payload.closed()) {
                        minecraft.setScreenAndShow(new NpcDialogueScreen(payload, minecraft.gui.screen()));
                    }
                })
        );

        event.register(NpcDialogueEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new NpcDialogueEditorScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(NpcDialogueEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcDialogueEditorScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        event.register(StatisticEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new StatisticEditorScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(StatisticEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof StatisticEditorScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );

        event.register(QuestBookDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof QuestBookScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreenAndShow(new QuestBookScreen(payload, minecraft.gui.screen()));
                    }
                })
        );

        event.register(QuestEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new QuestEditorScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(QuestEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof QuestEditorScreen screen) screen.acceptResult(payload);
                })
        );

        event.register(AchievementMenuDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof AchievementMenuScreen screen) screen.accept(payload);
                    else minecraft.setScreenAndShow(new AchievementMenuScreen(payload, minecraft.gui.screen()));
                })
        );
        event.register(AchievementEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new AchievementEditorScreen(payload, minecraft.gui.screen()));
                })
        );
        event.register(AchievementEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof AchievementEditorScreen screen) screen.acceptResult(payload);
                })
        );


        event.register(MinigameLobbyDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (payload.adminView()) {
                        if (minecraft.gui.screen() instanceof MinigameAdminScreen screen) {
                            screen.accept(payload);
                        } else {
                            minecraft.setScreenAndShow(new MinigameAdminScreen(payload, minecraft.gui.screen()));
                        }
                    } else if (minecraft.gui.screen() instanceof MinigameLobbyScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreenAndShow(new MinigameLobbyScreen(payload, minecraft.gui.screen()));
                    }
                })
        );

        event.register(MinigameEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof MinigameEditorScreen screen) {
                        screen.acceptOpen(payload);
                    } else {
                        minecraft.setScreenAndShow(MinigameEditorScreen.create(payload, minecraft.gui.screen()));
                    }
                })
        );

        event.register(MinigameEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof MinigameEditorScreen screen) screen.accept(payload);
                })
        );

        event.register(MinigameHudPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MinigameHudClientState.apply(payload))
        );

        event.register(MinigameKothVisualPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> KingOfTheHillVisualClientState.apply(payload))
        );

        event.register(MinigameCastBarPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MinigameCastBarClientState.apply(payload))
        );

        event.register(MinigameDominationVisualPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> DominationClientState.apply(payload))
        );

        event.register(MinigameCtfVisualPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> CaptureTheFlagClientState.apply(payload))
        );

        event.register(MinigameSelectionCreateResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof MinigameSelectionCreateScreen screen) screen.accept(payload);
                    else if (minecraft.gui.screen() instanceof MinigameSetupCreateScreen screen) screen.accept(payload);
                })
        );

        event.register(MinigameSetupVisualPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MinigameSetupVisualClientState.apply(payload))
        );

        event.register(MinigameSetupToolOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof MinigameSetupToolScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreenAndShow(new MinigameSetupToolScreen(payload));
                    }
                })
        );

        event.register(MinigameResultsPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (!payload.visible()) {
                        if (minecraft.gui.screen() instanceof MinigameResultsScreen) minecraft.setScreenAndShow(null);
                    } else if (minecraft.gui.screen() instanceof MinigameResultsScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreenAndShow(new MinigameResultsScreen(payload));
                    }
                })
        );

        event.register(MinigameProfilePayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof MinigameProfileScreen screen) screen.accept(payload);
                    else minecraft.setScreenAndShow(new MinigameProfileScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(MinigameMatchOverviewPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minigameOverviewRequestId = Math.max(minigameOverviewRequestId, payload.requestId() + 1L);
                    if (!payload.active()) {
                        if (minecraft.gui.screen() instanceof MinigameMatchOverviewScreen) {
                            minecraft.setScreenAndShow(null);
                        }
                        if (!payload.notice().isBlank() && minecraft.player != null) {
                            minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(payload.notice()));
                        }
                        if (payload.openDashboardFallback() && minecraft.player != null) {
                            minecraft.player.connection.sendUnattendedCommand("ssu menu", null);
                        }
                    } else if (minecraft.gui.screen() instanceof MinigameMatchOverviewScreen screen) {
                        screen.accept(payload);
                    } else {
                        minecraft.setScreenAndShow(new MinigameMatchOverviewScreen(payload));
                    }
                })
        );

        event.register(MinigameKillFeedPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MinigameKillFeedClientState.add(payload))
        );

        event.register(MinigameValidationPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new MinigameValidationScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(MinigameDiagnosticsPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof MinigameDiagnosticsScreen screen) screen.accept(payload);
                    else minecraft.setScreenAndShow(new MinigameDiagnosticsScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(DungeonLobbyDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof DungeonLobbyScreen screen) screen.accept(payload);
                    else minecraft.setScreenAndShow(new DungeonLobbyScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(DungeonEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new DungeonEditorScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(DungeonEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof DungeonEditorScreen screen) screen.accept(payload);
                })
        );

        event.register(RegionSetupOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof RegionSelectionEditScreen worldEdit
                            && "SELECT".equalsIgnoreCase(payload.mode())) {
                        worldEdit.acceptSetupContext(payload);
                    } else if (minecraft.gui.screen() instanceof RegionSetupScreen screen) screen.accept(payload);
                    else minecraft.setScreenAndShow(new RegionSetupScreen(payload));
                })
        );

        event.register(RegionSnapshotPreviewPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    boolean wasActive = RegionSnapshotPreviewClientState.active();
                    RegionSnapshotPreviewClientState.accept(payload);
                    if (!payload.active()) {
                        if (minecraft.gui.screen() instanceof RegionSnapshotPreviewScreen) minecraft.setScreenAndShow(null);
                        return;
                    }
                    if (!wasActive) {
                        RegionSnapshotPreviewClientState.exitFreeMode();
                        minecraft.setScreenAndShow(new RegionSnapshotPreviewScreen());
                    } else if (minecraft.gui.screen() instanceof RegionSnapshotPreviewScreen screen) {
                        screen.accept(payload);
                    }
                })
        );

        event.register(RegionEditorOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> Minecraft.getInstance().setScreenAndShow(new RegionEditorScreen(payload)))
        );

        event.register(RegionEditorResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof RegionEditorScreen screen) {
                        screen.acceptResult(payload);
                    }
                })
        );


        event.register(RegionSelectionToolOpenPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreenAndShow(new RegionSelectionEditScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(RegionSelectionActionResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof RegionSelectionEditScreen screen) screen.acceptResult(payload);
                    else if (minecraft.gui.screen() instanceof WorldEditCompactOverlayScreen screen) screen.acceptResult(payload);
                    else if (minecraft.gui.screen() instanceof RegionSelectionToolScreen screen) screen.acceptResult(payload);
                    else if (minecraft.gui.screen() instanceof RegionSetupScreen screen) screen.acceptSelectionResult(payload);
                })
        );


        event.register(TitleManagerDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof TitleManagerScreen screen) screen.accept(payload);
                    else minecraft.setScreenAndShow(new TitleManagerScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(RankDisplayDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof RankDisplayEditorScreen screen) screen.accept(payload);
                    else minecraft.setScreenAndShow(new RankDisplayEditorScreen(payload, minecraft.gui.screen()));
                })
        );

        event.register(PlayerIdentitySyncPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> PlayerIdentityClientState.apply(payload))
        );

        event.register(DamageIndicatorPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> DamageIndicatorClientState.add(payload))
        );

        event.register(RegionSelectionClientTemplatePayload.TYPE, (payload, context) ->
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

        event.register(be.winnetrie.mod.simpleserverutilities.network.OnboardingStatePayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.OnboardingScreen screen) screen.accept(payload);
                    else if (!"complete".equals(payload.stage())) m.setScreenAndShow(new be.winnetrie.mod.simpleserverutilities.client.gui.OnboardingScreen(payload));
                }));
        event.register(be.winnetrie.mod.simpleserverutilities.network.OnboardingAdminDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.OnboardingAdminScreen screen) screen.accept(payload);
                    else m.setScreenAndShow(new be.winnetrie.mod.simpleserverutilities.client.gui.OnboardingAdminScreen(payload,m.gui.screen()));
                }));
        event.register(be.winnetrie.mod.simpleserverutilities.network.PlayerManagementDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.PlayerManagementScreen screen) screen.accept(payload);
                    else if (m.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.JailPunishmentScreen screen) screen.accept(payload);
                    else m.setScreenAndShow(new be.winnetrie.mod.simpleserverutilities.client.gui.PlayerManagementScreen(payload,m.gui.screen()));
                }));
        event.register(be.winnetrie.mod.simpleserverutilities.network.JailDashboardPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.JailDashboardScreen screen) screen.accept(payload);
                    else m.setScreenAndShow(new be.winnetrie.mod.simpleserverutilities.client.gui.JailDashboardScreen(payload));
                }));
        event.register(be.winnetrie.mod.simpleserverutilities.network.KitDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.KitScreen screen) screen.accept(payload);
                    else m.setScreenAndShow(new be.winnetrie.mod.simpleserverutilities.client.gui.KitScreen(payload,m.gui.screen()));
                }));
        event.register(be.winnetrie.mod.simpleserverutilities.network.KitContentsResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {Minecraft m=Minecraft.getInstance();if(m.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.KitEditorScreen screen)screen.accept(payload);}));
        event.register(be.winnetrie.mod.simpleserverutilities.network.JailAdminDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.JailAdministrationScreen screen) screen.accept(payload);
                    else if (m.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.JailPrisonerOverviewScreen screen) screen.accept(payload);
                    else m.setScreenAndShow(new be.winnetrie.mod.simpleserverutilities.client.gui.JailAdministrationScreen(payload,m.gui.screen()));
                }));
        event.register(be.winnetrie.mod.simpleserverutilities.network.MineDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.MineScreen screen) screen.accept(payload);
                    else m.setScreenAndShow(new be.winnetrie.mod.simpleserverutilities.client.gui.MineScreen(payload,m.gui.screen()));
                }));
        event.register(be.winnetrie.mod.simpleserverutilities.network.ServerOperationsDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft m=Minecraft.getInstance();
                    if (m.gui.screen() instanceof be.winnetrie.mod.simpleserverutilities.client.gui.ServerOperationsScreen screen) screen.accept(payload);
                    else m.setScreenAndShow(new be.winnetrie.mod.simpleserverutilities.client.gui.ServerOperationsScreen(payload,m.gui.screen()));
                }));
    }

    @SubscribeEvent
    static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "hologram_image_cache"),
        (ResourceManagerReloadListener) resourceManager -> HologramImageCache.clear());
    }

    @SubscribeEvent
    static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
        event.register(ClaimRegionBorderRenderer::new);
        event.register(UtilityMiningOutlineRenderer::new);
        event.register(HologramRenderer::new);
        event.register(NpcLabelRenderer::new);
        event.register(MapMarkerRenderer::new);
        event.register(DominationRenderer::new);
        event.register(MinigameSetupVisualRenderer::new);
        event.register(KingOfTheHillVisualRenderer::new);
        event.register(PlayerTitleRenderer::new);
        event.register(DamageIndicatorRenderer::new);
        event.register(RegionSnapshotPreviewRenderer::new);
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
                    && (minecraft.gui.screen() == null || minecraft.gui.screen() instanceof WorldEditCompactOverlayScreen)) {
                if (minecraft.gui.screen() instanceof WorldEditCompactOverlayScreen) minecraft.setScreenAndShow(null);
                else minecraft.setScreenAndShow(new WorldEditCompactOverlayScreen());
            }
        }
        tickUtilityMining(minecraft);
        while (OPEN_MENU.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                ClientPacketDistributor.sendToServer(new MinigameMatchOverviewRequestPayload(
                        "open", minigameOverviewRequestId++));
            }
        }
        while (OPEN_WORLD_MAP.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                ClientPacketDistributor.sendToServer(new WorldMapRequestPayload(
                        minecraft.player.chunkPosition().x(),
                        minecraft.player.chunkPosition().z(),
                        8
                ));
            }
        }
        while (TOGGLE_MINIGAME_HUD.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null
                    && MinigameHudClientState.isServerVisible()) {
                String mode = MinigameHudClientState.cycleDisplayMode();
                minecraft.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Minigame scoreboard: " + mode + "."));
            }
        }
        MinigameKillFeedClientState.tick();
        DamageIndicatorClientState.tick();
        while (SPECTATE_PREVIOUS.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                ClientPacketDistributor.sendToServer(new MinigameSpectatorActionPayload("previous"));
            }
        }
        while (SPECTATE_NEXT.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                ClientPacketDistributor.sendToServer(new MinigameSpectatorActionPayload("next"));
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
            ClientPacketDistributor.sendToServer(new UtilityMiningActivationPayload(treeHeld, veinHeld));
            lastTreeHeld = treeHeld;
            lastVeinHeld = veinHeld;
        }

        if (minecraft.gui.screen() == null && utilityMiningTick % 4 == 0
                && minecraft.hitResult instanceof BlockHitResult blockHit) {
            ClientPacketDistributor.sendToServer(UtilityMiningPreviewRequestPayload.at(blockHit.getBlockPos()));
        }
    }
}
