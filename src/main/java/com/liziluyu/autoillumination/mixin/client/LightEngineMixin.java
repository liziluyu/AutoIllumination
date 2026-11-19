package com.liziluyu.autoillumination.mixin.client;

import com.liziluyu.autoillumination.client.ClientBeamLights;
import com.liziluyu.autoillumination.client.HeadlightActivation;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public abstract class LightEngineMixin {
    @Unique private final Set<Long> autoillumination$packetSections = new HashSet<>();

    @Inject(method = "updateSectionStatus", at = @At("HEAD"), cancellable = true)
    private void autoillumination$retainEmitterSection(SectionPos section, boolean empty, CallbackInfo callback) {
        if (empty && (Object) this instanceof BlockLightEngine
                && ((LightEngineAccessor) this).autoillumination$getChunkSource().getLevel() instanceof ClientLevel level
                && ClientBeamLights.retainsSection(level, section.asLong())) {
            callback.cancel();
        }
    }

    @Inject(method = "queueSectionData", at = @At("TAIL"))
    private void autoillumination$rememberPacketLayer(long section, DataLayer data, CallbackInfo callback) {
        if ((Object) this instanceof BlockLightEngine
                && ((LightEngineAccessor) this).autoillumination$getChunkSource().getLevel() instanceof ClientLevel level) {
            HeadlightActivation.recordServerBlockLight(level, section, data);
            if (data != null) {
                autoillumination$packetSections.add(section);
            }
        }
    }

    @Inject(method = "runLightUpdates", at = @At("RETURN"))
    private void autoillumination$repairPacketLayers(CallbackInfoReturnable<Integer> callback) {
        if (!autoillumination$packetSections.isEmpty()
                && ((LightEngineAccessor) this).autoillumination$getChunkSource().getLevel() instanceof ClientLevel level) {
            ClientBeamLights.recheckPacketSections(level, autoillumination$packetSections,
                (BlockLightEngine) (Object) this);
            autoillumination$packetSections.clear();
        }
    }
}
