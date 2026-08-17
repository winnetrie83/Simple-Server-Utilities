package be.winnetrie.mod.simpleserverutilities.client.npc;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.network.NpcLabelSyncPayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttitude;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Renders quest state plus role, NPC name and colored faction as one camera-facing identity stack. */
public final class NpcLabelRenderer implements net.minecraft.client.renderer.debug.DebugRenderer.SimpleDebugRenderer {
    private static final double MAX_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final float NAME_SCALE = 0.40F;
    private static final float SMALL_SCALE = 0.29F;
    private static final float QUEST_SCALE = 0.42F;
    private static final double QUEST_OFFSET = 0.54D;
    private static final double ROLE_OFFSET = 0.235D;
    private static final double FACTION_OFFSET = -0.225D;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int HOSTILE_COLOR = 0xFFFF5555;
    private static final int NEUTRAL_COLOR = 0xFFFFFF55;
    private static final int FRIENDLY_COLOR = 0xFF55FF55;
    private static final int QUEST_AVAILABLE_COLOR = 0xFFFFD54F;
    private static final int QUEST_READY_COLOR = 0xFF55FF55;
    private static final int QUEST_ACTIVE_COLOR = 0xFFB9C3CB;

    private final Minecraft minecraft;

    public NpcLabelRenderer(Minecraft minecraft) { this.minecraft = minecraft; }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues,
                           Frustum frustum, float partialTicks) {
        if (minecraft.player == null || minecraft.level == null) return;
        Vec3 playerPosition = minecraft.player.position();
        for (NpcLabelSyncPayload.Entry entry : NpcLabelClientState.entries()) {
            if (!entry.labelVisible() && entry.questMarker().isBlank()) continue;
            Entity entity = minecraft.level.getEntity(entry.entityId());
            if (entity == null || entity.isRemoved() || !sameUuid(entity, entry.entityUuid())) continue;
            float entityScale = entity instanceof LivingEntity living
                    ? (float) Math.max(0.0625D, Math.min(16.0D, living.getAttributeValue(Attributes.SCALE)))
                    : 1.0F;
            // Render from the same partial-tick interpolated position as the entity model itself.
            // Using entity.position() here sampled only the last client tick and made the SSU role/name/
            // faction stack visibly trail behind a smoothly interpolated moving NPC.
            Vec3 interpolated = entity.getPosition(partialTicks);
            Vec3 base = interpolated.add(0.0D, entity.getBbHeight() + 0.52D * entityScale, 0.0D);
            if (playerPosition.distanceToSqr(base) > MAX_DISTANCE_SQUARED) continue;

            if (!entry.questMarker().isBlank()) {
                text(entry.questMarker(), base.add(0.0D, QUEST_OFFSET * entityScale, 0.0D),
                        questMarkerColor(entry.questMarker()), QUEST_SCALE * entityScale);
            }
            if (entry.labelVisible()) {
                text(entry.roleId(),
                        base.add(0.0D, ROLE_OFFSET * entityScale, 0.0D),
                        0xFF000000 | HologramRichText.minecraftColorRgb(entry.roleColor()), SMALL_SCALE * entityScale);
                text(entry.displayName(), base, NAME_COLOR, NAME_SCALE * entityScale);
                if (!entry.factionName().isBlank()) {
                    text(entry.factionName(), base.add(0.0D, FACTION_OFFSET * entityScale, 0.0D),
                            attitudeColor(entry.attitude()), SMALL_SCALE * entityScale);
                }
            }
        }
    }

    private static boolean sameUuid(Entity entity, String rawUuid) {
        try { return entity.getUUID().equals(UUID.fromString(rawUuid)); }
        catch (IllegalArgumentException exception) { return false; }
    }

    private static int questMarkerColor(String marker) {
        return switch (marker == null ? "" : marker) {
            case "?" -> QUEST_READY_COLOR;
            case "!" -> QUEST_AVAILABLE_COLOR;
            default -> QUEST_ACTIVE_COLOR;
        };
    }

    private static int attitudeColor(String rawAttitude) {
        return switch (NpcAttitude.parse(rawAttitude)) {
            case HOSTILE -> HOSTILE_COLOR;
            case FRIENDLY -> FRIENDLY_COLOR;
            case NEUTRAL -> NEUTRAL_COLOR;
        };
    }

    private static void text(String value, Vec3 position, int color, float scale) {
        if (value == null || value.isBlank()) return;
        Gizmos.billboardText(value, position, TextGizmo.Style.forColorAndCentered(color).withScale(scale));
    }
}
