package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.client.npc.NpcTextureRenderState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces only the base texture lookup of an existing Minecraft living-entity
 * renderer when NeoForge's render state carries an SSU texture override.
 *
 * Geometry, animation, pose, equipment and renderer-specific behavior remain the
 * original Minecraft implementation. Render layers which intentionally use their
 * own textures (for example armor or special emissive layers) are not rewritten.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererTextureMixin {
    @Redirect(
            method = "getRenderType",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;"
            ),
            require = 0
    )
    private Identifier ssu$customNpcTextureForRenderType(
            LivingEntityRenderer<?, ?, ?> renderer,
            LivingEntityRenderState state
    ) {
        return ssu$resolveTexture(renderer, state);
    }

    // Kept as a second, optional hook because the rendering pipeline has changed
    // substantially in 26.x. If a vanilla path performs the texture lookup directly
    // from submit(), it receives the same per-state override; if no such invocation
    // exists this redirect simply has zero required matches.
    @Redirect(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;"
            ),
            require = 0
    )
    private Identifier ssu$customNpcTextureForSubmit(
            LivingEntityRenderer<?, ?, ?> renderer,
            LivingEntityRenderState state
    ) {
        return ssu$resolveTexture(renderer, state);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Identifier ssu$resolveTexture(
            LivingEntityRenderer<?, ?, ?> renderer,
            LivingEntityRenderState state
    ) {
        Identifier custom = NpcTextureRenderState.customTexture(state);
        if (custom != null) return custom;
        // Raw invocation is deliberate: the target method's erased parameter is
        // LivingEntityRenderState while each concrete renderer owns a narrower S.
        return ((LivingEntityRenderer) renderer).getTextureLocation(state);
    }
}
