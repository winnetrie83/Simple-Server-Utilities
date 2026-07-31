package be.winnetrie.mod.simpleserverutilities;

import com.mojang.blaze3d.platform.InputConstants;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramClientEvents;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramImageCache;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramClientState;
import be.winnetrie.mod.simpleserverutilities.client.hologram.HologramRenderer;
import be.winnetrie.mod.simpleserverutilities.client.gui.ClaimMapScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.SsuDashboardScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.PropertySettingsScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionPermissionScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionEditorScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.WorldMapScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MailScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MailComposeScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.HologramEditorScreen;
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
import be.winnetrie.mod.simpleserverutilities.network.RegionEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.RegionEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinimapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailComposeResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailRecipientSuggestionsPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerSyncPayload;
import be.winnetrie.mod.simpleserverutilities.network.MapMarkerActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuPageDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPlayerProfileDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsDataPayload;
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
    }

    @SubscribeEvent
    static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMailMenus.MAIL_COMPOSE.get(), MailComposeScreen::new);
    }

    @SubscribeEvent
    static void onRegisterClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ClaimMapDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.gui.screen() instanceof ClaimMapScreen screen) {
                        screen.acceptSnapshot(payload);
                    } else {
                        minecraft.setScreenAndShow(new ClaimMapScreen(payload));
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
        event.register(MapMarkerRenderer::new);
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
        MapMarkerClientState.clear();
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
