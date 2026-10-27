package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The other viewers of a recorded memory, drawn as pulsing silver ghosts with no name tag. The server sends the
 * list ({@code PensieveGhostsPacket}); a real player in it is drawn here instead of by the vanilla renderer. The
 * local player is never one.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class SilverGhosts {
    private static final float R = 0.80f;
    private static final float G = 0.86f;
    private static final float B = 0.95f;
    private static final int MOTE_EVERY = 10;
    private static final int MOTES = 3;

    private static Set<UUID> ghosts = Set.of();

    private SilverGhosts() {
    }

    public static void set(List<UUID> list) {
        ghosts = list == null || list.isEmpty() ? Set.of() : new HashSet<>(list);
    }

    static void clear() {
        ghosts = Set.of();
    }

    public static boolean isGhost(Player p) {
        return p != null && !ghosts.isEmpty() && !p.isLocalPlayer() && ghosts.contains(p.getUUID());
    }

    /** The ghost's translucency, pulsing slowly. */
    static float alpha(float clock) {
        return 0.35f + 0.08f * Mth.sin(clock * 0.15f);
    }

    /** The player's own model, drawn full-bright in translucent silver. {@code poseStack} is at the player's feet. */
    public static void render(AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffer, int light,
                              float partial) {
        EntityRenderer<?> er = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if (!(er instanceof PlayerRenderer renderer)) return;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        float bodyRot = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float headRot = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
        float pitch = Mth.lerp(partial, player.xRotO, player.getXRot());
        float walkSpeed = Math.min(1f, player.walkAnimation.speed(partial));
        float walkPos = player.walkAnimation.position(partial);
        float age = player.tickCount + partial;

        model.setAllVisible(true);
        model.young = false;
        model.riding = player.isPassenger();
        model.crouching = player.isCrouching();
        model.attackTime = player.getAttackAnim(partial);
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        model.prepareMobModel(player, walkPos, walkSpeed, partial);
        model.setupAnim(player, walkPos, walkSpeed, age, headRot - bodyRot, pitch);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - bodyRot));
        poseStack.scale(-1f, -1f, 1f);
        poseStack.scale(0.9375f, 0.9375f, 0.9375f);
        poseStack.translate(0f, -1.501f, 0f);
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(player.getSkin().texture()));
        int a = Math.round(Mth.clamp(alpha(PensieveClientState.clock(partial)), 0f, 1f) * 255f);
        int color = a << 24 | Math.round(R * 255) << 16 | Math.round(G * 255) << 8 | Math.round(B * 255);
        model.renderToBuffer(poseStack, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        poseStack.popPose();
    }

    /** A few silver motes drifting off each ghost, every {@link #MOTE_EVERY} ticks. */
    static void tick() {
        if (ghosts.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        for (UUID id : ghosts) {
            Player p = level.getPlayerByUUID(id);
            if (p == null || p.isLocalPlayer() || (p.tickCount + id.hashCode()) % MOTE_EVERY != 0) continue;
            for (int i = 0; i < MOTES; i++) {
                double x = p.getX() + (level.random.nextDouble() - 0.5) * 0.7;
                double y = p.getY() + level.random.nextDouble() * 1.8;
                double z = p.getZ() + (level.random.nextDouble() - 0.5) * 0.7;
                net.minecraft.client.particle.Particle mote = mc.particleEngine.createParticle(ParticleTypes.END_ROD, x, y, z,
                        0, 0.01 + level.random.nextDouble() * 0.015, 0);
                if (mote == null) continue;
                mote.setColor(R, G, B);
                mc.particleEngine.add(mote);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player) || !isGhost(player)) return;
        event.setCanceled(true);
        render(player, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                event.getPartialTick());
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (isGhost(event.getEntity() instanceof Player p ? p : null)) event.setResult(Event.Result.DENY);
    }
}
