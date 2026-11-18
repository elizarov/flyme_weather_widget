package me.elizarov.weather;

import android.content.Context;

import java.util.Locale;

/**
 * Both providers use the same official Yandex artwork, without recolouring it.
 */
public final class WeatherIcons {
    private WeatherIcons() {
    }

    public static int resource(Context context, String icon, boolean lightText) {
        String code = icon.replace("+", "_plus_").replace("-", "_minus_").toLowerCase(Locale.ROOT);
        return context.getResources().getIdentifier("yandex_" + (lightText ? "dark_" : "light_") + code,
                "drawable", context.getPackageName());
    }
}
