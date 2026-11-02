package app.urok7b;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/** Главный экран «Звонка»: живой таймер, расписание по дням, настройки. Чёрный минимализм. */
public class MainActivity extends Activity {

    private static final int BG = Color.BLACK;
    private static final int CARD = Color.parseColor("#0B0B0B");
    private static final int RAISED = Color.parseColor("#161616");
    private static final int LINE = Color.parseColor("#1E1E1E");
    private static final int TEXT = Color.WHITE;
    private static final int SOFT = Color.parseColor("#BDBDBD");
    private static final int MUTED = Color.parseColor("#7A7A7A");
    private static final int FAINT = Color.parseColor("#3A3A3A");

    private static final DayOfWeek[] DAYS = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY};
    private static final String[] DAY_SHORT = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб"};
    /** Адрес сайта (GitHub Pages). Там же — свежий APK и инструкции. */
    static final String SITE = "https://danick-dack.github.io/uroki-7b/";
    private static final String[] DAY_FULL = {"понедельник", "вторник", "среда", "четверг", "пятница",
            "суббота", "воскресенье"};
    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    private TextView dateText, pill, heroDots, heroTitle, heroRoom, heroBig;
    private TextView sideHead, sideValue, statHead, statValue, nextHead, nextSubj, nextRoom, heroFoot;
    private LinearLayout nextBlock, statCol;
    private Chronometer heroChrono;
    private ProgressBar heroProgress;
    private TextView dayTitle, dayMeta, badge;
    private LinearLayout settingsRows;
    private LinearLayout dayList, checks;
    private final LinearLayout[] chips = new LinearLayout[DAYS.length];
    private final TextView[] chipText = new TextView[DAYS.length];
    private final TextView[] chipDot = new TextView[DAYS.length];

    private DayOfWeek selectedDay;
    private String lastSignature = "";
    private long lastTarget = -1;
    private boolean askedNotif, askedClass;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh(false);
            // следующее обновление — ровно на смене минуты (звонки всегда на целой минуте)
            long ms = System.currentTimeMillis();
            handler.postDelayed(this, 60_000 - ms % 60_000 + 300);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(26), dp(18), dp(34));
        sv.addView(root);

        // ---- шапка ----
        LinearLayout head = horizontal();
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = vertical();
        titles.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titles.addView(text("Звонок", 30, TEXT, "sans-serif-medium"));
        dateText = text("", 13, MUTED, null);
        titles.addView(dateText);
        head.addView(titles);
        badge = text("7Б", 15, BG, "sans-serif-medium");
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(TEXT, 16, 0));
        badge.setPadding(dp(10), 0, dp(10), 0);
        badge.setMinWidth(dp(46));
        badge.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)));
        badge.setContentDescription("Выбрать класс");
        badge.setOnClickListener(v -> pickClass());
        head.addView(badge);
        root.addView(head);

        // ---- главная карточка ----
        LinearLayout hero = vertical();
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#171717"), Color.parseColor("#080808")});
        hg.setCornerRadius(dp(28));
        hg.setStroke(dp(1), LINE);
        hero.setBackground(hg);
        hero.setPadding(dp(20), dp(18), dp(20), dp(20));
        hero.setLayoutParams(margins(0, 22, 0, 0));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        pill = caps("", SOFT);
        pill.setPadding(dp(10), dp(5), dp(12), dp(5));
        pill.setBackground(round(Color.parseColor("#202020"), 12, 0));
        top.addView(pill);
        top.addView(spacer());
        heroDots = text("", 8, FAINT, null);
        heroDots.setLetterSpacing(0.3f);
        top.addView(heroDots);
        hero.addView(top);

        LinearLayout titleRow = horizontal();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setLayoutParams(margins(0, 18, 0, 0));
        heroTitle = text("", 26, TEXT, "sans-serif-medium");
        heroTitle.setMaxLines(2);
        heroTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(heroTitle);
        heroRoom = chip("", true);
        titleRow.addView(heroRoom);
        hero.addView(titleRow);

        heroChrono = new Chronometer(this);
        heroChrono.setTextColor(TEXT);
        heroChrono.setTextSize(66);
        heroChrono.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        heroChrono.setFontFeatureSettings("tnum");
        heroChrono.setIncludeFontPadding(false);
        heroChrono.setCountDown(true);
        heroChrono.setLayoutParams(margins(0, 8, 0, 0));
        hero.addView(heroChrono);
        heroBig = text("", 66, TEXT, "sans-serif-thin");
        heroBig.setIncludeFontPadding(false);
        heroBig.setLayoutParams(margins(0, 8, 0, 0));
        hero.addView(heroBig);

        heroProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        heroProgress.setMax(100);
        heroProgress.setProgressDrawable(getDrawable(R.drawable.progress_mono));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        pp.topMargin = dp(12);
        heroProgress.setLayoutParams(pp);
        hero.addView(heroProgress);

        LinearLayout stats = horizontal();
        stats.setLayoutParams(margins(0, 16, 0, 0));
        LinearLayout sideCol = vertical();
        sideCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        sideHead = caps("", MUTED);
        sideValue = text("", 18, TEXT, "sans-serif-medium");
        sideCol.addView(sideHead);
        sideCol.addView(sideValue);
        statCol = vertical();
        statCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        statHead = caps("", MUTED);
        statValue = text("", 18, SOFT, null);
        statCol.addView(statHead);
        statCol.addView(statValue);
        stats.addView(sideCol);
        stats.addView(statCol);
        hero.addView(stats);

        nextBlock = vertical();
        nextBlock.setLayoutParams(margins(0, 18, 0, 0));
        View div = new View(this);
        div.setBackgroundColor(LINE);
        div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        nextBlock.addView(div);
        nextHead = caps("", MUTED);
        nextHead.setPadding(0, dp(16), 0, dp(4));
        nextBlock.addView(nextHead);
        LinearLayout nextRow = horizontal();
        nextRow.setGravity(Gravity.CENTER_VERTICAL);
        nextSubj = text("", 18, TEXT, null);
        nextSubj.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nextRow.addView(nextSubj);
        nextRoom = chip("", false);
        nextRow.addView(nextRoom);
        nextBlock.addView(nextRow);
        hero.addView(nextBlock);

        heroFoot = text("", 12, MUTED, null);
        heroFoot.setPadding(0, dp(14), 0, 0);
        hero.addView(heroFoot);
        root.addView(hero);

        // ---- выбор дня ----
        LinearLayout seg = horizontal();
        seg.setBackground(round(CARD, 18, LINE));
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setLayoutParams(margins(0, 28, 0, 0));
        for (int i = 0; i < DAYS.length; i++) {
            final DayOfWeek day = DAYS[i];
            LinearLayout c = vertical();
            c.setGravity(Gravity.CENTER);
            c.setPadding(0, dp(8), 0, dp(4));
            c.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView t = text(DAY_SHORT[i], 15, TEXT, "sans-serif-medium");
            t.setGravity(Gravity.CENTER);
            TextView dot = text("•", 12, TEXT, null);
            dot.setGravity(Gravity.CENTER);
            dot.setIncludeFontPadding(false);
            c.addView(t);
            c.addView(dot);
            c.setOnClickListener(v -> {
                selectedDay = day;
                lastSignature = "";
                refresh(true);
            });
            chips[i] = c;
            chipText[i] = t;
            chipDot[i] = dot;
            seg.addView(c);
        }
        root.addView(seg);

        LinearLayout dayHead = horizontal();
        dayHead.setGravity(Gravity.BOTTOM);
        dayHead.setLayoutParams(margins(4, 22, 4, 10));
        dayTitle = text("", 20, TEXT, "sans-serif-medium");
        dayTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dayMeta = text("", 12, MUTED, null);
        dayHead.addView(dayTitle);
        dayHead.addView(dayMeta);
        root.addView(dayHead);

        dayList = vertical();
        dayList.setBackground(round(CARD, 24, LINE));
        dayList.setPadding(dp(6), dp(6), dp(6), dp(6));
        root.addView(dayList);

        // ---- настройки ----
        TextView setHead = caps("НАСТРОЙКИ", MUTED);
        setHead.setPadding(dp(4), dp(34), 0, dp(10));
        root.addView(setHead);
        LinearLayout set = vertical();
        set.setBackground(round(CARD, 24, LINE));
        set.setPadding(dp(18), dp(8), dp(18), dp(8));

        LinearLayout swRow = horizontal();
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setPadding(0, dp(10), 0, dp(10));
        LinearLayout swText = twoLines("Уведомление", "Чёрное, на экране блокировки, не смахивается");
        swText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        swRow.addView(swText);
        Switch sw = new Switch(this);
        sw.setChecked(Updater.notifEnabled(this));
        sw.setThumbTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{TEXT, Color.parseColor("#8A8A8A")}));
        sw.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Color.parseColor("#6A6A6A"), Color.parseColor("#2A2A2A")}));
        sw.setOnCheckedChangeListener((b, on) -> {
            Updater.prefs(this).edit().putBoolean(Updater.KEY_NOTIF, on).apply();
            Updater.update(this);
            refresh(true);
        });
        swRow.addView(sw);
        set.addView(swRow);

        settingsRows = vertical();
        set.addView(settingsRows);

        checks = vertical();
        set.addView(checks);
        root.addView(set);

        // ---- поделиться ----
        TextView shareHead = caps("ПОДЕЛИТЬСЯ", MUTED);
        shareHead.setPadding(dp(4), dp(34), 0, dp(10));
        root.addView(shareHead);
        LinearLayout share = vertical();
        share.setBackground(round(CARD, 24, LINE));
        share.setPadding(dp(18), dp(4), dp(18), dp(4));
        share.addView(Ui.linkRow(this, "Отправить приложение другу",
                "APK по Bluetooth, Quick Share, Telegram — даже без интернета", null, v -> shareApk()));
        share.addView(Ui.divider(this));
        share.addView(Ui.linkRow(this, "Поделиться ссылкой на сайт",
                "Расписание всех классов, установка для Android и iPhone", null, v -> shareSite()));
        root.addView(share);

        TextView hint = text("Виджеты: долгое нажатие на рабочий стол → Виджеты → «Звонок» (2×2) "
                        + "или «Звонок · мини» (2×1).\n\n"
                        + "Батарея: пока экран выключен, приложение ничего не делает и телефон не будит. "
                        + "Таймеры тикают сами, без участия приложения.\n\n"
                        + "Приватность: у приложения нет доступа в интернет — оно физически не может ничего "
                        + "отправить. Нет рекламы, аккаунтов и трекеров. Всё хранится только на телефоне.",
                12, MUTED, null);
        hint.setLineSpacing(0, 1.2f);
        hint.setPadding(dp(4), dp(16), dp(4), 0);
        root.addView(hint);

        TextView footer = text("Звонок · " + Schedule.school() + " · версия " + versionName(), 11, FAINT, null);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(28), 0, 0);
        root.addView(footer);

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 33 && !askedNotif
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askedNotif = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Updater.update(this);
        buildSettings();
        buildChecks();
        if (!Updater.prefs(this).contains(Schedule.KEY_CLASS) && !askedClass) {
            askedClass = true;
            pickClass();
        }
        lastSignature = "";
        lastTarget = -1;
        handler.removeCallbacks(tick);
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
        buildChecks();
    }

    // ---------- обновление экрана ----------

    private void refresh(boolean force) {
        Schedule.load(this);
        badge.setText(Schedule.title());
        ZonedDateTime now = ZonedDateTime.now();
        long nowMs = System.currentTimeMillis();
        Schedule.Status s = Schedule.compute(now);
        Updater.Display d = Updater.describe(s, nowMs);

        LocalDate date = now.toLocalDate();
        String wd = DAY_FULL[date.getDayOfWeek().getValue() - 1];
        dateText.setText(Character.toUpperCase(wd.charAt(0)) + wd.substring(1) + ", "
                + date.getDayOfMonth() + " " + MONTHS[date.getMonthValue() - 1] + " · " + Schedule.school());

        pill.setText((d.live ? "●  " : "") + d.label);
        pill.setTextColor(d.live ? TEXT : SOFT);
        if (d.dotTotal > 0) {
            heroDots.setVisibility(View.VISIBLE);
            heroDots.setText(Updater.dots(d, TEXT, Color.parseColor("#5A5A5A"), Color.parseColor("#262626")));
        } else {
            heroDots.setVisibility(View.GONE);
        }
        heroTitle.setText(d.title);
        setChip(heroRoom, d.room);

        if (d.timer) {
            heroChrono.setVisibility(View.VISIBLE);
            heroBig.setVisibility(View.GONE);
            if (d.target != lastTarget) {
                heroChrono.setBase(Updater.chronoBase(d.target));
                heroChrono.start();
                lastTarget = d.target;
            }
        } else {
            heroChrono.stop();
            heroChrono.setVisibility(View.GONE);
            heroBig.setVisibility(View.VISIBLE);
            heroBig.setText(d.big);
            lastTarget = -1;
        }

        if (d.progress >= 0) {
            heroProgress.setVisibility(View.VISIBLE);
            heroProgress.setProgress(d.progress);
        } else {
            heroProgress.setVisibility(View.GONE);
        }

        sideHead.setText(d.sideHead);
        sideValue.setText(d.sideValue);
        statHead.setText(d.statHead);
        statValue.setText(d.statValue);
        statCol.setVisibility(d.statHead.isEmpty() ? View.GONE : View.VISIBLE);

        nextBlock.setVisibility(d.nextSubject.isEmpty() ? View.GONE : View.VISIBLE);
        nextHead.setText(d.nextHead);
        nextSubj.setText(d.nextSubject);
        setChip(nextRoom, d.nextRoom);
        heroFoot.setText(d.foot);
        heroFoot.setVisibility(d.foot.isEmpty() ? View.GONE : View.VISIBLE);

        if (selectedDay == null) selectedDay = defaultDay(now, s);
        String sig = selectedDay + "|" + d.sig + "|" + date + "|" + (d.progress / 5) + "|" + Schedule.title();
        if (force || !sig.equals(lastSignature)) {
            lastSignature = sig;
            buildChips(now.getDayOfWeek());
            buildDay(now, s, nowMs);
        }
    }

    /** Сегодня, а в выходные и после уроков — следующий учебный день. */
    private DayOfWeek defaultDay(ZonedDateTime now, Schedule.Status s) {
        DayOfWeek today = now.getDayOfWeek();
        boolean school = !Schedule.lessons(today).isEmpty();
        if (school && s.kind != Schedule.Kind.AFTER) return today;
        LocalDate d = now.toLocalDate();
        for (int i = 1; i <= 7; i++) {
            DayOfWeek w = d.plusDays(i).getDayOfWeek();
            if (w.getValue() <= 6 && !Schedule.lessons(w).isEmpty()) return w;
        }
        return DayOfWeek.MONDAY;
    }

    private void buildChips(DayOfWeek today) {
        for (int i = 0; i < DAYS.length; i++) {
            boolean sel = DAYS[i] == selectedDay;
            boolean isToday = DAYS[i] == today;
            chips[i].setBackground(sel ? round(TEXT, 14, 0) : null);
            chipText[i].setTextColor(sel ? BG : (isToday ? TEXT : MUTED));
            chipDot[i].setTextColor(sel ? BG : TEXT);
            chipDot[i].setVisibility(isToday ? View.VISIBLE : View.INVISIBLE);
        }
        boolean sat = !Schedule.lessons(DayOfWeek.SATURDAY).isEmpty();
        chips[5].setVisibility(sat ? View.VISIBLE : View.GONE);
    }

    private void buildDay(ZonedDateTime now, Schedule.Status s, long nowMs) {
        dayList.removeAllViews();
        boolean isToday = selectedDay == now.getDayOfWeek();
        int nowMin = now.getHour() * 60 + now.getMinute();
        List<Schedule.Lesson> list = Schedule.lessons(selectedDay);

        String name = DAY_FULL[selectedDay.getValue() - 1];
        dayTitle.setText(isToday ? "Сегодня" : Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (list.isEmpty()) {
            dayMeta.setText("");
            TextView empty = text("Уроков нет", 15, MUTED, null);
            empty.setPadding(dp(16), dp(18), dp(16), dp(18));
            dayList.addView(empty);
            return;
        }
        Schedule.Lesson last = list.get(list.size() - 1);
        dayMeta.setText(Schedule.lessonsWord(list.size()) + " · " + list.get(0).startText() + "–" + last.endText());

        for (int i = 0; i < list.size(); i++) {
            Schedule.Lesson l = list.get(i);
            if (i > 0) {
                Schedule.Lesson prev = list.get(i - 1);
                boolean breakNow = isToday && s.kind == Schedule.Kind.BREAK
                        && s.next != null && s.next.num == l.num;
                dayList.addView(breakRow(l.start - prev.end, breakNow, breakNow ? s.targetMillis - nowMs : 0));
            }
            boolean current = isToday && s.kind == Schedule.Kind.LESSON && s.current.num == l.num;
            boolean next = isToday && (s.kind == Schedule.Kind.BREAK || s.kind == Schedule.Kind.BEFORE)
                    && s.next != null && s.next.num == l.num;
            boolean past = isToday && !current && l.end <= nowMin;
            int progress = current ? Updater.describe(s, nowMs).progress : -1;
            dayList.addView(lessonRow(l, current, next, past, progress, current ? s.targetMillis - nowMs : 0));
        }
    }

    private View lessonRow(Schedule.Lesson l, boolean current, boolean next, boolean past,
                           int progress, long leftMs) {
        LinearLayout r = horizontal();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(12), dp(12), dp(12));
        if (current) r.setBackground(round(RAISED, 18, Color.parseColor("#2C2C2C")));
        else if (next) r.setBackground(round(Color.parseColor("#101010"), 18, LINE));
        if (past) r.setAlpha(0.38f);

        LinearLayout time = vertical();
        time.setLayoutParams(new LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.WRAP_CONTENT));
        time.addView(text(l.startText(), 14, TEXT, "sans-serif-medium"));
        time.addView(text(l.endText(), 11, MUTED, null));

        View bar = new View(this);
        bar.setBackground(round(current ? TEXT : (next ? Color.parseColor("#8A8A8A") : Color.parseColor("#2A2A2A")), 1, 0));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT);
        bp.leftMargin = dp(4);
        bp.rightMargin = dp(14);
        bar.setLayoutParams(bp);

        LinearLayout mid = vertical();
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView subj = text(l.subject, 16, TEXT, current ? "sans-serif-medium" : null);
        String info = "Урок " + l.num + " · " + (l.end - l.start) + " мин";
        if (current) info = "Сейчас · осталось " + Schedule.fmtLeft(leftMs);
        else if (next) info = "Следующий · урок " + l.num;
        TextView sub = text(info, 12, current ? SOFT : MUTED, null);
        sub.setPadding(0, dp(2), 0, 0);
        mid.addView(subj);
        mid.addView(sub);
        if (current && progress >= 0) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(progress);
            pb.setProgressDrawable(getDrawable(R.drawable.progress_mono));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
            lp.topMargin = dp(8);
            pb.setLayoutParams(lp);
            mid.addView(pb);
        }

        r.addView(time);
        r.addView(bar);
        r.addView(mid);
        if (!l.room.isEmpty()) r.addView(chip(l.room, current));
        return r;
    }

    private View breakRow(int minutes, boolean now, long leftMs) {
        TextView t = text(now ? "●  перемена сейчас · ещё " + Schedule.fmtLeft(leftMs)
                : "перемена " + minutes + " мин", 11, now ? SOFT : FAINT, now ? "sans-serif-medium" : null);
        t.setPadding(dp(76), dp(3), 0, dp(3));
        return t;
    }

    // ---------- настройки ----------

    private void buildSettings() {
        settingsRows.removeAllViews();
        settingsRows.addView(Ui.divider(this));
        settingsRows.addView(Ui.linkRow(this, "Класс", Schedule.isCustom() ? "Сейчас включено своё расписание" : null,
                Updater.prefs(this).getString(Schedule.KEY_CLASS, Schedule.DEFAULT_CLASS), v -> pickClass()));
        settingsRows.addView(Ui.linkRow(this, "Своё расписание", "Любые предметы, кабинеты и звонки",
                Schedule.isCustom() ? "вкл" : null, v -> startActivity(new Intent(this, EditorActivity.class))));
        settingsRows.addView(Ui.linkRow(this, "Оформление", "Цвета виджетов и уведомления",
                null, v -> startActivity(new Intent(this, StyleActivity.class))));
        settingsRows.addView(Ui.divider(this));
        settingsRows.addView(Ui.switchRow(this, "Только в учебное время",
                "После уроков и в выходные уведомления нет",
                Updater.prefs(this).getBoolean(Updater.KEY_SCHOOL_ONLY, false),
                (b, on) -> setFlag(Updater.KEY_SCHOOL_ONLY, on)));
        Theme t = Theme.load(this);
        settingsRows.addView(Ui.switchRow(this, "Экономный таймер",
                "Без секунд: «24 мин» вместо 24:16 — ещё меньше расход", !t.seconds, (b, on) -> {
                    Theme th = Theme.load(this);
                    th.seconds = !on;
                    th.save(this);
                    Updater.update(this);
                }));
        settingsRows.addView(Ui.switchRow(this, "Каникулы",
                "Всё на паузе: ни уведомления, ни будильников",
                Updater.prefs(this).getBoolean(Updater.KEY_VACATION, false),
                (b, on) -> setFlag(Updater.KEY_VACATION, on)));
        settingsRows.addView(Ui.divider(this));
    }

    private void setFlag(String key, boolean on) {
        Updater.prefs(this).edit().putBoolean(key, on).apply();
        Updater.update(this);
        refresh(true);
    }

    /** Выбор класса: сетка по параллелям. */
    private void pickClass() {
        List<String> all = Schedule.classes(this);
        String cur = Updater.prefs(this).getString(Schedule.KEY_CLASS, Schedule.DEFAULT_CLASS);
        LinearLayout box = vertical();
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        final AlertDialog[] dlg = new AlertDialog[1];
        String grade = null;
        LinearLayout row = null;
        for (String c : all) {
            String g = c.replaceAll("\\D", "");
            if (!g.equals(grade)) {
                grade = g;
                TextView h = caps(g + " КЛАССЫ", MUTED);
                h.setPadding(0, dp(12), 0, dp(6));
                box.addView(h);
                row = horizontal();
                box.addView(row);
            }
            row.addView(Ui.pill(this, c, c.equals(cur) && !Schedule.isCustom(), v -> {
                Updater.prefs(this).edit().putString(Schedule.KEY_CLASS, c)
                        .putBoolean(Schedule.KEY_USE_CUSTOM, false).apply();
                Schedule.invalidate();
                Updater.update(this);
                selectedDay = null;
                buildSettings();
                refresh(true);
                if (dlg[0] != null) dlg[0].dismiss();
            }));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dlg[0] = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Твой класс")
                .setView(sv)
                .setNegativeButton("Закрыть", null)
                .show();
    }

    /** Отправить APK этого приложения — работает без интернета. */
    private void shareApk() {
        Intent i = new Intent(Intent.ACTION_SEND)
                .setType(ApkProvider.MIME)
                .putExtra(Intent.EXTRA_STREAM, ApkProvider.URI)
                .putExtra(Intent.EXTRA_TEXT, "«Звонок» — расписание лицея №165: таймер до звонка, кабинет, "
                        + "виджеты. Без интернета и рекламы. Сайт: " + SITE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri("Звонок", ApkProvider.URI));
        open(Intent.createChooser(i, "Отправить приложение"));
    }

    private void shareSite() {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "«Звонок» — расписание всех классов лицея №165 с таймером до звонка. "
                        + "Работает без интернета, есть для Android и iPhone: " + SITE);
        open(Intent.createChooser(i, "Поделиться сайтом"));
    }

    // ---------- проверка настроек ----------

    private void buildChecks() {
        checks.removeAllViews();
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean notifOk = nm != null && nm.areNotificationsEnabled();
        if (notifOk) {
            NotificationChannel ch = nm.getNotificationChannel(Updater.CHANNEL);
            notifOk = ch == null || ch.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        checks.addView(checkRow("Показ уведомления", "Разрешение и экран блокировки", notifOk, v -> {
            Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, Updater.CHANNEL);
            if (!open(i)) open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }));

        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = getSystemService(AlarmManager.class);
            boolean exactOk = am == null || am.canScheduleExactAlarms();
            checks.addView(checkRow("Точное время звонков", "Таймер переключается ровно по звонку", exactOk, v ->
                    open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())))));
        }

        PowerManager pm = getSystemService(PowerManager.class);
        boolean batteryOk = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        checks.addView(checkRow("Без ограничений батареи", "Чтобы MIUI не закрывал уведомление. Расход не растёт",
                batteryOk, v -> {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName()));
                    if (!open(i)) open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }));

        checks.addView(checkRow("Автозапуск (Xiaomi)", "Чтобы уведомление вернулось после перезагрузки",
                null, v -> {
                    Intent i = new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                    if (!open(i)) open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
                }));
    }

    /** ok: true — готово, false — нужно включить, null — проверить нельзя. */
    private View checkRow(String title, String sub, Boolean ok, View.OnClickListener l) {
        LinearLayout r = horizontal();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(12), 0, dp(12));
        LinearLayout t = twoLines(title, sub);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(t);
        TextView st;
        if (ok == null) {
            st = text("Открыть", 12, TEXT, "sans-serif-medium");
            st.setBackground(round(0, 12, Color.parseColor("#3A3A3A")));
        } else if (ok) {
            st = text("✓ Готово", 12, MUTED, "sans-serif-medium");
        } else {
            st = text("Включить", 12, BG, "sans-serif-medium");
            st.setBackground(round(TEXT, 12, 0));
        }
        st.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = dp(10);
        st.setLayoutParams(sp);
        r.addView(st);
        r.setOnClickListener(l);
        return r;
    }

    private boolean open(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- helpers ----------

    private LinearLayout twoLines(String title, String sub) {
        LinearLayout t = vertical();
        t.addView(text(title, 15, TEXT, null));
        TextView s = text(sub, 12, MUTED, null);
        s.setPadding(0, dp(2), 0, 0);
        t.addView(s);
        return t;
    }

    private TextView chip(String s, boolean now) {
        TextView t = text(s, 13, now ? BG : TEXT, "sans-serif-medium");
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(now ? round(TEXT, 9, 0) : round(Color.parseColor("#1A1A1A"), 9, Color.parseColor("#2C2C2C")));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }

    private void setChip(TextView chip, String room) {
        boolean has = room != null && !room.isEmpty();
        chip.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) chip.setText(room);
    }

    private GradientDrawable round(int color, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(radiusDp));
        g.setColor(color);
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private View spacer() {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

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

    private TextView caps(String s, int color) {
        TextView t = text(s, 10, color, "sans-serif-medium");
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
