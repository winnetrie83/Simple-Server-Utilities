package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Viewer-personalized, bounded living-entity set used by Entity Insight. */
public record EntityInsightPayload(
        boolean enabled,
        boolean showHealth,
        int range,
        int maxEntities,
        List<Entry> entries
) implements CustomPacketPayload {
    private static final int HARD_MAX_ENTRIES = 50;

    public static final Type<EntityInsightPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "entity_insight")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityInsightPayload> STREAM_CODEC =
            StreamCodec.of(EntityInsightPayload::encode, EntityInsightPayload::decode);

    public EntityInsightPayload {
        range = Math.max(0, Math.min(32, range));
        maxEntities = Math.max(1, Math.min(HARD_MAX_ENTRIES, maxEntities));
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.size() > HARD_MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many Entity Insight entries: " + entries.size());
        }
    }

    public static EntityInsightPayload disabled(boolean showHealth, int range, int maxEntities) {
        return new EntityInsightPayload(false, showHealth, range, maxEntities, List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, EntityInsightPayload payload) {
        buffer.writeBoolean(payload.enabled);
        buffer.writeBoolean(payload.showHealth);
        buffer.writeVarInt(payload.range);
        buffer.writeVarInt(payload.maxEntities);
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeVarInt(entry.entityId);
            buffer.writeUUID(entry.entityUuid);
            buffer.writeByte(entry.attitude.wireId);
        }
    }

    private static EntityInsightPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        boolean showHealth = buffer.readBoolean();
        int range = buffer.readVarInt();
        int maxEntities = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > HARD_MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid Entity Insight entry count: " + count);
        }
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readVarInt(), buffer.readUUID(), Attitude.fromWireId(buffer.readByte())));
        }
        return new EntityInsightPayload(enabled, showHealth, range, maxEntities, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(int entityId, UUID entityUuid, Attitude attitude) {
        public Entry {
            entityId = Math.max(0, entityId);
            entityUuid = entityUuid == null ? new UUID(0L, 0L) : entityUuid;
            attitude = attitude == null ? Attitude.FRIENDLY : attitude;
        }
    }

    public enum Attitude {
        FRIENDLY(0, 0x55FF55),
        NEUTRAL(1, 0xFFFF55),
        HOSTILE(2, 0xFF5555),
        FLEEING(3, 0x55FFFF);

        private final int wireId;
        private final int rgb;

        Attitude(int wireId, int rgb) {
            this.wireId = wireId;
            this.rgb = rgb;
        }

        public int rgb() {
            return rgb;
        }

        public static Attitude fromWireId(int id) {
            return switch (id) {
                case 1 -> NEUTRAL;
                case 2 -> HOSTILE;
                case 3 -> FLEEING;
                default -> FRIENDLY;
            };
        }
    }
}
