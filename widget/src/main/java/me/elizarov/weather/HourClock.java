package me.elizarov.weather;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Calendar hours follow the Android timezone, including fractional offsets and DST.
 */
public final class HourClock {
    public static final long HOUR = 3600000L;

    private HourClock() {
    }

    public static ZonedDateTime at(long now) {
        return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault());
    }

    static long start(long now) {
        return at(now).truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli();
    }

    static long next(long now) {
        return at(now).truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli();
    }

    static boolean same(long first, long second) {
        return first > 0 && start(first) == start(second);
    }
}
