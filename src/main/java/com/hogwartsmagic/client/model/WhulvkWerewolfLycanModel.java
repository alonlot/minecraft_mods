package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.LycanthropeVillagerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.animation.KeyframeAnimations;
import org.joml.Vector3f;


public class WhulvkWerewolfLycanModel extends HierarchicalModel<LycanthropeVillagerEntity> {
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "lycan_werewolf"), "main");
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart tail;
	private final ModelPart tail_1;
	private final ModelPart tail_2;
	private final ModelPart tail_3;
	private final ModelPart tail_4;
	private final ModelPart tighLeft;
	private final ModelPart tighFurLeft;
	private final ModelPart legLeft;
	private final ModelPart legFurLeft;
	private final ModelPart shankLeft;
	private final ModelPart footToe9;
	private final ModelPart toeNail9;
	private final ModelPart toeNail4_3;
	private final ModelPart toeNail4_4;
	private final ModelPart footLeft;
	private final ModelPart soleLeft;
	private final ModelPart footToe1;
	private final ModelPart toeNail1;
	private final ModelPart toeNail1_1;
	private final ModelPart toeNail1_2;
	private final ModelPart footToe2;
	private final ModelPart toeNail2;
	private final ModelPart toeNail2_1;
	private final ModelPart toeNail2_2;
	private final ModelPart footToe3;
	private final ModelPart toeNail3;
	private final ModelPart toeNail3_1;
	private final ModelPart toeNail3_2;
	private final ModelPart footToe4;
	private final ModelPart toeNail4;
	private final ModelPart toeNail4_1;
	private final ModelPart toeNail4_2;
	private final ModelPart tighRight;
	private final ModelPart tighFurRight;
	private final ModelPart legRight;
	private final ModelPart legFurRIght;
	private final ModelPart shankRight;
	private final ModelPart footToe10;
	private final ModelPart toeNail10;
	private final ModelPart toeNail4_5;
	private final ModelPart toeNail4_6;
	private final ModelPart footRight;
	private final ModelPart soleRight;
	private final ModelPart footToe5;
	private final ModelPart toeNail5;
	private final ModelPart toeNail5_1;
	private final ModelPart toeNail5_2;
	private final ModelPart footToe6;
	private final ModelPart toeNail6;
	private final ModelPart toeNail6_1;
	private final ModelPart toeNail6_2;
	private final ModelPart footToe7;
	private final ModelPart toeNail7;
	private final ModelPart toeNail7_1;
	private final ModelPart toeNail7_2;
	private final ModelPart footToe8;
	private final ModelPart toeNail8;
	private final ModelPart toeNail8_1;
	private final ModelPart toeNail8_2;
	private final ModelPart lowerBody;
	private final ModelPart middleBody;
	private final ModelPart upperBody;
	private final ModelPart necklace;
	private final ModelPart chestRight;
	private final ModelPart leftChest;
	private final ModelPart armLeft;
	private final ModelPart upperArmLeft;
	private final ModelPart lowerArmLeft;
	private final ModelPart LowerArmFurLeft;
	private final ModelPart handLeft;
	private final ModelPart fingerLeft_2;
	private final ModelPart nailRing7;
	private final ModelPart upperFingerLeft_2;
	private final ModelPart lowerFingerLeft_2;
	private final ModelPart fingerNailLeft_2;
	private final ModelPart fingerLeft_5;
	private final ModelPart nailRing6;
	private final ModelPart upperFingerLeft_5;
	private final ModelPart lowerFingerLeft_5;
	private final ModelPart fingerNailLeft_5;
	private final ModelPart fingerLeft_3;
	private final ModelPart nailRing8;
	private final ModelPart upperFingerLeft_3;
	private final ModelPart lowerFingerLeft_3;
	private final ModelPart fingerNailLeft_3;
	private final ModelPart fingerLeft_4;
	private final ModelPart nailRing9;
	private final ModelPart upperFingerLeft_4;
	private final ModelPart lowerFingerLeft_4;
	private final ModelPart fingerNailLeft_4;
	private final ModelPart finger_6;
	private final ModelPart nailRing10;
	private final ModelPart upperFinger_6;
	private final ModelPart fingerNail_6;
	private final ModelPart armRight;
	private final ModelPart upperArmRight;
	private final ModelPart lowerArmRight;
	private final ModelPart lowerArmFurRight;
	private final ModelPart handRight;
	private final ModelPart fingerRight_7;
	private final ModelPart nailRing4;
	private final ModelPart upperFinger_7;
	private final ModelPart lowerFinger_6;
	private final ModelPart fingerNail_7;
	private final ModelPart fingerRight_8;
	private final ModelPart nailRing5;
	private final ModelPart upperFinger_8;
	private final ModelPart lowerFinger_7;
	private final ModelPart fingerNail_8;
	private final ModelPart fingerRight_9;
	private final ModelPart nailRing3;
	private final ModelPart upperFinger_9;
	private final ModelPart lowerFinger_8;
	private final ModelPart fingerNail_9;
	private final ModelPart fingerRight_10;
	private final ModelPart nailRing2;
	private final ModelPart upperFinger_10;
	private final ModelPart lowerFinger_9;
	private final ModelPart fingerNail_10;
	private final ModelPart fingerRight_11;
	private final ModelPart upperFinger_11;
	private final ModelPart fingerNail_11;
	private final ModelPart lowerNeck;
	private final ModelPart upperNeck;
	private final ModelPart head;
	private final ModelPart headFurLeft;
	private final ModelPart headFurRight;
	private final ModelPart headFurTop;
	private final ModelPart earLeft;
	private final ModelPart earRight;
	private final ModelPart upperHead;
	private final ModelPart eyebrowLeft;
	private final ModelPart eyebrowRight;
	private final ModelPart jaw;
	private final ModelPart tongue;

	public WhulvkWerewolfLycanModel(ModelPart root) {
		this.root = root.getChild("root");
		this.body = this.root.getChild("body");
		this.tail = this.body.getChild("tail");
		this.tail_1 = this.tail.getChild("tail_1");
		this.tail_2 = this.tail_1.getChild("tail_2");
		this.tail_3 = this.tail_2.getChild("tail_3");
		this.tail_4 = this.tail_3.getChild("tail_4");
		this.tighLeft = this.body.getChild("tighLeft");
		this.tighFurLeft = this.tighLeft.getChild("tighFurLeft");
		this.legLeft = this.tighLeft.getChild("legLeft");
		this.legFurLeft = this.legLeft.getChild("legFurLeft");
		this.shankLeft = this.legLeft.getChild("shankLeft");
		this.footToe9 = this.shankLeft.getChild("footToe9");
		this.toeNail9 = this.footToe9.getChild("toeNail9");
		this.toeNail4_3 = this.toeNail9.getChild("toeNail4_3");
		this.toeNail4_4 = this.toeNail4_3.getChild("toeNail4_4");
		this.footLeft = this.shankLeft.getChild("footLeft");
		this.soleLeft = this.footLeft.getChild("soleLeft");
		this.footToe1 = this.soleLeft.getChild("footToe1");
		this.toeNail1 = this.footToe1.getChild("toeNail1");
		this.toeNail1_1 = this.toeNail1.getChild("toeNail1_1");
		this.toeNail1_2 = this.toeNail1_1.getChild("toeNail1_2");
		this.footToe2 = this.soleLeft.getChild("footToe2");
		this.toeNail2 = this.footToe2.getChild("toeNail2");
		this.toeNail2_1 = this.toeNail2.getChild("toeNail2_1");
		this.toeNail2_2 = this.toeNail2_1.getChild("toeNail2_2");
		this.footToe3 = this.soleLeft.getChild("footToe3");
		this.toeNail3 = this.footToe3.getChild("toeNail3");
		this.toeNail3_1 = this.toeNail3.getChild("toeNail3_1");
		this.toeNail3_2 = this.toeNail3_1.getChild("toeNail3_2");
		this.footToe4 = this.soleLeft.getChild("footToe4");
		this.toeNail4 = this.footToe4.getChild("toeNail4");
		this.toeNail4_1 = this.toeNail4.getChild("toeNail4_1");
		this.toeNail4_2 = this.toeNail4_1.getChild("toeNail4_2");
		this.tighRight = this.body.getChild("tighRight");
		this.tighFurRight = this.tighRight.getChild("tighFurRight");
		this.legRight = this.tighRight.getChild("legRight");
		this.legFurRIght = this.legRight.getChild("legFurRIght");
		this.shankRight = this.legRight.getChild("shankRight");
		this.footToe10 = this.shankRight.getChild("footToe10");
		this.toeNail10 = this.footToe10.getChild("toeNail10");
		this.toeNail4_5 = this.toeNail10.getChild("toeNail4_5");
		this.toeNail4_6 = this.toeNail4_5.getChild("toeNail4_6");
		this.footRight = this.shankRight.getChild("footRight");
		this.soleRight = this.footRight.getChild("soleRight");
		this.footToe5 = this.soleRight.getChild("footToe5");
		this.toeNail5 = this.footToe5.getChild("toeNail5");
		this.toeNail5_1 = this.toeNail5.getChild("toeNail5_1");
		this.toeNail5_2 = this.toeNail5_1.getChild("toeNail5_2");
		this.footToe6 = this.soleRight.getChild("footToe6");
		this.toeNail6 = this.footToe6.getChild("toeNail6");
		this.toeNail6_1 = this.toeNail6.getChild("toeNail6_1");
		this.toeNail6_2 = this.toeNail6_1.getChild("toeNail6_2");
		this.footToe7 = this.soleRight.getChild("footToe7");
		this.toeNail7 = this.footToe7.getChild("toeNail7");
		this.toeNail7_1 = this.toeNail7.getChild("toeNail7_1");
		this.toeNail7_2 = this.toeNail7_1.getChild("toeNail7_2");
		this.footToe8 = this.soleRight.getChild("footToe8");
		this.toeNail8 = this.footToe8.getChild("toeNail8");
		this.toeNail8_1 = this.toeNail8.getChild("toeNail8_1");
		this.toeNail8_2 = this.toeNail8_1.getChild("toeNail8_2");
		this.lowerBody = this.body.getChild("lowerBody");
		this.middleBody = this.lowerBody.getChild("middleBody");
		this.upperBody = this.middleBody.getChild("upperBody");
		this.necklace = this.upperBody.getChild("necklace");
		this.chestRight = this.upperBody.getChild("chestRight");
		this.leftChest = this.upperBody.getChild("leftChest");
		this.armLeft = this.upperBody.getChild("armLeft");
		this.upperArmLeft = this.armLeft.getChild("upperArmLeft");
		this.lowerArmLeft = this.upperArmLeft.getChild("lowerArmLeft");
		this.LowerArmFurLeft = this.lowerArmLeft.getChild("LowerArmFurLeft");
		this.handLeft = this.lowerArmLeft.getChild("handLeft");
		this.fingerLeft_2 = this.handLeft.getChild("fingerLeft_2");
		this.nailRing7 = this.fingerLeft_2.getChild("nailRing7");
		this.upperFingerLeft_2 = this.fingerLeft_2.getChild("upperFingerLeft_2");
		this.lowerFingerLeft_2 = this.upperFingerLeft_2.getChild("lowerFingerLeft_2");
		this.fingerNailLeft_2 = this.lowerFingerLeft_2.getChild("fingerNailLeft_2");
		this.fingerLeft_5 = this.handLeft.getChild("fingerLeft_5");
		this.nailRing6 = this.fingerLeft_5.getChild("nailRing6");
		this.upperFingerLeft_5 = this.fingerLeft_5.getChild("upperFingerLeft_5");
		this.lowerFingerLeft_5 = this.upperFingerLeft_5.getChild("lowerFingerLeft_5");
		this.fingerNailLeft_5 = this.lowerFingerLeft_5.getChild("fingerNailLeft_5");
		this.fingerLeft_3 = this.handLeft.getChild("fingerLeft_3");
		this.nailRing8 = this.fingerLeft_3.getChild("nailRing8");
		this.upperFingerLeft_3 = this.fingerLeft_3.getChild("upperFingerLeft_3");
		this.lowerFingerLeft_3 = this.upperFingerLeft_3.getChild("lowerFingerLeft_3");
		this.fingerNailLeft_3 = this.lowerFingerLeft_3.getChild("fingerNailLeft_3");
		this.fingerLeft_4 = this.handLeft.getChild("fingerLeft_4");
		this.nailRing9 = this.fingerLeft_4.getChild("nailRing9");
		this.upperFingerLeft_4 = this.fingerLeft_4.getChild("upperFingerLeft_4");
		this.lowerFingerLeft_4 = this.upperFingerLeft_4.getChild("lowerFingerLeft_4");
		this.fingerNailLeft_4 = this.lowerFingerLeft_4.getChild("fingerNailLeft_4");
		this.finger_6 = this.handLeft.getChild("finger_6");
		this.nailRing10 = this.finger_6.getChild("nailRing10");
		this.upperFinger_6 = this.finger_6.getChild("upperFinger_6");
		this.fingerNail_6 = this.upperFinger_6.getChild("fingerNail_6");
		this.armRight = this.upperBody.getChild("armRight");
		this.upperArmRight = this.armRight.getChild("upperArmRight");
		this.lowerArmRight = this.upperArmRight.getChild("lowerArmRight");
		this.lowerArmFurRight = this.lowerArmRight.getChild("lowerArmFurRight");
		this.handRight = this.lowerArmRight.getChild("handRight");
		this.fingerRight_7 = this.handRight.getChild("fingerRight_7");
		this.nailRing4 = this.fingerRight_7.getChild("nailRing4");
		this.upperFinger_7 = this.fingerRight_7.getChild("upperFinger_7");
		this.lowerFinger_6 = this.upperFinger_7.getChild("lowerFinger_6");
		this.fingerNail_7 = this.lowerFinger_6.getChild("fingerNail_7");
		this.fingerRight_8 = this.handRight.getChild("fingerRight_8");
		this.nailRing5 = this.fingerRight_8.getChild("nailRing5");
		this.upperFinger_8 = this.fingerRight_8.getChild("upperFinger_8");
		this.lowerFinger_7 = this.upperFinger_8.getChild("lowerFinger_7");
		this.fingerNail_8 = this.lowerFinger_7.getChild("fingerNail_8");
		this.fingerRight_9 = this.handRight.getChild("fingerRight_9");
		this.nailRing3 = this.fingerRight_9.getChild("nailRing3");
		this.upperFinger_9 = this.fingerRight_9.getChild("upperFinger_9");
		this.lowerFinger_8 = this.upperFinger_9.getChild("lowerFinger_8");
		this.fingerNail_9 = this.lowerFinger_8.getChild("fingerNail_9");
		this.fingerRight_10 = this.handRight.getChild("fingerRight_10");
		this.nailRing2 = this.fingerRight_10.getChild("nailRing2");
		this.upperFinger_10 = this.fingerRight_10.getChild("upperFinger_10");
		this.lowerFinger_9 = this.upperFinger_10.getChild("lowerFinger_9");
		this.fingerNail_10 = this.lowerFinger_9.getChild("fingerNail_10");
		this.fingerRight_11 = this.handRight.getChild("fingerRight_11");
		this.upperFinger_11 = this.fingerRight_11.getChild("upperFinger_11");
		this.fingerNail_11 = this.upperFinger_11.getChild("fingerNail_11");
		this.lowerNeck = this.upperBody.getChild("lowerNeck");
		this.upperNeck = this.lowerNeck.getChild("upperNeck");
		this.head = this.upperNeck.getChild("head");
		this.headFurLeft = this.head.getChild("headFurLeft");
		this.headFurRight = this.head.getChild("headFurRight");
		this.headFurTop = this.head.getChild("headFurTop");
		this.earLeft = this.head.getChild("earLeft");
		this.earRight = this.head.getChild("earRight");
		this.upperHead = this.head.getChild("upperHead");
		this.eyebrowLeft = this.upperHead.getChild("eyebrowLeft");
		this.eyebrowRight = this.upperHead.getChild("eyebrowRight");
		this.jaw = this.head.getChild("jaw");
		this.tongue = this.jaw.getChild("tongue");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.25F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(72, 36).addBox(-3.0F, -8.0F, -4.0F, 7.0F, 5.0F, 8.0F, new CubeDeformation(-0.01F))
		.texOffs(47, 82).addBox(-1.9675F, -3.142F, -4.02F, 6.0F, 3.5F, 8.0F, new CubeDeformation(-0.02F))
		.texOffs(130, 54).addBox(-0.25F, -4.25F, -2.5F, 4.5F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(130, 33).addBox(-0.25F, -4.25F, 0.5F, 4.5F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(135, 95).addBox(-0.25F, -3.25F, -0.5F, 4.5F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -24.0F, 2.0F, 0.0F, 1.5708F, 0.0F));

		PartDefinition cube_r1 = body.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 93).addBox(-0.5F, -1.0F, -1.5F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, -5.75F, 1.75F, 0.0F, -0.1309F, 0.0873F));

		PartDefinition cube_r2 = body.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(16, 111).addBox(-0.5F, -1.0F, -1.5F, 1.0F, 2.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0621F, -5.75F, -1.5959F, 0.0F, 0.1309F, 0.0873F));

		PartDefinition cube_r3 = body.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(88, 63).addBox(0.0F, 0.0F, -4.0F, 4.0F, 3.5F, 8.0F, new CubeDeformation(-0.03F)), PartPose.offsetAndRotation(-3.0F, -3.0F, 0.0F, 0.0F, 0.0F, -0.3054F));

		PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(58, 118).addBox(-6.0F, 0.0F, -2.0F, 6.0F, 3.5F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, -6.0F, 0.0F, 0.0F, 0.0F, -0.6109F));

		PartDefinition tail_1 = tail.addOrReplaceChild("tail_1", CubeListBuilder.create().texOffs(97, 44).addBox(-7.1309F, -2.1694F, -2.5F, 7.0F, 4.5F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.75F, 1.75F, 0.0F, 0.0F, 0.0F, 0.6981F));

		PartDefinition tail_2 = tail_1.addOrReplaceChild("tail_2", CubeListBuilder.create().texOffs(95, 74).addBox(-6.7121F, -2.3856F, -2.5F, 7.0F, 4.5F, 5.0F, new CubeDeformation(0.11F)), PartPose.offsetAndRotation(-5.9302F, 0.2626F, 0.0F, 0.0F, 0.0F, 0.7418F));

		PartDefinition tail_3 = tail_2.addOrReplaceChild("tail_3", CubeListBuilder.create().texOffs(62, 36).addBox(-4.9835F, -1.4581F, -1.5F, 6.0F, 3.0F, 3.0F, new CubeDeformation(0.11F)), PartPose.offsetAndRotation(-6.9621F, -0.1356F, 0.0F, 0.0F, 0.0F, -0.5672F));

		PartDefinition tail_4 = tail_3.addOrReplaceChild("tail_4", CubeListBuilder.create().texOffs(11, 107).addBox(-3.1435F, -0.9816F, -0.54F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.15F)), PartPose.offsetAndRotation(-5.0354F, 0.0408F, -0.5F, 0.0F, 0.0F, -0.6109F));

		PartDefinition tighLeft = body.addOrReplaceChild("tighLeft", CubeListBuilder.create().texOffs(64, 102).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -2.0F, -1.25F, -0.48F, 0.0F, -0.5236F));

		PartDefinition cube_r4 = tighLeft.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(98, 133).addBox(-1.25F, -5.0F, -2.0F, 3.25F, 5.0F, 2.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(-0.015F, 8.0645F, 1.8829F, 0.1745F, 0.0F, 0.0F));

		PartDefinition cube_r5 = tighLeft.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(0, 135).addBox(-0.75F, -4.0F, -2.25F, 3.25F, 4.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(-0.5F, 12.0F, 1.25F, -0.2182F, 0.0F, 0.0F));

		PartDefinition cube_r6 = tighLeft.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(46, 132).addBox(-0.75F, -6.0F, 0.0F, 3.25F, 6.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(-0.5F, 12.0F, -3.0F, 0.1745F, 0.0F, 0.0F));

		PartDefinition cube_r7 = tighLeft.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(135, 62).addBox(-0.75F, -4.0F, 0.0F, 3.25F, 4.0F, 2.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(-0.5F, 6.1108F, -4.0384F, -0.3054F, 0.0F, 0.0F));

		PartDefinition cube_r8 = tighLeft.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(74, 123).addBox(-3.0F, -2.75F, -1.75F, 4.0F, 5.0F, 4.5F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(-0.4675F, 1.108F, -1.23F, 0.2618F, 0.0F, 0.1745F));

		PartDefinition tighFurLeft = tighLeft.addOrReplaceChild("tighFurLeft", CubeListBuilder.create().texOffs(76, 17).addBox(0.05F, 0.0F, -2.0F, 0.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, 11.0F, -1.0F));

		PartDefinition legLeft = tighLeft.addOrReplaceChild("legLeft", CubeListBuilder.create().texOffs(42, 110).addBox(-2.5F, 0.0F, -2.5F, 3.0F, 10.0F, 5.0F, new CubeDeformation(0.01F))
		.texOffs(82, 102).addBox(-2.0F, 0.0F, -2.0F, 4.5F, 12.0F, 4.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 10.0F, -1.0F, 0.3054F, -0.3054F, 0.9599F));

		PartDefinition cube_r9 = legLeft.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(123, 125).addBox(0.0F, -3.0F, -2.5F, 2.0F, 3.0F, 5.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(-2.5F, 9.0F, 0.0F, 0.0F, 0.0F, -0.5672F));

		PartDefinition cube_r10 = legLeft.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(121, 57).addBox(0.0F, -6.0F, -2.5F, 2.0F, 6.0F, 5.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(-4.1012F, 6.4867F, 0.0F, 0.0F, 0.0F, 0.2574F));

		PartDefinition legFurLeft = legLeft.addOrReplaceChild("legFurLeft", CubeListBuilder.create().texOffs(76, 13).addBox(-0.05F, 0.0F, -2.0F, 0.0F, 4.0F, 4.0F, new CubeDeformation(-0.01F)), PartPose.offset(-2.0F, 11.0F, 0.0F));

		PartDefinition shankLeft = legLeft.addOrReplaceChild("shankLeft", CubeListBuilder.create().texOffs(0, 122).addBox(0.0F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(-2.0F, 11.0F, 0.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition footToe9 = shankLeft.addOrReplaceChild("footToe9", CubeListBuilder.create().texOffs(19, 79).addBox(0.0F, -0.625F, -0.625F, 2.5F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 3.375F, 0.75F, 0.0F, -0.48F, 1.7453F));

		PartDefinition toeNail9 = footToe9.addOrReplaceChild("toeNail9", CubeListBuilder.create(), PartPose.offset(2.3195F, -0.4427F, -0.0222F));

		PartDefinition cube_r11 = toeNail9.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(98, 15).addBox(4.5F, 0.75F, -2.5F, 1.25F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-4.0695F, -2.4323F, 1.9972F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail4_3 = toeNail9.addOrReplaceChild("toeNail4_3", CubeListBuilder.create(), PartPose.offsetAndRotation(0.9317F, 0.296F, -0.0052F, 0.0F, 0.0F, 0.2618F));

		PartDefinition cube_r12 = toeNail4_3.addOrReplaceChild("cube_r12", CubeListBuilder.create().texOffs(98, 13).addBox(5.25F, 0.75F, -2.5F, 1.5F, 1.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.7927F, -2.7678F, 2.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail4_4 = toeNail4_3.addOrReplaceChild("toeNail4_4", CubeListBuilder.create(), PartPose.offsetAndRotation(0.533F, 0.3234F, 0.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition footLeft = shankLeft.addOrReplaceChild("footLeft", CubeListBuilder.create(), PartPose.offsetAndRotation(1.75F, 8.5F, 0.0F, 0.0F, 0.0F, 0.3491F));

		PartDefinition cube_r13 = footLeft.addOrReplaceChild("cube_r13", CubeListBuilder.create().texOffs(128, 12).addBox(-1.0881F, -0.0449F, -2.0F, 4.5F, 1.5F, 4.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(-0.75F, -0.75F, 0.0F, 0.0F, 0.0F, 0.48F));

		PartDefinition soleLeft = footLeft.addOrReplaceChild("soleLeft", CubeListBuilder.create().texOffs(126, 114).addBox(0.0F, -0.75F, -2.25F, 4.0F, 1.5F, 4.5F, new CubeDeformation(0.01F)), PartPose.offset(0.5F, 1.25F, 0.0F));

		PartDefinition footToe1 = soleLeft.addOrReplaceChild("footToe1", CubeListBuilder.create().texOffs(19, 79).addBox(0.0F, -0.625F, -0.625F, 2.5F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.75F, 0.125F, -1.75F, 0.0F, 0.1309F, 0.0F));

		PartDefinition toeNail1 = footToe1.addOrReplaceChild("toeNail1", CubeListBuilder.create(), PartPose.offset(2.3195F, -0.4427F, -0.0222F));

		PartDefinition cube_r14 = toeNail1.addOrReplaceChild("cube_r14", CubeListBuilder.create().texOffs(98, 15).addBox(4.5F, 0.75F, -2.5F, 1.25F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-4.0695F, -2.4323F, 1.9972F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail1_1 = toeNail1.addOrReplaceChild("toeNail1_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.9317F, 0.296F, -0.0052F, 0.0F, 0.0F, 0.2618F));

		PartDefinition cube_r15 = toeNail1_1.addOrReplaceChild("cube_r15", CubeListBuilder.create().texOffs(98, 13).addBox(5.25F, 0.75F, -2.5F, 1.5F, 1.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.7927F, -2.7678F, 2.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail1_2 = toeNail1_1.addOrReplaceChild("toeNail1_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.533F, 0.3234F, 0.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition footToe2 = soleLeft.addOrReplaceChild("footToe2", CubeListBuilder.create().texOffs(19, 79).addBox(0.0F, -0.625F, -0.625F, 2.5F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offset(3.55F, 0.125F, -0.625F));

		PartDefinition toeNail2 = footToe2.addOrReplaceChild("toeNail2", CubeListBuilder.create(), PartPose.offset(2.3195F, -0.4427F, -0.0222F));

		PartDefinition cube_r16 = toeNail2.addOrReplaceChild("cube_r16", CubeListBuilder.create().texOffs(98, 15).addBox(4.5F, 0.75F, -2.5F, 1.25F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-4.0695F, -2.4323F, 1.9972F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail2_1 = toeNail2.addOrReplaceChild("toeNail2_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.9317F, 0.296F, -0.0052F, 0.0F, 0.0F, 0.2618F));

		PartDefinition cube_r17 = toeNail2_1.addOrReplaceChild("cube_r17", CubeListBuilder.create().texOffs(98, 13).addBox(5.25F, 0.75F, -2.5F, 1.5F, 1.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.7927F, -2.7678F, 2.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail2_2 = toeNail2_1.addOrReplaceChild("toeNail2_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.533F, 0.3234F, 0.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition footToe3 = soleLeft.addOrReplaceChild("footToe3", CubeListBuilder.create().texOffs(19, 79).addBox(0.0F, -0.625F, -0.625F, 2.5F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.25F, 0.125F, 0.6F, 0.0F, -0.0873F, 0.0F));

		PartDefinition toeNail3 = footToe3.addOrReplaceChild("toeNail3", CubeListBuilder.create(), PartPose.offset(2.3195F, -0.4427F, -0.0222F));

		PartDefinition cube_r18 = toeNail3.addOrReplaceChild("cube_r18", CubeListBuilder.create().texOffs(98, 15).addBox(4.5F, 0.75F, -2.5F, 1.25F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-4.0695F, -2.4323F, 1.9972F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail3_1 = toeNail3.addOrReplaceChild("toeNail3_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.9317F, 0.296F, -0.0052F, 0.0F, 0.0F, 0.2618F));

		PartDefinition cube_r19 = toeNail3_1.addOrReplaceChild("cube_r19", CubeListBuilder.create().texOffs(98, 13).addBox(5.25F, 0.75F, -2.5F, 1.5F, 1.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.7927F, -2.7678F, 2.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail3_2 = toeNail3_1.addOrReplaceChild("toeNail3_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.533F, 0.3234F, 0.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition footToe4 = soleLeft.addOrReplaceChild("footToe4", CubeListBuilder.create().texOffs(19, 79).addBox(0.0F, -0.625F, -0.625F, 2.5F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.75F, 0.125F, 1.75F, 0.0F, -0.1745F, 0.0F));

		PartDefinition toeNail4 = footToe4.addOrReplaceChild("toeNail4", CubeListBuilder.create(), PartPose.offset(2.3195F, -0.4427F, -0.0222F));

		PartDefinition cube_r20 = toeNail4.addOrReplaceChild("cube_r20", CubeListBuilder.create().texOffs(98, 15).addBox(4.5F, 0.75F, -2.5F, 1.25F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-4.0695F, -2.4323F, 1.9972F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail4_1 = toeNail4.addOrReplaceChild("toeNail4_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.9317F, 0.296F, -0.0052F, 0.0F, 0.0F, 0.2618F));

		PartDefinition cube_r21 = toeNail4_1.addOrReplaceChild("cube_r21", CubeListBuilder.create().texOffs(98, 13).addBox(5.25F, 0.75F, -2.5F, 1.5F, 1.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.7927F, -2.7678F, 2.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail4_2 = toeNail4_1.addOrReplaceChild("toeNail4_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.533F, 0.3234F, 0.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition tighRight = body.addOrReplaceChild("tighRight", CubeListBuilder.create().texOffs(24, 101).addBox(-2.5F, 0.0F, -1.0F, 5.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -2.0F, 1.25F, 0.48F, 0.0F, -0.5236F));

		PartDefinition cube_r22 = tighRight.addOrReplaceChild("cube_r22", CubeListBuilder.create().texOffs(69, 132).addBox(-1.25F, -5.0F, 0.0F, 3.25F, 5.0F, 2.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(-0.015F, 8.0645F, -1.8829F, -0.1745F, 0.0F, 0.0F));

		PartDefinition cube_r23 = tighRight.addOrReplaceChild("cube_r23", CubeListBuilder.create().texOffs(134, 72).addBox(-0.75F, -4.0F, 0.25F, 3.25F, 4.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(-0.5F, 12.0F, -1.25F, 0.2182F, 0.0F, 0.0F));

		PartDefinition cube_r24 = tighRight.addOrReplaceChild("cube_r24", CubeListBuilder.create().texOffs(114, 131).addBox(-0.75F, -6.0F, -2.0F, 3.25F, 6.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(-0.5F, 12.0F, 3.0F, -0.1745F, 0.0F, 0.0F));

		PartDefinition cube_r25 = tighRight.addOrReplaceChild("cube_r25", CubeListBuilder.create().texOffs(134, 133).addBox(-0.75F, -4.0F, -2.0F, 3.25F, 4.0F, 2.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(-0.5F, 6.1108F, 4.0384F, 0.3054F, 0.0F, 0.0F));

		PartDefinition cube_r26 = tighRight.addOrReplaceChild("cube_r26", CubeListBuilder.create().texOffs(46, 72).addBox(-3.0F, -2.75F, -2.75F, 4.0F, 5.0F, 4.5F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(-0.4675F, 1.108F, 1.23F, -0.2618F, 0.0F, 0.1745F));

		PartDefinition tighFurRight = tighRight.addOrReplaceChild("tighFurRight", CubeListBuilder.create().texOffs(62, 38).addBox(0.05F, 0.0F, -2.0F, 0.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, 11.0F, 1.0F));

		PartDefinition legRight = tighRight.addOrReplaceChild("legRight", CubeListBuilder.create().texOffs(0, 107).addBox(-2.5F, 0.0F, -2.5F, 3.0F, 10.0F, 5.0F, new CubeDeformation(0.01F))
		.texOffs(0, 0).addBox(-2.0F, 0.0F, -2.0F, 4.5F, 12.0F, 4.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 10.0F, 1.0F, -0.3054F, 0.3054F, 0.9599F));

		PartDefinition cube_r27 = legRight.addOrReplaceChild("cube_r27", CubeListBuilder.create().texOffs(55, 0).addBox(0.0F, -3.0F, -2.5F, 2.0F, 3.0F, 5.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(-2.5F, 9.0F, 0.0F, 0.0F, 0.0F, -0.5672F));

		PartDefinition cube_r28 = legRight.addOrReplaceChild("cube_r28", CubeListBuilder.create().texOffs(31, 120).addBox(0.0F, -6.0F, -2.5F, 2.0F, 6.0F, 5.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(-4.1012F, 6.4867F, 0.0F, 0.0F, 0.0F, 0.2574F));

		PartDefinition legFurRIght = legRight.addOrReplaceChild("legFurRIght", CubeListBuilder.create().texOffs(60, 58).addBox(0.0F, 0.0F, -2.0F, 0.0F, 4.0F, 4.0F, new CubeDeformation(-0.01F)), PartPose.offset(-2.05F, 11.0F, 0.0F));

		PartDefinition shankRight = legRight.addOrReplaceChild("shankRight", CubeListBuilder.create().texOffs(0, 24).addBox(0.0F, 0.0F, -1.5F, 3.0F, 10.0F, 3.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(-2.0F, 11.0F, 0.0F, 0.0F, 0.0F, -0.7854F));

		PartDefinition footToe10 = shankRight.addOrReplaceChild("footToe10", CubeListBuilder.create().texOffs(19, 79).addBox(-0.154F, 0.2485F, -0.1633F, 2.5F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.154F, 3.5015F, -1.2117F, 0.0F, 0.48F, 1.7453F));

		PartDefinition toeNail10 = footToe10.addOrReplaceChild("toeNail10", CubeListBuilder.create(), PartPose.offset(2.3195F, -0.4427F, -0.0222F));

		PartDefinition cube_r29 = toeNail10.addOrReplaceChild("cube_r29", CubeListBuilder.create().texOffs(98, 15).addBox(4.5F, 0.75F, -2.5F, 1.25F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-4.2236F, -1.5587F, 2.459F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail4_5 = toeNail10.addOrReplaceChild("toeNail4_5", CubeListBuilder.create(), PartPose.offsetAndRotation(0.9317F, 0.296F, -0.0052F, 0.0F, 0.0F, 0.2618F));

		PartDefinition cube_r30 = toeNail4_5.addOrReplaceChild("cube_r30", CubeListBuilder.create().texOffs(98, 13).addBox(5.25F, 0.75F, -2.5F, 1.5F, 1.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.9467F, -1.8943F, 2.4641F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail4_6 = toeNail4_5.addOrReplaceChild("toeNail4_6", CubeListBuilder.create(), PartPose.offsetAndRotation(0.533F, 0.3234F, 0.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition footRight = shankRight.addOrReplaceChild("footRight", CubeListBuilder.create(), PartPose.offsetAndRotation(1.75F, 8.5F, 0.0F, 0.0F, 0.0F, 0.3491F));

		PartDefinition cube_r31 = footRight.addOrReplaceChild("cube_r31", CubeListBuilder.create().texOffs(126, 106).addBox(-1.0881F, -0.0449F, -2.0F, 4.5F, 1.5F, 4.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(-0.75F, -0.75F, 0.0F, 0.0F, 0.0F, 0.48F));

		PartDefinition soleRight = footRight.addOrReplaceChild("soleRight", CubeListBuilder.create().texOffs(126, 101).addBox(0.0F, -0.75F, -2.25F, 4.0F, 1.5F, 4.5F, new CubeDeformation(0.01F)), PartPose.offset(0.5F, 1.25F, 0.0F));

		PartDefinition footToe5 = soleRight.addOrReplaceChild("footToe5", CubeListBuilder.create().texOffs(70, 17).addBox(0.0F, -0.625F, -0.625F, 2.5F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.75F, 0.125F, -1.75F, 0.0F, 0.1745F, 0.0F));

		PartDefinition toeNail5 = footToe5.addOrReplaceChild("toeNail5", CubeListBuilder.create(), PartPose.offset(2.3195F, -0.4427F, 0.0222F));

		PartDefinition cube_r32 = toeNail5.addOrReplaceChild("cube_r32", CubeListBuilder.create().texOffs(98, 15).addBox(4.5F, 0.75F, 1.5F, 1.25F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-4.0695F, -2.4323F, -1.9972F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail5_1 = toeNail5.addOrReplaceChild("toeNail5_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.9317F, 0.296F, 0.0052F, 0.0F, 0.0F, 0.2618F));

		PartDefinition cube_r33 = toeNail5_1.addOrReplaceChild("cube_r33", CubeListBuilder.create().texOffs(98, 13).addBox(5.25F, 0.75F, 1.5F, 1.5F, 1.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.7927F, -2.7678F, -2.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail5_2 = toeNail5_1.addOrReplaceChild("toeNail5_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.533F, 0.3234F, -0.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition footToe6 = soleRight.addOrReplaceChild("footToe6", CubeListBuilder.create().texOffs(70, 17).addBox(0.0F, -0.625F, -0.625F, 2.5F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.25F, 0.125F, -0.625F, 0.0F, 0.0873F, 0.0F));

		PartDefinition toeNail6 = footToe6.addOrReplaceChild("toeNail6", CubeListBuilder.create(), PartPose.offset(2.3195F, -0.4427F, 0.0222F));

		PartDefinition cube_r34 = toeNail6.addOrReplaceChild("cube_r34", CubeListBuilder.create().texOffs(98, 15).addBox(4.5F, 0.75F, 1.5F, 1.25F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-4.0695F, -2.4323F, -1.9972F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail6_1 = toeNail6.addOrReplaceChild("toeNail6_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.9317F, 0.296F, 0.0052F, 0.0F, 0.0F, 0.2618F));

		PartDefinition cube_r35 = toeNail6_1.addOrReplaceChild("cube_r35", CubeListBuilder.create().texOffs(98, 13).addBox(5.25F, 0.75F, 1.5F, 1.5F, 1.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.7927F, -2.7678F, -2.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail6_2 = toeNail6_1.addOrReplaceChild("toeNail6_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.533F, 0.3234F, -0.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition footToe7 = soleRight.addOrReplaceChild("footToe7", CubeListBuilder.create().texOffs(70, 17).addBox(0.0F, -0.625F, -0.625F, 2.5F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offset(3.55F, 0.125F, 0.6F));

		PartDefinition toeNail7 = footToe7.addOrReplaceChild("toeNail7", CubeListBuilder.create(), PartPose.offset(2.3195F, -0.4427F, 0.0222F));

		PartDefinition cube_r36 = toeNail7.addOrReplaceChild("cube_r36", CubeListBuilder.create().texOffs(98, 15).addBox(4.5F, 0.75F, 1.5F, 1.25F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-4.0695F, -2.4323F, -1.9972F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail7_1 = toeNail7.addOrReplaceChild("toeNail7_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.9317F, 0.296F, 0.0052F, 0.0F, 0.0F, 0.2618F));

		PartDefinition cube_r37 = toeNail7_1.addOrReplaceChild("cube_r37", CubeListBuilder.create().texOffs(98, 13).addBox(5.25F, 0.75F, 1.5F, 1.5F, 1.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.7927F, -2.7678F, -2.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail7_2 = toeNail7_1.addOrReplaceChild("toeNail7_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.533F, 0.3234F, -0.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition footToe8 = soleRight.addOrReplaceChild("footToe8", CubeListBuilder.create().texOffs(70, 17).addBox(0.0F, -0.625F, -0.625F, 2.5F, 1.25F, 1.25F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.75F, 0.125F, 1.75F, 0.0F, -0.1309F, 0.0F));

		PartDefinition toeNail8 = footToe8.addOrReplaceChild("toeNail8", CubeListBuilder.create(), PartPose.offset(2.3195F, -0.4427F, 0.0222F));

		PartDefinition cube_r38 = toeNail8.addOrReplaceChild("cube_r38", CubeListBuilder.create().texOffs(98, 15).addBox(4.5F, 0.75F, 1.5F, 1.25F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(-4.0695F, -2.4323F, -1.9972F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail8_1 = toeNail8.addOrReplaceChild("toeNail8_1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.9317F, 0.296F, 0.0052F, 0.0F, 0.0F, 0.2618F));

		PartDefinition cube_r39 = toeNail8_1.addOrReplaceChild("cube_r39", CubeListBuilder.create().texOffs(98, 13).addBox(5.25F, 0.75F, 1.5F, 1.5F, 1.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-4.7927F, -2.7678F, -2.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition toeNail8_2 = toeNail8_1.addOrReplaceChild("toeNail8_2", CubeListBuilder.create(), PartPose.offsetAndRotation(0.533F, 0.3234F, -0.0024F, 0.0F, 0.0F, 0.3491F));

		PartDefinition lowerBody = body.addOrReplaceChild("lowerBody", CubeListBuilder.create().texOffs(0, 64).addBox(-5.0F, -5.0F, -4.0F, 7.0F, 7.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 93).addBox(-5.0F, 0.0F, -4.0F, 4.0F, 6.0F, 8.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(2.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.1309F));

		PartDefinition cube_r40 = lowerBody.addOrReplaceChild("cube_r40", CubeListBuilder.create().texOffs(76, 136).addBox(-1.0F, -1.0F, -1.75F, 2.0F, 2.0F, 3.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.75F, -0.5F, 2.0F, 0.0436F, -0.1309F, 0.0873F));

		PartDefinition cube_r41 = lowerBody.addOrReplaceChild("cube_r41", CubeListBuilder.create().texOffs(76, 17).addBox(-3.75F, -0.75F, -4.5F, 4.0F, 4.0F, 9.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(-1.1477F, -1.5802F, 0.0F, 0.0F, 0.0F, 0.1309F));

		PartDefinition cube_r42 = lowerBody.addOrReplaceChild("cube_r42", CubeListBuilder.create().texOffs(19, 137).addBox(-1.0F, -1.0F, -1.75F, 2.0F, 2.0F, 3.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.75F, -0.5F, -2.0F, -0.0436F, 0.1309F, 0.0873F));

		PartDefinition cube_r43 = lowerBody.addOrReplaceChild("cube_r43", CubeListBuilder.create().texOffs(90, 124).addBox(-5.0F, -6.0F, 0.0F, 7.0F, 6.0F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 2.0F, -4.0F, 0.1745F, 0.0F, 0.0F));

		PartDefinition cube_r44 = lowerBody.addOrReplaceChild("cube_r44", CubeListBuilder.create().texOffs(45, 125).addBox(-5.0F, -6.0F, -1.0F, 7.0F, 6.0F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 2.0F, 4.0F, -0.1745F, 0.0F, 0.0F));

		PartDefinition middleBody = lowerBody.addOrReplaceChild("middleBody", CubeListBuilder.create().texOffs(60, 0).addBox(-2.8977F, -4.8302F, -5.0F, 8.0F, 7.0F, 10.0F, new CubeDeformation(0.01F))
		.texOffs(60, 56).addBox(-3.3977F, -4.8302F, -5.0F, 4.0F, 8.0F, 10.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(-2.5F, -4.5F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition cube_r45 = middleBody.addOrReplaceChild("cube_r45", CubeListBuilder.create().texOffs(98, 102).addBox(-5.0F, -6.0F, 0.0F, 8.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.1023F, 2.1698F, -5.0F, 0.4363F, 0.0F, 0.0F));

		PartDefinition cube_r46 = middleBody.addOrReplaceChild("cube_r46", CubeListBuilder.create().texOffs(104, 53).addBox(-5.0F, -6.0F, -3.0F, 8.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.1023F, 2.1698F, 5.0F, -0.4363F, 0.0F, 0.0F));

		PartDefinition cube_r47 = middleBody.addOrReplaceChild("cube_r47", CubeListBuilder.create().texOffs(20, 57).addBox(-0.5F, -1.25F, -2.0F, 1.0F, 2.5F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.3523F, 0.4198F, -2.25F, -0.0873F, 0.1309F, 0.0873F));

		PartDefinition cube_r48 = middleBody.addOrReplaceChild("cube_r48", CubeListBuilder.create().texOffs(56, 134).addBox(-0.5F, -1.25F, -2.0F, 1.0F, 2.5F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.3523F, 0.4198F, 2.25F, 0.0873F, -0.1309F, 0.0873F));

		PartDefinition upperBody = middleBody.addOrReplaceChild("upperBody", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -6.75F, -8.5F, 8.0F, 7.0F, 17.0F, new CubeDeformation(0.0F))
		.texOffs(49, 19).addBox(-3.0F, -4.25F, -5.5F, 8.0F, 6.0F, 11.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(-0.8977F, -3.0802F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition necklace = upperBody.addOrReplaceChild("necklace", CubeListBuilder.create().texOffs(18, 0).addBox(-5.5F, -13.75F, -7.5F, 11.5F, 0.0F, 15.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 6.75F, 0.0F));

		PartDefinition chestRight = upperBody.addOrReplaceChild("chestRight", CubeListBuilder.create().texOffs(0, 79).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 7.0F, 7.0F, new CubeDeformation(-0.25F)), PartPose.offsetAndRotation(5.0F, -2.25F, 3.25F, 0.0873F, 0.0F, 0.0F));

		PartDefinition leftChest = upperBody.addOrReplaceChild("leftChest", CubeListBuilder.create().texOffs(78, 49).addBox(-3.0F, -4.0F, -4.0F, 6.0F, 7.0F, 7.0F, new CubeDeformation(-0.25F)), PartPose.offsetAndRotation(5.0F, -2.25F, -3.5F, -0.0873F, 0.0F, 0.0F));

		PartDefinition armLeft = upperBody.addOrReplaceChild("armLeft", CubeListBuilder.create().texOffs(69, 88).addBox(-3.0F, 0.2321F, -5.7141F, 6.0F, 8.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, -5.75F, -6.5F, -0.5672F, 0.0F, -0.2182F));

		PartDefinition upperArmLeft = armLeft.addOrReplaceChild("upperArmLeft", CubeListBuilder.create().texOffs(94, 30).addBox(-3.0F, 0.0F, -1.0F, 5.0F, 9.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 6.7321F, -3.9641F, 0.2182F, 0.0F, -0.1745F));

		PartDefinition lowerArmLeft = upperArmLeft.addOrReplaceChild("lowerArmLeft", CubeListBuilder.create().texOffs(113, 83).addBox(-2.5F, 0.0F, -2.0F, 5.0F, 10.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 8.0F, 1.5F, 0.3447F, 0.0524F, -0.5236F));

		PartDefinition cube_r49 = lowerArmLeft.addOrReplaceChild("cube_r49", CubeListBuilder.create().texOffs(34, 131).addBox(-2.0F, -4.0F, -2.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 4.0712F, 2.7805F, 0.1745F, 0.0F, 0.0F));

		PartDefinition cube_r50 = lowerArmLeft.addOrReplaceChild("cube_r50", CubeListBuilder.create().texOffs(124, 133).addBox(-1.5F, -3.0F, -2.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 6.8441F, 1.4947F, -0.3927F, 0.0F, 0.0F));

		PartDefinition cube_r51 = lowerArmLeft.addOrReplaceChild("cube_r51", CubeListBuilder.create().texOffs(53, 110).addBox(-1.5F, -3.0F, -2.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 9.7788F, 0.9773F, -0.1745F, 0.0F, 0.0F));

		PartDefinition cube_r52 = lowerArmLeft.addOrReplaceChild("cube_r52", CubeListBuilder.create().texOffs(88, 131).addBox(-1.5F, -6.0F, 0.0F, 3.0F, 6.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 10.0F, -2.0F, 0.1745F, 0.0F, 0.0F));

		PartDefinition cube_r53 = lowerArmLeft.addOrReplaceChild("cube_r53", CubeListBuilder.create().texOffs(132, 41).addBox(-1.9995F, 0.0F, 0.0F, 4.0F, 4.5F, 2.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(0.01F, -0.2789F, -2.0802F, -0.2182F, 0.0F, 0.0F));

		PartDefinition LowerArmFurLeft = lowerArmLeft.addOrReplaceChild("LowerArmFurLeft", CubeListBuilder.create().texOffs(0, 62).addBox(-0.0583F, -5.0488F, -2.0F, 0.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.4341F, 2.038F, -0.0718F, 0.0F, 0.0F, -0.1745F));

		PartDefinition handLeft = lowerArmLeft.addOrReplaceChild("handLeft", CubeListBuilder.create().texOffs(124, 5).addBox(-2.5F, -1.0F, -1.5F, 6.0F, 5.0F, 2.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 10.0F, -0.5F, 0.5672F, 0.0F, -0.1309F));

		PartDefinition cube_r54 = handLeft.addOrReplaceChild("cube_r54", CubeListBuilder.create().texOffs(138, 47).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 1.8978F, -2.2765F, 0.3927F, 0.0F, 0.0F));

		PartDefinition cube_r55 = handLeft.addOrReplaceChild("cube_r55", CubeListBuilder.create().texOffs(117, 101).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -1.0F, -1.5F, -0.2618F, 0.0F, 0.0F));

		PartDefinition fingerLeft_2 = handLeft.addOrReplaceChild("fingerLeft_2", CubeListBuilder.create().texOffs(46, 72).addBox(-0.75F, 0.0F, 0.0F, 1.5F, 3.0F, 1.5F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(-1.25F, 2.5F, -1.5F, -0.4363F, 0.0F, 0.0F));

		PartDefinition nailRing7 = fingerLeft_2.addOrReplaceChild("nailRing7", CubeListBuilder.create(), PartPose.offset(0.0F, 1.5F, 21.1782F));

		PartDefinition upperFingerLeft_2 = fingerLeft_2.addOrReplaceChild("upperFingerLeft_2", CubeListBuilder.create().texOffs(44, 93).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 3.0F, 0.25F, 0.4363F, 0.0F, 0.0F));

		PartDefinition lowerFingerLeft_2 = upperFingerLeft_2.addOrReplaceChild("lowerFingerLeft_2", CubeListBuilder.create().texOffs(96, 9).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.5F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.5236F, 0.0F, 0.0F));

		PartDefinition fingerNailLeft_2 = lowerFingerLeft_2.addOrReplaceChild("fingerNailLeft_2", CubeListBuilder.create().texOffs(0, 24).addBox(-0.5F, -0.025F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.3491F, 0.0F, 0.0F));

		PartDefinition fingerLeft_5 = handLeft.addOrReplaceChild("fingerLeft_5", CubeListBuilder.create().texOffs(65, 56).addBox(-0.75F, 0.0F, 0.0F, 1.5F, 3.0F, 1.5F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(-2.5F, 1.5F, -1.0F, -0.4363F, 0.7854F, 0.0F));

		PartDefinition nailRing6 = fingerLeft_5.addOrReplaceChild("nailRing6", CubeListBuilder.create(), PartPose.offset(0.0F, 1.5F, 20.1782F));

		PartDefinition upperFingerLeft_5 = fingerLeft_5.addOrReplaceChild("upperFingerLeft_5", CubeListBuilder.create().texOffs(95, 76).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.5F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 3.0F, 0.25F, 0.4363F, 0.0F, 0.0F));

		PartDefinition lowerFingerLeft_5 = upperFingerLeft_5.addOrReplaceChild("lowerFingerLeft_5", CubeListBuilder.create().texOffs(95, 74).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.5F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.5236F, 0.0F, 0.0F));

		PartDefinition fingerNailLeft_5 = lowerFingerLeft_5.addOrReplaceChild("fingerNailLeft_5", CubeListBuilder.create().texOffs(0, 24).addBox(-0.5F, -0.025F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.3491F, 0.0F, 0.0F));

		PartDefinition fingerLeft_3 = handLeft.addOrReplaceChild("fingerLeft_3", CubeListBuilder.create().texOffs(26, 57).addBox(-0.75F, 0.0F, 0.0F, 1.5F, 3.5F, 1.5F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(0.5F, 2.5F, -1.5F, -0.4363F, 0.0F, 0.0F));

		PartDefinition nailRing8 = fingerLeft_3.addOrReplaceChild("nailRing8", CubeListBuilder.create(), PartPose.offset(0.0F, 1.75F, 21.1782F));

		PartDefinition upperFingerLeft_3 = fingerLeft_3.addOrReplaceChild("upperFingerLeft_3", CubeListBuilder.create().texOffs(16, 93).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 3.5F, 0.25F, 0.4363F, 0.0F, 0.0F));

		PartDefinition lowerFingerLeft_3 = upperFingerLeft_3.addOrReplaceChild("lowerFingerLeft_3", CubeListBuilder.create().texOffs(71, 88).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.5F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.5236F, 0.0F, 0.0F));

		PartDefinition fingerNailLeft_3 = lowerFingerLeft_3.addOrReplaceChild("fingerNailLeft_3", CubeListBuilder.create().texOffs(0, 24).addBox(-0.5F, -0.025F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.3491F, 0.0F, 0.0F));

		PartDefinition fingerLeft_4 = handLeft.addOrReplaceChild("fingerLeft_4", CubeListBuilder.create().texOffs(0, 57).addBox(-0.75F, 0.0F, 0.0F, 1.5F, 3.0F, 1.5F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(2.25F, 2.5F, -1.5F, -0.4363F, 0.0F, 0.0F));

		PartDefinition nailRing9 = fingerLeft_4.addOrReplaceChild("nailRing9", CubeListBuilder.create(), PartPose.offset(0.0F, 1.5F, 21.1782F));

		PartDefinition upperFingerLeft_4 = fingerLeft_4.addOrReplaceChild("upperFingerLeft_4", CubeListBuilder.create().texOffs(86, 3).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 3.0F, 0.25F, 0.4363F, 0.0F, 0.0F));

		PartDefinition lowerFingerLeft_4 = upperFingerLeft_4.addOrReplaceChild("lowerFingerLeft_4", CubeListBuilder.create().texOffs(67, 88).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 1.5F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.5236F, 0.0F, 0.0F));

		PartDefinition fingerNailLeft_4 = lowerFingerLeft_4.addOrReplaceChild("fingerNailLeft_4", CubeListBuilder.create().texOffs(0, 24).addBox(-0.5F, -0.025F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.3491F, 0.0F, 0.0F));

		PartDefinition finger_6 = handLeft.addOrReplaceChild("finger_6", CubeListBuilder.create().texOffs(52, 56).addBox(-0.75F, 0.0F, 0.0F, 1.5F, 3.0F, 1.5F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(3.5F, 1.5F, -1.0F, -0.4363F, -0.7854F, 0.0F));

		PartDefinition nailRing10 = finger_6.addOrReplaceChild("nailRing10", CubeListBuilder.create(), PartPose.offset(0.0F, 1.75F, 20.1782F));

		PartDefinition upperFinger_6 = finger_6.addOrReplaceChild("upperFinger_6", CubeListBuilder.create().texOffs(86, 0).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 3.0F, 0.25F, 0.4363F, 0.0F, 0.0F));

		PartDefinition fingerNail_6 = upperFinger_6.addOrReplaceChild("fingerNail_6", CubeListBuilder.create().texOffs(0, 24).addBox(-0.485F, -1.3598F, 0.3949F, 1.0F, 2.0F, 0.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 3.5F, 0.0F, 0.3491F, 0.0F, 0.0F));

		PartDefinition armRight = upperBody.addOrReplaceChild("armRight", CubeListBuilder.create().texOffs(20, 87).addBox(-3.0F, 0.2321F, -0.2859F, 6.0F, 8.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, -5.75F, 6.5F, 0.5672F, 0.0F, -0.2182F));

		PartDefinition upperArmRight = armRight.addOrReplaceChild("upperArmRight", CubeListBuilder.create().texOffs(93, 88).addBox(-3.0F, 0.0F, -4.0F, 5.0F, 9.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 6.7321F, 3.9641F, -0.2182F, 0.0F, -0.1745F));

		PartDefinition lowerArmRight = upperArmRight.addOrReplaceChild("lowerArmRight", CubeListBuilder.create().texOffs(98, 111).addBox(-2.5F, 0.0F, -1.0F, 5.0F, 10.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 8.0F, -1.5F, -0.3447F, -0.0349F, -0.5236F));

		PartDefinition cube_r56 = lowerArmRight.addOrReplaceChild("cube_r56", CubeListBuilder.create().texOffs(129, 89).addBox(-2.0F, -4.0F, 0.0F, 4.0F, 4.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 4.0712F, -2.7805F, -0.1745F, 0.0F, 0.0F));

		PartDefinition cube_r57 = lowerArmRight.addOrReplaceChild("cube_r57", CubeListBuilder.create().texOffs(132, 124).addBox(-1.5F, -3.0F, 0.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 6.8441F, -1.4947F, 0.3927F, 0.0F, 0.0F));

		PartDefinition cube_r58 = lowerArmRight.addOrReplaceChild("cube_r58", CubeListBuilder.create().texOffs(87, 88).addBox(-1.5F, -3.0F, 0.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 9.7788F, -0.9773F, 0.1745F, 0.0F, 0.0F));

		PartDefinition cube_r59 = lowerArmRight.addOrReplaceChild("cube_r59", CubeListBuilder.create().texOffs(12, 131).addBox(-1.5F, -6.0F, -2.0F, 3.0F, 6.0F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 10.0F, 2.0F, -0.1745F, 0.0F, 0.0F));

		PartDefinition cube_r60 = lowerArmRight.addOrReplaceChild("cube_r60", CubeListBuilder.create().texOffs(22, 131).addBox(-1.9995F, 0.0F, -2.0F, 4.0F, 4.5F, 2.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(0.01F, -0.2789F, 2.0802F, 0.2182F, 0.0F, 0.0F));

		PartDefinition lowerArmFurRight = lowerArmRight.addOrReplaceChild("lowerArmFurRight", CubeListBuilder.create().texOffs(0, 57).addBox(0.0F, -5.0F, -2.0F, 0.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, 2.0F, 0.0F, 0.0F, 0.0F, -0.1745F));

		PartDefinition handRight = lowerArmRight.addOrReplaceChild("handRight", CubeListBuilder.create().texOffs(112, 123).addBox(-2.5F, -1.0F, -1.0F, 6.0F, 5.0F, 2.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.5F, 10.0F, 0.5F, -0.5672F, 0.0F, -0.1309F));

		PartDefinition cube_r61 = handRight.addOrReplaceChild("cube_r61", CubeListBuilder.create().texOffs(138, 28).addBox(-1.5F, 0.0F, -1.0F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 1.8978F, 2.2765F, -0.3927F, 0.0F, 0.0F));

		PartDefinition cube_r62 = handRight.addOrReplaceChild("cube_r62", CubeListBuilder.create().texOffs(112, 5).addBox(-2.0F, 0.0F, -1.0F, 4.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -1.0F, 1.5F, 0.2618F, 0.0F, 0.0F));

		PartDefinition fingerRight_7 = handRight.addOrReplaceChild("fingerRight_7", CubeListBuilder.create().texOffs(55, 10).addBox(-0.75F, 0.0F, -1.5F, 1.5F, 3.0F, 1.5F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(-1.25F, 2.5F, 1.5F, 0.4363F, 0.0F, 0.0F));

		PartDefinition nailRing4 = fingerRight_7.addOrReplaceChild("nailRing4", CubeListBuilder.create(), PartPose.offset(0.0F, 1.5F, -0.75F));

		PartDefinition upperFinger_7 = fingerRight_7.addOrReplaceChild("upperFinger_7", CubeListBuilder.create().texOffs(19, 83).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 3.0F, -0.25F, -0.4363F, 0.0F, 0.0F));

		PartDefinition lowerFinger_6 = upperFinger_7.addOrReplaceChild("lowerFinger_6", CubeListBuilder.create().texOffs(87, 34).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 1.5F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, -0.5236F, 0.0F, 0.0F));

		PartDefinition fingerNail_7 = lowerFinger_6.addOrReplaceChild("fingerNail_7", CubeListBuilder.create().texOffs(0, 24).addBox(-0.5F, -0.025F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition fingerRight_8 = handRight.addOrReplaceChild("fingerRight_8", CubeListBuilder.create().texOffs(55, 0).addBox(-0.75F, 0.0F, -1.5F, 1.5F, 3.0F, 1.5F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(-2.5F, 1.5F, 1.0F, 0.4363F, -0.7854F, 0.0F));

		PartDefinition nailRing5 = fingerRight_8.addOrReplaceChild("nailRing5", CubeListBuilder.create(), PartPose.offset(0.0F, 1.5F, -0.75F));

		PartDefinition upperFinger_8 = fingerRight_8.addOrReplaceChild("upperFinger_8", CubeListBuilder.create().texOffs(23, 83).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 1.5F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 3.0F, -0.25F, -0.4363F, 0.0F, 0.0F));

		PartDefinition lowerFinger_7 = upperFinger_8.addOrReplaceChild("lowerFinger_7", CubeListBuilder.create().texOffs(25, 79).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 1.5F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, -0.5236F, 0.0F, 0.0F));

		PartDefinition fingerNail_8 = lowerFinger_7.addOrReplaceChild("fingerNail_8", CubeListBuilder.create().texOffs(0, 24).addBox(-0.5F, -0.025F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition fingerRight_9 = handRight.addOrReplaceChild("fingerRight_9", CubeListBuilder.create().texOffs(30, 51).addBox(-0.75F, 0.0F, -1.5F, 1.5F, 3.5F, 1.5F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(0.5F, 2.5F, 1.5F, 0.4363F, 0.0F, 0.0F));

		PartDefinition nailRing3 = fingerRight_9.addOrReplaceChild("nailRing3", CubeListBuilder.create(), PartPose.offset(0.0F, 1.75F, -0.75F));

		PartDefinition upperFinger_9 = fingerRight_9.addOrReplaceChild("upperFinger_9", CubeListBuilder.create().texOffs(80, 49).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 3.5F, -0.25F, -0.4363F, 0.0F, 0.0F));

		PartDefinition lowerFinger_8 = upperFinger_9.addOrReplaceChild("lowerFinger_8", CubeListBuilder.create().texOffs(58, 74).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 1.5F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, -0.5236F, 0.0F, 0.0F));

		PartDefinition fingerNail_9 = lowerFinger_8.addOrReplaceChild("fingerNail_9", CubeListBuilder.create().texOffs(0, 24).addBox(-0.5F, -0.025F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition fingerRight_10 = handRight.addOrReplaceChild("fingerRight_10", CubeListBuilder.create().texOffs(12, 0).addBox(-0.75F, 0.0F, -1.5F, 1.5F, 3.0F, 1.5F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(2.25F, 2.5F, 1.5F, 0.4363F, 0.0F, 0.0F));

		PartDefinition nailRing2 = fingerRight_10.addOrReplaceChild("nailRing2", CubeListBuilder.create(), PartPose.offset(0.0F, 1.5F, -0.75F));

		PartDefinition upperFinger_10 = fingerRight_10.addOrReplaceChild("upperFinger_10", CubeListBuilder.create().texOffs(72, 49).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 3.0F, -0.25F, -0.4363F, 0.0F, 0.0F));

		PartDefinition lowerFinger_9 = upperFinger_10.addOrReplaceChild("lowerFinger_9", CubeListBuilder.create().texOffs(65, 8).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 1.5F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, -0.5236F, 0.0F, 0.0F));

		PartDefinition fingerNail_10 = lowerFinger_9.addOrReplaceChild("fingerNail_10", CubeListBuilder.create().texOffs(0, 24).addBox(-0.5F, -0.025F, 0.0F, 1.0F, 2.0F, 0.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition fingerRight_11 = handRight.addOrReplaceChild("fingerRight_11", CubeListBuilder.create().texOffs(0, 0).addBox(-0.75F, 0.0F, -1.5F, 1.5F, 3.0F, 1.5F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(3.5F, 1.5F, 1.0F, 0.4363F, 0.7854F, 0.0F));

		PartDefinition upperFinger_11 = fingerRight_11.addOrReplaceChild("upperFinger_11", CubeListBuilder.create().texOffs(46, 24).addBox(-0.5F, 0.0F, -1.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 3.0F, -0.25F, -0.4363F, 0.0F, 0.0F));

		PartDefinition fingerNail_11 = upperFinger_11.addOrReplaceChild("fingerNail_11", CubeListBuilder.create().texOffs(0, 24).addBox(-0.485F, -1.3598F, -0.3949F, 1.0F, 2.0F, 0.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 3.5F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition lowerNeck = upperBody.addOrReplaceChild("lowerNeck", CubeListBuilder.create().texOffs(0, 24).addBox(0.0F, -6.0F, -6.5F, 9.0F, 9.0F, 13.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, -5.25F, 0.0F, 0.0F, 0.0F, 0.3491F));

		PartDefinition upperNeck = lowerNeck.addOrReplaceChild("upperNeck", CubeListBuilder.create().texOffs(34, 36).addBox(0.0F, -5.0F, -5.0F, 9.0F, 10.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -6.0F, 0.0F, 0.0F, 0.0F, 0.3054F));

		PartDefinition head = upperNeck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(22, 56).addBox(-5.0F, 0.0F, -4.0F, 11.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, -5.0F, 0.0F, 0.0F, 0.0F, -1.1345F));

		PartDefinition cube_r63 = head.addOrReplaceChild("cube_r63", CubeListBuilder.create().texOffs(67, 82).addBox(-3.0F, -2.0F, -2.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, 4.0F, 4.0F, 0.0F, 0.3927F, 0.0F));

		PartDefinition cube_r64 = head.addOrReplaceChild("cube_r64", CubeListBuilder.create().texOffs(67, 82).addBox(-3.0F, -2.0F, 0.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, 4.0F, -4.0F, 0.0F, -0.3927F, 0.0F));

		PartDefinition cube_r65 = head.addOrReplaceChild("cube_r65", CubeListBuilder.create().texOffs(114, 105).addBox(-3.0F, 0.0F, -3.25F, 3.0F, 3.0F, 6.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.1809F, -1.0261F, 0.0F, 0.0F, 0.0F, -0.3491F));

		PartDefinition cube_r66 = head.addOrReplaceChild("cube_r66", CubeListBuilder.create().texOffs(114, 114).addBox(-3.0F, 0.0F, -3.25F, 3.0F, 3.0F, 6.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3491F));

		PartDefinition headFurLeft = head.addOrReplaceChild("headFurLeft", CubeListBuilder.create(), PartPose.offset(3.2284F, 4.0F, -5.1481F));

		PartDefinition cube_r67 = headFurLeft.addOrReplaceChild("cube_r67", CubeListBuilder.create().texOffs(64, 0).addBox(-3.0F, -2.0F, 0.0F, 3.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.3927F, 0.0F));

		PartDefinition headFurRight = head.addOrReplaceChild("headFurRight", CubeListBuilder.create(), PartPose.offset(3.2284F, 4.0F, 5.1481F));

		PartDefinition cube_r68 = headFurRight.addOrReplaceChild("cube_r68", CubeListBuilder.create().texOffs(64, 0).addBox(-3.0F, -2.0F, 0.0F, 3.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.3927F, 0.0F));

		PartDefinition headFurTop = head.addOrReplaceChild("headFurTop", CubeListBuilder.create(), PartPose.offset(3.1809F, -1.0261F, 0.0F));

		PartDefinition cube_r69 = headFurTop.addOrReplaceChild("cube_r69", CubeListBuilder.create().texOffs(104, 62).addBox(-5.0F, 0.0F, -3.25F, 5.0F, 2.0F, 6.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.1309F));

		PartDefinition earLeft = head.addOrReplaceChild("earLeft", CubeListBuilder.create(), PartPose.offset(3.0F, 1.5F, -3.0F));

		PartDefinition cube_r70 = earLeft.addOrReplaceChild("cube_r70", CubeListBuilder.create().texOffs(136, 78).addBox(-3.764F, -2.6165F, -1.1844F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.944F, -0.6614F, -2.0036F, -0.5724F, -0.4299F, 0.7256F));

		PartDefinition cube_r71 = earLeft.addOrReplaceChild("cube_r71", CubeListBuilder.create().texOffs(93, 22).addBox(-4.0F, -1.5F, -1.0F, 5.0F, 3.0F, 1.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.5724F, -0.4299F, 0.7256F));

		PartDefinition earRight = head.addOrReplaceChild("earRight", CubeListBuilder.create(), PartPose.offset(3.0F, 1.5F, 3.0F));

		PartDefinition cube_r72 = earRight.addOrReplaceChild("cube_r72", CubeListBuilder.create().texOffs(132, 111).addBox(-3.764F, -2.6165F, 0.1844F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.944F, -0.6614F, 2.0036F, 0.5724F, 0.4299F, 0.7256F));

		PartDefinition cube_r73 = earRight.addOrReplaceChild("cube_r73", CubeListBuilder.create().texOffs(87, 30).addBox(-4.0F, -1.5F, -0.5F, 5.0F, 3.0F, 1.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.5724F, 0.4299F, 0.7256F));

		PartDefinition upperHead = head.addOrReplaceChild("upperHead", CubeListBuilder.create().texOffs(126, 22).addBox(3.0F, 2.0F, -2.0F, 4.0F, 2.5F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(80, 78).addBox(6.25F, 1.25F, -1.0F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(38, 83).addBox(0.0F, 2.0F, -2.5F, 3.0F, 2.5F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(41, 11).addBox(7.0F, 4.5F, -2.0F, 0.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(6.0F, 1.0F, 0.0F));

		PartDefinition cube_r74 = upperHead.addOrReplaceChild("cube_r74", CubeListBuilder.create().texOffs(72, 49).addBox(-1.9947F, 0.0018F, 0.0F, 2.0F, 3.0F, 4.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(1.9301F, -0.477F, -2.0F, 0.0F, 0.0F, -0.3927F));

		PartDefinition cube_r75 = upperHead.addOrReplaceChild("cube_r75", CubeListBuilder.create().texOffs(102, 127).addBox(-3.0F, 0.0F, -2.0F, 3.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, -1.309F));

		PartDefinition cube_r76 = upperHead.addOrReplaceChild("cube_r76", CubeListBuilder.create().texOffs(65, 60).addBox(-1.9842F, -2.49F, -0.0216F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.3635F, 6.99F, 3.6964F, 0.0F, 0.1745F, 0.0F));

		PartDefinition cube_r77 = upperHead.addOrReplaceChild("cube_r77", CubeListBuilder.create().texOffs(0, 71).addBox(-2.0F, -2.5F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.9994F, 7.0F, 2.4992F, 0.0F, 0.6109F, 0.0F));

		PartDefinition cube_r78 = upperHead.addOrReplaceChild("cube_r78", CubeListBuilder.create().texOffs(52, 62).addBox(-4.0F, -3.0F, 0.0F, 4.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.9887F, 7.49F, 1.9903F, 0.0F, 0.1222F, 0.0F));

		PartDefinition cube_r79 = upperHead.addOrReplaceChild("cube_r79", CubeListBuilder.create().texOffs(4, 71).addBox(-1.9642F, -2.49F, -0.1168F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.3151F, 6.97F, -3.5102F, 0.0F, -0.1745F, 0.0F));

		PartDefinition cube_r80 = upperHead.addOrReplaceChild("cube_r80", CubeListBuilder.create().texOffs(72, 52).addBox(-2.0F, -2.5F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, 7.0F, -2.5F, 0.0F, -0.6109F, 0.0F));

		PartDefinition cube_r81 = upperHead.addOrReplaceChild("cube_r81", CubeListBuilder.create().texOffs(20, 63).addBox(-4.0F, -3.0F, 0.0F, 4.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.9889F, 7.49F, -1.9913F, 0.0F, -0.1222F, 0.0F));

		PartDefinition cube_r82 = upperHead.addOrReplaceChild("cube_r82", CubeListBuilder.create().texOffs(60, 93).addBox(-2.0F, -2.5F, -2.0F, 2.0F, 2.5F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(3.0F, 4.5F, 2.5F, 0.0F, 0.6109F, 0.0F));

		PartDefinition cube_r83 = upperHead.addOrReplaceChild("cube_r83", CubeListBuilder.create().texOffs(78, 102).addBox(-1.9842F, -2.49F, -2.0216F, 2.0F, 2.5F, 2.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(1.3755F, 4.5F, 3.6734F, 0.0F, 0.1745F, 0.0F));

		PartDefinition cube_r84 = upperHead.addOrReplaceChild("cube_r84", CubeListBuilder.create().texOffs(109, 30).addBox(-1.9642F, -2.49F, -0.1168F, 2.0F, 2.5F, 2.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(1.3383F, 4.49F, -3.5265F, 0.0F, -0.1745F, 0.0F));

		PartDefinition cube_r85 = upperHead.addOrReplaceChild("cube_r85", CubeListBuilder.create().texOffs(112, 70).addBox(-2.0F, -2.5F, 0.0F, 2.0F, 2.5F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(3.0F, 4.5F, -2.5F, 0.0F, -0.6109F, 0.0F));

		PartDefinition cube_r86 = upperHead.addOrReplaceChild("cube_r86", CubeListBuilder.create().texOffs(78, 118).addBox(0.0F, 0.0F, -1.5F, 7.0F, 2.0F, 3.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(0.0F, 0.5F, 0.0F, 0.0F, 0.0F, 0.1309F));

		PartDefinition cube_r87 = upperHead.addOrReplaceChild("cube_r87", CubeListBuilder.create().texOffs(31, 34).addBox(-5.0F, -3.0F, -1.0F, 5.0F, 2.5F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(7.0F, 5.0F, 2.0F, 0.0F, 0.1222F, 0.0F));

		PartDefinition cube_r88 = upperHead.addOrReplaceChild("cube_r88", CubeListBuilder.create().texOffs(8, 61).addBox(-5.0F, -3.0F, 0.0F, 5.0F, 2.5F, 1.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(7.0F, 5.0F, -2.0F, 0.0F, -0.1222F, 0.0F));

		PartDefinition cube_r89 = upperHead.addOrReplaceChild("cube_r89", CubeListBuilder.create().texOffs(122, 22).addBox(0.0F, -2.5F, -2.0F, 2.5F, 2.5F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.1F, 2.025F, 3.925F, 0.0F, 0.8727F, 0.0F));

		PartDefinition cube_r90 = upperHead.addOrReplaceChild("cube_r90", CubeListBuilder.create().texOffs(79, 132).addBox(0.0F, -2.5F, 0.0F, 2.5F, 2.5F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.1F, 2.025F, -3.925F, 0.0F, -0.8727F, 0.0F));

		PartDefinition eyebrowLeft = upperHead.addOrReplaceChild("eyebrowLeft", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, -3.0F));

		PartDefinition cube_r91 = eyebrowLeft.addOrReplaceChild("cube_r91", CubeListBuilder.create().texOffs(104, 86).addBox(-1.75F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.8651F, 0.0F, 0.2531F, -0.5271F, -0.8764F, 0.352F));

		PartDefinition eyebrowRight = upperHead.addOrReplaceChild("eyebrowRight", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 3.0F));

		PartDefinition cube_r92 = eyebrowRight.addOrReplaceChild("cube_r92", CubeListBuilder.create().texOffs(104, 86).addBox(-1.75F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.8651F, 0.0F, -0.2531F, 0.5271F, 0.8764F, 0.352F));

		PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(114, 70).addBox(-1.9526F, -2.0671F, -3.0F, 4.0F, 2.5F, 6.0F, new CubeDeformation(-0.01F))
		.texOffs(115, 17).addBox(2.0474F, -2.0671F, -2.0F, 6.0F, 1.5F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(34, 113).addBox(8.0474F, -0.5671F, -2.125F, 0.0F, 2.0F, 4.25F, new CubeDeformation(0.0F))
		.texOffs(33, 11).addBox(8.0474F, -4.0671F, -2.0F, 0.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.1745F));

		PartDefinition cube_r93 = jaw.addOrReplaceChild("cube_r93", CubeListBuilder.create().texOffs(52, 63).addBox(-4.0F, -1.5F, 0.0F, 3.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.025F, -1.5471F, 1.9816F, 0.0F, 0.1222F, 0.0F));

		PartDefinition cube_r94 = jaw.addOrReplaceChild("cube_r94", CubeListBuilder.create().texOffs(94, 102).addBox(-4.0F, -2.0F, 0.0F, 3.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.025F, 1.4129F, 1.9816F, 0.0F, 0.1222F, 0.0F));

		PartDefinition cube_r95 = jaw.addOrReplaceChild("cube_r95", CubeListBuilder.create().texOffs(38, 101).addBox(-4.0F, -2.0F, 0.0F, 3.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
		.texOffs(64, 4).addBox(-4.0F, -4.46F, 0.0F, 3.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.0251F, 1.4129F, -1.9826F, 0.0F, -0.1222F, 0.0F));

		PartDefinition cube_r96 = jaw.addOrReplaceChild("cube_r96", CubeListBuilder.create().texOffs(55, 8).addBox(-5.0F, -2.0F, -1.0F, 4.0F, 1.5F, 1.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(9.0474F, -0.0671F, 2.0F, 0.0F, 0.1222F, 0.0F));

		PartDefinition cube_r97 = jaw.addOrReplaceChild("cube_r97", CubeListBuilder.create().texOffs(60, 17).addBox(-5.0F, -2.0F, 0.0F, 4.0F, 1.5F, 1.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(9.0474F, -0.0671F, -2.0F, 0.0F, -0.1222F, 0.0F));

		PartDefinition cube_r98 = jaw.addOrReplaceChild("cube_r98", CubeListBuilder.create().texOffs(55, 4).addBox(-1.9642F, -0.99F, 0.1168F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.3624F, -2.0571F, 3.5092F, 0.0F, 0.1745F, 0.0F));

		PartDefinition cube_r99 = jaw.addOrReplaceChild("cube_r99", CubeListBuilder.create().texOffs(55, 14).addBox(-2.0F, -1.0F, 0.0F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0329F, -2.0571F, 2.4967F, 0.0F, 0.6109F, 0.0F));

		PartDefinition cube_r100 = jaw.addOrReplaceChild("cube_r100", CubeListBuilder.create().texOffs(24, 101).addBox(-2.0F, -1.5F, 0.0F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0549F, 0.9129F, -2.4701F, 0.0F, -0.6109F, 0.0F));

		PartDefinition cube_r101 = jaw.addOrReplaceChild("cube_r101", CubeListBuilder.create().texOffs(64, 101).addBox(-1.9642F, -1.49F, -0.1168F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.361F, 0.9029F, -3.5084F, 0.0F, -0.1745F, 0.0F));

		PartDefinition cube_r102 = jaw.addOrReplaceChild("cube_r102", CubeListBuilder.create().texOffs(38, 103).addBox(-1.9642F, -1.49F, 0.1168F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.3609F, 0.9029F, 3.5075F, 0.0F, 0.1745F, 0.0F));

		PartDefinition cube_r103 = jaw.addOrReplaceChild("cube_r103", CubeListBuilder.create().texOffs(24, 103).addBox(-2.0F, -1.5F, 0.0F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0544F, 0.9129F, 2.4692F, 0.0F, 0.6109F, 0.0F));

		PartDefinition cube_r104 = jaw.addOrReplaceChild("cube_r104", CubeListBuilder.create().texOffs(88, 68).addBox(-1.9642F, -1.49F, -1.8832F, 2.0F, 1.5F, 2.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(3.3857F, -0.5771F, 3.5265F, 0.0F, 0.1745F, 0.0F));

		PartDefinition cube_r105 = jaw.addOrReplaceChild("cube_r105", CubeListBuilder.create().texOffs(38, 90).addBox(-2.0F, -1.5F, -2.0F, 2.0F, 1.5F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(5.0474F, -0.5671F, 2.5F, 0.0F, 0.6109F, 0.0F));

		PartDefinition cube_r106 = jaw.addOrReplaceChild("cube_r106", CubeListBuilder.create().texOffs(30, 55).addBox(-1.99F, -1.01F, -0.01F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0335F, -2.0571F, -2.4975F, 0.0F, -0.6109F, 0.0F));

		PartDefinition cube_r107 = jaw.addOrReplaceChild("cube_r107", CubeListBuilder.create().texOffs(0, 98).addBox(-2.0F, -1.5F, 0.0F, 2.0F, 1.5F, 2.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(5.0474F, -0.5671F, -2.5F, 0.0F, -0.6109F, 0.0F));

		PartDefinition cube_r108 = jaw.addOrReplaceChild("cube_r108", CubeListBuilder.create().texOffs(52, 60).addBox(-1.9642F, -0.99F, -0.1168F, 2.0F, 1.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.3625F, -2.0571F, -3.5102F, 0.0F, -0.1745F, 0.0F));

		PartDefinition cube_r109 = jaw.addOrReplaceChild("cube_r109", CubeListBuilder.create().texOffs(61, 125).addBox(-1.9642F, -1.49F, -0.1168F, 2.0F, 1.5F, 2.0F, new CubeDeformation(-0.02F)), PartPose.offsetAndRotation(3.3857F, -0.5771F, -3.5265F, 0.0F, -0.1745F, 0.0F));

		PartDefinition cube_r110 = jaw.addOrReplaceChild("cube_r110", CubeListBuilder.create().texOffs(126, 28).addBox(0.0F, -1.5F, -2.0F, 4.0F, 1.5F, 4.0F, new CubeDeformation(-0.01F)), PartPose.offsetAndRotation(2.0474F, 0.4329F, 0.0F, 0.0F, 0.0F, -0.2443F));

		PartDefinition tongue = jaw.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(0, 57).addBox(-3.25F, 0.0F, -2.0F, 10.0F, 0.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.7974F, -2.0671F, 0.0F, 0.0F, 0.0F, -0.0436F));

		return LayerDefinition.create(meshdefinition, 256, 256);
	}

	@Override
	public ModelPart root() {
		return this.root;
	}

	@Override
	public void setupAnim(LycanthropeVillagerEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);
		if (entity.isClientMorphingToWolf()) {
			entity.clientEnsureMorphAnimBaseline(ageInTicks);
			float elapsedTicks = ageInTicks - entity.getClientMorphAnimBaselineAgeInTicks();
			long elapsedMs = Math.round(elapsedTicks * 50.0F);
			elapsedMs = Math.min(elapsedMs, 10_150L);
			KeyframeAnimations.animate(this, WhulvkLycanSlowHumanToWolfAnimations.slow_human_to_wolf, elapsedMs, 1.0F, new Vector3f());
			return;
		}
		if (entity.isClientMorphingToHuman()) {
			this.animate(entity.wolfToIdleAnimationState, WhulvkLycanWolfToIdleAnimations.wolf_to_idle, ageInTicks);
			return;
		}
		if (entity.isWerewolfForm()) {
			if (entity.isClientWolfLegsMoving()) {
				this.animate(entity.walkWolfAnimationState, WhulvkLycanWolfWalkAnimations.walk_wolf, ageInTicks);
			} else {
				this.animate(entity.wolfIdleAnimationState, WhulvkLycanWolfIdleAnimations.idle_wolf, ageInTicks);
			}
		} else {
			this.animate(entity.humanIdleAnimationState, WhulvkLycanHumanAnimations.idle, ageInTicks);
		}
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color) {
		this.root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
	}
}