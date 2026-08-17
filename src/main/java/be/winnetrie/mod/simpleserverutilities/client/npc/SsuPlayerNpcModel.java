package be.winnetrie.mod.simpleserverutilities.client.npc;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.npc.entity.SsuPlayerNpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/** Dependency-free 64x64 Steve/Alex model used by the native SSU player NPC runtime. */
public final class SsuPlayerNpcModel extends EntityModel<SsuPlayerNpcEntity> implements ArmedModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(SimpleServerUtilities.MODID, "player_npc"), "main");

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
    private boolean slim;

    public SsuPlayerNpcModel(ModelPart root) {
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
    public void setupAnim(SsuPlayerNpcEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        this.slim = NpcCustomTextureClientState.isSlimModelForEntity(entity.getId());
        rightArm.visible = !slim;
        leftArm.visible = !slim;
        rightArmSlim.visible = slim;
        leftArmSlim.visible = slim;

        float headPitchRad = headPitch * ((float) Math.PI / 180.0F);
        float headYawRad = netHeadYaw * ((float) Math.PI / 180.0F);
        head.xRot = headPitchRad;
        head.yRot = headYawRad;

        float walk = limbSwing * 0.6662F;
        float amount = Math.min(1.0F, limbSwingAmount);
        rightLeg.xRot = Mth.cos(walk) * 1.4F * amount;
        leftLeg.xRot = Mth.cos(walk + (float) Math.PI) * 1.4F * amount;
        float rightArmRot = Mth.cos(walk + (float) Math.PI) * 1.2F * amount;
        float leftArmRot = Mth.cos(walk) * 1.2F * amount;
        resetArm(rightArm, rightArmRot);
        resetArm(rightArmSlim, rightArmRot);
        resetArm(leftArm, leftArmRot);
        resetArm(leftArmSlim, leftArmRot);
        body.xRot = 0.0F;

        if (this.attackTime > 0.0F) {
            float attackSwing = Mth.sin(this.attackTime * (float) Math.PI) * 1.35F;
            HumanoidArm attackArm = entity.getMainArm();
            ModelPart wide = attackArm == HumanoidArm.LEFT ? leftArm : rightArm;
            ModelPart slimPart = attackArm == HumanoidArm.LEFT ? leftArmSlim : rightArmSlim;
            wide.xRot -= attackSwing;
            slimPart.xRot -= attackSwing;
        }

        HumanoidArm mainArm = entity.getMainArm();
        applyHeldItemPose(mainArm, true, poseFor(entity.getMainHandItem(), entity.isAggressive()), headYawRad, headPitchRad);
        applyHeldItemPose(mainArm, false, poseFor(entity.getOffhandItem(), entity.isAggressive()), headYawRad, headPitchRad);

        if (entity.isCrouching()) {
            body.xRot = 0.5F;
            rightArm.xRot += 0.4F;
            leftArm.xRot += 0.4F;
            rightArmSlim.xRot += 0.4F;
            leftArmSlim.xRot += 0.4F;
        }
    }

    private static void resetArm(ModelPart part, float xRot) {
        part.xRot = xRot;
        part.yRot = 0.0F;
        part.zRot = 0.0F;
    }

    private void applyHeldItemPose(HumanoidArm mainArm, boolean mainHand, HumanoidModel.ArmPose pose,
            float headYaw, float headPitch) {
        HumanoidArm physicalArm = mainHand ? mainArm : opposite(mainArm);
        ModelPart wide = physicalArm == HumanoidArm.RIGHT ? rightArm : leftArm;
        ModelPart slimPart = physicalArm == HumanoidArm.RIGHT ? rightArmSlim : leftArmSlim;
        float side = physicalArm == HumanoidArm.RIGHT ? 1.0F : -1.0F;

        switch (pose) {
            case BLOCK -> {
                wide.xRot = wide.xRot * 0.5F - 0.9424779F;
                wide.yRot = -0.5235988F * side;
            }
            case BOW_AND_ARROW -> {
                wide.yRot = -0.1F * side + headYaw;
                wide.xRot = -1.5707964F + headPitch;
            }
            case CROSSBOW_HOLD -> {
                wide.yRot = -0.3F * side + headYaw;
                wide.xRot = -1.35F + headPitch;
            }
            case ITEM -> wide.xRot = wide.xRot * 0.5F - 0.31415927F;
            default -> { }
        }
        slimPart.xRot = wide.xRot;
        slimPart.yRot = wide.yRot;
        slimPart.zRot = wide.zRot;
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

    private static boolean itemIdContains(ItemStack stack, String needle) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains(needle);
    }

    private static HumanoidArm opposite(HumanoidArm arm) {
        return arm == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
        head.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        body.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        rightArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        leftArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        rightArmSlim.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        leftArmSlim.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        rightLeg.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
        leftLeg.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
        ModelPart part = arm == HumanoidArm.RIGHT
                ? (slim ? rightArmSlim : rightArm)
                : (slim ? leftArmSlim : leftArm);
        part.translateAndRotate(poseStack);
    }
}
