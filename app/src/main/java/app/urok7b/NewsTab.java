package app.urok7b;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.util.Linkify;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Вкладка «Новости»: посты канала лицея и объявления «Звонка», текст и фото. Газетная шапка, как на сайте. */
final class NewsTab {

    private static final String TG = "https://t.me/sl165_official";
    final ScrollView view;
    private final MainActivity a;
    private final LinearLayout list;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long fetchedAt;
    private boolean loading;

    NewsTab(MainActivity a) {
        this.a = a;
        view = new ScrollView(a);
        view.setVerticalScrollBarEnabled(false);
        LinearLayout root = Ui.vertical(a);
        root.setPadding(a.dp(18), a.dp(20), a.dp(18), a.dp(28));
        view.addView(root);
        // шапка как у газеты: двойная линейка, заголовок с засечками
        root.addView(rule());
        TextView h = Ui.heading(Ui.text(a, "Новости", 30, Ui.TEXT, null));
        h.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        h.setPadding(0, a.dp(6), 0, 0);
        root.addView(h);
        TextView sub = Ui.text(a, "Канал лицея в Telegram", 14, Ui.MUTED, null);
        sub.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        sub.setPadding(0, 0, 0, a.dp(8));
        root.addView(sub);
        root.addView(rule());
        list = Ui.vertical(a);
        root.addView(list, Ui.mw(a, 6));
        root.addView(Ui.button(a, "Открыть канал в Telegram", false, v -> open(TG)), Ui.mw(a, 16));
    }

    private android.view.View rule() {
        android.view.View r = new android.view.View(a);
        r.setBackgroundColor(Ui.TEXT);
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, a.dp(2)));
        return r;
    }

    private void open(String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            android.widget.Toast.makeText(a, "Не нашлось приложения, чтобы это открыть", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** Вкладку открыли: показать сохранённое и, если разрешено, подтянуть свежее (не чаще раза в 10 минут). */
    void show() {
        render();
        if (Updater.prefs(a).getBoolean(Updates.KEY_AUTO, false) && System.currentTimeMillis() - fetchedAt > 600_000) load();
    }

    private void load() {
        if (loading) return;
        loading = true;
        fetchedAt = System.currentTimeMillis();
        android.content.Context app = a.getApplicationContext();
        new Thread(() -> {
            Feed.fetch(app, Feed.NEWS);
            main.post(() -> { loading = false; render(); });
        }).start();
    }

    private void render() {
        JSONArray items = Feed.items(a, Feed.NEWS);
        list.removeAllViews();
        if (items.length() == 0) {
            boolean allowed = Updater.prefs(a).getBoolean(Updates.KEY_AUTO, false);
            list.addView(Ui.text(a, loading ? "Загружаю новости…" : allowed ? "Новости подтянутся, когда появится интернет."
                    : "Новости лежат на сайте «Звонка». Приложение ходит в интернет только с твоего разрешения.", 15, Ui.MUTED, null), Ui.mw(a, 10));
            if (!loading) list.addView(Ui.button(a, "Загрузить новости", true, v -> { load(); render(); }), Ui.mw(a, 12));
            return;
        }
        Updater.prefs(a).edit().putLong(Feed.KEY_SEEN, Feed.top(a)).apply();
        a.newsDot();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < items.length() && i < 30; i++) {
            JSONObject p = items.optJSONObject(i);
            if (p == null || p.optInt("id") <= 0) continue;
            String link = TG + "/" + p.optInt("id");
            boolean own = p.optBoolean("own");
            LinearLayout card = Ui.card(a);
            card.setPadding(0, 0, 0, a.dp(14));
            card.setClipToOutline(true);
            String photo = p.optString("photo");
            if (!photo.isEmpty()) {
                ImageView img = new ImageView(a);
                img.setAdjustViewBounds(true);
                img.setMaxHeight(a.dp(420));
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (own) img.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                else {
                    img.setContentDescription("Фото поста. Открыть в Telegram");
                    img.setOnClickListener(v -> open(link));
                }
                card.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                android.content.Context app = a.getApplicationContext();
                Feed.IO.execute(() -> {
                    Bitmap b = Feed.photo(app, photo);
                    if (b != null) main.post(() -> img.setImageBitmap(b));
                });
            }
            TextView when = Ui.text(a, (own ? "Объявление «Звонка» · " : "") + date(p.optString("date"), today), 13, own ? Ui.ACCENT : Ui.MUTED, null);
            when.setPadding(a.dp(16), a.dp(12), a.dp(16), 0);
            card.addView(when);
            String text = p.optString("text");
            if (!text.isEmpty()) {
                TextView t = Ui.text(a, text, 15, Ui.TEXT, null);
                t.setLineSpacing(0, 1.2f);
                t.setPadding(a.dp(16), a.dp(6), a.dp(16), 0);
                t.setLinkTextColor(Ui.ACCENT);
                Linkify.addLinks(t, Linkify.WEB_URLS); // только ссылки из текста; HTML канала сюда не попадает
                if (text.length() > 420) {
                    t.setMaxLines(9);
                    t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    TextView more = Ui.text(a, "Читать целиком", 14, Ui.ACCENT, "sans-serif-medium");
                    more.setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(2));
                    more.setOnClickListener(v -> { t.setMaxLines(Integer.MAX_VALUE); more.setVisibility(android.view.View.GONE); });
                    card.addView(t);
                    card.addView(more);
                } else card.addView(t);
            }
            if (!own) {
                int more = p.optInt("more");
                TextView tg = Ui.text(a, (p.optBoolean("video") ? "Видео в Telegram" : more > 0
                        ? "Ещё " + more + " фото в Telegram" : "В Telegram") + " ›", 14, Ui.ACCENT, "sans-serif-medium");
                tg.setPadding(a.dp(16), a.dp(10), a.dp(16), 0);
                tg.setOnClickListener(v -> open(link));
                card.addView(tg);
            }
            list.addView(card, Ui.mw(a, 12));
        }
    }

    /** «Сегодня, 14:45» или «6 ноября, 14:45» по часам телефона. */
    private static String date(String iso, LocalDate today) {
        try {
            ZonedDateTime d = Instant.parse(iso.replace("+00:00", "Z")).atZone(ZoneId.systemDefault());
            return (d.toLocalDate().equals(today) ? "Сегодня" : Schedule.dateText(d.toLocalDate())) + ", " + Schedule.hm(d.getHour() * 60 + d.getMinute());
        } catch (Exception e) {
            return "";
        }
    }
}
