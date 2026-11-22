package com.hogwartsmagic.client.model;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.UkrainianIronbellyEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Ukrainian Ironbelly's own sculpt, on the Horntail's skeleton.
 *
 * <p>It used to be the Horntail model with a grey texture, horn crown and all, which made the
 * mod's two dragons the same animal in two coats. This keeps exactly what is worth keeping - the
 * bone names, the bone lengths and the pivots, so every pose and attack in
 * {@link HungarianHorntailModel#pose} still lands its feet and wrists on the ground and still aims
 * its fire out of its mouth - and replaces every cube hung on those bones.
 *
 * <p>What makes it a different animal, in the order you see it:
 * <ul>
 *   <li><b>Mass.</b> A deeper, broader barrel, a heavy shoulder hump and thick limbs. The renderer
 *       draws it a fifth bigger than the Horntail as well.</li>
 *   <li><b>The iron belly</b> it is named for: a plated keel under the whole torso.</li>
 *   <li><b>A blunt, broad head</b> with a heavy brow and two short back-swept horns. No crown.</li>
 *   <li><b>Low blunt plates</b> down the spine instead of the Horntail's spikes, and a tail that
 *       ends in a club rather than a sting.</li>
 *   <li><b>Torn wings.</b> The membranes are holed in the texture (the model renders cutout), so a
 *       dragon that has been kept underground for decades reads as one that cannot fly.</li>
 *   <li><b>Iron on it.</b> A collar round the base of the neck and a shackle on the left ankle,
 *       with a ring the chains are drawn to ({@link #translateToShackle}).</li>
 * </ul>
 *
 * <p>And three poses the Horntail never needed, layered over the shared animation:
 * <b>cowering</b> under Clankers (head down to the floor, turned away, wings clamped, tail curled),
 * <b>sniffing</b> when it has no target (it is near blind, so the head goes low and sweeps), and
 * <b>straining</b> at the end of its chain (leaning in, the shackled leg dragged back).
 *
 * <p>UV layout is shared with {@code build_scripts/generate_ironbelly_texture.py}, which paints
 * the hide from the same table; change one and change the other.
 */
public class UkrainianIronbellyModel extends HungarianHorntailModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "ukrainian_ironbelly"), "main");

    /** The shackle ring, in left-shank space: where the chains are drawn from. */
    public static final float RING_X = 5.0F, RING_Y = 6.5F, RING_Z = 0.0F;

    // Shared UV regions for the small parts.
    private static final int IRON_U = 122, IRON_V = 94;
    private static final int IVORY_U = 162, IVORY_V = 94;
    private static final int PLATE_U = 208, PLATE_V = 115;

    private final ModelPart rootPart;
    private final ModelPart body;
    private final ModelPart neck1;
    private final ModelPart neck2;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart[] tail;
    private final ModelPart leftHumerus;
    private final ModelPart rightHumerus;
    private final ModelPart leftForearm;
    private final ModelPart rightForearm;
    private final ModelPart leftThigh;
    private final ModelPart leftShank;

    public UkrainianIronbellyModel(ModelPart root) {
        super(root);
        this.rootPart = root;
        this.body = root.getChild("body");
        this.neck1 = this.body.getChild("neck1");
        this.neck2 = this.neck1.getChild("neck2");
        this.head = this.neck2.getChild("head");
        this.jaw = this.head.getChild("jaw");
        ModelPart t1 = this.body.getChild("tail1");
        ModelPart t2 = t1.getChild("tail2");
        ModelPart t3 = t2.getChild("tail3");
        this.tail = new ModelPart[]{t1, t2, t3, t3.getChild("tail4")};
        this.leftHumerus = this.body.getChild("left_humerus");
        this.rightHumerus = this.body.getChild("right_humerus");
        this.leftForearm = this.leftHumerus.getChild("left_forearm");
        this.rightForearm = this.rightHumerus.getChild("right_forearm");
        this.leftThigh = this.body.getChild("left_thigh");
        this.leftShank = this.leftThigh.getChild("left_shank");
    }

    // -------------------------------------------------------------------------------------------
    // Geometry
    // -------------------------------------------------------------------------------------------

    public static LayerDefinition createIronbellyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation none = CubeDeformation.NONE;

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-8.0F, -9.0F, -16.0F, 16.0F, 15.0F, 30.0F, none)
                        // the shoulder hump the wings hang from
                        .texOffs(88, 72).addBox(-9.0F, -13.0F, -13.0F, 18.0F, 6.0F, 15.0F, none)
                        // pelvis
                        .texOffs(190, 45).addBox(-7.0F, -9.0F, 5.0F, 14.0F, 11.0F, 11.0F, none)
                        // the iron belly: a plated keel standing proud of the underside
                        .texOffs(92, 0).addBox(-6.0F, 4.0F, -14.0F, 12.0F, 3.0F, 26.0F, none),
                PartPose.offset(0.0F, BODY_Y, 0.0F));

        // Low blunt plates down the spine, alternating in height. Not spikes: this is the breed
        // with the armour, not the one with the weapons.
        for (int i = 0; i < 8; i++) {
            float z = -11.0F + i * 3.4F;
            float rise = (i % 2 == 0) ? 0.0F : 1.0F;
            plate(body, "spine_" + i, 0.0F, -12.5F - rise, z, -0.18F);
        }

        // ---- Neck -> head -> jaw. Same pivots as the Horntail's, thicker. ----
        PartDefinition neck1 = body.addOrReplaceChild("neck1", CubeListBuilder.create()
                        .texOffs(168, 0).addBox(-5.0F, -5.0F, -14.0F, 10.0F, 10.0F, 14.0F, none)
                        // the collar, riveted round the base of the neck
                        .texOffs(IRON_U, IRON_V).addBox(-6.0F, -6.0F, -11.0F, 12.0F, 12.0F, 3.0F, none),
                PartPose.offsetAndRotation(0.0F, -8.0F, -14.0F, STAND_NECK1_X, 0.0F, 0.0F));
        for (int i = 0; i < 2; i++) {
            plate(neck1, "neck1_plate_" + i, 0.0F, -5.0F, -5.0F - i * 5.0F, -0.2F);
        }

        PartDefinition neck2 = neck1.addOrReplaceChild("neck2", CubeListBuilder.create()
                        .texOffs(0, 72).addBox(-4.0F, -4.0F, -14.0F, 8.0F, 8.0F, 14.0F, none),
                PartPose.offsetAndRotation(0.0F, 0.0F, -13.0F, STAND_NECK2_X, 0.0F, 0.0F));
        for (int i = 0; i < 2; i++) {
            plate(neck2, "neck2_plate_" + i, 0.0F, -4.0F, -4.0F - i * 5.0F, -0.2F);
        }

        PartDefinition head = neck2.addOrReplaceChild("head", CubeListBuilder.create()
                        // broad flat skull
                        .texOffs(0, 94).addBox(-5.5F, -4.0F, -12.0F, 11.0F, 8.0F, 13.0F, none)
                        // blunt muzzle: the fire leaves from SNOUT_Y/SNOUT_Z, inside this
                        .texOffs(40, 115).addBox(-4.0F, -1.0F, -20.0F, 8.0F, 5.0F, 8.0F, none)
                        // one heavy brow across both eyes
                        .texOffs(172, 115).addBox(-6.0F, -5.5F, -12.0F, 12.0F, 3.0F, 6.0F, none),
                PartPose.offsetAndRotation(0.0F, 0.0F, -13.0F, STAND_HEAD_X, 0.0F, 0.0F));

        // Small, milky, half-blind eyes under the brow.
        head.addOrReplaceChild("eye_left", CubeListBuilder.create()
                        .texOffs(240, 45).addBox(-0.5F, -1.0F, -1.5F, 1.0F, 2.0F, 3.0F, none),
                PartPose.offset(5.6F, -2.2F, -9.0F));
        head.addOrReplaceChild("eye_right", CubeListBuilder.create()
                        .texOffs(240, 45).addBox(-0.5F, -1.0F, -1.5F, 1.0F, 2.0F, 3.0F, none),
                PartPose.offset(-5.6F, -2.2F, -9.0F));

        // Two short horns swept back off the brow, each in two pieces so it curves.
        for (int side = -1; side <= 1; side += 2) {
            String n = side > 0 ? "l" : "r";
            PartDefinition horn = head.addOrReplaceChild("horn_" + n, CubeListBuilder.create()
                            .texOffs(IVORY_U, IVORY_V).addBox(-1.5F, -8.0F, -1.5F, 3.0F, 8.0F, 3.0F, none),
                    PartPose.offsetAndRotation(3.5F * side, -4.5F, -3.0F, -1.15F, 0.0F, 0.32F * side));
            horn.addOrReplaceChild("horn_tip_" + n, CubeListBuilder.create()
                            .texOffs(IVORY_U, IVORY_V).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F, none),
                    PartPose.offsetAndRotation(0.0F, -7.5F, 0.0F, -0.45F, 0.0F, 0.0F));
            // A short frill of spines at the angle of the jaw.
            for (int i = 0; i < 3; i++) {
                spike(head, "frill_" + n + i, 5.2F * side, 1.0F - i * 1.5F, -2.0F - i * 1.2F,
                        0.3F, 1.25F * side, 3.5F - i * 0.6F);
            }
            // Fangs hanging over the lower jaw.
            head.addOrReplaceChild("fang_" + n, CubeListBuilder.create()
                            .texOffs(IVORY_U, IVORY_V).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, none),
                    PartPose.offset(2.8F * side, 3.5F, -18.5F));
        }

        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                        .texOffs(78, 94).addBox(-4.0F, 0.0F, -14.0F, 8.0F, 3.0F, 14.0F, none),
                PartPose.offset(0.0F, 3.5F, -6.0F));

        // ---- Tail: thick, plated, ending in a club. ----
        PartDefinition tail1 = body.addOrReplaceChild("tail1", CubeListBuilder.create()
                        .texOffs(100, 45).addBox(-5.0F, -5.0F, 0.0F, 10.0F, 10.0F, 14.0F, none),
                PartPose.offset(0.0F, -3.0F, 14.0F));
        PartDefinition tail2 = tail1.addOrReplaceChild("tail2", CubeListBuilder.create()
                        .texOffs(44, 72).addBox(-4.0F, -4.0F, 0.0F, 8.0F, 8.0F, 14.0F, none),
                PartPose.offset(0.0F, 0.0F, 13.0F));
        PartDefinition tail3 = tail2.addOrReplaceChild("tail3", CubeListBuilder.create()
                        .texOffs(216, 0).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 14.0F, none),
                PartPose.offset(0.0F, 0.0F, 13.0F));
        PartDefinition tail4 = tail3.addOrReplaceChild("tail4", CubeListBuilder.create()
                        .texOffs(216, 72).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 14.0F, none)
                        .texOffs(72, 115).addBox(-4.0F, -3.0F, 9.0F, 8.0F, 6.0F, 7.0F, none),
                PartPose.offset(0.0F, 0.0F, 13.0F));
        for (int i = 0; i < 3; i++) {
            plate(tail1, "tail1_plate_" + i, 0.0F, -5.0F, 2.5F + i * 4.0F, -0.15F);
            plate(tail2, "tail2_plate_" + i, 0.0F, -4.0F, 2.5F + i * 4.0F, -0.15F);
        }
        // Blunt knobs on the club, not stings.
        spike(tail4, "club_l", 4.0F, 0.0F, 12.5F, 0.0F, 1.4F, 3.0F);
        spike(tail4, "club_r", -4.0F, 0.0F, 12.5F, 0.0F, -1.4F, 3.0F);
        spike(tail4, "club_up", 0.0F, -3.0F, 12.5F, -0.3F, 0.0F, 3.0F);

        buildWing(body, true);
        buildWing(body, false);
        buildLeg(body, true);
        buildLeg(body, false);

        return LayerDefinition.create(mesh, 256, 256);
    }

    private static void plate(PartDefinition parent, String name, float x, float y, float z, float xRot) {
        parent.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(PLATE_U, PLATE_V).addBox(-2.0F, -3.0F, -2.5F, 4.0F, 3.0F, 5.0F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(x, y, z, xRot, 0.0F, 0.0F));
    }

    private static void spike(PartDefinition parent, String name, float x, float y, float z,
                              float xRot, float zRot, float length) {
        parent.addOrReplaceChild(name, CubeListBuilder.create()
                        .texOffs(IVORY_U, IVORY_V).addBox(-1.0F, -length, -1.0F, 2.0F, length, 2.0F, CubeDeformation.NONE),
                PartPose.offsetAndRotation(x, y, z, xRot, 0.0F, zRot));
    }

    /** Same three bones and lengths as the Horntail's; thicker, and the sails are the torn ones. */
    private static void buildWing(PartDefinition body, boolean left) {
        float s = left ? 1.0F : -1.0F;
        String p = left ? "left_" : "right_";

        CubeListBuilder humerus = CubeListBuilder.create();
        mirrored(humerus, left, 102, 115, 0.0F, -3.0F, -3.0F, 13.0F, 6.0F, 6.0F);
        mirrored(humerus, left, 186, 94, 0.0F, -0.5F, 3.0F, 13.0F, 1.0F, 14.0F);
        PartDefinition hum = body.addOrReplaceChild(p + "humerus", humerus,
                PartPose.offsetAndRotation(7.0F * s, -7.0F, -6.0F, 0.0F, 0.0F, STAND_HUMERUS_Z * s));

        CubeListBuilder forearm = CubeListBuilder.create();
        mirrored(forearm, left, 140, 115, 0.0F, -2.5F, -2.5F, 11.0F, 5.0F, 5.0F);
        mirrored(forearm, left, 154, 72, 0.0F, -0.5F, 2.0F, 11.0F, 1.0F, 20.0F);
        PartDefinition fore = hum.addOrReplaceChild(p + "forearm", forearm,
                PartPose.offsetAndRotation(13.0F * s, 0.0F, 0.0F, 0.0F, 0.0F, STAND_FOREARM_Z * s));
        spike(fore, p + "claw", 11.0F * s, 0.0F, 0.0F, 2.05F, 0.0F, 5.0F);

        CubeListBuilder finger = CubeListBuilder.create();
        mirrored(finger, left, 0, 129, 0.0F, -1.5F, -1.5F, 24.0F, 3.0F, 3.0F);
        mirrored(finger, left, 0, 45, 0.0F, -0.5F, 1.0F, 24.0F, 1.0F, 26.0F);
        fore.addOrReplaceChild(p + "finger", finger,
                PartPose.offsetAndRotation(11.0F * s, 0.0F, 0.0F,
                        0.0F, STAND_FINGER_Y * s, STAND_FINGER_Z * s));
    }

    private static void mirrored(CubeListBuilder b, boolean left, int u, int v,
                                 float x, float y, float z, float dx, float dy, float dz) {
        if (left) {
            b.texOffs(u, v).addBox(x, y, z, dx, dy, dz, CubeDeformation.NONE);
        } else {
            b.texOffs(u, v).mirror().addBox(-x - dx, y, z, dx, dy, dz, CubeDeformation.NONE).mirror(false);
        }
    }

    /** Same pivots and lengths, so the foot still lands on the ground plane; thicker everywhere. */
    private static void buildLeg(PartDefinition body, boolean left) {
        float s = left ? 1.0F : -1.0F;
        String p = left ? "left_" : "right_";

        CubeListBuilder thigh = CubeListBuilder.create();
        mirrored(thigh, left, 148, 45, -5.0F, -2.0F, -5.5F, 10.0F, 12.0F, 11.0F);
        PartDefinition th = body.addOrReplaceChild(p + "thigh", thigh,
                PartPose.offsetAndRotation(7.0F * s, -3.0F, 9.0F, STAND_THIGH_X, 0.0F, 0.0F));

        CubeListBuilder shank = CubeListBuilder.create();
        mirrored(shank, left, 48, 94, -3.5F, -1.0F, -4.0F, 7.0F, 10.0F, 8.0F);
        if (left) {
            // The shackle, round the ankle, and the ring the chains run to.
            shank.texOffs(IRON_U, IRON_V).addBox(-4.5F, 5.0F, -5.0F, 9.0F, 3.0F, 10.0F, CubeDeformation.NONE);
            shank.texOffs(IRON_U, IRON_V).addBox(4.5F, 4.0F, -2.5F, 1.0F, 5.0F, 5.0F, CubeDeformation.NONE);
        }
        PartDefinition sh = th.addOrReplaceChild(p + "shank", shank,
                PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, STAND_SHANK_X, 0.0F, 0.0F));

        CubeListBuilder foot = CubeListBuilder.create();
        mirrored(foot, left, 0, 115, -4.5F, 0.0F, -8.0F, 9.0F, 3.0F, 11.0F);
        PartDefinition ft = sh.addOrReplaceChild(p + "foot", foot,
                PartPose.offsetAndRotation(0.0F, 9.0F, 0.0F, STAND_FOOT_X, 0.0F, 0.0F));
        spike(ft, p + "claw_a", -3.0F, 3.0F, -8.0F, 1.9F, 0.25F, 4.5F);
        spike(ft, p + "claw_b", 0.0F, 3.0F, -8.5F, 1.9F, 0.0F, 5.0F);
        spike(ft, p + "claw_c", 3.0F, 3.0F, -8.0F, 1.9F, -0.25F, 4.5F);
    }

    // -------------------------------------------------------------------------------------------
    // Animation: the shared dragon, then what only this one does
    // -------------------------------------------------------------------------------------------

    @Override
    public void setupAnim(HungarianHorntailEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (!(entity instanceof UkrainianIronbellyEntity iron)) {
            return;
        }
        float partial = ageInTicks - entity.tickCount;
        float cower = iron.cowerAmount(partial);
        float strain = iron.strainAmount(partial);
        float sniff = iron.sniffAmount(partial);
        boolean idle = entity.getAttackState() == HungarianHorntailEntity.AttackState.NONE;

        if (idle && sniff > 0.0F) {
            applySniff(sniff, ageInTicks);
        }
        if (idle && strain > 0.0F) {
            applyStrain(strain, ageInTicks);
        }
        if (cower > 0.0F) {
            applyCower(cower, ageInTicks);
        }
    }

    /**
     * Nearly blind, so when it has nothing to fight it hunts with its nose: head low and sweeping
     * slowly side to side, stopping, the nostrils (the jaw) twitching.
     */
    private void applySniff(float s, float age) {
        float sweep = Mth.sin(age * 0.045F);
        float pause = Mth.clamp(Math.abs(Mth.sin(age * 0.0225F)) * 1.6F, 0.0F, 1.0F);
        this.neck1.xRot += 0.28F * s;
        this.neck2.xRot += 0.10F * s;
        this.head.xRot += 0.20F * s;
        this.neck1.yRot += sweep * 0.32F * s * pause;
        this.neck2.yRot += Mth.sin(age * 0.045F - 0.6F) * 0.22F * s * pause;
        this.head.yRot += Mth.sin(age * 0.045F - 1.2F) * 0.15F * s * pause;
        float twitch = Mth.sin(age * 0.9F);
        twitch = twitch * twitch * twitch * twitch;
        this.jaw.xRot += twitch * 0.06F * s;
    }

    /** At the end of its chain: leaning into it, neck out, the shackled leg dragged back. */
    private void applyStrain(float t, float age) {
        float judder = Mth.sin(age * 1.7F) * 0.02F * t;
        this.body.xRot += 0.10F * t + judder;
        this.body.y += 1.2F * t;
        this.neck1.xRot += 0.22F * t;
        this.neck2.xRot -= 0.12F * t;
        this.leftThigh.xRot += 0.50F * t;
        this.leftShank.xRot -= 0.25F * t;
        this.jaw.xRot += 0.25F * t;
    }

    /**
     * Clankers: it has been taught to expect pain from that sound. Belly to the floor, head pressed
     * down and turned away, wings clamped in, tail wrapped round.
     */
    private void applyCower(float c, float age) {
        float flinch = Mth.sin(age * 2.1F) * 0.025F * c;
        this.body.y += 3.5F * c;
        this.body.xRot += 0.10F * c + flinch;
        this.neck1.xRot += 0.55F * c;
        this.neck2.xRot += 0.30F * c;
        this.head.xRot += 0.35F * c;
        this.neck1.yRot += 0.55F * c;
        this.neck2.yRot += 0.30F * c;
        this.head.yRot += 0.20F * c;
        this.jaw.xRot = Mth.lerp(c, this.jaw.xRot, 0.0F);
        this.leftHumerus.zRot += 0.22F * c;
        this.rightHumerus.zRot -= 0.22F * c;
        this.leftForearm.zRot += 0.18F * c;
        this.rightForearm.zRot -= 0.18F * c;
        float[] curl = {0.30F, 0.40F, 0.48F, 0.55F};
        for (int i = 0; i < this.tail.length; i++) {
            this.tail[i].yRot += curl[i] * c;
        }
    }

    /**
     * Moves {@code poseStack} from the model root into the posed left shank's space, so
     * ({@link #RING_X}, {@link #RING_Y}, {@link #RING_Z}) px lands on the shackle ring as drawn.
     */
    public void translateToShackle(PoseStack poseStack) {
        this.rootPart.translateAndRotate(poseStack);
        this.body.translateAndRotate(poseStack);
        this.leftThigh.translateAndRotate(poseStack);
        this.leftShank.translateAndRotate(poseStack);
    }
}
