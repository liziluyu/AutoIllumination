package com.liziluyu.autoillumination;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(AutoIllumination.MOD_ID)
public final class AutoIllumination {
    public static final String MOD_ID = "autoillumination";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AutoIllumination() {
        LOGGER.info("Auto Illumination initialized");
    }
}
