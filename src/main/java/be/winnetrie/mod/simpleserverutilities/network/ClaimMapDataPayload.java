package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimChunkStatus;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapChunk;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapData;
import be.winnetrie.mod.simpleserverutilities.visualization.BorderCategory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClaimMapDataPayload(
        int centerChunkX,
        int centerChunkZ,
        int radius,
        String selectedClaimGroup,
        List<String> ownedClaimGroups,
        int usedChunks,
        int maxChunks,
        int usedClaimGroups,
        int maxClaimGroups,
        int selectedClaimChunks,
        int maxChunksPerClaim,
        boolean canCreateClaims,
        boolean selectedClaimTaxSettlementRequired,
        String selectedClaimTaxEstimate,
        int selectedClaimTaxPeakChunks,
        long selectedClaimTaxDueAt,
        int confiscatedChunks,
        int ownClaimColor,
        int otherClaimColor,
        int regionColor,
        int selectionColor,
        String notice,
        boolean error,
        List<Entry> chunks
) implements CustomPacketPayload {

    private static final int MAX_CHUNKS = 625;
    private static final int MAX_CLAIMS = 256;

    public static final Type<ClaimMapDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "claim_map_data")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimMapDataPayload> STREAM_CODEC =
            StreamCodec.of(ClaimMapDataPayload::encode, ClaimMapDataPayload::decode);

    public ClaimMapDataPayload {
        selectedClaimGroup = selectedClaimGroup == null ? "" : selectedClaimGroup;
        ownedClaimGroups = ownedClaimGroups == null ? List.of() : List.copyOf(ownedClaimGroups);
        selectedClaimTaxEstimate = selectedClaimTaxEstimate == null ? "" : selectedClaimTaxEstimate;
        notice = notice == null ? "" : notice;
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        if (chunks.size() > MAX_CHUNKS || ownedClaimGroups.size() > MAX_CLAIMS) {
            throw new IllegalArgumentException("Claim map payload exceeds safe limits.");
        }
    }

    public static ClaimMapDataPayload from(ClaimMapData data) {
        List<Entry> entries = new ArrayList<>();
        for (ClaimMapChunk chunk : data.getChunks()) {
            entries.add(new Entry(
                    chunk.getChunkX(),
                    chunk.getChunkZ(),
                    chunk.getStatus(),
                    chunk.getClaimName(),
                    chunk.getOwner(),
                    chunk.isCurrentChunk(),
                    chunk.canClaim(),
                    chunk.canUnclaim()
            ));
        }

        var borderSettings = SimpleServerUtilities.BORDER_SETTINGS.settings();
        return new ClaimMapDataPayload(
                data.getCenterChunkX(),
                data.getCenterChunkZ(),
                data.getRadius(),
                data.getSelectedClaimGroup(),
                data.getOwnedClaimGroups(),
                data.getUsedChunks(),
                data.getMaxChunks(),
                data.getUsedClaimGroups(),
                data.getMaxClaimGroups(),
                data.getSelectedClaimChunks(),
                data.getMaxChunksPerClaim(),
                data.canCreateClaims(),
                data.isSelectedClaimTaxSettlementRequired(),
                data.getSelectedClaimTaxEstimate(),
                data.getSelectedClaimTaxPeakChunks(),
                data.getSelectedClaimTaxDueAt(),
                data.getConfiscatedChunks(),
                borderSettings.getStrokeArgb(BorderCategory.OWN_CLAIM),
                borderSettings.getStrokeArgb(BorderCategory.OTHER_CLAIM),
                borderSettings.getStrokeArgb(BorderCategory.SERVER_REGION),
                borderSettings.getStrokeArgb(BorderCategory.SELECTION),
                data.getNotice(),
                data.isError(),
                entries
        );
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClaimMapDataPayload payload) {
        buffer.writeVarInt(payload.centerChunkX);
        buffer.writeVarInt(payload.centerChunkZ);
        buffer.writeVarInt(payload.radius);
        buffer.writeUtf(payload.selectedClaimGroup, 64);

        buffer.writeVarInt(payload.ownedClaimGroups.size());
        for (String claim : payload.ownedClaimGroups) {
            buffer.writeUtf(claim, 64);
        }

        buffer.writeVarInt(payload.usedChunks);
        buffer.writeVarInt(payload.maxChunks);
        buffer.writeVarInt(payload.usedClaimGroups);
        buffer.writeVarInt(payload.maxClaimGroups);
        buffer.writeVarInt(payload.selectedClaimChunks);
        buffer.writeVarInt(payload.maxChunksPerClaim);
        buffer.writeBoolean(payload.canCreateClaims);
        buffer.writeBoolean(payload.selectedClaimTaxSettlementRequired);
        buffer.writeUtf(payload.selectedClaimTaxEstimate, 64);
        buffer.writeVarInt(payload.selectedClaimTaxPeakChunks);
        buffer.writeVarLong(payload.selectedClaimTaxDueAt);
        buffer.writeVarInt(payload.confiscatedChunks);
        buffer.writeInt(payload.ownClaimColor);
        buffer.writeInt(payload.otherClaimColor);
        buffer.writeInt(payload.regionColor);
        buffer.writeInt(payload.selectionColor);
        buffer.writeUtf(payload.notice, 256);
        buffer.writeBoolean(payload.error);

        buffer.writeVarInt(payload.chunks.size());
        for (Entry entry : payload.chunks) {
            buffer.writeVarInt(entry.chunkX);
            buffer.writeVarInt(entry.chunkZ);
            buffer.writeEnum(entry.status);
            buffer.writeUtf(entry.claimName == null ? "" : entry.claimName, 64);
            buffer.writeBoolean(entry.owner != null);
            if (entry.owner != null) {
                buffer.writeUUID(entry.owner);
            }
            buffer.writeBoolean(entry.currentChunk);
            buffer.writeBoolean(entry.canClaim);
            buffer.writeBoolean(entry.canUnclaim);
        }
    }

    private static ClaimMapDataPayload decode(RegistryFriendlyByteBuf buffer) {
        int centerChunkX = buffer.readVarInt();
        int centerChunkZ = buffer.readVarInt();
        int radius = buffer.readVarInt();
        String selectedClaimGroup = buffer.readUtf(64);

        int claimCount = readSize(buffer, MAX_CLAIMS, "owned claims");
        List<String> ownedClaims = new ArrayList<>(claimCount);
        for (int i = 0; i < claimCount; i++) {
            ownedClaims.add(buffer.readUtf(64));
        }

        int usedChunks = buffer.readVarInt();
        int maxChunks = buffer.readVarInt();
        int usedClaimGroups = buffer.readVarInt();
        int maxClaimGroups = buffer.readVarInt();
        int selectedClaimChunks = buffer.readVarInt();
        int maxChunksPerClaim = buffer.readVarInt();
        boolean canCreateClaims = buffer.readBoolean();
        boolean selectedClaimTaxSettlementRequired = buffer.readBoolean();
        String selectedClaimTaxEstimate = buffer.readUtf(64);
        int selectedClaimTaxPeakChunks = buffer.readVarInt();
        long selectedClaimTaxDueAt = buffer.readVarLong();
        int confiscatedChunks = buffer.readVarInt();
        int ownClaimColor = buffer.readInt();
        int otherClaimColor = buffer.readInt();
        int regionColor = buffer.readInt();
        int selectionColor = buffer.readInt();
        String notice = buffer.readUtf(256);
        boolean error = buffer.readBoolean();

        int size = readSize(buffer, MAX_CHUNKS, "chunks");
        List<Entry> chunks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int chunkX = buffer.readVarInt();
            int chunkZ = buffer.readVarInt();
            ClaimChunkStatus status = buffer.readEnum(ClaimChunkStatus.class);
            String claimName = buffer.readUtf(64);
            UUID owner = buffer.readBoolean() ? buffer.readUUID() : null;
            chunks.add(new Entry(
                    chunkX,
                    chunkZ,
                    status,
                    claimName,
                    owner,
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            ));
        }

        return new ClaimMapDataPayload(
                centerChunkX,
                centerChunkZ,
                radius,
                selectedClaimGroup,
                ownedClaims,
                usedChunks,
                maxChunks,
                usedClaimGroups,
                maxClaimGroups,
                selectedClaimChunks,
                maxChunksPerClaim,
                canCreateClaims,
                selectedClaimTaxSettlementRequired,
                selectedClaimTaxEstimate,
                selectedClaimTaxPeakChunks,
                selectedClaimTaxDueAt,
                confiscatedChunks,
                ownClaimColor,
                otherClaimColor,
                regionColor,
                selectionColor,
                notice,
                error,
                chunks
        );
    }

    private static int readSize(RegistryFriendlyByteBuf buffer, int max, String name) {
        int size = buffer.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid claim map " + name + " count: " + size);
        }
        return size;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            int chunkX,
            int chunkZ,
            ClaimChunkStatus status,
            String claimName,
            UUID owner,
            boolean currentChunk,
            boolean canClaim,
            boolean canUnclaim
    ) {
    }
}
