package app.urok7b;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Подписи к урокам, как на сайте (site/src/js/fun.js): что будет на уроке, пишут сами ученики, видит весь класс.
 * Живут в сети, поэтому работают только с интернетом. Адрес базы лежит в data/schedule.json ("notes"):
 * пока он пустой, подписей нет ни на сайте, ни в приложении.
 */
final class Notes {

    private static final String KEY = "notes_cache";
    private static final Pattern RUDE = Pattern.compile("(^|[^а-яё])((на|по|ни|о|а)?ху[йеёяию]|бля|[её]б|сук[аиу](?![а-яё])|муд[аи]|чмо(?![а-яё])|лох(?![а-яё]))"
            + "|пизд|говн|жоп|пид[оа]?р|шлюх|г[ао]ндон|залуп|дроч|(за|на|вы|по|про|до|съ|отъ|разъ|у|долбо)[её]б");
    private static JSONObject days;  // {"2026-11-16": {"n3": {"t": "текст"}}}
    private static String cls = "";  // чьи это подписи
    private static long at;          // когда их скачивали

    private Notes() {}

    static boolean on() {
        return !Schedule.notes().isEmpty() && !Schedule.isCustom();
    }

    private static String clean(String t) {
        t = t.replaceAll("[\\u0000-\\u001f\\u007f]", " ").trim();
        return t.length() > 80 ? t.substring(0, 80) : t;
    }

    /** Подписи своего класса: при смене класса поднимаются из сохранённых. */
    private static JSONObject days(Context c) {
        if (days != null && cls.equals(Schedule.cls())) return days;
        cls = Schedule.cls();
        at = 0;
        days = new JSONObject();
        try {
            JSONObject saved = new JSONObject(Updater.prefs(c).getString(KEY, "{}"));
            if (cls.equals(saved.optString("cls")) && saved.optJSONObject("days") != null) days = saved.optJSONObject("days");
        } catch (JSONException e) {
            // сохранённое испорчено: начнём с пустого
        }
        return days;
    }

    private static void save(Context c) {
        try {
            Updater.prefs(c).edit().putString(KEY, new JSONObject().put("cls", cls).put("days", days).toString()).apply();
        } catch (JSONException e) {
            // ключи не пустые, сюда не попадаем
        }
    }

    static String of(Context c, LocalDate date, int num) {
        if (!on()) return "";
        JSONObject day = days(c).optJSONObject(date.toString()), n = day == null ? null : day.optJSONObject("n" + num);
        return n == null ? "" : clean(n.optString("t"));
    }

    private static String path(String c) {
        return Schedule.notes() + "/notes/" + MoreTab.slug(c);
    }

    /** Свежие подписи класса: не чаще раза в 5 минут. changed: в главном потоке, если что-то поменялось. */
    static void load(Context app, Runnable changed) {
        if (!on()) return;
        days(app);
        if (System.currentTimeMillis() - at < 300_000) return;
        at = System.currentTimeMillis();
        String c = cls, url = path(c) + ".json?orderBy=%22$key%22&startAt=%22" + LocalDate.now() + "%22";
        new Thread(() -> {
            String body = http("GET", url, null);
            if (body == null) return;
            new Handler(Looper.getMainLooper()).post(() -> {
                JSONObject next;
                try {
                    next = body.trim().equals("null") ? new JSONObject() : new JSONObject(body);
                } catch (JSONException e) {
                    return;
                }
                if (!c.equals(cls) || next.toString().equals(days.toString())) return;
                days = next;
                save(app);
                changed.run();
            });
        }).start();
    }

    /** Окно подписи к уроку. Пустая строка убирает подпись. saved: когда подпись ушла в сеть. */
    static void edit(Activity a, LocalDate date, Schedule.Lesson l, Runnable saved) {
        EditText field = Ui.field(a, of(a, date, l.num), "Контрольная, принести атлас");
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        TextView hint = Ui.text(a, "Подпись увидит весь класс. Пустая строка убирает подпись.", 13, Ui.MUTED, null);
        hint.setPadding(0, Ui.dp(a, 8), 0, 0);
        LinearLayout box = Ui.vertical(a);
        box.setPadding(Ui.dp(a, 24), Ui.dp(a, 8), Ui.dp(a, 24), 0);
        box.addView(field);
        box.addView(hint);
        String c = cls, day = date.toString(), key = "n" + l.num;
        new AlertDialog.Builder(a, Ui.dialogTheme()).setTitle(l.subject)
                .setMessage(Schedule.dayName(date.getDayOfWeek()) + ", " + Schedule.dateText(date) + " · урок " + l.num)
                .setView(box).setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String t = clean(field.getText().toString());
                    if (RUDE.matcher(t.toLowerCase(java.util.Locale.ROOT)).find()) {
                        Toast.makeText(a, "Давай без грубостей", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    new Thread(() -> {
                        String body = null;
                        try {
                            if (!t.isEmpty()) body = new JSONObject().put("t", t).put("at", new JSONObject().put(".sv", "timestamp")).toString();
                        } catch (JSONException e) {
                            return;
                        }
                        boolean ok = http(body == null ? "DELETE" : "PUT", path(c) + "/" + day + "/" + key + ".json", body) != null;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (ok && c.equals(cls)) {
                                try {
                                    JSONObject x = days.optJSONObject(day);
                                    if (x == null) days.put(day, x = new JSONObject());
                                    if (t.isEmpty()) x.remove(key);
                                    else x.put(key, new JSONObject().put("t", t));
                                } catch (JSONException e) {
                                    // ключи не пустые, сюда не попадаем
                                }
                                save(a.getApplicationContext());
                                saved.run();
                            }
                            Toast.makeText(a.getApplicationContext(), !ok ? "Не получилось сохранить. Проверь интернет"
                                    : t.isEmpty() ? "Подпись убрана" : "Подпись сохранена, её видит весь класс", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                }).show();
    }

    /** Запрос к базе подписей (адрес всегда HTTPS): тело ответа или null, если не вышло. */
    private static String http(String method, String url, String body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setRequestMethod(method);
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (c.getResponseCode() / 100 != 2) return null;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0 && b.size() < 200_000; ) b.write(buf, 0, n);
                return new String(b.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
