package app.urok7b;

import android.Manifest;
import android.app.Activity;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/** Главный экран «Звонка»: кольцо-таймер, следующий урок и расписание дня таймлайном. */
public class MainActivity extends Activity {

    // цвета темы: светлая, тёмная или как в системе (Ui.apply); при смене экран пересобирается
    private int BG, TEXT, SOFT, MUTED, FAINT;
    private int builtVersion;
    private LinearLayout hero;
    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    private TextView date, badge, status, title, room, big, caption, sideH, sideV, statH, statV;
    private TextView nextH, nextS, nextR, foot, dayTitle, dayMeta, dayNote, update, vac;
    private LinearLayout dots;
    private Chronometer chrono;
    private RingView ring;
    private LinearLayout nextCard, list;
    private final TextView[] dayBtn = new TextView[6];

    private DayOfWeek selected;
    private String lastSig = "";
    private long lastTarget = -1;
    private boolean askedNotif, askedClass, permsDue;
    private ScrollView scroll;
    private View daysRow;
    static final String EXTRA_WEEK = "week";
    private long notifAskT;

    /** Только что показали системный запрос уведомлений — свою подсказку не накладываем сверху. */
    private boolean askedNotifNow() {
        return System.currentTimeMillis() - notifAskT < 1500;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh(false);
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 300);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        permsDue = state == null; // подсказка про разрешения — при каждом запуске, пока чего-то не хватает
        Ui.apply(this);
        BG = Ui.BG; TEXT = Ui.TEXT; SOFT = Ui.SOFT; MUTED = Ui.MUTED; FAINT = Ui.FAINT;
        builtVersion = Ui.version;
        ScrollView sv = new ScrollView(this);
        scroll = sv;
        sv.setBackgroundColor(BG);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout root = Ui.vertical(this);
        root.setPadding(dp(18), dp(22), dp(18), dp(36));
        sv.addView(root);

        // шапка: название, дата, класс, настройки
        LinearLayout head = Ui.horizontal(this);
        LinearLayout t = Ui.vertical(this);
        t.setLayoutParams(Ui.weight(1));
        t.addView(Ui.text(this, "Звонок", 28, TEXT, "sans-serif-medium"));
        date = Ui.text(this, "", 13, MUTED, null);
        t.addView(date);
        head.addView(t);
        badge = Ui.text(this, "", 15, Ui.ON_ACCENT, "sans-serif-medium");
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(48));
        badge.setPadding(dp(12), 0, dp(12), 0);
        badge.setBackground(Ui.round(this, Ui.ACCENT, 16, 0));
        badge.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        badge.setContentDescription("Выбрать класс");
        badge.setOnClickListener(v -> Ui.pickClass(this, this::rebuild));
        head.addView(badge);
        TextView gear = Ui.text(this, "⚙", 20, TEXT, null);
        gear.setGravity(Gravity.CENTER);
        gear.setBackground(Ui.round(this, Ui.RAISED, 16, Ui.LINE));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(44), dp(44));
        gp.leftMargin = dp(8);
        gear.setLayoutParams(gp);
        gear.setContentDescription("Настройки");
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        head.addView(gear);
        root.addView(head);

        // баннер обновления
        update = Ui.text(this, "", 14, Ui.ON_ACCENT, "sans-serif-medium");
        update.setPadding(dp(16), dp(12), dp(16), dp(12));
        update.setBackground(Ui.round(this, Ui.ACCENT, 16, 0));
        update.setLayoutParams(Ui.mw(this, 16));
        update.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SettingsActivity.APK_URL))));
        root.addView(update);

        // главная карточка с кольцом
        hero = Ui.vertical(this);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setBackground(heroBg(null));
        hero.setPadding(dp(20), dp(18), dp(20), dp(22));
        hero.setLayoutParams(Ui.mw(this, 16));

        LinearLayout top = Ui.horizontal(this);
        top.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        status = Ui.text(this, "", 10, SOFT, "sans-serif-medium");
        status.setLetterSpacing(0.14f);
        status.setPadding(dp(11), dp(6), dp(12), dp(6));
        status.setBackground(Ui.round(this, Ui.RAISED, 12, 0));
        top.addView(status);
        top.addView(new View(this), Ui.weight(1));
        dots = Ui.horizontal(this); // капсулы уроков дня
        top.addView(dots);
        hero.addView(top);

        FrameLayout ringBox = new FrameLayout(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(236), dp(236));
        rp.topMargin = dp(14);
        ringBox.setLayoutParams(rp);
        ring = new RingView(this);
        ringBox.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout center = Ui.vertical(this);
        center.setGravity(Gravity.CENTER);
        chrono = new Chronometer(this);
        chrono.setTextColor(TEXT);
        chrono.setTextSize(50);
        chrono.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        chrono.setFontFeatureSettings("tnum");
        chrono.setIncludeFontPadding(false);
        chrono.setCountDown(true);
        center.addView(chrono);
        big = Ui.text(this, "", 50, TEXT, "sans-serif-thin");
        big.setIncludeFontPadding(false);
        center.addView(big);
        caption = Ui.text(this, "", 10, MUTED, "sans-serif-medium");
        caption.setLetterSpacing(0.16f);
        caption.setPadding(0, dp(8), 0, 0);
        chrono.setGravity(Gravity.CENTER); big.setGravity(Gravity.CENTER); caption.setGravity(Gravity.CENTER);
        center.addView(caption);
        ringBox.addView(center, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hero.addView(ringBox);

        LinearLayout titleRow = Ui.horizontal(this);
        titleRow.setGravity(Gravity.CENTER);
        titleRow.setPadding(0, dp(12), 0, 0);
        title = Ui.text(this, "", 24, TEXT, "sans-serif-medium");
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title);
        room = chip(true);
        titleRow.addView(room);
        hero.addView(titleRow);

        LinearLayout stats = Ui.horizontal(this);
        stats.setLayoutParams(Ui.mw(this, 18));
        LinearLayout c1 = stat(), c2 = stat();
        sideH = (TextView) c1.getChildAt(0);
        sideV = (TextView) c1.getChildAt(1);
        statH = (TextView) c2.getChildAt(0);
        statV = (TextView) c2.getChildAt(1);
        View sep = new View(this);
        sep.setBackgroundColor(Ui.LINE);
        stats.addView(c1);
        stats.addView(sep, new LinearLayout.LayoutParams(dp(1), dp(36)));
        stats.addView(c2);
        hero.addView(stats);
        root.addView(hero);

        // следующий урок
        nextCard = Ui.card(this);
        nextCard.setPadding(dp(18), dp(14), dp(18), dp(16));
        nextCard.setLayoutParams(Ui.mw(this, 12));
        nextH = Ui.text(this, "", 10, MUTED, "sans-serif-medium");
        nextH.setLetterSpacing(0.16f);
        nextCard.addView(nextH);
        LinearLayout nr = Ui.horizontal(this);
        nr.setPadding(0, dp(6), 0, 0);
        nextS = Ui.text(this, "", 18, TEXT, null);
        nextS.setLayoutParams(Ui.weight(1));
        nr.addView(nextS);
        nextR = chip(false);
        nr.addView(nextR);
        nextCard.addView(nr);
        foot = Ui.text(this, "", 12, MUTED, null);
        foot.setPadding(0, dp(10), 0, 0);
        nextCard.addView(foot);
        root.addView(nextCard);

        // сколько учебных дней до каникул
        vac = Ui.text(this, "", 13, MUTED, null);
        vac.setPadding(dp(4), dp(12), dp(4), 0);
        root.addView(vac);

        // дни недели
        LinearLayout days = Ui.horizontal(this);
        days.setBackground(Ui.round(this, Ui.CARD, 18, Ui.LINE));
        days.setPadding(dp(4), dp(4), dp(4), dp(4));
        days.setLayoutParams(Ui.mw(this, 26));
        for (int i = 0; i < 6; i++) {
            DayOfWeek d = DayOfWeek.of(i + 1);
            TextView b = Ui.text(this, "", 15, TEXT, "sans-serif-medium");
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, dp(10), 0, dp(10));
            b.setLayoutParams(Ui.weight(1));
            b.setOnClickListener(v -> { selected = d; lastSig = ""; refresh(true); });
            dayBtn[i] = b;
            days.addView(b);
        }
        root.addView(days);
        daysRow = days;

        LinearLayout dh = Ui.horizontal(this);
        dh.setGravity(Gravity.BOTTOM);
        dh.setPadding(dp(4), dp(22), dp(4), dp(10));
        dayTitle = Ui.text(this, "", 20, TEXT, "sans-serif-medium");
        dayTitle.setLayoutParams(Ui.weight(1));
        dayMeta = Ui.text(this, "", 12, MUTED, null);
        dh.addView(dayTitle);
        dh.addView(dayMeta);
        root.addView(dh);

        // пометка дня: каникулы или замены
        dayNote = Ui.text(this, "", 13, SOFT, null);
        dayNote.setPadding(dp(14), dp(10), dp(14), dp(10));
        dayNote.setBackground(Ui.round(this, (Ui.WARN & 0x00FFFFFF) | 0x26000000, 14, 0));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        np.bottomMargin = dp(10);
        root.addView(dayNote, np);

        list = Ui.vertical(this);
        root.addView(list);

        TextView by = Ui.text(this, "Звонок · " + Schedule.school(), 11, FAINT, null);
        by.setGravity(Gravity.CENTER);
        by.setPadding(0, dp(28), 0, 0);
        root.addView(by, Ui.mw(this, 0));
        setContentView(sv);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /** Ярлык «Неделя»: сразу к дням недели и списку уроков. */
    private void handleIntent(Intent i) {
        // из ярлыка extra приходит строкой "true"
        if (i == null || !"true".equals(String.valueOf(i.getExtras() == null ? null : i.getExtras().get(EXTRA_WEEK)))) return;
        i.removeExtra(EXTRA_WEEK);
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, daysRow.getTop() - dp(12))));
    }

    /** Необязательная подсказка: чего не хватает и что нажать. Можно закрыть «Не сейчас». */
    private void askPerms() {
        java.util.List<Perms.Item> miss = Perms.missing(this);
        if (miss.isEmpty()) return;
        LinearLayout box = Ui.vertical(this);
        box.setPadding(dp(22), dp(4), dp(22), dp(4));
        TextView intro = Ui.text(this, "Это не обязательно, но без этих разрешений таймер в уведомлении и на экране блокировки "
                + "может не появиться или отставать. Нажми на пункт и разреши в открывшихся настройках.", 13, SOFT, null);
        intro.setLineSpacing(0, 1.15f);
        intro.setPadding(0, 0, 0, dp(8));
        box.addView(intro);
        android.app.AlertDialog[] dlg = new android.app.AlertDialog[1];
        for (Perms.Item it : miss) {
            LinearLayout r = Ui.horizontal(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(10), 0, dp(10));
            LinearLayout tl = Ui.twoLines(this, it.title, it.why);
            tl.setLayoutParams(Ui.weight(1));
            r.addView(tl);
            TextView b = Ui.text(this, it.ok == null ? "Открыть" : "Включить", 12, it.ok == null ? TEXT : Ui.ON_ACCENT, "sans-serif-medium");
            b.setBackground(it.ok == null ? Ui.round(this, 0, 12, Ui.LINE) : Ui.round(this, Ui.ACCENT, 12, 0));
            b.setPadding(dp(12), dp(6), dp(12), dp(6));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.leftMargin = dp(10);
            r.addView(b, bp);
            r.setOnClickListener(v -> {
                Perms.markSeen(this, it);
                if (dlg[0] != null) dlg[0].dismiss();
                it.fix.run();
            });
            box.addView(r);
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dlg[0] = new android.app.AlertDialog.Builder(this, Ui.dialogTheme())
                .setTitle("Чтобы всё работало").setView(sv)
                .setNegativeButton("Не сейчас", null)
                .setPositiveButton("Все настройки", (d, w) -> startActivity(new Intent(this, SettingsActivity.class)))
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.palette(this);
        if (Ui.version != builtVersion) {
            recreate();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !askedNotif
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askedNotif = true;
            Updater.prefs(this).edit().putBoolean("notif_asked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, Perms.REQ_NOTIF);
            notifAskT = System.currentTimeMillis();
        }
        Updater.update(this);
        if (!Updater.prefs(this).contains(Schedule.KEY_CLASS) && !askedClass) {
            askedClass = true;
            // запоминаем сразу: если окно закроют без выбора, больше не спрашиваем (класс меняется кнопкой сверху)
            Updater.prefs(this).edit().putString(Schedule.KEY_CLASS, Schedule.DEFAULT_CLASS).apply();
            Ui.pickClass(this, () -> { rebuild(); Updates.askOnce(this, () -> Updates.check(this, ok -> showUpdate())); });
            permsDue = false;
        } else {
            Updates.askOnce(this, () -> Updates.check(this, ok -> showUpdate()));
        }
        if (permsDue && !askedNotifNow()) {
            permsDue = false;
            askPerms();
        }
        if (Updates.due(this)) Updates.check(this, ok -> { showUpdate(); rebuild(); });
        else if (Updates.changesDue(this)) Updates.checkChanges(this, this::rebuild);
        showUpdate();
        rebuild();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        chrono.stop();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        Updater.update(this);
    }

    private void rebuild() {
        selected = null;
        lastSig = "";
        lastTarget = -1;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void showUpdate() {
        boolean a = Updates.available(this);
        update.setVisibility(a ? View.VISIBLE : View.GONE);
        if (a) update.setText("Вышла версия " + Updates.latestText(this) + ". Нажми, чтобы обновить");
    }

    // ---------- обновление экрана ----------

    private void refresh(boolean force) {
        Schedule.load(this);
        ZonedDateTime now = ZonedDateTime.now();
        long nowMs = System.currentTimeMillis();
        Schedule.Status s = Schedule.compute(now);
        Updater.Display d = Updater.describe(s, nowMs);

        LocalDate today = now.toLocalDate();
        date.setText(Schedule.dayName(today.getDayOfWeek()) + ", " + today.getDayOfMonth() + " "
                + MONTHS[today.getMonthValue() - 1]);
        badge.setText(Schedule.title());
        status.setText((d.live ? "●  " : "") + d.label);
        status.setTextColor(d.live ? TEXT : SOFT);
        buildDots(d);

        if (d.timer) {
            if (d.target != lastTarget) {
                chrono.setBase(Updater.chronoBase(d.target));
                chrono.start();
                lastTarget = d.target;
            }
        } else {
            chrono.stop();
            big.setText(d.big);
            lastTarget = -1;
        }
        chrono.setVisibility(d.timer ? View.VISIBLE : View.GONE);
        big.setVisibility(d.timer ? View.GONE : View.VISIBLE);
        caption.setText(captionFor(s));
        String subj = d.kind == Schedule.Kind.LESSON ? d.title
                : d.kind == Schedule.Kind.BREAK || d.kind == Schedule.Kind.BEFORE ? d.nextSubject : null;
        ring.setColor(subj == null ? Ui.ACCENT : Hues.vivid(subj, Ui.DARK));
        ring.setProgress(d.progress >= 0 ? d.progress / 100f : -1);
        hero.setBackground(heroBg(d.timer ? subj : null));

        title.setText(d.title);
        setChip(room, d.room, subj, d.timer);
        sideH.setText(d.sideHead);
        sideV.setText(d.sideValue);
        statH.setText(d.statHead);
        statV.setText(d.statValue);

        nextCard.setVisibility(d.nextSubject.isEmpty() ? View.GONE : View.VISIBLE);
        nextH.setText(d.nextHead);
        nextS.setText(d.nextSubject);
        setChip(nextR, d.nextRoom, d.nextSubject, false);
        foot.setText(d.foot);
        foot.setVisibility(d.foot.isEmpty() ? View.GONE : View.VISIBLE);
        String v = Schedule.vacationText(Schedule.vacation(now));
        vac.setText(v);
        vac.setVisibility(v.isEmpty() ? View.GONE : View.VISIBLE);

        if (selected == null) selected = defaultDay(now, s);
        String sig = selected + "|" + d.sig + "|" + today + "|" + d.progress / 5;
        if (force || !sig.equals(lastSig)) {
            lastSig = sig;
            buildDays(now.getDayOfWeek());
            buildList(now, s, nowMs, d.progress);
        }
    }

    private String captionFor(Schedule.Status s) {
        switch (s.kind) {
            case LESSON: return "ДО ЗВОНКА";
            case BREAK: return "ДО УРОКА";
            case BEFORE: return s.notify ? "ДО УРОКОВ" : "ПЕРВЫЙ ЗВОНОК";
            default:
                if (s.next == null) return "";
                if (s.holiday != null && s.nextDays > 1) return Schedule.word(s.nextDays, "ДЕНЬ", "ДНЯ", "ДНЕЙ").replaceAll("^\\d+ ", "") + " ДО УРОКОВ";
                return s.nextDayLabel.toUpperCase() + " В";
        }
    }

    /** Сегодня, а в выходные и после уроков — следующий учебный день. */
    private DayOfWeek defaultDay(ZonedDateTime now, Schedule.Status s) {
        DayOfWeek today = now.getDayOfWeek();
        if (!Schedule.lessons(today).isEmpty() && s.kind != Schedule.Kind.AFTER) return today;
        for (int i = 1; i <= 7; i++) {
            DayOfWeek w = today.plus(i);
            if (w.getValue() <= 6 && !Schedule.lessons(w).isEmpty()) return w;
        }
        return DayOfWeek.MONDAY;
    }

    private void buildDays(DayOfWeek today) {
        for (int i = 0; i < 6; i++) {
            DayOfWeek d = DayOfWeek.of(i + 1);
            boolean sel = d == selected;
            dayBtn[i].setBackground(sel ? Ui.round(this, Ui.ACCENT, 14, 0) : null);
            dayBtn[i].setTextColor(sel ? Ui.ON_ACCENT : d == today ? TEXT : MUTED);
            dayBtn[i].setText(Schedule.dayShort(d) + (d == today && !sel ? " •" : ""));
        }
        dayBtn[5].setVisibility(Schedule.lessons(DayOfWeek.SATURDAY).isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Капсулы уроков дня: прошедшие серые, текущий длиннее и белый, будущие тёмные. */
    private void buildDots(Updater.Display d) {
        dots.removeAllViews();
        dots.setVisibility(d.dotTotal > 0 ? View.VISIBLE : View.GONE);
        for (int i = 1; i <= d.dotTotal; i++) {
            boolean cur = i == d.dotCurrent;
            View c = new View(this);
            c.setBackground(Ui.round(this, cur ? Hues.vivid(d.title, Ui.DARK) : i <= d.dotDone ? Ui.FAINT : Ui.TRACK, 2, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(cur ? 18 : 10), dp(4));
            lp.leftMargin = dp(4);
            dots.addView(c, lp);
        }
    }

    private void buildList(ZonedDateTime now, Schedule.Status s, long nowMs, int progress) {
        list.removeAllViews();
        // выбранный день недели: сегодня или ближайший такой день впереди (с заменами и каникулами)
        LocalDate date = now.toLocalDate().plusDays((selected.getValue() - now.getDayOfWeek().getValue() + 7) % 7);
        String off = Schedule.dayOff(date);
        boolean isToday = selected == now.getDayOfWeek() && off == null;
        int nowMin = now.getHour() * 60 + now.getMinute();
        List<Schedule.Lesson> l = off != null ? Schedule.lessons(selected) : Schedule.lessonsOn(date);
        dayTitle.setText(selected == now.getDayOfWeek() ? "Сегодня" : Schedule.dayName(selected));
        String note = "";
        if (off != null) {
            note = Schedule.dateText(date) + ": " + off + ". Уроков не будет, ниже обычное расписание этого дня.";
        } else if (Schedule.changesOn(date) != null) {
            String n = Schedule.clean(Schedule.changesOn(date).optString("note", ""));
            List<Integer> gone = Schedule.cancelledOn(date);
            StringBuilder sb = new StringBuilder(n);
            if (n.isEmpty() && !gone.isEmpty()) sb.append("Есть изменения в расписании");
            if (!gone.isEmpty()) {
                sb.append(". ").append(gone.size() > 1 ? "Отменены уроки " : "Отменён урок ");
                for (int i = 0; i < gone.size(); i++) sb.append(i > 0 ? ", " : "").append(gone.get(i));
            }
            note = sb.toString();
            if (note.isEmpty()) {
                for (Schedule.Lesson x : l) if (x.changed || x.shortBell) note = "Есть изменения в расписании";
            }
        }
        dayNote.setText(note);
        dayNote.setTextColor(TEXT);
        dayNote.setBackground(Ui.round(this, ((off != null ? Ui.OK : Ui.WARN) & 0x00FFFFFF) | 0x26000000, 14, 0));
        dayNote.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
        if (l.isEmpty()) {
            dayMeta.setText("");
            TextView e = Ui.text(this, "Уроков нет. Отдыхай.", 15, MUTED, null);
            e.setPadding(dp(4), dp(8), 0, 0);
            list.addView(e);
            return;
        }
        dayMeta.setText(Schedule.lessonsWord(l.size()) + " · " + l.get(0).startText() + "–" + l.get(l.size() - 1).endText());
        for (int i = 0; i < l.size(); i++) {
            Schedule.Lesson x = l.get(i);
            if (i > 0) {
                boolean on = isToday && s.kind == Schedule.Kind.BREAK && s.next != null && s.next.num == x.num;
                int len = x.start - l.get(i - 1).end;
                list.addView(row(null, on ? "●  перемена сейчас · ещё " + Schedule.fmtLeft(s.targetMillis - nowMs)
                        : (len > 25 ? "окно " : "перемена ") + len + " мин", on, false, false, -1, false, false));
            }
            boolean cur = isToday && s.kind == Schedule.Kind.LESSON && s.current != null && s.current.num == x.num;
            boolean next = isToday && (s.kind == Schedule.Kind.BREAK || s.kind == Schedule.Kind.BEFORE)
                    && s.next != null && s.next.num == x.num;
            list.addView(row(x, null, cur, next, isToday && !cur && x.end <= nowMin, cur ? progress : -1,
                    i == 0, i == l.size() - 1));
        }
    }

    /** Строка таймлайна: время | узел с линией | урок (или перемена, если x == null). */
    private View row(Schedule.Lesson x, String breakText, boolean cur, boolean next, boolean past,
                     int progress, boolean first, boolean last) {
        LinearLayout r = Ui.horizontal(this);
        r.setGravity(Gravity.TOP);
        if (past) r.setAlpha(0.4f);

        LinearLayout time = Ui.vertical(this);
        time.setLayoutParams(new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT));
        time.setPadding(0, dp(13), 0, 0);
        if (x != null) {
            time.addView(Ui.text(this, x.startText(), 14, TEXT, "sans-serif-medium"));
            time.addView(Ui.text(this, x.endText(), 11, MUTED, null));
        }
        r.addView(time);

        FrameLayout node = new FrameLayout(this);
        node.setLayoutParams(new LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.MATCH_PARENT));
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(2),
                last ? dp(20) : ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
        lp.topMargin = first ? dp(20) : 0;
        node.addView(line, lp);
        if (x != null) {
            View dot = new View(this);
            int size = cur ? 14 : 10;
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            int hue = Hues.vivid(x.subject, Ui.DARK);
            g.setColor(cur ? hue : past ? Ui.FAINT : Ui.BG);
            g.setStroke(dp(next ? 3 : 2), past ? Ui.FAINT : hue);
            dot.setBackground(g);
            FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(dp(size), dp(size), Gravity.CENTER_HORIZONTAL);
            np.topMargin = dp(cur ? 17 : 19);
            node.addView(dot, np);
        }
        r.addView(node);

        if (x == null) {
            TextView t = Ui.text(this, breakText, 11, cur ? SOFT : FAINT, cur ? "sans-serif-medium" : null);
            t.setPadding(dp(12), dp(4), 0, dp(4));
            r.addView(t);
            return r;
        }
        LinearLayout box = Ui.horizontal(this);
        box.setPadding(dp(14), dp(12), dp(12), dp(12));
        if (cur) box.setBackground(Ui.round(this, Hues.soft(x.subject, Ui.DARK), 18, 0));
        else if (next) box.setBackground(Ui.round(this, Ui.CARD, 18, Hues.vivid(x.subject, Ui.DARK)));
        LinearLayout.LayoutParams bp = Ui.weight(1);
        bp.leftMargin = dp(6);
        box.setLayoutParams(bp);
        LinearLayout mid = Ui.vertical(this);
        mid.setLayoutParams(Ui.weight(1));
        mid.addView(Ui.text(this, x.subject, 16, TEXT, cur ? "sans-serif-medium" : null));
        TextView sub = Ui.text(this, cur ? "Идёт, урок " + x.num : next ? "Следующий, урок " + x.num
                : "Урок " + x.num + " · " + (x.end - x.start) + " мин", 12, cur || next ? Hues.strong(x.subject, Ui.DARK) : MUTED,
                cur || next ? "sans-serif-medium" : null);
        sub.setPadding(0, dp(2), 0, 0);
        mid.addView(sub);
        if (progress >= 0) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(progress);
            pb.setProgressDrawable(getDrawable(R.drawable.progress_mono));
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Hues.vivid(x.subject, Ui.DARK)));
            pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.TRACK));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
            pl.topMargin = dp(8);
            mid.addView(pb, pl);
        }
        box.addView(mid);
        if (x.changed) {
            // замена: плашка с рамкой
            TextView z = Ui.text(this, "замена", 10, Ui.WARN, "sans-serif-medium");
            z.setPadding(dp(6), dp(2), dp(6), dp(2));
            z.setBackground(Ui.round(this, (Ui.WARN & 0x00FFFFFF) | 0x26000000, 6, 0));
            box.addView(z);
        }
        if (!x.room.isEmpty()) {
            TextView c = chip(cur);
            setChip(c, x.room, x.subject, cur);
            box.addView(c);
        }
        r.addView(box);
        return r;
    }

    // ---------- помощники ----------

    private LinearLayout stat() {
        LinearLayout c = Ui.vertical(this);
        c.setGravity(Gravity.CENTER);
        c.setLayoutParams(Ui.weight(1));
        TextView h = Ui.text(this, "", 9, MUTED, "sans-serif-medium");
        h.setLetterSpacing(0.16f);
        c.addView(h);
        c.addView(Ui.text(this, "", 17, TEXT, "sans-serif-medium"));
        return c;
    }

    private TextView chip(boolean now) {
        TextView t = Ui.text(this, "", 14, TEXT, "sans-serif-medium");
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(Ui.round(this, Ui.RAISED, 10, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }

    /** Кабинет в цвете предмета: идущий урок залит, остальные на мягком фоне. */
    private void setChip(TextView c, String room, String subject, boolean now) {
        boolean has = room != null && !room.isEmpty();
        c.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) return;
        c.setText(room);
        if (subject == null || subject.isEmpty()) {
            c.setTextColor(TEXT);
            c.setBackground(Ui.round(this, Ui.RAISED, 10, 0));
        } else {
            c.setTextColor(now ? Hues.onStrong(Ui.DARK) : Hues.strong(subject, Ui.DARK));
            c.setBackground(Ui.round(this, now ? Hues.strong(subject, Ui.DARK) : Hues.soft(subject, Ui.DARK), 10, 0));
        }
    }

    /** Фон карточки таймера: мягкий цвет предмета сверху, как на сайте. */
    private GradientDrawable heroBg(String subject) {
        int top = subject == null ? Ui.CARD : Hues.soft(subject, Ui.DARK);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, Ui.CARD, Ui.CARD});
        g.setCornerRadius(dp(32));
        g.setStroke(dp(1), Ui.LINE);
        return g;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
