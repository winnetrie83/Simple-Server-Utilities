package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimChunkStatus;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapChunk;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ClaimMapDataPayload(
        int centerChunkX,
        int centerChunkZ,
        int radius,
        String selectedClaimGroup,
        List<Entry> chunks
) implements CustomPacketPayload {

    public static final Type<ClaimMapDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "claim_map_data")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimMapDataPayload> STREAM_CODEC =
            StreamCodec.of(ClaimMapDataPayload::encode, ClaimMapDataPayload::decode);

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

        return new ClaimMapDataPayload(
                data.getCenterChunkX(),
                data.getCenterChunkZ(),
                data.getRadius(),
                data.getSelectedClaimGroup(),
                entries
        );
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClaimMapDataPayload payload) {
        buffer.writeVarInt(payload.centerChunkX);
        buffer.writeVarInt(payload.centerChunkZ);
        buffer.writeVarInt(payload.radius);
        buffer.writeUtf(payload.selectedClaimGroup);

        buffer.writeVarInt(payload.chunks.size());

        for (Entry entry : payload.chunks) {
            buffer.writeVarInt(entry.chunkX);
            buffer.writeVarInt(entry.chunkZ);
            buffer.writeEnum(entry.status);
            buffer.writeUtf(entry.claimName == null ? "" : entry.claimName);

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
        String selectedClaimGroup = buffer.readUtf();

        int size = buffer.readVarInt();
        List<Entry> chunks = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            int chunkX = buffer.readVarInt();
            int chunkZ = buffer.readVarInt();
            ClaimChunkStatus status = buffer.readEnum(ClaimChunkStatus.class);
            String claimName = buffer.readUtf();

            UUID owner = null;
            if (buffer.readBoolean()) {
                owner = buffer.readUUID();
            }

            boolean currentChunk = buffer.readBoolean();
            boolean canClaim = buffer.readBoolean();
            boolean canUnclaim = buffer.readBoolean();

            chunks.add(new Entry(
                    chunkX,
                    chunkZ,
                    status,
                    claimName,
                    owner,
                    currentChunk,
                    canClaim,
                    canUnclaim
            ));
        }

        return new ClaimMapDataPayload(
                centerChunkX,
                centerChunkZ,
                radius,
                selectedClaimGroup,
                chunks
        );
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