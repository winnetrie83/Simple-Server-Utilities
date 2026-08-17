package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimChunkStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative claim and region overlay data plus validated HUD settings. */
public record MinimapDataPayload(
        boolean allowed,
        boolean enabled,
        int size,
        String shape,
        boolean texturedFrame,
        String position,
        boolean northUp,
        boolean showClaims,
        boolean showRegions,
        boolean showCalendar,
        int liveUpdateRadiusChunks,
        String dimension,
        int centerChunkX,
        int centerChunkZ,
        int ownClaimColor,
        int otherClaimColor,
        int regionColor,
        List<ClaimOverlay> claims,
        List<RegionOverlay> regions
) implements CustomPacketPayload {

    private static final int MAX_CLAIMS = 512;
    private static final int MAX_REGIONS = 256;

    public static final Type<MinimapDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minimap_data")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MinimapDataPayload> STREAM_CODEC =
            StreamCodec.of(MinimapDataPayload::encode, MinimapDataPayload::decode);

    public MinimapDataPayload {
        size = Math.max(64, Math.min(256, size));
        shape = "RECTANGLE".equalsIgnoreCase(shape) ? "RECTANGLE" : "CIRCLE";
        position = normalizePosition(position);
        liveUpdateRadiusChunks = Math.max(1, Math.min(32, liveUpdateRadiusChunks));
        dimension = limit(dimension, 128);
        claims = claims == null ? List.of() : List.copyOf(claims);
        regions = regions == null ? List.of() : List.copyOf(regions);
        validateSize(claims.size(), MAX_CLAIMS, "claims");
        validateSize(regions.size(), MAX_REGIONS, "regions");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinimapDataPayload payload) {
        buffer.writeBoolean(payload.allowed());
        buffer.writeBoolean(payload.enabled());
        buffer.writeVarInt(payload.size());
        buffer.writeUtf(payload.shape(), 16);
        buffer.writeBoolean(payload.texturedFrame());
        buffer.writeUtf(payload.position(), 16);
        buffer.writeBoolean(payload.northUp());
        buffer.writeBoolean(payload.showClaims());
        buffer.writeBoolean(payload.showRegions());
        buffer.writeBoolean(payload.showCalendar());
        buffer.writeVarInt(payload.liveUpdateRadiusChunks());
        buffer.writeUtf(payload.dimension(), 128);
        buffer.writeVarInt(payload.centerChunkX());
        buffer.writeVarInt(payload.centerChunkZ());
        buffer.writeInt(payload.ownClaimColor());
        buffer.writeInt(payload.otherClaimColor());
        buffer.writeInt(payload.regionColor());

        buffer.writeVarInt(payload.claims().size());
        for (ClaimOverlay claim : payload.claims()) {
            buffer.writeVarInt(claim.chunkX());
            buffer.writeVarInt(claim.chunkZ());
            buffer.writeEnum(claim.status());
            buffer.writeUUID(claim.claimId());
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

    private static MinimapDataPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean allowed = buffer.readBoolean();
        boolean enabled = buffer.readBoolean();
        int size = buffer.readVarInt();
        String shape = buffer.readUtf(16);
        boolean texturedFrame = buffer.readBoolean();
        String position = buffer.readUtf(16);
        boolean northUp = buffer.readBoolean();
        boolean showClaims = buffer.readBoolean();
        boolean showRegions = buffer.readBoolean();
        boolean showCalendar = buffer.readBoolean();
        int liveUpdateRadiusChunks = buffer.readVarInt();
        String dimension = buffer.readUtf(128);
        int centerChunkX = buffer.readVarInt();
        int centerChunkZ = buffer.readVarInt();
        int ownClaimColor = buffer.readInt();
        int otherClaimColor = buffer.readInt();
        int regionColor = buffer.readInt();

        int claimCount = readSize(buffer, MAX_CLAIMS, "claims");
        List<ClaimOverlay> claims = new ArrayList<>(claimCount);
        for (int i = 0; i < claimCount; i++) {
            claims.add(new ClaimOverlay(
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readEnum(ClaimChunkStatus.class),
                    buffer.readUUID()
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

        return new MinimapDataPayload(
                allowed, enabled, size, shape, texturedFrame, position, northUp, showClaims, showRegions, showCalendar,
                liveUpdateRadiusChunks,
                dimension, centerChunkX, centerChunkZ, ownClaimColor, otherClaimColor, regionColor,
                claims, regions
        );
    }

    private static String normalizePosition(String value) {
        if (value == null) {
            return "TOP_RIGHT";
        }
        return switch (value.toUpperCase(java.util.Locale.ROOT)) {
            case "TOP_LEFT" -> "TOP_LEFT";
            case "BOTTOM_LEFT" -> "BOTTOM_LEFT";
            case "BOTTOM_RIGHT" -> "BOTTOM_RIGHT";
            default -> "TOP_RIGHT";
        };
    }

    private static String limit(String value, int maximumLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength);
    }

    private static int readSize(RegistryFriendlyByteBuf buffer, int maximum, String name) {
        int size = buffer.readVarInt();
        validateSize(size, maximum, name);
        return size;
    }

    private static void validateSize(int size, int maximum, String name) {
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid minimap " + name + " count: " + size);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record ClaimOverlay(int chunkX, int chunkZ, ClaimChunkStatus status, UUID claimId) {
        public ClaimOverlay {
            status = status == null ? ClaimChunkStatus.OWNED_BY_OTHER : status;
            claimId = claimId == null ? new UUID(0L, 0L) : claimId;
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
