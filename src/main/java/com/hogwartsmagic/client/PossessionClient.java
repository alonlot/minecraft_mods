package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.spell.mind.PossessionRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

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
    private static long snapshotTick;
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
    private static int resistPresses;
    private static int resistTimer;
    @Nullable
    private static SpellPackets.PuppetSnapshotPacket snapshot;
    private static int displayedSlot;

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

    public static void acceptSnapshot(SpellPackets.PuppetSnapshotPacket packet) {
        if (role != PossessionRules.ROLE_CASTER || packet.victimId() != otherId) {
            return;
        }
        snapshot = packet;
        Minecraft now = Minecraft.getInstance();
        snapshotTick = now.level == null ? 0L : now.level.getGameTime();
        displayedSlot = packet.selectedSlot();
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

    /** Client game time at which the current snapshot arrived (the wheel ages its cooldowns from it). */
    public static long snapshotTick() {
        return snapshotTick;
    }

    @Nullable
    public static SpellPackets.PuppetSnapshotPacket snapshot() {
        return snapshot;
    }

    public static int displayedSlot() {
        return displayedSlot;
    }

    /** An icon for a hotbar entry of the snapshot (item id only). */
    public static ItemStack stackFor(String id, int count) {
        if (id == null || id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(key).map(item -> new ItemStack(item, Math.max(1, count))).orElse(ItemStack.EMPTY);
    }

    private static void selectPuppetSlot(int slot) {
        if (snapshot == null || !PossessionRules.validHotbarSlot(slot)) {
            return;
        }
        displayedSlot = slot; // shown at once; the next snapshot confirms it
        SpellPackets.CHANNEL.send(new SpellPackets.PossessionHotbarPacket(slot), PacketDistributor.SERVER.noArg());
    }

    /** The wheel key while possessing: the puppet's spells, but only while they hold their wand. */
    public static void openPuppetWheel(Minecraft minecraft) {
        if (snapshot == null || !snapshot.holdsWand()) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".puppet.no_wand"), true);
            }
            return;
        }
        minecraft.setScreen(new PuppetSpellWheelScreen(snapshot));
    }

    /** The spell-slot cast keys while possessing cast the puppet's wand slots. */
    public static void handlePuppetSlotKeys(KeyMapping[] keys) {
        for (int slot = 0; slot < keys.length; slot++) {
            while (keys[slot].consumeClick()) {
                if (snapshot != null && slot < snapshot.wandSlots().size() && !snapshot.wandSlots().get(slot).isEmpty()) {
                    castPuppetSpell(snapshot.wandSlots().get(slot));
                }
            }
        }
    }

    /** The use key's physical state, polled from GLFW so a wheel screen (which releases mappings) cannot fake a release. */
    public static boolean isUseHeld(Minecraft minecraft) {
        KeyMapping use = minecraft.options.keyUse;
        if (use.isDown()) {
            return true;
        }
        long window = minecraft.getWindow().getWindow();
        com.mojang.blaze3d.platform.InputConstants.Key key = use.getKey();
        int value = key.getValue();
        if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, value) == GLFW.GLFW_PRESS;
        }
        if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
            return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, value);
        }
        return false;
    }

    public static void castPuppetSpell(String spellId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (role != PossessionRules.ROLE_CASTER || snapshot == null || minecraft.player == null) {
            return;
        }
        if (!snapshot.holdsWand()) {
            minecraft.player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".puppet.no_wand"), true);
            return;
        }
        if (PossessionRules.isPuppetBlocked(spellId)) {
            minecraft.player.displayClientMessage(Component.translatable("gui." + ExampleMod.MODID + ".puppet.blocked"), true);
            return;
        }
        SpellPackets.CHANNEL.send(new SpellPackets.PossessionCastPacket(spellId), PacketDistributor.SERVER.noArg());
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
        resistPresses = 0;
        resistTimer = 0;
        snapshot = null;
        displayedSlot = 0;
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
                    // A possessed player can't sprint on their own. Toggle-sprint ignores setDown(false): flip it off instead.
                    if (minecraft.options.keySprint.isDown()) {
                        minecraft.options.keySprint.setDown(minecraft.options.toggleSprint().get());
                    }
                }
            }
            if (role == PossessionRules.ROLE_VICTIM) {
                // Drain only: holding SPACE repeats KeyMapping.click, so presses are counted from the raw key events.
                while (minecraft.options.keyJump.consumeClick()) {
                    // swallowed
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
        } else {
            reportResist();
        }
    }

    /** Twice a second while resisting is open: send the presses since the last report. */
    private static void reportResist() {
        if (++resistTimer < PossessionRules.RESIST_WINDOW_TICKS) {
            return;
        }
        resistTimer = 0;
        if (resistOpen && resistPresses > 0) {
            SpellPackets.CHANNEL.send(new SpellPackets.PossessionResistPacket(resistPresses), PacketDistributor.SERVER.noArg());
        }
        resistPresses = 0;
    }

    /** Runs before vanilla handles keys this tick: neither body drops, swaps, opens its inventory or changes slot. */
    private static void drainBodyKeys(Minecraft minecraft) {
        drain(minecraft.options.keyDrop);
        drain(minecraft.options.keyInventory);
        drain(minecraft.options.keySwapOffhand);
        for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length; slot++) {
            while (minecraft.options.keyHotbarSlots[slot].consumeClick()) {
                if (role == PossessionRules.ROLE_CASTER) {
                    selectPuppetSlot(slot); // the caster picks the puppet's slot, never their own
                }
            }
        }
    }

    /** A real SPACE press (never the key repeat of a hold) counts as one mash while the resist window is open. */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (role == PossessionRules.ROLE_VICTIM && resistOpen && minecraft.screen == null
                && event.getAction() == GLFW.GLFW_PRESS && minecraft.options.keyJump.matches(event.getKey(), event.getScanCode())) {
            resistPresses++;
        }
    }

    /** The same for a jump key bound to a mouse button. */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (role == PossessionRules.ROLE_VICTIM && resistOpen && minecraft.screen == null
                && event.getAction() == GLFW.GLFW_PRESS && minecraft.options.keyJump.matchesMouse(event.getButton())) {
            resistPresses++;
        }
    }

    /** The caster's scroll wheel turns the puppet's hotbar (same direction as vanilla scrolling). */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (role != PossessionRules.ROLE_CASTER || Minecraft.getInstance().screen != null) {
            return;
        }
        event.setCanceled(true);
        int step = (int) Math.signum(event.getDeltaY());
        if (step != 0) {
            selectPuppetSlot(Math.floorMod(displayedSlot - step, 9));
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
        // Closing the puppet wheel releases mouse-bound key mappings (KeyMapping.setAll only restores keyboard keys):
        // trust the physical state, and re-press the mapping once the wheel is gone.
        boolean held = isUseHeld(minecraft);
        if (held && minecraft.screen == null && !minecraft.options.keyUse.isDown()) {
            minecraft.options.keyUse.setDown(true);
        }
        if (holdRequired && !held) {
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
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
