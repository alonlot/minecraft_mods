package com.hogwartsmagic.event;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.OwlModel;
import com.hogwartsmagic.client.OwlRenderer;
import com.hogwartsmagic.client.BroomModel;
import com.hogwartsmagic.client.BroomRenderer;
import com.hogwartsmagic.client.CaveInimicumClientState;
import com.hogwartsmagic.client.HorcruxClientState;
import com.hogwartsmagic.client.InvisibilityCloakClientState;
import com.hogwartsmagic.client.PolyjuiceClientState;
import com.hogwartsmagic.client.WandRadialMenuScreen;
import com.hogwartsmagic.client.WandWorkbenchRenderer;
import com.hogwartsmagic.client.WizardModel;
import com.hogwartsmagic.client.WizardRenderer;
import com.hogwartsmagic.client.StudentRenderer;
import com.hogwartsmagic.client.SpellBeamRenderer;
import com.hogwartsmagic.client.FiendfyreSerpentRenderer;
import com.hogwartsmagic.client.model.FlyingMobModel;
import com.hogwartsmagic.client.model.GillyweedGillsModel;
import com.hogwartsmagic.client.model.HippogriffModel;
import com.hogwartsmagic.client.FlyingMobRenderer;
import com.hogwartsmagic.client.model.AcromantulaModel;
import com.hogwartsmagic.client.model.BasiliskModel;
import com.hogwartsmagic.client.renderer.AcromantulaRenderer;
import com.hogwartsmagic.client.renderer.BasiliskRenderer;
import com.hogwartsmagic.client.renderer.DarkMarkRenderer;
import com.hogwartsmagic.client.model.DementorModel;
import com.hogwartsmagic.client.renderer.DementorRenderer;
import com.hogwartsmagic.client.renderer.GillyweedGillsLayer;
import com.hogwartsmagic.client.model.PatronusModel;
import com.hogwartsmagic.client.model.SortingHatArmorModel;
import com.hogwartsmagic.client.renderer.HorcruxSoulRenderer;
import com.hogwartsmagic.client.renderer.PatronusRenderer;
import com.hogwartsmagic.client.renderer.PetrifiedRemainsRenderer;
import com.hogwartsmagic.client.renderer.HippogriffRenderer;
import com.hogwartsmagic.client.model.WhulvkWerewolfLycanModel;
import com.hogwartsmagic.client.model.MinatorModel;
import com.hogwartsmagic.client.renderer.LycanthropeVillagerRenderer;
import com.hogwartsmagic.client.renderer.MinatorRenderer;
import com.hogwartsmagic.client.model.TrollModel;
import com.hogwartsmagic.client.renderer.TrollRenderer;
import com.hogwartsmagic.client.model.HungarianHorntailModel;
import com.hogwartsmagic.client.renderer.HungarianHorntailRenderer;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.entity.StudentModel;
import com.hogwartsmagic.entity.FiendfyreEntity;
import com.hogwartsmagic.entity.FiendfyreSerpentEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import com.mojang.blaze3d.platform.InputConstants;
import com.hogwartsmagic.spell.SpellType;
import net.minecraftforge.network.PacketDistributor;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.client.sounds.BroomFlightSoundInstance;
import com.hogwartsmagic.client.sounds.GringottsCartSoundInstance;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    private static boolean wasBoosting = false;
    private static boolean wasJumpDownForTrick = false;
    private static boolean wasRadialDown = false;
    private static boolean wasProtegoMaximaChannelKeyDown = false;
    private static boolean protegoMaximaChannelActive = false;
    private static boolean fiendfyreHoldPending = false;
    private static boolean fiendfyreChannelActive = false;
    private static int fiendfyreHoldTicks = 0;
    private static final int FIENDFYRE_HOLD_THRESHOLD_TICKS = 8;
    private static boolean wingardiumHoldPending = false;
    private static boolean wingardiumChannelActive = false;
    private static boolean patronusHoldPending = false;
    private static boolean patronusChannelActive = false;
    private static boolean imperioHoldPending = false;
    private static boolean imperioChannelActive = false;
    private static int imperioHoldTicks = 0;
    private static final int IMPERIO_HOLD_THRESHOLD_TICKS = 40;
    private static final KeyMapping WAND_RADIAL_MENU_KEY = new KeyMapping(
            "key.hogwartsmagic.wand_radial_menu",
            GLFW.GLFW_KEY_R,
            "key.categories.hogwartsmagic"
    );
    private static final KeyMapping GRIMOIRE_KEY = new KeyMapping(
            "key.hogwartsmagic.grimoire",
            GLFW.GLFW_KEY_G,
            "key.categories.hogwartsmagic"
    );
    private static boolean wasGrimoireDown = false;
    private static final KeyMapping[] SPELL_SLOT_KEYS = createSpellSlotKeys();

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {

        @Nullable
        private static BroomFlightSoundInstance broomFlightSound;
        private static GringottsCartSoundInstance cartRumbleSound;
        private static GringottsCartSoundInstance cartScreechSound;

        /**
         * The ride camera, and the most important comfort call in the whole feature.
         *
         * <p>The broom couples camera roll straight to its bank and gets away with it because a
         * broom bank is over in a second. A cart on a helix holds twenty-five degrees for forty
         * seconds, and coupling that straight through would pin the horizon at a permanent tilt,
         * which is a textbook way to make somebody ill.
         *
         * <p>So the camera follows the <b>change</b> in bank instead: {@code cartRollWashed} chases
         * the real bank with a two-second lag, and what the camera shows is mostly the gap between
         * them plus a small steady residue. Entering a spiral you lurch to the cap, it washes out
         * to about five degrees within two seconds, and leaving it you get an equal and opposite
         * lurch. Turns get felt at their boundaries, which is how the inner ear actually works.
         */
        private static float cartRollWashed;
        private static float cartCameraRoll;
        /** Hard cap. Past about twenty degrees the horizon stops being a horizon. */
        private static final float CART_ROLL_CAP = 18.0F;
        /** Client-tracked decaying counter for the boost FOV punch (ticks remaining of bonus FOV). */
        private static int boostFovPunchTicks = 0;
        /** Set in RenderLivingEvent.Pre so the Post handler knows whether it pushed a pose. */
        private static boolean trickPushedPose = false;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null || mc.player == null) {
                    CaveInimicumClientState.clear();
                    HorcruxClientState.clear();
                }
                HorcruxClientState.tick(mc);
                // Sprint/boost packets first so nothing in the broom sound path can prevent them from sending.
                if (mc.player != null && mc.player.getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity broom) {
                    // Respect stamina lock: if broom is overheated, suppress boost input client-side
                    // so the player doesn't keep re-triggering against a locked engine.
                    boolean isBoosting = mc.options.keySprint.isDown() && broom.canBoost();
                    if (isBoosting != wasBoosting) {
                        wasBoosting = isBoosting;
                        SpellPackets.CHANNEL.send(new SpellPackets.BroomBoostPacket(isBoosting), PacketDistributor.SERVER.noArg());
                        if (isBoosting) {
                            // Brief FOV punch on boost-on transition; decays over ~14 ticks.
                            boostFovPunchTicks = 14;
                        }
                    }

                    // Trick input: jump key transitions onto-down.
                    // Strafe (xxa != 0) → barrel roll in that direction; pure forward → loop.
                    boolean jumpDown = mc.options.keyJump.isDown();
                    if (jumpDown && !wasJumpDownForTrick) {
                        byte trick = com.hogwartsmagic.entity.BroomEntity.TRICK_NONE;
                        float xxa = mc.player.xxa;
                        float zza = mc.player.zza;
                        if (xxa > 0.3F) {
                            trick = com.hogwartsmagic.entity.BroomEntity.TRICK_BARREL_RIGHT;
                        } else if (xxa < -0.3F) {
                            trick = com.hogwartsmagic.entity.BroomEntity.TRICK_BARREL_LEFT;
                        } else if (zza > 0.3F) {
                            trick = com.hogwartsmagic.entity.BroomEntity.TRICK_LOOP;
                        }
                        if (trick != com.hogwartsmagic.entity.BroomEntity.TRICK_NONE) {
                            SpellPackets.CHANNEL.send(new SpellPackets.BroomTrickPacket(trick), PacketDistributor.SERVER.noArg());
                        }
                    }
                    wasJumpDownForTrick = jumpDown;
                } else {
                    wasBoosting = false;
                    wasJumpDownForTrick = false;
                }
                if (boostFovPunchTicks > 0) {
                    boostFovPunchTicks--;
                }

                boolean radialDown = InputConstants.isKeyDown(
                        mc.getWindow().getWindow(),
                        WAND_RADIAL_MENU_KEY.getKey().getValue()
                );
                if (mc.player != null) {
                    boolean holdingMainWand = MagicWandItem.isAnyWand(mc.player.getMainHandItem());

                    if (radialDown && !wasRadialDown && holdingMainWand && !(mc.screen instanceof WandRadialMenuScreen) && mc.screen == null) {
                        mc.setScreen(new WandRadialMenuScreen(mc.player));
                    }

                    if (!radialDown && wasRadialDown && mc.screen instanceof WandRadialMenuScreen radialScreen) {
                        radialScreen.confirmSelectionAndClose();
                    }
                }
                wasRadialDown = radialDown;

                boolean grimoireDown = InputConstants.isKeyDown(
                        mc.getWindow().getWindow(),
                        GRIMOIRE_KEY.getKey().getValue()
                );
                if (grimoireDown && !wasGrimoireDown && mc.player != null && mc.screen == null) {
                    mc.setScreen(new com.hogwartsmagic.client.SpellCompendiumScreen(mc.player));
                }
                wasGrimoireDown = grimoireDown;

                if (mc.player != null && mc.screen == null) {
                    handleSpellSlotKeybinds(mc);
                }

                if (mc.player != null) {
                    boolean useDown = mc.options.keyUse.isDown();
                    boolean holdingWand = MagicWandItem.isAnyWand(mc.player.getMainHandItem());
                    com.hogwartsmagic.spell.SpellType selected = holdingWand
                            ? MagicWandItem.getSelectedSpell(mc.player.getMainHandItem())
                            : null;
                    boolean protegoMaximaSkyCast = holdingWand
                            && selected == com.hogwartsmagic.spell.SpellType.PROTEGO_MAXIMA
                            && mc.player.getXRot() <= -55.0F;
                    boolean fiendfyreSelected = holdingWand
                            && selected == com.hogwartsmagic.spell.SpellType.FIENDFYRE;
                    boolean wingardiumSelected = holdingWand
                            && selected == com.hogwartsmagic.spell.SpellType.WINGARDIUM_LEVIOSA;
                    boolean imperioSelected = holdingWand
                            && selected == com.hogwartsmagic.spell.SpellType.IMPERIO;
                    boolean patronusSelected = holdingWand
                            && selected == com.hogwartsmagic.spell.SpellType.EXPECTO_PATRONUM;

                    if (protegoMaximaChannelActive && (!useDown || !protegoMaximaSkyCast)) {
                        SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, true, false, false), PacketDistributor.SERVER.noArg());
                        protegoMaximaChannelActive = false;
                    } else if (protegoMaximaChannelActive && useDown && protegoMaximaSkyCast) {
                        SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, true, false), PacketDistributor.SERVER.noArg());
                    }

                    wasProtegoMaximaChannelKeyDown = useDown && protegoMaximaSkyCast;

                    if (fiendfyreHoldPending && (!holdingWand || !fiendfyreSelected)) {
                        if (fiendfyreChannelActive) {
                            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, true, false, false), PacketDistributor.SERVER.noArg());
                        }
                        fiendfyreHoldPending = false;
                        fiendfyreChannelActive = false;
                        fiendfyreHoldTicks = 0;
                    } else if (fiendfyreHoldPending) {
                        boolean attackDown = mc.options.keyAttack.isDown();
                        if (useDown) {
                            fiendfyreHoldTicks++;
                            if (!fiendfyreChannelActive && fiendfyreHoldTicks >= FIENDFYRE_HOLD_THRESHOLD_TICKS) {
                                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, true, attackDown), PacketDistributor.SERVER.noArg());
                                fiendfyreChannelActive = true;
                                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            } else if (fiendfyreChannelActive) {
                                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, true, attackDown), PacketDistributor.SERVER.noArg());
                            }
                        } else {
                            if (fiendfyreChannelActive) {
                                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, true, false, false), PacketDistributor.SERVER.noArg());
                            } else {
                                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, false, false), PacketDistributor.SERVER.noArg());
                                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            }
                            fiendfyreHoldPending = false;
                            fiendfyreChannelActive = false;
                            fiendfyreHoldTicks = 0;
                        }
                    }

                    if (wingardiumHoldPending && (!holdingWand || !wingardiumSelected)) {
                        if (wingardiumChannelActive) {
                            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, true, false, false), PacketDistributor.SERVER.noArg());
                        }
                        wingardiumHoldPending = false;
                        wingardiumChannelActive = false;
                    } else if (wingardiumHoldPending) {
                        if (useDown) {
                            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, true, false), PacketDistributor.SERVER.noArg());
                            if (!wingardiumChannelActive) {
                                wingardiumChannelActive = true;
                                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            }
                        } else {
                            if (wingardiumChannelActive) {
                                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, true, false, false), PacketDistributor.SERVER.noArg());
                            }
                            wingardiumHoldPending = false;
                            wingardiumChannelActive = false;
                        }
                    }

                    // Expecto Patronum is held: the shield lives only as long as the key is down.
                    if (patronusHoldPending && (!holdingWand || !patronusSelected)) {
                        if (patronusChannelActive) {
                            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, true, false, false), PacketDistributor.SERVER.noArg());
                        }
                        patronusHoldPending = false;
                        patronusChannelActive = false;
                    } else if (patronusHoldPending) {
                        if (useDown) {
                            SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, false, true, false), PacketDistributor.SERVER.noArg());
                            if (!patronusChannelActive) {
                                patronusChannelActive = true;
                                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            }
                        } else {
                            if (patronusChannelActive) {
                                SpellPackets.CHANNEL.send(new SpellPackets.CastSpellPacket(false, true, false, false), PacketDistributor.SERVER.noArg());
                            }
                            patronusHoldPending = false;
                            patronusChannelActive = false;
                        }
                    }

                    if (imperioHoldPending && (!holdingWand || !imperioSelected)) {
                        if (imperioChannelActive) {
                            SpellPackets.CHANNEL.send(new SpellPackets.ImperioControlPacket(false, true, 0.0f, 0.0f, false, false, mc.player.getYRot(), mc.player.getXRot()), PacketDistributor.SERVER.noArg());
                        }
                        imperioHoldPending = false;
                        imperioChannelActive = false;
                        imperioHoldTicks = 0;
                    } else if (imperioHoldPending) {
                        float forward = mc.player.zza;
                        float strafe = mc.player.xxa;
                        boolean jumpDown = mc.options.keyJump.isDown();
                        boolean attackDown = mc.options.keyAttack.isDown();
                        if (useDown) {
                            imperioHoldTicks++;
                            if (!imperioChannelActive && imperioHoldTicks >= IMPERIO_HOLD_THRESHOLD_TICKS) {
                                SpellPackets.CHANNEL.send(new SpellPackets.ImperioControlPacket(true, false, forward, strafe, jumpDown, attackDown, mc.player.getYRot(), mc.player.getXRot()), PacketDistributor.SERVER.noArg());
                                imperioChannelActive = true;
                                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                            } else if (imperioChannelActive) {
                                SpellPackets.CHANNEL.send(new SpellPackets.ImperioControlPacket(false, false, forward, strafe, jumpDown, attackDown, mc.player.getYRot(), mc.player.getXRot()), PacketDistributor.SERVER.noArg());
                            }
                        } else {
                            if (imperioChannelActive) {
                                SpellPackets.CHANNEL.send(new SpellPackets.ImperioControlPacket(false, true, 0.0f, 0.0f, false, false, mc.player.getYRot(), mc.player.getXRot()), PacketDistributor.SERVER.noArg());
                            }
                            imperioHoldPending = false;
                            imperioChannelActive = false;
                            imperioHoldTicks = 0;
                        }
                    }
                } else {
                    wasProtegoMaximaChannelKeyDown = false;
                    protegoMaximaChannelActive = false;
                    fiendfyreHoldPending = false;
                    fiendfyreChannelActive = false;
                    fiendfyreHoldTicks = 0;
                    wingardiumHoldPending = false;
                    wingardiumChannelActive = false;
                    patronusHoldPending = false;
                    patronusChannelActive = false;
                    imperioHoldPending = false;
                    imperioChannelActive = false;
                    imperioHoldTicks = 0;
                }

                if (mc.player != null && mc.player.getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity) {
                    if (broomFlightSound == null || broomFlightSound.isStopped()) {
                        broomFlightSound = new BroomFlightSoundInstance(mc.player);
                        mc.getSoundManager().play(broomFlightSound);
                    }
                } else if (broomFlightSound != null) {
                    if (!broomFlightSound.isStopped()) {
                        mc.getSoundManager().stop(broomFlightSound);
                    }
                    broomFlightSound = null;
                }

                if (mc.player != null
                        && mc.player.getVehicle() instanceof com.hogwartsmagic.entity.GringottsCartEntity cart) {
                    float bank = cart.displayRoll;
                    cartRollWashed += (bank - cartRollWashed) * 0.035F;
                    cartCameraRoll = net.minecraft.util.Mth.clamp(
                            (bank - cartRollWashed) * 1.6F + bank * 0.18F,
                            -CART_ROLL_CAP, CART_ROLL_CAP);
                    if (cartRumbleSound == null || cartRumbleSound.isStopped()) {
                        cartRumbleSound = new GringottsCartSoundInstance(mc.player, false);
                        mc.getSoundManager().play(cartRumbleSound);
                    }
                    if (cartScreechSound == null || cartScreechSound.isStopped()) {
                        cartScreechSound = new GringottsCartSoundInstance(mc.player, true);
                        mc.getSoundManager().play(cartScreechSound);
                    }
                } else {
                    cartRollWashed = 0.0F;
                    cartCameraRoll = 0.0F;
                    if (cartRumbleSound != null) {
                        if (!cartRumbleSound.isStopped()) {
                            mc.getSoundManager().stop(cartRumbleSound);
                        }
                        cartRumbleSound = null;
                    }
                    if (cartScreechSound != null) {
                        if (!cartScreechSound.isStopped()) {
                            mc.getSoundManager().stop(cartScreechSound);
                        }
                        cartScreechSound = null;
                    }
                }

                if (mc.level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
                    for (net.minecraft.client.player.AbstractClientPlayer p : clientLevel.players()) {
                        PolyjuiceClientState.advanceSurrogateOneGameTick(p);
                    }
                }
                InvisibilityCloakClientState.tick();
            }
        }

        @SubscribeEvent
        public static void onRenderGui(net.minecraftforge.client.event.CustomizeGuiOverlayEvent.Chat event) {
            com.hogwartsmagic.client.ManaOverlay.render(
                event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight()
            );
            com.hogwartsmagic.client.BroomHudOverlay.render(
                event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight()
            );
        }

        @SubscribeEvent
        public static void onComputeFov(net.minecraftforge.client.event.ComputeFovModifierEvent event) {
            if (event.getPlayer().getVehicle()
                    instanceof com.hogwartsmagic.entity.GringottsCartEntity cart) {
                // Straight speed, not the broom's boost-punch: a cart is fast the whole way down
                // and what you want is the tunnel widening under you, not a kick at the top.
                double f = cart.getSpeed() / com.hogwartsmagic.entity.GringottsCartEntity.maxSpeed();
                event.setNewFovModifier(1.0F + (float) (Math.max(0.0D, f) * 0.22D
                        * com.hogwartsmagic.Config.CART_CAMERA_FOV.get()));
                return;
            }
            if (event.getPlayer().getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity broom) {
                // Determine horizontal speed
                float horizontalSpeed = (float) broom.getDeltaMovement().horizontalDistance();
                // Base modifier is 1.0. We want to scale FOV up as speed reaches 10.0 (Firebolt boost).
                // Max FOV modifier addition: ~0.5 at 10.0 speed.
                float modifier = 1.0F + (horizontalSpeed * 0.04F);
                // Brief punch on boost-on transition — decays linearly over 14 ticks.
                if (boostFovPunchTicks > 0) {
                    modifier += 0.18F * (boostFovPunchTicks / 14.0F);
                }
                // The minimum should be 1.0
                event.setNewFovModifier(Math.max(1.0F, modifier));
            }
        }

        @SubscribeEvent
        public static void onRenderLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
            // Skip players here — RenderPlayerEvent handles them so we don't double-push.
            if (event.getEntity() instanceof net.minecraft.world.entity.player.Player) {
                return;
            }
            applyTrickPosePre(event.getEntity(), event.getPartialTick(), event.getPoseStack());
        }

        @SubscribeEvent
        public static void onRenderLivingPost(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> event) {
            if (event.getEntity() instanceof net.minecraft.world.entity.player.Player) {
                return;
            }
            applyTrickPosePost(event.getPoseStack());
        }

        @SubscribeEvent
        public static void onRenderPlayerPreTrick(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
            applyTrickPosePre(event.getEntity(), event.getPartialTick(), event.getPoseStack());
        }

        @SubscribeEvent
        public static void onRenderPlayerPostTrick(net.minecraftforge.client.event.RenderPlayerEvent.Post event) {
            applyTrickPosePost(event.getPoseStack());
        }

        private static void applyTrickPosePre(net.minecraft.world.entity.LivingEntity ent, float partialTick, com.mojang.blaze3d.vertex.PoseStack pose) {
            trickPushedPose = false;
            if (!(ent.getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity broom)) {
                return;
            }
            byte trick = broom.getTrickType();
            if (trick == com.hogwartsmagic.entity.BroomEntity.TRICK_NONE) {
                return;
            }
            int duration = com.hogwartsmagic.entity.BroomEntity.trickDurationFor(trick);
            float lerpTicks = net.minecraft.util.Mth.lerp(partialTick, broom.prevTrickTicks, broom.getTrickTicksSynced());
            float progress = 1.0F - net.minecraft.util.Mth.clamp(lerpTicks / duration, 0.0F, 1.0F);
            float spinDeg = progress * 360.0F;
            float lerpBroomYaw = net.minecraft.util.Mth.lerp(partialTick, broom.yRotO, broom.getYRot());

            // Pivot is at the broom center, which is ~0.45 BELOW the rider's feet in rider-local space
            // (passenger attachment is (0, +0.45, 0) on the broom, so rider feet are 0.45 above the broom).
            // Standard "rotate around point p in broom-aligned frame":
            //   T(p) * R_y(-broomYaw) * R_trick * R_y(+broomYaw) * T(-p)
            // For p = (0, -0.45, 0), PoseStack calls go in matrix order, so we issue them as:
            //   translate(p), mulPose(R_y(-yaw)), mulPose(R_trick), mulPose(R_y(+yaw)), translate(-p).
            pose.pushPose();
            trickPushedPose = true;
            pose.translate(0.0D, -0.45D, 0.0D);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-lerpBroomYaw));
            // BroomRenderer applies its trick rotation INSIDE a scale(-1, -1, 1) mirror so
            // its Z/X axis spins look "correct" on the mirrored mesh. The player model is
            // rendered without that mirror, so using the same signs here makes the player
            // roll opposite to the broom. Negate them so player and broom rotate together.
            if (trick == com.hogwartsmagic.entity.BroomEntity.TRICK_BARREL_LEFT) {
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(spinDeg));
            } else if (trick == com.hogwartsmagic.entity.BroomEntity.TRICK_BARREL_RIGHT) {
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-spinDeg));
            } else if (trick == com.hogwartsmagic.entity.BroomEntity.TRICK_LOOP) {
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-spinDeg));
            }
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(lerpBroomYaw));
            pose.translate(0.0D, 0.45D, 0.0D);
        }

        private static void applyTrickPosePost(com.mojang.blaze3d.vertex.PoseStack pose) {
            if (trickPushedPose) {
                pose.popPose();
                trickPushedPose = false;
            }
        }

        @SubscribeEvent
        public static void onComputeCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            if (mc.player.getVehicle() instanceof com.hogwartsmagic.entity.GringottsCartEntity) {
                event.setRoll(event.getRoll()
                        + cartCameraRoll * com.hogwartsmagic.Config.CART_CAMERA_ROLL.get().floatValue());
                return;
            }
            if (!(mc.player.getVehicle() instanceof com.hogwartsmagic.entity.BroomEntity broom)) {
                return;
            }
            // Match the renderer's lerp so the camera banks in lockstep with the broom mesh.
            float partial = (float) event.getPartialTick();
            float bankDeg = net.minecraft.util.Mth.lerp(partial, broom.prevDisplayRoll, broom.displayRoll);
            // Damp slightly so first-person feels less nauseating than the visual model bank.
            event.setRoll(event.getRoll() + bankDeg * 0.55F);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
            // Polyjuice surrogates use negative entity ids and are rendered via this handler's
            // manual renderer.render call. PlayerRenderer fires Pre again for that entity — if we
            // hijack again we recurse until stack overflow (especially when disguising as yourself).
            if (event.getEntity().getId() < 0) {
                return;
            }
            if (InvisibilityCloakClientState.shouldHidePlayerCompletely(event.getEntity())) {
                event.setCanceled(true);
                return;
            }
            if (!PolyjuiceClientState.hasDisguise(event.getEntity().getUUID())) {
                return;
            }
            // Cancel vanilla player render first — avoids one-frame flicker if surrogate setup lags.
            event.setCanceled(true);

            net.minecraft.world.entity.Entity surrogate = PolyjuiceClientState.getOrCreateSurrogate(event.getEntity(), event.getPartialTick());
            if (surrogate == null) {
                return;
            }

            @SuppressWarnings("unchecked")
            net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.Entity> renderer =
                    (net.minecraft.client.renderer.entity.EntityRenderer<net.minecraft.world.entity.Entity>)
                            Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(surrogate);
            if (renderer == null) {
                return;
            }

            renderer.render(surrogate, surrogate.getYRot(), event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onRenderPlayerCloakTransitionPre(RenderPlayerEvent.Pre event) {
            if (event.isCanceled()) {
                return;
            }
            if (!(event.getEntity() instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer)) {
                return;
            }
            if (!InvisibilityCloakClientState.isAnimating(clientPlayer)) {
                return;
            }
            InvisibilityCloakClientState.VisibilityMask mask =
                    InvisibilityCloakClientState.getVisibilityMask(clientPlayer, event.getPartialTick());
            InvisibilityCloakClientState.applyToModel(event.getRenderer().getModel(), mask);
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onRenderPlayerCloakTransitionPost(RenderPlayerEvent.Post event) {
            if (!(event.getEntity() instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer)) {
                return;
            }
            if (!InvisibilityCloakClientState.isAnimating(clientPlayer)) {
                return;
            }
            InvisibilityCloakClientState.resetModel(event.getRenderer().getModel());
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && InvisibilityCloakClientState.shouldHidePlayerCompletely(minecraft.player)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onRenderArm(net.minecraftforge.client.event.RenderArmEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && InvisibilityCloakClientState.shouldHidePlayerCompletely(minecraft.player)) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onScreenInit(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {
            HorcruxClientState.applyButtonLock(event);
        }

        @SubscribeEvent
        public static void onScreenOpening(net.minecraftforge.client.event.ScreenEvent.Opening event) {
            if (!(event.getNewScreen() instanceof net.minecraft.client.gui.screens.DeathScreen) && HorcruxClientState.isRespawnUnlocked()) {
                HorcruxClientState.clear();
            }
        }

        @SubscribeEvent
        public static void onScreenRender(net.minecraftforge.client.event.ScreenEvent.Render.Post event) {
            HorcruxClientState.renderOverlay(event);
        }
    }

    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ExampleMod.WAND_WORKBENCH_MENU.get(), com.hogwartsmagic.client.WandWorkbenchScreen::new);
            MenuScreens.register(ExampleMod.BROOM_WORKBENCH_MENU.get(), com.hogwartsmagic.client.BroomWorkbenchScreen::new);
            ItemBlockRenderTypes.setRenderLayer(ExampleMod.FINE_WOOD_SAPLING.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ExampleMod.FINE_WOOD_LEAVES.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ExampleMod.HORCRUX.get(), RenderType.cutout());

            net.minecraftforge.client.settings.KeyConflictContext context = net.minecraftforge.client.settings.KeyConflictContext.IN_GAME;
            WAND_RADIAL_MENU_KEY.setKeyConflictContext(context);
            GRIMOIRE_KEY.setKeyConflictContext(context);
            for (KeyMapping spellSlotKey : SPELL_SLOT_KEYS) {
                spellSlotKey.setKeyConflictContext(context);
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(WAND_RADIAL_MENU_KEY);
        event.register(GRIMOIRE_KEY);
        for (KeyMapping spellSlotKey : SPELL_SLOT_KEYS) {
            event.register(spellSlotKey);
        }
    }

    private static KeyMapping[] createSpellSlotKeys() {
        KeyMapping[] mappings = new KeyMapping[MagicWandItem.SPELL_SLOT_COUNT];
        for (int i = 0; i < mappings.length; i++) {
            mappings[i] = new KeyMapping(
                    "key.hogwartsmagic.cast_spell_slot_" + (i + 1),
                    InputConstants.UNKNOWN.getValue(),
                    "key.categories.hogwartsmagic"
            );
        }
        return mappings;
    }

    private static void handleSpellSlotKeybinds(Minecraft mc) {
        ItemStack wand = mc.player.getMainHandItem();
        if (!MagicWandItem.isAnyWand(wand)) {
            wand = mc.player.getOffhandItem();
        }
        if (!MagicWandItem.isAnyWand(wand)) {
            drainSpellSlotClicks();
            return;
        }

        for (int slot = 0; slot < SPELL_SLOT_KEYS.length; slot++) {
            while (SPELL_SLOT_KEYS[slot].consumeClick()) {
                SpellType spell = MagicWandItem.getSpellSlot(wand, slot);
                if (spell != null) {
                    com.hogwartsmagic.client.ModEventsClientHooks.selectSpellAndCast(mc.player, spell);
                }
            }
        }
    }

    private static void drainSpellSlotClicks() {
        for (KeyMapping spellSlotKey : SPELL_SLOT_KEYS) {
            while (spellSlotKey.consumeClick()) {
                // Drain queued clicks while no wand is held so they do not fire later.
            }
        }
    }

    public static KeyMapping getSpellSlotKeyMapping(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= SPELL_SLOT_KEYS.length) {
            return null;
        }
        return SPELL_SLOT_KEYS[slotIndex];
    }

    public static void setProtegoMaximaChannelActive(boolean active) {
        protegoMaximaChannelActive = active;
    }

    public static void startFiendfyreHold() {
        if (!fiendfyreHoldPending) {
            fiendfyreHoldPending = true;
            fiendfyreChannelActive = false;
            fiendfyreHoldTicks = 0;
        }
    }

    public static void startWingardiumHold() {
        if (!wingardiumHoldPending) {
            wingardiumHoldPending = true;
            wingardiumChannelActive = false;
        }
    }

    /** Returns true only on the tick the hold actually begins, so the caster swings once. */
    public static boolean startPatronusHold() {
        if (patronusHoldPending) {
            return false;
        }
        patronusHoldPending = true;
        patronusChannelActive = false;
        return true;
    }

    public static void startImperioHold() {
        if (!imperioHoldPending) {
            imperioHoldPending = true;
            imperioChannelActive = false;
            imperioHoldTicks = 0;
        }
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) ->
                        level != null && pos != null
                                ? BiomeColors.getAverageFoliageColor(level, pos)
                                : FoliageColor.getDefaultColor(),
                ExampleMod.FINE_WOOD_LEAVES.get());
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> event.getBlockColors()
                        .getColor(ExampleMod.FINE_WOOD_LEAVES.get().defaultBlockState(), null, null, tintIndex),
                ExampleMod.FINE_WOOD_LEAVES_ITEM.get());
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(OwlModel.LAYER_LOCATION, OwlModel::createBodyLayer);
        event.registerLayerDefinition(HippogriffModel.LAYER_LOCATION, HippogriffModel::createBodyLayer);
        event.registerLayerDefinition(FlyingMobModel.LAYER_LOCATION, FlyingMobModel::createBodyLayer);
        event.registerLayerDefinition(BroomModel.LAYER_LOCATION, BroomModel::createBodyLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.model.GringottsCartModel.LAYER,
                com.hogwartsmagic.client.model.GringottsCartModel::createBodyLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.BroomModelV2.TRAINING_LAYER, com.hogwartsmagic.client.BroomModelV2::createTrainingLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.BroomModelV2.NIMBUS_LAYER, com.hogwartsmagic.client.BroomModelV2::createNimbusLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.BroomModelV2.FIREBOLT_LAYER, com.hogwartsmagic.client.BroomModelV2::createFireboltLayer);
        event.registerLayerDefinition(WizardModel.LAYER_LOCATION, WizardModel::createBodyLayer);
        event.registerLayerDefinition(StudentModel.LAYER_LOCATION, StudentModel::createBodyLayer);
        event.registerLayerDefinition(AcromantulaModel.LAYER_LOCATION, AcromantulaModel::createBodyLayer);
        event.registerLayerDefinition(BasiliskModel.LAYER_LOCATION, BasiliskModel::createBodyLayer);
        event.registerLayerDefinition(DementorModel.LAYER_LOCATION, DementorModel::createBodyLayer);
        event.registerLayerDefinition(PatronusModel.LAYER_LOCATION, PatronusModel::createBodyLayer);
        event.registerLayerDefinition(SortingHatArmorModel.LAYER_LOCATION, SortingHatArmorModel::createBodyLayer);
        event.registerLayerDefinition(MinatorModel.LAYER_LOCATION, MinatorModel::createBodyLayer);
        event.registerLayerDefinition(TrollModel.LAYER_LOCATION, TrollModel::createBodyLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.model.GiantModel.LAYER_LOCATION,
                com.hogwartsmagic.client.model.GiantModel::createBodyLayer);
        event.registerLayerDefinition(HungarianHorntailModel.LAYER_LOCATION, HungarianHorntailModel::createBodyLayer);
        event.registerLayerDefinition(WhulvkWerewolfLycanModel.LAYER_LOCATION, WhulvkWerewolfLycanModel::createBodyLayer);
        event.registerLayerDefinition(GillyweedGillsModel.LAYER_LOCATION, GillyweedGillsModel::createBodyLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.GoldenSnitchModel.LAYER_LOCATION, com.hogwartsmagic.client.GoldenSnitchModel::createBodyLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.model.GoblinModel.LAYER_LOCATION, com.hogwartsmagic.client.model.GoblinModel::createBodyLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.model.HouseElfModel.LAYER_LOCATION, com.hogwartsmagic.client.model.HouseElfModel::createBodyLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.model.MerpersonModel.LAYER_LOCATION,
                com.hogwartsmagic.client.model.MerpersonModel::createBodyLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.model.MerchieftainessCrownModel.LAYER_LOCATION,
                com.hogwartsmagic.client.model.MerchieftainessCrownModel::createBodyLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.model.GrindylowModel.LAYER_LOCATION,
                com.hogwartsmagic.client.model.GrindylowModel::createBodyLayer);
        event.registerLayerDefinition(com.hogwartsmagic.client.model.WhompingWillowModel.LAYER_LOCATION,
                com.hogwartsmagic.client.model.WhompingWillowModel::createBodyLayer);
        // Named LAYER, not LAYER_LOCATION like its neighbours -- the statue's model is emitted by
        // build_scripts/generate_living_statue_model.py, which uses its own naming.
        event.registerLayerDefinition(com.hogwartsmagic.client.LivingStatueModel.LAYER,
                com.hogwartsmagic.client.LivingStatueModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skinName : event.getSkins()) {
            PlayerRenderer renderer = event.getPlayerSkin(skinName);
            if (renderer != null) {
                renderer.addLayer(new GillyweedGillsLayer(renderer, event.getEntityModels()));
            }
        }
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ExampleMod.GRINGOTTS_VAULT_DOOR_BLOCK_ENTITY.get(),
                com.hogwartsmagic.client.GringottsVaultDoorRenderer::new);
        event.registerBlockEntityRenderer(ExampleMod.WAND_WORKBENCH_BLOCK_ENTITY.get(), WandWorkbenchRenderer::new);
        event.registerBlockEntityRenderer(ExampleMod.BROOM_WORKBENCH_BLOCK_ENTITY.get(), com.hogwartsmagic.client.BroomWorkbenchRenderer::new);

        event.registerEntityRenderer(ModEntities.OWL.get(), OwlRenderer::new);
        event.registerEntityRenderer(ModEntities.HIPPOGRIFF.get(), HippogriffRenderer::new);
        event.registerEntityRenderer(ModEntities.FLYING_MOB.get(), FlyingMobRenderer::new);
        event.registerEntityRenderer(ModEntities.SPELL_BEAM.get(), SpellBeamRenderer::new);
        event.registerEntityRenderer(ModEntities.UKRAINIAN_IRONBELLY.get(),
                com.hogwartsmagic.client.renderer.UkrainianIronbellyRenderer::new);
        event.registerEntityRenderer(ModEntities.GRINGOTTS_CART.get(),
                com.hogwartsmagic.client.GringottsCartRenderer::new);
        event.registerEntityRenderer(ModEntities.BROOM.get(), BroomRenderer::new);
        event.registerEntityRenderer(ModEntities.WIZARD.get(), WizardRenderer::new);
        event.registerEntityRenderer(ModEntities.VOLDEMORT.get(), context -> new WizardRenderer<com.hogwartsmagic.entity.VoldemortEntity>(context));
        event.registerEntityRenderer(ModEntities.STUDENT.get(), StudentRenderer::new);
        event.registerEntityRenderer(ModEntities.ACROMANTULA.get(), AcromantulaRenderer::new);
        event.registerEntityRenderer(ModEntities.BASILISK.get(), BasiliskRenderer::new);
        event.registerEntityRenderer(ModEntities.BASILISK_VENOM.get(),
                context -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(context, 1.4F, false));
        event.registerEntityRenderer(ModEntities.DEMENTOR.get(), DementorRenderer::new);
        event.registerEntityRenderer(ModEntities.PATRONUS.get(), PatronusRenderer::new);
        event.registerEntityRenderer(ModEntities.MINATOR.get(), MinatorRenderer::new);
        event.registerEntityRenderer(ModEntities.TROLL.get(), TrollRenderer::new);
        event.registerEntityRenderer(ModEntities.GIANT.get(),
                com.hogwartsmagic.client.renderer.GiantRenderer::new);
        event.registerEntityRenderer(ModEntities.HUNGARIAN_HORNTAIL.get(), HungarianHorntailRenderer::new);
        event.registerEntityRenderer(ModEntities.PETRIFIED_REMAINS.get(), PetrifiedRemainsRenderer::new);
        event.registerEntityRenderer(ModEntities.HORCRUX_SOUL.get(), HorcruxSoulRenderer::new);
        event.registerEntityRenderer(ModEntities.LYCANTHROPE_VILLAGER.get(), LycanthropeVillagerRenderer::new);
        event.registerEntityRenderer(ModEntities.GOLDEN_SNITCH.get(), com.hogwartsmagic.client.GoldenSnitchRenderer::new);
        event.registerEntityRenderer(ModEntities.GOBLIN.get(),
                context -> new com.hogwartsmagic.client.GoblinRenderer<com.hogwartsmagic.entity.GoblinEntity>(
                        context, com.hogwartsmagic.client.GoblinRenderer.TELLER_TEXTURE));
        event.registerEntityRenderer(ModEntities.GOBLIN_GUARD.get(),
                context -> new com.hogwartsmagic.client.GoblinRenderer<com.hogwartsmagic.entity.GoblinGuardEntity>(
                        context, com.hogwartsmagic.client.GoblinRenderer.GUARD_TEXTURE));
        event.registerEntityRenderer(ModEntities.HOUSE_ELF.get(),
                com.hogwartsmagic.client.renderer.HouseElfRenderer::new);
        event.registerEntityRenderer(ModEntities.MERPERSON.get(),
                com.hogwartsmagic.client.renderer.MerpersonRenderer::new);
        event.registerEntityRenderer(ModEntities.GRINDYLOW.get(),
                com.hogwartsmagic.client.renderer.GrindylowRenderer::new);
        event.registerEntityRenderer(ModEntities.WHOMPING_WILLOW.get(),
                com.hogwartsmagic.client.renderer.WhompingWillowRenderer::new);
        event.registerEntityRenderer(ModEntities.LIVING_STATUE.get(),
                com.hogwartsmagic.client.LivingStatueRenderer::new);

        event.registerEntityRenderer(ModEntities.DARK_MARK.get(), DarkMarkRenderer::new);
        event.registerEntityRenderer(ModEntities.VOLDEMORT_SUMMONING.get(),
                context -> new EntityRenderer<com.hogwartsmagic.entity.VoldemortSummoningEntity>(context) {
                    @Override
                    public ResourceLocation getTextureLocation(com.hogwartsmagic.entity.VoldemortSummoningEntity entity) {
                        return TextureAtlas.LOCATION_BLOCKS;
                    }
                });
        event.registerEntityRenderer(ModEntities.FIENDFYRE_SERPENT.get(), FiendfyreSerpentRenderer::new);
        
        event.registerEntityRenderer(ModEntities.FIENDFYRE.get(),
                context -> new EntityRenderer<FiendfyreEntity>(context) {
                    @Override
                    public ResourceLocation getTextureLocation(FiendfyreEntity entity) {
                        return TextureAtlas.LOCATION_BLOCKS;
                    }
                });
    }

}
