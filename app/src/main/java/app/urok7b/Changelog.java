package app.urok7b;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** «Что нового» прямо в приложении: тот же data/changelog.json, что на сайте, с отбором по платформе. */
public class Changelog extends Activity {

    private static final String[] KEYS = {"", "android", "ios", "site"};
    private static final String[] NAMES = {"Всё", "Android", "iPhone", "Сайт"};
    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};
    private int filter;

    static String read(Context c) throws java.io.IOException {
        return Ui.asset(c, "changelog.json");
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) filter = state.getInt("filter");
        build();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("filter", filter);
    }

    private void build() {
        LinearLayout root = Ui.screen(this, "Что нового", "Все обновления сайта, Android и iPhone");
        root.addView(Ui.segments(this, NAMES, filter, k -> { filter = k; build(); }), Ui.mw(this, 16));
        JSONArray log;
        try {
            log = new JSONArray(read(this));
        } catch (Exception e) {
            log = new JSONArray();
        }
        String last = "";
        int shown = 0;
        for (int i = 0; i < log.length(); i++) {
            JSONObject r = log.optJSONObject(i);
            if (r == null) continue;
            JSONArray pl = r.optJSONArray("platforms");
            if (filter > 0 && (pl == null || !pl.toString().contains("\"" + KEYS[filter] + "\""))) continue;
            String date = r.optString("date");
            if (!date.equals(last)) {
                last = date;
                String[] p = date.split("-");
                String text = p.length == 3 ? Integer.parseInt(p[2]) + " " + MONTHS[Integer.parseInt(p[1]) - 1] + " " + p[0] : date;
                root.addView(Ui.heading(Ui.caps(this, text.toUpperCase())));
            }
            LinearLayout card = Ui.card(this);
            card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
            StringBuilder tags = new StringBuilder(shown == 0 ? "НОВОЕ · " : "");
            for (int k = 0; pl != null && k < pl.length(); k++) {
                String p = pl.optString(k);
                tags.append(k > 0 ? " · " : "").append("android".equals(p) ? "ANDROID" + (r.has("version") ? " " + r.optString("version") : "")
                        : "ios".equals(p) ? "IPHONE" : "САЙТ");
            }
            TextView tg = Ui.text(this, tags.toString(), 11, shown == 0 ? Ui.ACCENT : Ui.MUTED, "sans-serif-medium");
            tg.setLetterSpacing(0.1f);
            card.addView(tg);
            TextView title = Ui.heading(Ui.text(this, r.optString("title"), 18, Ui.TEXT, "sans-serif-medium"));
            title.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
            card.addView(title);
            JSONArray items = r.optJSONArray("items");
            for (int k = 0; items != null && k < items.length(); k++) {
                TextView t = Ui.text(this, "•  " + items.optString(k), 15, Ui.SOFT, null);
                t.setLineSpacing(0, 1.15f);
                t.setPadding(0, Ui.dp(this, 4), 0, 0);
                card.addView(t);
            }
            root.addView(card, Ui.mw(this, 0));
            shown++;
        }
        if (shown == 0) root.addView(Ui.text(this, "Тут пока пусто.", 15, Ui.MUTED, null), Ui.mw(this, 16));
    }
}
