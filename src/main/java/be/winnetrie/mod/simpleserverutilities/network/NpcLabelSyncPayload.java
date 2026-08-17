package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttitude;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Lightweight client snapshot for the SSU NPC overhead identity/quest label. */
public record NpcLabelSyncPayload(List<Entry> entries) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 2_048;

    public static final Type<NpcLabelSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_label_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcLabelSyncPayload> STREAM_CODEC =
            StreamCodec.of(NpcLabelSyncPayload::encode, NpcLabelSyncPayload::decode);

    public NpcLabelSyncPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many NPC labels: " + entries.size());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, NpcLabelSyncPayload payload) {
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeVarInt(entry.entityId);
            buffer.writeUtf(entry.entityUuid, 36);
            buffer.writeUtf(entry.definitionId, 64);
            buffer.writeBoolean(entry.labelVisible);
            buffer.writeUtf(entry.displayName, 64);
            buffer.writeUtf(entry.roleId, 64);
            buffer.writeVarInt(entry.roleColor);
            buffer.writeUtf(entry.factionName, 64);
            buffer.writeUtf(entry.attitude, 16);
            buffer.writeUtf(entry.questMarker, 8);
        }
    }

    private static NpcLabelSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid NPC label count: " + count);
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readVarInt(), buffer.readUtf(36), buffer.readUtf(64), buffer.readBoolean(), buffer.readUtf(64),
                    buffer.readUtf(64), buffer.readVarInt(), buffer.readUtf(64), buffer.readUtf(16), buffer.readUtf(8)));
        }
        return new NpcLabelSyncPayload(entries);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(int entityId, String entityUuid, String definitionId, boolean labelVisible, String displayName,
                        String roleId, int roleColor, String factionName, String attitude, String questMarker) {
        public Entry {
            entityId = Math.max(0, entityId);
            entityUuid = PayloadBounds.string(entityUuid, 36);
            definitionId = PayloadBounds.string(definitionId, 64);
            displayName = PayloadBounds.string(displayName, 64);
            roleId = PayloadBounds.string(roleId, 64);
            roleColor = Math.max(0, Math.min(15, roleColor));
            factionName = PayloadBounds.string(factionName, 64);
            attitude = NpcAttitude.parse(attitude).id();
            questMarker = PayloadBounds.string(questMarker, 8);
        }
    }


}
