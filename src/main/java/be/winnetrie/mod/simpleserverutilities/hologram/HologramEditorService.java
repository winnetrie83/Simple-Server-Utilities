package be.winnetrie.mod.simpleserverutilities.hologram;

import java.net.URI;

import be.winnetrie.mod.simpleserverutilities.Config;
import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.core.module.SsuModuleAccess;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorOpenPayload;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorRequestPayload;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorResultPayload;
import be.winnetrie.mod.simpleserverutilities.network.HologramEditorSubmitPayload;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionKeys;
import be.winnetrie.mod.simpleserverutilities.permission.PermissionService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Validates, opens and stores holograms submitted by the custom admin editor. */
public final class HologramEditorService {
    private HologramEditorService() {
    }

    public static void handleOpenRequest(HologramEditorRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!SimpleServerUtilities.HOLOGRAM_TOOLS.isTool(player, player.getMainHandItem())) {
            player.sendSystemMessage(Component.literal("Hold the SSU Hologram Tool in your main hand to edit locally."), true);
            return;
        }
        if (!openEditor(player, payload.id())) {
            player.sendSystemMessage(Component.literal("That hologram no longer exists or cannot be edited."), true);
        }
    }

    public static boolean openEditor(ServerPlayer player, String rawId) {
        if (!canAdmin(player)) return false;
        HologramDefinition value = SimpleServerUtilities.HOLOGRAMS.get(rawId);
        if (value == null) return false;
        SimpleServerUtilities.HOLOGRAM_TOOLS.suppressCreateBriefly(player);
        PacketDistributor.sendToPlayer(player, toOpenPayload(value));
        return true;
    }

    public static void handleSubmit(HologramEditorSubmitPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Result result = save(player, payload);
        PacketDistributor.sendToPlayer(player,
                new HologramEditorResultPayload(result.success(), result.message(), payload.requestId()));
    }

    private static Result save(ServerPlayer player, HologramEditorSubmitPayload payload) {
        if (!SsuModuleAccess.active("holograms")) return Result.fail("The hologram module is disabled or blocked.");
        if (!PermissionService.getBoolean(player, PermissionKeys.HOLOGRAMS_ADMIN, false)) {
            return Result.fail("You do not have permission to manage holograms.");
        }

        boolean editing = payload.originalId() != null && !payload.originalId().isBlank();
        HologramDefinition existing = editing ? SimpleServerUtilities.HOLOGRAMS.get(payload.originalId()) : null;
        if (editing && existing == null) return Result.fail("The hologram no longer exists.");

        if (payload.deleteRequested()) {
            if (!editing) return Result.fail("Only an existing hologram can be deleted.");
            return SimpleServerUtilities.HOLOGRAMS.delete(payload.originalId())
                    ? Result.ok("Hologram '" + existing.id + "' deleted.")
                    : Result.fail("The hologram could not be deleted.");
        }

        HologramToolManager.Anchor anchor = editing ? null : SimpleServerUtilities.HOLOGRAM_TOOLS.validAnchor(player);
        if (!editing && anchor == null) {
            return Result.fail("The creation position expired. Close this screen and right-click with the tool again.");
        }

        if (payload.id().isBlank()) return Result.fail("Enter a unique hologram ID.");
        String id = HologramDefinition.sanitizeId(payload.id());
        HologramDefinition conflicting = SimpleServerUtilities.HOLOGRAMS.get(id);
        if (conflicting != null && (!editing || !conflicting.id.equals(existing.id))) {
            return Result.fail("A hologram with ID '" + id + "' already exists.");
        }

        if (!validCoordinates(payload.x(), payload.y(), payload.z())) {
            return Result.fail("Enter valid coordinates inside the Minecraft world bounds.");
        }

        HologramDefinition value = new HologramDefinition();
        value.id = id;
        value.type = payload.hologramType();
        value.dimension = editing ? existing.dimension : anchor.dimension();
        value.x = payload.x();
        value.y = payload.y();
        value.z = payload.z();
        if (editing) value.enabled = existing.enabled;
        value.text = payload.text();
        value.color = payload.color();
        value.backgroundColor = payload.backgroundColor();
        value.scale = payload.scale();
        // Rich formatting is embedded in selected text ranges; whole-text flags
        // are accepted only for backwards-compatible packet decoding.
        value.bold = payload.bold(); value.italic = payload.italic();
        value.underlined = payload.underlined(); value.strikethrough = payload.strikethrough();
        value.shadow = false; value.seeThrough = payload.seeThrough();
        value.viewDistance = payload.viewDistance();
        value.imageWidth = payload.imageWidth(); value.imageHeight = payload.imageHeight();
        value.objective = payload.objective(); value.scoreboardMode = payload.scoreboardMode();
        value.maxLines = payload.maxLines(); value.updateIntervalTicks = payload.updateIntervalTicks();

        switch (value.type) {
            case TEXT -> {
                if (value.text.isBlank()) return Result.fail("Enter the floating text.");
            }
            case LINK -> {
                if (value.text.isBlank()) return Result.fail("Enter the visible link text.");
                if (!validWebsite(payload.urlOrImageSource())) return Result.fail("Enter a valid http/https website link.");
                value.url = payload.urlOrImageSource();
            }
            case IMAGE -> {
                String imageSource = payload.urlOrImageSource() == null ? "" : payload.urlOrImageSource().trim();
                if (isRemoteImageSource(imageSource) && !Config.ALLOW_REMOTE_HOLOGRAM_IMAGES.get()) {
                    return Result.fail("Remote image links are disabled in the server config (allowRemoteHologramImages).");
                }
                if (!validImageSource(imageSource)) {
                    return Result.fail("Use a PNG, GIF or JPG resource ID or a direct http/https image URL.");
                }
                value.imageSource = imageSource;
            }
            case SCOREBOARD -> {
                if (value.objective.isBlank()) return Result.fail("Enter a scoreboard objective.");
                if (value.text.isBlank()) value.text = value.objective;
            }
        }

        value.normalize();
        boolean saved = editing
                ? SimpleServerUtilities.HOLOGRAMS.replace(existing.id, value)
                : SimpleServerUtilities.HOLOGRAMS.put(value);
        if (!saved) {
            return Result.fail(editing
                    ? "The hologram could not be updated."
                    : "The hologram could not be saved; the server limit may have been reached.");
        }
        if (!editing) SimpleServerUtilities.HOLOGRAM_TOOLS.clearAnchor(player.getUUID());
        return Result.ok("Hologram '" + value.id + "' " + (editing ? "updated." : "created."));
    }


    private static boolean validCoordinates(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Math.abs(x) <= 30_000_000.0D
                && Math.abs(z) <= 30_000_000.0D
                && y >= -4_096.0D && y <= 4_096.0D;
    }

    private static boolean canAdmin(ServerPlayer player) {
        return player != null && SsuModuleAccess.active("holograms")
                && PermissionService.getBoolean(player, PermissionKeys.HOLOGRAMS_ADMIN, false);
    }

    private static HologramEditorOpenPayload toOpenPayload(HologramDefinition value) {
        String source = value.type == HologramType.LINK ? value.url
                : value.type == HologramType.IMAGE ? value.imageSource : "";
        return new HologramEditorOpenPayload(
                true, value.id, value.dimension, value.x, value.y, value.z, value.id, value.type, value.text,
                value.color, value.backgroundColor, value.scale, value.bold, value.italic, value.underlined, value.strikethrough,
                value.seeThrough, value.viewDistance, source, value.imageWidth, value.imageHeight,
                value.objective, value.scoreboardMode, value.maxLines, value.updateIntervalTicks
        );
    }


    private static boolean isRemoteImageSource(String value) {
        String lower = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    private static boolean validImageSource(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (isRemoteImageSource(value)) {
            return Config.ALLOW_REMOTE_HOLOGRAM_IMAGES.get() && validWebsite(value);
        }
        try {
            Identifier identifier = Identifier.parse(value);
            String path = identifier.getPath().toLowerCase(java.util.Locale.ROOT);
            return path.endsWith(".png") || path.endsWith(".gif")
                    || path.endsWith(".jpg") || path.endsWith(".jpeg");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean validWebsite(String raw) {
        try {
            URI uri = URI.create(raw);
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme));
        } catch (Exception ignored) {
            return false;
        }
    }

    private record Result(boolean success, String message) {
        static Result ok(String message) { return new Result(true, message); }
        static Result fail(String message) { return new Result(false, message); }
    }
}
