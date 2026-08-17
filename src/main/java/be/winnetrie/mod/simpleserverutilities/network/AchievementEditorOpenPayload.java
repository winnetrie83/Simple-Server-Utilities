package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Opens the guided achievement editor and provides currency formatting for friendly money rewards. */
public record AchievementEditorOpenPayload(String originalAchievementId,String achievementJson,String currencySymbol,int currencyDecimalPlaces,long requestId) implements CustomPacketPayload {
    public static final Type<AchievementEditorOpenPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"achievement_editor_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf,AchievementEditorOpenPayload> STREAM_CODEC=StreamCodec.of(AchievementEditorOpenPayload::encode,AchievementEditorOpenPayload::decode);
    public AchievementEditorOpenPayload{
        originalAchievementId=PayloadBounds.string(originalAchievementId,64);
        achievementJson=PayloadBounds.string(achievementJson,131071);
        currencySymbol=PayloadBounds.string(currencySymbol,16);
        currencyDecimalPlaces=Math.max(0,Math.min(4,currencyDecimalPlaces));
        requestId=Math.max(0L,requestId);
    }
    private static void encode(RegistryFriendlyByteBuf b,AchievementEditorOpenPayload p){b.writeUtf(p.originalAchievementId,64);b.writeUtf(p.achievementJson,131071);b.writeUtf(p.currencySymbol,16);b.writeVarInt(p.currencyDecimalPlaces);b.writeVarLong(p.requestId);}
    private static AchievementEditorOpenPayload decode(RegistryFriendlyByteBuf b){return new AchievementEditorOpenPayload(b.readUtf(64),b.readUtf(131071),b.readUtf(16),b.readVarInt(),b.readVarLong());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
