package com.liziluyu.autoillumination;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Client appearance settings, parsed once on config load/reload rather than during vehicle rendering. */
@Mod.EventBusSubscriber(modid = AutoIllumination.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HeadlightConfig {
    private static final double DEFAULT_LENGTH = 2d;
    private static final String DEFAULT_COLOR = "FFFCC6";
    private static final double DEFAULT_ANGLE = 32d;
    private static final double DEFAULT_OPACITY = 0.15d;
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue LENGTH;
    private static final ForgeConfigSpec.ConfigValue<String> COLOR;
    private static final ForgeConfigSpec.DoubleValue OPENING_ANGLE;
    private static final ForgeConfigSpec.DoubleValue OPACITY;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> FRAME_OVERRIDES;

    private static volatile Settings current = settings(DEFAULT_LENGTH, DEFAULT_COLOR, DEFAULT_ANGLE, DEFAULT_OPACITY);

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Appearance shared by all frame headlight beams. Override individual frame lamps with frameOverrides.")
            .push("headlights");
        LENGTH = builder.comment("Maximum beam length in blocks. Block collisions can shorten each beam.")
            .defineInRange("length", DEFAULT_LENGTH, 0.1d, 128d);
        COLOR = builder.comment("Six hexadecimal RGB digits, without # (for example FFFFFF or FFE0A0).")
            .define("color", DEFAULT_COLOR, value -> value instanceof String text && text.matches("[0-9a-fA-F]{6}"));
        OPENING_ANGLE = builder.comment("Full opening angle in degrees, applied horizontally and vertically. 0 gives parallel sides.")
            .defineInRange("openingAngleDegrees", DEFAULT_ANGLE, 0d, 179d);
        OPACITY = builder.comment("Per-side opacity: 0 is fully transparent, 1 is fully opaque. The beam fades towards its end.")
            .defineInRange("opacity", DEFAULT_OPACITY, 0d, 1d);
        FRAME_OVERRIDES = builder.comment(
                "Override built-in beams by full frame ID. Each string defines the complete beam list for one frame.",
                "Format: namespace:frame_id | width,height,x,y,z,yaw,pitch,roll; next beam",
                "Width/height are full dimensions; x/y/z are offsets. All five values use 1/16 of a block. Decimals are allowed; dimensions must be positive.",
                "+X is right, +Y is up, +Z is forward. Y is additional height above the frame origin, which already includes the wheel radius.",
                "Yaw/pitch/roll use degrees: positive yaw turns right, positive pitch points down, positive roll is clockwise when looking along the beam from behind.",
                "Omit all three rotation values to use zero rotation. Separate beams with semicolons; do not add a trailing semicolon.",
                "Leave the right side of | empty to disable beams for that frame. Unlisted IDs use built-in profiles. Frames from other mods are supported.",
                "For duplicate IDs, the last valid entry wins. Invalid entries are logged and ignored without replacing existing profiles.",
                "Example (the default empty list applies no overrides; copy this assignment to replace frameOverrides = []):",
                "frameOverrides = [",
                "  \"automobility:wooden_motorcar | 3,3,-4.5,6.5,21.8,-10,5,0; 3,3,4.5,6.5,21.8,10,5,0\",",
                "  \"examplemod:custom_frame | 3,3,0,6.5,21.8\",",
                "  \"automobility:steel_motorcar |\"",
                "]")
            .defineListAllowEmpty("frameOverrides", List.<String>of(), value -> value instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    private HeadlightConfig() {
    }

    public static Settings current() {
        return current;
    }

    /** Raw definitions for client profile parsing on config load/reload, never on a render frame. */
    public static List<? extends String> frameOverrides() {
        return FRAME_OVERRIDES.get();
    }

    @SubscribeEvent
    public static void onLoading(ModConfigEvent.Loading event) {
        reload(event);
    }

    @SubscribeEvent
    public static void onReloading(ModConfigEvent.Reloading event) {
        reload(event);
    }

    private static void reload(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            current = settings(LENGTH.get(), COLOR.get(), OPENING_ANGLE.get(), OPACITY.get());
        }
    }

    private static Settings settings(double length, String color, double angle, double opacity) {
        return new Settings((float) length, Integer.parseInt(color, 16),
            (float) Math.tan(Math.toRadians(angle / 2d)), (float) opacity);
    }

    /** Spread is the increase in half-width and half-height per block, derived from the full angle. */
    public record Settings(float length, int color, float spread, float opacity) {
    }
}
