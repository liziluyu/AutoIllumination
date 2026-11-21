package com.liziluyu.autoillumination.client;

import com.liziluyu.autoillumination.AutoIllumination;
import com.liziluyu.autoillumination.client.FrameLightProfiles.Beam;
import java.util.List;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Optional bridge to RIA model headlight discovery, isolated behind a loaded-mod check. */
@Mod.EventBusSubscriber(modid = AutoIllumination.MOD_ID, value = Dist.CLIENT,
    bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RiaHeadlightCompat {
    private RiaHeadlightCompat() {
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        if (ModList.get().isLoaded("riautomobility")) {
            event.registerReloadListener((ResourceManagerReloadListener) RiaModelHeadlights::reload);
            MinecraftForge.EVENT_BUS.addListener(RiaModelHeadlights::clientTick);
        }
    }

    /**
     * Returns detected RIA model lamps when RIAutomobility is loaded; otherwise returns no lamps.
     * The bridge deliberately has no RIA class references so the optional mod stays optional.
     */
    public static List<Beam> beams(ResourceLocation frameId) {
        if (!ModList.get().isLoaded("riautomobility")) {
            return List.of();
        }
        return RiaModelHeadlights.beams(frameId);
    }
}
