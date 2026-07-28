package be.winnetrie.mod.simpleserverutilities.network;

import java.util.ArrayList;
import java.util.List;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SsuMenuSnapshotPayload(
        String playerName,
        String primaryRank,
        boolean settingsAvailable,
        UiSettingsSummary uiSettings,
        boolean administrator,
        boolean claimBordersVisible,
        boolean regionBordersVisible,
        boolean canViewClaimBorders,
        boolean canViewRegionBorders,
        int activeJobs,
        int pendingStorageWrites,
        CoreSummary core,
        EconomySummary economy,
        List<ClaimSummary> claims,
        List<RegionSummary> regions,
        List<LocationSummary> homes,
        List<LocationSummary> warps
) implements CustomPacketPayload {

    private static final int MAX_LIST_SIZE = 4096;

    public static final Type<SsuMenuSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "menu_snapshot")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SsuMenuSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(SsuMenuSnapshotPayload::encode, SsuMenuSnapshotPayload::decode);

    public SsuMenuSnapshotPayload {
        playerName = playerName == null ? "" : playerName;
        primaryRank = primaryRank == null ? "" : primaryRank;
        uiSettings = uiSettings == null ? UiSettingsSummary.defaults() : uiSettings;
        core = core == null ? CoreSummary.empty() : core;
        economy = economy == null ? EconomySummary.empty() : economy;
        claims = claims == null ? List.of() : List.copyOf(claims);
        regions = regions == null ? List.of() : List.copyOf(regions);
        homes = homes == null ? List.of() : List.copyOf(homes);
        warps = warps == null ? List.of() : List.copyOf(warps);
        validateSize(claims.size(), "claims");
        validateSize(regions.size(), "regions");
        validateSize(homes.size(), "homes");
        validateSize(warps.size(), "warps");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SsuMenuSnapshotPayload payload) {
        buffer.writeUtf(payload.playerName, 64);
        buffer.writeUtf(payload.primaryRank, 64);
        buffer.writeBoolean(payload.settingsAvailable);
        writeUiSettings(buffer, payload.uiSettings);
        buffer.writeBoolean(payload.administrator);
        buffer.writeBoolean(payload.claimBordersVisible);
        buffer.writeBoolean(payload.regionBordersVisible);
        buffer.writeBoolean(payload.canViewClaimBorders);
        buffer.writeBoolean(payload.canViewRegionBorders);
        buffer.writeVarInt(payload.activeJobs);
        buffer.writeVarInt(payload.pendingStorageWrites);
        writeCore(buffer, payload.core);
        writeEconomy(buffer, payload.economy);

        writeClaims(buffer, payload.claims);
        writeRegions(buffer, payload.regions);
        writeLocations(buffer, payload.homes);
        writeLocations(buffer, payload.warps);
    }

    private static SsuMenuSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        return new SsuMenuSnapshotPayload(
                buffer.readUtf(64),
                buffer.readUtf(64),
                buffer.readBoolean(),
                readUiSettings(buffer),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readCore(buffer),
                readEconomy(buffer),
                readClaims(buffer),
                readRegions(buffer),
                readLocations(buffer),
                readLocations(buffer)
        );
    }


    private static void writeUiSettings(RegistryFriendlyByteBuf buffer, UiSettingsSummary settings) {
        buffer.writeBoolean(settings.dashboardHints());
        buffer.writeBoolean(settings.minimapEnabled());
        buffer.writeVarInt(settings.minimapSize());
        buffer.writeUtf(settings.minimapShape(), 16);
        buffer.writeUtf(settings.minimapPosition(), 16);
        buffer.writeBoolean(settings.minimapNorthUp());
        buffer.writeBoolean(settings.minimapShowClaims());
        buffer.writeBoolean(settings.minimapShowRegions());
    }

    private static UiSettingsSummary readUiSettings(RegistryFriendlyByteBuf buffer) {
        return new UiSettingsSummary(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readUtf(16),
                buffer.readUtf(16),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    private static void writeCore(RegistryFriendlyByteBuf buffer, CoreSummary core) {
        buffer.writeVarLong(core.permissionChecks());
        buffer.writeVarInt(core.permissionCacheHitPermille());
        buffer.writeVarInt(core.permissionCacheEntries());
        buffer.writeVarLong(core.regionLookups());
        buffer.writeDouble(core.averageRegionCandidates());
        buffer.writeVarInt(core.regionIndexCells());
        buffer.writeVarInt(core.regionIndexReferences());
    }

    private static CoreSummary readCore(RegistryFriendlyByteBuf buffer) {
        return new CoreSummary(
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    private static void writeEconomy(RegistryFriendlyByteBuf buffer, EconomySummary economy) {
        buffer.writeBoolean(economy.enabled());
        buffer.writeUtf(economy.formattedBalance(), 128);
        buffer.writeVarLong(economy.balanceMinor());
        buffer.writeBoolean(economy.canPay());
        buffer.writeBoolean(economy.canAdmin());
        buffer.writeVarInt(economy.accountCount());
        buffer.writeUtf(economy.formattedTotalSupply(), 128);
        buffer.writeVarInt(economy.rentOwnerSharePercent());
        buffer.writeVarInt(economy.playerCancelRefundPercent());
        buffer.writeVarInt(economy.adminCancelRefundPercent());
        buffer.writeVarInt(economy.pendingRentOperations());
        buffer.writeVarInt(economy.recentTransactions().size());
        for (TransactionSummary transaction : economy.recentTransactions()) {
            buffer.writeUtf(transaction.type(), 64);
            buffer.writeUtf(transaction.direction(), 16);
            buffer.writeUtf(transaction.formattedAmount(), 128);
            buffer.writeUtf(transaction.otherParty(), 64);
            buffer.writeUtf(transaction.status(), 32);
            buffer.writeVarLong(transaction.createdAtEpochMilli());
        }
    }

    private static EconomySummary readEconomy(RegistryFriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        String formattedBalance = buffer.readUtf(128);
        long balanceMinor = buffer.readVarLong();
        boolean canPay = buffer.readBoolean();
        boolean canAdmin = buffer.readBoolean();
        int accountCount = buffer.readVarInt();
        String formattedTotalSupply = buffer.readUtf(128);
        int rentOwnerSharePercent = buffer.readVarInt();
        int playerCancelRefundPercent = buffer.readVarInt();
        int adminCancelRefundPercent = buffer.readVarInt();
        int pendingRentOperations = buffer.readVarInt();
        int transactionCount = readBoundedSize(buffer, 20, "economy transactions");
        List<TransactionSummary> transactions = new ArrayList<>(transactionCount);
        for (int i = 0; i < transactionCount; i++) {
            transactions.add(new TransactionSummary(
                    buffer.readUtf(64),
                    buffer.readUtf(16),
                    buffer.readUtf(128),
                    buffer.readUtf(64),
                    buffer.readUtf(32),
                    buffer.readVarLong()
            ));
        }
        return new EconomySummary(
                enabled,
                formattedBalance,
                balanceMinor,
                canPay,
                canAdmin,
                accountCount,
                formattedTotalSupply,
                rentOwnerSharePercent,
                playerCancelRefundPercent,
                adminCancelRefundPercent,
                pendingRentOperations,
                transactions
        );
    }

    private static void writeClaims(RegistryFriendlyByteBuf buffer, List<ClaimSummary> claims) {
        buffer.writeVarInt(claims.size());
        for (ClaimSummary claim : claims) {
            buffer.writeUtf(claim.name());
            buffer.writeUtf(claim.dimension());
            buffer.writeVarInt(claim.chunkCount());
        }
    }

    private static List<ClaimSummary> readClaims(RegistryFriendlyByteBuf buffer) {
        int size = readSize(buffer, "claims");
        List<ClaimSummary> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new ClaimSummary(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt()));
        }
        return result;
    }

    private static void writeRegions(RegistryFriendlyByteBuf buffer, List<RegionSummary> regions) {
        buffer.writeVarInt(regions.size());
        for (RegionSummary region : regions) {
            buffer.writeUtf(region.name());
            buffer.writeUtf(region.dimension());
            buffer.writeUtf(region.bounds());
            buffer.writeBoolean(region.visible());
            buffer.writeBoolean(region.rented());
            buffer.writeBoolean(region.rentable());
            buffer.writeBoolean(region.rentedByPlayer());
            buffer.writeUtf(region.formattedPrice(), 128);
            buffer.writeUtf(region.periodText(), 64);
            buffer.writeUtf(region.renterName(), 64);
            buffer.writeUtf(region.remainingText(), 64);
        }
    }

    private static List<RegionSummary> readRegions(RegistryFriendlyByteBuf buffer) {
        int size = readSize(buffer, "regions");
        List<RegionSummary> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new RegionSummary(
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readUtf(128),
                    buffer.readUtf(64),
                    buffer.readUtf(64),
                    buffer.readUtf(64)
            ));
        }
        return result;
    }

    private static void writeLocations(RegistryFriendlyByteBuf buffer, List<LocationSummary> locations) {
        buffer.writeVarInt(locations.size());
        for (LocationSummary location : locations) {
            buffer.writeUtf(location.name());
            buffer.writeUtf(location.dimension());
            buffer.writeDouble(location.x());
            buffer.writeDouble(location.y());
            buffer.writeDouble(location.z());
        }
    }

    private static List<LocationSummary> readLocations(RegistryFriendlyByteBuf buffer) {
        int size = readSize(buffer, "locations");
        List<LocationSummary> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new LocationSummary(
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble()
            ));
        }
        return result;
    }

    private static int readBoundedSize(RegistryFriendlyByteBuf buffer, int max, String name) {
        int size = buffer.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid SSU menu " + name + " count: " + size);
        }
        return size;
    }

    private static int readSize(RegistryFriendlyByteBuf buffer, String name) {
        int size = buffer.readVarInt();
        validateSize(size, name);
        return size;
    }

    private static void validateSize(int size, String name) {
        if (size < 0 || size > MAX_LIST_SIZE) {
            throw new IllegalArgumentException("Invalid SSU menu " + name + " count: " + size);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record UiSettingsSummary(
            boolean dashboardHints,
            boolean minimapEnabled,
            int minimapSize,
            String minimapShape,
            String minimapPosition,
            boolean minimapNorthUp,
            boolean minimapShowClaims,
            boolean minimapShowRegions
    ) {
        public UiSettingsSummary {
            minimapSize = Math.max(64, Math.min(256, minimapSize));
            minimapShape = minimapShape == null ? "CIRCLE" : minimapShape;
            minimapPosition = minimapPosition == null ? "TOP_RIGHT" : minimapPosition;
        }

        public static UiSettingsSummary defaults() {
            return new UiSettingsSummary(true, false, 96, "CIRCLE", "TOP_RIGHT", true, true, true);
        }
    }

    public record CoreSummary(
            long permissionChecks,
            int permissionCacheHitPermille,
            int permissionCacheEntries,
            long regionLookups,
            double averageRegionCandidates,
            int regionIndexCells,
            int regionIndexReferences
    ) {
        public CoreSummary {
            permissionChecks = Math.max(0L, permissionChecks);
            permissionCacheHitPermille = Math.max(0, Math.min(1000, permissionCacheHitPermille));
            permissionCacheEntries = Math.max(0, permissionCacheEntries);
            regionLookups = Math.max(0L, regionLookups);
            averageRegionCandidates = Math.max(0.0D, averageRegionCandidates);
            regionIndexCells = Math.max(0, regionIndexCells);
            regionIndexReferences = Math.max(0, regionIndexReferences);
        }

        public static CoreSummary empty() {
            return new CoreSummary(0L, 0, 0, 0L, 0.0D, 0, 0);
        }
    }

    public record EconomySummary(
            boolean enabled,
            String formattedBalance,
            long balanceMinor,
            boolean canPay,
            boolean canAdmin,
            int accountCount,
            String formattedTotalSupply,
            int rentOwnerSharePercent,
            int playerCancelRefundPercent,
            int adminCancelRefundPercent,
            int pendingRentOperations,
            List<TransactionSummary> recentTransactions
    ) {
        public EconomySummary {
            formattedBalance = formattedBalance == null ? "" : formattedBalance;
            balanceMinor = Math.max(0L, balanceMinor);
            accountCount = Math.max(0, accountCount);
            formattedTotalSupply = formattedTotalSupply == null ? "" : formattedTotalSupply;
            rentOwnerSharePercent = Math.max(0, Math.min(100, rentOwnerSharePercent));
            playerCancelRefundPercent = Math.max(0, Math.min(100, playerCancelRefundPercent));
            adminCancelRefundPercent = Math.max(0, Math.min(100, adminCancelRefundPercent));
            pendingRentOperations = Math.max(0, pendingRentOperations);
            recentTransactions = recentTransactions == null ? List.of() : List.copyOf(recentTransactions);
            if (recentTransactions.size() > 20) {
                throw new IllegalArgumentException("Too many economy transactions in SSU menu snapshot.");
            }
        }

        public static EconomySummary empty() {
            return new EconomySummary(false, "", 0L, false, false, 0, "", 0, 0, 100, 0, List.of());
        }
    }

    public record TransactionSummary(
            String type,
            String direction,
            String formattedAmount,
            String otherParty,
            String status,
            long createdAtEpochMilli
    ) {
        public TransactionSummary {
            type = type == null ? "" : type;
            direction = direction == null ? "" : direction;
            formattedAmount = formattedAmount == null ? "" : formattedAmount;
            otherParty = otherParty == null ? "" : otherParty;
            status = status == null ? "" : status;
            createdAtEpochMilli = Math.max(0L, createdAtEpochMilli);
        }
    }

    public record ClaimSummary(String name, String dimension, int chunkCount) {
        public ClaimSummary {
            name = name == null ? "" : name;
            dimension = dimension == null ? "" : dimension;
            chunkCount = Math.max(0, chunkCount);
        }
    }

    public record RegionSummary(
            String name,
            String dimension,
            String bounds,
            boolean visible,
            boolean rented,
            boolean rentable,
            boolean rentedByPlayer,
            String formattedPrice,
            String periodText,
            String renterName,
            String remainingText
    ) {
        public RegionSummary {
            name = name == null ? "" : name;
            dimension = dimension == null ? "" : dimension;
            bounds = bounds == null ? "" : bounds;
            formattedPrice = formattedPrice == null ? "" : formattedPrice;
            periodText = periodText == null ? "" : periodText;
            renterName = renterName == null ? "" : renterName;
            remainingText = remainingText == null ? "" : remainingText;
        }
    }

    public record LocationSummary(String name, String dimension, double x, double y, double z) {
        public LocationSummary {
            name = name == null ? "" : name;
            dimension = dimension == null ? "" : dimension;
        }
    }
}
