package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NpcLoadoutResultPayload(boolean successful, String message, long requestId)
        implements CustomPacketPayload {
    public static final Type<NpcLoadoutResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_loadout_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcLoadoutResultPayload> STREAM_CODEC =
            StreamCodec.of((b, p) -> { b.writeBoolean(p.successful); b.writeUtf(p.message, 256); b.writeVarLong(p.requestId); },
                    b -> new NpcLoadoutResultPayload(b.readBoolean(), b.readUtf(256), b.readVarLong()));
    public NpcLoadoutResultPayload { message = message == null ? "" : message; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
