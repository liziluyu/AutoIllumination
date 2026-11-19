package com.liziluyu.autoillumination;

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

    private static volatile Settings current = settings(DEFAULT_LENGTH, DEFAULT_COLOR, DEFAULT_ANGLE, DEFAULT_OPACITY);

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Appearance shared by all frame headlight beams. Lamp sizes and positions are defined in FrameLightProfiles.")
            .push("headlights");
        LENGTH = builder.comment("Maximum beam length in blocks. Block collisions can shorten each beam.")
            .defineInRange("length", DEFAULT_LENGTH, 0.1d, 128d);
        COLOR = builder.comment("Six hexadecimal RGB digits, without # (for example FFFFFF or FFE0A0).")
            .define("color", DEFAULT_COLOR, value -> value instanceof String text && text.matches("[0-9a-fA-F]{6}"));
        OPENING_ANGLE = builder.comment("Full opening angle in degrees, applied horizontally and vertically. 0 gives parallel sides.")
            .defineInRange("openingAngleDegrees", DEFAULT_ANGLE, 0d, 179d);
        OPACITY = builder.comment("Per-side opacity: 0 is fully transparent, 1 is fully opaque. The beam fades towards its end.")
            .defineInRange("opacity", DEFAULT_OPACITY, 0d, 1d);
        builder.pop();
        SPEC = builder.build();
    }

    private HeadlightConfig() {
    }

    public static Settings current() {
        return current;
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
