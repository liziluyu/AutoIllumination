package com.liziluyu.autoillumination.client;

import com.liziluyu.autoillumination.AutoIllumination;
import com.liziluyu.autoillumination.HeadlightConfig;
import com.liziluyu.autoillumination.client.FrameLightProfiles.Beam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Client config overrides parsed once and atomically replaced on load/reload. */
@Mod.EventBusSubscriber(modid = AutoIllumination.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FrameLightOverrides {
    private static volatile Map<ResourceLocation, List<Beam>> profiles = Map.of();

    private FrameLightOverrides() {
    }

    /** Returns null for an absent override; an explicitly empty list disables every lamp source. */
    public static List<Beam> override(ResourceLocation frameId) {
        return profiles.get(frameId);
    }

    /** Config overrides take precedence over every detected or built-in profile. */
    public static List<Beam> beams(ResourceLocation frameId, List<Beam> builtIn) {
        return profiles.getOrDefault(frameId, builtIn);
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
        if (event.getConfig().getSpec() != HeadlightConfig.SPEC) {
            return;
        }
        Map<ResourceLocation, List<Beam>> next = new HashMap<>();
        List<? extends String> definitions = HeadlightConfig.frameOverrides();
        for (int index = 0; index < definitions.size(); index++) {
            String definition = definitions.get(index);
            try {
                String[] parts = definition.split("\\|", -1);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Expected namespace:frame_id | beam; beam");
                }
                String idText = parts[0].trim();
                ResourceLocation id = ResourceLocation.tryParse(idText);
                if (idText.indexOf(':') <= 0 || id == null) {
                    throw new IllegalArgumentException("Frame ID must be a valid, complete namespace:path");
                }
                List<Beam> beams = new ArrayList<>();
                if (!parts[1].isBlank()) {
                    for (String beam : parts[1].split(";", -1)) {
                        beams.add(parseBeam(beam.trim()));
                    }
                }
                // Validate the whole profile before publishing it, so a malformed lamp cannot
                // partially replace either the built-in profile or a previous valid override.
                if (next.put(id, List.copyOf(beams)) != null) {
                    AutoIllumination.LOGGER.warn("Duplicate frameOverrides ID {}: using the last valid entry", id);
                }
            } catch (IllegalArgumentException exception) {
                AutoIllumination.LOGGER.warn("Ignoring frameOverrides entry {}: {} ({})",
                    index + 1, definition, exception.getMessage());
            }
        }
        profiles = Map.copyOf(next);
    }

    private static Beam parseBeam(String definition) {
        String[] components = definition.split(",", -1);
        if (components.length != 5 && components.length != 8) {
            throw new IllegalArgumentException("Each beam needs 5 values (size/offset) or 8 values (including rotation)");
        }
        float[] values = new float[8];
        for (int index = 0; index < components.length; index++) {
            values[index] = Float.parseFloat(components[index].trim());
        }
        return new Beam(values[0], values[1], values[2], values[3], values[4],
            values[5], values[6], values[7]);
    }
}
