package app.urok7b;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Новости лицея и олимпиады: файлы news.json и olympiads.json со своего сайта (в Telegram приложение не ходит,
 * посты раз в час собирает сборка сайта). Скачанное хранится на телефоне и открывается без интернета.
 * В сеть только с разрешения (как обновления) или по явному нажатию «Загрузить».
 */
final class Feed {

    static final String NEWS = "news.json", OLYMP = "olympiads.json";
    static final String KEY_NOTIFY = "news_notify";   // уведомлять о новых постах
    static final String KEY_SEEN = "news_seen";       // последний пост, который видели во вкладке
    private static final String KEY_TOLD = "news_told", KEY_UPDATE_TOLD = "update_told";
    private static final String CHANNEL = "school_news";
    private static final int NEWS_ID = 21, UPDATE_ID = 22;
    private static final Pattern PHOTO = Pattern.compile("^news/\\d{1,9}\\.jpg$");
    /** Картинки грузятся по одной: не десять соединений сразу. */
    static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private Feed() {}

    /** Записи файла: скачанная копия, а для олимпиад ещё и встроенная в приложение. Пусто, если ничего нет. */
    static JSONArray items(Context c, String name) {
        try {
            File f = new File(c.getFilesDir(), name);
            String text = f.exists() ? new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                    : OLYMP.equals(name) ? Ui.asset(c, name) : "{}";
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            JSONArray a = new JSONObject(text).optJSONArray("items");
            return a == null ? new JSONArray() : a;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Скачать файл с сайта. @return true, если он изменился. Не в главном потоке. */
    static boolean fetch(Context app, String name) {
        try {
            SharedPreferences p = Updater.prefs(app);
            String[] r = Updates.get(Updates.SITE + name, 600_000, p.getString("etag_" + name, null));
            if (r[0] == null) return false; // на сайте то же самое
            if (new JSONObject(r[0]).optJSONArray("items") == null) return false; // не наш файл
            try (FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), name))) {
                out.write(r[0].getBytes(StandardCharsets.UTF_8));
            }
            p.edit().putString("etag_" + name, r[1]).apply();
            return true;
        } catch (Exception e) {
            return false; // нет сети: остаётся сохранённое
        }
    }

    /** Номер самого свежего поста (0: новостей нет). */
    static int top(Context c) {
        JSONObject p = items(c, NEWS).optJSONObject(0);
        return p == null ? 0 : p.optInt("id");
    }

    /**
     * Картинка поста: только из папки news/ своего сайта, не больше 600 КБ, с диска после первого раза.
     * Декодируется уменьшенной (не шире ~720 px): в память не попадает лишнего. null, если не получилось.
     */
    static Bitmap photo(Context c, String path) {
        if (path == null || !PHOTO.matcher(path).matches()) return null;
        File f = new File(c.getCacheDir(), path.replace('/', '_'));
        try {
            if (!f.exists()) {
                HttpURLConnection con = (HttpURLConnection) new URL(Updates.SITE + path).openConnection();
                try {
                    con.setConnectTimeout(6000);
                    con.setReadTimeout(10000);
                    if (con.getResponseCode() != 200 || con.getContentLength() > 600_000) return null;
                    File tmp = new File(f.getPath() + ".part");
                    try (InputStream in = con.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[8192];
                        int n, total = 0;
                        while ((n = in.read(buf)) > 0 && (total += n) <= 600_000) out.write(buf, 0, n);
                        if (total > 600_000) return null;
                    }
                    if (!tmp.renameTo(f)) return null;
                } finally {
                    con.disconnect();
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            o.inSampleSize = Math.max(1, o.outWidth / 720);
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- уведомления: новый пост и новая версия ----------

    private static NotificationManager channel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Новости и обновления", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Новый пост в канале лицея и новая версия «Звонка»");
            nm.createNotificationChannel(ch);
        }
        return nm;
    }

    private static void post(Context c, int id, String title, String text, Intent open) {
        NotificationManager nm = channel(c);
        if (nm == null) return;
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            nm.notify(id, new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(pi).setAutoCancel(true).build());
        } catch (RuntimeException e) {
            // уведомления запрещены: новость всё равно ждёт во вкладке
        }
    }

    /** После фоновой загрузки: если появился пост новее того, о котором уже говорили, одно уведомление. */
    static void tellNews(Context c) {
        SharedPreferences p = Updater.prefs(c);
        JSONObject top = items(c, NEWS).optJSONObject(0);
        int id = top == null ? 0 : top.optInt("id"), told = Math.max(p.getInt(KEY_TOLD, 0), p.getInt(KEY_SEEN, 0));
        if (id <= told) return;
        p.edit().putInt(KEY_TOLD, id).apply();
        if (told == 0 || !p.getBoolean(KEY_NOTIFY, true)) return; // первый раз только запоминаем, с чего начать
        String text = Schedule.clean(top.optString("text").split("\n")[0]);
        post(c, NEWS_ID, "Новости лицея", text.isEmpty() ? "Новый пост в канале" : text,
                new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, "news").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Вышла новая версия: одно уведомление на версию, нажатие начинает скачивание. */
    static void tellUpdate(Context c) {
        SharedPreferences p = Updater.prefs(c);
        String latest = Updates.latestText(c);
        if (!Updates.available(c) || latest.equals(p.getString(KEY_UPDATE_TOLD, ""))) return;
        p.edit().putString(KEY_UPDATE_TOLD, latest).apply();
        post(c, UPDATE_ID, "Вышел «Звонок» " + latest.split(" ")[0], "Нажми, чтобы скачать. Потом открой файл и нажми «Обновить»",
                new Intent(Intent.ACTION_VIEW, Uri.parse(Updates.APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
