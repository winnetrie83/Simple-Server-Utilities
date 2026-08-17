package be.winnetrie.mod.simpleserverutilities.network;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;import net.minecraft.network.codec.StreamCodec;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.resources.ResourceLocation;
public record QuestEditorRequestPayload(String questId,long requestId) implements CustomPacketPayload{
 public static final Type<QuestEditorRequestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"quest_editor_request"));
 public static final StreamCodec<RegistryFriendlyByteBuf,QuestEditorRequestPayload> STREAM_CODEC=StreamCodec.of(QuestEditorRequestPayload::encode,QuestEditorRequestPayload::decode);
 public QuestEditorRequestPayload{questId=PayloadBounds.trimmedString(questId,64);requestId=Math.max(0L,requestId);}private static void encode(RegistryFriendlyByteBuf b,QuestEditorRequestPayload p){b.writeUtf(p.questId,64);b.writeVarLong(p.requestId);}private static QuestEditorRequestPayload decode(RegistryFriendlyByteBuf b){return new QuestEditorRequestPayload(b.readUtf(64),b.readVarLong());}@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}}
