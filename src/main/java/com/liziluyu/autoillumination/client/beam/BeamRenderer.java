package com.liziluyu.autoillumination.client.beam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Client-only unlit square-frustum renderer. Draw and flush during AFTER_PARTICLES,
 * so the particles output target also works with Fabulous graphics.
 */
public final class BeamRenderer {
    private static final float[] CORNER_X = {-1f, 1f, 1f, -1f};
    private static final float[] CORNER_Y = {-1f, -1f, 1f, 1f};
    private static final double MIN_DIRECTION_LENGTH_SQUARED = 1.0E-12;
    private static final double HIT_INSET = 0.01;

    private BeamRenderer() {
    }

    public static boolean isReady() {
        return BeamShaders.beam() != null;
    }

    /** Flush this type once after all beams have been submitted to a dedicated BufferSource. */
    public static RenderType renderType() {
        return BeamRenderTypes.BEAM;
    }

    /**
     * Draws from the current pose origin along local +Z. Length is measured from the
     * emitting face, not the center of a fixture. This method does not flush the buffer.
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, BeamStyle style, float length) {
        if (!isReady() || !Float.isFinite(length) || length <= 0f || style.opacity() <= 0f) {
            return;
        }
        length = Math.min(length, style.range());
        float fadeStart = Math.max(0f, length - style.endFade());
        float tipShare = style.tipOpacity() / style.opacity();
        // Compress short beams into one fade instead of creating a zero-length main segment.
        // Interpolating the base taper keeps this continuous as a wall approaches the emitter.
        float midTaper = Mth.lerp(fadeStart / length, 1f, tipShare);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vertices = buffers.getBuffer(renderType());
        float red = ((style.color() >> 16) & 255) / 255f;
        float green = ((style.color() >> 8) & 255) / 255f;
        float blue = (style.color() & 255) / 255f;

        for (int side = 0; side < 4; side++) {
            int next = (side + 1) & 3;
            if (fadeStart > 0f) {
                side(vertices, matrix, style, side, next, 0f, fadeStart, 1f, midTaper, red, green, blue);
            }
            if (fadeStart < length) {
                side(vertices, matrix, style, side, next, fadeStart, length, midTaper, 0f, red, green, blue);
            }
        }
    }

    /**
     * Convenience for world-space beams. Supply the stage pose before camera translation;
     * the method applies origin minus camera position and rotates local +Z into direction.
     */
    public static void renderWorld(PoseStack poseStack, MultiBufferSource buffers, BeamStyle style,
                                   Vec3 origin, Vec3 direction, Vec3 cameraPosition, float length) {
        if (!isFinite(origin) || !isFinite(cameraPosition) || !isValidDirection(direction)) {
            return;
        }
        Vec3 normal = direction.normalize();
        poseStack.pushPose();
        try {
            poseStack.translate(origin.x - cameraPosition.x, origin.y - cameraPosition.y,
                    origin.z - cameraPosition.z);
            poseStack.mulPose(new Quaternionf().rotationTo(0f, 0f, 1f,
                    (float) normal.x, (float) normal.y, (float) normal.z));
            render(poseStack, buffers, style, length);
        } finally {
            poseStack.popPose();
        }
    }

    /**
     * Clips the beam's center ray against block collision shapes, ignoring fluids.
     * Direction need not be normalized; ignoredEntity supplies the collision context.
     * The four widening sides still use the normal depth test for off-axis obstacles.
     * Stops at the first unloaded chunk instead of requesting terrain to finish the ray.
     * ClientLevel also ensures vanilla's boundary-epsilon reads cannot load terrain.
     */
    public static float clipLength(ClientLevel level, Vec3 origin, Vec3 direction, double maxLength,
                                   Entity ignoredEntity) {
        if (!isFinite(origin) || !isValidDirection(direction) || !Double.isFinite(maxLength)
                || maxLength <= 0d || maxLength > Float.MAX_VALUE) {
            return 0f;
        }
        Vec3 normal = direction.normalize();
        maxLength = loadedLength(level, origin, normal, maxLength);
        if (maxLength <= 0d) {
            return 0f;
        }
        Vec3 end = origin.add(normal.scale(maxLength));
        BlockHitResult hit = level.clip(new ClipContext(origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ignoredEntity));
        if (hit.getType() == HitResult.Type.MISS) {
            return (float) maxLength;
        }
        return (float) Math.max(0d, Math.min(maxLength, origin.distanceTo(hit.getLocation()) - HIT_INSET));
    }

    /** Visits only chunk columns intersected by the ray; hasChunk never loads a chunk. */
    private static double loadedLength(ClientLevel level, Vec3 origin, Vec3 normal, double maxLength) {
        int chunkX = Mth.floor(origin.x) >> 4;
        int chunkZ = Mth.floor(origin.z) >> 4;
        int stepX = normal.x > 0d ? 1 : normal.x < 0d ? -1 : 0;
        int stepZ = normal.z > 0d ? 1 : normal.z < 0d ? -1 : 0;
        double nextX = stepX == 0 ? Double.POSITIVE_INFINITY
                : ((chunkX + (stepX > 0 ? 1d : 0d)) * 16d - origin.x) / normal.x;
        double nextZ = stepZ == 0 ? Double.POSITIVE_INFINITY
                : ((chunkZ + (stepZ > 0 ? 1d : 0d)) * 16d - origin.z) / normal.z;
        double strideX = stepX == 0 ? Double.POSITIVE_INFINITY : 16d / Math.abs(normal.x);
        double strideZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 16d / Math.abs(normal.z);
        double enteredAt = 0d;
        while (level.hasChunk(chunkX, chunkZ)) {
            enteredAt = Math.min(nextX, nextZ);
            if (enteredAt >= maxLength) {
                return maxLength;
            }
            if (nextX <= enteredAt) {
                chunkX += stepX;
                nextX += strideX;
            }
            if (nextZ <= enteredAt) {
                chunkZ += stepZ;
                nextZ += strideZ;
            }
        }
        return Math.max(0d, enteredAt - HIT_INSET);
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static boolean isValidDirection(Vec3 direction) {
        double lengthSquared = direction.lengthSqr();
        return isFinite(direction) && Double.isFinite(lengthSquared)
                && lengthSquared > MIN_DIRECTION_LENGTH_SQUARED;
    }

    private static void side(VertexConsumer vertices, Matrix4f matrix, BeamStyle style,
                              int first, int second, float start, float end, float startTaper,
                              float endTaper, float red, float green, float blue) {
        float startHalf = style.startHalfWidth() + start * style.spread();
        float endHalf = style.startHalfWidth() + end * style.spread();
        vertex(vertices, matrix, first, startHalf, start, style.opacity(), startTaper, red, green, blue);
        vertex(vertices, matrix, second, startHalf, start, style.opacity(), startTaper, red, green, blue);
        vertex(vertices, matrix, second, endHalf, end, style.opacity(), endTaper, red, green, blue);
        vertex(vertices, matrix, first, endHalf, end, style.opacity(), endTaper, red, green, blue);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, int corner, float half,
                                float z, float opacity, float taper, float red, float green, float blue) {
        // UV0 stores two floats rather than texture coordinates; byte alpha would lose dim beams.
        vertices.vertex(matrix, CORNER_X[corner] * half, CORNER_Y[corner] * half, z)
                .uv(opacity, taper)
                .color(red, green, blue, 1f)
                .endVertex();
    }
}
