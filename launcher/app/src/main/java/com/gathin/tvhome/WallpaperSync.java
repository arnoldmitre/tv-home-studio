package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fetches and caches curated online wallpapers from Bing or NASA APOD. */
public final class WallpaperSync {
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final String BING_URL = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US";
    private static final String NASA_URL = "https://images-api.nasa.gov/search?q=space%20nebula&media_type=image";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onComplete();
    }

    public static void checkDaily(Context context, String source, Callback callback) {
        if ("custom".equals(source) || source == null || source.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences("tvhome_wallpaper_sync", Context.MODE_PRIVATE);
        long last = prefs.getLong("last_sync_" + source, 0);
        long now = System.currentTimeMillis();
        if (now - last >= 12 * 3600000L) {
            sync(context, source, callback);
        }
    }

    public static void sync(Context context, String source, Callback callback) {
        if (!BUSY.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();

        new Thread(() -> {
            try {
                JSONArray wallpaperIds = new JSONArray();

                if ("bing".equals(source)) {
                    JSONObject res = fetchJson(BING_URL);
                    JSONArray items = res.getJSONArray("images");
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.getJSONObject(i);
                        String id = "bing_" + it.getString("startdate");
                        wallpaperIds.put(id);
                        File target = StudioConfig.image(app, id);
                        downloadImage("https://www.bing.com" + it.getString("urlbase") + "_1920x1080.jpg", target);
                    }
                } else if ("nasa".equals(source)) {
                    JSONObject res = fetchJson(NASA_URL);
                    JSONArray items = res.getJSONObject("collection").getJSONArray("items");
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject it = items.getJSONObject(i);
                        JSONArray dataArr = it.optJSONArray("data");
                        JSONArray links = it.optJSONArray("links");
                        if (dataArr == null || links == null || links.length() == 0) continue;
                        String nasaId = dataArr.getJSONObject(0).getString("nasa_id").replaceAll("[^a-zA-Z0-9_-]", "");
                        String thumb = links.getJSONObject(0).getString("href");
                        String origUrl = thumb.replace("~thumb.jpg", "~orig.jpg");
                        wallpaperIds.put(nasaId);
                        File target = StudioConfig.image(app, nasaId);
                        downloadImage(origUrl, target);
                        if (wallpaperIds.length() >= 8) break;
                    }
                }

                if (wallpaperIds.length() > 0) {
                    JSONObject config = StudioConfig.read(app);
                    JSONObject active = StudioConfig.active(config);
                    active.put("wallpaperSource", source);
                    active.put("wallpapers", wallpaperIds);
                    if (active.optInt("rotateMinutes", 0) == 0) {
                        active.put("rotateMinutes", 30);
                    }
                    StudioConfig.save(app, config);
                    app.getSharedPreferences("tvhome_wallpaper_sync", Context.MODE_PRIVATE)
                            .edit().putLong("last_sync_" + source, System.currentTimeMillis()).apply();
                }

                if (callback != null) {
                    MAIN.post(callback::onComplete);
                }
            } catch (Exception ignored) {
            } finally {
                BUSY.set(false);
            }
        }, "wallpaper-sync").start();
    }

    private static JSONObject fetchJson(String urlStr) throws Exception {
        return new JSONObject(fetchString(urlStr));
    }

    private static JSONArray fetchJsonArray(String urlStr) throws Exception {
        return new JSONArray(fetchString(urlStr));
    }

    private static String fetchString(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "TVHomeStudio/1.8");
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private static void downloadImage(String urlStr, File dest) throws IOException {
        if (dest.exists() && dest.length() > 0) return;
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "TVHomeStudio/1.8");
        File temp = new File(dest.getAbsolutePath() + ".upload");
        try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                if (temp.length() + n > 3 * 1024 * 1024) throw new IOException("Image too large");
                out.write(buf, 0, n);
            }
        } finally {
            c.disconnect();
        }
        if (!temp.renameTo(dest)) {
            throw new IOException("Failed to save wallpaper");
        }
    }
}
