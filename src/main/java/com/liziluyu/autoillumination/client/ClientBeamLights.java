package com.liziluyu.autoillumination.client;

import com.liziluyu.autoillumination.AutoIllumination;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Virtual emitters in client light storage only: never places blocks or sends packets. */
@Mod.EventBusSubscriber(modid = AutoIllumination.MOD_ID, value = Dist.CLIENT)
public final class ClientBeamLights {
    public static final int UPDATE_INTERVAL = 5;
    public static final int LIGHT_LEVEL = 15;
    private static Snapshot active = new Snapshot(null, Map.of(), Set.of());
    private static final Map<Long, Long> recentRemovals = new HashMap<>();
    private static long gameTicks;
    private static int ticks;

    private ClientBeamLights() {
    }

    /** Called only by the client block light engine; integrated-server levels never match. */
    public static int emission(ClientLevel level, long position) {
        Snapshot current = active;
        return current.level() == level && current.references().containsKey(position) ? LIGHT_LEVEL : 0;
    }

    /** Empty sections containing emitters still need allocated block-light storage. */
    public static boolean retainsSection(ClientLevel level, long section) {
        Snapshot current = active;
        return current.level() == level && current.sections().contains(section)
            && level.hasChunk(SectionPos.x(section), SectionPos.z(section));
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (active.level() != level) {
            active = new Snapshot(level, Map.of(), Set.of());
            recentRemovals.clear();
            gameTicks = 0;
            ticks = UPDATE_INTERVAL - 1;
        }
        if (level == null || minecraft.isPaused()) {
            return;
        }
        gameTicks++;
        recentRemovals.values().removeIf(expiry -> expiry <= gameTicks);
        if (++ticks < UPDATE_INTERVAL) {
            return;
        }
        ticks = 0;

        Map<Long, Integer> next = new HashMap<>();
        Consumer<Vec3> collect = point -> {
            if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
                return;
            }
            BlockPos position = BlockPos.containing(point);
            if (!level.isOutsideBuildHeight(position) && level.hasChunk(position.getX() >> 4, position.getZ() >> 4)) {
                // Count endpoints, so sharing one block never makes one beam own another's light.
                next.merge(position.asLong(), 1, Integer::sum);
            }
        };
        AutomobileHeadlights.collectLightEndpoints(level, collect);
        BeamPreview.collectLightEndpoints(level, collect);
        replace(level, next);
    }

    private static void replace(ClientLevel level, Map<Long, Integer> next) {
        Snapshot previous = active;
        for (long position : previous.references().keySet()) {
            if (!next.containsKey(position)) {
                recentRemovals.put(position, gameTicks + 20);
            }
        }
        Set<Long> sections = new HashSet<>();
        next.keySet().forEach(position -> sections.add(SectionPos.blockToSection(position)));
        // Publish before scheduling checks: removal must immediately return vanilla emission again.
        active = new Snapshot(level, Map.copyOf(next), Set.copyOf(sections));
        LayerLightEventListener blockLight = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        for (long section : sections) {
            blockLight.updateSectionStatus(SectionPos.of(section), false);
        }
        for (long section : previous.sections()) {
            if (!sections.contains(section)) {
                SectionPos position = SectionPos.of(section);
                LevelChunk chunk = level.getChunkSource().getChunk(position.x(), position.z(), false);
                boolean empty = chunk == null
                    || chunk.getSection(level.getSectionIndexFromSectionY(position.y())).hasOnlyAir();
                blockLight.updateSectionStatus(position, empty);
            }
        }
        Set<Long> checks = new HashSet<>(previous.references().keySet());
        checks.addAll(next.keySet());
        // Recheck stationary sources too, including those whose chunk lighting just became ready.
        // checkBlock only queues work; Minecraft runs propagation and renderer notifications normally.
        checks.forEach(position -> blockLight.checkBlock(BlockPos.of(position)));
    }

    /** Called after packet layers have been applied, never before their storage replacement. */
    public static void recheckPacketSections(ClientLevel level, Set<Long> sections,
            LayerLightEventListener blockLight) {
        if (active.level() != level || (active.references().isEmpty() && recentRemovals.isEmpty())) {
            return;
        }
        Set<Long> anchors = new HashSet<>(active.references().keySet());
        anchors.addAll(recentRemovals.keySet());
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (long section : sections) {
            // Include a one-block border: after a removal, stale light can remain just outside
            // a replaced layer whose authoritative zeroes broke the original decrease wave.
            int minX = SectionPos.sectionToBlockCoord(SectionPos.x(section)) - 1;
            int minY = SectionPos.sectionToBlockCoord(SectionPos.y(section)) - 1;
            int minZ = SectionPos.sectionToBlockCoord(SectionPos.z(section)) - 1;
            List<BlockPos> nearby = anchors.stream().map(BlockPos::of)
                .filter(anchor -> distanceToInterval(anchor.getX(), minX, minX + 17)
                    + distanceToInterval(anchor.getY(), minY, minY + 17)
                    + distanceToInterval(anchor.getZ(), minZ, minZ + 17) < LIGHT_LEVEL).toList();
            if (nearby.isEmpty()) {
                continue;
            }
            for (int x = minX; x <= minX + 17; x++) {
                for (int z = minZ; z <= minZ + 17; z++) {
                    if (!level.hasChunk(x >> 4, z >> 4)) {
                        continue;
                    }
                    for (int y = minY; y <= minY + 17; y++) {
                        position.set(x, y, z);
                        if (level.isOutsideBuildHeight(position)) {
                            continue;
                        }
                        for (BlockPos anchor : nearby) {
                            if (anchor.distManhattan(position) < LIGHT_LEVEL) {
                                // Sources alone cannot repair an overwritten layer beyond intact,
                                // equally bright neighbors. Pull/rebuild all affected cells instead.
                                blockLight.checkBlock(position);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static int distanceToInterval(int value, int min, int max) {
        return Math.max(0, Math.max(min - value, value - max));
    }

    @SubscribeEvent
    public static void chunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() != active.level()) {
            return;
        }
        var chunk = event.getChunk().getPos();
        Map<Long, Integer> next = new HashMap<>(active.references());
        boolean changed = next.keySet().removeIf(position -> (BlockPos.getX(position) >> 4) == chunk.x
            && (BlockPos.getZ(position) >> 4) == chunk.z);
        if (changed) {
            replace(active.level(), next);
        }
    }

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() == active.level()) {
            // This entire light engine is discarded with its level; retain no world/entity references.
            active = new Snapshot(null, Map.of(), Set.of());
            recentRemovals.clear();
            gameTicks = 0;
            ticks = 0;
        }
    }

    private record Snapshot(ClientLevel level, Map<Long, Integer> references, Set<Long> sections) {
    }
}
