package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AchievementMenuRequestPayload(String action,String target,String achievementId,String filter,int page,long requestId) implements CustomPacketPayload {
    public static final Type<AchievementMenuRequestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"achievement_menu_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,AchievementMenuRequestPayload> STREAM_CODEC=StreamCodec.of(AchievementMenuRequestPayload::encode,AchievementMenuRequestPayload::decode);
    public AchievementMenuRequestPayload{action=PayloadBounds.trimmedString(action,32);target=PayloadBounds.trimmedString(target,64);achievementId=PayloadBounds.trimmedString(achievementId,64);filter=PayloadBounds.trimmedString(filter,16);page=Math.max(0,Math.min(65535,page));requestId=Math.max(0L,requestId);}
    private static void encode(RegistryFriendlyByteBuf b,AchievementMenuRequestPayload p){b.writeUtf(p.action,32);b.writeUtf(p.target,64);b.writeUtf(p.achievementId,64);b.writeUtf(p.filter,16);b.writeVarInt(p.page);b.writeVarLong(p.requestId);}
    private static AchievementMenuRequestPayload decode(RegistryFriendlyByteBuf b){return new AchievementMenuRequestPayload(b.readUtf(32),b.readUtf(64),b.readUtf(64),b.readUtf(16),b.readVarInt(),b.readVarLong());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
