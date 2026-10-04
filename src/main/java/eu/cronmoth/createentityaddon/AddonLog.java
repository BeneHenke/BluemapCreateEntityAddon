package eu.cronmoth.createentityaddon;

import de.bluecolored.bluemap.core.logger.Logger;

public class AddonLog {

    private static final String PREFIX = "[createentityaddon] ";

    private AddonLog() {}

    public static void info(String message) {
        Logger.global.logInfo(PREFIX + message);
    }

    public static void warn(String message) {
        Logger.global.logWarning(PREFIX + message);
    }

    public static void error(String message, Throwable t) {
        Logger.global.logError(PREFIX + message, t);
    }

    public static void warnOnce(String key, String message) {
        Logger.global.noFloodWarning(PREFIX + key, PREFIX + message);
    }
}
