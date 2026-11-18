package me.elizarov.weather;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

/**
 * Coordinates sent to the provider are grid centres, not the Android location.
 */
public final class LocationPolicy {
    static final int GRID_METERS = 5000;
    private static final double EARTH_RADIUS_METERS = 6371000;
    private static final double LATITUDE_STEP = Math.toDegrees(GRID_METERS / EARTH_RADIUS_METERS);

    public static final class Coordinates {
        public final double latitude;
        public final double longitude;

        public Coordinates(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private LocationPolicy() {
    }

    static Location recentLocation(Context context) {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return null;
        LocationManager manager = context.getSystemService(LocationManager.class);
        Location best = null;
        for (String provider : manager.getProviders(true)) {
            try {
                Location location = manager.getLastKnownLocation(provider);
                if (location != null && System.currentTimeMillis() - location.getTime() < 7200000
                        && !location.isFromMockProvider() && (best == null || location.getTime() > best.getTime())) {
                    best = location;
                }
            } catch (SecurityException ignored) {
                // A location provider can require a different permission.
            }
        }
        return best;
    }

    public static Coordinates rounded(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Invalid Android coordinates");
        }
        double lat = Math.max(-90, Math.min(90, Math.rint(latitude / LATITUDE_STEP) * LATITUDE_STEP));
        // Use the rounded latitude to choose the longitude grid. Using the original
        // latitude here would leak additional precision through the grid spacing.
        long columns = Math.max(1, Math.round(2 * Math.PI * EARTH_RADIUS_METERS
                * Math.cos(Math.toRadians(lat)) / GRID_METERS));
        double step = 360.0 / columns;
        double lon = columns == 1 ? 0 : Math.rint(longitude / step) * step;
        if (lon >= 180) lon -= 360;
        if (lon < -180) lon += 360;
        return new Coordinates(lat == 0 ? 0 : lat, lon == 0 ? 0 : lon);
    }

    public static boolean canReuseStation(SharedPreferences cache, Location location) {
        if (cache.getString("located_station", "").isEmpty()
                || cache.getInt("located_grid_meters", 0) != GRID_METERS
                || !cache.contains("located_lat") || !cache.contains("located_lon")) {
            return false;
        }
        float[] distance = new float[1];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                Double.longBitsToDouble(cache.getLong("located_lat", 0)),
                Double.longBitsToDouble(cache.getLong("located_lon", 0)), distance);
        return Float.isFinite(distance[0]) && distance[0] < RefreshPolicy.MOVED_METERS;
    }

    public static void rememberStation(SharedPreferences cache, Location location, String station) {
        // Keep only the last precise point, in private local storage, so grid
        // boundaries do not turn a small movement into a new network request.
        Coordinates grid = rounded(location.getLatitude(), location.getLongitude());
        cache.edit().putString("located_station", station).putInt("located_grid_meters", GRID_METERS)
                .putLong("located_lat", Double.doubleToRawLongBits(location.getLatitude()))
                .putLong("located_lon", Double.doubleToRawLongBits(location.getLongitude()))
                .putLong("grid_lat", Double.doubleToRawLongBits(grid.latitude))
                .putLong("grid_lon", Double.doubleToRawLongBits(grid.longitude))
                .remove("located_at").commit();
    }
}
