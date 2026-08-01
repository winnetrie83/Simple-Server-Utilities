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
        PayloadRegistrar registrar = event.registrar(SimpleServerUtilities.MODID).versioned("39");

        registrar.playToClient(
                ClaimMapDataPayload.TYPE,
                ClaimMapDataPayload.STREAM_CODEC
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

        registrar.playToClient(
                BorderVisualizationPayload.TYPE,
                BorderVisualizationPayload.STREAM_CODEC
        );

        registrar.playToClient(
                SsuMenuSnapshotPayload.TYPE,
                SsuMenuSnapshotPayload.STREAM_CODEC
        );

        registrar.playToServer(
                SsuMenuPageRequestPayload.TYPE,
                SsuMenuPageRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MENUS.handlePageRequest(payload, context)
        );

        registrar.playToClient(
                SsuMenuPageDataPayload.TYPE,
                SsuMenuPageDataPayload.STREAM_CODEC
        );

        registrar.playToServer(
                SsuPermissionEditorRequestPayload.TYPE,
                SsuPermissionEditorRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MENUS.handlePermissionEditorRequest(payload, context)
        );

        registrar.playToClient(
                SsuPermissionEditorDataPayload.TYPE,
                SsuPermissionEditorDataPayload.STREAM_CODEC
        );

        registrar.playToServer(
                SsuPlayerProfileRequestPayload.TYPE,
                SsuPlayerProfileRequestPayload.STREAM_CODEC,
                (payload, context) -> SimpleServerUtilities.MENUS.handlePlayerProfileRequest(payload, context)
        );

        registrar.playToClient(
                SsuPlayerProfileDataPayload.TYPE,
                SsuPlayerProfileDataPayload.STREAM_CODEC
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
                SsuMenuActionResultPayload.STREAM_CODEC
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
                SsuPropertySettingsDataPayload.STREAM_CODEC
        );

        registrar.playToServer(
                MinimapRequestPayload.TYPE,
                MinimapRequestPayload.STREAM_CODEC,
                MinimapService::handleRequest
        );

        registrar.playToClient(
                MinimapDataPayload.TYPE,
                MinimapDataPayload.STREAM_CODEC
        );

        registrar.playToServer(
                WorldMapRequestPayload.TYPE,
                WorldMapRequestPayload.STREAM_CODEC,
                WorldMapService::handleRequest
        );

        registrar.playToClient(
                WorldMapDataPayload.TYPE,
                WorldMapDataPayload.STREAM_CODEC
        );

        registrar.playToClient(
                MapMarkerSyncPayload.TYPE,
                MapMarkerSyncPayload.STREAM_CODEC
        );

        registrar.playToServer(
                MapMarkerActionPayload.TYPE,
                MapMarkerActionPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.mapmarker.MapMarkerService::handleAction
        );

        registrar.playToClient(
                MapMarkerActionResultPayload.TYPE,
                MapMarkerActionResultPayload.STREAM_CODEC
        );

        registrar.playToClient(
                AuctionHouseDataPayload.TYPE,
                AuctionHouseDataPayload.STREAM_CODEC
        );

        registrar.playToClient(
                AuctionHouseActionResultPayload.TYPE,
                AuctionHouseActionResultPayload.STREAM_CODEC
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
                MailDataPayload.STREAM_CODEC
        );

        registrar.playToClient(
                MailComposeResultPayload.TYPE,
                MailComposeResultPayload.STREAM_CODEC
        );

        registrar.playToClient(
                MailRecipientSuggestionsPayload.TYPE,
                MailRecipientSuggestionsPayload.STREAM_CODEC
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
                UtilityMiningPreviewPayload.STREAM_CODEC
        );

        registrar.playToClient(
                HologramSyncPayload.TYPE,
                HologramSyncPayload.STREAM_CODEC
        );

        registrar.playToClient(
                HologramEditorOpenPayload.TYPE,
                HologramEditorOpenPayload.STREAM_CODEC
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
                HologramEditorResultPayload.STREAM_CODEC
        );

        registrar.playToClient(
                RegionEditorOpenPayload.TYPE,
                RegionEditorOpenPayload.STREAM_CODEC
        );

        registrar.playToServer(
                RegionEditorSubmitPayload.TYPE,
                RegionEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.region.RegionEditorService::handleSubmit
        );

        registrar.playToClient(
                RegionEditorResultPayload.TYPE,
                RegionEditorResultPayload.STREAM_CODEC
        );

        registrar.playToClient(
                BlockInformationStatePayload.TYPE,
                BlockInformationStatePayload.STREAM_CODEC
        );

        registrar.playToClient(
                BlockInformationContentPayload.TYPE,
                BlockInformationContentPayload.STREAM_CODEC
        );

        registrar.playToClient(
                StatisticEditorOpenPayload.TYPE,
                StatisticEditorOpenPayload.STREAM_CODEC
        );

        registrar.playToServer(
                StatisticEditorSubmitPayload.TYPE,
                StatisticEditorSubmitPayload.STREAM_CODEC,
                be.winnetrie.mod.simpleserverutilities.statistics.StatisticEditorService::handleSubmit
        );

        registrar.playToClient(
                StatisticEditorResultPayload.TYPE,
                StatisticEditorResultPayload.STREAM_CODEC
        );

    }
}
