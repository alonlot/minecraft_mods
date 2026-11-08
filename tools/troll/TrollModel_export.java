// Made with Blockbench 5.1.6
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class mountain_troll_v3<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "mountain_troll_v3"), "main");
	private final ModelPart root;
	private final ModelPart hull;
	private final ModelPart body;
	private final ModelPart belly;
	private final ModelPart loin_front;
	private final ModelPart loin_back;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart ear_r;
	private final ModelPart ear_l;
	private final ModelPart arm_r;
	private final ModelPart forearm_r;
	private final ModelPart hand_r;
	private final ModelPart fingers_r;
	private final ModelPart club;
	private final ModelPart arm_l;
	private final ModelPart forearm_l;
	private final ModelPart hand_l;
	private final ModelPart leg_r;
	private final ModelPart shin_r;
	private final ModelPart foot_r;
	private final ModelPart leg_l;
	private final ModelPart shin_l;
	private final ModelPart foot_l;

	public mountain_troll_v3(ModelPart root) {
		this.root = root.getChild("root");
		this.hull = this.root.getChild("hull");
		this.body = this.hull.getChild("body");
		this.belly = this.body.getChild("belly");
		this.loin_front = this.body.getChild("loin_front");
		this.loin_back = this.body.getChild("loin_back");
		this.head = this.body.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.ear_r = this.head.getChild("ear_r");
		this.ear_l = this.head.getChild("ear_l");
		this.arm_r = this.body.getChild("arm_r");
		this.forearm_r = this.arm_r.getChild("forearm_r");
		this.hand_r = this.forearm_r.getChild("hand_r");
		this.fingers_r = this.hand_r.getChild("fingers_r");
		this.club = this.hand_r.getChild("club");
		this.arm_l = this.body.getChild("arm_l");
		this.forearm_l = this.arm_l.getChild("forearm_l");
		this.hand_l = this.forearm_l.getChild("hand_l");
		this.leg_r = this.hull.getChild("leg_r");
		this.shin_r = this.leg_r.getChild("shin_r");
		this.foot_r = this.shin_r.getChild("foot_r");
		this.leg_l = this.hull.getChild("leg_l");
		this.shin_l = this.leg_l.getChild("shin_l");
		this.foot_l = this.shin_l.getChild("foot_l");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition body = hull.addOrReplaceChild("body", CubeListBuilder.create().texOffs(160, 0).addBox(-11.0F, -2.0F, -9.0F, 22.0F, 7.0F, 17.0F, new CubeDeformation(0.0F))
		.texOffs(81, 0).addBox(-12.0F, -13.0F, -6.0F, 24.0F, 12.0F, 15.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-11.0F, -23.0F, -9.0F, 22.0F, 11.0F, 18.0F, new CubeDeformation(0.0F))
		.texOffs(94, 30).addBox(-15.0F, -28.0F, -7.0F, 30.0F, 7.0F, 15.0F, new CubeDeformation(0.0F))
		.texOffs(69, 116).addBox(-9.0F, -31.0F, -5.0F, 18.0F, 4.0F, 12.0F, new CubeDeformation(0.0F))
		.texOffs(185, 133).addBox(-7.0F, -33.0F, 0.0F, 14.0F, 4.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(164, 97).addBox(-6.0F, -31.0F, -11.0F, 12.0F, 8.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(31, 163).addBox(1.0F, -22.0F, -11.0F, 9.0F, 7.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(56, 163).addBox(-10.0F, -22.0F, -11.0F, 9.0F, 7.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(0, 54).addBox(-12.0F, 1.0F, -10.0F, 24.0F, 2.0F, 19.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -18.0F, 0.0F, 0.1047F, 0.0F, 0.0F));

		PartDefinition belly = body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(50, 133).addBox(-12.0F, -9.0F, -5.0F, 24.0F, 6.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(35, 76).addBox(-14.0F, -4.0F, -8.0F, 28.0F, 8.0F, 11.0F, new CubeDeformation(0.0F))
		.texOffs(0, 116).addBox(-12.0F, 3.0F, -7.0F, 24.0F, 6.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(136, 133).addBox(-8.0F, 8.0F, -5.0F, 16.0F, 4.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(128, 150).addBox(-9.0F, -3.0F, -10.0F, 18.0F, 9.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -6.0F, -8.0F));

		PartDefinition loin_front = body.addOrReplaceChild("loin_front", CubeListBuilder.create().texOffs(101, 150).addBox(-6.0F, 0.0F, -0.5F, 12.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 3.0F, -7.5F));

		PartDefinition loin_back = body.addOrReplaceChild("loin_back", CubeListBuilder.create().texOffs(0, 150).addBox(-8.0F, 0.0F, -0.5F, 16.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.0F, 9.5F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(169, 54).addBox(-6.0F, -9.0F, -11.0F, 12.0F, 9.0F, 11.0F, new CubeDeformation(0.0F))
		.texOffs(96, 163).addBox(-4.0F, -11.0F, -9.0F, 8.0F, 2.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(127, 163).addBox(-5.0F, -8.0F, 0.0F, 10.0F, 7.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(214, 175).addBox(-7.0F, -7.0F, -13.0F, 14.0F, 2.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(22, 175).addBox(-2.5F, -5.0F, -14.0F, 5.0F, 4.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(199, 163).addBox(5.0F, -3.0F, -9.0F, 2.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(214, 163).addBox(-7.0F, -3.0F, -9.0F, 2.0F, 3.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(69, 175).addBox(-5.0F, -1.0F, -13.0F, 10.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -29.0F, -8.0F, -0.1047F, 0.0F, 0.0F));

		PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(130, 116).addBox(-6.0F, 0.0F, -10.0F, 12.0F, 5.0F, 11.0F, new CubeDeformation(0.0F))
		.texOffs(98, 175).addBox(-3.5F, 5.0F, -10.0F, 7.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(196, 175).addBox(3.0F, -3.0F, -10.5F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(205, 175).addBox(-5.0F, -3.0F, -10.5F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(66, 184).addBox(0.5F, -1.0F, -10.2F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(71, 184).addBox(-1.5F, -1.0F, -10.2F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -4.0F));

		PartDefinition ear_r = head.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(0, 184).addBox(-5.0F, -2.0F, -0.5F, 5.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(52, 184).addBox(-7.0F, 0.0F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-6.0F, -4.0F, -4.0F, 0.0F, -0.4189F, -0.2443F));

		PartDefinition ear_l = head.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(13, 184).addBox(0.0F, -2.0F, -0.5F, 5.0F, 4.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(59, 184).addBox(5.0F, 0.0F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(6.0F, -4.0F, -4.0F, 0.0F, 0.4189F, 0.2443F));

		PartDefinition arm_r = body.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(0, 97).addBox(-5.0F, -4.0F, -6.0F, 10.0F, 7.0F, 11.0F, new CubeDeformation(0.0F))
		.texOffs(216, 54).addBox(-4.0F, 2.0F, -5.0F, 8.0F, 11.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-17.0F, -24.0F, 0.0F, 0.0349F, 0.0F, 0.0873F));

		PartDefinition forearm_r = arm_r.addOrReplaceChild("forearm_r", CubeListBuilder.create().texOffs(87, 54).addBox(-5.0F, -1.0F, -5.0F, 10.0F, 11.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(35, 150).addBox(-4.0F, 9.0F, -4.0F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, -0.4189F, 0.0F, 0.0F));

		PartDefinition hand_r = forearm_r.addOrReplaceChild("hand_r", CubeListBuilder.create().texOffs(17, 133).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 7.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 11.0F, 0.0F, 0.2443F, 0.0F, 0.0F));

		PartDefinition fingers_r = hand_r.addOrReplaceChild("fingers_r", CubeListBuilder.create().texOffs(169, 150).addBox(-3.5F, -4.5F, -3.5F, 7.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(200, 150).addBox(-3.5F, -1.5F, -3.5F, 7.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 163).addBox(-3.5F, 1.5F, -3.5F, 7.0F, 3.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(81, 163).addBox(3.0F, -3.5F, -1.5F, 2.0F, 5.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.5F, -1.0F, -0.9774F, 0.0F, 0.2793F));

		PartDefinition club = hand_r.addOrReplaceChild("club", CubeListBuilder.create().texOffs(123, 175).addBox(-2.5F, -6.5F, -2.5F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(0, 133).addBox(-2.0F, -5.5F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(115, 133).addBox(-2.5F, 6.5F, -2.5F, 5.0F, 8.0F, 5.0F, new CubeDeformation(0.0F))
		.texOffs(212, 116).addBox(-4.0F, 14.5F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(174, 163).addBox(-3.0F, 22.5F, -3.0F, 6.0F, 2.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(229, 163).addBox(4.0F, 16.5F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(242, 163).addBox(-6.0F, 17.5F, -2.0F, 2.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(26, 184).addBox(-2.0F, 16.5F, -6.0F, 4.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(39, 184).addBox(-2.0F, 9.5F, 2.5F, 4.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.5F, -1.0F, -0.9774F, 0.0F, 0.2793F));

		PartDefinition arm_l = body.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(43, 97).addBox(-5.0F, -4.0F, -6.0F, 10.0F, 7.0F, 11.0F, new CubeDeformation(0.0F))
		.texOffs(0, 76).addBox(-4.0F, 2.0F, -5.0F, 8.0F, 11.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(17.0F, -24.0F, 0.0F, -0.0698F, 0.0F, -0.0873F));

		PartDefinition forearm_l = arm_l.addOrReplaceChild("forearm_l", CubeListBuilder.create().texOffs(128, 54).addBox(-5.0F, -1.0F, -5.0F, 10.0F, 11.0F, 10.0F, new CubeDeformation(0.0F))
		.texOffs(68, 150).addBox(-4.0F, 9.0F, -4.0F, 8.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 12.0F, 0.0F, -0.1745F, 0.0F, 0.0F));

		PartDefinition hand_l = forearm_l.addOrReplaceChild("hand_l", CubeListBuilder.create().texOffs(177, 116).addBox(-4.0F, 0.0F, -4.0F, 8.0F, 7.0F, 9.0F, new CubeDeformation(0.0F))
		.texOffs(0, 175).addBox(2.0F, 7.0F, -4.0F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(152, 163).addBox(-1.0F, 7.0F, -4.0F, 2.0F, 6.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(11, 175).addBox(-4.0F, 7.0F, -4.0F, 2.0F, 5.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(163, 163).addBox(-6.0F, 3.0F, 0.0F, 2.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.0F, 0.0F));

		PartDefinition leg_r = hull.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(0, 30).addBox(-6.0F, -2.0F, -6.0F, 11.0F, 11.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(-8.0F, -18.0F, 0.0F));

		PartDefinition shin_r = leg_r.addOrReplaceChild("shin_r", CubeListBuilder.create().texOffs(86, 97).addBox(-4.5F, -1.0F, -5.0F, 9.0F, 8.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 8.0F, 0.0F));

		PartDefinition foot_r = shin_r.addOrReplaceChild("foot_r", CubeListBuilder.create().texOffs(114, 76).addBox(-5.5F, 0.0F, -10.0F, 11.0F, 4.0F, 15.0F, new CubeDeformation(0.0F))
		.texOffs(144, 175).addBox(2.5F, 1.0F, -13.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(39, 175).addBox(-1.5F, 1.0F, -14.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(157, 175).addBox(-5.5F, 1.0F, -13.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.0F, 0.0F));

		PartDefinition leg_l = hull.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(47, 30).addBox(-5.0F, -2.0F, -6.0F, 11.0F, 11.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(8.0F, -18.0F, 0.0F));

		PartDefinition shin_l = leg_l.addOrReplaceChild("shin_l", CubeListBuilder.create().texOffs(125, 97).addBox(-4.5F, -1.0F, -5.0F, 9.0F, 8.0F, 10.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 8.0F, 0.0F));

		PartDefinition foot_l = shin_l.addOrReplaceChild("foot_l", CubeListBuilder.create().texOffs(167, 76).addBox(-5.5F, 0.0F, -10.0F, 11.0F, 4.0F, 15.0F, new CubeDeformation(0.0F))
		.texOffs(170, 175).addBox(2.5F, 1.0F, -13.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
		.texOffs(54, 175).addBox(-1.5F, 1.0F, -14.0F, 3.0F, 3.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(183, 175).addBox(-5.5F, 1.0F, -13.0F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 6.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 256, 256);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		root.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}