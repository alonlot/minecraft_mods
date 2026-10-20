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
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * {@code /pensieve validate [id]}, {@code list}, {@code preview <id>} and {@code leave <player>} (op level 2), and
 * {@code leave} (anyone inside a memory). Server side only.
 */
public final class PensieveCommand {
    private PensieveCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pensieve")
                .then(Commands.literal("list").requires(source -> source.hasPermission(2))
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("validate").requires(source -> source.hasPermission(2))
                        .executes(ctx -> validate(ctx.getSource(), null))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(MemoryRegistry.ids(), b))
                                .executes(ctx -> validate(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("preview").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(MemoryRegistry.ids(), b))
                                .executes(ctx -> PensieveSessions.startPreview(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "id")) ? 1 : 0)))
                // Anyone inside a memory may leave it; this is also the only command a viewer can run.
                // Ops can pull anyone out (the same exit as their own /pensieve leave).
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource()))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> leaveOther(ctx.getSource(),
                                        net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int leaveOther(CommandSourceStack source, ServerPlayer target) {
        if (!PensieveSessions.isViewer(target)) {
            source.sendFailure(Component.literal(target.getGameProfile().getName() + " is not inside a memory."));
            return 0;
        }
        PensieveSessions.leave(target, ExitReason.LEFT);
        source.sendSuccess(() -> Component.literal("Pulling " + target.getGameProfile().getName()
                + " out of the memory."), true);
        return 1;
    }

    private static int leave(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!PensieveSessions.isViewer(player)) {
            source.sendFailure(Component.literal("You are not inside a memory."));
            return 0;
        }
        PensieveSessions.leave(player, ExitReason.LEFT);
        return 1;
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
    public static SceneNames liveNames(MinecraftServer server) {
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
