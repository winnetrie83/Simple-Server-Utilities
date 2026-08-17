package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcDialogueChoicePayload(String sessionId, String choiceId, long requestId)
        implements CustomPacketPayload {
    public static final Type<NpcDialogueChoicePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_dialogue_choice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDialogueChoicePayload> STREAM_CODEC =
            StreamCodec.of(NpcDialogueChoicePayload::encode, NpcDialogueChoicePayload::decode);

    public NpcDialogueChoicePayload {
        sessionId = PayloadBounds.string(sessionId, 36); choiceId = PayloadBounds.string(choiceId, 64); requestId = Math.max(0L, requestId);
    }
    private static void encode(RegistryFriendlyByteBuf b, NpcDialogueChoicePayload p) {
        b.writeUtf(p.sessionId, 36); b.writeUtf(p.choiceId, 64); b.writeVarLong(p.requestId);
    }
    private static NpcDialogueChoicePayload decode(RegistryFriendlyByteBuf b) {
        return new NpcDialogueChoicePayload(b.readUtf(36), b.readUtf(64), b.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
