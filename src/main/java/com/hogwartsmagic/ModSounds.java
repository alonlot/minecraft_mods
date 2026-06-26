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

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
