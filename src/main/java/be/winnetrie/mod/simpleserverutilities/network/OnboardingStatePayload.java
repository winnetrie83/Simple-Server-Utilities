package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OnboardingStatePayload(String stage, String rules, String introduction, int pageIndex, int pageCount,
                                     boolean skippable, String notice, boolean error) implements CustomPacketPayload {
    public static final Type<OnboardingStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID,"onboarding_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,OnboardingStatePayload> STREAM_CODEC=StreamCodec.of(OnboardingStatePayload::encode,OnboardingStatePayload::decode);
    public OnboardingStatePayload { stage=PayloadBounds.string(stage,24); rules=PayloadBounds.string(rules,HologramRichText.MAX_STORED_CHARACTERS); introduction=PayloadBounds.string(introduction,HologramRichText.MAX_STORED_CHARACTERS); pageIndex=Math.max(0,pageIndex); pageCount=Math.max(0,Math.min(16,pageCount)); notice=PayloadBounds.string(notice,512); }
    public static OnboardingStatePayload complete(String notice){return new OnboardingStatePayload("complete","","",0,0,true,notice,false);}
    private static void encode(RegistryFriendlyByteBuf b,OnboardingStatePayload p){b.writeUtf(p.stage,24);b.writeUtf(p.rules,HologramRichText.MAX_STORED_CHARACTERS);b.writeUtf(p.introduction,HologramRichText.MAX_STORED_CHARACTERS);b.writeVarInt(p.pageIndex);b.writeVarInt(p.pageCount);b.writeBoolean(p.skippable);b.writeUtf(p.notice,512);b.writeBoolean(p.error);}
    private static OnboardingStatePayload decode(RegistryFriendlyByteBuf b){return new OnboardingStatePayload(b.readUtf(24),b.readUtf(HologramRichText.MAX_STORED_CHARACTERS),b.readUtf(HologramRichText.MAX_STORED_CHARACTERS),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readUtf(512),b.readBoolean());}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
