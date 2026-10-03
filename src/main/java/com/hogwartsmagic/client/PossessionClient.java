package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.mind.PossessionRules;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;

/**
 * Client half of possession. The caster's client swaps its camera to the victim (setCameraEntity), turns a
 * virtual look with the mouse, zeroes its own body's input, and streams raw keys plus the virtual look to the
 * server every tick on ImperioControlPacket. The victim's client applies the caster's input (PuppetInputPacket)
 * as its own movement and look. Every exit path snaps the camera back: the server's ROLE_NONE, a lapsed
 * keepalive, a level change, the victim entity vanishing, a logout.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PossessionClient {
    private static final float MOUSE_SCALE = 0.15F;
    private static final int IGNORE_AFTER_LOCAL_STOP = 10;

    private static byte role = PossessionRules.ROLE_NONE;
    private static int otherId = -1;
    private static int ticksLeft;
    private static boolean resistOpen;
    private static boolean holdRequired = true;
    private static int ticksSinceState;
    private static int missingTicks;
    private static int ignoreStateTicks;
    @Nullable
    private static ClientLevel stateLevel;
    private static float virtualYaw;
    private static float virtualPitch;
    private static float puppetForward;
    private static float puppetStrafe;
    private static float puppetYaw;
    private static float puppetPitch;
    private static boolean puppetJump;
    /** The victim has received relayed input this session; it is held until ROLE_NONE or the keepalive times out. */
    private static boolean puppetSeen;

    private PossessionClient() {
    }

    // ---- Packets in ----------------------------------------------------------------------------

    public static void acceptState(SpellPackets.PossessionStatePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (packet.role() == PossessionRules.ROLE_NONE || minecraft.player == null) {
            reset();
            return;
        }
        if (ignoreStateTicks > 0) {
            return; // a keepalive that crossed our own stop packet
        }
        if (packet.role() == PossessionRules.ROLE_VICTIM && role != PossessionRules.ROLE_VICTIM) {
            puppetSeen = false;
        }
        boolean becameCaster = packet.role() == PossessionRules.ROLE_CASTER && role != PossessionRules.ROLE_CASTER;
        role = packet.role();
        otherId = packet.otherId();
        ticksLeft = packet.ticksLeft();
        resistOpen = packet.resistOpen();
        holdRequired = packet.holdRequired();
        ticksSinceState = 0;
        stateLevel = minecraft.level;
        if (becameCaster) {
            virtualYaw = minecraft.player.getYRot();
            virtualPitch = minecraft.player.getXRot();
            missingTicks = 0;
        }
    }

    public static void acceptPuppetInput(SpellPackets.PuppetInputPacket packet) {
        if (role != PossessionRules.ROLE_VICTIM) {
            return;
        }
        puppetForward = packet.forward();
        puppetStrafe = packet.strafe();
        puppetJump = packet.jump();
        puppetYaw = packet.yaw();
        puppetPitch = packet.pitch();
        puppetSeen = true;
    }

    // ---- Queries -------------------------------------------------------------------------------

    public static boolean isCaster() {
        return role == PossessionRules.ROLE_CASTER;
    }

    public static boolean isVictim() {
        return role == PossessionRules.ROLE_VICTIM;
    }

    public static int ticksLeft() {
        return ticksLeft;
    }

    public static boolean resistOpen() {
        return resistOpen;
    }

    public static int otherId() {
        return otherId;
    }

    // ---- State ---------------------------------------------------------------------------------

    private static void reset() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.getCameraEntity() != minecraft.player) {
            minecraft.setCameraEntity(minecraft.player);
        }
        role = PossessionRules.ROLE_NONE;
        otherId = -1;
        ticksLeft = 0;
        resistOpen = false;
        holdRequired = true;
        ticksSinceState = 0;
        missingTicks = 0;
        stateLevel = null;
        puppetSeen = false;
    }

    /** The caster lets go (or its view lost the victim): tell the server, snap back, ignore stale keepalives. */
    private static void stopLocally() {
        SpellPackets.CHANNEL.send(new SpellPackets.ImperioControlPacket(false, true, 0.0F, 0.0F, false, false,
                virtualYaw, virtualPitch), PacketDistributor.SERVER.noArg());
        reset();
        ignoreStateTicks = IGNORE_AFTER_LOCAL_STOP;
    }

    /** Called by PossessionTurnMixin for every Entity#turn: true cancels the vanilla turn. */
    public static boolean interceptTurn(Entity entity, double yaw, double pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (role == PossessionRules.ROLE_NONE || entity != minecraft.player) {
            return false;
        }
        if (role == PossessionRules.ROLE_CASTER) {
            virtualYaw += (float) yaw * MOUSE_SCALE;
            virtualPitch = Mth.clamp(virtualPitch + (float) pitch * MOUSE_SCALE, -90.0F, 90.0F);
        }
        return true;
    }

    // ---- Ticks ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            if (role != PossessionRules.ROLE_NONE) {
                drainBodyKeys(minecraft);
                if (role == PossessionRules.ROLE_VICTIM) {
                    minecraft.options.keySprint.setDown(false); // a possessed player can't sprint on their own
                }
            }
            return;
        }
        if (ignoreStateTicks > 0) {
            ignoreStateTicks--;
        }
        if (role == PossessionRules.ROLE_NONE) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.level != stateLevel
                || ++ticksSinceState > PossessionRules.CLIENT_STATE_TIMEOUT) {
            if (role == PossessionRules.ROLE_CASTER && minecraft.player != null) {
                stopLocally();
            } else {
                reset();
            }
            return;
        }
        if (ticksLeft > 0) {
            ticksLeft--;
        }
        if (role == PossessionRules.ROLE_VICTIM && minecraft.player.isSprinting()) {
            minecraft.player.setSprinting(false);
        }
        if (role == PossessionRules.ROLE_CASTER) {
            tickCaster(minecraft);
        }
    }

    /** Runs before vanilla handles keys this tick: neither body drops, swaps, opens its inventory or changes slot. */
    private static void drainBodyKeys(Minecraft minecraft) {
        drain(minecraft.options.keyDrop);
        drain(minecraft.options.keyInventory);
        drain(minecraft.options.keySwapOffhand);
        for (KeyMapping hotbar : minecraft.options.keyHotbarSlots) {
            drain(hotbar);
        }
    }

    private static void drain(KeyMapping key) {
        while (key.consumeClick()) {
            // swallowed
        }
    }

    private static void tickCaster(Minecraft minecraft) {
        Entity victim = minecraft.level.getEntity(otherId);
        if (victim == null || victim.isRemoved()) {
            if (++missingTicks > PossessionRules.CLIENT_STATE_TIMEOUT) {
                stopLocally();
            }
            return;
        }
        missingTicks = 0;
        if (minecraft.getCameraEntity() != victim) {
            minecraft.setCameraEntity(victim);
        }
        if (holdRequired && !minecraft.options.keyUse.isDown()) {
            stopLocally();
            return;
        }
        SpellPackets.CHANNEL.send(new SpellPackets.ImperioControlPacket(false, false, rawForward(minecraft), rawStrafe(minecraft),
                minecraft.options.keyJump.isDown(), minecraft.options.keyAttack.isDown(), virtualYaw, virtualPitch),
                PacketDistributor.SERVER.noArg());
    }

    /** Raw keys: the caster's own movement input is zeroed below, so it can't be read from the player. */
    private static float rawForward(Minecraft minecraft) {
        return (minecraft.options.keyUp.isDown() ? 1.0F : 0.0F) - (minecraft.options.keyDown.isDown() ? 1.0F : 0.0F);
    }

    private static float rawStrafe(Minecraft minecraft) {
        return (minecraft.options.keyLeft.isDown() ? 1.0F : 0.0F) - (minecraft.options.keyRight.isDown() ? 1.0F : 0.0F);
    }

    /** Every frame: the caster's camera looks exactly along the virtual look; a puppet looks where the caster looks. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || role == PossessionRules.ROLE_NONE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (role == PossessionRules.ROLE_CASTER) {
            Entity victim = minecraft.level.getEntity(otherId);
            if (victim != null && minecraft.getCameraEntity() == victim) {
                pin(victim, virtualYaw, virtualPitch);
            }
        } else if (puppetSeen) {
            pin(minecraft.player, puppetYaw, puppetPitch);
        }
    }

    private static void pin(Entity entity, float yaw, float pitch) {
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setXRot(pitch);
        entity.xRotO = pitch;
        if (entity instanceof LivingEntity living) {
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
        }
    }

    // ---- Input ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (role == PossessionRules.ROLE_NONE || event.getEntity() != Minecraft.getInstance().player) {
            return;
        }
        boolean drive = role == PossessionRules.ROLE_VICTIM && puppetSeen;
        float forward = drive ? puppetForward : 0.0F;
        float strafe = drive ? puppetStrafe : 0.0F;
        Input input = event.getInput();
        input.forwardImpulse = forward;
        input.leftImpulse = strafe;
        input.up = forward > 0.0F;
        input.down = forward < 0.0F;
        input.left = strafe > 0.0F;
        input.right = strafe < 0.0F;
        input.jumping = drive && puppetJump;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (role != PossessionRules.ROLE_NONE) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Looking through the victim's eyes, the caster's own hand and wand would float in view. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (role == PossessionRules.ROLE_CASTER) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        ignoreStateTicks = 0;
    }
}
