package com.hogwartsmagic.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** The named children of a model part, so Pensieve actors can reach a limb by its name. */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("children")
    Map<String, ModelPart> hogwartsmagic$children();
}
