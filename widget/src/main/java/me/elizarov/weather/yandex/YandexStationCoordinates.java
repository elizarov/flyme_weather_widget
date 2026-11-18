package me.elizarov.weather.yandex;

import me.elizarov.weather.LocationPolicy;
import me.elizarov.weather.WeatherConfig;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Resolves WMO station IDs and remembers the coordinates independently of forecast data. */
final class YandexStationCoordinates extends SQLiteOpenHelper {
    private static final String ENDPOINT = "https://oscar.wmo.int/surface/rest/api/search/station?wigosId=";
    static final long RECHECK_INTERVAL = TimeUnit.DAYS.toMillis(30);
    private static final LocationPolicy.Coordinates DEFAULT_COORDINATES = new LocationPolicy.Coordinates(55.833, 37.617);
    private static YandexStationCoordinates instance;

    private final StationLookup lookup;
    private final Object refreshLock = new Object();

    interface StationLookup {
        LocationPolicy.Coordinates fetch(String wigosId) throws IOException, InterruptedException;
    }

    private static final class Entry {
        final LocationPolicy.Coordinates coordinates;
        final long checkedAt;

        Entry(LocationPolicy.Coordinates coordinates, long checkedAt) {
            this.coordinates = coordinates;
            this.checkedAt = checkedAt;
        }
    }

    YandexStationCoordinates(Context context, String database, StationLookup lookup) {
        super(context, database, null, 1);
        this.lookup = lookup;
    }

    private static synchronized YandexStationCoordinates database(Context context) {
        if (instance == null) {
            instance = new YandexStationCoordinates(context.getApplicationContext(), "station-coordinates.db",
                    YandexStationCoordinates::download);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE station (station_id TEXT PRIMARY KEY NOT NULL, latitude REAL NOT NULL, "
                + "longitude REAL NOT NULL, checked_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Unsupported station database upgrade");
    }

    static LocationPolicy.Coordinates cached(Context context, String station) {
        if (station.isEmpty()) return null;
        try {
            Entry entry = database(context).read(station);
            if (entry != null) return rounded(entry.coordinates);
        } catch (SQLiteException error) {
            android.util.Log.w("WeatherWidget", "Station cache unavailable");
        }
        return WeatherConfig.DEFAULT_STATION.equals(station) ? rounded(DEFAULT_COORDINATES) : null;
    }

    static LocationPolicy.Coordinates get(Context context, String station) throws IOException, InterruptedException {
        return rounded(database(context).resolve(station, System.currentTimeMillis()));
    }

    private static LocationPolicy.Coordinates rounded(LocationPolicy.Coordinates point) {
        return LocationPolicy.rounded(point.latitude, point.longitude);
    }

    LocationPolicy.Coordinates resolve(String station, long now) throws IOException, InterruptedException {
        if (!station.matches("[0-9]{5}")) throw new IOException("Invalid WMO station ID");
        synchronized (refreshLock) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Entry saved = read(station);
            if (saved != null && now >= saved.checkedAt && now - saved.checkedAt < RECHECK_INTERVAL)
                return saved.coordinates;
            if (saved == null && WeatherConfig.DEFAULT_STATION.equals(station)) {
                save(station, DEFAULT_COORDINATES, now);
                return DEFAULT_COORDINATES;
            }
            LocationPolicy.Coordinates point;
            try {
                point = lookup.fetch("0-20000-0-" + station);
            } catch (IOException error) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (saved == null) throw error;
                // Retain the last known point and wait until the next recheck interval.
                ContentValues checked = new ContentValues();
                checked.put("checked_at", now);
                getWritableDatabase().update("station", checked, "station_id = ?", new String[]{station});
                return saved.coordinates;
            }
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            save(station, point, now);
            return point;
        }
    }

    private Entry read(String station) {
        try (Cursor cursor = getReadableDatabase().query("station",
                new String[]{"latitude", "longitude", "checked_at"}, "station_id = ?", new String[]{station},
                null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            return new Entry(new LocationPolicy.Coordinates(cursor.getDouble(0), cursor.getDouble(1)), cursor.getLong(2));
        }
    }

    private void save(String station, LocationPolicy.Coordinates point, long checkedAt) {
        ContentValues row = new ContentValues();
        row.put("station_id", station);
        row.put("latitude", point.latitude);
        row.put("longitude", point.longitude);
        row.put("checked_at", checkedAt);
        getWritableDatabase().replaceOrThrow("station", null, row);
    }

    static LocationPolicy.Coordinates parse(String json, String wigosId) throws IOException, JSONException {
        JSONArray stations = new JSONObject(json).getJSONArray("stationSearchResults");
        LocationPolicy.Coordinates point = null;
        for (int i = 0; i < stations.length(); i++) {
            JSONObject station = stations.getJSONObject(i);
            JSONArray identifiers = station.getJSONArray("wigosStationIdentifiers");
            for (int j = 0; j < identifiers.length(); j++) {
                if (!wigosId.equals(identifiers.getJSONObject(j).optString("wigosStationIdentifier"))) continue;
                double lat = station.optDouble("latitude");
                double lon = station.optDouble("longitude");
                if (point != null || !Double.isFinite(lat) || !Double.isFinite(lon)
                        || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
                    throw new IOException("Invalid coordinates for WMO station");
                }
                point = new LocationPolicy.Coordinates(lat, lon);
                break;
            }
        }
        if (point == null) throw new IOException("WMO station not found");
        return point;
    }

    private static LocationPolicy.Coordinates download(String wigosId) throws IOException, InterruptedException {
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT + wigosId).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("WMO station lookup unavailable");
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream response = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                for (int count; (count = in.read(buffer)) != -1; ) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (response.size() + count > 32768) throw new IOException("WMO station response too large");
                    response.write(buffer, 0, count);
                }
                return parse(response.toString(StandardCharsets.UTF_8.name()), wigosId);
            }
        } catch (JSONException error) {
            throw new IOException("Invalid WMO station response", error);
        } finally {
            connection.disconnect();
        }
    }
}
