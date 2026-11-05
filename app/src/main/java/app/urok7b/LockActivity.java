package app.urok7b;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Экран поверх блокировки: открывается нажатием на уведомление, когда телефон заблокирован.
 * Кольцо с таймером, урок и кабинет, что дальше и весь день списком; листается, как приложение.
 * На разблокированном телефоне сразу передаёт управление главному экрану.
 * Экран не включает, блокировку не снимает. Обновляется только пока виден: без фоновых таймеров.
 * Закрывается нажатием, «Назад» или кнопкой питания.
 */
public class LockActivity extends Activity {

    private TextView label, big, caption, title, room, sub, nextH, nextS, nextR, dayH, foot;
    private Chronometer chrono;
    private RingView ring;
    private LinearLayout next, list;
    private long lastTarget = -1;
    private String lastList = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long wait = refresh();
            handler.postDelayed(this, wait);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km == null || !km.isKeyguardLocked()) {
            openMain();
            return;
        }
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(false);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        Ui.apply(this);
        build();
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
        overridePendingTransition(0, 0);
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout root = Ui.vertical(this);
        root.setPadding(dp(24), dp(36), dp(24), dp(36));
        root.setOnClickListener(v -> finish());
        sv.addView(root);

        label = Ui.text(this, "", 12, Ui.SOFT, "sans-serif-medium");
        label.setLetterSpacing(0.14f);
        root.addView(label);

        // кольцо с таймером внутри, как на главном экране и на сайте
        FrameLayout box = new FrameLayout(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(250), dp(250));
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        rp.topMargin = dp(18);
        ring = new RingView(this);
        box.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout center = Ui.vertical(this);
        center.setGravity(Gravity.CENTER);
        chrono = new Chronometer(this);
        chrono.setTextColor(Ui.TEXT);
        chrono.setTextSize(54);
        chrono.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        chrono.setFontFeatureSettings("tnum");
        chrono.setIncludeFontPadding(false);
        chrono.setCountDown(true);
        chrono.setGravity(Gravity.CENTER);
        center.addView(chrono);
        big = Ui.text(this, "", 54, Ui.TEXT, "sans-serif-thin");
        big.setIncludeFontPadding(false);
        big.setGravity(Gravity.CENTER);
        center.addView(big);
        caption = Ui.text(this, "", 12, Ui.MUTED, "sans-serif-medium");
        caption.setLetterSpacing(0.14f);
        caption.setPadding(0, dp(8), 0, 0);
        caption.setGravity(Gravity.CENTER);
        center.addView(caption);
        box.addView(center, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(box, rp);

        // урок и кабинет крупно: на перемене это главное
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(0, dp(22), 0, 0);
        title = Ui.text(this, "", 30, Ui.TEXT, "sans-serif-medium");
        title.setLayoutParams(Ui.weight(1));
        row.addView(title);
        room = Ui.text(this, "", 26, Ui.TEXT, "sans-serif-medium");
        room.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        room.setPadding(dp(14), dp(4), dp(14), dp(6));
        row.addView(room);
        root.addView(row);
        sub = Ui.text(this, "", 15, Ui.MUTED, null);
        sub.setPadding(0, dp(6), 0, 0);
        root.addView(sub);

        next = Ui.vertical(this);
        next.setBackground(Ui.round(this, Ui.CARD, 20, Ui.LINE));
        next.setPadding(dp(16), dp(12), dp(16), dp(14));
        LinearLayout.LayoutParams np = Ui.mw(this, 22);
        nextH = Ui.text(this, "", 11, Ui.MUTED, "sans-serif-medium");
        nextH.setLetterSpacing(0.14f);
        next.addView(nextH);
        LinearLayout nr = Ui.horizontal(this);
        nr.setPadding(0, dp(6), 0, 0);
        nextS = Ui.text(this, "", 19, Ui.TEXT, null);
        nextS.setLayoutParams(Ui.weight(1));
        nr.addView(nextS);
        nextR = Ui.text(this, "", 17, Ui.TEXT, "sans-serif-medium");
        nextR.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        nextR.setPadding(dp(10), dp(3), dp(10), dp(4));
        nr.addView(nextR);
        next.addView(nr);
        root.addView(next, np);

        // весь день: листается вниз
        dayH = Ui.caps(this, "СЕГОДНЯ");
        root.addView(dayH);
        list = Ui.vertical(this);
        root.addView(list);

        foot = Ui.text(this, "", 13, Ui.MUTED, null);
        foot.setPadding(0, dp(18), 0, 0);
        root.addView(foot);

        TextView hint = Ui.text(this, "Нажми в любом месте, чтобы закрыть", 12, Ui.MUTED, null);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(28), 0, 0);
        root.addView(hint, Ui.mw(this, 0));

        setContentView(sv);
    }

    /** @return через сколько обновить снова (до звонка или до следующей минуты). */
    private long refresh() {
        Schedule.load(this);
        ZonedDateTime now = ZonedDateTime.now();
        long nowMs = System.currentTimeMillis();
        Schedule.Status s = Schedule.compute(now);
        Updater.Display d = Updater.describe(s, nowMs);
        String subj = s.kind == Schedule.Kind.LESSON ? d.title : d.nextSubject;

        label.setText((d.live ? "●  " : "") + d.label);
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
        caption.setText(d.timer ? (s.kind == Schedule.Kind.LESSON ? "ДО ЗВОНКА" : "ДО УРОКА") : d.sideHead);
        ring.setColor(subj == null || subj.isEmpty() ? Ui.ACCENT : Hues.vivid(subj, Ui.DARK));
        ring.setProgress(d.progress >= 0 ? d.progress / 100f : -1);

        title.setText(d.title);
        room.setText(d.room);
        room.setVisibility(d.room.isEmpty() ? View.GONE : View.VISIBLE);
        room.setTextColor(Hues.onStrong(Ui.DARK));
        room.setBackground(Ui.round(this, Hues.strong(subj, Ui.DARK), 16, 0));
        sub.setText(d.sideHead.isEmpty() ? "" : capital(d.sideHead) + " " + d.sideValue);
        sub.setVisibility(d.sideHead.isEmpty() || !d.timer ? View.GONE : View.VISIBLE);

        next.setVisibility(d.nextSubject.isEmpty() ? View.GONE : View.VISIBLE);
        nextH.setText(d.nextHead);
        nextS.setText(d.nextSubject);
        nextR.setText(d.nextRoom);
        nextR.setVisibility(d.nextRoom.isEmpty() ? View.GONE : View.VISIBLE);
        nextR.setTextColor(Hues.strong(d.nextSubject, Ui.DARK));
        nextR.setBackground(Ui.round(this, Hues.soft(d.nextSubject, Ui.DARK), 12, 0));

        String v = Schedule.vacationText(Schedule.vacation(now));
        String f = d.foot + (v.isEmpty() ? "" : (d.foot.isEmpty() ? "" : "\n") + v);
        foot.setText(f);
        foot.setVisibility(f.isEmpty() ? View.GONE : View.VISIBLE);

        buildDay(now, s);

        long toMinute = 60_000 - nowMs % 60_000 + 300;
        long toTarget = d.timer ? d.target - nowMs + 300 : Long.MAX_VALUE;
        return Math.max(1_000, Math.min(toMinute, toTarget));
    }

    /** Уроки дня списком: время, точка в цвете предмета, предмет, кабинет. Перерисовывается только при смене урока. */
    private void buildDay(ZonedDateTime now, Schedule.Status s) {
        LocalDate date = now.toLocalDate();
        List<Schedule.Lesson> l = Schedule.lessonsOn(date);
        boolean today = !l.isEmpty() && s.kind != Schedule.Kind.AFTER;
        if (!today) {
            date = s.next != null && s.nextDays > 0 ? date.plusDays(s.nextDays) : date.plusDays(1);
            l = Schedule.lessonsOn(date);
        }
        int cur = s.kind == Schedule.Kind.LESSON && s.current != null ? s.current.num : -1;
        int nx = (s.kind == Schedule.Kind.BREAK || s.kind == Schedule.Kind.BEFORE) && s.next != null ? s.next.num : -1;
        String sig = date + "|" + cur + "|" + nx + "|" + l.size();
        if (sig.equals(lastList)) return;
        lastList = sig;
        dayH.setText(today ? "СЕГОДНЯ" : (s.nextDays == 1 ? "ЗАВТРА" : Schedule.dayName(date.getDayOfWeek()).toUpperCase()));
        list.removeAllViews();
        int nowMin = now.getHour() * 60 + now.getMinute();
        for (Schedule.Lesson x : l) {
            boolean isCur = today && x.num == cur, isNext = today && x.num == nx, past = today && !isCur && x.end <= nowMin;
            LinearLayout r = Ui.horizontal(this);
            r.setPadding(dp(12), dp(10), dp(12), dp(10));
            if (isCur) r.setBackground(Ui.round(this, Hues.soft(x.subject, Ui.DARK), 16, 0));
            else if (isNext) r.setBackground(Ui.round(this, 0, 16, Hues.vivid(x.subject, Ui.DARK)));
            if (past) r.setAlpha(0.45f);
            TextView t = Ui.text(this, x.startText(), 15, Ui.TEXT, "sans-serif-medium");
            t.setFontFeatureSettings("tnum");
            t.setLayoutParams(new LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT));
            r.addView(t);
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Hues.vivid(x.subject, Ui.DARK));
            dot.setBackground(g);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(10), dp(10));
            dl.rightMargin = dp(12);
            r.addView(dot, dl);
            TextView n = Ui.text(this, x.subject + (x.changed ? " · замена" : ""), 16, Ui.TEXT, isCur ? "sans-serif-medium" : null);
            n.setLayoutParams(Ui.weight(1));
            r.addView(n);
            if (!x.room.isEmpty()) {
                TextView c = Ui.room(this, x.room, x.subject, isCur, 14);
                r.addView(c);
            }
            LinearLayout.LayoutParams lp = Ui.mw(this, 4);
            list.addView(r, lp);
        }
        if (l.isEmpty()) list.addView(Ui.text(this, "Уроков нет. Можно выдохнуть", 15, Ui.MUTED, null));
    }

    private static String capital(String s) {
        s = s.toLowerCase();
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (chrono == null) return;
        lastTarget = -1;
        lastList = "";
        handler.post(tick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(tick);
        if (chrono != null) chrono.stop();
        // кнопка питания или ушли с экрана: в следующий раз блокировка без нашего окна
        if (!isFinishing()) finish();
    }
}
