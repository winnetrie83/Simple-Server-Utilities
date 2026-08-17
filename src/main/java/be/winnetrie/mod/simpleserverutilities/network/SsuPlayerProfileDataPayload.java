package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded admin-only player profile, player choices and effective permission page. */
public record SsuPlayerProfileDataPayload(
        String selectedPlayer,
        String selectedLabel,
        int permissionPageIndex,
        int permissionPageSize,
        int totalPermissions,
        long requestId,
        String notice,
        boolean error,
        List<PlayerEntry> players,
        Profile profile,
        List<PermissionLine> permissions
) implements CustomPacketPayload {

    private static final int MAX_PLAYERS = 1_000;
    private static final int MAX_PERMISSIONS = SsuPlayerProfileRequestPayload.MAX_PERMISSION_PAGE_SIZE;

    public static final Type<SsuPlayerProfileDataPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "player_profile_data")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SsuPlayerProfileDataPayload> STREAM_CODEC =
            StreamCodec.of(SsuPlayerProfileDataPayload::encode, SsuPlayerProfileDataPayload::decode);

    public SsuPlayerProfileDataPayload {
        selectedPlayer = PayloadBounds.string(selectedPlayer, 64);
        selectedLabel = PayloadBounds.string(selectedLabel, 64);
        permissionPageIndex = Math.max(0, permissionPageIndex);
        permissionPageSize = Math.max(1, Math.min(MAX_PERMISSIONS, permissionPageSize));
        totalPermissions = Math.max(0, totalPermissions);
        requestId = Math.max(0L, requestId);
        notice = PayloadBounds.string(notice, 512);
        players = copy(players, MAX_PLAYERS, "players");
        profile = profile == null ? Profile.empty() : profile;
        permissions = copy(permissions, MAX_PERMISSIONS, "permissions");
    }

    public static SsuPlayerProfileDataPayload empty(long requestId, String notice, boolean error) {
        return new SsuPlayerProfileDataPayload("", "", 0, 8, 0, requestId, notice, error,
                List.of(), Profile.empty(), List.of());
    }

    private static void encode(RegistryFriendlyByteBuf b, SsuPlayerProfileDataPayload p) {
        b.writeUtf(p.selectedPlayer, 64);
        b.writeUtf(p.selectedLabel, 64);
        b.writeVarInt(p.permissionPageIndex);
        b.writeVarInt(p.permissionPageSize);
        b.writeVarInt(p.totalPermissions);
        b.writeVarLong(p.requestId);
        b.writeUtf(p.notice, 512);
        b.writeBoolean(p.error);

        b.writeVarInt(p.players.size());
        for (PlayerEntry player : p.players) {
            b.writeUtf(player.id, 64);
            b.writeUtf(player.label, 64);
            b.writeUtf(player.summary, 192);
            b.writeBoolean(player.online);
        }

        Profile profile = p.profile;
        b.writeBoolean(profile.selected);
        b.writeBoolean(profile.online);
        b.writeUtf(profile.playerId, 64);
        b.writeUtf(profile.name, 64);
        b.writeUtf(profile.primaryRank, 64);
        b.writeUtf(profile.assignedRanks, 256);
        b.writeUtf(profile.adminStatus, 128);
        b.writeUtf(profile.formattedBalance, 64);
        b.writeVarInt(profile.claimGroups);
        b.writeVarInt(profile.claimChunks);
        b.writeVarInt(profile.homes);
        b.writeVarInt(profile.rentals);
        b.writeUtf(profile.rentalNames, 512);
        b.writeUtf(profile.dimension, 128);
        b.writeUtf(profile.position, 96);
        b.writeUtf(profile.healthAndFood, 128);
        b.writeVarInt(profile.directOverrides);

        b.writeVarInt(p.permissions.size());
        for (PermissionLine permission : p.permissions) {
            b.writeUtf(permission.key, 128);
            b.writeUtf(permission.value, 128);
            b.writeUtf(permission.source, 64);
        }
    }

    private static SsuPlayerProfileDataPayload decode(RegistryFriendlyByteBuf b) {
        String selectedPlayer = b.readUtf(64);
        String selectedLabel = b.readUtf(64);
        int permissionPageIndex = b.readVarInt();
        int permissionPageSize = b.readVarInt();
        int totalPermissions = b.readVarInt();
        long requestId = b.readVarLong();
        String notice = b.readUtf(512);
        boolean error = b.readBoolean();

        int playerCount = size(b, MAX_PLAYERS, "players");
        List<PlayerEntry> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            players.add(new PlayerEntry(b.readUtf(64), b.readUtf(64), b.readUtf(192), b.readBoolean()));
        }

        Profile profile = new Profile(
                b.readBoolean(), b.readBoolean(), b.readUtf(64), b.readUtf(64),
                b.readUtf(64), b.readUtf(256), b.readUtf(128), b.readUtf(64),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readUtf(512), b.readUtf(128), b.readUtf(96), b.readUtf(128), b.readVarInt()
        );

        int permissionCount = size(b, MAX_PERMISSIONS, "permissions");
        List<PermissionLine> permissions = new ArrayList<>(permissionCount);
        for (int i = 0; i < permissionCount; i++) {
            permissions.add(new PermissionLine(b.readUtf(128), b.readUtf(128), b.readUtf(64)));
        }

        return new SsuPlayerProfileDataPayload(selectedPlayer, selectedLabel,
                permissionPageIndex, permissionPageSize, totalPermissions, requestId, notice, error,
                players, profile, permissions);
    }

    private static int size(RegistryFriendlyByteBuf buffer, int maximum, String name) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximum) {
            throw new IllegalArgumentException("Invalid SSU player profile " + name + " size: " + size);
        }
        return size;
    }

    private static <T> List<T> copy(List<T> values, int maximum, String name) {
        List<T> result = values == null ? List.of() : List.copyOf(values);
        if (result.size() > maximum) {
            throw new IllegalArgumentException("Too many SSU player profile " + name);
        }
        return result;
    }
@Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record PlayerEntry(String id, String label, String summary, boolean online) {
        public PlayerEntry {
            id = PayloadBounds.string(id, 64);
            label = PayloadBounds.string(label, 64);
            summary = PayloadBounds.string(summary, 192);
        }
    }

    public record Profile(
            boolean selected,
            boolean online,
            String playerId,
            String name,
            String primaryRank,
            String assignedRanks,
            String adminStatus,
            String formattedBalance,
            int claimGroups,
            int claimChunks,
            int homes,
            int rentals,
            String rentalNames,
            String dimension,
            String position,
            String healthAndFood,
            int directOverrides
    ) {
        public Profile {
            playerId = PayloadBounds.string(playerId, 64);
            name = PayloadBounds.string(name, 64);
            primaryRank = PayloadBounds.string(primaryRank, 64);
            assignedRanks = PayloadBounds.string(assignedRanks, 256);
            adminStatus = PayloadBounds.string(adminStatus, 128);
            formattedBalance = PayloadBounds.string(formattedBalance, 64);
            claimGroups = Math.max(0, claimGroups);
            claimChunks = Math.max(0, claimChunks);
            homes = Math.max(0, homes);
            rentals = Math.max(0, rentals);
            rentalNames = PayloadBounds.string(rentalNames, 512);
            dimension = PayloadBounds.string(dimension, 128);
            position = PayloadBounds.string(position, 96);
            healthAndFood = PayloadBounds.string(healthAndFood, 128);
            directOverrides = Math.max(0, directOverrides);
        }

        public static Profile empty() {
            return new Profile(false, false, "", "", "", "", "", "",
                    0, 0, 0, 0, "", "", "", "", 0);
        }
    }

    public record PermissionLine(String key, String value, String source) {
        public PermissionLine {
            key = PayloadBounds.string(key, 128);
            value = PayloadBounds.string(value, 128);
            source = PayloadBounds.string(source, 64);
        }
    }
}
