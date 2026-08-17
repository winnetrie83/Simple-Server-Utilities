package be.winnetrie.mod.simpleserverutilities.client.npc;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/** Dependency-free 64x64 Steve/Alex model used by the native SSU player NPC runtime. */
public final class SsuPlayerNpcModel extends EntityModel<SsuPlayerNpcRenderState> implements ArmedModel<SsuPlayerNpcRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(SimpleServerUtilities.MODID, "player_npc"), "main");

    private static final String HEAD = "head";
    private static final String BODY = "body";
    private static final String RIGHT_ARM = "right_arm";
    private static final String LEFT_ARM = "left_arm";
    private static final String RIGHT_ARM_SLIM = "right_arm_slim";
    private static final String LEFT_ARM_SLIM = "left_arm_slim";
    private static final String RIGHT_LEG = "right_leg";
    private static final String LEFT_LEG = "left_leg";

    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightArmSlim;
    private final ModelPart leftArmSlim;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public SsuPlayerNpcModel(ModelPart root) {
        super(root);
        this.head = root.getChild(HEAD);
        this.body = root.getChild(BODY);
        this.rightArm = root.getChild(RIGHT_ARM);
        this.leftArm = root.getChild(LEFT_ARM);
        this.rightArmSlim = root.getChild(RIGHT_ARM_SLIM);
        this.leftArmSlim = root.getChild(LEFT_ARM_SLIM);
        this.rightLeg = root.getChild(RIGHT_LEG);
        this.leftLeg = root.getChild(LEFT_LEG);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation outer = new CubeDeformation(0.25F);

        PartDefinition head = root.addOrReplaceChild(HEAD,
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        head.addOrReplaceChild("hat",
                CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, outer),
                PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild(BODY,
                CubeListBuilder.create().texOffs(16, 16).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        body.addOrReplaceChild("jacket",
                CubeListBuilder.create().texOffs(16, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, outer),
                PartPose.ZERO);

        PartDefinition rightArm = root.addOrReplaceChild(RIGHT_ARM,
                CubeListBuilder.create().texOffs(40, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        rightArm.addOrReplaceChild("right_sleeve",
                CubeListBuilder.create().texOffs(40, 32).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
                PartPose.ZERO);

        PartDefinition leftArm = root.addOrReplaceChild(LEFT_ARM,
                CubeListBuilder.create().mirror().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(5.0F, 2.0F, 0.0F));
        leftArm.addOrReplaceChild("left_sleeve",
                CubeListBuilder.create().mirror().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
                PartPose.ZERO);

        PartDefinition rightSlim = root.addOrReplaceChild(RIGHT_ARM_SLIM,
                CubeListBuilder.create().texOffs(40, 16).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                PartPose.offset(-5.0F, 2.5F, 0.0F));
        rightSlim.addOrReplaceChild("right_sleeve_slim",
                CubeListBuilder.create().texOffs(40, 32).addBox(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, outer),
                PartPose.ZERO);

        PartDefinition leftSlim = root.addOrReplaceChild(LEFT_ARM_SLIM,
                CubeListBuilder.create().mirror().texOffs(32, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F),
                PartPose.offset(5.0F, 2.5F, 0.0F));
        leftSlim.addOrReplaceChild("left_sleeve_slim",
                CubeListBuilder.create().mirror().texOffs(48, 48).addBox(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, outer),
                PartPose.ZERO);

        PartDefinition rightLeg = root.addOrReplaceChild(RIGHT_LEG,
                CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(-1.9F, 12.0F, 0.0F));
        rightLeg.addOrReplaceChild("right_pants",
                CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
                PartPose.ZERO);

        PartDefinition leftLeg = root.addOrReplaceChild(LEFT_LEG,
                CubeListBuilder.create().mirror().texOffs(16, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offset(1.9F, 12.0F, 0.0F));
        leftLeg.addOrReplaceChild("left_pants",
                CubeListBuilder.create().mirror().texOffs(0, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, outer),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(SsuPlayerNpcRenderState state) {
        super.setupAnim(state);

        rightArm.visible = !state.slim;
        leftArm.visible = !state.slim;
        rightArmSlim.visible = state.slim;
        leftArmSlim.visible = state.slim;

        head.xRot = state.headPitch;
        head.yRot = state.headYaw;

        float walk = state.walkAnimationPos * 0.6662F;
        float amount = Math.min(1.0F, state.walkAnimationSpeed);
        float rightLegRot = Mth.cos(walk) * 1.4F * amount;
        float leftLegRot = Mth.cos(walk + (float) Math.PI) * 1.4F * amount;
        float rightArmRot = Mth.cos(walk + (float) Math.PI) * 1.2F * amount;
        float leftArmRot = Mth.cos(walk) * 1.2F * amount;

        rightLeg.xRot = rightLegRot;
        leftLeg.xRot = leftLegRot;
        rightArm.xRot = rightArmRot;
        leftArm.xRot = leftArmRot;
        rightArmSlim.xRot = rightArmRot;
        leftArmSlim.xRot = leftArmRot;
        body.xRot = 0.0F;

        // SSU combat already publishes the normal LivingEntity swing via ArmedEntityRenderState.
        if (state.attackTime > 0.0F) {
            float attackSwing = Mth.sin(state.attackTime * (float) Math.PI) * 1.35F;
            ModelPart attackArm = state.attackArm == HumanoidArm.LEFT ? leftArm : rightArm;
            ModelPart attackArmSlim = state.attackArm == HumanoidArm.LEFT ? leftArmSlim : rightArmSlim;
            attackArm.xRot -= attackSwing;
            attackArmSlim.xRot -= attackSwing;
        }

        applyHeldItemPose(state, HumanoidArm.RIGHT, rightArm, rightArmSlim);
        applyHeldItemPose(state, HumanoidArm.LEFT, leftArm, leftArmSlim);

        if (state.crouching) {
            body.xRot = 0.5F;
            rightArm.xRot += 0.4F;
            leftArm.xRot += 0.4F;
            rightArmSlim.xRot += 0.4F;
            leftArmSlim.xRot += 0.4F;
        }
    }
    private static void applyHeldItemPose(SsuPlayerNpcRenderState state, HumanoidArm arm, ModelPart wide, ModelPart slim) {
        HumanoidModel.ArmPose pose = arm == HumanoidArm.RIGHT ? state.rightArmPose : state.leftArmPose;
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        if (pose == null) return;

        switch (pose) {
            case BLOCK -> {
                wide.xRot = wide.xRot * 0.5F - 0.9424779F;
                wide.yRot = -0.5235988F * side;
            }
            case BOW_AND_ARROW -> {
                wide.yRot = -0.1F * side + state.headYaw;
                wide.xRot = -1.5707964F + state.headPitch;
            }
            case CROSSBOW_HOLD -> {
                wide.yRot = -0.3F * side + state.headYaw;
                wide.xRot = -1.35F + state.headPitch;
            }
            case ITEM -> wide.xRot = wide.xRot * 0.5F - 0.31415927F;
            default -> { }
        }

        slim.xRot = wide.xRot;
        slim.yRot = wide.yRot;
        slim.zRot = wide.zRot;
    }

    /**
     * Attachment hook used by vanilla ItemInHandLayer. This deliberately keeps the proven
     * dev3.40.4.1 player model instead of turning the entire NPC model into HumanoidModel.
     */
    @Override
    public void translateToHand(SsuPlayerNpcRenderState state, HumanoidArm arm, PoseStack poseStack) {
        ModelPart part;
        if (arm == HumanoidArm.RIGHT) part = state.slim ? rightArmSlim : rightArm;
        else part = state.slim ? leftArmSlim : leftArm;
        part.translateAndRotate(poseStack);
    }

}
