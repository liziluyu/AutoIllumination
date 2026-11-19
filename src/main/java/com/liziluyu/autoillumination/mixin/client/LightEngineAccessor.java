package com.liziluyu.autoillumination.mixin.client;

import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Use a mapped accessor on the declaring class, including for its block-engine subclass. */
@Mixin(LightEngine.class)
public interface LightEngineAccessor {
    @Accessor("chunkSource")
    LightChunkGetter autoillumination$getChunkSource();
}
