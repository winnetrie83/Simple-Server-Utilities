package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SsuMenuSnapshotPayload(
        boolean administrator,
        boolean claimBordersVisible,
        boolean regionBordersVisible,
        boolean canViewClaimBorders,
        boolean canViewRegionBorders,
        int activeJobs,
        int pendingStorageWrites,
        List<ClaimSummary> claims,
        List<RegionSummary> regions,
        List<LocationSummary> homes,
        List<LocationSummary> warps
) implements CustomPacketPayload {

    private static final int MAX_LIST_SIZE = 4096;

    public static final Type<SsuMenuSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "menu_snapshot")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SsuMenuSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(SsuMenuSnapshotPayload::encode, SsuMenuSnapshotPayload::decode);

    public SsuMenuSnapshotPayload {
        claims = claims == null ? List.of() : List.copyOf(claims);
        regions = regions == null ? List.of() : List.copyOf(regions);
        homes = homes == null ? List.of() : List.copyOf(homes);
        warps = warps == null ? List.of() : List.copyOf(warps);
        validateSize(claims.size(), "claims");
        validateSize(regions.size(), "regions");
        validateSize(homes.size(), "homes");
        validateSize(warps.size(), "warps");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuMenuSnapshotPayload payload) {
        buffer.writeBoolean(payload.administrator);
        buffer.writeBoolean(payload.claimBordersVisible);
        buffer.writeBoolean(payload.regionBordersVisible);
        buffer.writeBoolean(payload.canViewClaimBorders);
        buffer.writeBoolean(payload.canViewRegionBorders);
        buffer.writeVarInt(payload.activeJobs);
        buffer.writeVarInt(payload.pendingStorageWrites);

        writeClaims(buffer, payload.claims);
        writeRegions(buffer, payload.regions);
        writeLocations(buffer, payload.homes);
        writeLocations(buffer, payload.warps);
    }

    private static SsuMenuSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SsuMenuSnapshotPayload(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readClaims(buffer),
                readRegions(buffer),
                readLocations(buffer),
                readLocations(buffer)
        );
    }

    private static void writeClaims(RegistryFriendlyByteBuf buffer, List<ClaimSummary> claims) {
        buffer.writeVarInt(claims.size());
        for (ClaimSummary claim : claims) {
            buffer.writeUtf(claim.name());
            buffer.writeUtf(claim.dimension());
            buffer.writeVarInt(claim.chunkCount());
        }
    }

    private static List<ClaimSummary> readClaims(RegistryFriendlyByteBuf buffer) {
        int size = readSize(buffer, "claims");
        List<ClaimSummary> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new ClaimSummary(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt()));
        }
        return result;
    }

    private static void writeRegions(RegistryFriendlyByteBuf buffer, List<RegionSummary> regions) {
        buffer.writeVarInt(regions.size());
        for (RegionSummary region : regions) {
            buffer.writeUtf(region.name());
            buffer.writeUtf(region.dimension());
            buffer.writeUtf(region.bounds());
            buffer.writeBoolean(region.visible());
            buffer.writeBoolean(region.rented());
        }
    }

    private static List<RegionSummary> readRegions(RegistryFriendlyByteBuf buffer) {
        int size = readSize(buffer, "regions");
        List<RegionSummary> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new RegionSummary(
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            ));
        }
        return result;
    }

    private static void writeLocations(RegistryFriendlyByteBuf buffer, List<LocationSummary> locations) {
        buffer.writeVarInt(locations.size());
        for (LocationSummary location : locations) {
            buffer.writeUtf(location.name());
            buffer.writeUtf(location.dimension());
            buffer.writeDouble(location.x());
            buffer.writeDouble(location.y());
            buffer.writeDouble(location.z());
        }
    }

    private static List<LocationSummary> readLocations(RegistryFriendlyByteBuf buffer) {
        int size = readSize(buffer, "locations");
        List<LocationSummary> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new LocationSummary(
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            ));
        }
        return result;
    }

    private static int readSize(RegistryFriendlyByteBuf buffer, String name) {
        int size = buffer.readVarInt();
        validateSize(size, name);
        return size;
    }

    private static void validateSize(int size, String name) {
        if (size < 0 || size > MAX_LIST_SIZE) {
            throw new IllegalArgumentException("Invalid SSU menu " + name + " count: " + size);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record ClaimSummary(String name, String dimension, int chunkCount) {
        public ClaimSummary {
            name = name == null ? "" : name;
            dimension = dimension == null ? "" : dimension;
            chunkCount = Math.max(0, chunkCount);
        }
    }

    public record RegionSummary(String name, String dimension, String bounds, boolean visible, boolean rented) {
        public RegionSummary {
            name = name == null ? "" : name;
            dimension = dimension == null ? "" : dimension;
            bounds = bounds == null ? "" : bounds;
        }
    }

    public record LocationSummary(String name, String dimension, double x, double y, double z) {
        public LocationSummary {
            name = name == null ? "" : name;
            dimension = dimension == null ? "" : dimension;
        }
    }
}
