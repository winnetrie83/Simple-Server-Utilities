package be.winnetrie.mod.simpleserverutilities;

import com.mojang.blaze3d.platform.InputConstants;
import be.winnetrie.mod.simpleserverutilities.client.gui.ClaimMapScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.SsuDashboardScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.PropertySettingsScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.RegionPermissionScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.WorldMapScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MailScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.MailComposeScreen;
import be.winnetrie.mod.simpleserverutilities.client.map.AerialMapAtlas;
import be.winnetrie.mod.simpleserverutilities.client.minimap.MinimapClientState;
import be.winnetrie.mod.simpleserverutilities.client.visualization.BorderVisualizationClientState;
import be.winnetrie.mod.simpleserverutilities.client.visualization.ClaimRegionBorderRenderer;
import be.winnetrie.mod.simpleserverutilities.network.BorderVisualizationPayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinimapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailComposeResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MailRecipientSuggestionsPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuPageDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuActionResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPermissionEditorDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPlayerProfileDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuPropertySettingsDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.WorldMapRequestPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
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

    public SimpleServerUtilitiesClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(SimpleServerUtilitiesClient::onClientLogout);
        NeoForge.EVENT_BUS.addListener(SimpleServerUtilitiesClient::onClientTick);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(SSU_CATEGORY);
        event.register(OPEN_MENU);
        event.register(OPEN_WORLD_MAP);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minimap"),
                MinimapClientState::render
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

        event.register(SsuMenuSnapshotPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> {
                    MinimapClientState.applySettings(payload.uiSettings());
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
    }

    @SubscribeEvent
    static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
        event.register(ClaimRegionBorderRenderer::new);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        AerialMapAtlas.tick();
        MinimapClientState.tick();
        Minecraft minecraft = Minecraft.getInstance();
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
        MinimapClientState.clear();
        AerialMapAtlas.clear();
    }
}
