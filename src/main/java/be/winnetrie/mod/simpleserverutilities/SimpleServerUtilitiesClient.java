package be.winnetrie.mod.simpleserverutilities;

import com.mojang.blaze3d.platform.InputConstants;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramClientEvents;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramImageCache;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramClientState;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramRenderer;
import be.winnetrie.mod.simpleserverutilities.client.npc.NpcLabelClientState;
import be.winnetrie.mod.simpleserverutilities.client.npc.NpcLabelRenderer;
import be.winnetrie.mod.simpleserverutilities.client.gui.ManagedDimensionScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.ClaimMapScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.SsuDashboardScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.PropertySettingsScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.TrustedPlayersScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionPermissionScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionSelectionToolScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionSelectionEditScreen;
import be.winnetrie.mod.simpleserverutilities.client.region.RegionSelectionClientStorage;
import be.winnetrie.mod.simpleserverutilities.client.gui.WorldMapScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MailScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MailComposeScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.AuctionHouseScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.AuctionSellScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.HologramEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcLoadoutScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcAdminScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcDialogueScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcDialogueEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcFunctionMenuScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcShopScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcShopAdminScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcShopEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.NpcItemPriceCatalogScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.QuestBookScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.QuestEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameLobbyScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameAdminScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameSetupToolScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameSetupCreateScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MinigameSelectionCreateScreen;
import be.winnetrie.mod.simpleserverutilities.client.minigame.MinigameHudClientState;
import be.winnetrie.mod.simpleserverutilities.client.minigame.CaptureTheFlagClientState;
import be.winnetrie.mod.simpleserverutilities.client.minigame.DominationClientState;
import be.winnetrie.mod.simpleserverutilities.client.minigame.DominationRenderer;
import be.winnetrie.mod.simpleserverutilities.client.minigame.MinigameCastBarClientState;
import be.winnetrie.mod.simpleserverutilities.client.minigame.MinigameSetupVisualClientState;
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
import be.winnetrie.mod.simpleserverutilities.network.BlockInformationContentPayload;
import be.winnetrie.mod.simpleserverutilities.network.BlockInformationStatePayload;
import be.winnetrie.mod.simpleserverutilities.network.StatisticEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.StatisticEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.HologramSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcLabelSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcLoadoutResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcAdminListPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueViewPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcDialogueEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcFunctionMenuPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopAdminDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcShopEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.NpcItemPriceCatalogDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestBookDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.QuestEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameLobbyDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameHudPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameCastBarPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameDominationVisualPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameCtfVisualPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSelectionCreateResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupToolOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameSetupVisualPayload;
import be.winnetrie.mod.simpleserverutilities.network.DungeonLobbyDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.DungeonEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.DungeonEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionEditorOpenPayload;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
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

    private static int utilityMiningTick;
    private static boolean lastTreeHeld;
    private static boolean lastVeinHeld;

    public SimpleServerUtilitiesClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(SimpleServerUtilitiesClient::onClientLogout);
        NeoForge.EVENT_BUS.addListener(SimpleServerUtilitiesClient::onClientTick);
        NeoForge.EVENT_BUS.register(HologramClientEvents.class);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(SSU_CATEGORY);
        event.register(OPEN_MENU);
        event.register(OPEN_WORLD_MAP);
        event.register(ACTIVATE_TREECAPITATOR);
        event.register(ACTIVATE_VEINMINER);
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
                Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_hud"),
                MinigameHudClientState::render
        );
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_cast_bar"),
                MinigameCastBarClientState::render
        );
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMailMenus.MAIL_COMPOSE.get(), MailComposeScreen::new);
        event.register(ModAuctionMenus.AUCTION_SELL.get(), AuctionSellScreen::new);
        event.register(ModNpcMenus.NPC_LOADOUT.get(), NpcLoadoutScreen::new);
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

        event.register(NpcLabelSyncPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> NpcLabelClientState.apply(payload))
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

        event.register(NpcLoadoutResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcLoadoutScreen screen) screen.acceptResult(payload);
                })
        );

        event.register(NpcAdminListPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof NpcAdminScreen screen) screen.accept(payload);
                    else minecraft.setScreenAndShow(new NpcAdminScreen(payload, minecraft.gui.screen()));
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
                context.enqueueWork(() -> Minecraft.getInstance().setScreenAndShow(new RegionSelectionToolScreen(payload)))
        );

        event.register(RegionSelectionActionResultPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof RegionSelectionEditScreen screen) screen.acceptResult(payload);
                    else if (minecraft.gui.screen() instanceof RegionSelectionToolScreen screen) screen.acceptResult(payload);
                })
        );

        event.register(RegionSelectionClientTemplatePayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof RegionSelectionEditScreen screen) {
                        screen.acceptClientTemplate(payload.name(), payload.data(), payload.requestId());
                        return;
                    }
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
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        AerialMapAtlas.tick();
        MinimapClientState.tick();
        Minecraft minecraft = Minecraft.getInstance();
        tickUtilityMining(minecraft);
        while (OPEN_MENU.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                minecraft.player.connection.sendUnattendedCommand("ssu menu", null);
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
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BorderVisualizationClientState.clear();
        BlockInformationClientState.clear();
        MinimapClientState.clear();
        AerialMapAtlas.clear();
        UtilityMiningClientState.clear();
        HologramClientState.clear();
        NpcLabelClientState.clear();
        MapMarkerClientState.clear();
        MinigameHudClientState.clear();
        CaptureTheFlagClientState.clear();
        DominationClientState.clear();
        MinigameCastBarClientState.clear();
        utilityMiningTick = 0;
        lastTreeHeld = false;
        lastVeinHeld = false;
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
