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
    public static final RegistryObject<SoundEvent> AVADA_CHARGE = register("spell.avada_kedavra.charge");
    public static final RegistryObject<SoundEvent> AVADA_CHARGE_SHORT = register("spell.avada_kedavra.charge_short");
    public static final RegistryObject<SoundEvent> AVADA_SNUFF = register("spell.avada_kedavra.snuff");
    public static final RegistryObject<SoundEvent> AVADA_IMPACT = register("spell.avada_kedavra.impact");
    public static final RegistryObject<SoundEvent> SPELL_CLASH = register("spell.clash");
    public static final RegistryObject<SoundEvent> SPELL_CLASH_GRIND = register("spell.clash_grind");
    public static final RegistryObject<SoundEvent> STING_PRIORI = register("music.sting_priori");
    public static final RegistryObject<SoundEvent> STING_DREAD = register("music.sting_dread");
    public static final RegistryObject<SoundEvent> PRIORI_LOOP = register("spell.priori_loop");
    public static final RegistryObject<SoundEvent> DEMENTOR_DREAD = register("dementor.dread");
    public static final RegistryObject<SoundEvent> APPARATE_SQUEEZE = register("spell.apparate_squeeze");
    public static final RegistryObject<SoundEvent> APPARATE_CRACK = register("spell.apparate_crack");
    public static final RegistryObject<SoundEvent> STING_DUEL = register("music.sting_duel");
    public static final RegistryObject<SoundEvent> STING_BOSS_DARK = register("music.sting_boss_dark");
    public static final RegistryObject<SoundEvent> STING_BOSS_SERPENT = register("music.sting_boss_serpent");
    public static final RegistryObject<SoundEvent> STING_BOSS_DRAGON = register("music.sting_boss_dragon");
    public static final RegistryObject<SoundEvent> STING_VICTORY = register("music.sting_victory");
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
    /** The first-contact bellow, before it has swung at anything. */
    public static final RegistryObject<SoundEvent> TROLL_ROAR = register("troll.roar");
    /** One-way latch when it drops below the rage threshold. */
    public static final RegistryObject<SoundEvent> TROLL_RAGE = register("troll.rage");
    /** Straining grunt while the club goes up over its head. */
    public static final RegistryObject<SoundEvent> TROLL_SLAM_WINDUP = register("troll.slam_windup");
    /** The club landing on the ground, and everything that comes loose with it. */
    public static final RegistryObject<SoundEvent> TROLL_SLAM_IMPACT = register("troll.slam_impact");
    /** The club going through the air on a horizontal swing. */
    public static final RegistryObject<SoundEvent> TROLL_SWEEP = register("troll.sweep");
    /** The club connecting with something soft. */
    public static final RegistryObject<SoundEvent> TROLL_CLUB_HIT = register("troll.club_hit");
    public static final RegistryObject<SoundEvent> TROLL_STEP = register("troll.step");
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

    // Humanoid casters. No recordings yet: sounds.json maps these to pitched vanilla illager/villager/warden events.
    public static final RegistryObject<SoundEvent> WIZARD_AMBIENT = register("wizard.ambient");
    public static final RegistryObject<SoundEvent> WIZARD_HURT = register("wizard.hurt");
    public static final RegistryObject<SoundEvent> WIZARD_DEATH = register("wizard.death");
    public static final RegistryObject<SoundEvent> STUDENT_AMBIENT = register("student.ambient");
    public static final RegistryObject<SoundEvent> STUDENT_HURT = register("student.hurt");
    public static final RegistryObject<SoundEvent> STUDENT_DEATH = register("student.death");
    public static final RegistryObject<SoundEvent> VOLDEMORT_AMBIENT = register("voldemort.ambient");
    public static final RegistryObject<SoundEvent> VOLDEMORT_HURT = register("voldemort.hurt");
    public static final RegistryObject<SoundEvent> VOLDEMORT_DEATH = register("voldemort.death");

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

    // Inferius: drowned corpses of the locket cave. Wet, laboured, never a voice.
    public static final RegistryObject<SoundEvent> INFERIUS_AMBIENT = register("inferius.ambient");
    public static final RegistryObject<SoundEvent> INFERIUS_HURT = register("inferius.hurt");
    public static final RegistryObject<SoundEvent> INFERIUS_DEATH = register("inferius.death");
    public static final RegistryObject<SoundEvent> INFERIUS_GRAB = register("inferius.grab");
    public static final RegistryObject<SoundEvent> INFERIUS_RISE = register("inferius.rise");

    // Whomping Willow. All timber and weather -- nothing here is a voice, because the tree does not
    // have one; every sound it makes is something a very large tree does under load.
    public static final RegistryObject<SoundEvent> WILLOW_AMBIENT = register("whomping_willow.ambient");
    public static final RegistryObject<SoundEvent> WILLOW_ALERT = register("whomping_willow.alert");
    public static final RegistryObject<SoundEvent> WILLOW_WHIP = register("whomping_willow.whip");
    public static final RegistryObject<SoundEvent> WILLOW_IMPACT = register("whomping_willow.impact");
    public static final RegistryObject<SoundEvent> WILLOW_GROAN = register("whomping_willow.groan");
    public static final RegistryObject<SoundEvent> WILLOW_ROOTS = register("whomping_willow.roots");
    public static final RegistryObject<SoundEvent> WILLOW_HURT = register("whomping_willow.hurt");
    public static final RegistryObject<SoundEvent> WILLOW_DEATH = register("whomping_willow.death");

    // Gringotts vault door. Four beats that have to read as one mechanism, which is why they are
    // four clips and not one: a finger on cold iron, the metal going liquid, the lock waking, the
    // bolts, and the whole thing parting.
    public static final RegistryObject<SoundEvent> VAULT_DOOR_TOUCH = register("vault_door.touch");
    public static final RegistryObject<SoundEvent> VAULT_DOOR_RIPPLE = register("vault_door.ripple");
    public static final RegistryObject<SoundEvent> VAULT_DOOR_COGS = register("vault_door.cogs");
    public static final RegistryObject<SoundEvent> VAULT_DOOR_BOLTS = register("vault_door.bolts");
    public static final RegistryObject<SoundEvent> VAULT_DOOR_OPEN = register("vault_door.open");
    public static final RegistryObject<SoundEvent> VAULT_DOOR_SEAL = register("vault_door.seal");

    // The cart. The rumble and the screech are looping instances driven client-side: the screech is
    // keyed on curvature, so it is silent on straights and rises through every switchback, which is
    // most of what makes a spiral sound like a spiral.
    public static final RegistryObject<SoundEvent> CART_RUMBLE = register("cart.rumble");
    public static final RegistryObject<SoundEvent> CART_SCREECH = register("cart.screech");
    public static final RegistryObject<SoundEvent> CART_LAUNCH = register("cart.launch");
    public static final RegistryObject<SoundEvent> CART_BRAKE = register("cart.brake");
    public static final RegistryObject<SoundEvent> CART_SLAM = register("cart.slam");

    // The Ukrainian Ironbelly. Old and sick rather than a boss -- the cower especially has to sound
    // pitiable, or Clankers reads as winning a fight instead of as bullying something.
    public static final RegistryObject<SoundEvent> IRONBELLY_AMBIENT = register("ironbelly.ambient");
    public static final RegistryObject<SoundEvent> IRONBELLY_ROAR = register("ironbelly.roar");
    public static final RegistryObject<SoundEvent> IRONBELLY_COWER = register("ironbelly.cower");
    public static final RegistryObject<SoundEvent> IRONBELLY_CHAIN = register("ironbelly.chain");
    public static final RegistryObject<SoundEvent> CLANKERS_RING = register("clankers.ring");

    // The Floo network. Five beats of one journey: the powder catching, the flue taking hold,
    // the spin between hearths, the landing, and the flames giving up when the far end is gone.
    public static final RegistryObject<SoundEvent> FLOO_IGNITE = register("floo.ignite");
    public static final RegistryObject<SoundEvent> FLOO_DEPART = register("floo.depart");
    public static final RegistryObject<SoundEvent> FLOO_TRAVEL = register("floo.travel");
    public static final RegistryObject<SoundEvent> FLOO_ARRIVE = register("floo.arrive");
    public static final RegistryObject<SoundEvent> FLOO_FIZZLE = register("floo.fizzle");

    // The Living Statue. Every one of these was a pitched-down vanilla stone block sound --
    // the step and the ambient are the ones that matter, since they are heard continuously
    // rather than once per fight, and a block step never read as several tons of granite.
    public static final RegistryObject<SoundEvent> STATUE_WAKE = register("statue.wake");
    public static final RegistryObject<SoundEvent> STATUE_RISE = register("statue.rise");
    public static final RegistryObject<SoundEvent> STATUE_STEP = register("statue.step");
    public static final RegistryObject<SoundEvent> STATUE_AMBIENT = register("statue.ambient");
    public static final RegistryObject<SoundEvent> STATUE_SWING = register("statue.swing");
    public static final RegistryObject<SoundEvent> STATUE_IMPACT = register("statue.impact");
    public static final RegistryObject<SoundEvent> STATUE_SLAM = register("statue.slam");
    public static final RegistryObject<SoundEvent> STATUE_HURT = register("statue.hurt");
    public static final RegistryObject<SoundEvent> STATUE_DEATH = register("statue.death");
    public static final RegistryObject<SoundEvent> STATUE_CRUMBLE = register("statue.crumble");
    public static final RegistryObject<SoundEvent> STATUE_BIND = register("statue.bind");

    // Ollivanders. The scene is staged in beats with deliberate silences between them, so
    // these stay small and dry; the one that matters is CHOSEN, which was a beacon plus
    // ELYTRA_FLYING -- an elytra is the last thing a wand finding its wizard should sound like.
    public static final RegistryObject<SoundEvent> OLLIVANDERS_SHOP = register("ollivanders.shop");
    public static final RegistryObject<SoundEvent> OLLIVANDERS_DOOR = register("ollivanders.door");
    public static final RegistryObject<SoundEvent> OLLIVANDERS_LEDGER = register("ollivanders.ledger");
    public static final RegistryObject<SoundEvent> OLLIVANDERS_MEASURE = register("ollivanders.measure");
    public static final RegistryObject<SoundEvent> OLLIVANDERS_BOX = register("ollivanders.box");
    public static final RegistryObject<SoundEvent> OLLIVANDERS_OFFER = register("ollivanders.offer");
    public static final RegistryObject<SoundEvent> OLLIVANDERS_CHOSEN = register("ollivanders.chosen");
    public static final RegistryObject<SoundEvent> OLLIVANDERS_CURIOUS = register("ollivanders.curious");
    public static final RegistryObject<SoundEvent> OLLIVANDERS_FAIL_PAPER = register("ollivanders.fail_paper");
    public static final RegistryObject<SoundEvent> OLLIVANDERS_FAIL_BANG = register("ollivanders.fail_bang");
    public static final RegistryObject<SoundEvent> OLLIVANDERS_FAIL_FIZZLE = register("ollivanders.fail_fizzle");

    // The Diagon Alley archway. The wall was opening on PISTON_EXTEND and IRON_DOOR_OPEN,
    // so an event made entirely of masonry sounded like redstone and a hinge. Every clip
    // here was generated with "no metal, no machinery" said out loud in the prompt.
    public static final RegistryObject<SoundEvent> ARCHWAY_KNOCK = register("archway.knock");
    public static final RegistryObject<SoundEvent> ARCHWAY_TAP = register("archway.tap");
    public static final RegistryObject<SoundEvent> ARCHWAY_ARM = register("archway.arm");
    public static final RegistryObject<SoundEvent> ARCHWAY_SEAM = register("archway.seam");
    public static final RegistryObject<SoundEvent> ARCHWAY_BEGIN = register("archway.begin");
    public static final RegistryObject<SoundEvent> ARCHWAY_SHIFT = register("archway.shift");
    public static final RegistryObject<SoundEvent> ARCHWAY_FOLD = register("archway.fold");
    public static final RegistryObject<SoundEvent> ARCHWAY_OPEN = register("archway.open");
    public static final RegistryObject<SoundEvent> ARCHWAY_SEAL = register("archway.seal");

    // The Horcrux. It was speaking with the Warden's voice -- WARDEN_HEARTBEAT while it
    // binds and WARDEN_ROAR when something gets out -- which is a loud, very specific mob
    // to borrow for the mod's quietest and nastiest object.
    public static final RegistryObject<SoundEvent> HORCRUX_PRIME = register("horcrux.prime");
    public static final RegistryObject<SoundEvent> HORCRUX_BIND = register("horcrux.bind");
    public static final RegistryObject<SoundEvent> HORCRUX_SHATTER = register("horcrux.shatter");
    public static final RegistryObject<SoundEvent> HORCRUX_BREAK = register("horcrux.break");
    public static final RegistryObject<SoundEvent> HORCRUX_SCREAM = register("horcrux.scream");

    public static final RegistryObject<SoundEvent> PENSIEVE_SWALLOW = register("pensieve.swallow");
    public static final RegistryObject<SoundEvent> PENSIEVE_FALL = register("pensieve.fall");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOID_HUM = register("pensieve.void_hum");
    public static final RegistryObject<SoundEvent> PENSIEVE_RISE = register("pensieve.rise");
    public static final RegistryObject<SoundEvent> PENSIEVE_GASP = register("pensieve.gasp");
    public static final RegistryObject<SoundEvent> PENSIEVE_DISTANT_THUD = register("pensieve.distant_thud");
    public static final RegistryObject<SoundEvent> PENSIEVE_POUR = register("pensieve.pour");
    public static final RegistryObject<SoundEvent> PENSIEVE_CAPTURE = register("pensieve.capture");

    // Voice lines of the lake_patronus memory (Fish Audio takes; played by the scene's sound events).
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LAKE_NARRATION = register("pensieve.voice.lake_narration");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LAKE_HARRY_WISP = register("pensieve.voice.lake_harry_wisp");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LAKE_HERMIONE = register("pensieve.voice.lake_hermione");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LAKE_HARRY_ME = register("pensieve.voice.lake_harry_me");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LAKE_HARRY_CAST = register("pensieve.voice.lake_harry_cast");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LAKE_HARRY_KNEW = register("pensieve.voice.lake_harry_knew");

    // BATTLE VOICES BEGIN (battle_voices.py writes this block)
    // Voice lines of the Battle of Hogwarts memory (Fish Audio takes; played by the scenes' voice cues).
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_MCG_THREAT = register("pensieve.voice.bh_mcg_threat");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_MCG_PIERTOTUM = register("pensieve.voice.bh_mcg_piertotum");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_MCG_QUIP = register("pensieve.voice.bh_mcg_quip");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_FLITWICK_SHIELD = register("pensieve.voice.bh_flitwick_shield");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_NEVILLE_BRILLIANT = register("pensieve.voice.bh_neville_brilliant");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_VOLD_FOOLS = register("pensieve.voice.bh_vold_fools");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_VOLD_HOUR = register("pensieve.voice.bh_vold_hour");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_VOLD_BEGIN = register("pensieve.voice.bh_vold_begin");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_RON_HOLD = register("pensieve.voice.bh_ron_hold");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_HERMIONE_HOLD = register("pensieve.voice.bh_hermione_hold");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_NEV_TAUNT = register("pensieve.voice.bh_nev_taunt");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_SEAMUS_RUN = register("pensieve.voice.bh_seamus_run");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_NEV_NOW = register("pensieve.voice.bh_nev_now");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_NEV_HA = register("pensieve.voice.bh_nev_ha");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_RON_SPIDERS = register("pensieve.voice.bh_ron_spiders");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_HERMIONE_RUN = register("pensieve.voice.bh_hermione_run");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_VOLD_WITHDRAW = register("pensieve.voice.bh_vold_withdraw");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_BELLA_PLAY = register("pensieve.voice.bh_bella_play");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_MOLLY_DAUGHTER = register("pensieve.voice.bh_molly_daughter");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_BELLA_CHILDREN = register("pensieve.voice.bh_bella_children");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_VOLD_COME_TO_DIE = register("pensieve.voice.bh_vold_come_to_die");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_HARRY_FINISH = register("pensieve.voice.bh_harry_finish");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_HARRY_EXPELLIARMUS = register("pensieve.voice.bh_harry_expelliarmus");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_VOLD_AVADA = register("pensieve.voice.bh_vold_avada");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_RON_ELDER = register("pensieve.voice.bh_ron_elder");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_HARRY_DONT_WANT = register("pensieve.voice.bh_harry_dont_want");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_BH_NARR_ALLWELL = register("pensieve.voice.bh_narr_allwell");
    // BATTLE VOICES END

    // Great Hall memory: ElevenLabs effects and Fish Audio voices (played by sfx and sound events).
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_DOOR_GROAN = register("pensieve.sfx.hall_door_groan");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_DOOR_BOOM = register("pensieve.sfx.hall_door_boom");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_MURMUR_A = register("pensieve.sfx.hall_murmur_a");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_MURMUR_B = register("pensieve.sfx.hall_murmur_b");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_HUSH = register("pensieve.sfx.hall_hush");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_CHEER_BIG = register("pensieve.sfx.hall_cheer_big");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_CHEER_SMALL = register("pensieve.sfx.hall_cheer_small");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_STEPS_GROUP = register("pensieve.sfx.hall_steps_group");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_STEPS_ONE = register("pensieve.sfx.hall_steps_one");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_STOOL = register("pensieve.sfx.hall_stool");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_CEILING_SHIMMER = register("pensieve.sfx.hall_ceiling_shimmer");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_HAT_BRIM = register("pensieve.sfx.hall_hat_brim");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_HALL_WHISPERS = register("pensieve.sfx.hall_whispers");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_WAND_BRICK_TAP = register("pensieve.sfx.wand_brick_tap");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_WAND_WALL_GRIND = register("pensieve.sfx.wand_wall_grind");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_WAND_STREET = register("pensieve.sfx.wand_street");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_WAND_SHOP_BELL = register("pensieve.sfx.wand_shop_bell");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_WAND_LADDER = register("pensieve.sfx.wand_ladder");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_WAND_BOXES_FALL = register("pensieve.sfx.wand_boxes_fall");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_WAND_VASE = register("pensieve.sfx.wand_vase");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_WAND_FIZZLE = register("pensieve.sfx.wand_fizzle");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_WAND_CHOICE = register("pensieve.sfx.wand_choice");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_WAND_TAPE = register("pensieve.sfx.wand_tape");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_LETTER_SLOT = register("pensieve.sfx.letter_slot");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_LETTER_HAMMER = register("pensieve.sfx.letter_hammer");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_LETTER_OWLS = register("pensieve.sfx.letter_owls");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_LETTER_CHIMNEY_RUMBLE = register("pensieve.sfx.letter_chimney_rumble");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_LETTER_WHOOSH = register("pensieve.sfx.letter_whoosh");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_LETTER_STORM = register("pensieve.sfx.letter_storm");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_LETTER_WAVES = register("pensieve.sfx.letter_waves");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_LETTER_THUNDER = register("pensieve.sfx.letter_thunder");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_LETTER_POUND = register("pensieve.sfx.letter_pound");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_LETTER_DOOR_CRASH = register("pensieve.sfx.letter_door_crash");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_LETTER_FIRE = register("pensieve.sfx.letter_fire");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_LAWN_AIR = register("pensieve.sfx.flying_lawn_air");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_BROOM_UP = register("pensieve.sfx.flying_broom_up");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_BROOM_WHOOSH = register("pensieve.sfx.flying_broom_whoosh");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_BROOM_BUCK = register("pensieve.sfx.flying_broom_buck");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_THUD = register("pensieve.sfx.flying_thud");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_WHISTLE = register("pensieve.sfx.flying_whistle");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_CRATE = register("pensieve.sfx.flying_crate");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_CROWD_BED = register("pensieve.sfx.flying_crowd_bed");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_CROWD_SWELL = register("pensieve.sfx.flying_crowd_swell");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_CROWD_ERUPT = register("pensieve.sfx.flying_crowd_erupt");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_CROWD_CHANT = register("pensieve.sfx.flying_crowd_chant");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_CROWD_GASP = register("pensieve.sfx.flying_crowd_gasp");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_RAIN = register("pensieve.sfx.flying_rain");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_THUNDER = register("pensieve.sfx.flying_thunder");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_BLUDGER_CRACK = register("pensieve.sfx.flying_bludger_crack");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_QUAFFLE_CATCH = register("pensieve.sfx.flying_quaffle_catch");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FLYING_SNITCH_FLUTTER = register("pensieve.sfx.flying_snitch_flutter");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_TENT_CROWD = register("pensieve.sfx.first_task_tent_crowd");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_GROUND_THUD = register("pensieve.sfx.first_task_ground_thud");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_CANNON = register("pensieve.sfx.first_task_cannon");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_CROWD_BED = register("pensieve.sfx.first_task_crowd_bed");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_CROWD_HUSH = register("pensieve.sfx.first_task_crowd_hush");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_HORNTAIL_ROAR = register("pensieve.sfx.first_task_horntail_roar");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_HORNTAIL_SNAP = register("pensieve.sfx.first_task_horntail_snap");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_WINGBEAT = register("pensieve.sfx.first_task_wingbeat");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_FIRE_ROAR = register("pensieve.sfx.first_task_fire_roar");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_FIRE_SCORCH = register("pensieve.sfx.first_task_fire_scorch");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_CHAIN_SNAP = register("pensieve.sfx.first_task_chain_snap");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_SLATE_SMASH = register("pensieve.sfx.first_task_slate_smash");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_BRIDGE_CRASH = register("pensieve.sfx.first_task_bridge_crash");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_ACCIO = register("pensieve.sfx.first_task_accio");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_WIND_HIGH = register("pensieve.sfx.first_task_wind_high");
    public static final RegistryObject<SoundEvent> PENSIEVE_SFX_FIRST_TASK_EGG_GRAB = register("pensieve.sfx.first_task_egg_grab");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_HOOCH_WELCOME = register("pensieve.voice.flying_hooch_welcome");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_HOOCH_LEFT = register("pensieve.voice.flying_hooch_left");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_HOOCH_UP = register("pensieve.voice.flying_hooch_up");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_CLASS_UP = register("pensieve.voice.flying_class_up");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_VIEWER_UP = register("pensieve.voice.flying_viewer_up");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_NEVILLE_WHOA = register("pensieve.voice.flying_neville_whoa");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_HOOCH_NOBODY = register("pensieve.voice.flying_hooch_nobody");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_MALFOY_LOOK = register("pensieve.voice.flying_malfoy_look");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_MALFOY_COME = register("pensieve.voice.flying_malfoy_come");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_MALFOY_CATCH = register("pensieve.voice.flying_malfoy_catch");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_MCG_FOLLOW = register("pensieve.voice.flying_mcg_follow");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_HOOCH_CLEAN = register("pensieve.voice.flying_hooch_clean");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_CAPTAIN = register("pensieve.voice.flying_captain");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_COMM_OFF = register("pensieve.voice.flying_comm_off");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_COMM_GOAL = register("pensieve.voice.flying_comm_goal");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FLYING_COMM_SEEKER = register("pensieve.voice.flying_comm_seeker");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FIRST_TASK_COMM_CALL = register("pensieve.voice.first_task_comm_call");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FIRST_TASK_COMM_CALL_MUFFLED = register("pensieve.voice.first_task_comm_call_muffled");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FIRST_TASK_COMM_HORNTAIL = register("pensieve.voice.first_task_comm_horntail");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FIRST_TASK_COMM_ACCIO = register("pensieve.voice.first_task_comm_accio");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FIRST_TASK_COMM_CHAIN = register("pensieve.voice.first_task_comm_chain");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FIRST_TASK_COMM_EGG = register("pensieve.voice.first_task_comm_egg");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_FIRST_TASK_VIEWER_ACCIO = register("pensieve.voice.first_task_viewer_accio");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LETTER_PETUNIA_UP = register("pensieve.voice.letter_petunia_up");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LETTER_VERNON_MINE = register("pensieve.voice.letter_vernon_mine");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LETTER_VERNON_NOMAIL = register("pensieve.voice.letter_vernon_nomail");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LETTER_VERNON_SUNDAY = register("pensieve.voice.letter_vernon_sunday");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LETTER_VERNON_SHOUT = register("pensieve.voice.letter_vernon_shout");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LETTER_VERNON_GUN = register("pensieve.voice.letter_vernon_gun");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LETTER_HAGRID_SORRY = register("pensieve.voice.letter_hagrid_sorry");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LETTER_HAGRID_FOUND = register("pensieve.voice.letter_hagrid_found");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LETTER_HAGRID_WIZARD = register("pensieve.voice.letter_hagrid_wizard");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_LETTER_HAGRID_LETTER = register("pensieve.voice.letter_hagrid_letter");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_HALL_HAT_OPEN = register("pensieve.voice.hall_hat_open");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_HALL_MCG_DOORS = register("pensieve.voice.hall_mcg_doors");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_HALL_MCG_CALL = register("pensieve.voice.hall_mcg_call");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_HALL_MCG_GRANGER = register("pensieve.voice.hall_mcg_granger");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_HALL_MCG_WEASLEY = register("pensieve.voice.hall_mcg_weasley");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_HALL_DUMBLEDORE = register("pensieve.voice.hall_dumbledore");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_HALL_HERMIONE_NERVOUS = register("pensieve.voice.hall_hermione_nervous");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_HALL_RON_NERVOUS = register("pensieve.voice.hall_ron_nervous");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_WAND_HAGRID_BRICKS = register("pensieve.voice.wand_hagrid_bricks");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_WAND_HAGRID_WELCOME = register("pensieve.voice.wand_hagrid_welcome");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_WAND_HAGRID_WAND = register("pensieve.voice.wand_hagrid_wand");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_WAND_OLLI_WONDERED = register("pensieve.voice.wand_olli_wondered");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_WAND_OLLI_WANDARM = register("pensieve.voice.wand_olli_wandarm");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_WAND_OLLI_TRY = register("pensieve.voice.wand_olli_try");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_WAND_OLLI_NO = register("pensieve.voice.wand_olli_no");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_WAND_OLLI_NO2 = register("pensieve.voice.wand_olli_no2");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_WAND_OLLI_TRICKY = register("pensieve.voice.wand_olli_tricky");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_WAND_OLLI_CURIOUS = register("pensieve.voice.wand_olli_curious");
    public static final RegistryObject<SoundEvent> PENSIEVE_VOICE_WAND_OLLI_GREAT = register("pensieve.voice.wand_olli_great");

    // The Sorting Hat's spoken lines (Fish Audio takes; played by the client HatVoice from the quiz packets).
    public static final RegistryObject<SoundEvent> SORTING_VOICE_GREETING = register("sorting.voice.greeting");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_Q1 = register("sorting.voice.q1");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_Q2 = register("sorting.voice.q2");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_Q3 = register("sorting.voice.q3");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_Q4 = register("sorting.voice.q4");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_Q5 = register("sorting.voice.q5");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_Q6 = register("sorting.voice.q6");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_Q7 = register("sorting.voice.q7");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_Q8 = register("sorting.voice.q8");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_Q9 = register("sorting.voice.q9");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_Q10 = register("sorting.voice.q10");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_REACT1 = register("sorting.voice.react1");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_REACT2 = register("sorting.voice.react2");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_REACT3 = register("sorting.voice.react3");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_TIE = register("sorting.voice.tie");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_GRYFFINDOR = register("sorting.voice.gryffindor");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_RAVENCLAW = register("sorting.voice.ravenclaw");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_HUFFLEPUFF = register("sorting.voice.hufflepuff");
    public static final RegistryObject<SoundEvent> SORTING_VOICE_SLYTHERIN = register("sorting.voice.slytherin");

    // Soundtrack of the Pensieve memories (streamed stereo; played by the scene's music events).
    // Film soundtrack excerpts supplied by the user: private use only, a licence is needed before any public release.
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_HALL_ENTRY = register("pensieve.music.hall_entry");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_HALL_PROLOGUE = register("pensieve.music.hall_prologue");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_WAND_WALL = register("pensieve.music.wand_wall");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_WAND_ALLEY = register("pensieve.music.wand_alley");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_LETTER_CUPBOARD = register("pensieve.music.letter_cupboard");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_LETTER_FLOOD = register("pensieve.music.letter_flood");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_LETTER_CROSSING = register("pensieve.music.letter_crossing");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_LETTER_HUT = register("pensieve.music.letter_hut");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_LETTER_WIZARD = register("pensieve.music.letter_wizard");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_FLYING_LESSON = register("pensieve.music.flying_lesson");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_FLYING_CHASE = register("pensieve.music.flying_chase");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_FLYING_MATCH = register("pensieve.music.flying_match");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_FLYING_SNITCH = register("pensieve.music.flying_snitch");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_FIRST_TASK_TENT = register("pensieve.music.first_task_tent");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_FIRST_TASK_ARENA = register("pensieve.music.first_task_arena");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_FIRST_TASK_CHASE = register("pensieve.music.first_task_chase");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_FIRST_TASK_EGG = register("pensieve.music.first_task_egg");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_LAKE_DEMENTORS = register("pensieve.music.lake_dementors");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_LAKE_PATRONUS_LIGHT = register("pensieve.music.lake_patronus_light");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_LAKE_WINDOW = register("pensieve.music.lake_window");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_CAVE_JOURNEY = register("pensieve.music.cave_journey");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_CAVE_DESPAIR = register("pensieve.music.cave_despair");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_CHAMBER_SECRETS = register("pensieve.music.chamber_secrets");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_CHAMBER_DUEL = register("pensieve.music.chamber_duel");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_CHAMBER_REUNION = register("pensieve.music.chamber_reunion");
    public static final RegistryObject<SoundEvent> PENSIEVE_MUSIC_SUMMONING_VOLDEMORT = register("pensieve.music.summoning_voldemort");

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
