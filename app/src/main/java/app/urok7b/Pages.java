package app.urok7b;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** Экраны из «Ещё»: «Олимпиады» (как вкладка на сайте), «Кружки и ШОР» (занятия после уроков) и гимн лицея. */
public class Pages extends Activity {

    static final String EXTRA_PAGE = "page", OLYMP = "olymp", CLUBS = "clubs", ANTHEM = "anthem";
    private static final String[] DAYS = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
    private boolean clubs;
    private int grade = -1;                       // олимпиады: 0 = все классы
    private final boolean[] days = new boolean[7]; // новый кружок
    private String name = "", place = "", from = "16:00", to = "17:30";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        clubs = CLUBS.equals(getIntent().getStringExtra(EXTRA_PAGE));
        Schedule.load(this);
        if (ANTHEM.equals(getIntent().getStringExtra(EXTRA_PAGE))) anthem();
        else if (clubs) clubs();
        else {
            olymp();
            // свежий список с сайта: только с разрешения ходить в интернет, раз за открытие
            if (state == null && Updater.prefs(this).getBoolean(Updates.KEY_AUTO, false)) {
                new Thread(() -> {
                    if (Feed.fetch(getApplicationContext(), Feed.OLYMP)) new Handler(Looper.getMainLooper()).post(() -> { if (!isFinishing()) olymp(); });
                }).start();
            }
        }
    }

    // ---------- гимн лицея: текст из data/anthem.txt, тот же, что на сайте ----------

    private void anthem() {
        LinearLayout root = Ui.screen(this, "Гимн лицея", Schedule.school());
        String text;
        try {
            text = Ui.asset(this, "anthem.txt");
        } catch (java.io.IOException e) {
            return;
        }
        String[] verses = text.trim().split("\\r?\\n\\s*\\r?\\n");
        for (int i = 0; i < verses.length; i++) {
            TextView t = Ui.text(this, verses[i], 17, Ui.TEXT, null);
            t.setLineSpacing(0, 1.3f);
            t.setPadding(Ui.dp(this, i % 2 * 22), 0, 0, 0); // чётные куплеты с отступом, как на сайте
            root.addView(t, Ui.mw(this, 20));
        }
    }

    // ---------- олимпиады ----------

    private void olymp() {
        LinearLayout root = Ui.screen(this, "Олимпиады", "Олимпиады и турниры в Алматы и онлайн · 7–11 классы");
        if (grade < 0) {
            try {
                grade = Schedule.isCustom() ? 0 : Integer.parseInt(Schedule.cls().replaceAll("\\D", ""));
            } catch (NumberFormatException e) {
                grade = 0;
            }
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout pills = Ui.horizontal(this);
        pills.setGravity(android.view.Gravity.TOP); // иначе прокрутка срезает кнопкам верх
        hs.addView(pills);
        for (int g : new int[]{0, 7, 8, 9, 10, 11}) {
            pills.addView(Ui.pill(this, g == 0 ? "Все" : g + " класс", g == grade, v -> { grade = g; olymp(); }));
        }
        root.addView(hs, Ui.mw(this, 16));
        JSONArray items = Feed.items(this, Feed.OLYMP);
        String today = java.time.LocalDate.now().minusDays(3).toString(); // завершившиеся держим ещё три дня, как сайт
        int shown = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null || (grade > 0 && !has(o.optJSONArray("grades"), grade))) continue;
            String end = o.optString("endDate");
            if (!end.isEmpty() && end.compareTo(today) < 0) continue;
            LinearLayout card = Ui.card(this);
            card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
            boolean hot = "hot".equals(o.optString("statusKind"));
            TextView st = Ui.text(this, (o.optString("status") + " · " + o.optString("place")).toUpperCase(), 11, hot ? Ui.ACCENT : Ui.MUTED, "sans-serif-medium");
            st.setLetterSpacing(0.08f);
            card.addView(st);
            TextView title = Ui.heading(Ui.text(this, Schedule.trim(o.optString("title")), 17, Ui.TEXT, "sans-serif-medium"));
            title.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
            card.addView(title);
            card.addView(Ui.text(this, o.optString("dateText"), 14, Ui.SOFT, "sans-serif-medium"));
            String subjects = join(o.optJSONArray("subjects")), who = join(o.optJSONArray("grades"));
            card.addView(Ui.text(this, (who.isEmpty() ? "" : who + " кл. · ") + subjects, 13, Ui.MUTED, null));
            TextView desc = Ui.text(this, o.optString("desc"), 14, Ui.SOFT, null);
            desc.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(desc);
            String url = o.optString("url");
            if (url.startsWith("https://")) { // чужие схемы не открываем
                TextView link = Ui.text(this, "Сайт олимпиады ›", 14, Ui.ACCENT, "sans-serif-medium");
                link.setPadding(0, Ui.dp(this, 10), 0, 0);
                link.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception e) {
                        Toast.makeText(this, "Не нашлось браузера", Toast.LENGTH_SHORT).show();
                    }
                });
                card.addView(link);
            }
            root.addView(card, Ui.mw(this, 12));
            shown++;
        }
        if (shown == 0) root.addView(Ui.text(this, "Для этого класса сейчас ничего нет. Загляни позже.", 15, Ui.MUTED, null), Ui.mw(this, 16));
    }

    private static boolean has(JSONArray a, int v) {
        for (int i = 0; a != null && i < a.length(); i++) if (a.optInt(i) == v) return true;
        return false;
    }

    private static String join(JSONArray a) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; a != null && i < a.length(); i++) b.append(i > 0 ? ", " : "").append(a.optString(i));
        return b.toString();
    }

    // ---------- кружки и ШОР ----------

    private JSONObject set() {
        try {
            return new JSONObject(Updater.prefs(this).getString(Plus.KEY_EXTRAS, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void save(JSONObject set) {
        Updater.prefs(this).edit().putString(Plus.KEY_EXTRAS, set.toString()).apply();
        Schedule.load(this);
        Updater.update(this); // уведомление и виджеты сразу знают про занятие
        clubs();
    }

    private static String times(JSONArray t) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; t != null && i < t.length(); i++) {
            JSONArray x = t.optJSONArray(i);
            if (x == null || x.optInt(0, -1) < 0 || x.optInt(0) > 6) continue;
            b.append(b.length() > 0 ? ", " : "").append(DAYS[x.optInt(0)]).append(' ').append(x.optString(1)).append('–').append(x.optString(2));
        }
        return b.toString();
    }

    private void clubs() {
        LinearLayout root = Ui.screen(this, "Кружки и ШОР", "Отметь свои занятия: таймер покажет, сколько до начала и до конца");
        JSONObject set = set();
        JSONArray picked = set.optJSONArray("shor") == null ? new JSONArray() : set.optJSONArray("shor");
        JSONArray own = set.optJSONArray("clubs") == null ? new JSONArray() : set.optJSONArray("clubs");
        JSONArray groups = Plus.shor();
        if (groups.length() > 0) root.addView(Ui.heading(Ui.caps(this, "ШОР: ШКОЛА ОЛИМПИЙСКОГО РЕЗЕРВА")));
        LinearLayout shor = Ui.card(this);
        shor.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6));
        for (int i = 0; i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i);
            if (g == null) continue;
            String id = g.optString("id");
            boolean on = false;
            for (int k = 0; k < picked.length(); k++) on |= id.equals(picked.optString(k));
            shor.addView(Ui.switchRow(this, g.optString("subject") + " · " + g.optString("coach"),
                    join(g.optJSONArray("grades")) + " кл. · " + times(g.optJSONArray("times")) + " · " + g.optString("place"), on, (b, checked) -> {
                        JSONArray next = new JSONArray();
                        for (int k = 0; k < picked.length(); k++) if (!id.equals(picked.optString(k))) next.put(picked.optString(k));
                        if (checked) next.put(id);
                        try {
                            save(set.put("shor", next));
                        } catch (Exception ignored) {
                            // put с непустым ключом не бросает
                        }
                    }));
        }
        if (groups.length() > 0) root.addView(shor, Ui.mw(this, 0));

        root.addView(Ui.heading(Ui.caps(this, "СВОИ КРУЖКИ")));
        if (own.length() > 0) {
            LinearLayout list = Ui.card(this);
            list.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6));
            for (int i = 0; i < own.length(); i++) {
                JSONObject c = own.optJSONObject(i);
                if (c == null) continue;
                final int at = i;
                String p = Schedule.clean(c.optString("place"));
                list.addView(Ui.linkRow(this, Schedule.clean(c.optString("name")), times(c.optJSONArray("times")) + (p.isEmpty() ? "" : " · " + p), "Удалить", v -> {
                    own.remove(at);
                    try {
                        save(set.put("clubs", own));
                    } catch (Exception ignored) {
                        // put с непустым ключом не бросает
                    }
                }));
            }
            root.addView(list, Ui.mw(this, 0));
        }
        // новый кружок
        LinearLayout form = Ui.card(this);
        form.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 16));
        EditText nameF = Ui.field(this, name, "Название: шахматы, бассейн"), placeF = Ui.field(this, place, "Где (необязательно)");
        EditText fromF = Ui.field(this, from, "16:00"), toF = Ui.field(this, to, "17:30");
        for (EditText e : new EditText[]{nameF, placeF}) e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        form.addView(nameF);
        form.addView(placeF, Ui.mw(this, 8));
        LinearLayout pills = Ui.horizontal(this);
        for (int i = 0; i < 7; i++) {
            final int d = i;
            TextView pill = Ui.pill(this, DAYS[i], days[i], v -> {
                days[d] = !days[d];
                name = nameF.getText().toString(); place = placeF.getText().toString(); from = fromF.getText().toString(); to = toF.getText().toString();
                clubs();
            });
            pill.setPadding(Ui.dp(this, 9), Ui.dp(this, 8), Ui.dp(this, 9), Ui.dp(this, 8));
            ((LinearLayout.LayoutParams) pill.getLayoutParams()).rightMargin = Ui.dp(this, 5);
            pill.setContentDescription(DAYS[i] + (days[i] ? ", выбран" : ""));
            pills.addView(pill);
        }
        form.addView(pills, Ui.mw(this, 10));
        LinearLayout time = Ui.horizontal(this);
        time.addView(Ui.text(this, "с ", 15, Ui.MUTED, null));
        time.addView(fromF, new LinearLayout.LayoutParams(Ui.dp(this, 84), -2));
        time.addView(Ui.text(this, "  до ", 15, Ui.MUTED, null));
        time.addView(toF, new LinearLayout.LayoutParams(Ui.dp(this, 84), -2));
        form.addView(time, Ui.mw(this, 4));
        form.addView(Ui.button(this, "Добавить кружок", true, v -> {
            String n = Schedule.clean(nameF.getText().toString()), f = fromF.getText().toString().trim(), t = toF.getText().toString().trim();
            JSONArray times = new JSONArray();
            for (int i = 0; i < 7; i++) if (days[i]) times.put(new JSONArray().put(i).put(f).put(t));
            String err = n.isEmpty() ? "Напиши название кружка" : times.length() == 0 ? "Выбери дни недели"
                    : Schedule.parseTime(f) < 0 || Schedule.parseTime(t) <= Schedule.parseTime(f) ? "Время вида 16:00, конец позже начала"
                    : own.length() >= 12 ? "Больше 12 кружков не влезет. Удали лишний" : null;
            if (err != null) {
                Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                own.put(new JSONObject().put("name", n).put("place", Schedule.clean(placeF.getText().toString())).put("times", times));
                name = place = "";
                java.util.Arrays.fill(days, false);
                save(set.put("clubs", own));
            } catch (Exception ignored) {
                // put с непустым ключом не бросает
            }
        }), Ui.mw(this, 14));
        root.addView(form, Ui.mw(this, 12));
        TextView note = Ui.text(this, "В дни занятий уведомление и виджеты после уроков считают время до начала, потом до конца. "
                + "В каникулы и праздники занятий нет.", 13, Ui.MUTED, null);
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4), 0);
        root.addView(note);
    }
}
