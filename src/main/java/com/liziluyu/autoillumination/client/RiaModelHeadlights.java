package com.liziluyu.autoillumination.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liziluyu.autoillumination.AutoIllumination;
import com.liziluyu.autoillumination.client.FrameLightProfiles.Beam;
import com.sshakusora.riautomobility.content.FrameSpec;
import com.sshakusora.riautomobility.content.FrameSpec.ModelSpec;
import com.sshakusora.riautomobility.content.RIAutomobilityComponentManager;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.event.TickEvent;

/** Loaded exclusively through RiaHeadlightCompat after the optional mod check. */
public final class RiaModelHeadlights {
    private static Map<ResourceLocation, FrameSpec> frames = Map.of();
    private static final Map<ResourceLocation, CachedProfile> profiles = new HashMap<>();

    private RiaModelHeadlights() {
    }

    /** Resource reloads discard both successful and missing-model results. */
    public static void reload(ResourceManager resources) {
        profiles.clear();
    }

    /** RIA synchronizes frame definitions on joining and datapack reload, independently of assets. */
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            frames = Map.of();
            profiles.clear();
            return;
        }
        frames = RIAutomobilityComponentManager.getCustomFrames();
        profiles.keySet().retainAll(frames.keySet());
    }

    public static List<Beam> beams(ResourceLocation frameId) {
        FrameSpec frame = frames.get(frameId);
        if (frame == null) {
            return List.of();
        }
        ModelSpec model = frame.model();
        CachedProfile cached = profiles.get(frameId);
        if (cached == null || !cached.model().equals(model)) {
            cached = new CachedProfile(model, readModel(frameId, model));
            profiles.put(frameId, cached);
        }
        return cached.beams();
    }

    private static List<Beam> readModel(ResourceLocation frameId, ModelSpec model) {
        ResourceLocation location;
        if (model.isGeckoLib()) {
            location = model.geoModel();
        } else {
            ResourceLocation layer = model.layerLocation();
            location = layer == null ? null : new ResourceLocation(layer.getNamespace(),
                "models/entity/" + layer.getPath() + "/main.json");
        }
        if (location == null) {
            return List.of();
        }
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(location).orElse(null);
        if (resource == null) {
            return List.of();
        }
        try (Reader reader = resource.openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return model.isGeckoLib() ? HeadlightModelGeometry.gecko(json)
                : HeadlightModelGeometry.jsonEm(json, model.rotationY());
        } catch (IOException | RuntimeException exception) {
            // Cache a failed resource too: malformed packs must not log or parse every frame.
            AutoIllumination.LOGGER.warn("Cannot read headlight markers for frame {} from {}", frameId,
                location, exception);
            return List.of();
        }
    }

    private record CachedProfile(ModelSpec model, List<Beam> beams) {
    }
}
