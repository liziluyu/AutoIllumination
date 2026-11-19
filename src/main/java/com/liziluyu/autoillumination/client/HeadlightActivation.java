package com.liziluyu.autoillumination.client;

import com.liziluyu.autoillumination.AutoIllumination;
import io.github.foundationgames.automobility.entity.AutomobileEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Shared vehicle headlight policy, with ambient light isolated from client virtual emitters. */
@Mod.EventBusSubscriber(modid = AutoIllumination.MOD_ID, value = Dist.CLIENT)
public final class HeadlightActivation {
    private static final int DARK_THRESHOLD = 5;
    private static final long DAY_LENGTH = 24000L;
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;
    private static final Map<Long, DataLayer> serverBlockLight = new HashMap<>();
    private static ClientLevel cachedLevel;

    private HeadlightActivation() {
    }

    public static boolean shouldEnable(ClientLevel level, AutomobileEntity automobile) {
        boolean occupied = false;
        // Include nested seat/passenger entities, not just the controlling driver.
        for (Entity passenger : automobile.getIndirectPassengers()) {
            if (passenger instanceof LivingEntity living && living.isAlive()) {
                occupied = true;
                break;
            }
        }
        if (!occupied) {
            return false;
        }
        if (isNightByTime(level)) {
            return true;
        }
        BlockPos position = automobile.blockPosition();
        if (level.isOutsideBuildHeight(position) || !level.hasChunk(position.getX() >> 4, position.getZ() >> 4)) {
            return false;
        }
        int sky = level.getBrightness(LightLayer.SKY, position) - currentSkyDarkening(level);
        DataLayer data = cachedLevel == level ? serverBlockLight.get(SectionPos.asLong(position)) : null;
        // Missing server light layers, like empty packet layers, have zero block light.
        int block = data == null ? 0 : data.get(SectionPos.sectionRelative(position.getX()),
            SectionPos.sectionRelative(position.getY()), SectionPos.sectionRelative(position.getZ()));
        return Math.max(sky, block) < DARK_THRESHOLD;
    }

    private static boolean isNightByTime(ClientLevel level) {
        // Level.isNight() reads skyDarken, which ClientLevel does not refresh as time advances.
        // Fixed-time dimensions have no day/night cycle; their ambient light still applies below.
        if (level.dimensionType().hasFixedTime()) {
            return false;
        }
        long time = Math.floorMod(level.getDayTime(), DAY_LENGTH);
        return time >= NIGHT_START && time < NIGHT_END;
    }

    private static int currentSkyDarkening(ClientLevel level) {
        // Same calculation as Level.updateSkyBrightness(), evaluated without mutating client state
        // or reading its stale getSkyDarken() cache. Dimension time includes fixed-time overrides.
        double rain = 1d - level.getRainLevel(1f) * 5d / 16d;
        double thunder = 1d - level.getThunderLevel(1f) * 5d / 16d;
        double sun = 0.5d + 2d * Mth.clamp(
            (double) Mth.cos(level.getTimeOfDay(1f) * ((float) Math.PI * 2f)), -0.25d, 0.25d);
        return (int) ((1d - sun * rain * thunder) * 11d);
    }

    /** Snapshot incoming block light before virtual propagation can modify client storage. */
    public static void recordServerBlockLight(ClientLevel level, long section, DataLayer data) {
        if (cachedLevel != level) {
            serverBlockLight.clear();
            cachedLevel = level;
        }
        if (data == null) {
            serverBlockLight.remove(section);
        } else {
            serverBlockLight.put(section, data.copy());
        }
    }

    @SubscribeEvent
    public static void chunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() == cachedLevel) {
            var chunk = event.getChunk().getPos();
            serverBlockLight.keySet().removeIf(section -> SectionPos.x(section) == chunk.x
                && SectionPos.z(section) == chunk.z);
        }
    }

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() == cachedLevel) {
            serverBlockLight.clear();
            cachedLevel = null;
        }
    }
}
