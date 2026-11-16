package me.elizarov.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import java.util.Locale;

/**
 * Exercises the transmitted form and station cache without any network requests.
 */
final class LocationPolicyChecks {
    static void run(Context context) {
        double[][] places = {{0, 0}, {59.934321, 30.335123}, {-33.8688, 151.2093},
                {89.999, 179.999}, {-89.999, -179.999}, {0, 179.999}, {0, -179.999}};
        for (double[] place : places) {
            LocationPolicy.Coordinates rounded = LocationPolicy.rounded(place[0], place[1]);
            require(rounded.latitude >= -90 && rounded.latitude <= 90
                    && rounded.longitude >= -180 && rounded.longitude < 180, "valid grid coordinates");
            float[] distance = new float[1];
            Location.distanceBetween(place[0], place[1], rounded.latitude, rounded.longitude, distance);
            require(distance[0] <= 4000, "nearest point of the approximately 5 km grid");
            LocationPolicy.Coordinates repeated = LocationPolicy.rounded(rounded.latitude, rounded.longitude);
            require(repeated.latitude == rounded.latitude && repeated.longitude == rounded.longitude,
                    "rounding is stable, including the poles and date line");
        }

        LocationPolicy.Coordinates centre = LocationPolicy.rounded(59.934321, 30.335123);
        Location first = location(centre.latitude + 0.001, centre.longitude + 0.001);
        Location second = location(centre.latitude - 0.001, centre.longitude - 0.001);
        String form = LocationPolicy.stationRequest(first);
        require(form.equals(LocationPolicy.stationRequest(second)), "nearby precise points produce identical request coordinates");
        require(!form.contains(Double.toString(first.getLatitude()))
                && !form.contains(Double.toString(first.getLongitude())), "request never contains the original precise values");
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            require(form.equals(LocationPolicy.stationRequest(first)), "form coordinates always use a decimal point");
        } finally {
            Locale.setDefault(original);
        }
        boolean rejected = false;
        try {
            LocationPolicy.rounded(Double.NaN, 0);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "invalid coordinates are never sent");

        SharedPreferences cache = context.getSharedPreferences("location_policy_test", Context.MODE_PRIVATE);
        cache.edit().clear().commit();
        try {
            Location start = location(0, 0);
            require(!LocationPolicy.canReuseStation(cache, start), "first lookup needs a station");
            cache.edit().putString("located_station", "27612").putLong("located_lat", 0)
                    .putLong("located_lon", 0).putLong("located_at", System.currentTimeMillis()).commit();
            require(!LocationPolicy.canReuseStation(cache, start), "legacy precise lookup cache is not reused");
            LocationPolicy.rememberStation(cache, start, "27612");
            cache.edit().putLong("located_at", 1).commit();
            require(LocationPolicy.canReuseStation(cache, start), "no stationary requery after six hours or any cache age");
            require(LocationPolicy.canReuseStation(cache, location(0.0899, 0)), "movement below 10 km reuses the station");
            require(!LocationPolicy.canReuseStation(cache, location(0.091, 0)), "movement above 10 km requests a new station");
            cache.edit().remove("located_lon").commit();
            require(!LocationPolicy.canReuseStation(cache, start), "incomplete location cache needs a lookup");
        } finally {
            cache.edit().clear().commit();
        }
    }

    private static Location location(double latitude, double longitude) {
        Location location = new Location("test");
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        return location;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
