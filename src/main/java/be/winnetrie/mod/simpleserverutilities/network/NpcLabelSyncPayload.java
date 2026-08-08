package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.NpcAttitude;
import be.winnetrie.mod.simpleserverutilities.npc.NpcRole;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Lightweight client snapshot for the three-line SSU NPC overhead identity label. */
public record NpcLabelSyncPayload(List<Entry> entries) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 2_048;

    public static final Type<NpcLabelSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_label_sync"));
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
            buffer.writeUtf(entry.roleId, 32);
            buffer.writeUtf(entry.factionName, 64);
            buffer.writeUtf(entry.attitude, 16);
        }
    }

    private static NpcLabelSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid NPC label count: " + count);
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readVarInt(), buffer.readUtf(36), buffer.readUtf(64), buffer.readBoolean(), buffer.readUtf(64),
                    buffer.readUtf(32), buffer.readUtf(64), buffer.readUtf(16)));
        }
        return new NpcLabelSyncPayload(entries);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(int entityId, String entityUuid, String definitionId, boolean labelVisible, String displayName,
                        String roleId, String factionName, String attitude) {
        public Entry {
            entityId = Math.max(0, entityId);
            entityUuid = PayloadBounds.string(entityUuid, 36);
            definitionId = PayloadBounds.string(definitionId, 64);
            displayName = PayloadBounds.string(displayName, 64);
            roleId = NpcRole.parse(roleId).id();
            factionName = PayloadBounds.string(factionName, 64);
            attitude = NpcAttitude.parse(attitude).id();
        }
    }


}
