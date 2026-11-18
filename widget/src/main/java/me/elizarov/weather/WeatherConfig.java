package me.elizarov.weather;

import android.content.Context;
import android.content.SharedPreferences;

public final class WeatherConfig {
    public static final String DEFAULT_STATION = "27612";
    public final String fallbackStation = DEFAULT_STATION;
    public final boolean automatic;
    final WeatherSource source;
    public final String cityPath, cityName, key, apiKey;

    private WeatherConfig(boolean automatic, String cityPath, String cityName, WeatherSource source, String apiKey) {
        this.automatic = automatic;
        this.cityPath = cityPath;
        this.cityName = cityName;
        this.source = source;
        this.apiKey = apiKey;
        key = fallbackStation + "|" + automatic + "|" + cityPath;
    }

    static SharedPreferences settings(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static WeatherConfig read(Context context) {
        SharedPreferences p = settings(context);
        WeatherSource source = WeatherSource.fromId(p.getString("source", "hmc"));
        return new WeatherConfig(p.getBoolean("automatic", false),
                p.getString("city_path", ""), p.getString("city_name", "Москва"),
                source, p.getString(source.provider.apiKeyPreference(), ""));
    }

    boolean isCurrent(Context context) {
        WeatherConfig current = read(context);
        return source == current.source && key.equals(current.key)
                && apiKey.equals(current.apiKey);
    }

    static void apiKey(Context context, String value) {
        WeatherConfig config = read(context);
        ForecastProvider<?> provider = config.source.provider;
        String key = value.trim();
        if (!provider.requiresApiKey() || config.apiKey.equals(key)) return;
        settings(context).edit().putString(provider.apiKeyPreference(), key).commit();
        if (key.isEmpty()) provider.clearForecast(context);
        WeatherRepository.configurationChanged(context);
    }

    static void source(Context context, WeatherSource source) {
        SharedPreferences prefs = settings(context);
        if (source == WeatherSource.fromId(prefs.getString("source", "hmc"))) return;
        prefs.edit().putString("source", source.provider.id).commit();
        WeatherRepository.configurationChanged(context);
    }

    static void select(Context context, String cityPath, String cityName) {
        settings(context).edit().putString("city_path", cityPath).putString("city_name", cityName).commit();
        WeatherRepository.configurationChanged(context);
    }

    static void automatic(Context context, boolean automatic) {
        settings(context).edit().putBoolean("automatic", automatic).commit();
        WeatherRepository.configurationChanged(context);
    }

}
