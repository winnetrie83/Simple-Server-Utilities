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
    private static final int MAX_OPTIONS = 100;
    public static final Type<SsuPropertySettingsDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "property_settings_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuPropertySettingsDataPayload> STREAM_CODEC =
            StreamCodec.of(SsuPropertySettingsDataPayload::encode, SsuPropertySettingsDataPayload::decode);

    public SsuPropertySettingsDataPayload {
        kind = PayloadBounds.string(kind, 16);
        target = PayloadBounds.string(target, 64);
        title = PayloadBounds.string(title, 128);
        requestId = Math.max(0L, requestId);
        notice = PayloadBounds.string(notice, 512);
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Too many property settings entries.");
        }
    }

    public static SsuPropertySettingsDataPayload error(String kind, String target, long id, String notice) {
        return new SsuPropertySettingsDataPayload(kind, target, "Settings", id, false, notice, true, List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuPropertySettingsDataPayload payload) {
        buffer.writeUtf(payload.kind, 16);
        buffer.writeUtf(payload.target, 64);
        buffer.writeUtf(payload.title, 128);
        buffer.writeVarLong(payload.requestId);
        buffer.writeBoolean(payload.canEdit);
        buffer.writeUtf(payload.notice, 512);
        buffer.writeBoolean(payload.error);
        buffer.writeVarInt(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeUtf(entry.key, 64);
            buffer.writeUtf(entry.label, 96);
            buffer.writeUtf(entry.value, 256);
            buffer.writeUtf(entry.type, 16);
            buffer.writeUtf(entry.description, 512);
            buffer.writeUtf(entry.defaultValue, 128);
            buffer.writeLong(entry.minimum);
            buffer.writeLong(entry.maximum);
            buffer.writeBoolean(entry.editable);
            buffer.writeVarInt(entry.options.size());
            for (Option option : entry.options) {
                buffer.writeUtf(option.value, 64);
                buffer.writeUtf(option.label, 64);
            }
        }
    }

    private static SsuPropertySettingsDataPayload decode(RegistryFriendlyByteBuf buffer) {
        String kind = buffer.readUtf(16);
        String target = buffer.readUtf(64);
        String title = buffer.readUtf(128);
        long id = buffer.readVarLong();
        boolean canEdit = buffer.readBoolean();
        String notice = buffer.readUtf(512);
        boolean error = buffer.readBoolean();
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) {
            throw new IllegalArgumentException("Invalid property settings size: " + size);
        }

        List<Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            String key = buffer.readUtf(64);
            String label = buffer.readUtf(96);
            String value = buffer.readUtf(256);
            String type = buffer.readUtf(16);
            String description = buffer.readUtf(512);
            String defaultValue = buffer.readUtf(128);
            long minimum = buffer.readLong();
            long maximum = buffer.readLong();
            boolean editable = buffer.readBoolean();
            int optionCount = buffer.readVarInt();
            if (optionCount < 0 || optionCount > MAX_OPTIONS) {
                throw new IllegalArgumentException("Invalid property setting option count: " + optionCount);
            }
            List<Option> options = new ArrayList<>(optionCount);
            for (int optionIndex = 0; optionIndex < optionCount; optionIndex++) {
                options.add(new Option(buffer.readUtf(64), buffer.readUtf(64)));
            }
            entries.add(new Entry(key, label, value, type, description, defaultValue,
                    minimum, maximum, editable, options));
        }
        return new SsuPropertySettingsDataPayload(kind, target, title, id, canEdit, notice, error, entries);
    }
@Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            String key,
            String label,
            String value,
            String type,
            String description,
            String defaultValue,
            long minimum,
            long maximum,
            boolean editable,
            List<Option> options
    ) {
        public Entry {
            key = PayloadBounds.string(key, 64);
            label = PayloadBounds.string(label, 96);
            value = PayloadBounds.string(value, 256);
            type = PayloadBounds.string(type, 16);
            description = PayloadBounds.string(description, 512);
            defaultValue = PayloadBounds.string(defaultValue, 128);
            if (maximum < minimum) {
                long swap = minimum;
                minimum = maximum;
                maximum = swap;
            }
            options = options == null ? List.of() : List.copyOf(options);
            if (options.size() > MAX_OPTIONS) {
                throw new IllegalArgumentException("Too many property setting options.");
            }
        }
    }

    public record Option(String value, String label) {
        public Option {
            value = PayloadBounds.string(value, 64);
            label = PayloadBounds.string(label, 64);
        }
    }
}
