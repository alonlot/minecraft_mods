package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.mixin.client.PlayerModelPartsAccessor;
import com.hogwartsmagic.pensieve.PensieveBodyEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Draws the helpless body left at the basin: a player in the {@code bent_over_basin} pose, facing the basin,
 * wearing the owner skin (found through the normal player list while the owner is online). The body entity itself
 * is a plain living entity; this renderer lends it a client-only {@link Surrogate} player and hands that to the
 * vanilla player renderer, so the skin, layers and the {@link ActorPoser} pose all come for free.
 */
public class PensieveBodyRenderer extends EntityRenderer<PensieveBodyEntity> {
    /** The stand-in player. A type of its own so the pose hook can recognise it. */
    public static final class Surrogate extends RemotePlayer {
        Surrogate(ClientLevel level, GameProfile profile) {
            super(level, profile);
            this.getEntityData().set(PlayerModelPartsAccessor.hogwartsmagic$modelParts(), (byte) 0x7F);
        }
    }

    private final PlayerRenderer wide;
    private final PlayerRenderer slim;
    private final WeakHashMap<PensieveBodyEntity, Surrogate> surrogates = new WeakHashMap<>();

    public PensieveBodyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.wide = new PlayerRenderer(context, false);
        this.slim = new PlayerRenderer(context, true);
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(@NotNull PensieveBodyEntity body, float yaw, float partial, @NotNull PoseStack pose,
                       @NotNull MultiBufferSource buffer, int light) {
        Surrogate s = surrogateFor(body);
        if (s == null) return;
        s.moveTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), 0f);
        s.xo = body.xo;
        s.yo = body.yo;
        s.zo = body.zo;
        s.xOld = body.xOld;
        s.yOld = body.yOld;
        s.zOld = body.zOld;
        s.yRotO = body.yRotO;
        s.xRotO = 0f;
        s.yBodyRot = body.yBodyRot;
        s.yBodyRotO = body.yBodyRotO;
        s.yHeadRot = body.yHeadRot;
        s.yHeadRotO = body.yHeadRotO;
        s.tickCount = body.tickCount;
        s.hurtTime = body.hurtTime;
        s.hurtDuration = body.hurtDuration;
        PlayerRenderer renderer = s.getSkin().model() == PlayerSkin.Model.SLIM ? slim : wide;
        renderer.render(s, yaw, partial, pose, buffer, light);
    }

    private Surrogate surrogateFor(PensieveBodyEntity body) {
        if (!(body.level() instanceof ClientLevel level)) return null;
        UUID owner = body.getOwnerId();
        if (owner == null) return null;
        Surrogate s = surrogates.get(body);
        if (s == null || s.level() != level || !owner.equals(s.getUUID())) {
            s = new Surrogate(level, new GameProfile(owner, body.getOwnerName()));
            surrogates.put(body, s);
        }
        return s;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull PensieveBodyEntity body) {
        Surrogate s = surrogates.get(body);
        return s != null ? s.getSkin().texture() : DefaultPlayerSkin.getDefaultTexture();
    }
}
