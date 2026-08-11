package com.hogwartsmagic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers sound events for spells. Each spell has a fixed sound plus a default fallback.
 */
public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ExampleMod.MODID);

    public static final RegistryObject<SoundEvent> SPELL_DEFAULT = register("spell.default");

    public static final RegistryObject<SoundEvent> SPELL_EXPELLIARMUS = register("spell.expelliarmus");
    public static final RegistryObject<SoundEvent> SPELL_LUMOS = register("spell.lumos");
    public static final RegistryObject<SoundEvent> SPELL_ACCIO = register("spell.accio");
    public static final RegistryObject<SoundEvent> SPELL_WINGARDIUM_LEVIOSA = register("spell.wingardium_leviosa");
    public static final RegistryObject<SoundEvent> SPELL_ALOHOMORA = register("spell.alohomora");
    public static final RegistryObject<SoundEvent> SPELL_GLACIUS = register("spell.glacius");
    public static final RegistryObject<SoundEvent> SPELL_CONFRINGO = register("spell.confringo");
    public static final RegistryObject<SoundEvent> SPELL_PROTEGO = register("spell.protego");
    public static final RegistryObject<SoundEvent> SPELL_INCENDIO = register("spell.incendio");
    public static final RegistryObject<SoundEvent> SPELL_STUPEFY = register("spell.stupefy");
    public static final RegistryObject<SoundEvent> SPELL_REVELIO = register("spell.revelio");
    public static final RegistryObject<SoundEvent> SPELL_EXPECTO_PATRONUM = register("spell.expecto_patronum");
    public static final RegistryObject<SoundEvent> SPELL_AVADA_KEDAVRA = register("spell.avada_kedavra");
    public static final RegistryObject<SoundEvent> SPELL_CRUCIO = register("spell.crucio");
    public static final RegistryObject<SoundEvent> SPELL_IMPERIO = register("spell.imperio");
    public static final RegistryObject<SoundEvent> SPELL_FIENDFYRE = register("spell.fiendfyre");
    public static final RegistryObject<SoundEvent> SPELL_SECTUMSEMPRA = register("spell.sectumsempra");
    public static final RegistryObject<SoundEvent> SPELL_MORSMORDRE = register("spell.morsmordre");
    public static final RegistryObject<SoundEvent> SPELL_DOLOHOV_CURSE = register("spell.dolohov_curse");
    public static final RegistryObject<SoundEvent> SPELL_INCARCEROUS = register("spell.incarcerous");
    public static final RegistryObject<SoundEvent> SPELL_FIZZLE = register("spell.fizzle");
    public static final RegistryObject<SoundEvent> SPELL_LEARN = register("spells.learn_spell");
    public static final RegistryObject<SoundEvent> SORTED_SOUND = register("sorted_sound");
    public static final RegistryObject<SoundEvent> WAND_ACHIEVEMENT_SOUND = register("wand_achievement_sound");
    public static final RegistryObject<SoundEvent> MANDRAKE_SCREAM = register("mandrake.scream");

    // Hungarian Horntail dragon (custom SFX)
    public static final RegistryObject<SoundEvent> HORNTAIL_AMBIENT = register("horntail.ambient");
    public static final RegistryObject<SoundEvent> HORNTAIL_ROAR = register("horntail.roar");
    public static final RegistryObject<SoundEvent> HORNTAIL_HURT = register("horntail.hurt");
    public static final RegistryObject<SoundEvent> HORNTAIL_DEATH = register("horntail.death");
    public static final RegistryObject<SoundEvent> HORNTAIL_FLAP = register("horntail.flap");
    public static final RegistryObject<SoundEvent> HORNTAIL_FIRE_BREATH = register("horntail.fire_breath");
    public static final RegistryObject<SoundEvent> HORNTAIL_BITE = register("horntail.bite");
    public static final RegistryObject<SoundEvent> HORNTAIL_TAIL_WHIP = register("horntail.tail_whip");

    // Creature mobs (custom SFX)
    public static final RegistryObject<SoundEvent> ACROMANTULA_AMBIENT = register("acromantula.ambient");
    public static final RegistryObject<SoundEvent> ACROMANTULA_HURT = register("acromantula.hurt");
    public static final RegistryObject<SoundEvent> ACROMANTULA_DEATH = register("acromantula.death");
    public static final RegistryObject<SoundEvent> BASILISK_AMBIENT = register("basilisk.ambient");
    public static final RegistryObject<SoundEvent> BASILISK_AMBIENT2 = register("basilisk.ambient2");
    public static final RegistryObject<SoundEvent> BASILISK_HISS_THREAT = register("basilisk.hiss_threat");
    public static final RegistryObject<SoundEvent> BASILISK_STRIKE = register("basilisk.strike");
    public static final RegistryObject<SoundEvent> BASILISK_SPIT = register("basilisk.spit");
    public static final RegistryObject<SoundEvent> BASILISK_BITE = register("basilisk.bite");
    public static final RegistryObject<SoundEvent> BASILISK_BURROW = register("basilisk.burrow");
    public static final RegistryObject<SoundEvent> BASILISK_ERUPT = register("basilisk.erupt");
    public static final RegistryObject<SoundEvent> BASILISK_GAZE_HEARTBEAT = register("basilisk.gaze_heartbeat");
    public static final RegistryObject<SoundEvent> BASILISK_HURT = register("basilisk.hurt");
    public static final RegistryObject<SoundEvent> BASILISK_DEATH = register("basilisk.death");
    public static final RegistryObject<SoundEvent> TROLL_AMBIENT = register("troll.ambient");
    public static final RegistryObject<SoundEvent> TROLL_HURT = register("troll.hurt");
    public static final RegistryObject<SoundEvent> TROLL_DEATH = register("troll.death");
    public static final RegistryObject<SoundEvent> CENTAUR_AMBIENT = register("centaur.ambient");
    public static final RegistryObject<SoundEvent> CENTAUR_HURT = register("centaur.hurt");
    public static final RegistryObject<SoundEvent> CENTAUR_DEATH = register("centaur.death");
    public static final RegistryObject<SoundEvent> DEMENTOR_AMBIENT = register("dementor.ambient");
    public static final RegistryObject<SoundEvent> DEMENTOR_HURT = register("dementor.hurt");
    public static final RegistryObject<SoundEvent> DEMENTOR_DEATH = register("dementor.death");
    /** The dragging inhale under the approach. */
    public static final RegistryObject<SoundEvent> DEMENTOR_SUCK = register("dementor.suck");
    /** Fires as the hood opens and it lunges. */
    public static final RegistryObject<SoundEvent> DEMENTOR_KISS = register("dementor.kiss");
    /** Driven back by a Patronus. */
    public static final RegistryObject<SoundEvent> DEMENTOR_SHRIEK = register("dementor.shriek");
    public static final RegistryObject<SoundEvent> OWL_AMBIENT = register("owl.ambient");
    public static final RegistryObject<SoundEvent> OWL_HURT = register("owl.hurt");
    public static final RegistryObject<SoundEvent> OWL_DEATH = register("owl.death");
    public static final RegistryObject<SoundEvent> HIPPOGRIFF_AMBIENT = register("hippogriff.ambient");
    public static final RegistryObject<SoundEvent> HIPPOGRIFF_HURT = register("hippogriff.hurt");
    public static final RegistryObject<SoundEvent> HIPPOGRIFF_DEATH = register("hippogriff.death");
    public static final RegistryObject<SoundEvent> HIPPOGRIFF_SCREECH = register("hippogriff.screech");
    public static final RegistryObject<SoundEvent> HIPPOGRIFF_BOW = register("hippogriff.bow");
    public static final RegistryObject<SoundEvent> HIPPOGRIFF_WINGS = register("hippogriff.wings");
    public static final RegistryObject<SoundEvent> HIPPOGRIFF_SNORT = register("hippogriff.snort");
    public static final RegistryObject<SoundEvent> HIPPOGRIFF_TAKEOFF = register("hippogriff.takeoff");
    public static final RegistryObject<SoundEvent> WEREWOLF_AMBIENT = register("werewolf.ambient");
    public static final RegistryObject<SoundEvent> WEREWOLF_HURT = register("werewolf.hurt");
    public static final RegistryObject<SoundEvent> WEREWOLF_DEATH = register("werewolf.death");
    public static final RegistryObject<SoundEvent> WEREWOLF_HOWL = register("werewolf.howl");
    public static final RegistryObject<SoundEvent> PATRONUS_AMBIENT = register("patronus.ambient");
    public static final RegistryObject<SoundEvent> PATRONUS_SHIELD_LOOP = register("patronus.shield_loop");
    public static final RegistryObject<SoundEvent> PATRONUS_SHIELD_REPEL = register("patronus.shield_repel");

    // Item / gear SFX
    public static final RegistryObject<SoundEvent> BROOM_BOOST = register("broom.boost");
    public static final RegistryObject<SoundEvent> BROOM_STALL = register("broom.stall");
    public static final RegistryObject<SoundEvent> CLOAK_VANISH = register("cloak.vanish");
    public static final RegistryObject<SoundEvent> CLOAK_REVEAL = register("cloak.reveal");

    // Quidditch SFX
    public static final RegistryObject<SoundEvent> SNITCH_FLUTTER = register("snitch.flutter");
    public static final RegistryObject<SoundEvent> SNITCH_CATCH = register("snitch.catch");

    // Gringotts goblins
    public static final RegistryObject<SoundEvent> GOBLIN_AMBIENT1 = register("goblin.ambient1");
    public static final RegistryObject<SoundEvent> GOBLIN_AMBIENT2 = register("goblin.ambient2");
    public static final RegistryObject<SoundEvent> GOBLIN_AMBIENT3 = register("goblin.ambient3");
    public static final RegistryObject<SoundEvent> GOBLIN_HURT = register("goblin.hurt");
    public static final RegistryObject<SoundEvent> GOBLIN_DEATH = register("goblin.death");
    public static final RegistryObject<SoundEvent> GOBLIN_TRADE = register("goblin.trade");
    public static final RegistryObject<SoundEvent> GOBLIN_NO = register("goblin.no");
    public static final RegistryObject<SoundEvent> GOBLIN_ANGRY = register("goblin.angry");

    // House elf
    public static final RegistryObject<SoundEvent> HOUSE_ELF_AMBIENT1 = register("house_elf.ambient1");
    public static final RegistryObject<SoundEvent> HOUSE_ELF_AMBIENT2 = register("house_elf.ambient2");
    public static final RegistryObject<SoundEvent> HOUSE_ELF_AMBIENT3 = register("house_elf.ambient3");
    public static final RegistryObject<SoundEvent> HOUSE_ELF_CRACK = register("house_elf.crack");
    public static final RegistryObject<SoundEvent> HOUSE_ELF_HURT = register("house_elf.hurt");
    public static final RegistryObject<SoundEvent> HOUSE_ELF_DEATH = register("house_elf.death");
    public static final RegistryObject<SoundEvent> HOUSE_ELF_BIND = register("house_elf.bind");
    public static final RegistryObject<SoundEvent> HOUSE_ELF_FREE = register("house_elf.free");
    public static final RegistryObject<SoundEvent> HOUSE_ELF_PUNISH = register("house_elf.punish");

    // Giant
    public static final RegistryObject<SoundEvent> GIANT_AMBIENT1 = register("giant.ambient1");
    public static final RegistryObject<SoundEvent> GIANT_AMBIENT2 = register("giant.ambient2");
    public static final RegistryObject<SoundEvent> GIANT_ANGRY = register("giant.angry");
    public static final RegistryObject<SoundEvent> GIANT_ROAR = register("giant.roar");
    public static final RegistryObject<SoundEvent> GIANT_STEP = register("giant.step");
    public static final RegistryObject<SoundEvent> GIANT_SWIPE = register("giant.swipe");
    public static final RegistryObject<SoundEvent> GIANT_KICK_SMASH = register("giant.kick_smash");
    public static final RegistryObject<SoundEvent> GIANT_GRAB = register("giant.grab");
    public static final RegistryObject<SoundEvent> GIANT_THROW = register("giant.throw");
    public static final RegistryObject<SoundEvent> GIANT_HURT = register("giant.hurt");
    public static final RegistryObject<SoundEvent> GIANT_DEATH = register("giant.death");

    // Merpeople of the Black Lake. Mermish sounds like screeching above the
    // water and like a real song below it, so the two are separate clips rather
    // than one played at different pitches.
    public static final RegistryObject<SoundEvent> MERPERSON_AMBIENT = register("merperson.ambient");
    public static final RegistryObject<SoundEvent> MERPERSON_CALL = register("merperson.call");
    public static final RegistryObject<SoundEvent> MERPERSON_SCREECH = register("merperson.screech");
    public static final RegistryObject<SoundEvent> MERPERSON_SONG = register("merperson.song");
    public static final RegistryObject<SoundEvent> MERPERSON_SPEAR = register("merperson.spear");
    public static final RegistryObject<SoundEvent> MERPERSON_HURT = register("merperson.hurt");
    public static final RegistryObject<SoundEvent> MERPERSON_DEATH = register("merperson.death");

    // Grindylows: small, wet and vicious. Nothing here is deep or heavy.
    public static final RegistryObject<SoundEvent> GRINDYLOW_AMBIENT = register("grindylow.ambient");
    public static final RegistryObject<SoundEvent> GRINDYLOW_SHRIEK = register("grindylow.shriek");
    public static final RegistryObject<SoundEvent> GRINDYLOW_GRAB = register("grindylow.grab");
    public static final RegistryObject<SoundEvent> GRINDYLOW_SNAP = register("grindylow.snap");
    public static final RegistryObject<SoundEvent> GRINDYLOW_HURT = register("grindylow.hurt");
    public static final RegistryObject<SoundEvent> GRINDYLOW_DEATH = register("grindylow.death");

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
