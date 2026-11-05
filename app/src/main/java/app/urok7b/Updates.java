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
    private static final String CHANGES = SettingsActivity.SITE + "changes.json";
    private static final String SCHEDULE = SettingsActivity.SITE + "schedule.json";
    private static final String KEY_CH_LAST = "changes_last_check";

    interface Done { void done(boolean ok); }

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
        return p.getBoolean(KEY_AUTO, false)
                && System.currentTimeMillis() - p.getLong(KEY_CH_LAST, 0) > 3 * 3600_000L;
    }

    /** Скачать замены с сайта в фоне; если что-то поменялось, обновить уведомление и виджеты. */
    static void checkChanges(Context ctx, Runnable done) {
        Context app = ctx.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            boolean changed = fetchSchedule(app) | fetchChanges(app);
            main.post(() -> {
                if (changed) Updater.update(app);
                if (done != null) done.run();
            });
        }).start();
    }

    /** Всё расписание с сайта (data/schedule.json). @return true, если оно изменилось. Не в главном потоке. */
    private static boolean fetchSchedule(Context app) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(SCHEDULE).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setUseCaches(false);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 2_000_000) out.write(buf, 0, n);
                String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
                JSONObject o = new JSONObject(text);
                if (o.optJSONObject("classes") == null || o.optJSONArray("bells") == null) return false; // не расписание
                SharedPreferences p = Updater.prefs(app);
                boolean changed = !text.equals(p.getString(Schedule.KEY_SCHEDULE, null));
                if (changed) {
                    p.edit().putString(Schedule.KEY_SCHEDULE, text).apply();
                    Schedule.resetBase();
                }
                return changed;
            }
        } catch (Exception ignored) {
            return false; // нет сети: работаем по сохранённому расписанию
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** @return true, если файл замен изменился. Вызывать не в главном потоке. */
    private static boolean fetchChanges(Context app) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(CHANGES).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            c.setUseCaches(false);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 512_000) out.write(buf, 0, n);
                String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
                if (new JSONObject(text).optJSONObject("days") == null) return false; // не файл замен
                SharedPreferences p = Updater.prefs(app);
                boolean changed = !text.equals(p.getString(Schedule.KEY_CHANGES, null));
                p.edit().putString(Schedule.KEY_CHANGES, text).putLong(KEY_CH_LAST, System.currentTimeMillis()).apply();
                if (changed) Schedule.invalidate();
                return changed;
            }
        } catch (Exception ignored) {
            return false; // нет сети: работаем по сохранённым заменам
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static boolean due(Context ctx) {
        SharedPreferences p = Updater.prefs(ctx);
        return p.getBoolean(KEY_AUTO, false)
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
        new Thread(() -> {
            boolean ok = false;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0 && out.size() < 512_000) out.write(buf, 0, n);
                    // тег вида v3.1-build-12
                    String tag = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8)).getString("tag_name");
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("^v([\\d.]+)-build-(\\d+)$").matcher(tag);
                    if (m.find()) {
                        Updater.prefs(app).edit().putString(KEY_NAME, m.group(1))
                                .putLong(KEY_BUILD, Long.parseLong(m.group(2)))
                                .putLong(KEY_LAST, System.currentTimeMillis()).apply();
                        ok = true;
                    }
                }
            } catch (Exception ignored) {
                // нет сети: не страшно, проверим в другой раз
            } finally {
                if (c != null) c.disconnect();
            }
            // заодно свежее расписание и замены (тот же разрешённый выход в интернет)
            boolean changed = fetchSchedule(app) | fetchChanges(app);
            final boolean r = ok;
            main.post(() -> {
                if (changed) Updater.update(app);
                cb.done(r);
            });
        }).start();
    }
}
