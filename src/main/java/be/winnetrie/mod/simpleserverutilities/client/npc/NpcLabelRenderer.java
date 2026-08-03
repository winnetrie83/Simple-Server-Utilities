package be.winnetrie.mod.simpleserverutilities.client.npc;

import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.network.NpcLabelSyncPayload;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttitude;
import be.winnetrie.mod.simpleserverutilities.npc.NpcRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Renders role, NPC name and colored faction as one camera-facing identity stack. */
public final class NpcLabelRenderer implements net.minecraft.client.renderer.debug.DebugRenderer.SimpleDebugRenderer {
    private static final double MAX_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final float NAME_SCALE = 0.20F;
    private static final float SMALL_SCALE = 0.145F;
    private static final double ROLE_OFFSET = 0.235D;
    private static final double FACTION_OFFSET = -0.225D;
    private static final int ROLE_COLOR = 0xFFB9C3CB;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int HOSTILE_COLOR = 0xFFFF5555;
    private static final int NEUTRAL_COLOR = 0xFFFFFF55;
    private static final int FRIENDLY_COLOR = 0xFF55FF55;

    private final Minecraft minecraft;

    public NpcLabelRenderer(Minecraft minecraft) { this.minecraft = minecraft; }

    @Override
    public void emitGizmos(double camX, double camY, double camZ, DebugValueAccess debugValues,
                           Frustum frustum, float partialTicks) {
        if (minecraft.player == null || minecraft.level == null) return;
        Vec3 playerPosition = minecraft.player.position();
        for (NpcLabelSyncPayload.Entry entry : NpcLabelClientState.entries()) {
            Entity entity = minecraft.level.getEntity(entry.entityId());
            if (entity == null || entity.isRemoved() || !sameUuid(entity, entry.entityUuid())) continue;
            Vec3 base = entity.position().add(0.0D, entity.getBbHeight() + 0.52D, 0.0D);
            if (playerPosition.distanceToSqr(base) > MAX_DISTANCE_SQUARED) continue;

            text(NpcRole.parse(entry.roleId()).label(), base.add(0.0D, ROLE_OFFSET, 0.0D), ROLE_COLOR, SMALL_SCALE);
            text(entry.displayName(), base, NAME_COLOR, NAME_SCALE);
            if (!entry.factionName().isBlank()) {
                text(entry.factionName(), base.add(0.0D, FACTION_OFFSET, 0.0D),
                        attitudeColor(entry.attitude()), SMALL_SCALE);
            }
        }
    }

    private static boolean sameUuid(Entity entity, String rawUuid) {
        try { return entity.getUUID().equals(UUID.fromString(rawUuid)); }
        catch (IllegalArgumentException exception) { return false; }
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
