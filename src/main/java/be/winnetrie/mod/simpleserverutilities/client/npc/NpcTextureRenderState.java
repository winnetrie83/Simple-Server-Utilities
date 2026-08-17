package be.winnetrie.mod.simpleserverutilities.client.npc;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;

/**
 * Per-render-state texture override for SSU-managed living NPCs.
 *
 * The value is attached through NeoForge's render-state modifier API. Keeping the
 * texture on the render state instead of mutating a shared vanilla renderer is
 * important: two zombies using different SSU definitions may be submitted in the
 * same frame and must retain different textures.
 */
public final class NpcTextureRenderState {
    public static final ContextKey<Identifier> CUSTOM_TEXTURE = new ContextKey<>(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "npc_custom_texture")
    );

    private NpcTextureRenderState() {}

    public static Identifier customTexture(LivingEntityRenderState state) {
        return state == null ? null : state.getRenderData(CUSTOM_TEXTURE);
    }
}
