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
        String selectedTitle,
        int selectedTitleColor,
        boolean settingsAvailable,
        UiSettingsSummary uiSettings,
        boolean administrator,
        boolean cropsHarvestingEnabled,
        boolean auctionHouseDashboardVisible,
        ModuleSettingsSummary moduleSettings,
        AdminAccessSummary adminAccess,
        boolean claimBordersVisible,
        boolean showOtherClaims,
        boolean regionBordersVisible,
        boolean minigameGameBorderVisible,
        boolean minigameSpectatorBorderVisible,
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
        selectedTitle = selectedTitle == null ? "" : selectedTitle;
        uiSettings = uiSettings == null ? UiSettingsSummary.defaults() : uiSettings;
        moduleSettings = moduleSettings == null ? ModuleSettingsSummary.defaults() : moduleSettings;
        adminAccess = adminAccess == null ? AdminAccessSummary.none() : adminAccess;
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
        buffer.writeUtf(payload.selectedTitle, 48);
        buffer.writeInt(payload.selectedTitleColor);
        buffer.writeBoolean(payload.settingsAvailable);
        writeUiSettings(buffer, payload.uiSettings);
        buffer.writeBoolean(payload.administrator);
        buffer.writeBoolean(payload.cropsHarvestingEnabled);
        buffer.writeBoolean(payload.auctionHouseDashboardVisible);
        writeModuleSettings(buffer, payload.moduleSettings);
        writeAdminAccess(buffer, payload.adminAccess);
        buffer.writeBoolean(payload.claimBordersVisible);
        buffer.writeBoolean(payload.showOtherClaims);
        buffer.writeBoolean(payload.regionBordersVisible);
        buffer.writeBoolean(payload.minigameGameBorderVisible);
        buffer.writeBoolean(payload.minigameSpectatorBorderVisible);
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
                buffer.readUtf(48),
                buffer.readInt(),
                buffer.readBoolean(),
                readUiSettings(buffer),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                readModuleSettings(buffer),
                readAdminAccess(buffer),
                buffer.readBoolean(),
                buffer.readBoolean(),
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


    private static void writeModuleSettings(RegistryFriendlyByteBuf buffer, ModuleSettingsSummary settings) {
        buffer.writeBoolean(settings.claims());
        buffer.writeBoolean(settings.homes());
        buffer.writeBoolean(settings.warps());
        buffer.writeBoolean(settings.regions());
        buffer.writeBoolean(settings.treecapitator());
        buffer.writeBoolean(settings.veinminer());
        buffer.writeBoolean(settings.cropHarvesting());
        buffer.writeBoolean(settings.holograms());
        buffer.writeBoolean(settings.blockInformation());
        buffer.writeBoolean(settings.statistics());
        buffer.writeBoolean(settings.mail());
        buffer.writeBoolean(settings.auctionHouse());
        buffer.writeBoolean(settings.npcs());
        buffer.writeBoolean(settings.quests());
        buffer.writeBoolean(settings.minigames());
        buffer.writeBoolean(settings.dungeons());
        buffer.writeUtf(settings.questAccessMode(), 16);
        buffer.writeUtf(settings.effectiveQuestAccessMode(), 16);
        buffer.writeBoolean(settings.permissions());
        buffer.writeBoolean(settings.remoteHologramImages());
        buffer.writeVarInt(settings.hologramRenderDistance());
        buffer.writeVarInt(settings.claimBorderRenderDistance());
        buffer.writeVarInt(settings.regionBorderRenderDistance());
    }

    private static ModuleSettingsSummary readModuleSettings(RegistryFriendlyByteBuf buffer) {
        return new ModuleSettingsSummary(
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readUtf(16), buffer.readUtf(16), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()
        );
    }

    private static void writeAdminAccess(RegistryFriendlyByteBuf buffer, AdminAccessSummary access) {
        buffer.writeBoolean(access.permissions());
        buffer.writeBoolean(access.core());
        buffer.writeBoolean(access.rentPolicy());
        buffer.writeBoolean(access.spawn());
    }

    private static AdminAccessSummary readAdminAccess(RegistryFriendlyByteBuf buffer) {
        return new AdminAccessSummary(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
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
        buffer.writeBoolean(settings.worldMapShowClaims());
        buffer.writeBoolean(settings.worldMapShowRegions());
        buffer.writeBoolean(settings.worldMapShowMarkers());
        buffer.writeBoolean(settings.minimapShowMarkers());
        buffer.writeBoolean(settings.minimapShowCalendar());
        buffer.writeBoolean(settings.worldMarkersVisible());
        buffer.writeBoolean(settings.markerBeamsVisible());
        buffer.writeVarInt(settings.markerBeamDistance());
        buffer.writeVarInt(settings.mapLiveUpdateRadiusChunks());
        buffer.writeBoolean(settings.blockInformationEnabled());
        buffer.writeBoolean(settings.blockInformationDebugAllowed());
        buffer.writeBoolean(settings.blockInformationDebugEnabled());
        buffer.writeBoolean(settings.mailAutoDeletePlayerAttachments());
        buffer.writeBoolean(settings.mailAutoDeleteSystemAttachments());
        buffer.writeBoolean(settings.mailAutoDeleteAuctionAttachments());
        buffer.writeBoolean(settings.treecapitatorEnabled());
        buffer.writeUtf(settings.treecapitatorActivation(), 16);
        buffer.writeInt(settings.treecapitatorOutlineColor());
        buffer.writeVarInt(settings.treecapitatorOutlineBrightness());
        buffer.writeBoolean(settings.treecapitatorInfoEnabled());
        buffer.writeBoolean(settings.veinminerEnabled());
        buffer.writeUtf(settings.veinminerActivation(), 16);
        buffer.writeInt(settings.veinminerOutlineColor());
        buffer.writeVarInt(settings.veinminerOutlineBrightness());
        buffer.writeBoolean(settings.veinminerInfoEnabled());
        buffer.writeBoolean(settings.titleVisible());
        buffer.writeBoolean(settings.rankVisible());
        buffer.writeBoolean(settings.damageIndicatorsEnabled());
        buffer.writeUtf(settings.damageIndicatorStyle(), 16);
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
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(16),
                buffer.readInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(16),
                buffer.readInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(16)
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
        buffer.writeVarInt(core.claimCount());
        buffer.writeVarInt(core.claimedChunkCount());
        buffer.writeVarInt(core.regionCount());
        buffer.writeVarInt(core.activeRentalCount());
        buffer.writeVarInt(core.homeCount());
        buffer.writeVarInt(core.maxHomes());
        buffer.writeVarInt(core.warpCount());
    }

    private static CoreSummary readCore(RegistryFriendlyByteBuf buffer) {
        return new CoreSummary(
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
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
        buffer.writeVarInt(economy.playerCancelRefundPercent());
        buffer.writeVarInt(economy.adminCancelRefundPercent());
        buffer.writeVarInt(economy.pendingRentOperations());
        buffer.writeVarInt(economy.transactionHistoryLimit());
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
        int playerCancelRefundPercent = buffer.readVarInt();
        int adminCancelRefundPercent = buffer.readVarInt();
        int pendingRentOperations = buffer.readVarInt();
        int transactionHistoryLimit = buffer.readVarInt();
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
                playerCancelRefundPercent,
                adminCancelRefundPercent,
                pendingRentOperations,
                transactionHistoryLimit,
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

    public record ModuleSettingsSummary(
            boolean claims,
            boolean homes,
            boolean warps,
            boolean regions,
            boolean treecapitator,
            boolean veinminer,
            boolean cropHarvesting,
            boolean holograms,
            boolean blockInformation,
            boolean statistics,
            boolean mail,
            boolean auctionHouse,
            boolean npcs,
            boolean quests,
            boolean minigames,
            boolean dungeons,
            String questAccessMode,
            String effectiveQuestAccessMode,
            boolean permissions,
            boolean remoteHologramImages,
            int hologramRenderDistance,
            int claimBorderRenderDistance,
            int regionBorderRenderDistance
    ) {
        public ModuleSettingsSummary {
            questAccessMode = questAccessMode == null ? "menu" : questAccessMode;
            effectiveQuestAccessMode = effectiveQuestAccessMode == null ? "menu" : effectiveQuestAccessMode;
            hologramRenderDistance = Math.max(8, Math.min(512, hologramRenderDistance));
            claimBorderRenderDistance = Math.max(16, Math.min(512, claimBorderRenderDistance));
            regionBorderRenderDistance = Math.max(16, Math.min(512, regionBorderRenderDistance));
        }

        public static ModuleSettingsSummary defaults() {
            return new ModuleSettingsSummary(true, true, true, true, true, true, true, true, true, true,
                    true, true, false, false, false, false, "menu", "menu", true, true, 64, 128, 128);
        }
    }

    public record AdminAccessSummary(boolean permissions, boolean core, boolean rentPolicy, boolean spawn) {
        public static AdminAccessSummary none() {
            return new AdminAccessSummary(false, false, false, false);
        }
    }

    public record UiSettingsSummary(
            boolean dashboardHints,
            boolean minimapEnabled,
            int minimapSize,
            String minimapShape,
            String minimapPosition,
            boolean minimapNorthUp,
            boolean minimapShowClaims,
            boolean minimapShowRegions,
            boolean worldMapShowClaims,
            boolean worldMapShowRegions,
            boolean worldMapShowMarkers,
            boolean minimapShowMarkers,
            boolean minimapShowCalendar,
            boolean worldMarkersVisible,
            boolean markerBeamsVisible,
            int markerBeamDistance,
            int mapLiveUpdateRadiusChunks,
            boolean blockInformationEnabled,
            boolean blockInformationDebugAllowed,
            boolean blockInformationDebugEnabled,
            boolean mailAutoDeletePlayerAttachments,
            boolean mailAutoDeleteSystemAttachments,
            boolean mailAutoDeleteAuctionAttachments,
            boolean treecapitatorEnabled,
            String treecapitatorActivation,
            int treecapitatorOutlineColor,
            int treecapitatorOutlineBrightness,
            boolean treecapitatorInfoEnabled,
            boolean veinminerEnabled,
            String veinminerActivation,
            int veinminerOutlineColor,
            int veinminerOutlineBrightness,
            boolean veinminerInfoEnabled,
            boolean titleVisible,
            boolean rankVisible,
            boolean damageIndicatorsEnabled,
            String damageIndicatorStyle
    ) {
        public UiSettingsSummary {
            minimapSize = Math.max(64, Math.min(256, minimapSize));
            minimapShape = minimapShape == null ? "CIRCLE" : minimapShape;
            minimapPosition = minimapPosition == null ? "TOP_RIGHT" : minimapPosition;
            markerBeamDistance = Math.max(16, Math.min(512, markerBeamDistance));
            mapLiveUpdateRadiusChunks = Math.max(1, Math.min(32, mapLiveUpdateRadiusChunks));
            treecapitatorActivation = treecapitatorActivation == null ? "SNEAK" : treecapitatorActivation;
            veinminerActivation = veinminerActivation == null ? "SNEAK" : veinminerActivation;
            treecapitatorOutlineBrightness = Math.max(10, Math.min(100, treecapitatorOutlineBrightness));
            veinminerOutlineBrightness = Math.max(10, Math.min(100, veinminerOutlineBrightness));
            damageIndicatorStyle = damageIndicatorStyle == null ? "FLOATING" : damageIndicatorStyle;
        }

        public static UiSettingsSummary defaults() {
            return new UiSettingsSummary(
                    true, false, 96, "CIRCLE", "TOP_RIGHT",
                    true, true, true, true, true,
                    true, true, false, true, true, 128,
                    8,
                    true, false, false,
                    false, false, false,
                    false, "SNEAK", 0xFF55FF77, 85, true,
                    false, "SNEAK", 0xFF55AAFF, 85, true,
                    true, true, true, "FLOATING"
            );
        }
    }

    public record CoreSummary(
            long permissionChecks,
            int permissionCacheHitPermille,
            int permissionCacheEntries,
            long regionLookups,
            double averageRegionCandidates,
            int regionIndexCells,
            int regionIndexReferences,
            int claimCount,
            int claimedChunkCount,
            int regionCount,
            int activeRentalCount,
            int homeCount,
            int maxHomes,
            int warpCount
    ) {
        public CoreSummary {
            permissionChecks = Math.max(0L, permissionChecks);
            permissionCacheHitPermille = Math.max(0, Math.min(1000, permissionCacheHitPermille));
            permissionCacheEntries = Math.max(0, permissionCacheEntries);
            regionLookups = Math.max(0L, regionLookups);
            averageRegionCandidates = Math.max(0.0D, averageRegionCandidates);
            regionIndexCells = Math.max(0, regionIndexCells);
            regionIndexReferences = Math.max(0, regionIndexReferences);
            claimCount = Math.max(0, claimCount);
            claimedChunkCount = Math.max(0, claimedChunkCount);
            regionCount = Math.max(0, regionCount);
            activeRentalCount = Math.max(0, activeRentalCount);
            homeCount = Math.max(0, homeCount);
            maxHomes = Math.max(0, maxHomes);
            warpCount = Math.max(0, warpCount);
        }

        public static CoreSummary empty() {
            return new CoreSummary(0L, 0, 0, 0L, 0.0D, 0, 0, 0, 0, 0, 0, 0, 0, 0);
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
            int playerCancelRefundPercent,
            int adminCancelRefundPercent,
            int pendingRentOperations,
            int transactionHistoryLimit,
            List<TransactionSummary> recentTransactions
    ) {
        public EconomySummary {
            formattedBalance = formattedBalance == null ? "" : formattedBalance;
            balanceMinor = Math.max(0L, balanceMinor);
            accountCount = Math.max(0, accountCount);
            formattedTotalSupply = formattedTotalSupply == null ? "" : formattedTotalSupply;
            playerCancelRefundPercent = Math.max(0, Math.min(100, playerCancelRefundPercent));
            adminCancelRefundPercent = Math.max(0, Math.min(100, adminCancelRefundPercent));
            pendingRentOperations = Math.max(0, pendingRentOperations);
            transactionHistoryLimit = Math.max(1, Math.min(1_000, transactionHistoryLimit));
            recentTransactions = recentTransactions == null ? List.of() : List.copyOf(recentTransactions);
            if (recentTransactions.size() > 20) {
                throw new IllegalArgumentException("Too many economy transactions in SSU menu snapshot.");
            }
        }

        public static EconomySummary empty() {
            return new EconomySummary(false, "", 0L, false, false,
                    0, "", 0, 100, 0, 50, List.of());
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
