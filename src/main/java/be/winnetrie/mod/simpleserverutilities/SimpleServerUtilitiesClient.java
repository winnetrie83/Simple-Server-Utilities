package be.winnetrie.mod.simpleserverutilities;

import com.mojang.blaze3d.platform.InputConstants;
import be.winnetrie.mod.simpleserverutilities.client.gui.ClaimMapScreen;
import be.winnetrie.mod.simpleserverutilities.client.gui.SsuDashboardScreen;
import be.winnetrie.mod.simpleserverutilities.client.minimap.MinimapClientState;
import be.winnetrie.mod.simpleserverutilities.client.visualization.BorderVisualizationClientState;
import be.winnetrie.mod.simpleserverutilities.client.visualization.ClaimRegionBorderRenderer;
import be.winnetrie.mod.simpleserverutilities.network.BorderVisualizationPayload;
import be.winnetrie.mod.simpleserverutilities.network.ClaimMapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinimapDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.SsuMenuSnapshotPayload;
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
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

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

    public SimpleServerUtilitiesClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(SimpleServerUtilitiesClient::onClientLogout);
        NeoForge.EVENT_BUS.addListener(SimpleServerUtilitiesClient::onClientTick);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(SSU_CATEGORY);
        event.register(OPEN_MENU);
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minimap"),
                MinimapClientState::render
        );
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
                    Minecraft.getInstance().setScreenAndShow(new SsuDashboardScreen(payload));
                })
        );

        event.register(MinimapDataPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> MinimapClientState.apply(payload))
        );
    }

    @SubscribeEvent
    static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
        event.register(ClaimRegionBorderRenderer::new);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        MinimapClientState.tick();
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_MENU.consumeClick()) {
            if (minecraft.player != null && minecraft.gui.screen() == null) {
                minecraft.player.connection.sendUnattendedCommand("ssu menu", null);
            }
        }
    }

    private static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BorderVisualizationClientState.clear();
        MinimapClientState.clear();
    }
}
