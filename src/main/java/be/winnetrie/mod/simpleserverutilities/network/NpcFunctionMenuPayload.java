package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded player-facing service menu generated from one NPC definition. */
public record NpcFunctionMenuPayload(String instanceId, String npcName, String roleLabel,
                                     List<Entry> entries) implements CustomPacketPayload {
    public static final Type<NpcFunctionMenuPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_function_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcFunctionMenuPayload> STREAM_CODEC =
            StreamCodec.of(NpcFunctionMenuPayload::encode, NpcFunctionMenuPayload::decode);

    public NpcFunctionMenuPayload {
        instanceId = PayloadBounds.string(instanceId, 36);
        npcName = PayloadBounds.string(npcName, 64);
        roleLabel = PayloadBounds.string(roleLabel, 64);
        List<Entry> safe = new ArrayList<>();
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry != null) safe.add(entry);
                if (safe.size() >= 8) break;
            }
        }
        entries = List.copyOf(safe);
    }

    private static void encode(RegistryFriendlyByteBuf b, NpcFunctionMenuPayload p) {
        b.writeUtf(p.instanceId, 36); b.writeUtf(p.npcName, 64); b.writeUtf(p.roleLabel, 64);
        b.writeVarInt(p.entries.size());
        for (Entry entry : p.entries) {
            b.writeUtf(entry.id, 64); b.writeUtf(entry.label, 64); b.writeBoolean(entry.available);
            b.writeUtf(entry.reason, 256);
        }
    }

    private static NpcFunctionMenuPayload decode(RegistryFriendlyByteBuf b) {
        String instance = b.readUtf(36), name = b.readUtf(64), role = b.readUtf(64);
        int count = b.readVarInt();
        if (count < 0 || count > 8) throw new IllegalArgumentException("Invalid NPC function count");
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(new Entry(
                b.readUtf(64), b.readUtf(64), b.readBoolean(), b.readUtf(256)));
        return new NpcFunctionMenuPayload(instance, name, role, entries);
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(String id, String label, boolean available, String reason) {
        public Entry {
            id = PayloadBounds.string(id, 64); label = PayloadBounds.string(label, 64); reason = PayloadBounds.string(reason, 256);
        }
    }
}
