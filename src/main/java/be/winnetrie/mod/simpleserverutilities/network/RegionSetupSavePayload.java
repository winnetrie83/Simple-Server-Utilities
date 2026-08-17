package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded full save from the Region Setup Tool. */
public record RegionSetupSavePayload(
        String mode, String regionName, String dimension, long point1, long point2,
        int priority, boolean borderVisible,
        boolean allowBreak, boolean allowPlace, boolean allowInteract, boolean allowPvp,
        boolean allowExplosions, boolean allowPistons, boolean allowWater, boolean allowLava,
        boolean allowRedstone, boolean allowHoppers, boolean allowFireSpread,
        String welcomeMessage, String leaveMessage,
        boolean rentable, String rentPrice, int rentPeriodDays, boolean resetOnExpire, boolean resetOnUnrent,
        boolean scheduledResetEnabled, long resetIntervalSeconds, String resetMode, boolean resetOnlyWhenEmpty,
        boolean replacePreset, List<Integer> presetSlots, List<Integer> presetPercentages,
        long requestId
) implements CustomPacketPayload {
    public static final int MAX_PRESET = 6;
    public static final Type<RegionSetupSavePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID,"region_setup_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RegionSetupSavePayload> STREAM_CODEC=StreamCodec.of(RegionSetupSavePayload::encode,RegionSetupSavePayload::decode);
    public RegionSetupSavePayload {
        mode=PayloadBounds.string(mode,16); regionName=PayloadBounds.trimmedString(regionName,64); dimension=PayloadBounds.string(dimension,128);
        priority=Math.max(-1_000_000,Math.min(1_000_000,priority)); welcomeMessage=PayloadBounds.string(welcomeMessage,256); leaveMessage=PayloadBounds.string(leaveMessage,256);
        rentPrice=PayloadBounds.trimmedString(rentPrice,64); rentPeriodDays=Math.max(-1,Math.min(365_000,rentPeriodDays));
        resetIntervalSeconds=Math.max(0L,resetIntervalSeconds); resetMode=PayloadBounds.string(resetMode,16); requestId=Math.max(0L,requestId);
        presetSlots=presetSlots==null?List.of():List.copyOf(presetSlots.subList(0,Math.min(MAX_PRESET,presetSlots.size())));
        presetPercentages=presetPercentages==null?List.of():List.copyOf(presetPercentages.subList(0,Math.min(MAX_PRESET,presetPercentages.size())));
    }
    private static void encode(RegistryFriendlyByteBuf b,RegionSetupSavePayload p){
        b.writeUtf(p.mode,16);b.writeUtf(p.regionName,64);b.writeUtf(p.dimension,128);b.writeLong(p.point1);b.writeLong(p.point2);b.writeVarInt(p.priority);b.writeBoolean(p.borderVisible);
        b.writeBoolean(p.allowBreak);b.writeBoolean(p.allowPlace);b.writeBoolean(p.allowInteract);b.writeBoolean(p.allowPvp);b.writeBoolean(p.allowExplosions);b.writeBoolean(p.allowPistons);b.writeBoolean(p.allowWater);b.writeBoolean(p.allowLava);b.writeBoolean(p.allowRedstone);b.writeBoolean(p.allowHoppers);b.writeBoolean(p.allowFireSpread);
        b.writeUtf(p.welcomeMessage,256);b.writeUtf(p.leaveMessage,256);b.writeBoolean(p.rentable);b.writeUtf(p.rentPrice,64);b.writeVarInt(p.rentPeriodDays);b.writeBoolean(p.resetOnExpire);b.writeBoolean(p.resetOnUnrent);
        b.writeBoolean(p.scheduledResetEnabled);b.writeVarLong(p.resetIntervalSeconds);b.writeUtf(p.resetMode,16);b.writeBoolean(p.resetOnlyWhenEmpty);b.writeBoolean(p.replacePreset);
        b.writeVarInt(p.presetSlots.size());for(int v:p.presetSlots)b.writeVarInt(v);b.writeVarInt(p.presetPercentages.size());for(int v:p.presetPercentages)b.writeVarInt(v);b.writeVarLong(p.requestId);
    }
    private static RegionSetupSavePayload decode(RegistryFriendlyByteBuf b){
        String mode=b.readUtf(16),name=b.readUtf(64),dimension=b.readUtf(128);long p1=b.readLong(),p2=b.readLong();int priority=b.readVarInt();boolean border=b.readBoolean();
        boolean br=b.readBoolean(),pl=b.readBoolean(),in=b.readBoolean(),pvp=b.readBoolean(),ex=b.readBoolean(),pi=b.readBoolean(),wa=b.readBoolean(),la=b.readBoolean(),re=b.readBoolean(),ho=b.readBoolean(),fi=b.readBoolean();
        String welcome=b.readUtf(256),leave=b.readUtf(256);boolean rentable=b.readBoolean();String price=b.readUtf(64);int days=b.readVarInt();boolean expire=b.readBoolean(),unrent=b.readBoolean();
        boolean enabled=b.readBoolean();long interval=b.readVarLong();String resetMode=b.readUtf(16);boolean empty=b.readBoolean(),replace=b.readBoolean();
        int sc=b.readVarInt();if(sc<0||sc>MAX_PRESET)throw new IllegalArgumentException("Invalid region preset slot count: "+sc);ArrayList<Integer> slots=new ArrayList<>(sc);for(int i=0;i<sc;i++)slots.add(b.readVarInt());
        int pc=b.readVarInt();if(pc<0||pc>MAX_PRESET)throw new IllegalArgumentException("Invalid region preset percentage count: "+pc);ArrayList<Integer> percentages=new ArrayList<>(pc);for(int i=0;i<pc;i++)percentages.add(b.readVarInt());
        return new RegionSetupSavePayload(mode,name,dimension,p1,p2,priority,border,br,pl,in,pvp,ex,pi,wa,la,re,ho,fi,welcome,leave,rentable,price,days,expire,unrent,enabled,interval,resetMode,empty,replace,slots,percentages,b.readVarLong());
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
