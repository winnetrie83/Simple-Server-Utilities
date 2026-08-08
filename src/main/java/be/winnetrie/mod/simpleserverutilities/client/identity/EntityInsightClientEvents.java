package be.winnetrie.mod.simpleserverutilities.client.identity;

import java.util.Locale;

import be.winnetrie.mod.simpleserverutilities.network.EntityInsightPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/** Forces a colored nameplate for living entities selected by Entity Insight. */
public final class EntityInsightClientEvents {
    private EntityInsightClientEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNameTag(RenderNameTagEvent.CanRender event) {
        if (!(event.getEntity() instanceof LivingEntity living) || living instanceof Player) return;
        // SSU NPCs already have their own multi-line overhead label. Vanilla still reveals a
        // custom-name nameplate while the player targets the entity, which caused the NPC name
        // to be drawn a second time on top of SSU's label. Suppress only that vanilla layer.
        if (living.entityTags().contains("ssu_npc")) {
            event.setCanRender(TriState.FALSE);
            return;
        }
        EntityInsightPayload.Entry entry = EntityInsightClientState.entry(living.getId());
        if (entry == null || !entry.entityUuid().equals(living.getUUID())) return;
        EntityInsightPayload.Attitude attitude = entry.attitude();

        StringBuilder label = new StringBuilder(living.getName().getString());
        if (EntityInsightClientState.showHealth()) {
            label.append("  ")
                    .append(formatHealth(living.getHealth()))
                    .append('/')
                    .append(formatHealth(living.getMaxHealth()))
                    .append(" HP");
        }
        event.setContent(Component.literal(label.toString())
                .withStyle(Style.EMPTY.withColor(attitude.rgb())));
        event.setCanRender(TriState.TRUE);
    }

    private static String formatHealth(float value) {
        float safe = Math.max(0.0F, value);
        float rounded = Math.round(safe * 10.0F) / 10.0F;
        if (Math.abs(rounded - Math.round(rounded)) < 0.001F) {
            return Integer.toString(Math.round(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }
}
