package com.liziluyu.autoillumination.client;

import com.liziluyu.autoillumination.AutoIllumination;
import com.liziluyu.autoillumination.client.beam.BeamRenderer;
import com.liziluyu.autoillumination.client.beam.BeamStyle;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A client-only, temporary world-space preview of the reusable beam renderer. */
@Mod.EventBusSubscriber(modid = AutoIllumination.MOD_ID, value = Dist.CLIENT)
public final class BeamPreview {
    private static final int DEFAULT_RANGE = 16;
    private static final int DEFAULT_SIGNAL = 8;
    private static final int DEFAULT_COLOR = 0xFFFCC6;
    private static final DynamicCommandExceptionType INVALID_COLOR = new DynamicCommandExceptionType(
        value -> Component.translatable("commands.autoillumination.beam.invalid_color", value));
    private static final SimpleCommandExceptionType NO_PLAYER = new SimpleCommandExceptionType(
        Component.translatable("commands.autoillumination.beam.no_player"));

    private static Preview preview;
    private static MultiBufferSource.BufferSource buffers;

    private BeamPreview() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("autoillumination")
            .then(Commands.literal("beam")
                .then(Commands.literal("preview")
                    .executes(context -> show(context.getSource(), DEFAULT_RANGE, DEFAULT_SIGNAL, DEFAULT_COLOR))
                    .then(Commands.argument("range", IntegerArgumentType.integer(2, 128))
                        .executes(context -> show(context.getSource(),
                            IntegerArgumentType.getInteger(context, "range"), DEFAULT_SIGNAL, DEFAULT_COLOR))
                        .then(Commands.argument("signal", IntegerArgumentType.integer(0, 15))
                            .executes(context -> show(context.getSource(),
                                IntegerArgumentType.getInteger(context, "range"),
                                IntegerArgumentType.getInteger(context, "signal"), DEFAULT_COLOR))
                            .then(Commands.argument("color", StringArgumentType.word())
                                .executes(context -> show(context.getSource(),
                                    IntegerArgumentType.getInteger(context, "range"),
                                    IntegerArgumentType.getInteger(context, "signal"),
                                    parseColor(StringArgumentType.getString(context, "color"))))))))
                .then(Commands.literal("clear").executes(context -> {
                    preview = null;
                    ClientBeamLights.requestPreviewRefresh();
                    context.getSource().sendSuccess(
                        () -> Component.translatable("commands.autoillumination.beam.cleared"), false);
                    return 1;
                }))));
    }

    private static int show(CommandSourceStack source, int range, int signal, int color)
            throws CommandSyntaxException {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            throw NO_PLAYER.create();
        }

        BeamStyle style = BeamStyle.spotlight(range, signal, color);
        Vec3 direction = player.getLookAngle().normalize();
        // Capturing these once lets the player walk around the beam without moving it.
        Vec3 origin = player.getEyePosition().add(direction);
        preview = new Preview(level, origin, direction, style);
        ClientBeamLights.requestPreviewRefresh();
        source.sendSuccess(() -> Component.translatable("commands.autoillumination.beam.preview",
            range, signal, String.format(Locale.ROOT, "%06X", color)), false);
        return 1;
    }

    private static int parseColor(String value) throws CommandSyntaxException {
        if (!value.matches("[0-9a-fA-F]{6}")) {
            throw INVALID_COLOR.create(value);
        }
        return Integer.parseInt(value, 16);
    }

    /** Collects the active preview's world-space origin and clipped endpoint, if present in this level. */
    public static void collectLightEndpoints(ClientLevel level, Consumer<Vec3> endpoint) {
        Preview current = preview;
        if (level == null || endpoint == null || current == null || current.level() != level
                || current.style().opacity() <= 0f) {
            return;
        }
        float length = BeamRenderer.clipLength(level, current.origin(), current.direction(),
            current.style().range(), Minecraft.getInstance().player);
        if (length > 0f) {
            endpoint.accept(current.origin());
            endpoint.accept(current.origin().add(current.direction().scale(length)));
        }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && preview != null
                && Minecraft.getInstance().level != preview.level()) {
            preview = null;
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || preview == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Preview current = preview;
        if (minecraft.level != current.level()) {
            preview = null;
            return;
        }
        if (current.style().opacity() <= 0f || !BeamRenderer.isReady()) {
            return;
        }

        float length = BeamRenderer.clipLength(current.level(), current.origin(), current.direction(),
            current.style().range(), minecraft.player);
        if (length <= 0f) {
            return;
        }
        Vec3 end = current.origin().add(current.direction().scale(length));
        double radius = current.style().boundingRadius(length);
        if (!event.getFrustum().isVisible(new AABB(current.origin(), end).inflate(radius))) {
            return;
        }

        if (buffers == null) {
            buffers = MultiBufferSource.immediate(new BufferBuilder(1536));
        }
        try {
            BeamRenderer.renderWorld(event.getPoseStack(), buffers, current.style(), current.origin(),
                current.direction(), event.getCamera().getPosition(), length);
        } finally {
            // This batch belongs to the preview; do not flush Minecraft's shared render buffers.
            buffers.endBatch(BeamRenderer.renderType());
        }
    }

    private record Preview(ClientLevel level, Vec3 origin, Vec3 direction, BeamStyle style) {
    }
}
