package be.winnetrie.mod.simpleserverutilities.minigame;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.mail.MailItemCodec;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.network.MinigameRewardCapturePayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative bridge for mode-specific minigame editors. */
public final class MinigameEditorService {
    private MinigameEditorService() {
    }

    public static void handleRequest(MinigameEditorRequestPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("minigames")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> open(player, payload.minigameId(), payload.requestId()));
    }

    public static void handleSubmit(MinigameEditorSubmitPayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("minigames")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!canAdmin(player)) {
                sendResult(player, false, "Minigame administrator permission is required.", "", payload.requestId());
                return;
            }
            try {
                MinigameDefinition definition = SimpleServerUtilities.MINIGAMES.fromJson(payload.definitionJson());
                if (!SimpleServerUtilities.MINIGAMES.saveDefinition(payload.originalMinigameId(), definition)) {
                    sendResult(player, false, "The minigame ID already exists or the library limit was reached.", "", payload.requestId());
                    return;
                }
                sendResult(player, true, "Minigame '" + definition.displayName + "' saved.", definition.id, payload.requestId());
            } catch (RuntimeException exception) {
                sendResult(player, false, exception.getMessage() == null ? "Minigame validation failed." : exception.getMessage(), "", payload.requestId());
            }
        });
    }

    public static void handleRewardCapture(MinigameRewardCapturePayload payload, IPayloadContext context) {
        if (!be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess.active("minigames")) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (!canAdmin(player)) {
                sendResult(player, false, "Minigame administrator permission is required.", "", payload.requestId());
                return;
            }
            try {
                if (payload.inventorySlot() < 0 || payload.inventorySlot() >= 36) {
                    throw new IllegalArgumentException("Select a valid inventory slot.");
                }
                ItemStack selected = player.getInventory().getItem(payload.inventorySlot());
                if (selected == null || selected.isEmpty()) {
                    throw new IllegalArgumentException("That inventory slot is empty or changed.");
                }
                MinigameDefinition definition = SimpleServerUtilities.MINIGAMES.fromDraftJson(payload.definitionJson());
                MinigameRewardSet rewards = "winner".equals(payload.rewardKind())
                        ? definition.winnerReward : definition.participationReward;
                int target = payload.rewardSlot();
                ItemStack copied;
                String message;
                if (payload.addOne()) {
                    ItemStack existing = ItemStack.EMPTY;
                    var encodedExisting = rewards.itemAt(target);
                    if (encodedExisting != null) {
                        existing = MailItemCodec.decode(player.level().registryAccess(), encodedExisting);
                    }
                    if (existing.isEmpty()) {
                        copied = selected.copy();
                        copied.setCount(1);
                    } else {
                        if (!ItemStack.isSameItemSameComponents(existing, selected)) {
                            throw new IllegalArgumentException("Right-click can only add one item to an empty slot or the same item stack.");
                        }
                        if (existing.getCount() >= existing.getMaxStackSize()) {
                            throw new IllegalArgumentException("That reward stack is already full.");
                        }
                        copied = existing.copy();
                        copied.grow(1);
                    }
                    message = "Added one " + selected.getHoverName().getString()
                            + " to reward slot " + (target + 1) + ".";
                } else {
                    copied = selected.copy();
                    message = "Copied the full " + selected.getHoverName().getString()
                            + " stack into reward slot " + (target + 1) + ".";
                }
                rewards.setItem(target, MailItemCodec.encode(player.level().registryAccess(), copied));
                sendEditor(player, payload.originalMinigameId(), definition, message, payload.requestId());
            } catch (RuntimeException exception) {
                sendResult(player, false, exception.getMessage() == null ? "Reward item could not be copied." : exception.getMessage(), "", payload.requestId());
            }
        });
    }

    public static void open(ServerPlayer player, String minigameId) {
        open(player, minigameId, 0L);
    }

    private static void open(ServerPlayer player, String minigameId, long requestId) {
        if (!canAdmin(player)) {
            player.sendSystemMessage(Component.literal("Minigame administrator permission is required."));
            return;
        }
        String id = minigameId == null ? "" : minigameId.trim();
        if (id.isBlank()) {
            SimpleServerUtilities.MINIGAME_SETUP_TOOLS.giveTool(player);
            MinigameSetupToolService.open(player,
                    "Use the Setup Tool to select arena bounds and create a supported game mode.", false, requestId);
            return;
        }
        MinigameDefinition definition = SimpleServerUtilities.MINIGAMES.copy(SimpleServerUtilities.MINIGAMES.definition(id));
        if (definition == null) {
            player.sendSystemMessage(Component.literal("That minigame no longer exists."));
            return;
        }
        definition.normalize();
        sendEditor(player, id, definition, "", requestId);
    }

    private static void sendEditor(ServerPlayer player, String originalId, MinigameDefinition definition,
                                   String notice, long requestId) {
        var settings = SimpleServerUtilities.ECONOMY.settings();
        PacketDistributor.sendToPlayer(player, new MinigameEditorOpenPayload(
                originalId, SimpleServerUtilities.MINIGAMES.toJson(definition), settings.getCurrencySymbol(),
                settings.getDecimalPlaces(), SimpleServerUtilities.CONTENT_ACTIONS.registeredTypes(), notice, requestId));
    }

    private static boolean canAdmin(ServerPlayer player) {
        return Config.ENABLE_MINIGAMES.get() && SimpleServerUtilities.CORE.modules().isActive("minigames")
                && PermissionService.getBoolean(player, PermissionKeys.MINIGAMES_ADMIN, false);
    }

    private static void sendResult(ServerPlayer player, boolean success, String message, String id, long requestId) {
        PacketDistributor.sendToPlayer(player, new MinigameEditorResultPayload(success, message, id, requestId));
    }
}
