package com.liziluyu.autoillumination.client.beam;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Subclass only to access the protected render state shards on Forge 1.20.1. */
final class BeamRenderTypes extends RenderType {
    static final RenderType BEAM = create("autoillumination:spotlight_beam",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, true,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(BeamShaders::beam))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setOutputState(PARTICLES_TARGET)
                    .createCompositeState(false));

    private BeamRenderTypes() {
        super("autoillumination:beam_states", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 1536, false, true, () -> {}, () -> {});
    }
}
