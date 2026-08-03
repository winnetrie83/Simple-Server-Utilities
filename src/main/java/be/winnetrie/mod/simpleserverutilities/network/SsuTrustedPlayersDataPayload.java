package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-authoritative trusted-player list and filtered add candidates for one claim. */
public record SsuTrustedPlayersDataPayload(
        String claim,
        String title,
        String search,
        long requestId,
        boolean canEdit,
        String notice,
        boolean error,
        int candidateTotal,
        List<Entry> trusted,
        List<Entry> candidates
) implements CustomPacketPayload {
    private static final int MAX_TRUSTED = 2048;
    private static final int MAX_CANDIDATES = 100;

    public static final Type<SsuTrustedPlayersDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "trusted_players_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuTrustedPlayersDataPayload> STREAM_CODEC =
            StreamCodec.of(SsuTrustedPlayersDataPayload::encode, SsuTrustedPlayersDataPayload::decode);

    public SsuTrustedPlayersDataPayload {
        claim = PayloadBounds.string(claim, 64).trim();
        title = PayloadBounds.string(title, 128);
        search = PayloadBounds.string(search, 64).trim();
        requestId = Math.max(0L, requestId);
        notice = PayloadBounds.string(notice, 256);
        candidateTotal = Math.max(0, candidateTotal);
        trusted = trusted == null ? List.of() : List.copyOf(trusted);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (trusted.size() > MAX_TRUSTED) throw new IllegalArgumentException("Too many trusted players.");
        if (candidates.size() > MAX_CANDIDATES) throw new IllegalArgumentException("Too many player candidates.");
    }

    public static SsuTrustedPlayersDataPayload error(String claim, String search, long requestId, String notice) {
        return new SsuTrustedPlayersDataPayload(claim, "Trusted players", search, requestId,
                false, notice, true, 0, List.of(), List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuTrustedPlayersDataPayload payload) {
        buffer.writeUtf(payload.claim, 64);
        buffer.writeUtf(payload.title, 128);
        buffer.writeUtf(payload.search, 64);
        buffer.writeVarLong(payload.requestId);
        buffer.writeBoolean(payload.canEdit);
        buffer.writeUtf(payload.notice, 256);
        buffer.writeBoolean(payload.error);
        buffer.writeVarInt(payload.candidateTotal);
        writeEntries(buffer, payload.trusted);
        writeEntries(buffer, payload.candidates);
    }

    private static SsuTrustedPlayersDataPayload decode(RegistryFriendlyByteBuf buffer) {
        String claim = buffer.readUtf(64);
        String title = buffer.readUtf(128);
        String search = buffer.readUtf(64);
        long requestId = buffer.readVarLong();
        boolean canEdit = buffer.readBoolean();
        String notice = buffer.readUtf(256);
        boolean error = buffer.readBoolean();
        int candidateTotal = buffer.readVarInt();
        List<Entry> trusted = readEntries(buffer, MAX_TRUSTED);
        List<Entry> candidates = readEntries(buffer, MAX_CANDIDATES);
        return new SsuTrustedPlayersDataPayload(claim, title, search, requestId, canEdit,
                notice, error, candidateTotal, trusted, candidates);
    }

    private static void writeEntries(RegistryFriendlyByteBuf buffer, List<Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.playerId);
            buffer.writeUtf(entry.name, 64);
            buffer.writeBoolean(entry.online);
        }
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer, int maximum) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximum) throw new IllegalArgumentException("Invalid trusted-player list size: " + size);
        List<Entry> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            result.add(new Entry(buffer.readUUID(), buffer.readUtf(64), buffer.readBoolean()));
        }
        return result;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(UUID playerId, String name, boolean online) {
        public Entry {
            playerId = playerId == null ? new UUID(0L, 0L) : playerId;
            name = PayloadBounds.string(name, 64);
        }
    }
}
