package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Complete, bounded state for the dashboard permission editor. */
public record SsuPermissionEditorDataPayload(
        String mode,
        String selectedTarget,
        String selectedDimension,
        String selectedLabel,
        String targetSummary,
        int pageIndex,
        int pageSize,
        int totalPermissions,
        long requestId,
        String notice,
        boolean error,
        List<TargetEntry> targets,
        List<TargetEntry> dimensions,
        List<String> rankOptions,
        List<PermissionEntry> permissions
) implements CustomPacketPayload {

    private static final int MAX_TARGETS = 200;
    private static final int MAX_DIMENSIONS = 256;
    private static final int MAX_RANKS = 100;
    private static final int MAX_PERMISSIONS = SsuPermissionEditorRequestPayload.MAX_PAGE_SIZE;

    public static final Type<SsuPermissionEditorDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "permission_editor_data")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuPermissionEditorDataPayload> STREAM_CODEC =
            StreamCodec.of(SsuPermissionEditorDataPayload::encode, SsuPermissionEditorDataPayload::decode);

    public SsuPermissionEditorDataPayload {
        mode = PayloadBounds.string(mode, 16);
        selectedTarget = PayloadBounds.string(selectedTarget, 64);
        selectedDimension = PayloadBounds.string(selectedDimension, 128);
        selectedLabel = PayloadBounds.string(selectedLabel, 64);
        targetSummary = PayloadBounds.string(targetSummary, 256);
        pageIndex = Math.max(0, pageIndex);
        pageSize = Math.max(1, Math.min(MAX_PERMISSIONS, pageSize));
        totalPermissions = Math.max(0, totalPermissions);
        requestId = Math.max(0L, requestId);
        notice = PayloadBounds.string(notice, 512);
        targets = copy(targets, MAX_TARGETS, "targets");
        dimensions = copy(dimensions, MAX_DIMENSIONS, "dimensions");
        rankOptions = copyStrings(rankOptions, MAX_RANKS, 64, "rank options");
        permissions = copy(permissions, MAX_PERMISSIONS, "permissions");
    }

    public static SsuPermissionEditorDataPayload empty(String mode, long requestId, String notice, boolean error) {
        return new SsuPermissionEditorDataPayload(mode, "", "", "", "", 0, 10, 0, requestId,
                notice, error, List.of(), List.of(), List.of(), List.of());
    }

    private static void encode(RegistryFriendlyByteBuf b, SsuPermissionEditorDataPayload p) {
        b.writeUtf(p.mode, 16);
        b.writeUtf(p.selectedTarget, 64);
        b.writeUtf(p.selectedDimension, 128);
        b.writeUtf(p.selectedLabel, 64);
        b.writeUtf(p.targetSummary, 256);
        b.writeVarInt(p.pageIndex);
        b.writeVarInt(p.pageSize);
        b.writeVarInt(p.totalPermissions);
        b.writeVarLong(p.requestId);
        b.writeUtf(p.notice, 512);
        b.writeBoolean(p.error);
        writeTargets(b, p.targets);
        writeTargets(b, p.dimensions);

        b.writeVarInt(p.rankOptions.size());
        for (String rank : p.rankOptions) b.writeUtf(rank, 64);

        b.writeVarInt(p.permissions.size());
        for (PermissionEntry permission : p.permissions) {
            b.writeUtf(permission.key, 128);
            b.writeUtf(permission.directValue, 128);
            b.writeUtf(permission.effectiveValue, 128);
            b.writeUtf(permission.defaultValue, 128);
            b.writeUtf(permission.source, 64);
            b.writeUtf(permission.valueType, 16);
            b.writeUtf(permission.description, 384);
            b.writeInt(permission.minimum);
            b.writeInt(permission.maximum);
        }
    }

    private static void writeTargets(RegistryFriendlyByteBuf b, List<TargetEntry> entries) {
        b.writeVarInt(entries.size());
        for (TargetEntry target : entries) {
            b.writeUtf(target.id, 128);
            b.writeUtf(target.label, 64);
            b.writeUtf(target.summary, 256);
        }
    }

    private static SsuPermissionEditorDataPayload decode(RegistryFriendlyByteBuf b) {
        String mode = b.readUtf(16);
        String selectedTarget = b.readUtf(64);
        String selectedDimension = b.readUtf(128);
        String selectedLabel = b.readUtf(64);
        String targetSummary = b.readUtf(256);
        int pageIndex = b.readVarInt();
        int pageSize = b.readVarInt();
        int totalPermissions = b.readVarInt();
        long requestId = b.readVarLong();
        String notice = b.readUtf(512);
        boolean error = b.readBoolean();
        List<TargetEntry> targets = readTargets(b, MAX_TARGETS, "targets");
        List<TargetEntry> dimensions = readTargets(b, MAX_DIMENSIONS, "dimensions");

        int rankCount = size(b, MAX_RANKS, "rank options");
        List<String> ranks = new ArrayList<>(rankCount);
        for (int i = 0; i < rankCount; i++) ranks.add(b.readUtf(64));

        int permissionCount = size(b, MAX_PERMISSIONS, "permissions");
        List<PermissionEntry> permissions = new ArrayList<>(permissionCount);
        for (int i = 0; i < permissionCount; i++) {
            permissions.add(new PermissionEntry(
                    b.readUtf(128), b.readUtf(128), b.readUtf(128), b.readUtf(128), b.readUtf(64),
                    b.readUtf(16), b.readUtf(384), b.readInt(), b.readInt()
            ));
        }

        return new SsuPermissionEditorDataPayload(mode, selectedTarget, selectedDimension, selectedLabel, targetSummary,
                pageIndex, pageSize, totalPermissions, requestId, notice, error, targets, dimensions, ranks, permissions);
    }

    private static List<TargetEntry> readTargets(RegistryFriendlyByteBuf b, int maximum, String name) {
        int count = size(b, maximum, name);
        List<TargetEntry> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new TargetEntry(b.readUtf(128), b.readUtf(64), b.readUtf(256)));
        }
        return result;
    }

    private static int size(RegistryFriendlyByteBuf buffer, int maximum, String name) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid SSU permission editor " + name + " size: " + size);
        }
        return size;
    }

    private static <T> List<T> copy(List<T> values, int maximum, String name) {
        List<T> result = values == null ? List.of() : List.copyOf(values);
        if (result.size() > maximum) throw new IllegalArgumentException("Too many SSU permission editor " + name);
        return result;
    }

    private static List<String> copyStrings(List<String> values, int maximum, int maximumLength, String name) {
        List<String> source = values == null ? List.of() : values;
        if (source.size() > maximum) throw new IllegalArgumentException("Too many SSU permission editor " + name);
        return source.stream().map(value -> PayloadBounds.string(value, maximumLength)).toList();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record TargetEntry(String id, String label, String summary) {
        public TargetEntry {
            id = PayloadBounds.string(id, 128);
            label = PayloadBounds.string(label, 64);
            summary = PayloadBounds.string(summary, 256);
        }
    }

    public record PermissionEntry(
            String key,
            String directValue,
            String effectiveValue,
            String defaultValue,
            String source,
            String valueType,
            String description,
            int minimum,
            int maximum
    ) {
        public PermissionEntry {
            key = PayloadBounds.string(key, 128);
            directValue = PayloadBounds.string(directValue, 128);
            effectiveValue = PayloadBounds.string(effectiveValue, 128);
            defaultValue = PayloadBounds.string(defaultValue, 128);
            source = PayloadBounds.string(source, 64);
            valueType = PayloadBounds.string(valueType, 16);
            description = PayloadBounds.string(description, 384);
        }
    }
}
