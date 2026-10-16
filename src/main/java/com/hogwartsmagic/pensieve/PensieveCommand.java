package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneNames;
import com.hogwartsmagic.pensieve.scene.SceneValidator;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** {@code /pensieve validate [id]} and {@code /pensieve list}, op level 2. Server side only. */
public final class PensieveCommand {
    private PensieveCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pensieve")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("validate")
                        .executes(ctx -> validate(ctx.getSource(), null))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(MemoryRegistry.ids(), b))
                                .executes(ctx -> validate(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))));
    }

    private static int list(CommandSourceStack source) {
        Set<String> ids = MemoryRegistry.ids();
        if (ids.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Pensieve memories are loaded."), false);
        } else {
            source.sendSuccess(() -> Component.literal(ids.size() + " memories: " + String.join(", ", ids)), false);
        }
        return ids.size();
    }

    private static int validate(CommandSourceStack source, String only) {
        Set<String> ids = MemoryRegistry.ids();
        if (only != null) {
            if (!ids.contains(only)) {
                source.sendFailure(Component.literal("No memory '" + only + "' is loaded (a file with errors is skipped; "
                        + "see the log). Loaded: " + (ids.isEmpty() ? "none" : String.join(", ", ids))));
                return 0;
            }
            ids = Set.of(only);
        }
        SceneNames names = liveNames(source.getServer());
        int problems = 0;
        for (String id : new TreeSet<>(ids)) {
            Optional<SceneDef> def = MemoryRegistry.get(id);
            if (def.isEmpty()) continue;
            List<String> messages = SceneValidator.validate(def.get(), names);
            if (messages.isEmpty()) {
                source.sendSuccess(() -> Component.literal(id + ": OK"), false);
            } else {
                for (String m : messages) {
                    source.sendFailure(Component.literal(id + ": " + m));
                }
                problems += messages.size();
            }
        }
        return problems;
    }

    /** The names that exist right now, read from the live registries and the server's data. */
    static SceneNames liveNames(MinecraftServer server) {
        Set<String> spells = new HashSet<>();
        for (SpellType type : SpellType.values()) spells.add(type.getId());
        Set<String> entities = keys(BuiltInRegistries.ENTITY_TYPE);
        entities.add("player");
        Set<String> templates = server.getStructureManager().listTemplates()
                .map(ResourceLocation::toString).collect(Collectors.toCollection(HashSet::new));
        Set<String> features = server.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).keySet().stream()
                .map(ResourceLocation::toString).collect(Collectors.toCollection(HashSet::new));
        return new SceneNames(entities, spells, keys(BuiltInRegistries.SOUND_EVENT), keys(BuiltInRegistries.BLOCK),
                keys(BuiltInRegistries.ITEM), templates, features, keys(BuiltInRegistries.PARTICLE_TYPE),
                new HashSet<>(MemoryRegistry.skins()));
    }

    private static Set<String> keys(Registry<?> registry) {
        return registry.keySet().stream().map(ResourceLocation::toString).collect(Collectors.toCollection(HashSet::new));
    }
}
