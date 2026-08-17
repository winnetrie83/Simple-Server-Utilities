package be.winnetrie.mod.simpleserverutilities.client.npc;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.entity.SsuPlayerNpcEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

/** Client renderer for the native SSU player-model NPC physical entity. */
public final class SsuPlayerNpcRenderer extends LivingEntityRenderer<SsuPlayerNpcEntity, SsuPlayerNpcModel> {
    private static final ResourceLocation DEFAULT_WIDE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");
    private static final ResourceLocation DEFAULT_SLIM =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/slim/alex.png");

    public SsuPlayerNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new SsuPlayerNpcModel(context.bakeLayer(SsuPlayerNpcModel.LAYER)), 0.5F);
        try {
            this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        } catch (Throwable t) {
            SimpleServerUtilities.LOGGER.error("Failed to attach player-NPC held-item layer; continuing without held items", t);
        }
    }

    @Override
    protected boolean shouldShowName(SsuPlayerNpcEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(SsuPlayerNpcEntity entity) {
        ResourceLocation custom = NpcCustomTextureClientState.textureForEntity(entity.getId());
        if (custom != null) return custom;
        return NpcCustomTextureClientState.isSlimModelForEntity(entity.getId()) ? DEFAULT_SLIM : DEFAULT_WIDE;
    }
}
