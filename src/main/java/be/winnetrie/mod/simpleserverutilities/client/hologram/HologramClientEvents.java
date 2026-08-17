package be.winnetrie.mod.simpleserverutilities.client.hologram;

import java.net.URI;

import be.winnetrie.mod.simpleserverutilities.hologram.HologramToolManager;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class HologramClientEvents {
    private HologramClientEvents() {
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) return;

        boolean hologramTool = minecraft.player.getMainHandItem().is(Items.AMETHYST_SHARD)
                && HologramToolManager.TOOL_NAME
                .equals(minecraft.player.getMainHandItem().getHoverName().getString());
        if (hologramTool) {
            String id = HologramClientState.targetedHologramId(minecraft);
            if (id == null) return; // Let the normal server-side right-click open a creation editor.
            PacketDistributor.sendToServer(new HologramEditorRequestPayload(id));
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }

        URI link = HologramClientState.targetedLink(minecraft);
        if (link == null) return;
        Util.getPlatform().openUri(link);
        event.setCanceled(true);
        event.setSwingHand(false);
    }
}
