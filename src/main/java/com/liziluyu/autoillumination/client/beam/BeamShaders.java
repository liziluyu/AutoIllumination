package com.liziluyu.autoillumination.client.beam;

import com.liziluyu.autoillumination.AutoIllumination;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AutoIllumination.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BeamShaders {
    private static ShaderInstance beam;

    private BeamShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        // Forge owns shader lifetime and replaces this reference after each resource reload.
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(AutoIllumination.MOD_ID, "spotlight_beam"),
                DefaultVertexFormat.POSITION_TEX_COLOR), shader -> beam = shader);
    }

    static ShaderInstance beam() {
        return beam;
    }
}
