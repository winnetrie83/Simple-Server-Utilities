package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapOperation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClaimMapActionPayload(
        ClaimMapOperation operation,
        String claimName,
        int centerChunkX,
        int centerChunkZ,
        int radius,
        List<ChunkCoordinate> chunks
) implements CustomPacketPayload {

    private static final int MAX_SELECTION_SIZE = 256;

    public static final Type<ClaimMapActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "claim_map_action")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimMapActionPayload> STREAM_CODEC =
            StreamCodec.of(ClaimMapActionPayload::encode, ClaimMapActionPayload::decode);

    public ClaimMapActionPayload {
        operation = operation == null ? ClaimMapOperation.ADD : operation;
        claimName = claimName == null ? "" : claimName;
        radius = Math.max(2, Math.min(radius, 12));
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        if (chunks.size() > MAX_SELECTION_SIZE) {
            throw new IllegalArgumentException("Claim map selection exceeds " + MAX_SELECTION_SIZE + " chunks.");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClaimMapActionPayload payload) {
        buffer.writeEnum(payload.operation);
        buffer.writeUtf(payload.claimName, 64);
        buffer.writeVarInt(payload.centerChunkX);
        buffer.writeVarInt(payload.centerChunkZ);
        buffer.writeVarInt(payload.radius);
        buffer.writeVarInt(payload.chunks.size());
        for (ChunkCoordinate chunk : payload.chunks) {
            buffer.writeVarInt(chunk.x());
            buffer.writeVarInt(chunk.z());
        }
    }

    private static ClaimMapActionPayload decode(RegistryFriendlyByteBuf buffer) {
        ClaimMapOperation operation = buffer.readEnum(ClaimMapOperation.class);
        String claimName = buffer.readUtf(64);
        int centerChunkX = buffer.readVarInt();
        int centerChunkZ = buffer.readVarInt();
        int radius = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_SELECTION_SIZE) {
            throw new IllegalArgumentException("Invalid claim map selection size: " + size);
        }
        List<ChunkCoordinate> chunks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            chunks.add(new ChunkCoordinate(buffer.readVarInt(), buffer.readVarInt()));
        }
        return new ClaimMapActionPayload(operation, claimName, centerChunkX, centerChunkZ, radius, chunks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record ChunkCoordinate(int x, int z) {
    }
}
