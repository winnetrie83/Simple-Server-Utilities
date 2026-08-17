package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TitleManagerDataPayload(
        boolean adminView, String selectedTitleId, List<Entry> titles,
        String notice, boolean error, long requestId
) implements CustomPacketPayload {
    private static final int MAX_TITLES = 512;
    public static final Type<TitleManagerDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "title_manager_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TitleManagerDataPayload> STREAM_CODEC =
            StreamCodec.of(TitleManagerDataPayload::encode, TitleManagerDataPayload::decode);

    public TitleManagerDataPayload {
        selectedTitleId = PayloadBounds.string(selectedTitleId, 64);
        titles = titles == null ? List.of() : List.copyOf(titles);
        if (titles.size() > MAX_TITLES) throw new IllegalArgumentException("Too many title definitions.");
        notice = PayloadBounds.string(notice, 512);
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, TitleManagerDataPayload p) {
        b.writeBoolean(p.adminView); b.writeUtf(p.selectedTitleId, 64); b.writeVarInt(p.titles.size());
        for (Entry e : p.titles) {
            b.writeUtf(e.id, 64); b.writeUtf(e.displayName, 48); b.writeInt(e.color);
            b.writeUtf(e.unlockType, 32); b.writeVarLong(e.requirement); b.writeUtf(e.requirementValue, 128);
            b.writeBoolean(e.enabled); b.writeBoolean(e.unlocked); b.writeBoolean(e.selected);
            b.writeUtf(e.acquisition, 192);
        }
        b.writeUtf(p.notice, 512); b.writeBoolean(p.error); b.writeVarLong(p.requestId);
    }

    private static TitleManagerDataPayload decode(RegistryFriendlyByteBuf b) {
        boolean admin = b.readBoolean(); String selected = b.readUtf(64); int count = b.readVarInt();
        if (count < 0 || count > MAX_TITLES) throw new IllegalArgumentException("Invalid title count: " + count);
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(new Entry(b.readUtf(64), b.readUtf(48), b.readInt(),
                b.readUtf(32), b.readVarLong(), b.readUtf(128), b.readBoolean(), b.readBoolean(),
                b.readBoolean(), b.readUtf(192)));
        return new TitleManagerDataPayload(admin, selected, entries, b.readUtf(512), b.readBoolean(), b.readVarLong());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Entry(String id, String displayName, int color, String unlockType, long requirement,
                        String requirementValue, boolean enabled, boolean unlocked, boolean selected,
                        String acquisition) {
        public Entry {
            id = PayloadBounds.string(id, 64); displayName = PayloadBounds.string(displayName, 48);
            unlockType = PayloadBounds.string(unlockType, 32); requirement = Math.max(0L, requirement);
            requirementValue = PayloadBounds.string(requirementValue, 128);
            acquisition = PayloadBounds.string(acquisition, 192);
        }
    }
}
