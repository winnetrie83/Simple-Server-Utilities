package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Live world labels and two-tone assault visuals for Domination nodes. */
public record MinigameDominationVisualPayload(boolean visible, List<Entry> nodes)
        implements CustomPacketPayload {
    public static final int MAX_NODES = 9;
    public static final Type<MinigameDominationVisualPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_domination_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameDominationVisualPayload> STREAM_CODEC =
            StreamCodec.of(MinigameDominationVisualPayload::encode, MinigameDominationVisualPayload::decode);

    public MinigameDominationVisualPayload {
        ArrayList<Entry> safe = new ArrayList<>();
        if (nodes != null) {
            for (Entry entry : nodes) {
                if (entry == null) continue;
                safe.add(entry.normalized());
                if (safe.size() >= MAX_NODES) break;
            }
        }
        nodes = List.copyOf(safe);
    }

    public static MinigameDominationVisualPayload clear() {
        return new MinigameDominationVisualPayload(false, List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameDominationVisualPayload payload) {
        buffer.writeBoolean(payload.visible);
        buffer.writeVarInt(payload.nodes.size());
        for (Entry entry : payload.nodes) {
            buffer.writeUtf(entry.dimension, 128);
            buffer.writeDouble(entry.x);
            buffer.writeDouble(entry.y);
            buffer.writeDouble(entry.z);
            buffer.writeUtf(entry.label, 96);
            buffer.writeInt(entry.baseColor);
            buffer.writeInt(entry.topColor);
            buffer.writeBoolean(entry.assaulted);
        }
    }

    private static MinigameDominationVisualPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean visible = buffer.readBoolean();
        int count = Math.max(0, Math.min(MAX_NODES, buffer.readVarInt()));
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new Entry(buffer.readUtf(128), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readUtf(96), buffer.readInt(), buffer.readInt(),
                    buffer.readBoolean()));
        }
        return new MinigameDominationVisualPayload(visible, entries);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(String dimension, double x, double y, double z, String label,
                        int baseColor, int topColor, boolean assaulted) {
        private Entry normalized() {
            return new Entry(PayloadBounds.string(dimension, 128), x, y, z,
                    PayloadBounds.string(label, 96), baseColor & 0x00FFFFFF,
                    topColor & 0x00FFFFFF, assaulted);
        }
    }
}
