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
        String selectedLabel,
        String targetSummary,
        int pageIndex,
        int pageSize,
        int totalPermissions,
        long requestId,
        String notice,
        boolean error,
        List<TargetEntry> targets,
        List<String> rankOptions,
        List<PermissionEntry> permissions
) implements CustomPacketPayload {

    private static final int MAX_TARGETS = 200;
    private static final int MAX_RANKS = 100;
    private static final int MAX_PERMISSIONS = SsuPermissionEditorRequestPayload.MAX_PAGE_SIZE;

    public static final Type<SsuPermissionEditorDataPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "permission_editor_data")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuPermissionEditorDataPayload> STREAM_CODEC =
            StreamCodec.of(SsuPermissionEditorDataPayload::encode, SsuPermissionEditorDataPayload::decode);

    public SsuPermissionEditorDataPayload {
        mode = bounded(mode, 16);
        selectedTarget = bounded(selectedTarget, 64);
        selectedLabel = bounded(selectedLabel, 64);
        targetSummary = bounded(targetSummary, 256);
        pageIndex = Math.max(0, pageIndex);
        pageSize = Math.max(1, Math.min(MAX_PERMISSIONS, pageSize));
        totalPermissions = Math.max(0, totalPermissions);
        requestId = Math.max(0L, requestId);
        notice = bounded(notice, 512);
        targets = copy(targets, MAX_TARGETS, "targets");
        rankOptions = copyStrings(rankOptions, MAX_RANKS, 64, "rank options");
        permissions = copy(permissions, MAX_PERMISSIONS, "permissions");
    }

    public static SsuPermissionEditorDataPayload empty(String mode, long requestId, String notice, boolean error) {
        return new SsuPermissionEditorDataPayload(mode, "", "", "", 0, 10, 0, requestId,
                notice, error, List.of(), List.of(), List.of());
    }

    private static void encode(RegistryFriendlyByteBuf b, SsuPermissionEditorDataPayload p) {
        b.writeUtf(p.mode, 16);
        b.writeUtf(p.selectedTarget, 64);
        b.writeUtf(p.selectedLabel, 64);
        b.writeUtf(p.targetSummary, 256);
        b.writeVarInt(p.pageIndex);
        b.writeVarInt(p.pageSize);
        b.writeVarInt(p.totalPermissions);
        b.writeVarLong(p.requestId);
        b.writeUtf(p.notice, 512);
        b.writeBoolean(p.error);

        b.writeVarInt(p.targets.size());
        for (TargetEntry target : p.targets) {
            b.writeUtf(target.id, 64);
            b.writeUtf(target.label, 64);
            b.writeUtf(target.summary, 256);
        }

        b.writeVarInt(p.rankOptions.size());
        for (String rank : p.rankOptions) {
            b.writeUtf(rank, 64);
        }

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

    private static SsuPermissionEditorDataPayload decode(RegistryFriendlyByteBuf b) {
        String mode = b.readUtf(16);
        String selectedTarget = b.readUtf(64);
        String selectedLabel = b.readUtf(64);
        String targetSummary = b.readUtf(256);
        int pageIndex = b.readVarInt();
        int pageSize = b.readVarInt();
        int totalPermissions = b.readVarInt();
        long requestId = b.readVarLong();
        String notice = b.readUtf(512);
        boolean error = b.readBoolean();

        int targetCount = size(b, MAX_TARGETS, "targets");
        List<TargetEntry> targets = new ArrayList<>(targetCount);
        for (int i = 0; i < targetCount; i++) {
            targets.add(new TargetEntry(b.readUtf(64), b.readUtf(64), b.readUtf(256)));
        }

        int rankCount = size(b, MAX_RANKS, "rank options");
        List<String> ranks = new ArrayList<>(rankCount);
        for (int i = 0; i < rankCount; i++) {
            ranks.add(b.readUtf(64));
        }

        int permissionCount = size(b, MAX_PERMISSIONS, "permissions");
        List<PermissionEntry> permissions = new ArrayList<>(permissionCount);
        for (int i = 0; i < permissionCount; i++) {
            permissions.add(new PermissionEntry(
                    b.readUtf(128), b.readUtf(128), b.readUtf(128), b.readUtf(128), b.readUtf(64),
                    b.readUtf(16), b.readUtf(384), b.readInt(), b.readInt()
            ));
        }

        return new SsuPermissionEditorDataPayload(mode, selectedTarget, selectedLabel, targetSummary,
                pageIndex, pageSize, totalPermissions, requestId, notice, error, targets, ranks, permissions);
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
        if (result.size() > maximum) {
            throw new IllegalArgumentException("Too many SSU permission editor " + name);
        }
        return result;
    }

    private static List<String> copyStrings(List<String> values, int maximum, int maximumLength, String name) {
        List<String> source = values == null ? List.of() : values;
        if (source.size() > maximum) {
            throw new IllegalArgumentException("Too many SSU permission editor " + name);
        }
        return source.stream().map(value -> bounded(value, maximumLength)).toList();
    }

    private static String bounded(String value, int maximum) {
        String safe = value == null ? "" : value;
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record TargetEntry(String id, String label, String summary) {
        public TargetEntry {
            id = bounded(id, 64);
            label = bounded(label, 64);
            summary = bounded(summary, 256);
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
            key = bounded(key, 128);
            directValue = bounded(directValue, 128);
            effectiveValue = bounded(effectiveValue, 128);
            defaultValue = bounded(defaultValue, 128);
            source = bounded(source, 64);
            valueType = bounded(valueType, 16);
            description = bounded(description, 384);
        }
    }
}
