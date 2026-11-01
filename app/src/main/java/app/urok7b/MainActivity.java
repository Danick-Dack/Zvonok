package app.urok7b;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/** Главный экран: живой таймер, расписание по дням и настройки. Чёрный минимализм. */
public class MainActivity extends Activity {

    private static final int BG = Color.BLACK;
    private static final int CARD = Color.parseColor("#0B0B0B");
    private static final int LINE = Color.parseColor("#1C1C1C");
    private static final int TEXT = Color.WHITE;
    private static final int SOFT = Color.parseColor("#BDBDBD");
    private static final int MUTED = Color.parseColor("#6E6E6E");
    private static final int FAINT = Color.parseColor("#3C3C3C");

    private static final DayOfWeek[] DAYS = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY};
    private static final String[] DAY_SHORT = {"Пн", "Вт", "Ср", "Чт", "Пт"};
    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    private TextView heroLabel, heroTitle, heroLine1, heroLine2, dateText, daySummary;
    private Chronometer heroChrono;
    private ProgressBar heroProgress;
    private LinearLayout chipsRow, dayList;
    private Button exactBtn;
    private final TextView[] chips = new TextView[DAYS.length];

    private DayOfWeek selectedDay;
    private String lastSignature = "";
    private long lastTarget = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh(false);
            handler.postDelayed(this, 20_000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout root = vertical();
        root.setPadding(dp(22), dp(30), dp(22), dp(36));
        sv.addView(root);

        // шапка
        LinearLayout head = horizontal();
        TextView app = caps("УРОКИ 7Б", MUTED);
        app.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dateText = caps("", MUTED);
        head.addView(app);
        head.addView(dateText);
        root.addView(head);

        // главный блок
        heroLabel = caps("", MUTED);
        heroLabel.setPadding(0, dp(34), 0, 0);
        root.addView(heroLabel);

        heroTitle = text("", 30, TEXT, "sans-serif-light");
        heroTitle.setPadding(0, dp(4), 0, 0);
        root.addView(heroTitle);

        heroChrono = new Chronometer(this);
        heroChrono.setTextColor(TEXT);
        heroChrono.setTextSize(76);
        heroChrono.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        heroChrono.setIncludeFontPadding(false);
        heroChrono.setCountDown(true);
        root.addView(heroChrono);

        heroProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        heroProgress.setMax(100);
        heroProgress.setProgressDrawable(getDrawable(R.drawable.progress_mono));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        pp.topMargin = dp(10);
        heroProgress.setLayoutParams(pp);
        root.addView(heroProgress);

        heroLine1 = text("", 16, TEXT, null);
        heroLine1.setPadding(0, dp(16), 0, 0);
        root.addView(heroLine1);
        heroLine2 = text("", 16, MUTED, null);
        heroLine2.setPadding(0, dp(4), 0, 0);
        root.addView(heroLine2);

        // выбор дня
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(40);
        hs.setLayoutParams(hp);
        chipsRow = horizontal();
        for (int i = 0; i < DAYS.length; i++) {
            final DayOfWeek day = DAYS[i];
            TextView c = text(DAY_SHORT[i], 14, TEXT, "sans-serif-medium");
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(16), dp(8), dp(16), dp(8));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = dp(8);
            c.setLayoutParams(cp);
            c.setOnClickListener(v -> {
                selectedDay = day;
                lastSignature = "";
                refresh(true);
            });
            chips[i] = c;
            chipsRow.addView(c);
        }
        hs.addView(chipsRow);
        root.addView(hs);

        // список уроков дня
        dayList = vertical();
        dayList.setPadding(0, dp(18), 0, 0);
        root.addView(dayList);
        daySummary = text("", 13, MUTED, null);
        daySummary.setPadding(0, dp(12), 0, 0);
        root.addView(daySummary);

        // настройки
        TextView setHead = caps("НАСТРОЙКИ", MUTED);
        setHead.setPadding(0, dp(40), 0, dp(12));
        root.addView(setHead);
        LinearLayout set = card();
        Switch sw = new Switch(this);
        sw.setText("Уведомление на экране блокировки");
        sw.setTextColor(TEXT);
        sw.setTextSize(15);
        sw.setChecked(Updater.notifEnabled(this));
        sw.setOnCheckedChangeListener((b, on) -> {
            Updater.prefs(this).edit().putBoolean(Updater.KEY_NOTIF, on).apply();
            Updater.update(this);
            refresh(true);
        });
        set.addView(sw);

        exactBtn = button("Разрешить точное время звонков", v -> {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        set.addView(exactBtn);

        set.addView(button("Показ на экране блокировки", v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(i);
        }));

        set.addView(button("Автозапуск и батарея (MIUI)", v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())))));

        TextView hint = text("Если уведомления нет на экране блокировки: «Показ на экране блокировки» → "
                + "включи «Экран блокировки» и выбери показ полностью.\n\n"
                + "Уведомление нельзя смахнуть, выключить его можно только переключателем выше. "
                + "Приложение не просыпается, пока экран выключен, и обновляется, когда ты смотришь "
                + "на телефон. На Xiaomi включи «Автозапуск», а в «Контроль активности» выбери «Нет ограничений».\n\n"
                + "Виджеты: долгое нажатие на рабочий стол → Виджеты → «Уроки 7Б» (2×2) или «Уроки 7Б · мини» (2×1).",
                12, MUTED, null);
        hint.setLineSpacing(0, 1.15f);
        hint.setPadding(0, dp(14), 0, 0);
        set.addView(hint);
        root.addView(set);

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        boolean needExact = false;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = getSystemService(AlarmManager.class);
            needExact = am != null && !am.canScheduleExactAlarms();
        }
        exactBtn.setVisibility(needExact ? View.VISIBLE : View.GONE);

        Updater.update(this);
        lastSignature = "";
        lastTarget = -1;
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        heroChrono.stop();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        Updater.update(this);
    }

    // ---------- обновление экрана ----------

    private void refresh(boolean force) {
        ZonedDateTime now = ZonedDateTime.now();
        long nowMs = System.currentTimeMillis();
        Schedule.Status s = Schedule.compute(now);
        Updater.Display d = Updater.describe(s, nowMs);

        LocalDate date = now.toLocalDate();
        String[] wd = {"ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"};
        dateText.setText(wd[date.getDayOfWeek().getValue() - 1] + ", " + date.getDayOfMonth() + " "
                + MONTHS[date.getMonthValue() - 1].toUpperCase());

        heroLabel.setText(d.label);
        heroTitle.setText(d.title);
        heroLine1.setText(d.line1);
        heroLine1.setVisibility(d.line1.isEmpty() ? View.GONE : View.VISIBLE);
        heroLine2.setText(d.line2);
        heroLine2.setVisibility(d.line2.isEmpty() ? View.GONE : View.VISIBLE);

        if (d.timer) {
            heroChrono.setVisibility(View.VISIBLE);
            if (d.target != lastTarget) {
                heroChrono.setBase(SystemClock.elapsedRealtime() + (d.target - nowMs));
                heroChrono.start();
                lastTarget = d.target;
            }
        } else {
            heroChrono.stop();
            heroChrono.setVisibility(View.GONE);
            lastTarget = -1;
        }

        if (d.progress >= 0) {
            heroProgress.setVisibility(View.VISIBLE);
            heroProgress.setProgress(d.progress);
        } else {
            heroProgress.setVisibility(View.GONE);
        }


        if (selectedDay == null) selectedDay = defaultDay(now, s);
        String sig = selectedDay + "|" + s.kind + "|" + (s.current == null ? 0 : s.current.num)
                + "|" + (s.next == null ? 0 : s.next.num) + "|" + date;
        if (force || !sig.equals(lastSignature)) {
            lastSignature = sig;
            buildChips(now.getDayOfWeek());
            buildDay(now, s);
        }
    }

    /** Сегодня, а в выходные и после уроков — следующий учебный день. */
    private DayOfWeek defaultDay(ZonedDateTime now, Schedule.Status s) {
        DayOfWeek today = now.getDayOfWeek();
        boolean weekday = today.getValue() <= 5;
        if (weekday && s.kind != Schedule.Kind.AFTER) return today;
        LocalDate d = now.toLocalDate();
        for (int i = 1; i <= 7; i++) {
            DayOfWeek w = d.plusDays(i).getDayOfWeek();
            if (w.getValue() <= 5) return w;
        }
        return DayOfWeek.MONDAY;
    }

    private void buildChips(DayOfWeek today) {
        for (int i = 0; i < DAYS.length; i++) {
            boolean sel = DAYS[i] == selectedDay;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(18));
            g.setColor(sel ? TEXT : BG);
            g.setStroke(dp(1), sel ? TEXT : (DAYS[i] == today ? MUTED : LINE));
            chips[i].setBackground(g);
            chips[i].setTextColor(sel ? BG : (DAYS[i] == today ? TEXT : MUTED));
        }
    }

    private void buildDay(ZonedDateTime now, Schedule.Status s) {
        dayList.removeAllViews();
        boolean isToday = selectedDay == now.getDayOfWeek();
        int nowMin = now.getHour() * 60 + now.getMinute();
        List<Schedule.Lesson> list = Schedule.lessons(selectedDay);

        for (int i = 0; i < list.size(); i++) {
            Schedule.Lesson l = list.get(i);
            if (i > 0) {
                Schedule.Lesson prev = list.get(i - 1);
                boolean breakNow = isToday && s.kind == Schedule.Kind.BREAK
                        && s.next != null && s.next.num == l.num;
                dayList.addView(breakRow(l.start - prev.end, breakNow));
            }
            boolean current = isToday && s.kind == Schedule.Kind.LESSON && s.current.num == l.num;
            boolean next = isToday && (s.kind == Schedule.Kind.BREAK || s.kind == Schedule.Kind.BEFORE)
                    && s.next != null && s.next.num == l.num;
            boolean past = isToday && !current && l.end <= nowMin
                    && s.kind != Schedule.Kind.BEFORE;
            dayList.addView(lessonRow(l, current, next, past));
        }
        if (!list.isEmpty()) {
            Schedule.Lesson last = list.get(list.size() - 1);
            daySummary.setText(list.size() + " уроков · " + list.get(0).startText() + "–" + last.endText());
        } else {
            daySummary.setText("");
        }
    }

    private View lessonRow(Schedule.Lesson l, boolean current, boolean next, boolean past) {
        LinearLayout r = horizontal();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (current || next) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(16));
            g.setColor(current ? Color.parseColor("#161616") : CARD);
            g.setStroke(dp(1), current ? Color.parseColor("#3A3A3A") : LINE);
            r.setBackground(g);
        }
        int main = past ? FAINT : TEXT;
        int sub = past ? FAINT : MUTED;

        TextView num = text(String.valueOf(l.num), 13, sub, "sans-serif-medium");
        num.setLayoutParams(new LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout mid = vertical();
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView subj = text(l.subject, 16, main, current ? "sans-serif-medium" : null);
        TextView time = text(l.startText() + " – " + l.endText()
                + (current ? "   · сейчас" : next ? "   · дальше" : ""), 12, sub, null);
        time.setPadding(0, dp(2), 0, 0);
        mid.addView(subj);
        mid.addView(time);

        TextView room = text(l.room, 13, current ? TEXT : sub, "sans-serif-medium");
        room.setGravity(Gravity.CENTER);
        room.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable rg = new GradientDrawable();
        rg.setCornerRadius(dp(9));
        rg.setColor(Color.TRANSPARENT);
        rg.setStroke(dp(1), past ? Color.parseColor("#161616") : LINE);
        room.setBackground(rg);

        r.addView(num);
        r.addView(mid);
        r.addView(room);
        return r;
    }

    private View breakRow(int minutes, boolean now) {
        TextView t = text((now ? "● перемена сейчас · " : "перемена ") + minutes + " мин",
                11, now ? SOFT : FAINT, null);
        t.setPadding(dp(50), dp(2), 0, dp(2));
        return t;
    }

    // ---------- helpers ----------

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout c = vertical();
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(CARD);
        g.setStroke(dp(1), LINE);
        c.setBackground(g);
        c.setPadding(dp(18), dp(16), dp(18), dp(18));
        return c;
    }

    private TextView caps(String s, int color) {
        TextView t = text(s, 11, color, "sans-serif-medium");
        t.setLetterSpacing(0.16f);
        return t;
    }

    private TextView text(String s, int sp, int color, String family) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (family != null) t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(14);
        b.setStateListAnimator(null);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Color.parseColor("#141414"));
        g.setStroke(dp(1), LINE);
        b.setBackground(g);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
