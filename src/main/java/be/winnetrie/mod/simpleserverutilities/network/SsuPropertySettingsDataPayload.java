package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SsuPropertySettingsDataPayload(
        String kind, String target, String title, long requestId, boolean canEdit,
        String notice, boolean error, List<Entry> entries
) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 40;
    public static final Type<SsuPropertySettingsDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "property_settings_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuPropertySettingsDataPayload> STREAM_CODEC =
            StreamCodec.of(SsuPropertySettingsDataPayload::encode, SsuPropertySettingsDataPayload::decode);

    public SsuPropertySettingsDataPayload {
        kind = bounded(kind, 16); target = bounded(target, 64); title = bounded(title, 128);
        requestId = Math.max(0L, requestId); notice = bounded(notice, 512);
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many property settings entries.");
    }
    public static SsuPropertySettingsDataPayload error(String kind, String target, long id, String notice) {
        return new SsuPropertySettingsDataPayload(kind, target, "Settings", id, false, notice, true, List.of());
    }
    private static void encode(RegistryFriendlyByteBuf b, SsuPropertySettingsDataPayload p) {
        b.writeUtf(p.kind,16); b.writeUtf(p.target,64); b.writeUtf(p.title,128); b.writeVarLong(p.requestId);
        b.writeBoolean(p.canEdit); b.writeUtf(p.notice,512); b.writeBoolean(p.error); b.writeVarInt(p.entries.size());
        for (Entry e : p.entries) {
            b.writeUtf(e.key,64); b.writeUtf(e.label,96); b.writeUtf(e.value,256); b.writeUtf(e.type,16);
            b.writeUtf(e.description,512); b.writeUtf(e.defaultValue,128); b.writeLong(e.minimum); b.writeLong(e.maximum);
            b.writeBoolean(e.editable);
        }
    }
    private static SsuPropertySettingsDataPayload decode(RegistryFriendlyByteBuf b) {
        String kind=b.readUtf(16), target=b.readUtf(64), title=b.readUtf(128); long id=b.readVarLong();
        boolean canEdit=b.readBoolean(); String notice=b.readUtf(512); boolean error=b.readBoolean();
        int size=b.readVarInt(); if(size<0||size>MAX_ENTRIES)throw new IllegalArgumentException("Invalid property settings size: "+size);
        List<Entry> entries=new ArrayList<>(size); for(int i=0;i<size;i++) entries.add(new Entry(
                b.readUtf(64),b.readUtf(96),b.readUtf(256),b.readUtf(16),b.readUtf(512),b.readUtf(128),
                b.readLong(),b.readLong(),b.readBoolean()));
        return new SsuPropertySettingsDataPayload(kind,target,title,id,canEdit,notice,error,entries);
    }
    private static String bounded(String value,int max){String safe=value==null?"":value;return safe.length()<=max?safe:safe.substring(0,max);}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}

    public record Entry(String key,String label,String value,String type,String description,String defaultValue,
                        long minimum,long maximum,boolean editable) {
        public Entry { key=bounded(key,64); label=bounded(label,96); value=bounded(value,256); type=bounded(type,16);
            description=bounded(description,512); defaultValue=bounded(defaultValue,128);
            if(maximum<minimum){long swap=minimum;minimum=maximum;maximum=swap;} }
    }
}
