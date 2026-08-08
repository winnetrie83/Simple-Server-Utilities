package be.winnetrie.mod.simpleserverutilities.serverops;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.network.ServerOperationsActionPayload;
import be.winnetrie.mod.simpleserverutilities.network.ServerOperationsDataPayload;
import be.winnetrie.mod.simpleserverutilities.network.ServerOperationsRequestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Closed, typed network action dispatcher for Server Operations and player Support. */
public final class ServerOperationsService {
    private ServerOperationsService() { }

    public static void request(ServerOperationsRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        send(player, payload.admin(), payload.requestId(), "", false);
    }

    public static void action(ServerOperationsActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        boolean admin = payload.admin();
        String action = payload.action();
        if (admin && !SimpleServerUtilities.SERVER_OPERATIONS.canAdmin(player)) {
            send(player, true, payload.requestId(), "Server Operations administration denied.", true); return;
        }
        if (!admin && !SimpleServerUtilities.SERVER_OPERATIONS.canUseReports(player)) {
            send(player, false, payload.requestId(), "Support tickets are not available to your permissions.", true); return;
        }
        String notice;
        boolean error = false;
        try {
            notice = dispatch(player, admin, action, payload.target(), payload.value(), payload.extra());
        } catch (Exception exception) {
            notice = exception.getMessage() == null ? "Server Operations action failed safely." : exception.getMessage();
            error = true;
        }
        long detailId = action.startsWith("ticket_") && !action.equals("ticket_create") ? longValue(payload.target(), -1L) : -1L;
        int detailPage = action.equals("ticket_view") ? integer(payload.value(), 0) : 0;
        send(player, admin, payload.requestId(), notice, error, detailId, detailPage);
    }

    private static String dispatch(ServerPlayer player, boolean admin, String action, String target, String value, String extra) {
        ServerOperationsManager manager = SimpleServerUtilities.SERVER_OPERATIONS;
        if (!admin) {
            return switch (action) {
                case "ticket_create" -> manager.createTicket(player, target, value, extra);
                case "ticket_view" -> manager.markTicketRead(player, longValue(target, 0L), false);
                case "ticket_reply" -> manager.replyTicket(player, longValue(target, 0L), value, false);
                case "ticket_close" -> manager.closeOwnTicket(player, longValue(target, 0L), value);
                default -> throw new IllegalArgumentException("Unknown support action.");
            };
        }
        return switch (action) {
            case "activity_settings" -> {
                String[] options = extra.split("\\|", 2);
                yield manager.setActivitySettings(player, bool(target), integer(value, 14),
                        options.length < 1 || bool(options[0]), options.length < 2 || bool(options[1]));
            }
            case "activity_rollback" -> manager.startRollback(player, target, integer(value, 24), integer(extra, 32));
            case "backup_create" -> manager.createBackup(player, target);
            case "backup_settings" -> manager.setAutomaticBackup(player, bool(target), integer(value, 360), integer(extra, 7));
            case "backup_delete" -> manager.deleteBackup(player, target);
            case "backup_restore" -> manager.requestRestore(player, target);
            case "task_add" -> {
                String[] parts = extra.split("\\|", 2);
                String schedule = parts.length > 0 ? parts[0] : "60";
                String taskPayload = parts.length > 1 ? parts[1] : "";
                yield manager.addTask(player, target, value, schedule, taskPayload);
            }
            case "task_toggle" -> manager.setTaskEnabled(player, target, bool(value));
            case "task_delete" -> manager.deleteTask(player, target);
            case "task_run" -> manager.runTaskNow(player, target);
            case "maintenance" -> manager.setMaintenance(player, bool(target), value, bool(extra));
            case "chat_settings" -> manager.setChatSettings(player, extra);
            case "mute" -> {
                String[] parts = extra.split("\\|", 2);
                yield manager.mute(player, target, integer(value, 0), parts.length > 1 ? parts[1] : (parts.length == 1 ? parts[0] : ""));
            }
            case "unmute" -> manager.unmute(player, target);
            case "ticket_view" -> manager.markTicketRead(player, longValue(target, 0L), true);
            case "ticket_reply" -> manager.replyTicket(player, longValue(target, 0L), value, true);
            case "ticket_assign", "ticket_resolve", "ticket_reopen" -> manager.updateTicket(player, longValue(target, 0L), action.substring("ticket_".length()));
            case "ticket_close" -> manager.updateTicket(player, longValue(target, 0L), "close", value);
            case "ticket_retention" -> manager.setClosedTicketRetention(player, integer(target, 24));
            case "world_border" -> {
                String[] p = extra.split("\\|", 3);
                if (p.length < 3) throw new IllegalArgumentException("World border needs center X, center Z and size.");
                yield manager.setWorldBorder(player, target, decimal(p[0], 0), decimal(p[1], 0), decimal(p[2], 1000));
            }
            case "pregen_start" -> manager.startPregeneration(player, target, integer(value, 16));
            case "pregen_stop" -> manager.stopPregeneration(player);
            case "pregen_settings" -> manager.setPregenSettings(player, integer(target, 1), decimal(value, 48.0D));
            case "economy_threshold" -> manager.setEconomyAlertThreshold(player, longValue(target, 0L));
            case "profile_export" -> manager.exportProfile(player, target);
            case "profile_import" -> manager.importProfile(player, target);
            case "profile_delete" -> manager.deleteProfile(player, target);
            default -> throw new IllegalArgumentException("Unknown Server Operations action.");
        };
    }

    public static void send(ServerPlayer player, boolean admin, long requestId, String notice, boolean error) {
        send(player, admin, requestId, notice, error, -1L, 0);
    }

    private static void send(ServerPlayer player, boolean admin, long requestId, String notice, boolean error, long ticketId, int ticketPage) {
        if (admin && !SimpleServerUtilities.SERVER_OPERATIONS.canAdmin(player)) {
            PacketDistributor.sendToPlayer(player, new ServerOperationsDataPayload(true, "{}", "Server Operations administration denied.", true, requestId));
            return;
        }
        if (!admin && !SimpleServerUtilities.SERVER_OPERATIONS.canUseReports(player)) {
            PacketDistributor.sendToPlayer(player, new ServerOperationsDataPayload(false, "{}", "Support tickets are not available to your permissions.", true, requestId));
            return;
        }
        PacketDistributor.sendToPlayer(player, new ServerOperationsDataPayload(admin,
                SimpleServerUtilities.SERVER_OPERATIONS.snapshot(player, admin, ticketId, ticketPage).toString(), notice, error, requestId));
    }

    private static int integer(String raw, int fallback) { try { return Integer.parseInt(raw == null ? "" : raw.trim()); } catch (RuntimeException ignored) { return fallback; } }
    private static long longValue(String raw, long fallback) { try { return Long.parseLong(raw == null ? "" : raw.trim()); } catch (RuntimeException ignored) { return fallback; } }
    private static double decimal(String raw, double fallback) { try { return Double.parseDouble(raw == null ? "" : raw.trim()); } catch (RuntimeException ignored) { return fallback; } }
    private static boolean bool(String raw) { return Boolean.parseBoolean(raw == null ? "false" : raw.trim()); }
}
