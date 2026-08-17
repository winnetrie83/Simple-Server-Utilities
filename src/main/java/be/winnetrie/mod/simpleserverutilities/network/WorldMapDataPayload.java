package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimChunkStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative overlays and viewport metadata for the SSU world map. */
public record WorldMapDataPayload(
        boolean allowed,
        String dimension,
        int centerChunkX,
        int centerChunkZ,
        int radius,
        int ownClaimColor,
        int otherClaimColor,
        int regionColor,
        boolean showClaims,
        boolean showRegions,
        List<ClaimOverlay> claims,
        List<RegionOverlay> regions
) implements CustomPacketPayload {

    private static final int MAX_CLAIMS = 8192;
    private static final int MAX_REGIONS = 512;

    public static final Type<WorldMapDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "world_map_data")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, WorldMapDataPayload> STREAM_CODEC =
            StreamCodec.of(WorldMapDataPayload::encode, WorldMapDataPayload::decode);

    public WorldMapDataPayload {
        dimension = limit(dimension, 128);
        radius = Math.max(3, Math.min(32, radius));
        claims = claims == null ? List.of() : List.copyOf(claims);
        regions = regions == null ? List.of() : List.copyOf(regions);
        validateSize(claims.size(), MAX_CLAIMS, "claims");
        validateSize(regions.size(), MAX_REGIONS, "regions");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, WorldMapDataPayload payload) {
        buffer.writeBoolean(payload.allowed());
        buffer.writeUtf(payload.dimension(), 128);
        buffer.writeVarInt(payload.centerChunkX());
        buffer.writeVarInt(payload.centerChunkZ());
        buffer.writeVarInt(payload.radius());
        buffer.writeInt(payload.ownClaimColor());
        buffer.writeInt(payload.otherClaimColor());
        buffer.writeInt(payload.regionColor());
        buffer.writeBoolean(payload.showClaims());
        buffer.writeBoolean(payload.showRegions());

        buffer.writeVarInt(payload.claims().size());
        for (ClaimOverlay claim : payload.claims()) {
            buffer.writeVarInt(claim.chunkX());
            buffer.writeVarInt(claim.chunkZ());
            buffer.writeEnum(claim.status());
        }

        buffer.writeVarInt(payload.regions().size());
        for (RegionOverlay region : payload.regions()) {
            buffer.writeUtf(region.name(), 64);
            buffer.writeVarInt(region.minX());
            buffer.writeVarInt(region.minZ());
            buffer.writeVarInt(region.maxX());
            buffer.writeVarInt(region.maxZ());
        }
    }

    private static WorldMapDataPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean allowed = buffer.readBoolean();
        String dimension = buffer.readUtf(128);
        int centerChunkX = buffer.readVarInt();
        int centerChunkZ = buffer.readVarInt();
        int radius = buffer.readVarInt();
        int ownClaimColor = buffer.readInt();
        int otherClaimColor = buffer.readInt();
        int regionColor = buffer.readInt();
        boolean showClaims = buffer.readBoolean();
        boolean showRegions = buffer.readBoolean();

        int claimCount = readSize(buffer, MAX_CLAIMS, "claims");
        List<ClaimOverlay> claims = new ArrayList<>(claimCount);
        for (int i = 0; i < claimCount; i++) {
            claims.add(new ClaimOverlay(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readEnum(ClaimChunkStatus.class)
            ));
        }

        int regionCount = readSize(buffer, MAX_REGIONS, "regions");
        List<RegionOverlay> regions = new ArrayList<>(regionCount);
        for (int i = 0; i < regionCount; i++) {
            regions.add(new RegionOverlay(
                    buffer.readUtf(64),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            ));
        }

        return new WorldMapDataPayload(
                allowed,
                dimension,
                centerChunkX,
                centerChunkZ,
                radius,
                ownClaimColor,
                otherClaimColor,
                regionColor,
                showClaims,
                showRegions,
                claims,
                regions
        );
    }

    private static int readSize(RegistryFriendlyByteBuf buffer, int maximum, String name) {
        int size = buffer.readVarInt();
        validateSize(size, maximum, name);
        return size;
    }

    private static void validateSize(int size, int maximum, String name) {
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid world-map " + name + " count: " + size);
        }
    }

    private static String limit(String value, int maximumLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record ClaimOverlay(int chunkX, int chunkZ, ClaimChunkStatus status) {
        public ClaimOverlay {
            status = status == null ? ClaimChunkStatus.OWNED_BY_OTHER : status;
        }
    }

    public record RegionOverlay(String name, int minX, int minZ, int maxX, int maxZ) {
        public RegionOverlay {
            name = limit(name, 64);
            int normalizedMinX = Math.min(minX, maxX);
            int normalizedMinZ = Math.min(minZ, maxZ);
            int normalizedMaxX = Math.max(minX, maxX);
            int normalizedMaxZ = Math.max(minZ, maxZ);
            minX = normalizedMinX;
            minZ = normalizedMinZ;
            maxX = normalizedMaxX;
            maxZ = normalizedMaxZ;
        }
    }
}
