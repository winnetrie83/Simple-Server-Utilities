package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcLoadoutSavePayload(int containerId, int mode, int rolls, int[] chances, long requestId)
        implements CustomPacketPayload {
    public static final Type<NpcLoadoutSavePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_loadout_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcLoadoutSavePayload> STREAM_CODEC =
            StreamCodec.of(NpcLoadoutSavePayload::encode, NpcLoadoutSavePayload::decode);
    public NpcLoadoutSavePayload {
        chances = chances == null ? new int[9] : java.util.Arrays.copyOf(chances, 9);
        rolls = Math.max(1, Math.min(100, rolls)); requestId = Math.max(0L, requestId);
    }
    private static void encode(RegistryFriendlyByteBuf b, NpcLoadoutSavePayload p) {
        b.writeVarInt(p.containerId); b.writeVarInt(p.mode); b.writeVarInt(p.rolls);
        for (int chance : p.chances) b.writeVarInt(chance); b.writeVarLong(p.requestId);
    }
    private static NpcLoadoutSavePayload decode(RegistryFriendlyByteBuf b) {
        int container = b.readVarInt(), mode = b.readVarInt(), rolls = b.readVarInt(); int[] chances = new int[9];
        for (int i = 0; i < 9; i++) chances[i] = b.readVarInt();
        return new NpcLoadoutSavePayload(container, mode, rolls, chances, b.readVarLong());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
