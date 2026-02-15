package io.github.m4gshm;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class VirtualThreadSchedulerUtils {

    public static final String VT_PARALLELISM_DELTA = "VT_PARALLELISM_DELTA";

    public static void initVirtualThreadScheduler() {
        var delta = System.getenv(VT_PARALLELISM_DELTA);
        if (delta != null) try {
            var deltaI = Integer.parseInt(delta);
            var parallelism = String.valueOf(Runtime.getRuntime().availableProcessors() + deltaI);
            var key = "jdk.virtualThreadScheduler.parallelism";
            log.info("{} = {}", key, parallelism);
            System.setProperty(key, parallelism);
        } catch (NumberFormatException _) {
            log.error("invalid " + VT_PARALLELISM_DELTA + " value {}", delta);
        }
    }
}
