package me.elizarov.weather;

import java.util.Locale;

final class WeatherIcons {
    private WeatherIcons() {
    }

    static int forCondition(String description, boolean night) {
        String s = description.toLowerCase(Locale.ROOT);
        if (s.contains("гроз")) return R.drawable.weather_thunder;
        if (s.contains("снег") && s.contains("дожд")) return R.drawable.weather_sleet;
        if (s.contains("снег") || s.contains("метел")) return R.drawable.weather_snow;
        if (s.contains("дожд") || s.contains("морось") || s.contains("осадки"))
            return R.drawable.weather_rain;
        if (s.contains("туман") || s.contains("дымк")) return R.drawable.weather_fog;
        if (s.contains("ясно") || s.contains("малооблачно"))
            return night ? R.drawable.weather_moon : R.drawable.weather_sun;
        if (s.contains("переменная") || s.contains("прояснен"))
            return night ? R.drawable.weather_cloud : R.drawable.weather_partly;
        if (s.contains("облач")) return R.drawable.weather_cloud;
        return R.drawable.weather_unknown;
    }
}
