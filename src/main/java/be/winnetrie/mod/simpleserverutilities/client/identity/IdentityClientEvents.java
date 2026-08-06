package be.winnetrie.mod.simpleserverutilities.client.identity;

import be.winnetrie.mod.simpleserverutilities.identity.RichTextComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/** Replaces the vanilla player nametag text with an optional styled rank prefix plus a plain player name. */
public final class IdentityClientEvents {
    private IdentityClientEvents() {
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent.CanRender event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var entry = PlayerIdentityClientState.get(player.getId());
        if (entry == null) return;
        MutableComponent name = Component.empty();
        if (entry.showRank() && !entry.rankPrefix().isBlank()) name.append(RichTextComponents.fromEncoded(entry.rankPrefix()));
        name.append(Component.literal(entry.playerName().isBlank() ? player.getName().getString() : entry.playerName()).withStyle(Style.EMPTY));
        event.setContent(name);
    }
}
