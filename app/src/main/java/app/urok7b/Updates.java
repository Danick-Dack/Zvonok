package app.urok7b;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Проверка обновлений: один HTTPS-запрос к GitHub (последний релиз), только когда приложение открыто,
 * не чаще раза в сутки и только если пользователь сам разрешил. Ничего о пользователе не отправляется.
 */
final class Updates {

    static final String KEY_AUTO = "update_auto";
    static final String KEY_ASKED = "update_asked";
    private static final String KEY_LAST = "update_last_check";
    private static final String KEY_BUILD = "update_latest_build";
    private static final String KEY_NAME = "update_latest_name";
    private static final String API = "https://api.github.com/repos/Danick-Dack/Zvonok/releases/latest";
    // Номер новой версии: крошечный файл последнего релиза (с 5.0). GitHub считает его скачивания, по ним сайт
    // показывает, сколько раз за сутки приложения проверили обновление. Ничего о телефоне при этом не уходит.
    private static final String VERSION = "https://github.com/Danick-Dack/Zvonok/releases/latest/download/version.json";
    static final String SITE = "https://danick-dack.github.io/Zvonok/";
    static final String APK_URL = "https://github.com/Danick-Dack/Zvonok/releases/latest/download/Zvonok-165.apk";
    private static final String CHANGES = SITE + "changes.json";
    private static final String SCHEDULE = SITE + "schedule.json";
    private static final String KEY_CH_LAST = "changes_last_check";
    // ETag файлов сайта: сохраняется вместе с самим файлом, по нему сайт отвечает «не изменилось»
    private static final String KEY_SCHEDULE_TAG = "schedule_etag", KEY_CHANGES_TAG = "changes_etag";

    interface Done { void done(boolean ok); }

    /** Идёт фоновая проверка: автоматическая при следующем открытии не запустит вторую параллельно. */
    private static final java.util.concurrent.atomic.AtomicBoolean BUSY = new java.util.concurrent.atomic.AtomicBoolean();

    private Updates() {}

    /** Номер сборки установленного приложения (versionCode = номер сборки в GitHub Actions). */
    static long currentBuild(Context ctx) {
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return android.os.Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static String currentName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /** Есть ли версия новее (по последней проверке). */
    static boolean available(Context ctx) {
        return Updater.prefs(ctx).getLong(KEY_BUILD, 0) > currentBuild(ctx);
    }

    /** "3.1 (сборка 12)" последней известной версии. */
    static String latestText(Context ctx) {
        SharedPreferences p = Updater.prefs(ctx);
        return p.getString(KEY_NAME, "?") + " (сборка " + p.getLong(KEY_BUILD, 0) + ")";
    }

    /** Пора ли спросить замены (раз в 3 часа, только если пользователь разрешил выход в интернет). */
    static boolean changesDue(Context ctx) {
        SharedPreferences p = Updater.prefs(ctx);
        return p.getBoolean(KEY_AUTO, false) && !BUSY.get()
                && System.currentTimeMillis() - p.getLong(KEY_CH_LAST, 0) > 3 * 3600_000L;
    }

    /** Скачать замены с сайта в фоне; если что-то поменялось, обновить уведомление и виджеты. */
    static void checkChanges(Context ctx, Runnable done) {
        Context app = ctx.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        BUSY.set(true);
        new Thread(() -> {
            boolean changed;
            try {
                changed = fetchSchedule(app) | fetchChanges(app);
            } finally {
                BUSY.set(false);
            }
            main.post(() -> {
                if (changed) Updater.update(app);
                if (done != null) done.run();
            });
        }).start();
    }

    /**
     * GET по HTTPS: {текст не больше max байт, ETag}. С etag сайт отвечает «не изменилось» (304) без тела,
     * тогда текст null: ничего не качаем и не разбираем. Исключение, если сети нет. Не в главном потоке.
     */
    static String[] get(String url, int max, String etag) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            if (url.equals(API)) c.setRequestProperty("Accept", "application/vnd.github+json");
            if (etag != null) c.setRequestProperty("If-None-Match", etag);
            String fresh = c.getHeaderField("ETag"); // сервер может прислать новый ETag и в ответе 304
            if (c.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) return new String[]{null, fresh != null ? fresh : etag};
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < max) out.write(buf, 0, Math.min(n, max - out.size()));
                return new String[]{new String(out.toByteArray(), StandardCharsets.UTF_8), fresh};
            }
        } finally {
            c.disconnect();
        }
    }

    /** Всё расписание с сайта (data/schedule.json). @return true, если оно изменилось. Не в главном потоке. */
    private static boolean fetchSchedule(Context app) {
        try {
            SharedPreferences p = Updater.prefs(app);
            String[] r = get(SCHEDULE, 2_000_000, p.getString(KEY_SCHEDULE_TAG, null));
            if (r[0] == null) { // на сайте то же самое
                p.edit().putString(KEY_SCHEDULE_TAG, r[1]).apply();
                return false;
            }
            JSONObject o = new JSONObject(r[0]);
            if (o.optJSONObject("classes") == null || o.optJSONArray("bells") == null) return false; // не расписание
            boolean changed = !r[0].equals(p.getString(Schedule.KEY_SCHEDULE, null));
            p.edit().putString(Schedule.KEY_SCHEDULE, r[0]).putString(KEY_SCHEDULE_TAG, r[1]).apply();
            if (changed) Schedule.resetBase();
            return changed;
        } catch (Exception ignored) {
            return false; // нет сети: работаем по сохранённому расписанию
        }
    }

    /** @return true, если файл замен изменился. Вызывать не в главном потоке. */
    private static boolean fetchChanges(Context app) {
        try {
            SharedPreferences p = Updater.prefs(app);
            String[] r = get(CHANGES, 512_000, p.getString(KEY_CHANGES_TAG, null));
            if (r[0] != null && new JSONObject(r[0]).optJSONObject("days") == null) return false; // не файл замен
            boolean changed = r[0] != null && !r[0].equals(p.getString(Schedule.KEY_CHANGES, null));
            SharedPreferences.Editor ed = p.edit().putLong(KEY_CH_LAST, System.currentTimeMillis());
            ed.putString(KEY_CHANGES_TAG, r[1]);
            if (r[0] != null) ed.putString(Schedule.KEY_CHANGES, r[0]);
            ed.apply();
            if (changed) Schedule.invalidate();
            return changed;
        } catch (Exception ignored) {
            return false; // нет сети: работаем по сохранённым заменам
        }
    }

    static boolean due(Context ctx) {
        SharedPreferences p = Updater.prefs(ctx);
        return p.getBoolean(KEY_AUTO, false) && !BUSY.get()
                && System.currentTimeMillis() - p.getLong(KEY_LAST, 0) > 24 * 3600_000L;
    }

    /** Один раз спросить разрешение на автоматическую проверку (по умолчанию выключена). */
    static void askOnce(android.app.Activity a, Runnable allowed) {
        SharedPreferences p = Updater.prefs(a);
        if (p.getBoolean(KEY_ASKED, false)) return;
        p.edit().putBoolean(KEY_ASKED, true).apply();
        new android.app.AlertDialog.Builder(a, Ui.dialogTheme())
                .setTitle("Проверять обновления?")
                .setMessage("Когда приложение открыто, оно раз в сутки спросит GitHub, вышла ли новая версия, "
                        + "и раз в 3 часа скачает с сайта замены в расписании. О тебе ничего не отправляется. Это можно изменить в настройках.")
                .setPositiveButton("Разрешить", (d, w) -> {
                    p.edit().putBoolean(KEY_AUTO, true).apply();
                    allowed.run();
                })
                .setNegativeButton("Не надо", null)
                .show();
    }

    /** Проверить в фоне; результат — в главном потоке. */
    static void check(Context ctx, Done cb) {
        Context app = ctx.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        BUSY.set(true);
        new Thread(() -> {
            boolean ok = false, changed;
            try {
                try {
                    // тег вида v3.1-build-12; у релизов до 5.0 файла version.json нет, тогда спрашиваем API
                    String tag;
                    try {
                        tag = new JSONObject(get(VERSION, 4096, null)[0]).getString("tag");
                    } catch (Exception e) {
                        tag = new JSONObject(get(API, 512_000, null)[0]).getString("tag_name");
                    }
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^v([\\d.]+)-build-(\\d+)$").matcher(tag);
                    if (m.find()) {
                        Updater.prefs(app).edit().putString(KEY_NAME, m.group(1))
                                .putLong(KEY_BUILD, Long.parseLong(m.group(2)))
                                .putLong(KEY_LAST, System.currentTimeMillis()).apply();
                        ok = true;
                    }
                } catch (Exception ignored) {
                    // нет сети: не страшно, проверим в другой раз
                }
                // заодно свежее расписание и замены (тот же разрешённый выход в интернет)
                changed = fetchSchedule(app) | fetchChanges(app);
            } finally {
                BUSY.set(false); // что бы ни случилось, следующая проверка не застрянет
            }
            final boolean r = ok;
            main.post(() -> {
                if (changed) Updater.update(app);
                cb.done(r);
            });
        }).start();
    }
}
