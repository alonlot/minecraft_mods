package com.hogwartsmagic.cosy.client.card;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A hands-free look at the living cards for development: start the client with {@code -Dhogwartsmagic.cardDemo=true}
 * in a world with cheats on, and once the player is in it builds a wall of framed cards in front of them, puts Merlin
 * in their hand and plays a reveal, so every way a card is drawn can be checked without touching the keyboard.
 * Off unless the property is set; it only sends ordinary commands as the player.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CardDemo {
    private static final boolean ON = Boolean.getBoolean("hogwartsmagic.cardDemo");
    private static int ticks;

    private CardDemo() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            ticks = 0;
            return;
        }
        ticks++;
        if (ticks == 40) {
            cmd(mc, "gamerule doDaylightCycle false");
            cmd(mc, "time set noon");
            cmd(mc, "weather clear");
            cmd(mc, "gamemode creative");
            cmd(mc, "kill @e[type=minecraft:creeper]");
            cmd(mc, "kill @e[type=minecraft:item_frame,distance=..10]");
            cmd(mc, "kill @e[type=minecraft:glow_item_frame,distance=..10]");
            cmd(mc, "tp @s ~ ~ ~ 0 30");
        } else if (ticks == 60) {
            cmd(mc, "fill ~-4 ~-1 ~1 ~4 ~3 ~4 minecraft:air");
            cmd(mc, "fill ~-4 ~-1 ~4 ~4 ~3 ~4 minecraft:spruce_planks");
            // cards 2 (Dumbledore, legendary), 1 (Merlin, legendary), 9 (Circe), 5 (Ravenclaw, rare) on the wall
            frame(mc, "~-2 ~1 ~3", 2, true);
            frame(mc, "~ ~1 ~3", 9, false);
            frame(mc, "~2 ~1 ~3", 5, false);
            frame(mc, "~-1 ~2 ~3", 1, false);
            cmd(mc, "item replace entity @s weapon.mainhand with hogwartsmagic:wizard_card[hogwartsmagic:card_id=4]");
        } else if (ticks >= 100 && (ticks - 100) % 900 == 0) {
            // a reveal every 45 s: Merlin (legendary), Ravenclaw (rare), Circe (common), round and round
            int[] cycle = {1, 5, 9};
            CardRevealClient.offer(cycle[((ticks - 100) / 900) % cycle.length]);
        }
    }

    private static void frame(Minecraft mc, String pos, int card, boolean glow) {
        cmd(mc, "summon minecraft:" + (glow ? "glow_item_frame" : "item_frame") + " " + pos
                + " {Facing:2b,Fixed:1b,Item:{id:\"hogwartsmagic:wizard_card\",count:1,components:{\"hogwartsmagic:card_id\":"
                + card + "}}}");
    }

    private static void cmd(Minecraft mc, String command) {
        mc.player.connection.sendCommand(command);
    }
}
