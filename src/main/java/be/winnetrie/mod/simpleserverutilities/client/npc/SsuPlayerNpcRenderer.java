package be.winnetrie.mod.simpleserverutilities.client.npc;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.entity.SsuPlayerNpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/** Client renderer for the native SSU player-model NPC physical entity. */
public final class SsuPlayerNpcRenderer extends LivingEntityRenderer<SsuPlayerNpcEntity, SsuPlayerNpcRenderState, SsuPlayerNpcModel> {
    private static final Identifier DEFAULT_WIDE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");
    private static final Identifier DEFAULT_SLIM =
            Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/slim/alex.png");

    public SsuPlayerNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new SsuPlayerNpcModel(context.bakeLayer(SsuPlayerNpcModel.LAYER)), 0.5F);

        // Important: keep the proven dev3.40.4.1 LivingEntityRenderer + EntityModel startup path.
        // Only attach vanilla's held-item layer. The model itself implements ArmedModel and exposes
        // its real Steve/Alex arm as the attachment point.
        try {
            this.addLayer(new ItemInHandLayer<>(this));
        } catch (Throwable t) {
            // Held-item rendering is optional. A client resource/bootstrap failure must never
            // blank the entire title screen again; keep the NPC skin renderer alive and log it.
            SimpleServerUtilities.LOGGER.error("Failed to attach player-NPC held-item layer; continuing without held items", t);
        }
    }

    @Override
    public SsuPlayerNpcRenderState createRenderState() {
        return new SsuPlayerNpcRenderState();
    }

    @Override
    public void extractRenderState(SsuPlayerNpcEntity entity, SsuPlayerNpcRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModelResolver, partialTick);

        state.entityId = entity.getId();
        state.slim = NpcCustomTextureClientState.isSlimModelForEntity(entity.getId());
        state.crouching = entity.isCrouching();
        state.headPitch = entity.getXRot() * ((float) Math.PI / 180.0F);
        state.headYaw = Mth.wrapDegrees(entity.getYHeadRot() - entity.getYRot()) * ((float) Math.PI / 180.0F);

        ItemStack main = entity.getMainHandItem();
        ItemStack off = entity.getOffhandItem();
        HumanoidArm mainArm = entity.getMainArm();
        boolean fighting = entity.isAggressive();
        setPoseForHand(state, mainArm, true, poseFor(main, fighting));
        setPoseForHand(state, mainArm, false, poseFor(off, fighting));
    }

    private static HumanoidModel.ArmPose poseFor(ItemStack stack, boolean fighting) {
        if (stack == null || stack.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
        if (fighting) {
            if (stack.getItem() instanceof CrossbowItem || itemIdContains(stack, "crossbow")) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
            if (stack.getItem() instanceof ProjectileWeaponItem || itemIdContains(stack, "bow")) {
                return HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            if (itemIdContains(stack, "shield")) return HumanoidModel.ArmPose.BLOCK;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    private static void setPoseForHand(SsuPlayerNpcRenderState state, HumanoidArm mainArm,
            boolean mainHand, HumanoidModel.ArmPose pose) {
        HumanoidArm physicalArm = mainHand
                ? mainArm
                : (mainArm == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
        if (physicalArm == HumanoidArm.RIGHT) state.rightArmPose = pose;
        else state.leftArmPose = pose;
    }

    private static boolean itemIdContains(ItemStack stack, String needle) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(needle);
    }

    /**
     * SSU owns the complete overhead identity stack (role/name/faction/quest marker).
     * Never let vanilla render the entity type/custom-name plate for the physical player-NPC shell,
     * including while the crosshair is targeting it.
     */
    @Override
    protected boolean shouldShowName(SsuPlayerNpcEntity entity, double distanceToCameraSq) {
        return false;
    }

    @Override
    public Identifier getTextureLocation(SsuPlayerNpcRenderState state) {
        Identifier custom = NpcCustomTextureClientState.textureForEntity(state.entityId);
        if (custom != null) return custom;
        return state.slim ? DEFAULT_SLIM : DEFAULT_WIDE;
    }
}
