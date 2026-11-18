package me.elizarov.weather.hmc.catalog;

import me.elizarov.weather.WeatherConfig;
import me.elizarov.weather.hmc.HmcClient;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Official site selectors fetched on demand, cached for 24h; no bundled city list.
 */
public final class HmcLocationCatalog {
    public static final String CHANGED = "me.elizarov.weather.CATALOG_CHANGED";
    private static final long TTL = 86400000L;
    private static volatile List<HmcPlace> cached = Collections.emptyList();
    private static volatile boolean loaded, loading;
    private static volatile String status = "";
    private static long fetchedAt, attemptedAt;
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();

    public static final class HmcPlace {
        public final String path, name, region, country, search;

        HmcPlace(JSONArray row) throws Exception {
            path = row.getString(0);
            name = row.getString(1);
            region = row.getString(2);
            country = row.getString(3);
            search = normalize(name + " " + region + " " + country);
        }

        JSONArray json() {
            return new JSONArray().put(path).put(name).put(region).put(country);
        }

        public String detail() {
            return region.isEmpty() ? country : region + " · " + country;
        }
    }

    private HmcLocationCatalog() {
    }

    private static SharedPreferences configuredStations(Context context) {
        return context.getSharedPreferences("weather_stations", Context.MODE_PRIVATE);
    }

    public static String cachedStation(Context context, WeatherConfig config) {
        if (config.cityPath.isEmpty()) return config.fallbackStation;
        SharedPreferences stations = configuredStations(context);
        return config.cityPath.equals(stations.getString("resolved_path", ""))
                ? stations.getString("resolved_station", "") : "";
    }

    public static String fallbackStation(Context context, WeatherConfig config) throws Exception {
        if (config.cityPath.isEmpty()) return config.fallbackStation;
        SharedPreferences stations = configuredStations(context);
        String cached = cachedStation(context, config);
        if (!cached.isEmpty() && System.currentTimeMillis() - stations.getLong("resolved_at", 0) < 86400000)
            return cached;
        String station = stationForPath(config.cityPath);
        stations.edit().putString("resolved_path", config.cityPath).putString("resolved_station", station)
                .putLong("resolved_at", System.currentTimeMillis()).commit();
        return station;
    }

    private static String stationForPath(String path) throws Exception {
        if (!path.matches("[a-zA-Z0-9_./%~-]+") || path.contains("..") || path.startsWith("/"))
            throw new IllegalArgumentException("Invalid HMC city path");
        String page = HmcClient.cityPage(path);
        Matcher rss = Pattern.compile("rss/forecasts/index\\.php\\?s=([0-9]{5})(?![0-9])").matcher(page);
        if (!rss.find()) throw new IllegalArgumentException("City has no supported RSS feed");
        return rss.group(1);
    }

    public static synchronized List<HmcPlace> snapshot(Context c) {
        if (!loaded) {
            loaded = true;
            try {
                JSONObject saved = new JSONObject(read(new File(c.getFilesDir(), "locations.json")));
                cached = parse(saved.getJSONArray("locations"));
                fetchedAt = saved.optLong("fetched_at");
            } catch (Exception ignored) { /* First run: the UI stays usable while downloading. */ }
        }
        return cached;
    }

    static List<HmcPlace> parse(JSONArray rows) throws Exception {
        List<HmcPlace> list = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) list.add(new HmcPlace(rows.getJSONArray(i)));
        return Collections.unmodifiableList(list);
    }

    public static String status() {
        return status;
    }

    public static boolean loading() {
        return loading;
    }

    public static synchronized void ensure(Context c, Function<Context, String> recentCityPath) {
        snapshot(c);
        long now = System.currentTimeMillis();
        if (loading || now - fetchedAt < TTL || now - attemptedAt < 60000) return;
        loading = true;
        attemptedAt = now;
        status = "Загрузка каталога Гидрометцентра…";
        Context app = c.getApplicationContext();
        worker.execute(() -> refresh(app, recentCityPath));
    }

    private static void refresh(Context c, Function<Context, String> recentCityPath) {
        Map<String, HmcPlace> places = new LinkedHashMap<>();
        Map<String, HmcPlace> fresh = new LinkedHashMap<>();
        for (HmcPlace p : cached)
            places.put(p.path, p); // Stale data stays searchable during refresh.
        Set<String> seen = new HashSet<>();
        Map<String, String> countries = new LinkedHashMap<>();
        ExecutorService network = Executors.newFixedThreadPool(3);
        int errors = 0, pages = 0;
        try {
            JSONObject root = page(c, "");
            JSONArray countryRows = root.getJSONArray("countries");
            List<String> queue = new ArrayList<>();
            for (int i = 0; i < countryRows.length(); i++) {
                JSONArray row = countryRows.getJSONArray(i);
                countries.put(row.getString(0).split("/")[0], row.getString(1));
                queue.add(row.getString(0));
            }
            // Load the selected area's selector first; then Russia before the remaining countries.
            String selected = WeatherConfig.read(c).cityPath;
            if (selected.isEmpty()) selected = recentCityPath.apply(c);
            if (!selected.isEmpty()) queue.add(0, selected);
            queue.sort(Comparator.comparingInt(p -> p.startsWith("russia/") ? 0 : 1));
            // Fetch batches so new region selectors get priority over the remaining countries.
            while (!queue.isEmpty()) {
                List<String> batch = new ArrayList<>();
                while (!queue.isEmpty() && batch.size() < 3) {
                    String path = queue.remove(0);
                    if (seen.add(path)) batch.add(path);
                }
                CompletionService<JSONObject> completion = new ExecutorCompletionService<>(network);
                for (String path : batch) completion.submit(() -> page(c, path));
                List<String> regions = new ArrayList<>();
                for (int i = 0; i < batch.size(); i++) {
                    try {
                        JSONObject data = completion.take().get();
                        pages++;
                        JSONArray cityRows = data.getJSONArray("cities"), regionRows = data.getJSONArray("regions");
                        Map<String, String> regionNames = new LinkedHashMap<>();
                        for (int j = 0; j < regionRows.length(); j++) {
                            JSONArray r = regionRows.getJSONArray(j);
                            String path = r.getString(0);
                            regionNames.put(path.substring(0, path.lastIndexOf('/')), r.getString(1));
                            if (!seen.contains(path) && !regions.contains(path)) regions.add(path);
                        }
                        for (int j = 0; j < cityRows.length(); j++) {
                            JSONArray row = cityRows.getJSONArray(j);
                            String path = row.getString(0);
                            if (!path.contains("/")) continue;
                            HmcPlace p = new HmcPlace(new JSONArray().put(path).put(row.getString(1))
                                    .put(regionNames.getOrDefault(path.substring(0, path.lastIndexOf('/')), ""))
                                    .put(countries.getOrDefault(path.split("/")[0], "")));
                            places.put(path, p);
                            fresh.put(path, p);
                        }
                        cached = Collections.unmodifiableList(new ArrayList<>(places.values()));
                        status = "Загрузка каталога · " + cached.size() + " локаций";
                        notifyChanged(c);
                    } catch (Exception error) {
                        errors++;
                    }
                }
                // Every regional page repeats its siblings: don't duplicate pending requests.
                for (int i = regions.size() - 1; i >= 0; i--) {
                    String path = regions.get(i);
                    if (!queue.contains(path)) queue.add(0, path);
                }
                if (pages % 12 == 0) save(c, cached, 0);
            }
            if (cached.isEmpty()) throw new IllegalStateException("Empty catalog");
            if (errors == 0) cached = Collections.unmodifiableList(new ArrayList<>(fresh.values()));
            fetchedAt = errors == 0 ? System.currentTimeMillis() : 0;
            save(c, cached, fetchedAt);
            status = errors == 0 ? "Гидрометцентр · " + cached.size() + " локаций" : "Часть каталога недоступна · поиск по сохранённым данным";
        } catch (Exception error) {
            status = cached.isEmpty() ? "Каталог недоступен. Нажмите, чтобы повторить." : "Нет связи · сохранённый каталог";
        } finally {
            network.shutdownNow();
            loading = false;
            notifyChanged(c);
        }
    }

    private static JSONObject page(Context c, String path) throws Exception {
        File dir = new File(c.getFilesDir(), "location-pages");
        dir.mkdirs();
        String hash = Integer.toHexString(path.hashCode()) + "-" + path.length();
        File file = new File(dir, hash + ".json");
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < TTL) {
            JSONObject saved = new JSONObject(read(file));
            if (path.equals(saved.optString("path"))) return saved;
        }
        String body = HmcClient.cityPage(path);
        JSONObject result = new JSONObject().put("path", path)
                .put("countries", options(body, "sel_country")).put("regions", options(body, "sel_reg"))
                .put("cities", options(body, "sel_city"));
        if (result.getJSONArray("cities").length() == 0)
            throw new IllegalArgumentException("Missing city selector");
        write(file, result.toString());
        return result;
    }

    static JSONArray options(String html, String id) {
        Matcher select = Pattern.compile("<select\\b[^>]*\\bid=[\"']" + id + "[\"'][^>]*>(.*?)</select>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(html);
        JSONArray rows = new JSONArray();
        if (!select.find()) return rows;
        Matcher options = Pattern.compile("<option\\b[^>]*\\bvalue=[\"']([^\"']+)[\"'][^>]*>(.*?)</option>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(select.group(1));
        while (options.find())
            rows.put(new JSONArray().put(Html.fromHtml(options.group(1), 0).toString())
                    .put(Html.fromHtml(options.group(2), 0).toString().trim()));
        return rows;
    }

    private static void save(Context c, List<HmcPlace> places, long at) throws Exception {
        JSONArray rows = new JSONArray();
        for (HmcPlace p : places) rows.put(p.json());
        write(new File(c.getFilesDir(), "locations.json"), new JSONObject().put("fetched_at", at).put("locations", rows).toString());
    }

    private static String read(File file) throws Exception {
        try (java.io.InputStream in = new AtomicFile(file).openRead()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static void write(File file, String text) throws Exception {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream out = atomic.startWrite();
        try {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            atomic.finishWrite(out);
        } catch (Exception e) {
            atomic.failWrite(out);
            throw e;
        }
    }

    private static void notifyChanged(Context c) {
        c.sendBroadcast(new android.content.Intent(CHANGED).setPackage(c.getPackageName()));
    }

    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').replace('-', ' ').trim().replaceAll("\\s+", " ");
    }

    public static List<HmcPlace> search(List<HmcPlace> all, String query) {
        String q = normalize(query);
        List<HmcPlace> matches = new ArrayList<>();
        if (q.isEmpty()) return matches;
        String[] words = q.split(" ");
        for (HmcPlace p : all) {
            boolean found = true;
            for (String word : words)
                if (!p.search.contains(word)) {
                    found = false;
                    break;
                }
            if (found) matches.add(p);
        }
        matches.sort(Comparator.comparingInt((HmcPlace p) -> normalize(p.name).startsWith(q) ? 0 : 1).thenComparing(p -> p.name).thenComparing(p -> p.region));
        return new ArrayList<>(matches.subList(0, Math.min(40, matches.size())));
    }
}
