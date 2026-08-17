package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NpcDialogueEditorRequestPayload(String instanceId) implements CustomPacketPayload {
    public static final Type<NpcDialogueEditorRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_dialogue_editor_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDialogueEditorRequestPayload> STREAM_CODEC =
            StreamCodec.of(NpcDialogueEditorRequestPayload::encode, NpcDialogueEditorRequestPayload::decode);
    public NpcDialogueEditorRequestPayload { instanceId = PayloadBounds.string(instanceId, 36); }
    private static void encode(RegistryFriendlyByteBuf b, NpcDialogueEditorRequestPayload p) { b.writeUtf(p.instanceId, 36); }
    private static NpcDialogueEditorRequestPayload decode(RegistryFriendlyByteBuf b) { return new NpcDialogueEditorRequestPayload(b.readUtf(36)); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
