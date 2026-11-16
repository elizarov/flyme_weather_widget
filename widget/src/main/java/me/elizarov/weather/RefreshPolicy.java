package me.elizarov.weather;

final class RefreshPolicy {
    static final long FORECAST = 3600000L, LOCATION = 900000L, RETRY = 600000L, MANUAL_GUARD = 20000L;
    static final float MOVED_METERS = 10000;

    private RefreshPolicy() {
    }

    static long nextCheck(long now, boolean automatic) {
        return now + (automatic ? LOCATION : FORECAST);
    }

    static long nextCheck(long now, boolean automatic, boolean conditionsFailed) {
        return conditionsFailed ? now + RETRY : nextCheck(now, automatic);
    }

    static boolean needsForecast(long now, long fetched, boolean force, boolean sameStation, boolean sameConfig, boolean hasXml) {
        return force || !sameStation || !sameConfig || !hasXml || now - fetched >= FORECAST;
    }
}
