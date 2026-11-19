package com.liziluyu.autoillumination.client;

import com.liziluyu.autoillumination.AutoIllumination;
import com.liziluyu.autoillumination.HeadlightConfig;
import com.liziluyu.autoillumination.client.beam.BeamRenderer;
import com.liziluyu.autoillumination.client.beam.BeamStyle;
import com.mojang.blaze3d.vertex.BufferBuilder;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Renders vehicle beams after particles, using the same interpolated frame pose as the car renderer. */
@Mod.EventBusSubscriber(modid = AutoIllumination.MOD_ID, value = Dist.CLIENT)
public final class AutomobileHeadlights {
    private static MultiBufferSource.BufferSource buffers;

    private AutomobileHeadlights() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !BeamRenderer.isReady()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        HeadlightConfig.Settings settings = HeadlightConfig.current();
        if (settings.opacity() <= 0f) {
            return;
        }

        boolean submitted = false;
        float partialTick = event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        try {
            for (Entity entity : level.entitiesForRendering()) {
                if (!(entity instanceof AutomobileEntity automobile) || automobile.isRemoved()) {
                    continue;
                }
                List<FrameLightProfiles.Beam> lights = FrameLightProfiles.beams(automobile.getFrame().getId());
                if (lights.isEmpty()) {
                    continue;
                }
                Vec3 entityOrigin = new Vec3(Mth.lerp(partialTick, automobile.xOld, automobile.getX()),
                    Mth.lerp(partialTick, automobile.yOld, automobile.getY()),
                    Mth.lerp(partialTick, automobile.zOld, automobile.getZ()));

                Matrix4f framePose = AutomobileModelPose.beamToEntityLocal(automobile, partialTick);
                for (FrameLightProfiles.Beam light : lights) {
                    // Profile sizes are full diameters in 1/16 blocks; the renderer uses half sizes.
                    BeamStyle style = new BeamStyle(settings.length(), light.width16() / 32f,
                        light.height16() / 32f, settings.spread(), settings.opacity(),
                        settings.opacity() * 0.6f, 3f, settings.color());
                    // Beam-local +Y points down after the model's Z180 conversion. Profiles use +Y up.
                    Matrix4f beamToEntity = new Matrix4f(framePose).translate(light.offsetX16() / 16f,
                        -light.offsetY16() / 16f, light.offsetZ16() / 16f)
                        // Translate first so each lamp rotates about its own emitting face.
                        .rotateY((float) Math.toRadians(light.yawDegrees()))
                        .rotateX((float) Math.toRadians(-light.pitchDegrees()))
                        .rotateZ((float) Math.toRadians(light.rollDegrees()));
                    Vector3f localStart = beamToEntity.transformPosition(new Vector3f());
                    Vector3f localForward = beamToEntity.transformDirection(new Vector3f(0f, 0f, 1f)).normalize();
                    Vec3 origin = entityOrigin.add(localStart.x, localStart.y, localStart.z);
                    Vec3 direction = new Vec3(localForward.x, localForward.y, localForward.z);
                    Vec3 end = origin.add(direction.scale(style.range()));
                    if (!event.getFrustum().isVisible(
                            new AABB(origin, end).inflate(style.boundingRadius(style.range())))) {
                        continue;
                    }
                    float length = BeamRenderer.clipLength(level, origin, direction, style.range(), automobile);
                    if (length <= 0f) {
                        continue;
                    }
                    if (buffers == null) {
                        buffers = MultiBufferSource.immediate(new BufferBuilder(1536));
                    }
                    submitted = true;
                    event.getPoseStack().pushPose();
                    try {
                        // Keep world coordinates in doubles until the camera-relative translation.
                        event.getPoseStack().translate(entityOrigin.x - camera.x, entityOrigin.y - camera.y,
                            entityOrigin.z - camera.z);
                        event.getPoseStack().mulPoseMatrix(beamToEntity);
                        BeamRenderer.render(event.getPoseStack(), buffers, style, length);
                    } finally {
                        event.getPoseStack().popPose();
                    }
                }
            }
        } finally {
            if (submitted) {
                // Sort and draw all vehicle beams together without flushing shared game buffers.
                buffers.endBatch(BeamRenderer.renderType());
            }
        }
    }
}
