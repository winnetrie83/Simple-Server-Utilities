package be.winnetrie.mod.simpleserverutilities.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

public record NpcAdminEntry(boolean template, String id, String definitionId, String name, String model,
        String dimension, double x, double y, double z, int placements, boolean enabled, boolean dead) {
    public NpcAdminEntry {
        id = PayloadBounds.string(id, 64); definitionId = PayloadBounds.string(definitionId, 64); name = PayloadBounds.string(name, 64);
        model = PayloadBounds.string(model, 128); dimension = PayloadBounds.string(dimension, 256); placements = Math.max(0, placements);
    }
    public static void encode(RegistryFriendlyByteBuf b, NpcAdminEntry e) {
        b.writeBoolean(e.template); b.writeUtf(e.id, 64); b.writeUtf(e.definitionId, 64); b.writeUtf(e.name, 64);
        b.writeUtf(e.model, 128); b.writeUtf(e.dimension, 256); b.writeDouble(e.x); b.writeDouble(e.y); b.writeDouble(e.z);
        b.writeVarInt(e.placements); b.writeBoolean(e.enabled); b.writeBoolean(e.dead);
    }
    public static NpcAdminEntry decode(RegistryFriendlyByteBuf b) {
        return new NpcAdminEntry(b.readBoolean(), b.readUtf(64), b.readUtf(64), b.readUtf(64), b.readUtf(128),
                b.readUtf(256), b.readDouble(), b.readDouble(), b.readDouble(), b.readVarInt(), b.readBoolean(), b.readBoolean());
    }

}
