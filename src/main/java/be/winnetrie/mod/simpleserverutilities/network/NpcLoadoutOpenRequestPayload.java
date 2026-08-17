package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcLoadoutOpenRequestPayload(String instanceId, int mode) implements CustomPacketPayload {
    public static final Type<NpcLoadoutOpenRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_loadout_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcLoadoutOpenRequestPayload> STREAM_CODEC =
            StreamCodec.of((b, p) -> { b.writeUtf(p.instanceId, 36); b.writeVarInt(p.mode); },
                    b -> new NpcLoadoutOpenRequestPayload(b.readUtf(36), b.readVarInt()));
    public NpcLoadoutOpenRequestPayload { instanceId = instanceId == null ? "" : instanceId; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
