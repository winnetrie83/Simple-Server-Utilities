package be.winnetrie.mod.simpleserverutilities.network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.21.1 compatibility router for client-bound payload handlers.
 *
 * <p>NeoForge 1.21.1 registers the codec and handler together through
 * {@code PayloadRegistrar#playToClient}; newer NeoForge versions allow the
 * physical-client handler to be attached separately. Keeping this small
 * common-side router lets the dedicated server register every payload without
 * loading any Minecraft client classes. The physical client installs its real
 * handlers during client construction.</p>
 */
public final class ClientPayloadRouter {
    private static final Map<CustomPacketPayload.Type<?>, BiConsumer<CustomPacketPayload, IPayloadContext>> HANDLERS =
            new ConcurrentHashMap<>();

    private ClientPayloadRouter() {
    }

    public static <T extends CustomPacketPayload> void register(
            CustomPacketPayload.Type<T> type,
            BiConsumer<T, IPayloadContext> handler
    ) {
        HANDLERS.put(type, (payload, context) -> handler.accept(cast(payload), context));
    }

    public static <T extends CustomPacketPayload> void handle(T payload, IPayloadContext context) {
        BiConsumer<CustomPacketPayload, IPayloadContext> handler = HANDLERS.get(payload.type());
        if (handler == null) {
            throw new IllegalStateException("No SSU client payload handler registered for " + payload.type().id());
        }
        handler.accept(payload, context);
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> T cast(CustomPacketPayload payload) {
        return (T) payload;
    }
}
