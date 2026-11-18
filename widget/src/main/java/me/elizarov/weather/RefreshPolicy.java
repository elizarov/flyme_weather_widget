package me.elizarov.weather;

import android.content.SharedPreferences;

final class RefreshPolicy {
    static final float MOVED_METERS = 10000;

    private RefreshPolicy() {
    }

    static boolean hasFreshForecast(SharedPreferences cache, String target, long now) {
        return cache.contains("forecast") && target.equals(cache.getString("target", ""))
                && HourClock.same(cache.getLong("fetched_at", 0), now);
    }

    static long retryDelay(SharedPreferences cache, long now) {
        return HourClock.same(cache.getLong("attempted_at", 0), now) ? HourClock.next(now) - now : 0;
    }

    static synchronized boolean recordAttempt(SharedPreferences cache, String target, long now) {
        if (retryDelay(cache, now) > 0 && target.equals(cache.getString("attempted_target", "")))
            return false;
        return cache.edit().putLong("attempted_at", now).putString("attempted_target", target).commit();
    }
}
