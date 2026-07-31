package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramScoreboardMode;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HologramEditorSubmitPayload(
        String originalId,
        boolean deleteRequested,
        String id,
        HologramType hologramType,
        double x,
        double y,
        double z,
        String text,
        int color,
        int backgroundColor,
        float scale,
        boolean bold,
        boolean italic,
        boolean underlined,
        boolean strikethrough,
        boolean shadow,
        boolean seeThrough,
        double viewDistance,
        String urlOrImageSource,
        float imageWidth,
        float imageHeight,
        String objective,
        HologramScoreboardMode scoreboardMode,
        int maxLines,
        int updateIntervalTicks,
        long requestId
) implements CustomPacketPayload {
    public static final Type<HologramEditorSubmitPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "hologram_editor_submit")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, HologramEditorSubmitPayload> STREAM_CODEC =
            StreamCodec.of(HologramEditorSubmitPayload::encode, HologramEditorSubmitPayload::decode);

    public HologramEditorSubmitPayload {
        originalId = bound(originalId, 64, false);
        id = bound(id, 64, true);
        text = bound(text, HologramRichText.MAX_STORED_CHARACTERS, false);
        urlOrImageSource = bound(urlOrImageSource, 2048, true);
        objective = bound(objective, 64, true);
        hologramType = hologramType == null ? HologramType.TEXT : hologramType;
        scoreboardMode = scoreboardMode == null ? HologramScoreboardMode.TOP : scoreboardMode;
        requestId = Math.max(0L, requestId);
    }

    private static void encode(RegistryFriendlyByteBuf b, HologramEditorSubmitPayload p) {
        b.writeUtf(p.originalId, 64);
        b.writeBoolean(p.deleteRequested);
        b.writeUtf(p.id, 64);
        b.writeEnum(p.hologramType);
        b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
        b.writeUtf(p.text, HologramRichText.MAX_STORED_CHARACTERS);
        b.writeInt(p.color); b.writeInt(p.backgroundColor); b.writeFloat(p.scale);
        b.writeBoolean(p.bold); b.writeBoolean(p.italic); b.writeBoolean(p.underlined);
        b.writeBoolean(p.strikethrough); b.writeBoolean(p.shadow); b.writeBoolean(p.seeThrough);
        b.writeDouble(p.viewDistance); b.writeUtf(p.urlOrImageSource, 2048); b.writeFloat(p.imageWidth);
        b.writeFloat(p.imageHeight); b.writeUtf(p.objective, 64); b.writeEnum(p.scoreboardMode);
        b.writeVarInt(p.maxLines); b.writeVarInt(p.updateIntervalTicks); b.writeVarLong(p.requestId);
    }

    private static HologramEditorSubmitPayload decode(RegistryFriendlyByteBuf b) {
        return new HologramEditorSubmitPayload(
                b.readUtf(64), b.readBoolean(), b.readUtf(64), b.readEnum(HologramType.class),
                b.readDouble(), b.readDouble(), b.readDouble(),
                b.readUtf(HologramRichText.MAX_STORED_CHARACTERS), b.readInt(), b.readInt(), b.readFloat(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
                b.readDouble(), b.readUtf(2048), b.readFloat(), b.readFloat(), b.readUtf(64),
                b.readEnum(HologramScoreboardMode.class), b.readVarInt(), b.readVarInt(), b.readVarLong()
        );
    }

    private static String bound(String value, int max, boolean trim) {
        String safe = value == null ? "" : (trim ? value.trim() : value);
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
