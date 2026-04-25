package com.hogwartsmagic.client.model;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * ~10s human→werewolf morph: fingers, then hands, arms, legs, torso, head, tail (staggered from the fast {@code idle_to_wolf} clip).
 * Regenerate with {@code build_scripts/gen_slow_wolf_morph.py} if that clip changes.
 */
public final class WhulvkLycanSlowHumanToWolfAnimations {
	public static final AnimationDefinition slow_human_to_wolf = AnimationDefinition.Builder.withLength(10.0F)
		.addAnimation("root", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(9F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("body", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(5.0084F, KeyframeAnimations.degreeVec(-90.0F, 0.0F, -90.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(8.5F, KeyframeAnimations.degreeVec(-90.0F, 65.0F, -90.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(8.5F, KeyframeAnimations.posVec(0.0F, -8.25F, 10.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("tail", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(10F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("tighLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(-9.5772F, -6.0681F, -77.9634F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("tighFurLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -12.5F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("legLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(-36.2461F, -17.0471F, 62.7342F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("legFurLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 10.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 15.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("shankLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(0.0F, 15.0F, -50.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("shankLeft", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(6.8F, KeyframeAnimations.posVec(0.25F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("footLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("tighRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(9.5772F, 6.0681F, -77.9634F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("tighFurRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -12.5F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("legRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(36.2461F, 17.047F, 62.7342F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("legFurRIght", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 15.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("shankRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(0.0F, -15.0F, -50.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("shankRight", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(6.8F, KeyframeAnimations.posVec(0.25F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("footRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(3.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(6.8F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("lowerBody", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(8.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("middleBody", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(8.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 2.5F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("upperBody", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(8.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("chestRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(8.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 2.5F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("leftChest", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(8.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 2.5F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("armLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(2F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(5F, KeyframeAnimations.degreeVec(1.8526F, -12.3718F, -83.221F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("upperArmLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(2F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(5F, KeyframeAnimations.degreeVec(20.2679F, 15.8247F, -2.691F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("lowerArmLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(2F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(3.7999F, KeyframeAnimations.degreeVec(-5.7638F, -66.0721F, -22.6276F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(5F, KeyframeAnimations.degreeVec(-116.2675F, -85.2851F, 87.6368F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("LowerArmFurLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(2F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -17.5F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("handLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.8F, KeyframeAnimations.degreeVec(-73.039F, 0.2053F, 7.6118F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("fingerLeft_2", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("upperFingerLeft_2", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("lowerFingerLeft_2", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("fingerLeft_5", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(5.5232F, -43.3948F, 12.5438F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("upperFingerLeft_5", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("lowerFingerLeft_5", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("fingerLeft_3", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("upperFingerLeft_3", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("lowerFingerLeft_3", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("fingerLeft_4", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("upperFingerLeft_4", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("lowerFingerLeft_4", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("finger_6", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(16.2966F, 44.2157F, -10.4187F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("upperFinger_6", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("armRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(2F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(5F, KeyframeAnimations.degreeVec(-1.8526F, 12.3719F, -83.221F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("upperArmRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(2F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(5F, KeyframeAnimations.degreeVec(-20.2679F, -15.8246F, -2.691F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("lowerArmRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(2F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(3.7999F, KeyframeAnimations.degreeVec(-5.7638F, 66.0721F, -22.6276F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(5F, KeyframeAnimations.degreeVec(116.2675F, 85.2851F, 87.6368F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("lowerArmFurRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(2F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -17.5F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("handRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.8F, KeyframeAnimations.degreeVec(70.539F, -0.2053F, 7.6118F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("fingerRight_7", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("upperFinger_7", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("lowerFinger_6", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("fingerRight_8", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-5.5232F, 43.3948F, 12.5438F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("upperFinger_8", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("lowerFinger_7", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(7.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("fingerRight_9", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("upperFinger_9", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("lowerFinger_8", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("fingerRight_10", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("upperFinger_10", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-15.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("lowerFinger_9", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("fingerRight_11", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-16.2966F, -44.2157F, -10.4187F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("upperFinger_11", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.2F, KeyframeAnimations.degreeVec(-25.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("lowerNeck", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -27.5F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("upperNeck", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -7.5F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -25.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("headFurLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(0.0F, -10.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("headFurRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(0.0F, 10.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("headFurTop", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 10.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("earLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(0.0F, 5.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("earRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(0.0F, -5.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("eyebrowLeft", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("eyebrowRight", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(-5.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(5.0F, 0.0F, 5.0F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("jaw", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, -2.5F), AnimationChannel.Interpolations.CATMULLROM)
		))
		.addAnimation("tongue", new AnimationChannel(AnimationChannel.Targets.ROTATION, 
			new Keyframe(6.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(9.5F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.addAnimation("tongue", new AnimationChannel(AnimationChannel.Targets.POSITION, 
			new Keyframe(6.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(9.5F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
		))
		.build();
}
