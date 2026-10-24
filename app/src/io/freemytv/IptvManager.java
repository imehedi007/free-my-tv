package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance, low-memory IPTV manager.
 * Fetches, caches, and parses iptv-org playlists and channel logos.
 */
public final class IptvManager {
    private static final String BASE_URL = "https://iptv-org.github.io/iptv/countries/";
    private static final String COUNTRIES_API = "https://iptv-org.github.io/api/countries.json";
    private static final long CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L; // 24 hours

    /** South Asian countries pinned at the top */
    public static final List<IptvCountry> SOUTH_ASIA = Arrays.asList(
        new IptvCountry("BD", "Bangladesh", "🇧🇩"),
        new IptvCountry("IN", "India", "🇮🇳"),
        new IptvCountry("PK", "Pakistan", "🇵🇰"),
        new IptvCountry("NP", "Nepal", "🇳🇵"),
        new IptvCountry("LK", "Sri Lanka", "🇱🇰"),
        new IptvCountry("BT", "Bhutan", "🇧🇹"),
        new IptvCountry("MV", "Maldives", "🇲🇻"),
        new IptvCountry("AF", "Afghanistan", "🇦🇫")
    );

    /** Popular global countries */
    public static final List<IptvCountry> POPULAR_WORLD = Arrays.asList(
        new IptvCountry("US", "United States", "🇺🇸"),
        new IptvCountry("UK", "United Kingdom", "🇬🇧"),
        new IptvCountry("CA", "Canada", "🇨🇦"),
        new IptvCountry("SA", "Saudi Arabia", "🇸🇦"),
        new IptvCountry("AE", "United Arab Emirates", "🇦🇪"),
        new IptvCountry("QA", "Qatar", "🇶🇦"),
        new IptvCountry("TR", "Turkey", "🇹🇷"),
        new IptvCountry("MY", "Malaysia", "🇲🇾"),
        new IptvCountry("SG", "Singapore", "🇸🇬"),
        new IptvCountry("AU", "Australia", "🇦🇺"),
        new IptvCountry("DE", "Germany", "🇩🇪"),
        new IptvCountry("FR", "France", "🇫🇷"),
        new IptvCountry("ES", "Spain", "🇪🇸"),
        new IptvCountry("IT", "Italy", "🇮🇹"),
        new IptvCountry("JP", "Japan", "🇯🇵"),
        new IptvCountry("KR", "South Korea", "🇰🇷"),
        new IptvCountry("CN", "China", "🇨🇳"),
        new IptvCountry("BR", "Brazil", "🇧🇷")
    );

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    // Strict 10MB memory limit for channel logo bitmap cache
    private static final LruCache<String, Bitmap> LOGO_CACHE = new LruCache<String, Bitmap>(10 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    private static List<IptvChannel> activePlaylist = new ArrayList<>();

    public static synchronized void setActivePlaylist(List<IptvChannel> list) {
        activePlaylist = list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    public static synchronized List<IptvChannel> getActivePlaylist() {
        return activePlaylist;
    }

    public interface Callback<T> {
        void onResult(T result);
        void onError(Exception e);
    }

    public interface ImageCallback {
        void onLoaded(Bitmap bitmap);
    }

    private IptvManager() {}

    /** Returns the list of all countries (South Asia first, then global) */
    public static void getCountries(Context context, Callback<List<IptvCountry>> callback) {
        EXECUTOR.execute(() -> {
            List<IptvCountry> list = new ArrayList<>();
            list.addAll(SOUTH_ASIA);

            List<IptvCountry> remote = loadCachedCountries(context);
            if (remote != null && !remote.isEmpty()) {
                Set<String> added = new HashSet<>();
                for (IptvCountry c : SOUTH_ASIA) added.add(c.code);
                for (IptvCountry c : remote) {
                    if (added.add(c.code)) list.add(c);
                }
            } else {
                for (IptvCountry c : POPULAR_WORLD) {
                    if (!list.contains(c)) list.add(c);
                }
                // Try fetching remote index in background for next time
                fetchRemoteCountries(context);
            }

            MAIN_HANDLER.post(() -> callback.onResult(list));
        });
    }

    /** Loads and parses channels for a given country code (e.g. "BD") */
    public static void getChannels(Context context, String countryCode, Callback<List<IptvChannel>> callback) {
        final String code = (countryCode != null ? countryCode.toLowerCase() : "bd");
        EXECUTOR.execute(() -> {
            try {
                File cacheDir = new File(context.getCacheDir(), "iptv");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File cacheFile = new File(cacheDir, code + ".m3u");

                String content = null;
                boolean isStale = !cacheFile.exists() || (System.currentTimeMillis() - cacheFile.lastModified() > CACHE_MAX_AGE_MS);

                if (!isStale) {
                    content = readFile(cacheFile);
                }

                if (content == null || content.isEmpty()) {
                    String urlStr = BASE_URL + code + ".m3u";
                    content = fetchHttp(urlStr);
                    if (content != null && !content.isEmpty()) {
                        writeFile(cacheFile, content);
                    } else if (cacheFile.exists()) {
                        content = readFile(cacheFile); // Fallback to stale cache if network failed
                    }
                }

                if (content == null || content.isEmpty()) {
                    MAIN_HANDLER.post(() -> callback.onError(new Exception("No channels found for " + code)));
                    return;
                }

                List<IptvChannel> channels = parseM3u(content, code.toUpperCase());
                markStarred(context, channels);

                MAIN_HANDLER.post(() -> callback.onResult(channels));
            } catch (Exception e) {
                AppLog.w("getChannels error for " + code + ": " + e.getMessage());
                MAIN_HANDLER.post(() -> callback.onError(e));
            }
        });
    }

    /** Parses M3U playlist format into lightweight IptvChannel objects */
    private static List<IptvChannel> parseM3u(String content, String countryCode) {
        List<IptvChannel> list = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));

        Pattern idPattern = Pattern.compile("tvg-id=\"([^\"]*)\"");
        Pattern logoPattern = Pattern.compile("tvg-logo=\"([^\"]*)\"");
        Pattern groupPattern = Pattern.compile("group-title=\"([^\"]*)\"");
        Pattern resPattern = Pattern.compile("\\((1080p|720p|576p|480p|360p|240p|4K|HD|SD)\\)", Pattern.CASE_INSENSITIVE);

        String line;
        String curId = "", curLogo = "", curGroup = "General", curName = "", curRes = "";

        try {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("#EXTINF:")) {
                    curId = ""; curLogo = ""; curGroup = "General"; curName = ""; curRes = "";

                    Matcher idM = idPattern.matcher(line);
                    if (idM.find()) curId = idM.group(1);

                    Matcher logoM = logoPattern.matcher(line);
                    if (logoM.find()) curLogo = logoM.group(1);

                    Matcher groupM = groupPattern.matcher(line);
                    if (groupM.find()) {
                        curGroup = groupM.group(1);
                        // Clean up multiple groups
                        if (curGroup.contains(";")) curGroup = curGroup.split(";")[0].trim();
                    }

                    int comma = line.lastIndexOf(',');
                    if (comma >= 0 && comma < line.length() - 1) {
                        curName = line.substring(comma + 1).trim();
                    }

                    Matcher resM = resPattern.matcher(curName);
                    if (resM.find()) {
                        curRes = resM.group(1).toUpperCase();
                        curName = curName.replace(resM.group(0), "").trim();
                    }
                } else if (!line.startsWith("#") && (line.startsWith("http://") || line.startsWith("https://"))) {
                    if (curName.isEmpty()) curName = "Channel " + (list.size() + 1);
                    if (curRes.isEmpty()) curRes = "HD";

                    list.add(new IptvChannel(curId, curName, curLogo, curGroup, line, countryCode, curRes));
                }
            }
        } catch (Exception e) {
            AppLog.w("parseM3u error: " + e.getMessage());
        }

        return list;
    }

    /** Loads a thumbnail logo asynchronously with strictly capped downsampling */
    public static void loadLogo(String url, ImageCallback callback) {
        if (url == null || url.isEmpty()) {
            callback.onLoaded(null);
            return;
        }

        Bitmap cached = LOGO_CACHE.get(url);
        if (cached != null) {
            callback.onLoaded(cached);
            return;
        }

        EXECUTOR.execute(() -> {
            Bitmap bmp = null;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(5000);
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                if (conn.getResponseCode() == 200) {
                    byte[] data = readAllBytes(conn.getInputStream());
                    if (data != null && data.length > 0) {
                        BitmapFactory.Options opt = new BitmapFactory.Options();
                        opt.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(data, 0, data.length, opt);

                        int req = 128; // Standard 128x128 TV logo thumbnail
                        int sample = 1;
                        while ((opt.outWidth / (sample * 2)) >= req && (opt.outHeight / (sample * 2)) >= req) {
                            sample *= 2;
                        }
                        opt.inSampleSize = Math.max(1, sample);
                        opt.inJustDecodeBounds = false;
                        opt.inPreferredConfig = Bitmap.Config.RGB_565; // Uses half the memory of ARGB_8888

                        bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opt);
                        if (bmp != null) {
                            LOGO_CACHE.put(url, bmp);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            final Bitmap finalBmp = bmp;
            MAIN_HANDLER.post(() -> callback.onLoaded(finalBmp));
        });
    }

    /** Favorites / Starred management */
    public static List<IptvChannel> getStarredChannels(Context context) {
        List<IptvChannel> list = new ArrayList<>();
        SharedPreferences sp = context.getSharedPreferences("iptv_starred", Context.MODE_PRIVATE);
        String json = sp.getString("starred_list", "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                IptvChannel ch = new IptvChannel(
                    o.optString("id"),
                    o.optString("name"),
                    o.optString("logoUrl"),
                    o.optString("group"),
                    o.optString("url"),
                    o.optString("countryCode"),
                    o.optString("resolution")
                );
                ch.isStarred = true;
                list.add(ch);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void toggleStar(Context context, IptvChannel ch) {
        if (ch == null) return;
        List<IptvChannel> list = getStarredChannels(context);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).url.equals(ch.url)) {
                list.remove(i);
                ch.isStarred = false;
                found = true;
                break;
            }
        }
        if (!found) {
            ch.isStarred = true;
            list.add(0, ch);
        }
        saveStarred(context, list);
    }

    private static void saveStarred(Context context, List<IptvChannel> list) {
        try {
            JSONArray arr = new JSONArray();
            for (IptvChannel ch : list) {
                JSONObject o = new JSONObject();
                o.put("id", ch.id);
                o.put("name", ch.name);
                o.put("logoUrl", ch.logoUrl);
                o.put("group", ch.group);
                o.put("url", ch.url);
                o.put("countryCode", ch.countryCode);
                o.put("resolution", ch.resolution);
                arr.put(o);
            }
            context.getSharedPreferences("iptv_starred", Context.MODE_PRIVATE)
                    .edit()
                    .putString("starred_list", arr.toString())
                    .apply();
        } catch (Exception ignored) {}
    }

    private static void markStarred(Context context, List<IptvChannel> channels) {
        Set<String> starredUrls = new HashSet<>();
        for (IptvChannel c : getStarredChannels(context)) starredUrls.add(c.url);
        for (IptvChannel ch : channels) {
            ch.isStarred = starredUrls.contains(ch.url);
        }
    }

    // Helper I/O
    private static String fetchHttp(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                InputStream in = conn.getInputStream();
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            AppLog.w("fetchHttp error for " + urlStr + ": " + e.getMessage());
        }
        return null;
    }

    private static byte[] readAllBytes(InputStream in) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String readFile(File f) {
        try (FileInputStream fis = new FileInputStream(f);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l).append("\n");
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeFile(File f, String data) {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private static List<IptvCountry> loadCachedCountries(Context context) {
        File f = new File(context.getCacheDir(), "iptv_countries.json");
        if (!f.exists()) return null;
        String json = readFile(f);
        if (json == null || json.isEmpty()) return null;
        return parseCountriesJson(json);
    }

    private static void fetchRemoteCountries(Context context) {
        EXECUTOR.execute(() -> {
            String json = fetchHttp(COUNTRIES_API);
            if (json != null && !json.isEmpty()) {
                File f = new File(context.getCacheDir(), "iptv_countries.json");
                writeFile(f, json);
            }
        });
    }

    private static List<IptvCountry> parseCountriesJson(String json) {
        List<IptvCountry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new IptvCountry(
                    o.optString("code"),
                    o.optString("name"),
                    o.optString("flag", "🌐")
                ));
            }
        } catch (Exception ignored) {}
        return list;
    }
}
