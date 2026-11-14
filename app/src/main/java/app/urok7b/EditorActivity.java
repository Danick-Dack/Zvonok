package app.urok7b;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.util.List;

/**
 * Своё расписание: любые предметы, кабинеты и звонки, Пн–Сб.
 * Хранится только на телефоне. Можно поделиться кодом (ZVS1:...) — тот же код понимает сайт.
 */
public class EditorActivity extends Activity {

    static final String CODE_PREFIX = "ZVS1:";
    private static final int SLOTS = 9;
    private static final String[] DAY_SHORT = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб"};

    private final String[][][] cells = new String[Schedule.MAX_DAYS][SLOTS][2];
    private final String[][] bells = new String[SLOTS][2];
    private int day;
    private LinearLayout dayBar, slotList, bellList;
    private ArrayAdapter<String> subjects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        clear();
        int[][] base = Schedule.bells(this);
        for (int i = 0; i < SLOTS; i++) {
            bells[i][0] = i < base.length && base[i] != null ? Schedule.hm(base[i][0]) : "";
            bells[i][1] = i < base.length && base[i] != null ? Schedule.hm(base[i][1]) : "";
        }
        String saved = Updater.prefs(this).getString(Schedule.KEY_CUSTOM, null);
        if (saved == null || !fill(saved)) {
            takeClass(Updater.prefs(this).getString(Schedule.KEY_CLASS, Schedule.DEFAULT_CLASS));
        }
        int today = java.time.LocalDate.now().getDayOfWeek().getValue() - 1;
        day = today < Schedule.MAX_DAYS ? today : 0;
        subjects = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                Schedule.subjectNames(this));
        build();
    }

    private void build() {
        LinearLayout root = Ui.screen(this, "Своё расписание",
                "Любые предметы, кабинеты и звонки. Хранится только на этом телефоне.");

        boolean on = Updater.prefs(this).getBoolean(Schedule.KEY_USE_CUSTOM, false);
        LinearLayout sw = Ui.switchRow(this, "Использовать своё расписание",
                "Если выключить, будет расписание твоего класса", on, (b, v) -> {
                    if (v && !save()) b.setChecked(false); // ошибка уже показана у поля
                    else if (!v) Updater.prefs(this).edit().putBoolean(Schedule.KEY_USE_CUSTOM, false).apply();
                    applyNow();
                });
        sw.setLayoutParams(Ui.mw(this, 14));
        root.addView(sw);

        dayBar = Ui.horizontal(this);
        dayBar.setBackground(Ui.round(this, Ui.CARD, 18, Ui.LINE));
        dayBar.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        dayBar.setLayoutParams(Ui.mw(this, 14));
        root.addView(dayBar);

        slotList = Ui.vertical(this);
        slotList.setLayoutParams(Ui.mw(this, 12));
        root.addView(slotList);

        LinearLayout tools = Ui.horizontal(this);
        TextView fromClass = Ui.button(this, "Взять класс…", false, v -> pickClass());
        fromClass.setLayoutParams(Ui.weight(1));
        TextView clearDay = Ui.button(this, "Очистить день", false, v -> {
            for (int i = 0; i < SLOTS; i++) cells[day][i][0] = cells[day][i][1] = "";
            showDay();
        });
        LinearLayout.LayoutParams lp = Ui.weight(1);
        lp.leftMargin = Ui.dp(this, 8);
        clearDay.setLayoutParams(lp);
        tools.addView(fromClass);
        tools.addView(clearDay);
        tools.setLayoutParams(Ui.mw(this, 4));
        root.addView(tools);

        root.addView(Ui.label(this, "Звонки"));
        bellList = Ui.card(this);
        for (int i = 0; i < SLOTS; i++) bellList.addView(bellRow(i));
        root.addView(bellList);

        root.addView(Ui.label(this, "Готово"));
        root.addView(Ui.button(this, "Сохранить и включить", true, v -> {
            if (save()) {
                applyNow();
                Toast.makeText(this, "Своё расписание включено", Toast.LENGTH_SHORT).show();
                finish();
            }
        }));
        root.addView(Ui.button(this, "Поделиться кодом расписания", false, v -> shareCode()));
        root.addView(Ui.button(this, "Вставить код от друга или с сайта", false, v -> importCode()));
        TextView hint = Ui.text(this, "Код это обычный текст. В нём только предметы, кабинеты и звонки, больше ничего.",
                12, Ui.MUTED, null);
        hint.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), 0);
        root.addView(hint);

        showDay();
    }

    private void showDay() {
        dayBar.removeAllViews();
        for (int i = 0; i < DAY_SHORT.length; i++) {
            final int d = i;
            boolean sel = i == day;
            TextView t = Ui.text(this, DAY_SHORT[i], 15, sel ? Ui.BG : Ui.TEXT, "sans-serif-medium");
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 9));
            t.setBackground(sel ? Ui.round(this, Ui.TEXT, 14, 0) : null);
            t.setLayoutParams(Ui.weight(1));
            t.setOnClickListener(v -> {
                day = d;
                showDay();
            });
            dayBar.addView(t);
        }
        slotList.removeAllViews();
        for (int i = 0; i < SLOTS; i++) slotList.addView(slotRow(i));
    }

    private View slotRow(int i) {
        LinearLayout r = Ui.horizontal(this);
        r.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));

        LinearLayout num = Ui.vertical(this);
        num.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 44), LinearLayout.LayoutParams.WRAP_CONTENT));
        num.addView(Ui.text(this, String.valueOf(i + 1), 15, Ui.TEXT, "sans-serif-medium"));
        num.addView(Ui.text(this, bells[i][0], 11, Ui.MUTED, null));
        r.addView(num);

        AutoCompleteTextView subj = new AutoCompleteTextView(this);
        styleField(subj, cells[day][i][0], "Предмет");
        subj.setAdapter(subjects);
        subj.setThreshold(1);
        subj.setLayoutParams(Ui.weight(1));
        final int d = day;
        subj.addTextChangedListener(watch(s -> cells[d][i][0] = s));
        r.addView(subj);

        EditText room = Ui.field(this, cells[day][i][1], "Каб.");
        room.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 84), LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, 8);
        room.setLayoutParams(lp);
        room.addTextChangedListener(watch(s -> cells[d][i][1] = s));
        r.addView(room);
        return r;
    }

    private View bellRow(int i) {
        LinearLayout r = Ui.horizontal(this);
        r.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        TextView n = Ui.text(this, (i + 1) + " урок", 14, Ui.SOFT, null);
        n.setLayoutParams(Ui.weight(1));
        r.addView(n);
        EditText s = Ui.field(this, bells[i][0], "8:00");
        EditText e = Ui.field(this, bells[i][1], "8:45");
        for (EditText f : new EditText[]{s, e}) {
            f.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
            f.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 76), LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(this, 8);
            f.setLayoutParams(lp);
        }
        s.addTextChangedListener(watch(v -> bells[i][0] = v));
        e.addTextChangedListener(watch(v -> bells[i][1] = v));
        r.addView(s);
        r.addView(Ui.text(this, "–", 14, Ui.MUTED, null));
        r.addView(e);
        return r;
    }

    private void styleField(EditText e, String value, String hint) {
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(15);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.FAINT);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setBackground(Ui.round(this, Ui.FIELD, 12, Ui.LINE));
        e.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
    }

    interface Setter { void set(String s); }

    private static TextWatcher watch(Setter s) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable e) { s.set(e.toString()); }
        };
    }

    // ---------- данные ----------

    private void clear() {
        for (String[][] d : cells) for (String[] c : d) { c[0] = ""; c[1] = ""; }
    }

    private void takeClass(String cls) {
        clear();
        DayOfWeek[] days = DayOfWeek.values();
        for (int d = 0; d < Schedule.MAX_DAYS; d++) {
            for (Schedule.Lesson l : Schedule.classLessons(this, cls, days[d])) {
                if (l.num - 1 < SLOTS) {
                    cells[d][l.num - 1][0] = l.subject;
                    cells[d][l.num - 1][1] = l.room;
                }
            }
        }
    }

    private void pickClass() {
        List<String> cls = Schedule.classes(this);
        String[] items = cls.toArray(new String[0]);
        new AlertDialog.Builder(this, Ui.dialogTheme())
                .setTitle("Взять за основу класс")
                .setItems(items, (dlg, which) -> {
                    takeClass(items[which]);
                    showDay();
                })
                .show();
    }

    /** JSON своего расписания. */
    private JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("v", 1);
        JSONArray b = new JSONArray();
        for (String[] bell : bells) b.put(new JSONArray().put(bell[0].trim()).put(bell[1].trim()));
        o.put("bells", b);
        JSONArray days = new JSONArray();
        for (String[][] d : cells) {
            JSONArray day = new JSONArray();
            int last = -1;
            for (int i = 0; i < SLOTS; i++) if (!d[i][0].trim().isEmpty()) last = i;
            for (int i = 0; i <= last; i++) {
                day.put(new JSONArray().put(Schedule.clean(d[i][0])).put(Schedule.clean(d[i][1])));
            }
            days.put(day);
        }
        o.put("days", days);
        return o;
    }

    /** Заполнить редактор из JSON (своё сохранённое или импорт). */
    private boolean fill(String json) {
        try {
            JSONObject o = new JSONObject(json);
            Schedule.fromCustom(o); // проверка, что расписание читается
            clear();
            if (o.has("bells")) {
                JSONArray b = o.getJSONArray("bells");
                for (int i = 0; i < SLOTS && i < b.length(); i++) {
                    JSONArray p = b.getJSONArray(i);
                    bells[i][0] = Schedule.clean(p.optString(0, ""));
                    bells[i][1] = Schedule.clean(p.optString(1, ""));
                }
            }
            JSONArray days = o.getJSONArray("days");
            for (int d = 0; d < Schedule.MAX_DAYS && d < days.length(); d++) {
                JSONArray day = days.getJSONArray(d);
                for (int i = 0; i < SLOTS && i < day.length(); i++) {
                    JSONArray l = day.optJSONArray(i);
                    if (l == null) continue;
                    cells[d][i][0] = Schedule.clean(l.optString(0, ""));
                    cells[d][i][1] = Schedule.clean(l.optString(1, ""));
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Первый неверный звонок среди уроков с предметом: {номер урока с 0, что не так}; null, если всё верно.
     * Что не так: 0 — начало не разобрать, 1 — конец не позже начала, 2 — урок начинается раньше конца предыдущего.
     */
    static int[] badBell(String[][] bells, String[][][] cells) {
        int prevEnd = -1;
        for (int i = 0; i < bells.length; i++) {
            boolean used = false;
            for (String[][] d : cells) if (!d[i][0].trim().isEmpty()) used = true;
            if (!used) continue; // звонки пустых уроков ни на что не влияют
            int s = Schedule.parseTime(bells[i][0]), e = Schedule.parseTime(bells[i][1]);
            if (s < 0) return new int[]{i, 0};
            if (e <= s) return new int[]{i, 1};
            if (s < prevEnd) return new int[]{i, 2};
            prevEnd = e;
        }
        return null;
    }

    private boolean save() {
        int[] bad = badBell(bells, cells);
        if (bad != null) {
            // ошибка прямо у поля (её читает и TalkBack), а не всплывашкой, которая исчезает
            EditText f = (EditText) ((LinearLayout) bellList.getChildAt(bad[0])).getChildAt(bad[1] == 1 ? 3 : 1);
            f.setError(bad[1] == 0 ? "Напиши начало урока, например 8:00" : bad[1] == 1
                    ? "Конец урока должен быть позже начала, например 8:45" : "Урок начинается раньше, чем кончился предыдущий");
            f.requestFocus();
            return false;
        }
        try {
            SharedPreferences.Editor ed = Updater.prefs(this).edit();
            ed.putString(Schedule.KEY_CUSTOM, toJson().toString()).putBoolean(Schedule.KEY_USE_CUSTOM, true).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void applyNow() {
        Schedule.invalidate();
        Updater.update(this);
    }

    private void shareCode() {
        try {
            String json = toJson().toString();
            String code = CODE_PREFIX + Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Моё расписание для «Звонка». Открой приложение → "
                            + "Своё расписание → Вставить код:\n\n" + code);
            startActivity(Intent.createChooser(i, "Поделиться расписанием"));
        } catch (Exception ignored) {
        }
    }

    private void importCode() {
        EditText in = Ui.field(this, "", "ZVS1:...");
        in.setSingleLine(false);
        in.setMaxLines(4);
        new AlertDialog.Builder(this, Ui.dialogTheme())
                .setTitle("Вставить код расписания")
                .setView(in)
                .setPositiveButton("Загрузить", (d, w) -> {
                    String s = in.getText().toString().trim();
                    int p = s.indexOf(CODE_PREFIX);
                    boolean ok = false;
                    if (p >= 0 && s.length() < 40_000) {
                        String b64 = s.substring(p + CODE_PREFIX.length()).split("\\s")[0];
                        try {
                            String json = new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8);
                            ok = fill(json);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (ok) {
                        showDay();
                        rebuildBells();
                        Toast.makeText(this, "Загружено. Нажми «Сохранить и включить»", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Не похоже на код расписания", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void rebuildBells() {
        bellList.removeAllViews();
        for (int i = 0; i < SLOTS; i++) bellList.addView(bellRow(i));
    }
}
