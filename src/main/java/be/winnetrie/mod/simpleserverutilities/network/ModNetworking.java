package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.claim.map.ClaimMapService;
import be.winnetrie.mod.simpleserverutilities.claim.map.MinimapService;
import be.winnetrie.mod.simpleserverutilities.claim.map.WorldMapService;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModNetworking {

    private ModNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(SimpleServerUtilities.MODID).versioned("119");

        registrar.playToClient(
                ClaimMapDataPayload.TYPE,
                ClaimMapDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                ClaimMapRequestPayload.TYPE,
                ClaimMapRequestPayload.STREAM_CODEC,
                ClaimMapService::handleRequest
        );

        registrar.playToServer(
                ClaimMapActionPayload.TYPE,
                ClaimMapActionPayload.STREAM_CODEC,
                ClaimMapService::handleAction
        );

        registrar.playToServer(
                ClaimTaxDeleteActionPayload.TYPE,
                ClaimTaxDeleteActionPayload.STREAM_CODEC,
                ClaimMapService::handleTaxDelete
        );

        registrar.playToClient(
                BorderVisualizationPayload.TYPE,
                BorderVisualizationPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                SsuMenuSnapshotPayload.TYPE,
                SsuMenuSnapshotPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                EntityInsightPayload.TYPE,
                EntityInsightPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                SsuMenuPageRequestPayload.TYPE,
                SsuMenuPageRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MENUS.handlePageRequest(payload, context)
        );

        registrar.playToClient(
                SsuMenuPageDataPayload.TYPE,
                SsuMenuPageDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                SsuPermissionEditorRequestPayload.TYPE,
                SsuPermissionEditorRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MENUS.handlePermissionEditorRequest(payload, context)
        );

        registrar.playToClient(
                SsuPermissionEditorDataPayload.TYPE,
                SsuPermissionEditorDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                SsuDimensionManagerRequestPayload.TYPE,
                SsuDimensionManagerRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.dimension.ManagedDimensionService::handleRequest
        );

        registrar.playToServer(
                SsuDimensionManagerSubmitPayload.TYPE,
                SsuDimensionManagerSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.dimension.ManagedDimensionService::handleSubmit
        );

        registrar.playToClient(
                SsuDimensionManagerDataPayload.TYPE,
                SsuDimensionManagerDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                SsuPlayerProfileRequestPayload.TYPE,
                SsuPlayerProfileRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MENUS.handlePlayerProfileRequest(payload, context)
        );

        registrar.playToClient(
                SsuPlayerProfileDataPayload.TYPE,
                SsuPlayerProfileDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                SsuMenuActionPayload.TYPE,
                SsuMenuActionPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MENUS.handleAction(payload, context)
        );

        registrar.playToServer(
                PlayerUiSettingUpdatePayload.TYPE,
                PlayerUiSettingUpdatePayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MENUS.handlePlayerUiSettingUpdate(payload, context)
        );

        registrar.playToClient(
                SsuMenuActionResultPayload.TYPE,
                SsuMenuActionResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                SsuPropertySettingsRequestPayload.TYPE,
                SsuPropertySettingsRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.menu.PropertySettingsService::handleRequest
        );

        registrar.playToServer(
                SsuPropertySettingsActionPayload.TYPE,
                SsuPropertySettingsActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.menu.PropertySettingsService::handleAction
        );

        registrar.playToClient(
                SsuPropertySettingsDataPayload.TYPE,
                SsuPropertySettingsDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                SsuTrustedPlayersRequestPayload.TYPE,
                SsuTrustedPlayersRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.menu.TrustedPlayersService::handleRequest
        );

        registrar.playToServer(
                SsuTrustedPlayersActionPayload.TYPE,
                SsuTrustedPlayersActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.menu.TrustedPlayersService::handleAction
        );

        registrar.playToServer(
                SsuClaimRolePermissionActionPayload.TYPE,
                SsuClaimRolePermissionActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.menu.TrustedPlayersService::handleRolePermissionAction
        );

        registrar.playToClient(
                SsuTrustedPlayersDataPayload.TYPE,
                SsuTrustedPlayersDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                MinimapRequestPayload.TYPE,
                MinimapRequestPayload.STREAM_CODEC,
                MinimapService::handleRequest
        );

        registrar.playToClient(
                MinimapDataPayload.TYPE,
                MinimapDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                WorldMapRequestPayload.TYPE,
                WorldMapRequestPayload.STREAM_CODEC,
                WorldMapService::handleRequest
        );

        registrar.playToClient(
                WorldMapDataPayload.TYPE,
                WorldMapDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MapMarkerSyncPayload.TYPE,
                MapMarkerSyncPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                MapMarkerActionPayload.TYPE,
                MapMarkerActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.mapmarker.MapMarkerService::handleAction
        );

        registrar.playToClient(
                MapMarkerActionResultPayload.TYPE,
                MapMarkerActionResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                AuctionHouseDataPayload.TYPE,
                AuctionHouseDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                AuctionHouseActionResultPayload.TYPE,
                AuctionHouseActionResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                AuctionHouseRequestPayload.TYPE,
                AuctionHouseRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.AUCTION_HOUSE.handleRequest(payload, context)
        );

        registrar.playToServer(
                AuctionHouseActionPayload.TYPE,
                AuctionHouseActionPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.AUCTION_HOUSE.handleAction(payload, context)
        );

        registrar.playToClient(
                MailDataPayload.TYPE,
                MailDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MailComposeResultPayload.TYPE,
                MailComposeResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MailRecipientSuggestionsPayload.TYPE,
                MailRecipientSuggestionsPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                MailRequestPayload.TYPE,
                MailRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MAIL.handleRequest(payload, context)
        );

        registrar.playToServer(
                MailActionPayload.TYPE,
                MailActionPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MAIL.handleAction(payload, context)
        );

        registrar.playToServer(
                MailComposeSubmitPayload.TYPE,
                MailComposeSubmitPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MAIL.handleCompose(payload, context)
        );

        registrar.playToServer(
                MailRecipientSuggestionsRequestPayload.TYPE,
                MailRecipientSuggestionsRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MAIL.handleRecipientSuggestions(payload, context)
        );

        registrar.playToServer(
                UtilityMiningActivationPayload.TYPE,
                UtilityMiningActivationPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.UTILITY_MINING.handleActivation(payload, context)
        );

        registrar.playToServer(
                UtilityMiningPreviewRequestPayload.TYPE,
                UtilityMiningPreviewRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.UTILITY_MINING.handlePreview(payload, context)
        );

        registrar.playToClient(
                UtilityMiningPreviewPayload.TYPE,
                UtilityMiningPreviewPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                HologramSyncPayload.TYPE,
                HologramSyncPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                HologramEditorOpenPayload.TYPE,
                HologramEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                HologramEditorRequestPayload.TYPE,
                HologramEditorRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.hologram.HologramEditorService::handleOpenRequest
        );

        registrar.playToServer(
                HologramEditorSubmitPayload.TYPE,
                HologramEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.hologram.HologramEditorService::handleSubmit
        );

        registrar.playToClient(
                HologramEditorResultPayload.TYPE,
                HologramEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                NpcLabelSyncPayload.TYPE,
                NpcLabelSyncPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                NpcTextureSyncPayload.TYPE,
                NpcTextureSyncPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                NpcEditorOpenPayload.TYPE,
                NpcEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcEditorSubmitPayload.TYPE,
                NpcEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcEditorService::handleSubmit
        );

        registrar.playToClient(
                NpcEditorResultPayload.TYPE,
                NpcEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcLoadoutOpenRequestPayload.TYPE,
                NpcLoadoutOpenRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcLoadoutService::handleOpen
        );

        registrar.playToServer(
                NpcLoadoutSavePayload.TYPE,
                NpcLoadoutSavePayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcLoadoutService::handleSave
        );

        registrar.playToClient(
                NpcLoadoutResultPayload.TYPE,
                NpcLoadoutResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcAdminListRequestPayload.TYPE,
                NpcAdminListRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcAdminService::handleList
        );

        registrar.playToClient(
                NpcAdminListPayload.TYPE,
                NpcAdminListPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcAdminActionPayload.TYPE,
                NpcAdminActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcAdminService::handleAction
        );

        registrar.playToClient(
                NpcSpawnProfileEditorOpenPayload.TYPE,
                NpcSpawnProfileEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcSpawnProfileEditorSubmitPayload.TYPE,
                NpcSpawnProfileEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcSpawnProfileEditorService::handleSubmit
        );

        registrar.playToClient(
                NpcSpawnProfileEditorResultPayload.TYPE,
                NpcSpawnProfileEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );


        registrar.playToClient(
                NpcDialogueViewPayload.TYPE,
                NpcDialogueViewPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcDialogueChoicePayload.TYPE,
                NpcDialogueChoicePayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueService::handleChoice
        );

        registrar.playToClient(
                NpcFunctionMenuPayload.TYPE,
                NpcFunctionMenuPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcFunctionUsePayload.TYPE,
                NpcFunctionUsePayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcFunctionService::handleUse
        );

        registrar.playToClient(
                NpcShopDataPayload.TYPE,
                NpcShopDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcShopRequestPayload.TYPE,
                NpcShopRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopManager::handleRequest
        );

        registrar.playToServer(
                NpcShopActionPayload.TYPE,
                NpcShopActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopManager::handleAction
        );

        registrar.playToServer(
                NpcShopAdminRequestPayload.TYPE,
                NpcShopAdminRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopEditorService::handleAdminRequest
        );

        registrar.playToClient(
                NpcShopAdminDataPayload.TYPE,
                NpcShopAdminDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcShopAdminActionPayload.TYPE,
                NpcShopAdminActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopEditorService::handleAdminAction
        );

        registrar.playToClient(
                NpcShopEditorOpenPayload.TYPE,
                NpcShopEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcShopEditorSubmitPayload.TYPE,
                NpcShopEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npcshop.NpcShopEditorService::handleEditorSubmit
        );

        registrar.playToClient(
                NpcShopEditorResultPayload.TYPE,
                NpcShopEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcAbilityLibraryRequestPayload.TYPE,
                NpcAbilityLibraryRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcAbilityEditorService::handleRequest
        );

        registrar.playToClient(
                NpcAbilityLibraryDataPayload.TYPE,
                NpcAbilityLibraryDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcAbilityLibraryActionPayload.TYPE,
                NpcAbilityLibraryActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcAbilityEditorService::handleAction
        );

        registrar.playToClient(
                NpcAbilityEditorOpenPayload.TYPE,
                NpcAbilityEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcAbilityEditorSubmitPayload.TYPE,
                NpcAbilityEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcAbilityEditorService::handleSubmit
        );

        registrar.playToClient(
                NpcAbilityEditorResultPayload.TYPE,
                NpcAbilityEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcItemPriceCatalogRequestPayload.TYPE,
                NpcItemPriceCatalogRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npcshop.NpcItemPriceCatalogService::handleRequest
        );

        registrar.playToServer(
                NpcItemPriceCatalogActionPayload.TYPE,
                NpcItemPriceCatalogActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npcshop.NpcItemPriceCatalogService::handleAction
        );

        registrar.playToClient(
                NpcItemPriceCatalogDataPayload.TYPE,
                NpcItemPriceCatalogDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcDialogueEditorRequestPayload.TYPE,
                NpcDialogueEditorRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueService::handleEditorRequest
        );

        registrar.playToClient(
                NpcDialogueEditorOpenPayload.TYPE,
                NpcDialogueEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcDialogueEditorSubmitPayload.TYPE,
                NpcDialogueEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.npc.NpcDialogueService::handleEditorSubmit
        );

        registrar.playToClient(
                NpcDialogueEditorResultPayload.TYPE,
                NpcDialogueEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcQuestWorkflowRequestPayload.TYPE,
                NpcQuestWorkflowRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.quest.NpcQuestWorkflowService::handleRequest
        );

        registrar.playToClient(
                NpcQuestWorkflowOpenPayload.TYPE,
                NpcQuestWorkflowOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                NpcQuestWorkflowUpdatePayload.TYPE,
                NpcQuestWorkflowUpdatePayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.quest.NpcQuestWorkflowService::handleUpdate
        );

        registrar.playToServer(
                QuestBookRequestPayload.TYPE,
                QuestBookRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.QUESTS.handleRequest(payload, context)
        );

        registrar.playToClient(
                QuestBookDataPayload.TYPE,
                QuestBookDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                QuestEditorRequestPayload.TYPE,
                QuestEditorRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.quest.QuestEditorService::handleRequest
        );

        registrar.playToClient(
                QuestEditorOpenPayload.TYPE,
                QuestEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                QuestEditorSubmitPayload.TYPE,
                QuestEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.quest.QuestEditorService::handleSubmit
        );

        registrar.playToClient(
                QuestEditorResultPayload.TYPE,
                QuestEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                MinigameLobbyRequestPayload.TYPE,
                MinigameLobbyRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MINIGAMES.handleRequest(payload, context)
        );

        registrar.playToClient(
                MinigameLobbyDataPayload.TYPE,
                MinigameLobbyDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                MinigameMatchOverviewRequestPayload.TYPE,
                MinigameMatchOverviewRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MINIGAMES.handleMatchOverviewRequest(payload, context)
        );

        registrar.playToClient(
                MinigameMatchOverviewPayload.TYPE,
                MinigameMatchOverviewPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                MinigameScoreActionPayload.TYPE,
                MinigameScoreActionPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MINIGAMES.handleScoreAction(payload, context)
        );

        registrar.playToServer(
                MinigameEditorRequestPayload.TYPE,
                MinigameEditorRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.minigame.MinigameEditorService::handleRequest
        );

        registrar.playToClient(
                MinigameEditorOpenPayload.TYPE,
                MinigameEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                MinigameEditorSubmitPayload.TYPE,
                MinigameEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.minigame.MinigameEditorService::handleSubmit
        );

        registrar.playToServer(
                MinigameRewardCapturePayload.TYPE,
                MinigameRewardCapturePayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.minigame.MinigameEditorService::handleRewardCapture
        );

        registrar.playToClient(
                MinigameEditorResultPayload.TYPE,
                MinigameEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MinigameHudPayload.TYPE,
                MinigameHudPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MinigameKothVisualPayload.TYPE,
                MinigameKothVisualPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MinigameCastBarPayload.TYPE,
                MinigameCastBarPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MinigameDominationVisualPayload.TYPE,
                MinigameDominationVisualPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MinigameCtfVisualPayload.TYPE,
                MinigameCtfVisualPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                MinigameSelectionCreatePayload.TYPE,
                MinigameSelectionCreatePayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.minigame.MinigameSelectionService::handleCreate
        );

        registrar.playToClient(
                MinigameSelectionCreateResultPayload.TYPE,
                MinigameSelectionCreateResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                MinigameSetupToolConfigurePayload.TYPE,
                MinigameSetupToolConfigurePayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.minigame.MinigameSetupToolService::handleConfigure
        );

        registrar.playToClient(
                MinigameSetupToolOpenPayload.TYPE,
                MinigameSetupToolOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MinigameSetupVisualPayload.TYPE,
                MinigameSetupVisualPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MinigameResultsPayload.TYPE,
                MinigameResultsPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MinigameProfilePayload.TYPE,
                MinigameProfilePayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MinigameKillFeedPayload.TYPE,
                MinigameKillFeedPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                MinigameSpectatorActionPayload.TYPE,
                MinigameSpectatorActionPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MINIGAMES.handleSpectatorAction(payload, context)
        );

        registrar.playToClient(
                MinigameValidationPayload.TYPE,
                MinigameValidationPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                MinigameDiagnosticsPayload.TYPE,
                MinigameDiagnosticsPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                DungeonLobbyRequestPayload.TYPE,
                DungeonLobbyRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.DUNGEONS.handleRequest(payload, context)
        );

        registrar.playToClient(
                DungeonLobbyDataPayload.TYPE,
                DungeonLobbyDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                DungeonEditorRequestPayload.TYPE,
                DungeonEditorRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.dungeon.DungeonEditorService::handleRequest
        );

        registrar.playToClient(
                DungeonEditorOpenPayload.TYPE,
                DungeonEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                DungeonEditorSubmitPayload.TYPE,
                DungeonEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.dungeon.DungeonEditorService::handleSubmit
        );

        registrar.playToClient(
                DungeonEditorResultPayload.TYPE,
                DungeonEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                RegionSetupOpenPayload.TYPE,
                RegionSetupOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                RegionSnapshotPreviewPayload.TYPE,
                RegionSnapshotPreviewPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                RegionSetupRequestPayload.TYPE,
                RegionSetupRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.region.RegionSetupToolService::handleRequest
        );

        registrar.playToServer(
                RegionSetupSavePayload.TYPE,
                RegionSetupSavePayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.region.RegionSetupToolService::handleSave
        );

        registrar.playToServer(
                RegionSetupActionPayload.TYPE,
                RegionSetupActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.region.RegionSetupToolService::handleAction
        );

        registrar.playToClient(
                RegionEditorOpenPayload.TYPE,
                RegionEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                RegionEditorSubmitPayload.TYPE,
                RegionEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.region.RegionEditorService::handleSubmit
        );

        registrar.playToClient(
                RegionEditorResultPayload.TYPE,
                RegionEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );


        registrar.playToClient(
                RegionSelectionToolOpenPayload.TYPE,
                RegionSelectionToolOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                RegionSelectionActionPayload.TYPE,
                RegionSelectionActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.region.RegionSelectionToolService::handleAction
        );

        registrar.playToClient(
                RegionSelectionActionResultPayload.TYPE,
                RegionSelectionActionResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                RegionSelectionClientTemplatePayload.TYPE,
                RegionSelectionClientTemplatePayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                RegionSelectionClientTemplateUploadPayload.TYPE,
                RegionSelectionClientTemplateUploadPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.region.RegionSelectionToolService::handleClientTemplateUpload
        );

        registrar.playToClient(
                BlockInformationStatePayload.TYPE,
                BlockInformationStatePayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                BlockInformationContentPayload.TYPE,
                BlockInformationContentPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                StatisticEditorOpenPayload.TYPE,
                StatisticEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                StatisticEditorSubmitPayload.TYPE,
                StatisticEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.statistics.StatisticEditorService::handleSubmit
        );

        registrar.playToClient(
                StatisticEditorResultPayload.TYPE,
                StatisticEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                TitleManagerRequestPayload.TYPE,
                TitleManagerRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.IDENTITY.handleTitleRequest(payload, context)
        );

        registrar.playToServer(
                TitleManagerActionPayload.TYPE,
                TitleManagerActionPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.IDENTITY.handleTitleAction(payload, context)
        );

        registrar.playToClient(
                TitleManagerDataPayload.TYPE,
                TitleManagerDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(
                RankDisplayRequestPayload.TYPE,
                RankDisplayRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.IDENTITY.handleRankDisplayRequest(payload, context)
        );

        registrar.playToServer(
                RankDisplaySavePayload.TYPE,
                RankDisplaySavePayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.IDENTITY.handleRankDisplaySave(payload, context)
        );

        registrar.playToClient(
                RankDisplayDataPayload.TYPE,
                RankDisplayDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                PlayerIdentitySyncPayload.TYPE,
                PlayerIdentitySyncPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(
                DamageIndicatorPayload.TYPE,
                DamageIndicatorPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToClient(OnboardingStatePayload.TYPE, OnboardingStatePayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );
        registrar.playToServer(OnboardingActionPayload.TYPE, OnboardingActionPayload.STREAM_CODEC,
                (p,c) -> { if (c.player() instanceof net.minecraft.server.level.ServerPlayer player) SimpleServerUtilities.ONBOARDING.handleAction(player,p.action(),p.pageIndex(),p.requestId()); });
        registrar.playToServer(OnboardingAdminRequestPayload.TYPE, OnboardingAdminRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.onboarding.OnboardingService::handleRequest);
        registrar.playToServer(OnboardingAdminSavePayload.TYPE, OnboardingAdminSavePayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.onboarding.OnboardingService::handleSave);
        registrar.playToClient(OnboardingAdminDataPayload.TYPE, OnboardingAdminDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(PlayerManagementRequestPayload.TYPE, PlayerManagementRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.moderation.ModerationService::request);
        registrar.playToServer(PlayerManagementActionPayload.TYPE, PlayerManagementActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.moderation.ModerationService::action);
        registrar.playToClient(PlayerManagementDataPayload.TYPE, PlayerManagementDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );
        registrar.playToClient(JailDashboardPayload.TYPE, JailDashboardPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );
        registrar.playToServer(JailDashboardActionPayload.TYPE, JailDashboardActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.moderation.ModerationService::jailAction);
        registrar.playToServer(PlayerInventoryOpenPayload.TYPE, PlayerInventoryOpenPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.moderation.ModerationService::openInventory);

        registrar.playToServer(KitRequestPayload.TYPE, KitRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.kits.KitService::request);
        registrar.playToServer(KitActionPayload.TYPE, KitActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.kits.KitService::action);
        registrar.playToClient(KitDataPayload.TYPE, KitDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );
        registrar.playToServer(KitContentsSavePayload.TYPE, KitContentsSavePayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.kits.KitEditorService::save);
        registrar.playToClient(KitContentsResultPayload.TYPE, KitContentsResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(JailAdminRequestPayload.TYPE, JailAdminRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.jail.JailService::request);
        registrar.playToServer(JailAdminActionPayload.TYPE, JailAdminActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.jail.JailService::action);
        registrar.playToClient(JailAdminDataPayload.TYPE, JailAdminDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(MineRequestPayload.TYPE, MineRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.mine.MineService::request);
        registrar.playToServer(MineActionPayload.TYPE, MineActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.mine.MineService::action);
        registrar.playToClient(MineDataPayload.TYPE, MineDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(ServerOperationsRequestPayload.TYPE, ServerOperationsRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.serverops.ServerOperationsService::request);
        registrar.playToServer(ServerOperationsActionPayload.TYPE, ServerOperationsActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.serverops.ServerOperationsService::action);
        registrar.playToClient(ServerOperationsDataPayload.TYPE, ServerOperationsDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

        registrar.playToServer(AchievementMenuRequestPayload.TYPE, AchievementMenuRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.ACHIEVEMENTS.handleRequest(payload, context));
        registrar.playToClient(AchievementMenuDataPayload.TYPE, AchievementMenuDataPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );
        registrar.playToServer(AchievementEditorRequestPayload.TYPE, AchievementEditorRequestPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.achievement.AchievementEditorService::handleRequest);
        registrar.playToClient(AchievementEditorOpenPayload.TYPE, AchievementEditorOpenPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );
        registrar.playToServer(AchievementEditorSubmitPayload.TYPE, AchievementEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.achievement.AchievementEditorService::handleSubmit);
        registrar.playToClient(AchievementEditorResultPayload.TYPE, AchievementEditorResultPayload.STREAM_CODEC,
                ClientPayloadRouter::handle
        );

    }
}
