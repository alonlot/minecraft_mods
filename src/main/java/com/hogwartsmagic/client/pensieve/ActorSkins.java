package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves a scene's {@link SceneDef.SkinRef} to a {@link PlayerSkin}. A texture ref is a bundled memory skin
 * ({@code textures/memory_skin/<name>.png}, bare names in this mod's namespace); a username is fetched once, in
 * the background, and cached. Nothing here ever blocks: until a username's skin has loaded (or when offline, or
 * the name does not exist) the default skin for the actor's UUID is used.
 */
public final class ActorSkins {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID NO_UUID = new UUID(0L, 0L);

    /** Bundled skins, by "name|slim". Render thread only. */
    private static final Map<String, PlayerSkin> TEXTURES = new HashMap<>();
    /** Username skins, by lower-case name. Entries are created and read on the render thread. */
    private static final Map<String, Fetch> USERNAMES = new HashMap<>();

    private static final class Fetch {
        volatile PlayerSkin skin;
    }

    private ActorSkins() {
    }

    public static PlayerSkin skinFor(SceneDef.SkinRef ref) {
        return skinFor(ref, NO_UUID);
    }

    /** {@code fallback} picks the default skin while a username is still loading, or when there is no skin. */
    public static PlayerSkin skinFor(SceneDef.SkinRef ref, UUID fallback) {
        if (ref == null) return DefaultPlayerSkin.get(fallback);
        if (ref.texture() != null) return texture(ref.texture(), ref.slim());
        if (ref.username() != null) {
            PlayerSkin s = username(ref.username());
            if (s != null) return s;
        }
        return DefaultPlayerSkin.get(fallback);
    }

    private static PlayerSkin texture(String name, boolean slim) {
        String key = name + (slim ? "|slim" : "|wide");
        PlayerSkin cached = TEXTURES.get(key);
        if (cached != null) return cached;
        ResourceLocation id = ResourceLocation.tryParse(name.contains(":") ? name : ExampleMod.MODID + ":" + name);
        if (id == null) id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "missing");
        ResourceLocation tex = id.withPath("textures/memory_skin/" + id.getPath() + ".png");
        PlayerSkin skin = new PlayerSkin(tex, null, null, null,
                slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, true);
        TEXTURES.put(key, skin);
        return skin;
    }

    private static PlayerSkin username(String name) {
        String key = name.toLowerCase(java.util.Locale.ROOT);
        Fetch f = USERNAMES.get(key);
        if (f == null) {
            f = new Fetch();
            USERNAMES.put(key, f);
            begin(name, f);
        }
        return f.skin;
    }

    private static void begin(String name, Fetch fetch) {
        Minecraft mc = Minecraft.getInstance();
        try {
            SkullBlockEntity.fetchGameProfile(name).whenComplete((opt, err) -> {
                if (err != null || opt == null || opt.isEmpty()) {
                    if (err != null) LOGGER.debug("Pensieve: no profile for skin '{}': {}", name, err.toString());
                    return;
                }
                GameProfile profile = opt.get();
                // Loading a skin registers textures: do that on the client thread, not the lookup's thread.
                mc.execute(() -> {
                    CompletableFuture<PlayerSkin> future = mc.getSkinManager().getOrLoad(profile);
                    future.thenAccept(s -> fetch.skin = s);
                });
            });
        } catch (RuntimeException e) {
            LOGGER.debug("Pensieve: skin lookup for '{}' failed to start: {}", name, e.toString());
        }
    }

    /** Forget cached username lookups (a new session may want a retry after being offline). */
    public static void clearUsernames() {
        USERNAMES.entrySet().removeIf(e -> e.getValue().skin == null);
    }
}
