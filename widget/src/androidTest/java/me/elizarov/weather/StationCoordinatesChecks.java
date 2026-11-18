package me.elizarov.weather;

import android.content.Context;
import java.io.IOException;

import static me.elizarov.weather.WeatherCardChecks.require;

final class StationCoordinatesChecks {
    static void run(Context context, long now) throws Exception {
        String saintPetersburg = "0-20000-0-26063";
        String result = "{\"stationSearchResults\":[{\"latitude\":59.9666666667,\"longitude\":30.3,"
                + "\"wigosStationIdentifiers\":[{\"wigosStationIdentifier\":\"0-20000-0-26063\",\"primary\":true}]}]}";
        LocationPolicy.Coordinates original = StationCoordinates.parse(result, saintPetersburg);
        require(original.latitude == 59.9666666667 && original.longitude == 30.3, "WMO point from JSON");
        try {
            StationCoordinates.parse(result, "0-20000-0-26067");
            throw new AssertionError("Mismatched WIGOS ID accepted");
        } catch (IOException expected) {
        }
        String name = "test-station-coordinates.db";
        context.deleteDatabase(name);
        long interval = StationCoordinates.RECHECK_INTERVAL;
        LocationPolicy.Coordinates second = new LocationPolicy.Coordinates(59.4333, 29.5);
        LocationPolicy.Coordinates replacement = new LocationPolicy.Coordinates(59.96, 30.29);
        int[] calls = {0};
        StationCoordinates.StationLookup offline = id -> {
            calls[0]++;
            throw new IOException("Offline");
        };
        try {
            try (StationCoordinates db = new StationCoordinates(context, name, id -> {
                calls[0]++;
                if (saintPetersburg.equals(id)) return original;
                require(id.equals("0-20000-0-26067"), "Only station IDs are sent to WMO");
                return second;
            })) {
                require(db.resolve(WeatherConfig.DEFAULT_STATION, now).latitude == 55.833 && calls[0] == 0,
                        "Default Moscow point is available without WMO");
                require(db.resolve("26063", now).latitude == original.latitude, "First station is cached");
                require(db.resolve("26067", now).latitude == second.latitude, "Second station is cached separately");
                require(calls[0] == 2, "Only stations absent from the cache were looked up");
            }
            try (StationCoordinates db = new StationCoordinates(context, name, offline)) {
                require(db.resolve("26063", now + interval - 1).latitude == original.latitude && calls[0] == 2,
                        "Fresh coordinates survive reopening the database");
                require(db.resolve("26063", now + interval).latitude == original.latitude && calls[0] == 3,
                        "After 30 days, one failed recheck preserves the old coordinates");
                require(db.resolve("26063", now + interval + 1).latitude == original.latitude && calls[0] == 3,
                        "A failed recheck is also remembered");
                try {
                    db.resolve("31960", now + interval);
                    throw new AssertionError("Unknown station returned coordinates while offline");
                } catch (IOException expected) {
                }
            }
            try (StationCoordinates db = new StationCoordinates(context, name, id -> replacement)) {
                require(db.resolve("26063", now + 2 * interval).latitude == replacement.latitude,
                        "The next successful recheck replaces the coordinates");
                require(db.resolve("26067", now + 1).latitude == second.latitude,
                        "Refreshing one station keeps the other station's coordinates");
            }
        } finally {
            context.deleteDatabase(name);
        }
    }
}
