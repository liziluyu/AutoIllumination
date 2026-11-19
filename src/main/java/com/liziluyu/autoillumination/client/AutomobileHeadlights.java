package com.liziluyu.autoillumination.client;

import com.liziluyu.autoillumination.AutoIllumination;
import com.liziluyu.autoillumination.client.beam.BeamRenderer;
import com.liziluyu.autoillumination.client.beam.BeamStyle;
import com.mojang.blaze3d.vertex.BufferBuilder;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
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
    private static final BeamStyle STYLE = BeamStyle.spotlight(16f, 8, 0xFFFFFF);
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

        boolean submitted = false;
        float partialTick = event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        try {
            for (Entity entity : level.entitiesForRendering()) {
                if (!(entity instanceof AutomobileEntity automobile) || automobile.isRemoved()) {
                    continue;
                }
                Vec3 entityOrigin = new Vec3(Mth.lerp(partialTick, automobile.xOld, automobile.getX()),
                    Mth.lerp(partialTick, automobile.yOld, automobile.getY()),
                    Mth.lerp(partialTick, automobile.zOld, automobile.getZ()));

                // One complete local matrix drives the clip ray, culling bounds, and rendered beam.
                Matrix4f beamToEntity = AutomobileModelPose.beamToEntityLocal(automobile, partialTick);
                Vector3f localStart = beamToEntity.transformPosition(new Vector3f());
                Vector3f localForward = beamToEntity.transformDirection(new Vector3f(0f, 0f, 1f)).normalize();
                Vec3 origin = entityOrigin.add(localStart.x, localStart.y, localStart.z);
                Vec3 direction = new Vec3(localForward.x, localForward.y, localForward.z);
                Vec3 end = origin.add(direction.scale(STYLE.range()));
                double radius = (STYLE.startHalfWidth() + STYLE.range() * STYLE.spread()) * Math.sqrt(2);
                if (!event.getFrustum().isVisible(new AABB(origin, end).inflate(radius))) {
                    continue;
                }
                float length = BeamRenderer.clipLength(level, origin, direction, STYLE.range(), automobile);
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
                    BeamRenderer.render(event.getPoseStack(), buffers, STYLE, length);
                } finally {
                    event.getPoseStack().popPose();
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
