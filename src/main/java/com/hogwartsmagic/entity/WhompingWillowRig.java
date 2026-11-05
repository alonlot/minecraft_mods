package com.hogwartsmagic.entity;

import com.hogwartsmagic.entity.WhompingWillowEntity.AttackState;

/**
 * The Whomping Willow's skeleton and every pose it can take, in plain Java the server can load.
 *
 * <p><b>Why this class exists.</b> The willow used to describe its branches twice: once as model
 * geometry, once as a handful of constants the server built its damage arcs from. The two were
 * mirrored left-to-right, so two of the four branches swung on the far side of the trunk from the
 * arc that actually hurt you. Here there is one description. The bone table between the GENERATED
 * markers is written by {@code build_scripts/generate_whomping_willow_model.py} from the same tree
 * it builds the mesh from; {@link #pose} turns the entity's synched attack state into a rotation
 * for every bone; and {@link #limbPoints} runs forward kinematics with exactly the
 * {@code ModelPart} convention (translate by the pivot, then {@code Rz * Ry * Rx}) followed by
 * exactly {@code LivingEntityRenderer}'s transform. The server hit-tests against those points. The
 * client draws those rotations. There is nothing left to disagree.
 *
 * <p><b>Determinism contract.</b> With {@code Input.idle == false} the output depends only on the
 * synched attack parameters and the state tick, so client and server compute the same branch. The
 * client adds idle motion (wind, writhing) on top, but every idle term is multiplied by
 * {@code 1 - envelope} of the attack, and every damage window sits where that envelope is exactly 1.
 *
 * <p><b>Poses are written as intent.</b> A striking limb is not keyframed; it is told where its tip
 * should be (horizontal distance from the trunk axis and height above the roots, in blocks) and a
 * two-parameter Newton solve finds the bend that puts it there. That is what makes a slam actually
 * reach the ground on the tick it is supposed to, and a sweep actually pass at knee height.
 *
 * <p>No Minecraft classes beyond the entity's own enum: this must load in a plain JVM so the
 * parity harness can check it against {@code ModelPart} directly.
 */
public final class WhompingWillowRig {

    private WhompingWillowRig() {
    }

    // <<< GENERATED RIG
    public static final int BONES = 213;
    public static final String[] NAME = {"root", "willow", "flare", "root1_s1", "root1_s2", "root2_s1", "root2_s2", "root3_s1", "root3_s2", "root4_s1", "root4_s2", "root5_s1", "root5_s2", "root6_s1", "root6_s2", "root7_s1", "root7_s2", "trunk1", "trunk2", "trunk3", "trunk4", "trunk5", "trunk6", "bough1_s1", "bough1_s2", "bough1_s3", "bough1_x31a", "bough1_x31b", "bough1_x31b_cur", "bough1_x31a_cur", "bough1_x32a", "bough1_x32b", "bough1_x32b_cur", "bough1_x32a_cur", "bough1_x21a", "bough1_x21b", "bough1_x21b_cur", "bough1_x21a_cur", "bough1_x22a", "bough1_x22b", "bough1_x22b_cur", "bough1_x22a_cur", "bough1_x12a", "bough1_x12b", "bough1_x12b_cur", "bough1_x12a_cur", "bough2_s1", "bough2_s2", "bough2_s3", "bough2_x31a", "bough2_x31b", "bough2_x31b_cur", "bough2_x31a_cur", "bough2_x32a", "bough2_x32b", "bough2_x32b_cur", "bough2_x32a_cur", "bough2_x21a", "bough2_x21b", "bough2_x21b_cur", "bough2_x21a_cur", "bough2_x22a", "bough2_x22b", "bough2_x22b_cur", "bough2_x22a_cur", "bough2_x12a", "bough2_x12b", "bough2_x12b_cur", "bough2_x12a_cur", "bough3_s1", "bough3_s2", "bough3_s3", "bough3_x31a", "bough3_x31b", "bough3_x31b_cur", "bough3_x31a_cur", "bough3_x32a", "bough3_x32b", "bough3_x32b_cur", "bough3_x32a_cur", "bough3_x21a", "bough3_x21b", "bough3_x21b_cur", "bough3_x21a_cur", "bough3_x22a", "bough3_x22b", "bough3_x22b_cur", "bough3_x22a_cur", "bough3_x12a", "bough3_x12b", "bough3_x12b_cur", "bough3_x12a_cur", "bough4_s1", "bough4_s2", "bough4_s3", "bough4_x31a", "bough4_x31b", "bough4_x31b_cur", "bough4_x31a_cur", "bough4_x32a", "bough4_x32b", "bough4_x32b_cur", "bough4_x32a_cur", "bough4_x21a", "bough4_x21b", "bough4_x21b_cur", "bough4_x21a_cur", "bough4_x22a", "bough4_x22b", "bough4_x22b_cur", "bough4_x22a_cur", "bough4_x12a", "bough4_x12b", "bough4_x12b_cur", "bough4_x12a_cur", "leader_s1", "leader_s2", "leader_s3", "leader_x31a", "leader_x31b", "leader_x31b_cur", "leader_x31a_cur", "leader_x32a", "leader_x32b", "leader_x32b_cur", "leader_x32a_cur", "leader_x21a", "leader_x21b", "leader_x21b_cur", "leader_x21a_cur", "leader_x22a", "leader_x22b", "leader_x22b_cur", "leader_x22a_cur", "leader_x11a", "leader_x11b", "leader_x11b_cur", "leader_x11a_cur", "leader_x12a", "leader_x12b", "leader_x12b_cur", "leader_x12a_cur", "limb3_s1", "limb3_s2", "limb3_s3", "limb3_s4", "limb3_s5", "limb3_s6", "limb3_cur5", "limb3_f4", "limb3_f4_cur", "limb3_cur4", "limb3_f3", "limb3_f3_cur", "limb3_cur3", "limb3_f2", "limb3_f2_cur", "limb3_cur2", "limb3_f1", "maw", "maw_jaw", "limb1_s1", "limb1_s2", "limb1_s3", "limb1_s4", "limb1_s5", "limb1_s6", "limb1_cur5", "limb1_f4", "limb1_f4_cur", "limb1_cur4", "limb1_f3", "limb1_f3_cur", "limb1_cur3", "limb1_f2", "limb1_f2_cur", "limb1_cur2", "limb1_f1", "limb4_s1", "limb4_s2", "limb4_s3", "limb4_s4", "limb4_s5", "limb4_s6", "limb4_cur5", "limb4_f4", "limb4_f4_cur", "limb4_cur4", "limb4_f3", "limb4_f3_cur", "limb4_cur3", "limb4_f2", "limb4_f2_cur", "limb4_cur2", "limb4_f1", "limb2_s1", "limb2_s2", "limb2_s3", "limb2_s4", "limb2_s5", "limb2_s6", "limb2_cur5", "limb2_f4", "limb2_f4_cur", "limb2_cur4", "limb2_f3", "limb2_f3_cur", "limb2_cur3", "limb2_f2", "limb2_f2_cur", "limb2_cur2", "limb2_f1", "knot"};
    public static final int[] PARENT = {-1, 0, 1, 1, 3, 1, 5, 1, 7, 1, 9, 1, 11, 1, 13, 1,
            15, 1, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 26, 25, 30,
            31, 30, 24, 34, 35, 34, 24, 38, 39, 38, 23, 42, 43, 42, 22, 46,
            47, 48, 49, 50, 49, 48, 53, 54, 53, 47, 57, 58, 57, 47, 61, 62,
            61, 46, 65, 66, 65, 22, 69, 70, 71, 72, 73, 72, 71, 76, 77, 76,
            70, 80, 81, 80, 70, 84, 85, 84, 69, 88, 89, 88, 22, 92, 93, 94,
            95, 96, 95, 94, 99, 100, 99, 93, 103, 104, 103, 93, 107, 108, 107, 92,
            111, 112, 111, 22, 115, 116, 117, 118, 119, 118, 117, 122, 123, 122, 116, 126,
            127, 126, 116, 130, 131, 130, 115, 134, 135, 134, 115, 138, 139, 138, 20, 142,
            143, 144, 145, 146, 147, 146, 149, 146, 145, 152, 145, 144, 155, 144, 143, 19,
            159, 19, 161, 162, 163, 164, 165, 166, 165, 168, 165, 164, 171, 164, 163, 174,
            163, 162, 19, 178, 179, 180, 181, 182, 183, 182, 185, 182, 181, 188, 181, 180,
            191, 180, 179, 18, 195, 196, 197, 198, 199, 200, 199, 202, 199, 198, 205, 198,
            197, 208, 197, 196, 17};
    /** Local pivot offsets, model px, three per bone. */
    static final float[] OFF = {
            0.0F, 24.0F, 0.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F, -9.5317F, -5.0F, -19.8279F,
            0.0F, 0.0F, 20.0F, -21.3401F, -5.0F, -5.3479F,
            0.0F, 0.0F, 20.0F, -19.9884F, -5.0F, 9.1903F,
            0.0F, 0.0F, 20.0F, -1.5615F, -5.0F, 21.9445F,
            0.0F, 0.0F, 20.0F, 16.1677F, -5.0F, 14.92F,
            0.0F, 0.0F, 20.0F, 21.839F, -5.0F, -2.6564F,
            0.0F, 0.0F, 20.0F, 7.4827F, -5.0F, -20.6884F,
            0.0F, 0.0F, 20.0F, 0.0F, 0.0F, 0.0F,
            2.0F, -20.0F, -1.0F, 2.0977F, -18.0F, -0.7744F,
            0.5394F, -17.0F, 2.17F, -1.5941F, -16.0F, 1.568F,
            -2.6614F, -15.0F, 0.9576F, -3.122F, -12.0F, -6.2652F,
            0.0F, 0.0F, 34.0F, 0.0F, 0.0F, 30.0F,
            0.0F, 0.0F, 10.8F, 0.0F, 0.0F, 16.0F,
            0.0F, 2.0F, 13.0F, 0.0F, 3.0F, 13.6F,
            0.0F, 0.0F, 18.0F, 0.0F, 0.0F, 16.0F,
            0.0F, 2.0F, 13.0F, 0.0F, 3.0F, 13.6F,
            0.0F, 0.0F, 13.5F, 0.0F, 0.0F, 22.0F,
            0.0F, 2.0F, 18.0F, 0.0F, 3.0F, 18.7F,
            0.0F, 0.0F, 22.5F, 0.0F, 0.0F, 22.0F,
            0.0F, 2.0F, 18.0F, 0.0F, 3.0F, 18.7F,
            0.0F, 0.0F, 25.5F, 0.0F, 0.0F, 22.0F,
            0.0F, 2.0F, 18.0F, 0.0F, 3.0F, 18.7F,
            -6.3188F, -12.0F, 3.0122F, 0.0F, 0.0F, 34.0F,
            0.0F, 0.0F, 30.0F, 0.0F, 0.0F, 10.8F,
            0.0F, 0.0F, 16.0F, 0.0F, 2.0F, 13.0F,
            0.0F, 3.0F, 13.6F, 0.0F, 0.0F, 18.0F,
            0.0F, 0.0F, 16.0F, 0.0F, 2.0F, 13.0F,
            0.0F, 3.0F, 13.6F, 0.0F, 0.0F, 13.5F,
            0.0F, 0.0F, 22.0F, 0.0F, 2.0F, 18.0F,
            0.0F, 3.0F, 18.7F, 0.0F, 0.0F, 22.5F,
            0.0F, 0.0F, 22.0F, 0.0F, 2.0F, 18.0F,
            0.0F, 3.0F, 18.7F, 0.0F, 0.0F, 25.5F,
            0.0F, 0.0F, 22.0F, 0.0F, 2.0F, 18.0F,
            0.0F, 3.0F, 18.7F, 2.9014F, -12.0F, 6.3704F,
            0.0F, 0.0F, 34.0F, 0.0F, 0.0F, 30.0F,
            0.0F, 0.0F, 10.8F, 0.0F, 0.0F, 16.0F,
            0.0F, 2.0F, 13.0F, 0.0F, 3.0F, 13.6F,
            0.0F, 0.0F, 18.0F, 0.0F, 0.0F, 16.0F,
            0.0F, 2.0F, 13.0F, 0.0F, 3.0F, 13.6F,
            0.0F, 0.0F, 13.5F, 0.0F, 0.0F, 22.0F,
            0.0F, 2.0F, 18.0F, 0.0F, 3.0F, 18.7F,
            0.0F, 0.0F, 22.5F, 0.0F, 0.0F, 22.0F,
            0.0F, 2.0F, 18.0F, 0.0F, 3.0F, 18.7F,
            0.0F, 0.0F, 25.5F, 0.0F, 0.0F, 22.0F,
            0.0F, 2.0F, 18.0F, 0.0F, 3.0F, 18.7F,
            6.5572F, -12.0F, -2.45F, 0.0F, 0.0F, 34.0F,
            0.0F, 0.0F, 30.0F, 0.0F, 0.0F, 10.8F,
            0.0F, 0.0F, 16.0F, 0.0F, 2.0F, 13.0F,
            0.0F, 3.0F, 13.6F, 0.0F, 0.0F, 18.0F,
            0.0F, 0.0F, 16.0F, 0.0F, 2.0F, 13.0F,
            0.0F, 3.0F, 13.6F, 0.0F, 0.0F, 13.5F,
            0.0F, 0.0F, 22.0F, 0.0F, 2.0F, 18.0F,
            0.0F, 3.0F, 18.7F, 0.0F, 0.0F, 22.5F,
            0.0F, 0.0F, 22.0F, 0.0F, 2.0F, 18.0F,
            0.0F, 3.0F, 18.7F, 0.0F, 0.0F, 25.5F,
            0.0F, 0.0F, 22.0F, 0.0F, 2.0F, 18.0F,
            0.0F, 3.0F, 18.7F, 0.5988F, -12.0F, -2.9396F,
            0.0F, 0.0F, 36.0F, 0.0F, 0.0F, 30.0F,
            0.0F, 0.0F, 9.9F, 0.0F, 0.0F, 16.0F,
            0.0F, 2.0F, 13.0F, 0.0F, 3.0F, 13.6F,
            0.0F, 0.0F, 16.5F, 0.0F, 0.0F, 16.0F,
            0.0F, 2.0F, 13.0F, 0.0F, 3.0F, 13.6F,
            0.0F, 0.0F, 13.5F, 0.0F, 0.0F, 22.0F,
            0.0F, 2.0F, 18.0F, 0.0F, 3.0F, 18.7F,
            0.0F, 0.0F, 22.5F, 0.0F, 0.0F, 22.0F,
            0.0F, 2.0F, 18.0F, 0.0F, 3.0F, 18.7F,
            0.0F, 0.0F, 16.2F, 0.0F, 0.0F, 22.0F,
            0.0F, 2.0F, 18.0F, 0.0F, 3.0F, 18.7F,
            0.0F, 0.0F, 27.0F, 0.0F, 0.0F, 22.0F,
            0.0F, 2.0F, 18.0F, 0.0F, 3.0F, 18.7F,
            -2.0636F, -6.0F, 14.8574F, 0.0F, 0.0F, 25.0F,
            0.0F, 0.0F, 24.0F, 0.0F, 0.0F, 23.0F,
            0.0F, 0.0F, 21.0F, 0.0F, 0.0F, 19.0F,
            -0.3104F, 2.5F, 5.1F, 0.0F, 0.0F, 10.45F,
            0.0F, 1.5F, 15.0F, -1.1888F, 3.5F, 5.7F,
            0.0F, 0.0F, 11.55F, 0.0F, 2.0F, 13.5F,
            -0.8508F, 4.5F, 6.3F, 0.0F, 0.0F, 12.65F,
            0.0F, 3.0F, 12.0F, 1.2514F, 5.5F, 6.9F,
            0.0F, 0.0F, 13.2F, 4.3646F, -9.0F, -19.5179F,
            0.0F, 4.0F, 0.0F, -0.4138F, -10.0F, -16.995F,
            0.0F, 0.0F, 24.0F, 0.0F, 0.0F, 23.0F,
            0.0F, 0.0F, 22.0F, 0.0F, 0.0F, 20.0F,
            0.0F, 0.0F, 18.0F, -0.268F, 2.5F, 4.8F,
            0.0F, 0.0F, 9.9F, 0.0F, 1.5F, 15.0F,
            0.0232F, 3.5F, 5.4F, 0.0F, 0.0F, 11.0F,
            0.0F, 2.0F, 13.5F, -0.0551F, 4.5F, 6.0F,
            0.0F, 0.0F, 12.1F, 0.0F, 3.0F, 12.0F,
            -1.6476F, 5.5F, 6.6F, 0.0F, 0.0F, 12.65F,
            16.8086F, -14.0F, 2.5436F, 0.0F, 0.0F, 23.0F,
            0.0F, 0.0F, 22.0F, 0.0F, 0.0F, 21.0F,
            0.0F, 0.0F, 19.0F, 0.0F, 0.0F, 17.0F,
            0.1424F, 2.5F, 4.8F, 0.0F, 0.0F, 9.35F,
            0.0F, 1.5F, 15.0F, -0.3747F, 3.5F, 5.1F,
            0.0F, 0.0F, 10.45F, 0.0F, 2.0F, 13.5F,
            -0.6887F, 4.5F, 5.7F, 0.0F, 0.0F, 11.55F,
            0.0F, 3.0F, 12.0F, 0.5842F, 5.5F, 6.3F,
            0.0F, 0.0F, 12.1F, -18.9986F, -14.0F, 0.2313F,
            0.0F, 0.0F, 23.0F, 0.0F, 0.0F, 22.0F,
            0.0F, 0.0F, 21.0F, 0.0F, 0.0F, 19.0F,
            0.0F, 0.0F, 17.0F, 1.2512F, 2.5F, 4.5F,
            0.0F, 0.0F, 9.35F, 0.0F, 1.5F, 15.0F,
            -1.3068F, 3.5F, 5.1F, 0.0F, 0.0F, 10.45F,
            0.0F, 2.0F, 13.5F, 1.3777F, 4.5F, 5.7F,
            0.0F, 0.0F, 11.55F, 0.0F, 3.0F, 12.0F,
            -1.6813F, 5.5F, 6.3F, 0.0F, 0.0F, 12.1F,
            -7.2577F, -12.0F, -29.1089F};
    /** Rest rotations, radians (x, y, z), three per bone. */
    public static final float[] REST = {
            0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 0.0F, -0.16F, 3.5897F, 0.0F,
            -0.22F, 0.0F, 0.0F, -0.16F, 4.4668F, 0.0F,
            -0.22F, 0.0F, 0.0F, -0.16F, 5.1433F, 0.0F,
            -0.22F, 0.0F, 0.0F, -0.16F, 6.2122F, 0.0F,
            -0.22F, 0.0F, 0.0F, -0.16F, 7.1087F, 0.0F,
            -0.22F, 0.0F, 0.0F, -0.16F, 7.975F, 0.0F,
            -0.22F, 0.0F, 0.0F, -0.16F, 9.0777F, 0.0F,
            -0.22F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 0.11F, 0.0F, 0.0F, 0.11F, 0.0F,
            0.0F, 0.11F, 0.0F, 0.0F, 0.11F, 0.0F,
            0.0F, 0.11F, 0.0F, 0.9816F, 3.5956F, 0.0F,
            -0.2389F, 0.0F, 0.0F, -0.3757F, 0.0F, 0.0F,
            -0.7132F, -0.7882F, 0.0F, -0.7781F, 0.0F, 0.0F,
            1.3973F, -0.3918F, -0.4783F, 2.7089F, 0.0161F, -2.865F,
            -0.7049F, 1.1787F, 0.0F, -0.7262F, 0.0F, 0.0F,
            1.9603F, 0.2078F, 1.7806F, 2.4718F, -0.6335F, -3.0963F,
            -0.521F, -0.8748F, 0.0F, -0.8095F, 0.0F, 0.0F,
            2.4666F, 0.2013F, -2.5842F, -0.2065F, -1.5251F, 0.752F,
            -0.5092F, 1.1957F, 0.0F, -0.5514F, 0.0F, 0.0F,
            2.3804F, -0.7128F, 2.8692F, 0.5254F, 1.2821F, -0.1818F,
            -0.405F, 1.2883F, 0.0F, -0.8759F, 0.0F, 0.0F,
            1.3671F, -1.0746F, -2.4658F, 3.1356F, -0.1075F, 2.2175F,
            0.9766F, 5.0548F, 0.0F, -0.2211F, 0.0F, 0.0F,
            -0.3436F, 0.0F, 0.0F, -0.4074F, -0.8494F, 0.0F,
            -0.7306F, 0.0F, 0.0F, 2.1957F, -0.4757F, -2.1272F,
            2.9962F, 0.59F, -2.9152F, -0.6099F, 0.953F, 0.0F,
            -0.7888F, 0.0F, 0.0F, 1.134F, 0.3093F, -0.1248F,
            0.3427F, -0.0867F, -0.3846F, -0.4959F, -0.9989F, 0.0F,
            -0.6511F, 0.0F, 0.0F, 2.5103F, 0.5242F, -2.7573F,
            3.0502F, 1.3495F, -2.6161F, -0.7143F, 1.0421F, 0.0F,
            -0.6352F, 0.0F, 0.0F, 2.4542F, 0.0288F, 2.2457F,
            2.4783F, 1.2131F, 1.8584F, -0.4363F, 1.147F, 0.0F,
            -0.781F, 0.0F, 0.0F, 2.6703F, 0.4581F, 1.9462F,
            -0.0731F, -0.1499F, -0.8482F, 0.9331F, 6.8245F, 0.0F,
            -0.2527F, 0.0F, 0.0F, -0.3158F, 0.0F, 0.0F,
            -0.6636F, -1.0816F, 0.0F, -0.5623F, 0.0F, 0.0F,
            0.9659F, -0.0048F, 0.5796F, 0.4616F, 0.0944F, 0.4057F,
            -0.7107F, 0.8585F, 0.0F, -0.8864F, 0.0F, 0.0F,
            1.3487F, -0.2701F, -1.7816F, 0.6024F, -0.7013F, -0.7216F,
            -0.3692F, -0.9761F, 0.0F, -0.7023F, 0.0F, 0.0F,
            0.9978F, 0.9131F, 1.5395F, -3.0839F, 0.5805F, -2.561F,
            -0.3976F, 0.9885F, 0.0F, -0.9308F, 0.0F, 0.0F,
            0.7907F, 0.334F, -0.4684F, -0.0181F, 0.0922F, -0.555F,
            -0.3509F, 0.71F, 0.0F, -0.9027F, 0.0F, 0.0F,
            1.1886F, 1.1541F, 0.5561F, -0.3787F, 0.1084F, -0.6413F,
            0.9414F, 8.3299F, 0.0F, -0.1788F, 0.0F, 0.0F,
            -0.3439F, 0.0F, 0.0F, -0.4267F, -1.2306F, 0.0F,
            -0.6235F, 0.0F, 0.0F, 1.0765F, -0.6029F, -0.2205F,
            0.3549F, -0.7485F, 0.1605F, -0.6578F, 0.9068F, 0.0F,
            -0.5598F, 0.0F, 0.0F, 1.6956F, -0.6816F, -2.2936F,
            0.7632F, -1.0226F, -1.035F, -0.6671F, -0.7911F, 0.0F,
            -0.726F, 0.0F, 0.0F, 0.775F, -0.4639F, 0.2683F,
            0.0842F, 0.6729F, 0.5673F, -0.4091F, 1.297F, 0.0F,
            -0.6945F, 0.0F, 0.0F, 0.6007F, -0.1159F, -1.0126F,
            0.5797F, -1.3569F, -1.3029F, -0.4909F, 0.8811F, 0.0F,
            -0.6371F, 0.0F, 0.0F, 0.9059F, -1.1639F, -1.5801F,
            -0.1986F, -0.4679F, -0.5956F, 1.3975F, 2.9778F, 0.0F,
            -0.0972F, 0.0F, 0.0F, -0.1975F, 0.0F, 0.0F,
            -0.7023F, -1.1739F, 0.0F, -0.6228F, 0.0F, 0.0F,
            2.7475F, -0.4004F, -1.9051F, 0.1407F, -1.3697F, 0.8295F,
            -0.575F, 0.8005F, 0.0F, -0.8162F, 0.0F, 0.0F,
            0.6994F, -1.0276F, -1.3709F, -1.6191F, -1.2906F, 0.8916F,
            -0.4519F, -1.1498F, 0.0F, -0.9235F, 0.0F, 0.0F,
            0.2313F, -0.6274F, 0.9711F, -0.2405F, 0.0685F, 1.1156F,
            -0.4201F, 1.195F, 0.0F, -0.8152F, 0.0F, 0.0F,
            2.9918F, 0.4263F, 1.9484F, -2.8667F, 0.663F, 2.1513F,
            -0.5104F, -0.9665F, 0.0F, -0.8028F, 0.0F, 0.0F,
            0.0251F, -0.2676F, 0.9389F, -0.6976F, -0.8904F, 1.6665F,
            -0.5451F, 1.2439F, 0.0F, -0.7262F, 0.0F, 0.0F,
            3.0487F, -0.6922F, 1.992F, -2.4445F, -1.2848F, 1.2162F,
            0.3246F, 6.1452F, 0.0F, -0.0156F, 0.0F, 0.0F,
            -0.2678F, 0.0F, 0.0F, -0.2917F, 0.0F, 0.0F,
            -0.3947F, 0.0F, 0.0F, -0.3881F, 0.0F, 0.0F,
            1.4074F, 0.5143F, 1.2474F, -0.2288F, -0.7297F, 0.0F,
            1.1404F, 0.8232F, 0.4763F, 1.092F, 0.8263F, 0.9562F,
            -0.2491F, 0.8124F, 0.0F, 0.4976F, 0.5613F, 0.4795F,
            0.2678F, 0.3562F, 0.0954F, -0.2937F, -0.7573F, 0.0F,
            2.848F, 0.4492F, 3.0404F, -0.0492F, 0.5788F, -0.0269F,
            -0.2332F, 0.5876F, 0.0F, 3.1416F, 0.22F, 3.1416F,
            0.0F, 0.0F, 0.0F, 0.2918F, 3.1659F, 0.0F,
            -0.0129F, 0.0F, 0.0F, -0.2804F, 0.0F, 0.0F,
            -0.3292F, 0.0F, 0.0F, -0.3139F, 0.0F, 0.0F,
            -0.3877F, 0.0F, 0.0F, 1.6003F, -0.5378F, -1.6283F,
            -0.147F, -0.5983F, 0.0F, 0.6997F, -0.3348F, -0.7103F,
            1.1941F, -0.8683F, -1.0926F, 0.0937F, 0.6068F, 0.0F,
            2.9481F, 0.4004F, -3.0286F, 0.7561F, -1.0778F, -0.6932F,
            -0.3098F, -0.6536F, 0.0F, 0.9028F, -1.1704F, -0.8634F,
            3.1389F, -0.9984F, -3.1394F, -0.1175F, 0.7836F, 0.0F,
            0.2769F, 7.7038F, 0.0F, -0.0544F, 0.0F, 0.0F,
            -0.2999F, 0.0F, 0.0F, -0.3255F, 0.0F, 0.0F,
            -0.3846F, 0.0F, 0.0F, -0.3503F, 0.0F, 0.0F,
            1.2167F, 0.2547F, 0.5982F, -0.075F, 0.8986F, 0.0F,
            0.8003F, -0.8137F, 0.0481F, 1.1479F, 0.6809F, 0.9502F,
            -0.1474F, -0.7705F, 0.0F, 0.4263F, -0.0273F, -0.317F,
            0.4202F, -0.28F, -0.1228F, -0.0348F, 0.7071F, 0.0F,
            0.2799F, -1.2256F, -0.2137F, 0.0774F, 0.0034F, 0.0003F,
            -0.1662F, -0.8451F, 0.0F, 0.3072F, 4.7246F, 0.0F,
            -0.0737F, 0.0F, 0.0F, -0.2588F, 0.0F, 0.0F,
            -0.3637F, 0.0F, 0.0F, -0.3299F, 0.0F, 0.0F,
            -0.377F, 0.0F, 0.0F, 1.6254F, 0.472F, 1.6904F,
            -0.0833F, 0.8533F, 0.0F, 1.1935F, 1.0105F, 1.7406F,
            2.4092F, 0.174F, 2.9871F, -0.1053F, -0.7851F, 0.0F,
            2.7673F, -0.0934F, 2.8863F, 2.7062F, -0.4529F, -2.9408F,
            -0.2047F, 0.7616F, 0.0F, 1.9324F, 1.3321F, 1.9599F,
            3.0982F, 0.9475F, 3.1064F, -0.2376F, -0.5911F, 0.0F,
            3.1416F, -0.2443F, 3.1416F};
    public static final int WILLOW = 1;
    public static final int MAW_JAW = 160;
    public static final int[] TRUNK = {17, 18, 19, 20, 21, 22};
    public static final int[][] LIMB = {
            {161, 162, 163, 164, 165, 166},
            {195, 196, 197, 198, 199, 200},
            {142, 143, 144, 145, 146, 147},
            {178, 179, 180, 181, 182, 183}};
    /** Segment lengths, px. The tip of segment k is (0, 0, LIMB_LEN[l][k]) in its frame. */
    static final float[][] LIMB_LEN = {
            {24.0F, 23.0F, 22.0F, 20.0F, 18.0F, 16.0F},
            {23.0F, 22.0F, 21.0F, 19.0F, 17.0F, 15.0F},
            {25.0F, 24.0F, 23.0F, 21.0F, 19.0F, 17.0F},
            {23.0F, 22.0F, 21.0F, 19.0F, 17.0F, 16.0F}};
    /** Segment half-thickness, px. */
    static final float[][] LIMB_RAD = {
            {8.0F, 6.5F, 5.5F, 4.5F, 3.5F, 2.5F},
            {8.0F, 6.5F, 5.5F, 4.5F, 3.5F, 2.5F},
            {8.0F, 6.5F, 5.5F, 4.5F, 3.5F, 2.5F},
            {8.0F, 6.5F, 5.5F, 4.5F, 3.5F, 2.5F}};
    public static final float[] LIMB_BEARING = {14.0F, 97.0F, 191.0F, 274.0F};
    public static final int[][] ROOT = {
            {3, 4},
            {5, 6},
            {7, 8},
            {9, 10},
            {11, 12},
            {13, 14},
            {15, 16}};
    public static final int[][] BOUGH = {
            {23, 24, 25},
            {46, 47, 48},
            {69, 70, 71},
            {92, 93, 94},
            {115, 116, 117}};
    public static final int[] CURTAIN = {175, 176, 172, 173, 169, 170, 167, 209, 210, 206, 207, 203, 204, 201, 156, 157,
            153, 154, 150, 151, 148, 192, 193, 189, 190, 186, 187, 184, 45, 44, 37, 36,
            41, 40, 29, 28, 33, 32, 68, 67, 60, 59, 64, 63, 52, 51, 56, 55,
            91, 90, 83, 82, 87, 86, 75, 74, 79, 78, 114, 113, 106, 105, 110, 109,
            98, 97, 102, 101, 137, 136, 141, 140, 129, 128, 133, 132, 121, 120, 125, 124};
    static final float[] CURTAIN_LEN = {
            24.0F, 22.0F, 28.0F, 26.0F, 32.0F, 30.0F, 34.0F, 24.0F, 22.0F, 28.0F,
            26.0F, 32.0F, 30.0F, 34.0F, 24.0F, 22.0F, 28.0F, 26.0F, 32.0F, 30.0F,
            34.0F, 24.0F, 22.0F, 28.0F, 26.0F, 32.0F, 30.0F, 34.0F, 34.0F, 46.0F,
            34.0F, 46.0F, 34.0F, 46.0F, 26.0F, 34.0F, 26.0F, 34.0F, 34.0F, 46.0F,
            34.0F, 46.0F, 34.0F, 46.0F, 26.0F, 34.0F, 26.0F, 34.0F, 34.0F, 46.0F,
            34.0F, 46.0F, 34.0F, 46.0F, 26.0F, 34.0F, 26.0F, 34.0F, 34.0F, 46.0F,
            34.0F, 46.0F, 34.0F, 46.0F, 26.0F, 34.0F, 26.0F, 34.0F, 34.0F, 46.0F,
            34.0F, 46.0F, 34.0F, 46.0F, 34.0F, 46.0F, 26.0F, 34.0F, 26.0F, 34.0F};
    public static final float KNOT_BEARING = 14.0F;
    public static final float MODEL_SCALE = 1.0F;
    /** Horizontal reach of the surface roots, blocks. ROOT_STOMP's damage ring. */
    public static final float STOMP_RANGE = 3.6F;
    // >>> GENERATED RIG

    public static final int LIMBS = 4;
    public static final int SEGS = 6;

    // --- shared timings (ticks), also read by the entity -----------------------------------------
    // Every damage moment below is a tick constant, and every pose curve is built FROM those
    // constants, so the frame where a branch meets you and the tick that deals the damage are the
    // same number rather than two numbers that happen to agree today.
    public static final int WAKE_DURATION = 30;
    public static final int SWEEP_DURATION = 30;
    public static final int SWEEP_HIT_START = 9;
    public static final int SWEEP_HIT_END = 21;
    public static final int SLAM_DURATION = 34;
    public static final int SLAM_RAISED_TICK = 12;
    public static final int SLAM_STRIKE_TICK = 16;
    public static final int STOMP_DURATION = 40;
    public static final int STOMP_STRIKE_TICK = 22;
    public static final int SWAT_DURATION = 26;
    public static final int SWAT_STRIKE_TICK = 11;
    public static final int GRAB_DURATION = 18;
    public static final int GRAB_CATCH_TICK = 10;
    public static final int SMASH_DURATION = 48;
    public static final int SMASH_BLOW_A = 18;
    public static final int SMASH_BLOW_B = 32;
    public static final int SMASH_RELEASE_TICK = 38;
    public static final int STAGGER_DURATION = 20;

    /** Furthest out a limb is ever aimed, blocks: the sweep's reach at full stretch. */
    public static final float SWEEP_DIST = 8.0F;
    /** A sweep's tip runs at knee height... */
    public static final float SWEEP_HEIGHT = 0.7F;
    /** ...a little past the target, so the outer limb, not just the tip, crosses where they stand. */
    private static final float SWEEP_PAST = 0.35F;
    /** A slam lands just beyond the target and lies across them. */
    private static final float SLAM_PAST = 0.8F;
    /** Sweep yaw, radians from the limb's rest bearing: cocked back, then through. */
    public static final float SWEEP_FROM = -0.55F;
    public static final float SWEEP_TO = 1.30F;
    /** Furthest the aim parameters are allowed to turn a limb off its rest bearing. */
    public static final float MAX_AIM_YAW = 0.9F;

    /** Raise (Java xRot, + is up) the roused tree holds each limb segment at, on top of the limp
     *  rest pose: lifted off the trunk and open, the posture of something about to swing. */
    private static final float[] NEUTRAL = {0.22F, 0.10F, 0.17F, 0.15F, 0.12F, 0.08F};
    /** How far down the chain an uncurl reaches: used to flop the limbs in a stagger. */
    private static final float[] DC = {0.0F, 0.10F, 0.22F, 0.30F, 0.34F, 0.36F};
    /** Rear-up added to every limb for the stomp, and to the slam limb's wind-up. */
    private static final float[] REAR = {0.55F, 0.22F, 0.20F, 0.18F, 0.14F, 0.10F};

    /** Bone ids from the root down to segment 6 of each limb. */
    private static final int[][] LIMB_CHAIN = new int[LIMBS][];
    /** Accumulated rest yaw of each trunk ring's frame, so a lean can be expressed in it. */
    private static final float[] TRUNK_YAW;

    static {
        for (int l = 0; l < LIMBS; l++) {
            LIMB_CHAIN[l] = ancestry(LIMB[l][SEGS - 1]);
        }
        TRUNK_YAW = new float[TRUNK.length];
        for (int i = 0; i < TRUNK.length; i++) {
            TRUNK_YAW[i] = frameYaw(PARENT[TRUNK[i]]);
        }
    }

    private static int[] ancestry(int bone) {
        int n = 0;
        for (int b = bone; b >= 0; b = PARENT[b]) n++;
        int[] out = new int[n];
        for (int b = bone; b >= 0; b = PARENT[b]) out[--n] = b;
        return out;
    }

    /** Sum of rest yRot from the root to {@code bone} inclusive. Only exact while the chain has no
     *  rest tilt, which is true of the willow, the trunk rings and the root bases. */
    private static float frameYaw(int bone) {
        float y = 0.0F;
        for (int b = bone; b >= 0; b = PARENT[b]) y += REST[b * 3 + 1];
        return y;
    }

    // --- input -------------------------------------------------------------------------------------

    /** Everything a pose depends on. Fill it from the entity with {@link WhompingWillowEntity#rigInput}. */
    public static final class Input {
        public AttackState state = AttackState.DORMANT;
        /** State tick, fractional on the client (stateTick + partialTick). */
        public float tick;
        public int active;
        public float sign = 1.0F;
        public float aimYaw;
        public float aimDist = SWEEP_DIST;
        public float aimHeight = 1.0F;
        /** 0 hanging limp .. 1 roused. Forced to 1 inside every attack state. */
        public float rouse = 1.0F;
        /** Client clock for idle motion, already offset per entity. Ignored when !idle. */
        public float age;
        /** Client only: add wind and writhing. Never set on the server. */
        public boolean idle;

        // Scratch. Per Input rather than static: in singleplayer the integrated server and the
        // render thread pose willows at the same time in one JVM.
        final float[] delta = new float[SEGS];
        final float[] solved = new float[SEGS];
        final double[] m = new double[12 * 24];
        final double[] tip = new double[3];
    }

    // --- pose --------------------------------------------------------------------------------------

    /**
     * Writes an absolute (x, y, z) rotation for every bone into {@code rot}, which must hold
     * {@code 3 * BONES} floats. Curtains are left at rest; the model keeps them plumb itself.
     */
    public static void pose(Input in, float[] rot) {
        System.arraycopy(REST, 0, rot, 0, REST.length);
        AttackState s = in.state;
        boolean limp = s == AttackState.DORMANT || s == AttackState.FROZEN;
        boolean attacking = s == AttackState.SWEEP || s == AttackState.SLAM || s == AttackState.SWAT
                || s == AttackState.GRAB || s == AttackState.SMASH || s == AttackState.ROOT_STOMP;
        float rouse = attacking ? 1.0F : (limp ? Math.min(in.rouse, 1.0F) : in.rouse);
        float t = in.tick;
        int act = Math.floorMod(in.active, LIMBS);

        // 1. Master lean and trunk -- first, because the limb solve runs FK through them.
        float env = envelope(s, t);
        float lean = leanAmount(s, t);
        float leanYaw = LIMB_BEARING[act] * DEG + aimYawFor(in, s, t) * envYaw(s, t);
        if (lean != 0.0F) {
            leanToward(rot, WILLOW, 0.0F, lean * 0.45F, leanYaw);
            for (int i = 0; i < TRUNK.length; i++) {
                leanToward(rot, TRUNK[i], TRUNK_YAW[i], lean * 0.11F, leanYaw);
            }
        }
        if (s == AttackState.STAGGER) {
            float p = t / STAGGER_DURATION;
            float rock = (float) Math.sin(p * 22.0F) * (1.0F - p) * 0.12F;
            leanToward(rot, WILLOW, 0.0F, rock, LIMB_BEARING[act] * DEG);
        }
        if (s == AttackState.ROOT_STOMP) {
            // Rears back on its roots, then drives straight down: a vertical heave the master
            // carries as a quick pitch about the trunk axis.
            float heave = rootLift(t) * 0.035F;
            rot[WILLOW * 3] += heave;
        }

        // 2. Limbs.
        for (int l = 0; l < LIMBS; l++) {
            boolean active = l == act;
            float[] d = in.delta;
            for (int k = 0; k < SEGS; k++) d[k] = NEUTRAL[k] * rouse;
            float yaw = 0.0F;
            if (active && hasSolve(s)) {
                yaw = aimYawFor(in, s, t) * envYaw(s, t);
                rot[LIMB[l][0] * 3 + 1] = REST[LIMB[l][0] * 3 + 1] + yaw;
                float tx = targetDist(in, s, t);
                float ty = targetHeight(in, s, t);
                solve(in, l, rot, tx, ty, bendFor(s), in.solved);
                for (int k = 0; k < SEGS; k++) d[k] = lerp(env, d[k], in.solved[k]);
            } else if (s == AttackState.ROOT_STOMP) {
                // Rear with the roots and come down with them: the tell and the blow are one motion.
                float rear = rootLift(t);
                for (int k = 0; k < SEGS; k++) d[k] += REAR[k] * rear;
            } else if (s == AttackState.WAKE) {
                float p = t / WAKE_DURATION;
                float r = smooth(0.0F, 0.8F, p);
                for (int k = 0; k < SEGS; k++) d[k] = NEUTRAL[k] * Math.max(rouse, r);
            } else if (s == AttackState.STAGGER) {
                // Ramped in over three ticks: a stagger interrupts whatever the limb was doing, and
                // a recoil that starts at full amplitude reads as the branch teleporting.
                float p = t / STAGGER_DURATION;
                float in3 = smooth(0.0F, 3.0F, t);
                float flop = in3 * (1.0F - p) * (float) Math.sin(t * 0.9F + l * 1.3F) * 0.30F;
                for (int k = 0; k < SEGS; k++) {
                    d[k] = NEUTRAL[k] * (1.0F - 0.6F * in3 * (1.0F - p)) - Math.abs(flop) * DC[k];
                    rot[LIMB[l][k] * 3 + 2] += flop * 0.25F;
                }
            }
            for (int k = 0; k < SEGS; k++) {
                rot[LIMB[l][k] * 3] = REST[LIMB[l][k] * 3] + d[k];
            }
            if (in.idle) {
                float calm = active ? (1.0F - env) : 1.0F;
                writhe(rot, l, in.age, limp ? 0.022F : 0.075F, calm);
            }
        }

        // 3. Roots.
        float lift = s == AttackState.ROOT_STOMP ? rootLift(t) : 0.0F;
        for (int r = 0; r < ROOT.length; r++) {
            float w = 0.85F + 0.3F * (float) Math.sin(r * 2.1F);
            rot[ROOT[r][0] * 3] += lift * 0.42F * w;
            rot[ROOT[r][1] * 3] += lift * 0.30F * w;
        }

        // 4. Crown boughs. Never damage-bearing, so they are free to move however reads best.
        for (int b = 0; b < BOUGH.length; b++) {
            float sym = 0.0F;
            if (s == AttackState.ROOT_STOMP) {
                sym = rootLift(t) * 0.10F;
            } else if (attacking) {
                sym = env * 0.04F;
            } else if (s == AttackState.WAKE) {
                sym = (1.0F - t / WAKE_DURATION) * 0.08F * (float) Math.sin(t * 1.7F + b);
            }
            for (int k = 0; k < BOUGH[b].length; k++) {
                rot[BOUGH[b][k] * 3] += sym * (k + 1) * 0.5F;
                if (in.idle) {
                    float a = in.age * 0.031F + b * 1.9F - k * 0.6F;
                    float amp = (limp ? 0.012F : 0.03F) * (0.6F + k * 0.5F);
                    rot[BOUGH[b][k] * 3] += (float) Math.sin(a) * amp;
                    rot[BOUGH[b][k] * 3 + 2] += (float) Math.cos(a * 0.77F + 0.6F) * amp * 0.8F;
                }
            }
        }

        // 5. Wind in the bole -- client only, and silent while an attack is committed.
        if (in.idle) {
            float calm = 1.0F - env;
            float amp = (limp ? 0.010F : 0.022F) * calm;
            float wx = (float) (Math.sin(in.age * 0.021F) * 0.7F + Math.sin(in.age * 0.0083F + 2.3F) * 0.3F);
            float wz = (float) (Math.sin(in.age * 0.037F + 1.1F) * 0.6F + Math.sin(in.age * 0.0134F) * 0.4F);
            for (int i = 0; i < TRUNK.length; i++) {
                float g = (0.35F + i * 0.22F) * amp;
                rot[TRUNK[i] * 3] += wx * g;
                rot[TRUNK[i] * 3 + 2] += wz * g * 0.7F;
            }
        }
    }

    private static final float DEG = (float) (Math.PI / 180.0);

    /** The bend each attack holds its limb at, per segment (negative curls the tip down), or NaN
     *  where it is solved to land the tip at the aimed distance -- which is every attack: a swing
     *  with a fixed bend reaches one distance, and the tree would happily pick it against a target
     *  standing somewhere else. */
    private static float bendFor(AttackState s) {
        return Float.NaN;
    }

    private static boolean hasSolve(AttackState s) {
        return s == AttackState.SWEEP || s == AttackState.SLAM || s == AttackState.SWAT
                || s == AttackState.GRAB || s == AttackState.SMASH;
    }

    /** How committed the active limb is to its attack pose: 0 at neutral, 1 fully solved. Every
     *  damage window sits inside the span where this is exactly 1. */
    public static float envelope(AttackState s, float t) {
        if (s == AttackState.SWEEP) {
            return smooth(0.0F, 6.5F, t) * (1.0F - smooth(23.5F, SWEEP_DURATION, t));
        } else if (s == AttackState.SLAM) {
            return smooth(0.0F, 4.0F, t) * (1.0F - smooth(23.0F, SLAM_DURATION, t));
        } else if (s == AttackState.SWAT) {
            return smooth(0.0F, 3.0F, t) * (1.0F - smooth(16.0F, SWAT_DURATION, t));
        } else if (s == AttackState.GRAB) {
            return smooth(0.0F, 3.0F, t) * (1.0F - smooth(12.0F, GRAB_DURATION, t));
        } else if (s == AttackState.SMASH) {
            return 1.0F - smooth(SMASH_RELEASE_TICK + 1.0F, SMASH_DURATION, t);
        } else if (s == AttackState.ROOT_STOMP) {
            return smooth(0.0F, 4.0F, t) * (1.0F - smooth(30.0F, STOMP_DURATION, t));
        }
        return 0.0F;
    }

    /** The sweep's yaw is its own curve; every other attack turns the limb by aimYaw and holds it. */
    private static float envYaw(AttackState s, float t) {
        return s == AttackState.SWEEP ? 1.0F : envelope(s, t);
    }

    private static float aimYawFor(Input in, AttackState s, float t) {
        if (s == AttackState.SWEEP) {
            return sweepYaw(t) * in.sign;
        }
        return clamp(in.aimYaw, -MAX_AIM_YAW, MAX_AIM_YAW);
    }

    /** Yaw of a sweeping limb off its rest bearing at state tick {@code t}, before the sign. */
    public static float sweepYaw(float t) {
        if (t <= SWEEP_HIT_START) {
            return SWEEP_FROM * easeOut(t / SWEEP_HIT_START);
        }
        if (t <= SWEEP_HIT_END) {
            float u = (t - SWEEP_HIT_START) / (SWEEP_HIT_END - SWEEP_HIT_START);
            return lerp(u * u * (3.0F - 2.0F * u), SWEEP_FROM, SWEEP_TO);
        }
        float r = smooth(SWEEP_HIT_END, SWEEP_DURATION, t);
        // overshoot, then settle back to rest
        return SWEEP_TO * (1.0F - r) + (float) Math.sin(r * Math.PI) * 0.12F;
    }

    private static float targetDist(Input in, AttackState s, float t) {
        float aim = clamp(in.aimDist, 2.6F, SWEEP_DIST);
        if (s == AttackState.SWEEP) {
            float reach = clamp(in.aimDist + SWEEP_PAST, 4.0F, SWEEP_DIST + 0.6F);
            if (t <= SWEEP_HIT_START) return 5.6F;
            return lerp(smooth(SWEEP_HIT_START, SWEEP_HIT_START + 3.0F, t), 5.6F, reach);
        } else if (s == AttackState.SLAM) {
            float land = clamp(in.aimDist + SLAM_PAST, 4.0F, SWEEP_DIST + 0.6F);
            if (t <= SLAM_RAISED_TICK) return lerp(smooth(0.0F, SLAM_RAISED_TICK, t), 5.8F, 3.4F);
            return lerp(smooth(SLAM_RAISED_TICK, SLAM_STRIKE_TICK, t), 3.4F, land);
        } else if (s == AttackState.SWAT) {
            if (t <= 6.0F) return 5.0F;
            return lerp(smooth(6.0F, SWAT_STRIKE_TICK, t), 5.0F, aim);
        } else if (s == AttackState.GRAB) {
            if (t <= 4.0F) return 4.2F;
            return lerp(smooth(4.0F, GRAB_CATCH_TICK, t), 4.2F, aim);
        } else if (s == AttackState.SMASH) {
            float lifted = 4.6F;
            float down = Math.max(aim, 4.6F);
            if (t <= 12.0F) return lerp(smooth(0.0F, 12.0F, t), aim, lifted);
            if (t <= SMASH_BLOW_A) return lerp(smooth(12.0F, SMASH_BLOW_A, t), lifted, down);
            if (t <= 28.0F) return lerp(smooth(SMASH_BLOW_A + 2.0F, 28.0F, t), down, lifted);
            if (t <= SMASH_BLOW_B) return lerp(smooth(28.0F, SMASH_BLOW_B, t), lifted, down);
            return down;
        }
        return 5.5F;
    }

    private static float targetHeight(Input in, AttackState s, float t) {
        if (s == AttackState.SWEEP) {
            if (t <= SWEEP_HIT_START) return 2.6F;
            return lerp(smooth(SWEEP_HIT_START, SWEEP_HIT_START + 3.0F, t), 2.6F, SWEEP_HEIGHT);
        } else if (s == AttackState.SLAM) {
            if (t <= SLAM_RAISED_TICK) return lerp(easeOut(t / SLAM_RAISED_TICK), 3.0F, 8.6F);
            if (t <= SLAM_STRIKE_TICK) {
                // accelerating, not eased: the hammer falls
                float u = (t - SLAM_RAISED_TICK) / (SLAM_STRIKE_TICK - SLAM_RAISED_TICK);
                return lerp(u * u, 8.6F, 0.25F);
            }
            float b = t - SLAM_STRIKE_TICK;
            return 0.25F + (b < 4.0F ? (float) Math.sin(b / 4.0F * Math.PI) * 0.35F : 0.0F);
        } else if (s == AttackState.SWAT) {
            float aim = clamp(in.aimHeight, 2.0F, 11.0F);
            if (t <= 6.0F) return lerp(smooth(0.0F, 6.0F, t), 3.0F, 1.6F);
            if (t <= SWAT_STRIKE_TICK) {
                float u = (t - 6.0F) / (SWAT_STRIKE_TICK - 6.0F);
                return lerp(u * u, 1.6F, aim);
            }
            return aim + smooth(SWAT_STRIKE_TICK, SWAT_STRIKE_TICK + 4.0F, t) * 1.5F;
        } else if (s == AttackState.GRAB) {
            if (t <= 4.0F) return lerp(smooth(0.0F, 4.0F, t), 3.0F, 5.0F);
            if (t <= GRAB_CATCH_TICK) return lerp(smooth(4.0F, GRAB_CATCH_TICK, t), 5.0F, 0.9F);
            return 0.9F + smooth(GRAB_CATCH_TICK, GRAB_DURATION, t) * 1.5F;
        } else if (s == AttackState.SMASH) {
            return smashHeight(t);
        }
        return 3.0F;
    }

    /** Height of the held victim's branch tip across SMASH, blocks. It touches the ground on
     *  exactly SMASH_BLOW_A and SMASH_BLOW_B, which is when the entity deals the blows. */
    public static float smashHeight(float t) {
        final float high = 7.4F;
        final float ground = 0.15F;
        if (t <= 12.0F) return lerp(smooth(0.0F, 12.0F, t), 0.9F, high);
        if (t <= SMASH_BLOW_A) {
            float u = (t - 12.0F) / (SMASH_BLOW_A - 12.0F);
            return lerp(u * u, high, ground);
        }
        if (t <= 28.0F) return lerp(smooth(SMASH_BLOW_A + 1.0F, 28.0F, t), ground, high * 0.85F);
        if (t <= SMASH_BLOW_B) {
            float u = (t - 28.0F) / (SMASH_BLOW_B - 28.0F);
            return lerp(u * u, high * 0.85F, ground);
        }
        if (t <= SMASH_RELEASE_TICK) return ground + smooth(SMASH_BLOW_B + 1.0F, SMASH_RELEASE_TICK, t) * 0.6F;
        return 0.75F + smooth(SMASH_RELEASE_TICK, SMASH_DURATION, t) * 2.0F;
    }

    private static float leanAmount(AttackState s, float t) {
        if (s == AttackState.SWEEP) {
            return arc(t / SWEEP_DURATION, 0.55F) * 0.10F;
        } else if (s == AttackState.SLAM) {
            // draws back while the hammer rises, then throws its weight into the blow
            float back = arc(t / SLAM_DURATION, SLAM_RAISED_TICK / (float) SLAM_DURATION) * -0.06F;
            float into = arc(t / SLAM_DURATION, SLAM_STRIKE_TICK / (float) SLAM_DURATION) * 0.15F;
            return t < SLAM_RAISED_TICK ? back : into + back * 0.2F;
        } else if (s == AttackState.SWAT) {
            return -arc(t / SWAT_DURATION, SWAT_STRIKE_TICK / (float) SWAT_DURATION) * 0.08F;
        } else if (s == AttackState.GRAB) {
            return arc(t / GRAB_DURATION, GRAB_CATCH_TICK / (float) GRAB_DURATION) * 0.08F;
        } else if (s == AttackState.SMASH) {
            return 0.05F + (float) Math.sin(t * 0.4F) * 0.02F;
        }
        return 0.0F;
    }

    /** Root heave across ROOT_STOMP: up through the wind-up, slammed flat on the strike tick, a
     *  small rebound after. */
    public static float rootLift(float t) {
        if (t <= STOMP_STRIKE_TICK - 4) return easeOut(t / (STOMP_STRIKE_TICK - 4));
        if (t <= STOMP_STRIKE_TICK) {
            float u = (t - (STOMP_STRIKE_TICK - 4)) / 4.0F;
            return 1.0F - u * u;
        }
        float b = t - STOMP_STRIKE_TICK;
        return b < 6.0F ? (float) Math.sin(b / 6.0F * Math.PI) * 0.12F : 0.0F;
    }

    private static void writhe(float[] rot, int l, float age, float amp, float calm) {
        if (calm <= 0.0F) return;
        float phase = l * 1.7F;
        for (int k = 0; k < SEGS; k++) {
            float travel = age * 0.047F + phase - k * 0.55F;
            float grow = (0.4F + k * 0.3F) * amp * calm;
            int b = LIMB[l][k] * 3;
            rot[b] += (float) Math.cos(travel * 0.72F) * grow * 0.7F;
            rot[b + 1] += (float) Math.sin(travel) * grow * 0.6F;
            rot[b + 2] += (float) Math.sin(travel * 1.3F + 0.8F) * grow * 0.4F;
        }
    }

    /**
     * Tilts {@code bone} so its top moves toward world bearing {@code bearingRad} (relative to the
     * planted yaw) by {@code amount} radians. {@code frameYaw} is the accumulated yaw of the bone's
     * parent frame. Model-space direction of a bearing is (-sin a, -cos a); a positive xRot carries
     * an upward point toward -Z and a positive zRot toward +X.
     */
    private static void leanToward(float[] rot, int bone, float frameYaw, float amount, float bearingRad) {
        double mx = -Math.sin(bearingRad);
        double mz = -Math.cos(bearingRad);
        // into the parent's frame
        double c = Math.cos(-frameYaw), s = Math.sin(-frameYaw);
        double lx = mx * c + mz * s;
        double lz = -mx * s + mz * c;
        rot[bone * 3] += (float) (-amount * lz);
        rot[bone * 3 + 2] += (float) (amount * lx);
    }

    // --- solve -------------------------------------------------------------------------------------

    /**
     * Bends limb {@code l} into a constant-curvature arc whose tip sits {@code height} blocks above
     * the roots, and -- unless {@code kappa} is given -- {@code dist} blocks out from the trunk axis.
     * Writes per-segment pitch deltas (on top of REST) into {@code out}.
     *
     * <p>The arc is two numbers: the shoulder's pitch theta, and kappa, the bend every later segment
     * carries. Turning theta swings the whole arc rigidly about the shoulder, so tip height is
     * monotonic in theta and a bisection finds it exactly; for a fixed height, more bend shortens the
     * reach monotonically, so an outer bisection on kappa finds the distance. Bisection has one
     * answer and it moves continuously with the target, which is the property a Newton solve lacked:
     * it stalled near vertical and hopped between two bends that both reached the tip, and on screen
     * that is a branch teleporting. Fixed iteration counts keep client and server bit-for-bit alike.
     *
     * @param kappa the bend to use, or NaN to solve for it from {@code dist}
     */
    private static void solve(Input in, int l, float[] rot, float dist, float height, float kappa, float[] out) {
        float k;
        if (Float.isNaN(kappa)) {
            // Not every point is reachable by a drooping arc. The steeper the chord, the less the
            // arc may bend before the shoulder runs out of pitch (theta <= THETA_MAX); the less it
            // bends, the longer its chord. Work out exactly what this elevation allows, then slide
            // the target along the ray from the shoulder into that range: the limb still points
            // straight at what it aims for, and the clamp is continuous in the target. Without it
            // the bend pinned at its limit, theta saturated, and the branch jumped mid-swing.
            shoulder(in, l, rot);
            double sr = in.tip[0], sh = in.tip[1];
            double vx = dist - sr, vy = height - sh;
            double c = Math.max(1.0E-3, Math.sqrt(vx * vx + vy * vy));
            double phi = Math.atan2(vy, vx);
            float kLim = KAPPA_MIN;
            if (chord0(in, l, rot, KAPPA_MIN) < phi - THETA_MAX + 0.05) {
                // chord0 falls monotonically as the arc curls
                float lo = KAPPA_MIN, hi = KAPPA_MAX;
                for (int it = 0; it < 14; it++) {
                    float mid = (lo + hi) * 0.5F;
                    if (chord0(in, l, rot, mid) < phi - THETA_MAX + 0.05) lo = mid; else hi = mid;
                }
                kLim = hi;
            }
            double cMin = chordLength(in, l, rot, kLim) * 1.01;
            double cMax = chordLength(in, l, rot, KAPPA_MAX) * 0.995;
            double want = Math.min(Math.max(c, cMin), cMax);
            height = (float) (sh + vy * want / c);
            // The chord's LENGTH depends on the bend alone -- theta only rotates the arc rigidly
            // about the shoulder -- and shrinks monotonically as it curls. So the bend is bisected
            // on chord length, and theta on height after, each monotonic on its own.
            float lo = kLim, hi = KAPPA_MAX;
            for (int it = 0; it < 14; it++) {
                float mid = (lo + hi) * 0.5F;
                if (chordLength(in, l, rot, mid) > want) hi = mid; else lo = mid;
            }
            k = (lo + hi) * 0.5F;
        } else {
            k = kappa;
        }
        float theta = pitchFor(in, l, rot, k, height);
        out[0] = theta - REST[LIMB[l][0] * 3];
        for (int s = 1; s < SEGS; s++) out[s] = k - REST[LIMB[l][s] * 3];
    }

    /** Sets the arc (theta, kappa) on limb {@code l} with theta bisected so the tip is at
     *  {@code height}; returns theta. Leaves the limb posed. */
    private static float pitchFor(Input in, int l, float[] rot, float kappa, float height) {
        for (int s = 1; s < SEGS; s++) rot[LIMB[l][s] * 3] = kappa;
        int s1 = LIMB[l][0] * 3;
        // keep the chord between straight down and straight up, where height is monotonic in theta
        rot[s1] = 0.0F;
        double chord0 = chordAngle(in, l, rot);
        float lo = (float) Math.max(-1.45, -1.50 - chord0);
        float hi = (float) Math.min(THETA_MAX, 1.50 - chord0);
        rot[s1] = lo;
        if (tipHeight(in, l, rot) >= height) return lo;
        rot[s1] = hi;
        if (tipHeight(in, l, rot) <= height) return hi;
        for (int it = 0; it < 16; it++) {
            float mid = (lo + hi) * 0.5F;
            rot[s1] = mid;
            if (tipHeight(in, l, rot) > height) hi = mid; else lo = mid;
        }
        rot[s1] = (lo + hi) * 0.5F;
        return rot[s1];
    }

    /** Shoulder of limb {@code l}: radius from the trunk axis into tip[0], height into tip[1]. */
    private static void shoulder(Input in, int l, float[] rot) {
        int n = fkChain(LIMB_CHAIN[l], rot, in.m);
        local(in.m, (n - SEGS) * 12, 0, 0, 0, in.tip);
        double r = Math.sqrt(sq(in.tip[0]) + sq(in.tip[2])) / 16.0 * MODEL_SCALE;
        double h = (1.501 - in.tip[1] / 16.0) * MODEL_SCALE;
        in.tip[0] = r;
        in.tip[1] = h;
    }

    private static final float KAPPA_MIN = -0.62F;
    private static final float KAPPA_MAX = 0.02F;
    private static final float THETA_MAX = 1.65F;

    /** Chord elevation of limb {@code l} bent at {@code kappa} with the shoulder pitch at zero. */
    private static double chord0(Input in, int l, float[] rot, float kappa) {
        for (int s = 1; s < SEGS; s++) rot[LIMB[l][s] * 3] = kappa;
        rot[LIMB[l][0] * 3] = 0.0F;
        return chordAngle(in, l, rot);
    }

    /** Shoulder-to-tip distance of limb {@code l} bent at {@code kappa}, blocks. */
    private static double chordLength(Input in, int l, float[] rot, float kappa) {
        for (int s = 1; s < SEGS; s++) rot[LIMB[l][s] * 3] = kappa;
        int n = fkChain(LIMB_CHAIN[l], rot, in.m);
        double[] t = in.tip;
        local(in.m, (n - SEGS) * 12, 0, 0, 0, t);
        double sx = t[0], sy = t[1], sz = t[2];
        local(in.m, (n - 1) * 12, 0, 0, LIMB_LEN[l][SEGS - 1], t);
        return Math.sqrt(sq(t[0] - sx) + sq(t[1] - sy) + sq(t[2] - sz)) / 16.0 * MODEL_SCALE;
    }

    private static double tipHeight(Input in, int l, float[] rot) {
        chainTip(in.m, l, rot, in.tip);
        return (1.501 - in.tip[1] / 16.0) * MODEL_SCALE;
    }

    /** Elevation of the shoulder-to-tip chord, radians, positive up. */
    private static double chordAngle(Input in, int l, float[] rot) {
        int n = fkChain(LIMB_CHAIN[l], rot, in.m);
        double[] t = in.tip;
        local(in.m, (n - SEGS) * 12, 0, 0, 0, t);
        double sx = t[0], sy = t[1], sz = t[2];
        local(in.m, (n - 1) * 12, 0, 0, LIMB_LEN[l][SEGS - 1], t);
        double up = -(t[1] - sy);                       // model -Y is up
        double out = Math.sqrt(sq(t[0]) + sq(t[2])) - Math.sqrt(sq(sx) + sq(sz));
        return Math.atan2(up, out);
    }

    private static double sq(double v) {
        return v * v;
    }

    // --- forward kinematics ------------------------------------------------------------------------

    /** Model-space (px) position of the tip of limb {@code l}. */
    private static void chainTip(double[] m, int l, float[] rot, double[] out) {
        int n = fkChain(LIMB_CHAIN[l], rot, m);
        local(m, (n - 1) * 12, 0, 0, LIMB_LEN[l][SEGS - 1], out);
    }

    /** Fills 12 doubles per bone along {@code chain} (3x3 rotation then translation, model px). */
    private static int fkChain(int[] chain, float[] rot, double[] m) {
        for (int i = 0; i < chain.length; i++) {
            compose(i == 0 ? null : m, (i - 1) * 12, chain[i], rot, m, i * 12);
        }
        return chain.length;
    }

    /** m[o..] = parent[po..] * T(OFF[b]) * Rz Ry Rx(rot[b]). parent null means identity. */
    private static void compose(double[] parent, int po, int b, float[] rot, double[] m, int o) {
        double x = rot[b * 3], y = rot[b * 3 + 1], z = rot[b * 3 + 2];
        double cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y);
        double cz = Math.cos(z), sz = Math.sin(z);
        double r00 = cz * cy, r01 = cz * sy * sx - sz * cx, r02 = cz * sy * cx + sz * sx;
        double r10 = sz * cy, r11 = sz * sy * sx + cz * cx, r12 = sz * sy * cx - cz * sx;
        double r20 = -sy, r21 = cy * sx, r22 = cy * cx;
        double ox = OFF[b * 3], oy = OFF[b * 3 + 1], oz = OFF[b * 3 + 2];
        if (parent == null) {
            m[o] = r00; m[o + 1] = r01; m[o + 2] = r02;
            m[o + 3] = r10; m[o + 4] = r11; m[o + 5] = r12;
            m[o + 6] = r20; m[o + 7] = r21; m[o + 8] = r22;
            m[o + 9] = ox; m[o + 10] = oy; m[o + 11] = oz;
            return;
        }
        double p00 = parent[po], p01 = parent[po + 1], p02 = parent[po + 2];
        double p10 = parent[po + 3], p11 = parent[po + 4], p12 = parent[po + 5];
        double p20 = parent[po + 6], p21 = parent[po + 7], p22 = parent[po + 8];
        m[o] = p00 * r00 + p01 * r10 + p02 * r20;
        m[o + 1] = p00 * r01 + p01 * r11 + p02 * r21;
        m[o + 2] = p00 * r02 + p01 * r12 + p02 * r22;
        m[o + 3] = p10 * r00 + p11 * r10 + p12 * r20;
        m[o + 4] = p10 * r01 + p11 * r11 + p12 * r21;
        m[o + 5] = p10 * r02 + p11 * r12 + p12 * r22;
        m[o + 6] = p20 * r00 + p21 * r10 + p22 * r20;
        m[o + 7] = p20 * r01 + p21 * r11 + p22 * r21;
        m[o + 8] = p20 * r02 + p21 * r12 + p22 * r22;
        m[o + 9] = parent[po + 9] + p00 * ox + p01 * oy + p02 * oz;
        m[o + 10] = parent[po + 10] + p10 * ox + p11 * oy + p12 * oz;
        m[o + 11] = parent[po + 11] + p20 * ox + p21 * oy + p22 * oz;
    }

    private static void local(double[] m, int o, double x, double y, double z, double[] out) {
        out[0] = m[o + 9] + m[o] * x + m[o + 1] * y + m[o + 2] * z;
        out[1] = m[o + 10] + m[o + 3] * x + m[o + 4] * y + m[o + 5] * z;
        out[2] = m[o + 11] + m[o + 6] * x + m[o + 7] * y + m[o + 8] * z;
    }

    /**
     * World offsets, in blocks from the entity's position, of limb {@code l}'s seven joints: the
     * six segment pivots and the tip. {@code out} holds 21 doubles. This is LivingEntityRenderer's
     * transform applied to ModelPart's: Ry(180 - yaw), scale(-1, -1, 1), MODEL_SCALE, then
     * translate(0, -1.501, 0).
     */
    public static void limbPoints(Input in, float[] rot, int l, float plantedYaw, double[] out) {
        double[] m = in.m;
        int n = fkChain(LIMB_CHAIN[l], rot, m);
        double[] p = in.tip;
        double phi = Math.toRadians(180.0 - plantedYaw);
        double cp = Math.cos(phi), sp = Math.sin(phi);
        for (int k = 0; k <= SEGS; k++) {
            int o = (n - SEGS + Math.min(k, SEGS - 1)) * 12;
            if (k < SEGS) {
                local(m, o, 0, 0, 0, p);
            } else {
                local(m, o, 0, 0, LIMB_LEN[l][SEGS - 1], p);
            }
            toWorld(p, cp, sp, out, k * 3);
        }
    }

    /** Half thickness of limb {@code l}'s segment {@code k}, blocks. */
    public static double limbRadius(int l, int k) {
        return LIMB_RAD[l][k] / 16.0 * MODEL_SCALE;
    }

    private static void toWorld(double[] p, double cp, double sp, double[] out, int o) {
        double qx = p[0] / 16.0, qy = p[1] / 16.0 - 1.501, qz = p[2] / 16.0;
        double vx = -qx * MODEL_SCALE, vy = -qy * MODEL_SCALE, vz = qz * MODEL_SCALE;
        out[o] = vx * cp + vz * sp;
        out[o + 1] = vy;
        out[o + 2] = -vx * sp + vz * cp;
    }

    /** Full FK for every bone into {@code m} (12 doubles per bone, model px). The model uses it to
     *  hang the curtains plumb; nothing on the server needs it. */
    public static void fkAll(float[] rot, double[] m) {
        for (int b = 0; b < BONES; b++) {
            int p = PARENT[b];
            compose(p < 0 ? null : m, p * 12, b, rot, m, b * 12);
        }
    }

    /** Length of curtain {@code i}, blocks. */
    public static float curtainLength(int i) {
        return CURTAIN_LEN[i] / 16.0F * MODEL_SCALE;
    }

    /** Horizontal reach of limb {@code l} held straight out from its shoulder, blocks. */
    public static float limbChainLength(int l) {
        float s = 0.0F;
        for (int k = 0; k < SEGS; k++) s += LIMB_LEN[l][k];
        return s / 16.0F * MODEL_SCALE;
    }

    // --- curves ------------------------------------------------------------------------------------

    static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static float smooth(float e0, float e1, float x) {
        if (e1 <= e0) return x >= e1 ? 1.0F : 0.0F;
        float u = clamp((x - e0) / (e1 - e0), 0.0F, 1.0F);
        return u * u * (3.0F - 2.0F * u);
    }

    static float easeOut(float u) {
        u = clamp(u, 0.0F, 1.0F);
        return 1.0F - (1.0F - u) * (1.0F - u);
    }

    /** Rise to 1 at {@code peak}, then fall away. */
    static float arc(float p, float peak) {
        p = clamp(p, 0.0F, 1.0F);
        if (p <= peak) {
            float w = peak <= 0.0F ? 1.0F : p / peak;
            return (float) Math.sin(w * Math.PI * 0.5);
        }
        float r = (p - peak) / Math.max(1.0E-4F, 1.0F - peak);
        return (1.0F - r) * (1.0F - r);
    }
}
