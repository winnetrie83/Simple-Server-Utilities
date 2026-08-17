package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramRichText;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramScoreboardMode;
import be.winnetrie.mod.simpleserverutilities.hologram.HologramType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Complete, bounded editor state for either creating or editing a hologram. */
public record HologramEditorOpenPayload(
        boolean editing,
        String originalId,
        String dimension,
        double x,
        double y,
        double z,
        String id,
        HologramType hologramType,
        String text,
        int color,
        int backgroundColor,
        float scale,
        boolean bold,
        boolean italic,
        boolean underlined,
        boolean strikethrough,
        boolean seeThrough,
        double viewDistance,
        String urlOrImageSource,
        float imageWidth,
        float imageHeight,
        String objective,
        HologramScoreboardMode scoreboardMode,
        int maxLines,
        int updateIntervalTicks
) implements CustomPacketPayload {
    public static final Type<HologramEditorOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "hologram_editor_open")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, HologramEditorOpenPayload> STREAM_CODEC =
            StreamCodec.of(HologramEditorOpenPayload::encode, HologramEditorOpenPayload::decode);

    public HologramEditorOpenPayload {
        originalId = PayloadBounds.string(originalId, 64);
        dimension = PayloadBounds.string(dimension, 256);
        id = PayloadBounds.string(id, 64);
        hologramType = hologramType == null ? HologramType.TEXT : hologramType;
        text = PayloadBounds.string(text, HologramRichText.MAX_STORED_CHARACTERS);
        urlOrImageSource = PayloadBounds.string(urlOrImageSource, 2048);
        objective = PayloadBounds.string(objective, 64);
        scoreboardMode = scoreboardMode == null ? HologramScoreboardMode.TOP : scoreboardMode;
        scale = Float.isFinite(scale) ? Math.max(1.0F, Math.min(8.0F, scale)) : 1.0F;
        viewDistance = Double.isFinite(viewDistance)
                ? Math.max(4.0D, Math.min(512.0D, viewDistance)) : 64.0D;
        imageWidth = Float.isFinite(imageWidth)
                ? Math.max(0.1F, Math.min(32.0F, imageWidth)) : 2.0F;
        imageHeight = Float.isFinite(imageHeight)
                ? Math.max(0.1F, Math.min(32.0F, imageHeight)) : 2.0F;
        maxLines = Math.max(1, Math.min(64, maxLines));
        updateIntervalTicks = Math.max(10, Math.min(72_000, updateIntervalTicks));
    }

    public static HologramEditorOpenPayload create(String dimension, double x, double y, double z) {
        return new HologramEditorOpenPayload(
                false, "", dimension, x, y, z, "", HologramType.TEXT, "", 0xFFFFFFFF, 0x00000000, 1.0F,
                false, false, false, false, true, 64.0D, "", 2.0F, 2.0F, "",
                HologramScoreboardMode.TOP, 10, 20
        );
    }

    private static void encode(RegistryFriendlyByteBuf b, HologramEditorOpenPayload p) {
        b.writeBoolean(p.editing);
        b.writeUtf(p.originalId, 64);
        b.writeUtf(p.dimension, 256);
        b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
        b.writeUtf(p.id, 64);
        b.writeEnum(p.hologramType);
        b.writeUtf(p.text, HologramRichText.MAX_STORED_CHARACTERS);
        b.writeInt(p.color);
        b.writeInt(p.backgroundColor);
        b.writeFloat(p.scale);
        b.writeBoolean(p.bold); b.writeBoolean(p.italic); b.writeBoolean(p.underlined);
        b.writeBoolean(p.strikethrough); b.writeBoolean(p.seeThrough);
        b.writeDouble(p.viewDistance);
        b.writeUtf(p.urlOrImageSource, 2048);
        b.writeFloat(p.imageWidth); b.writeFloat(p.imageHeight);
        b.writeUtf(p.objective, 64);
        b.writeEnum(p.scoreboardMode);
        b.writeVarInt(p.maxLines); b.writeVarInt(p.updateIntervalTicks);
    }

    private static HologramEditorOpenPayload decode(RegistryFriendlyByteBuf b) {
        return new HologramEditorOpenPayload(
                b.readBoolean(), b.readUtf(64), b.readUtf(256), b.readDouble(), b.readDouble(), b.readDouble(),
                b.readUtf(64), b.readEnum(HologramType.class), b.readUtf(HologramRichText.MAX_STORED_CHARACTERS), b.readInt(), b.readInt(), b.readFloat(),
                b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readDouble(),
                b.readUtf(2048), b.readFloat(), b.readFloat(), b.readUtf(64),
                b.readEnum(HologramScoreboardMode.class), b.readVarInt(), b.readVarInt()
        );
    }



    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
