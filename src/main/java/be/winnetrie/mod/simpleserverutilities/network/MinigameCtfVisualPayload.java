package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Small live visual state for carried Capture the Flag flags. */
public record MinigameCtfVisualPayload(boolean visible, List<Entry> carriers) implements CustomPacketPayload {
    public static final int MAX_CARRIERS = 16;
    public static final Type<MinigameCtfVisualPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "minigame_ctf_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinigameCtfVisualPayload> STREAM_CODEC =
            StreamCodec.of(MinigameCtfVisualPayload::encode, MinigameCtfVisualPayload::decode);

    public MinigameCtfVisualPayload {
        ArrayList<Entry> safe = new ArrayList<>();
        if (carriers != null) {
            for (Entry entry : carriers) {
                if (entry == null) continue;
                safe.add(entry.normalized());
                if (safe.size() >= MAX_CARRIERS) break;
            }
        }
        carriers = List.copyOf(safe);
    }

    public static MinigameCtfVisualPayload clear() { return new MinigameCtfVisualPayload(false, List.of()); }

    private static void encode(RegistryFriendlyByteBuf buffer, MinigameCtfVisualPayload payload) {
        buffer.writeBoolean(payload.visible);
        buffer.writeVarInt(payload.carriers.size());
        for (Entry entry : payload.carriers) {
            buffer.writeVarInt(entry.entityId);
            buffer.writeVarInt(entry.flagTeam);
            buffer.writeInt(entry.color);
        }
    }

    private static MinigameCtfVisualPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean visible = buffer.readBoolean();
        int count = Math.max(0, Math.min(MAX_CARRIERS, buffer.readVarInt()));
        ArrayList<Entry> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(new Entry(buffer.readVarInt(), buffer.readVarInt(), buffer.readInt()));
        }
        return new MinigameCtfVisualPayload(visible, values);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(int entityId, int flagTeam, int color) {
        private Entry normalized() {
            return new Entry(Math.max(0, entityId), flagTeam == 2 ? 2 : 1, color & 0x00FFFFFF);
        }
    }
}
