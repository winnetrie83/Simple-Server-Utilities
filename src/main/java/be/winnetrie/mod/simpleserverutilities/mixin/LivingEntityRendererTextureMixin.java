package be.winnetrie.mod.simpleserverutilities.mixin;

import be.winnetrie.mod.simpleserverutilities.client.npc.NpcCustomTextureClientState;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.21.1 compatibility hook for SSU custom textures on vanilla living-entity NPCs.
 * The native SSU player NPC renderer handles its own texture directly; this hook
 * only substitutes the base texture lookup of vanilla living renderers.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererTextureMixin {
    @Redirect(
            method = "getRenderType",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;getTextureLocation(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/resources/ResourceLocation;"
            ),
            require = 0
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResourceLocation ssu$customNpcTextureForRenderType(LivingEntityRenderer renderer, net.minecraft.world.entity.Entity entity) {
        if (entity instanceof LivingEntity living) {
            ResourceLocation custom = NpcCustomTextureClientState.textureForEntity(living.getId());
            if (custom != null) return custom;
        }
        return renderer.getTextureLocation(entity);
    }
}
