package com.liziluyu.autoillumination.client.beam;

import com.liziluyu.autoillumination.AutoIllumination;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraftforge.fml.ModList;

/** Optional-mod gate kept free of Iris types so installations without Oculus can load safely. */
final class OculusCompat {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private OculusCompat() {
    }

    static void apply(BeamShaderInstance shader) {
        if (!ModList.get().isLoaded("oculus")) {
            return;
        }
        try {
            OculusIntegration.apply(shader);
        } catch (LinkageError | RuntimeException exception) {
            warnOnce(exception);
        }
    }

    static void restore(BeamShaderInstance shader) {
        if (!shader.isRouted()) {
            return;
        }
        try {
            // The route only exists after the optional integration has loaded successfully.
            OculusIntegration.restore(shader);
        } catch (LinkageError | RuntimeException exception) {
            shader.endRoute();
            warnOnce(exception);
        }
    }

    static void warnOnce(Throwable exception) {
        if (WARNED.compareAndSet(false, true)) {
            AutoIllumination.LOGGER.warn("Oculus beam target routing is unavailable; Oculus routing is disabled",
                    exception);
        }
    }

    static void warnOnce(String message) {
        if (WARNED.compareAndSet(false, true)) {
            AutoIllumination.LOGGER.warn(message);
        }
    }
}
