package com.liziluyu.autoillumination.client;

import io.github.foundationgames.automobility.entity.AutomobileEntity;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Reconstructs the Automobility 0.4.2 / RIA 1.0.4b vehicle frame transform. */
public final class AutomobileModelPose {
    private AutomobileModelPose() {
    }

    /**
     * Returns beam-local to entity-local. The model pivot is the vehicle model's local zero;
     * this integration intentionally has no fixture offset. The renderer's local +Z is rotated
     * to the vehicle model's local -Z forward axis at the end of the frame transform.
     */
    public static Matrix4f beamToEntityLocal(AutomobileEntity automobile, float partialTick) {
        var displacement = automobile.getDisplacement();
        Matrix4f matrix = new Matrix4f();
        matrix.translate(0f, displacement.getVertical(partialTick), 0f);
        matrix.rotate(new Quaternionf().rotationXYZ(
            (float) Math.toRadians(displacement.getAngularX(partialTick)), 0f,
            (float) Math.toRadians(displacement.getAngularZ(partialTick))));
        matrix.rotate(Axis.ZP.rotationDegrees(180));
        matrix.rotate(Axis.YP.rotationDegrees(automobile.getAutomobileYaw(partialTick) + 180f));
        matrix.translate(0f, -automobile.getWheels().model().radius() / 16f, 0f);
        matrix.translate(0f, (float) (automobile.getSuspensionBounce(partialTick) * 0.048f
            + (automobile.engineRunning()
                ? Math.cos((automobile.getTime() + partialTick) * 2.7d) / 156d : 0d)), 0f);
        matrix.rotate(Axis.YP.rotationDegrees(180));
        return matrix;
    }
}
