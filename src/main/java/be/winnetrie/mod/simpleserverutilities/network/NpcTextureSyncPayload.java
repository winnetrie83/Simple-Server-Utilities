package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.NpcDefinition;
import be.winnetrie.mod.simpleserverutilities.npc.NpcTextureAssetService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded server-to-client binary custom textures for visible SSU NPC definitions. */
public record NpcTextureSyncPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 256;
    public static final Type<NpcTextureSyncPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_texture_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcTextureSyncPayload> STREAM_CODEC =
            StreamCodec.of(NpcTextureSyncPayload::encode, NpcTextureSyncPayload::decode);

    public NpcTextureSyncPayload {
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many NPC texture assets");
    }

    private static void encode(RegistryFriendlyByteBuf b, NpcTextureSyncPayload p) {
        b.writeVarInt(p.entries.size());
        for (Entry entry : p.entries) {
            b.writeUtf(entry.definitionId, 64);
            b.writeUtf(entry.hash, 64);
            b.writeUtf(entry.model, 8);
            b.writeVarInt(entry.png.length);
            b.writeBytes(entry.png);
        }
    }

    private static NpcTextureSyncPayload decode(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid NPC texture asset count");
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String definitionId = b.readUtf(64);
            String hash = b.readUtf(64);
            String model = b.readUtf(8);
            int length = b.readVarInt();
            if (length < 0 || length > NpcTextureAssetService.MAX_TEXTURE_BYTES) {
                throw new IllegalArgumentException("Invalid NPC texture byte length: " + length);
            }
            byte[] png = new byte[length];
            b.readBytes(png);
            entries.add(new Entry(definitionId, hash, model, png));
        }
        return new NpcTextureSyncPayload(entries);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(String definitionId, String hash, String model, byte[] png) {
        public Entry {
            definitionId = NpcDefinition.sanitizeId(definitionId);
            hash = PayloadBounds.string(hash, 64);
            model = "remove".equalsIgnoreCase(model) ? "remove"
                    : ("slim".equalsIgnoreCase(model) ? "slim" : "wide");
            if (png == null || png.length > NpcTextureAssetService.MAX_TEXTURE_BYTES) {
                throw new IllegalArgumentException("Invalid NPC texture payload");
            }
            if (png.length == 0 && !hash.isBlank()) {
                throw new IllegalArgumentException("NPC texture removal payload must use an empty hash");
            }
            png = png.clone();
        }
        @Override public byte[] png() { return png.clone(); }
    }
}
