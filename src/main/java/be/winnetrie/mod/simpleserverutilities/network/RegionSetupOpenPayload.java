package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Full region setup-tool state for selection, creation, local and remote editing. */
public record RegionSetupOpenPayload(
        String mode, String notice, boolean error, long requestId,
        String regionName, String dimension,
        boolean hasPoint1, long point1, boolean hasPoint2, long point2, long volume,
        int priority, boolean borderVisible,
        boolean allowBreak, boolean allowPlace, boolean allowInteract, boolean allowPvp,
        boolean allowExplosions, boolean allowPistons, boolean allowWater, boolean allowLava,
        boolean allowRedstone, boolean allowHoppers, boolean allowFireSpread,
        String welcomeMessage, String leaveMessage,
        boolean hasSpawn, long spawnPos, float spawnYaw, float spawnPitch,
        boolean rentable, String rentPrice, int rentPeriodDays,
        boolean resetOnExpire, boolean resetOnUnrent,
        int managerCount, int memberCount,
        boolean snapshotAvailable,
        boolean scheduledResetEnabled, long resetIntervalSeconds, String resetMode,
        boolean resetOnlyWhenEmpty, String presetSummary, long nextResetAt, long lastResetAt,
        boolean canCreate, boolean canEdit, boolean canDelete,
        String selectionDimension,
        boolean selectionHasPoint1, long selectionPoint1,
        boolean selectionHasPoint2, long selectionPoint2, long selectionVolume,
        String localRegionName,
        List<RegionEntry> regions,
        List<String> selectionSnapshots,
        boolean previewActive,
        String previewName
) implements CustomPacketPayload {
    public static final int MAX_REGIONS = 512;
    public static final int MAX_SNAPSHOTS = 256;
    public static final Type<RegionSetupOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "region_setup_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RegionSetupOpenPayload> STREAM_CODEC =
            StreamCodec.of(RegionSetupOpenPayload::encode, RegionSetupOpenPayload::decode);

    public RegionSetupOpenPayload {
        mode = PayloadBounds.string(mode, 16);
        notice = PayloadBounds.string(notice, 512);
        requestId = Math.max(0L, requestId);
        regionName = PayloadBounds.string(regionName, 64);
        dimension = PayloadBounds.string(dimension, 128);
        volume = Math.max(0L, volume);
        priority = Math.max(-1_000_000, Math.min(1_000_000, priority));
        welcomeMessage = PayloadBounds.string(welcomeMessage, 256);
        leaveMessage = PayloadBounds.string(leaveMessage, 256);
        rentPrice = PayloadBounds.string(rentPrice, 64);
        rentPeriodDays = Math.max(-1, Math.min(365_000, rentPeriodDays));
        managerCount = Math.max(0, managerCount);
        memberCount = Math.max(0, memberCount);
        resetIntervalSeconds = Math.max(0L, resetIntervalSeconds);
        resetMode = PayloadBounds.string(resetMode, 16);
        presetSummary = PayloadBounds.string(presetSummary, 512);
        selectionDimension = PayloadBounds.string(selectionDimension, 128);
        selectionVolume = Math.max(0L, selectionVolume);
        localRegionName = PayloadBounds.string(localRegionName, 64);
        regions = regions == null ? List.of() : List.copyOf(regions.subList(0, Math.min(MAX_REGIONS, regions.size())));
        selectionSnapshots = selectionSnapshots == null ? List.of()
                : selectionSnapshots.stream().limit(MAX_SNAPSHOTS).map(value -> PayloadBounds.string(value, 64)).toList();
        previewName = PayloadBounds.string(previewName, 64);
    }

    private static void encode(RegistryFriendlyByteBuf b, RegionSetupOpenPayload p) {
        b.writeUtf(p.mode,16); b.writeUtf(p.notice,512); b.writeBoolean(p.error); b.writeVarLong(p.requestId);
        b.writeUtf(p.regionName,64); b.writeUtf(p.dimension,128);
        b.writeBoolean(p.hasPoint1); b.writeLong(p.point1); b.writeBoolean(p.hasPoint2); b.writeLong(p.point2); b.writeVarLong(p.volume);
        b.writeVarInt(p.priority); b.writeBoolean(p.borderVisible);
        b.writeBoolean(p.allowBreak); b.writeBoolean(p.allowPlace); b.writeBoolean(p.allowInteract); b.writeBoolean(p.allowPvp);
        b.writeBoolean(p.allowExplosions); b.writeBoolean(p.allowPistons); b.writeBoolean(p.allowWater); b.writeBoolean(p.allowLava);
        b.writeBoolean(p.allowRedstone); b.writeBoolean(p.allowHoppers); b.writeBoolean(p.allowFireSpread);
        b.writeUtf(p.welcomeMessage,256); b.writeUtf(p.leaveMessage,256);
        b.writeBoolean(p.hasSpawn); b.writeLong(p.spawnPos); b.writeFloat(p.spawnYaw); b.writeFloat(p.spawnPitch);
        b.writeBoolean(p.rentable); b.writeUtf(p.rentPrice,64); b.writeVarInt(p.rentPeriodDays);
        b.writeBoolean(p.resetOnExpire); b.writeBoolean(p.resetOnUnrent);
        b.writeVarInt(p.managerCount); b.writeVarInt(p.memberCount); b.writeBoolean(p.snapshotAvailable);
        b.writeBoolean(p.scheduledResetEnabled); b.writeVarLong(p.resetIntervalSeconds); b.writeUtf(p.resetMode,16);
        b.writeBoolean(p.resetOnlyWhenEmpty); b.writeUtf(p.presetSummary,512); b.writeLong(p.nextResetAt); b.writeLong(p.lastResetAt);
        b.writeBoolean(p.canCreate); b.writeBoolean(p.canEdit); b.writeBoolean(p.canDelete);
        b.writeUtf(p.selectionDimension, 128);
        b.writeBoolean(p.selectionHasPoint1); b.writeLong(p.selectionPoint1);
        b.writeBoolean(p.selectionHasPoint2); b.writeLong(p.selectionPoint2); b.writeVarLong(p.selectionVolume);
        b.writeUtf(p.localRegionName, 64);
        b.writeVarInt(p.regions.size());
        for (RegionEntry entry : p.regions) entry.encode(b);
        b.writeVarInt(p.selectionSnapshots.size());
        for (String name : p.selectionSnapshots) b.writeUtf(name, 64);
        b.writeBoolean(p.previewActive);
        b.writeUtf(p.previewName, 64);
    }

    private static RegionSetupOpenPayload decode(RegistryFriendlyByteBuf b) {
        String mode=b.readUtf(16),notice=b.readUtf(512);boolean error=b.readBoolean();long requestId=b.readVarLong();
        String regionName=b.readUtf(64),dimension=b.readUtf(128);boolean p1=b.readBoolean();long point1=b.readLong();boolean p2=b.readBoolean();long point2=b.readLong();long volume=b.readVarLong();
        int priority=b.readVarInt();boolean border=b.readBoolean();
        boolean allowBreak=b.readBoolean(),allowPlace=b.readBoolean(),allowInteract=b.readBoolean(),allowPvp=b.readBoolean(),allowExplosions=b.readBoolean(),allowPistons=b.readBoolean(),allowWater=b.readBoolean(),allowLava=b.readBoolean(),allowRedstone=b.readBoolean(),allowHoppers=b.readBoolean(),allowFire=b.readBoolean();
        String welcome=b.readUtf(256),leave=b.readUtf(256);boolean hasSpawn=b.readBoolean();long spawn=b.readLong();float yaw=b.readFloat(),pitch=b.readFloat();
        boolean rentable=b.readBoolean();String price=b.readUtf(64);int days=b.readVarInt();boolean expire=b.readBoolean(),unrent=b.readBoolean();
        int managers=b.readVarInt(),members=b.readVarInt();boolean snapshot=b.readBoolean();boolean scheduled=b.readBoolean();long interval=b.readVarLong();String resetMode=b.readUtf(16);boolean empty=b.readBoolean();String preset=b.readUtf(512);long next=b.readLong(),last=b.readLong();
        boolean canCreate=b.readBoolean(),canEdit=b.readBoolean(),canDelete=b.readBoolean();
        String selectionDimension=b.readUtf(128);boolean selectionP1=b.readBoolean();long selectionPoint1=b.readLong();boolean selectionP2=b.readBoolean();long selectionPoint2=b.readLong();long selectionVolume=b.readVarLong();
        String local=b.readUtf(64);
        int regionCount=b.readVarInt();if(regionCount<0||regionCount>MAX_REGIONS)throw new IllegalArgumentException("Invalid region list size: "+regionCount);
        List<RegionEntry> regions=new ArrayList<>(regionCount);for(int i=0;i<regionCount;i++)regions.add(RegionEntry.decode(b));
        int snapshotCount=b.readVarInt();if(snapshotCount<0||snapshotCount>MAX_SNAPSHOTS)throw new IllegalArgumentException("Invalid selection snapshot list size: "+snapshotCount);
        List<String> snapshots=new ArrayList<>(snapshotCount);for(int i=0;i<snapshotCount;i++)snapshots.add(b.readUtf(64));
        boolean previewActive=b.readBoolean();String previewName=b.readUtf(64);
        return new RegionSetupOpenPayload(mode,notice,error,requestId,regionName,dimension,p1,point1,p2,point2,volume,priority,border,
                allowBreak,allowPlace,allowInteract,allowPvp,allowExplosions,allowPistons,allowWater,allowLava,allowRedstone,allowHoppers,allowFire,
                welcome,leave,hasSpawn,spawn,yaw,pitch,rentable,price,days,expire,unrent,managers,members,snapshot,scheduled,interval,resetMode,empty,preset,next,last,
                canCreate,canEdit,canDelete,selectionDimension,selectionP1,selectionPoint1,selectionP2,selectionPoint2,selectionVolume,
                local,regions,snapshots,previewActive,previewName);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record RegionEntry(String name, String dimension, long volume, int priority, boolean hasSpawn, long spawnPos) {
        public RegionEntry {
            name = PayloadBounds.string(name,64);
            dimension = PayloadBounds.string(dimension,128);
            volume = Math.max(0L,volume);
            priority = Math.max(-1_000_000,Math.min(1_000_000,priority));
        }
        private void encode(RegistryFriendlyByteBuf b){b.writeUtf(name,64);b.writeUtf(dimension,128);b.writeVarLong(volume);b.writeVarInt(priority);b.writeBoolean(hasSpawn);b.writeLong(spawnPos);}
        private static RegionEntry decode(RegistryFriendlyByteBuf b){return new RegionEntry(b.readUtf(64),b.readUtf(128),b.readVarLong(),b.readVarInt(),b.readBoolean(),b.readLong());}
    }
}
