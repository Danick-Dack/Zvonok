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

    private static final int BG = Ui.BG, TEXT = Ui.TEXT, SOFT = Ui.SOFT, MUTED = Ui.MUTED, FAINT = Ui.FAINT;
    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    private TextView date, badge, status, dots, title, room, big, caption, sideH, sideV, statH, statV;
    private TextView nextH, nextS, nextR, foot, dayTitle, dayMeta, update;
    private Chronometer chrono;
    private RingView ring;
    private LinearLayout nextCard, list;
    private final TextView[] dayBtn = new TextView[6];

    private DayOfWeek selected;
    private String lastSig = "";
    private long lastTarget = -1;
    private boolean askedNotif, askedClass;

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
        ScrollView sv = new ScrollView(this);
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
        badge = Ui.text(this, "", 15, BG, "sans-serif-medium");
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(dp(48));
        badge.setPadding(dp(12), 0, dp(12), 0);
        badge.setBackground(Ui.round(this, TEXT, 16, 0));
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
        update = Ui.text(this, "", 14, BG, "sans-serif-medium");
        update.setPadding(dp(16), dp(12), dp(16), dp(12));
        update.setBackground(Ui.round(this, TEXT, 16, 0));
        update.setLayoutParams(Ui.mw(this, 16));
        update.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SettingsActivity.APK_URL))));
        root.addView(update);

        // главная карточка с кольцом
        LinearLayout hero = Ui.vertical(this);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#141416"), Color.parseColor("#060607")});
        hg.setCornerRadius(dp(32));
        hg.setStroke(dp(1), Ui.LINE);
        hero.setBackground(hg);
        hero.setPadding(dp(20), dp(18), dp(20), dp(22));
        hero.setLayoutParams(Ui.mw(this, 16));

        LinearLayout top = Ui.horizontal(this);
        top.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        status = Ui.text(this, "", 10, SOFT, "sans-serif-medium");
        status.setLetterSpacing(0.14f);
        status.setPadding(dp(11), dp(6), dp(12), dp(6));
        status.setBackground(Ui.round(this, Color.parseColor("#1E1E20"), 12, 0));
        top.addView(status);
        top.addView(new View(this), Ui.weight(1));
        dots = Ui.text(this, "", 8, FAINT, null);
        dots.setLetterSpacing(0.3f);
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

        LinearLayout dh = Ui.horizontal(this);
        dh.setGravity(Gravity.BOTTOM);
        dh.setPadding(dp(4), dp(22), dp(4), dp(10));
        dayTitle = Ui.text(this, "", 20, TEXT, "sans-serif-medium");
        dayTitle.setLayoutParams(Ui.weight(1));
        dayMeta = Ui.text(this, "", 12, MUTED, null);
        dh.addView(dayTitle);
        dh.addView(dayMeta);
        root.addView(dh);

        list = Ui.vertical(this);
        root.addView(list);

        TextView by = Ui.text(this, "Звонок · " + Schedule.school(), 11, FAINT, null);
        by.setGravity(Gravity.CENTER);
        by.setPadding(0, dp(28), 0, 0);
        root.addView(by, Ui.mw(this, 0));
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
        if (!Updater.prefs(this).contains(Schedule.KEY_CLASS) && !askedClass) {
            askedClass = true;
            Ui.pickClass(this, () -> { rebuild(); Updates.askOnce(this, () -> Updates.check(this, ok -> showUpdate())); });
        } else {
            Updates.askOnce(this, () -> Updates.check(this, ok -> showUpdate()));
        }
        if (Updates.due(this)) Updates.check(this, ok -> showUpdate());
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
        dots.setVisibility(d.dotTotal > 0 ? View.VISIBLE : View.GONE);
        dots.setText(Updater.dots(d, TEXT, Color.parseColor("#5A5A5A"), Color.parseColor("#262626")));

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
        ring.setProgress(d.progress >= 0 ? d.progress / 100f : -1);

        title.setText(d.title);
        setChip(room, d.room);
        sideH.setText(d.sideHead);
        sideV.setText(d.sideValue);
        statH.setText(d.statHead);
        statV.setText(d.statValue);

        nextCard.setVisibility(d.nextSubject.isEmpty() ? View.GONE : View.VISIBLE);
        nextH.setText(d.nextHead);
        nextS.setText(d.nextSubject);
        setChip(nextR, d.nextRoom);
        foot.setText(d.foot);
        foot.setVisibility(d.foot.isEmpty() ? View.GONE : View.VISIBLE);

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
            default: return s.next == null ? "" : s.nextDayLabel.toUpperCase() + " В";
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
            dayBtn[i].setBackground(sel ? Ui.round(this, TEXT, 14, 0) : null);
            dayBtn[i].setTextColor(sel ? BG : d == today ? TEXT : MUTED);
            dayBtn[i].setText(Schedule.dayShort(d) + (d == today && !sel ? " •" : ""));
        }
        dayBtn[5].setVisibility(Schedule.lessons(DayOfWeek.SATURDAY).isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void buildList(ZonedDateTime now, Schedule.Status s, long nowMs, int progress) {
        list.removeAllViews();
        boolean isToday = selected == now.getDayOfWeek();
        int nowMin = now.getHour() * 60 + now.getMinute();
        List<Schedule.Lesson> l = Schedule.lessons(selected);
        dayTitle.setText(isToday ? "Сегодня" : Schedule.dayName(selected));
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
                boolean on = isToday && s.kind == Schedule.Kind.BREAK && s.next == x;
                int len = x.start - l.get(i - 1).end;
                list.addView(row(null, on ? "●  перемена сейчас · ещё " + Schedule.fmtLeft(s.targetMillis - nowMs)
                        : (len > 25 ? "окно " : "перемена ") + len + " мин", on, false, false, -1, false, false));
            }
            boolean cur = isToday && s.kind == Schedule.Kind.LESSON && s.current == x;
            boolean next = isToday && (s.kind == Schedule.Kind.BREAK || s.kind == Schedule.Kind.BEFORE) && s.next == x;
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
        line.setBackgroundColor(Color.parseColor("#232326"));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(2),
                last ? dp(20) : ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL);
        lp.topMargin = first ? dp(20) : 0;
        node.addView(line, lp);
        if (x != null) {
            View dot = new View(this);
            int size = cur ? 14 : 10;
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(cur ? TEXT : next ? Color.parseColor("#8A8A8A") : BG);
            g.setStroke(dp(2), cur || next ? TEXT : Color.parseColor("#3A3A3E"));
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
        if (cur) box.setBackground(Ui.round(this, Ui.RAISED, 18, Color.parseColor("#2C2C30")));
        else if (next) box.setBackground(Ui.round(this, Color.parseColor("#0F0F11"), 18, Ui.LINE));
        LinearLayout.LayoutParams bp = Ui.weight(1);
        bp.leftMargin = dp(6);
        box.setLayoutParams(bp);
        LinearLayout mid = Ui.vertical(this);
        mid.setLayoutParams(Ui.weight(1));
        mid.addView(Ui.text(this, x.subject, 16, TEXT, cur ? "sans-serif-medium" : null));
        TextView sub = Ui.text(this, cur ? "Сейчас · урок " + x.num : next ? "Следующий · урок " + x.num
                : "Урок " + x.num + " · " + (x.end - x.start) + " мин", 12, cur ? SOFT : MUTED, null);
        sub.setPadding(0, dp(2), 0, 0);
        mid.addView(sub);
        if (progress >= 0) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(progress);
            pb.setProgressDrawable(getDrawable(R.drawable.progress_mono));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
            pl.topMargin = dp(8);
            mid.addView(pb, pl);
        }
        box.addView(mid);
        if (!x.room.isEmpty()) {
            TextView c = chip(cur);
            c.setText(x.room);
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
        TextView t = Ui.text(this, "", 13, now ? BG : TEXT, "sans-serif-medium");
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        t.setBackground(now ? Ui.round(this, TEXT, 9, 0)
                : Ui.round(this, Color.parseColor("#1A1A1C"), 9, Color.parseColor("#2C2C30")));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(10);
        t.setLayoutParams(lp);
        return t;
    }

    private void setChip(TextView c, String room) {
        boolean has = room != null && !room.isEmpty();
        c.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) c.setText(room);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
