package net.octa.octapass.util;

import java.time.Duration;

public final class TimeUtil {
    private TimeUtil() {}
    public static String compact(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long days = seconds / 86400; seconds %= 86400;
        long hours = seconds / 3600; seconds %= 3600;
        long minutes = seconds / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
