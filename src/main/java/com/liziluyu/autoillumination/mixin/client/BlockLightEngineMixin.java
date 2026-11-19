package com.liziluyu.autoillumination.mixin.client;

import com.liziluyu.autoillumination.client.ClientBeamLights;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {
    @Inject(method = "getEmission", at = @At("RETURN"), cancellable = true)
    private void autoillumination$virtualEmission(long position, BlockState state,
            CallbackInfoReturnable<Integer> callback) {
        if (((LightEngineAccessor) this).autoillumination$getChunkSource().getLevel() instanceof ClientLevel level) {
            int virtual = ClientBeamLights.emission(level, position);
            if (virtual > callback.getReturnValueI()
                    && level.getLightEngine().lightOnInSection(SectionPos.of(SectionPos.blockToSection(position)))) {
                callback.setReturnValue(virtual);
            }
        }
    }
}
