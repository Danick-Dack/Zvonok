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
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.List;

public class MainActivity extends Activity {

    private static final int BG = Color.parseColor("#000000");
    private static final int CARD = Color.parseColor("#0D0D0D");
    private static final int TEXT = Color.parseColor("#FFFFFF");
    private static final int MUTED = Color.parseColor("#7A7A7A");

    private TextView stLabel, stTitle, stLine1, stLine2;
    private ProgressBar stProgress;
    private LinearLayout scheduleBox;
    private Button exactBtn;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 15_000);
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
        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(28), dp(18), dp(28));
        sv.addView(root);

        TextView h = text("Уроки 7Б", 30, TEXT, true);
        root.addView(h);
        TextView sub = text("2 четверть · 2026–2027", 14, MUTED, false);
        sub.setPadding(0, dp(2), 0, dp(16));
        root.addView(sub);

        // карточка «сейчас»
        LinearLayout now = card();
        stLabel = text("", 11, MUTED, true);
        stLabel.setLetterSpacing(0.16f);
        stTitle = text("", 24, TEXT, true);
        stTitle.setPadding(0, dp(4), 0, dp(4));
        stLine1 = text("", 15, TEXT, false);
        stLine2 = text("", 15, MUTED, false);
        stProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        stProgress.setMax(100);
        stProgress.setProgressDrawable(getDrawable(R.drawable.progress_mono));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2));
        pp.topMargin = dp(12);
        stProgress.setLayoutParams(pp);
        now.addView(stLabel);
        now.addView(stTitle);
        now.addView(stLine1);
        now.addView(stLine2);
        now.addView(stProgress);
        root.addView(now);

        // настройки
        LinearLayout set = card();
        Switch sw = new Switch(this);
        sw.setText("Уведомление на экране блокировки");
        sw.setTextColor(TEXT);
        sw.setTextSize(16);
        sw.setChecked(Updater.notifEnabled(this));
        sw.setOnCheckedChangeListener((b, on) -> {
            Updater.prefs(this).edit().putBoolean(Updater.KEY_NOTIF, on).apply();
            Updater.update(this);
        });
        set.addView(sw);

        exactBtn = button("Разрешить точное время (будильники)", v -> {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        set.addView(exactBtn);

        set.addView(button("Настройки приложения (автозапуск, батарея)", v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())))));

        TextView hint = text("Xiaomi: в настройках приложения включи «Автозапуск», а в «Контроль активности» "
                + "выбери «Нет ограничений», иначе MIUI может усыплять обновления. "
                + "Виджет: долгое нажатие на рабочий стол → Виджеты → «Уроки 7Б».", 13, MUTED, false);
        hint.setPadding(0, dp(10), 0, 0);
        set.addView(hint);
        root.addView(set);

        scheduleBox = vertical();
        root.addView(scheduleBox);

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
        buildSchedule();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        Updater.update(this);
    }

    private void refresh() {
        ZonedDateTime now = ZonedDateTime.now();
        Schedule.Status s = Schedule.compute(now);
        Updater.Display d = Updater.describe(s, System.currentTimeMillis());
        stLabel.setText(d.label);
        stTitle.setText(d.timer ? d.notifTitle : d.title);
        stLine1.setText(d.line1);
        stLine2.setText(d.line2);
        stLine2.setVisibility(d.line2.isEmpty() ? View.GONE : View.VISIBLE);
        if (d.progress >= 0) {
            stProgress.setVisibility(View.VISIBLE);
            stProgress.setProgress(d.progress);
        } else {
            stProgress.setVisibility(View.GONE);
        }
    }

    private void buildSchedule() {
        scheduleBox.removeAllViews();
        ZonedDateTime now = ZonedDateTime.now();
        Schedule.Status s = Schedule.compute(now);
        DayOfWeek today = now.getDayOfWeek();
        DayOfWeek[] days = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY};
        for (DayOfWeek day : days) {
            boolean isToday = day == today;
            LinearLayout c = card();
            TextView dh = text(Schedule.dayName(day) + (isToday ? "  · сегодня" : ""), 17,
                    isToday ? TEXT : MUTED, true);
            dh.setPadding(0, 0, 0, dp(8));
            c.addView(dh);
            List<Schedule.Lesson> list = Schedule.lessons(day);
            for (Schedule.Lesson l : list) {
                boolean cur = isToday && s.kind == Schedule.Kind.LESSON && s.current != null
                        && s.current.num == l.num;
                boolean nxt = isToday && s.kind != Schedule.Kind.LESSON && s.kind != Schedule.Kind.AFTER
                        && s.next != null && s.next.num == l.num && s.nextDayLabel == null;
                c.addView(row(l, cur, nxt));
            }
            scheduleBox.addView(c);
        }
    }

    private View row(Schedule.Lesson l, boolean current, boolean next) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(8), dp(7), dp(8), dp(7));
        if (current || next) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(10));
            g.setColor(current ? Color.parseColor("#1F1F1F") : Color.parseColor("#121212"));
            r.setBackground(g);
        }
        TextView num = text(String.valueOf(l.num), 14, MUTED, true);
        num.setLayoutParams(new LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView time = text(l.startText() + "–" + l.endText(), 13, MUTED, false);
        time.setLayoutParams(new LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView subj = text(l.subject, 15, TEXT, current);
        subj.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView room = text(l.room, 13, MUTED, false);
        room.setGravity(Gravity.END);
        r.addView(num);
        r.addView(time);
        r.addView(subj);
        r.addView(room);
        return r;
    }

    // ---------- helpers ----------

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout c = vertical();
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(22));
        g.setColor(CARD);
        g.setStroke(1, Color.parseColor("#1F1F1F"));
        c.setBackground(g);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Color.parseColor("#1A1A1A"));
        b.setBackground(g);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
