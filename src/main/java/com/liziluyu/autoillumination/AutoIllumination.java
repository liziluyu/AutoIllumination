package com.liziluyu.autoillumination;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(AutoIllumination.MOD_ID)
public final class AutoIllumination {
    public static final String MOD_ID = "autoillumination";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AutoIllumination() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HeadlightConfig.SPEC);
        LOGGER.info("Auto Illumination initialized");
    }
}
