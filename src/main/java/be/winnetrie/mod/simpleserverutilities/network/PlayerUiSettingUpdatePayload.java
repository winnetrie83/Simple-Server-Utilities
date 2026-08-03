package be.winnetrie.mod.simpleserverutilities.network;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Closed, server-validated personal setting update used outside the dashboard shell. */
public record PlayerUiSettingUpdatePayload(String key, String value) implements CustomPacketPayload {
    public static final Type<PlayerUiSettingUpdatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "player_ui_setting_update")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerUiSettingUpdatePayload> STREAM_CODEC =
            StreamCodec.of(PlayerUiSettingUpdatePayload::encode, PlayerUiSettingUpdatePayload::decode);

    public PlayerUiSettingUpdatePayload {
        key = PayloadBounds.string(key, 64).trim().toLowerCase(java.util.Locale.ROOT);
        value = PayloadBounds.string(value, 64).trim();
    }

    private static void encode(RegistryFriendlyByteBuf buffer, PlayerUiSettingUpdatePayload payload) {
        buffer.writeUtf(payload.key(), 64);
        buffer.writeUtf(payload.value(), 64);
    }

    private static PlayerUiSettingUpdatePayload decode(RegistryFriendlyByteBuf buffer) {
        return new PlayerUiSettingUpdatePayload(buffer.readUtf(64), buffer.readUtf(64));
    }



    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
