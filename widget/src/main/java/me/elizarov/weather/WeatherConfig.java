package me.elizarov.weather;

import android.content.Context;
import android.content.SharedPreferences;

public final class WeatherConfig {
    public final String fallbackStation = "27612";
    public final boolean automatic;
    public final String cityPath, cityName, key;

    private WeatherConfig(boolean automatic, String cityPath, String cityName) {
        this.automatic = automatic;
        this.cityPath = cityPath;
        this.cityName = cityName;
        key = fallbackStation + "|" + automatic + "|" + cityPath;
    }

    static SharedPreferences settings(Context context) {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static WeatherConfig read(Context context) {
        SharedPreferences p = settings(context);
        return new WeatherConfig(p.getBoolean("automatic", false),
                p.getString("city_path", ""), p.getString("city_name", "Москва"));
    }

    static void select(Context context, LocationCatalog.Place place) {
        settings(context).edit().putString("city_path", place.path).putString("city_name", place.name).commit();
        WeatherRepository.configurationChanged(context);
    }

    static void automatic(Context context, boolean automatic) {
        settings(context).edit().putBoolean("automatic", automatic).commit();
        WeatherRepository.configurationChanged(context);
    }

}
