package com.liziluyu.autoillumination.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit frame light profiles; unconfigured frames have no beams.
 */
public final class FrameLightProfiles {
    // Y is extra height above the frame origin, which already includes the installed wheel radius.
    private static final Beam MOTORCAR_LEFT = new Beam(3f, 3f, -4.5f, 6.5f, 21.8f, -10f, 5f, 0f);
    private static final Beam MOTORCAR_RIGHT = new Beam(3f, 3f, 4.5f, 6.5f, 21.8f, 10f, 5f, 0f);
    private static final Beam MOTORCAR_DOUBLE_LEFT = new Beam(3f, 3f, -10.5f, 6.5f, 21.8f, 0f, 5f, 0f);
    private static final Beam MOTORCAR_DOUBLE_RIGHT = new Beam(3f, 3f, 10.5f, 6.5f, 21.8f, 0f, 5f, 0f);
    private static final Beam MOTORCAR_QUAD_LEFT = new Beam(3f, 3f, -10.5f, 6.5f, 29.8f, 0f, 5f, 0f);
    private static final Beam MOTORCAR_QUAD_RIGHT = new Beam(3f, 3f, 10.5f, 6.5f, 29.8f, 0f, 5f, 0f);
    private static final Beam RICKSHAW = new Beam(3f, 3f, 0f, 6.5f, 13.8f, 0f, 5f, 0f);
    private static final Beam LORRY_LEFT = new Beam(6f, 8f, -13f, 9f, 58f, 0f, 5f, 0f);
    private static final Beam LORRY_RIGHT = new Beam(6f, 8f, 13f, 9f, 58f, 0f, 5f, 0f);
    private static final Beam DMC12_LEFT = new Beam(12f, 5f, -14f, 7.5f, 51f, 0f, 5f, 0f);
    private static final Beam DMC12_RIGHT = new Beam(12f, 5f, 14f, 7.5f, 51f, 0f, 5f, 0f);
    private static final Map<ResourceLocation, List<Beam>> PROFILES = createProfiles();

    private FrameLightProfiles() {
    }

    /**
     * Full width/height and offsets in sixteenths of a block; +X right, +Y up, +Z forward.
     * Angles are degrees about the lamp origin: positive yaw turns right, positive pitch points down,
     * positive roll is clockwise when viewed from behind the lamp looking along its beam.
     * Local rotations are composed as yaw, then pitch about the yawed axis, then axial roll.
     */
    public record Beam(float width16, float height16, float offsetX16, float offsetY16, float offsetZ16,
                       float yawDegrees, float pitchDegrees, float rollDegrees) {
        /**
         * Unrotated lamp, retained for profiles that only specify a size and position.
         */
        public Beam(float width16, float height16, float offsetX16, float offsetY16, float offsetZ16) {
            this(width16, height16, offsetX16, offsetY16, offsetZ16, 0f, 0f, 0f);
        }

        public Beam {
            if (!Float.isFinite(width16) || !Float.isFinite(height16)
                    || !Float.isFinite(offsetX16) || !Float.isFinite(offsetY16) || !Float.isFinite(offsetZ16)
                    || !Float.isFinite(yawDegrees) || !Float.isFinite(pitchDegrees) || !Float.isFinite(rollDegrees)) {
                throw new IllegalArgumentException("Beam dimensions, offsets and rotations must be finite");
            }
            if (width16 <= 0f || height16 <= 0f) {
                throw new IllegalArgumentException("Beam width and height must be positive");
            }
        }
    }

    /**
     * Config overrides take precedence over built-ins, including explicitly empty beam lists.
     * Returns an empty list for an ID with neither a config override nor a built-in profile.
     */
    public static List<Beam> beams(ResourceLocation frameId) {
        return FrameLightOverrides.beams(frameId, PROFILES.getOrDefault(frameId, List.of()));
    }

    private static Map<ResourceLocation, List<Beam>> createProfiles() {
        Map<ResourceLocation, List<Beam>> profiles = new HashMap<>();

        // Automobility 0.4.2 built-in frames.
        profiles.put(id("automobility", "wooden_motorcar"), List.of(MOTORCAR_LEFT, MOTORCAR_RIGHT));
        profiles.put(id("automobility", "copper_motorcar"), List.of(MOTORCAR_LEFT, MOTORCAR_RIGHT));
        profiles.put(id("automobility", "steel_motorcar"), List.of(MOTORCAR_LEFT, MOTORCAR_RIGHT));
        profiles.put(id("automobility", "golden_motorcar"), List.of(MOTORCAR_LEFT, MOTORCAR_RIGHT));
        profiles.put(id("automobility", "bejeweled_motorcar"), List.of(MOTORCAR_LEFT, MOTORCAR_RIGHT));
        profiles.put(id("automobility", "standard_white"), List.of());
        profiles.put(id("automobility", "standard_orange"), List.of());
        profiles.put(id("automobility", "standard_magenta"), List.of());
        profiles.put(id("automobility", "standard_light_blue"), List.of());
        profiles.put(id("automobility", "standard_yellow"), List.of());
        profiles.put(id("automobility", "standard_lime"), List.of());
        profiles.put(id("automobility", "standard_pink"), List.of());
        profiles.put(id("automobility", "standard_gray"), List.of());
        profiles.put(id("automobility", "standard_light_gray"), List.of());
        profiles.put(id("automobility", "standard_cyan"), List.of());
        profiles.put(id("automobility", "standard_purple"), List.of());
        profiles.put(id("automobility", "standard_blue"), List.of());
        profiles.put(id("automobility", "standard_brown"), List.of());
        profiles.put(id("automobility", "standard_green"), List.of());
        profiles.put(id("automobility", "standard_red"), List.of());
        profiles.put(id("automobility", "standard_black"), List.of());
        profiles.put(id("automobility", "amethyst_rickshaw"), List.of(RICKSHAW));
        profiles.put(id("automobility", "quartz_rickshaw"), List.of(RICKSHAW));
        profiles.put(id("automobility", "prismarine_rickshaw"), List.of(RICKSHAW));
        profiles.put(id("automobility", "echo_rickshaw"), List.of(RICKSHAW));
        profiles.put(id("automobility", "red_tractor"), List.of());
        profiles.put(id("automobility", "yellow_tractor"), List.of());
        profiles.put(id("automobility", "green_tractor"), List.of());
        profiles.put(id("automobility", "blue_tractor"), List.of());
        profiles.put(id("automobility", "shopping_cart"), List.of());
        profiles.put(id("automobility", "c_arr"), List.of());
        profiles.put(id("automobility", "pineapple"), List.of());

        // RIAutomobility 1.0.4b built-in frames.
        profiles.put(id("riautomobility", "wooden_doublemotorcar"), List.of(MOTORCAR_DOUBLE_LEFT, MOTORCAR_DOUBLE_RIGHT));
        profiles.put(id("riautomobility", "copper_doublemotorcar"), List.of(MOTORCAR_DOUBLE_LEFT, MOTORCAR_DOUBLE_RIGHT));
        profiles.put(id("riautomobility", "steel_doublemotorcar"), List.of(MOTORCAR_DOUBLE_LEFT, MOTORCAR_DOUBLE_RIGHT));
        profiles.put(id("riautomobility", "golden_doublemotorcar"), List.of(MOTORCAR_DOUBLE_LEFT, MOTORCAR_DOUBLE_RIGHT));
        profiles.put(id("riautomobility", "bejeweled_doublemotorcar"), List.of(MOTORCAR_DOUBLE_LEFT, MOTORCAR_DOUBLE_RIGHT));
        profiles.put(id("riautomobility", "wooden_quadmotorcar"), List.of(MOTORCAR_QUAD_LEFT, MOTORCAR_QUAD_RIGHT));
        profiles.put(id("riautomobility", "copper_quadmotorcar"), List.of(MOTORCAR_QUAD_LEFT, MOTORCAR_QUAD_RIGHT));
        profiles.put(id("riautomobility", "steel_quadmotorcar"), List.of(MOTORCAR_QUAD_LEFT, MOTORCAR_QUAD_RIGHT));
        profiles.put(id("riautomobility", "golden_quadmotorcar"), List.of(MOTORCAR_QUAD_LEFT, MOTORCAR_QUAD_RIGHT));
        profiles.put(id("riautomobility", "bejeweled_quadmotorcar"), List.of(MOTORCAR_QUAD_LEFT, MOTORCAR_QUAD_RIGHT));
        profiles.put(id("riautomobility", "lorry"), List.of(LORRY_LEFT,LORRY_RIGHT));
        profiles.put(id("riautomobility", "dmc12"), List.of(DMC12_LEFT,DMC12_RIGHT));
        profiles.put(id("riautomobility", "standard_formula"), List.of());

        return Map.copyOf(profiles);
    }

    private static ResourceLocation id(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
}
