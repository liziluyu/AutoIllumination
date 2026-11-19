package com.liziluyu.autoillumination.client.beam;

/**
 * Beam dimensions are in blocks; spread is the increase in half-width per block.
 * Opacities are per side, and color is packed RGB (0xRRGGBB).
 */
public record BeamStyle(float range, float startHalfWidth, float spread, float opacity,
                        float tipOpacity, float endFade, int color) {
    public static final BeamStyle DEFAULT = new BeamStyle(16f, 0.24f, 0.3f, 0.5f, 0.3f, 1.5f, 0xFFFFFF);

    public BeamStyle {
        if (!Float.isFinite(range) || range <= 0f
                || !Float.isFinite(startHalfWidth) || startHalfWidth < 0f
                || !Float.isFinite(spread) || spread < 0f
                || !Float.isFinite(startHalfWidth + range * spread)
                || !Float.isFinite(opacity) || opacity < 0f || opacity > 1f
                || !Float.isFinite(tipOpacity) || tipOpacity < 0f || tipOpacity > opacity
                || !Float.isFinite(endFade) || endFade < 0f) {
            throw new IllegalArgumentException("Invalid beam dimensions or opacity");
        }
        color &= 0xFFFFFF;
    }

    /** Source spotlight's redstone mapping; range must be finite and greater than zero. */
    public static BeamStyle spotlight(float range, int signal, int color) {
        float control = Math.max(0, Math.min(15, signal)) / 15f;
        return new BeamStyle(range, 0.24f, 0.3f * (1f - control),
                0.5f * control, 0.3f * control, 1.5f, color);
    }
}
