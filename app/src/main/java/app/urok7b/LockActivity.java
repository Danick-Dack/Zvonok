package app.urok7b;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.time.ZonedDateTime;

/**
 * Большой таймер поверх экрана блокировки. Открывается нажатием на уведомление, когда телефон заблокирован;
 * на разблокированном сразу передаёт управление главному экрану.
 * Экран не включает, блокировку не снимает. Обновляется только пока виден: без фоновых таймеров и будильников.
 * Закрывается тапом, «Назад» или кнопкой питания.
 */
public class LockActivity extends Activity {

    private TextView label, big, title, room, nextH, nextS, nextR, foot;
    private Chronometer chrono;
    private ProgressBar bar;
    private long lastTarget = -1;

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
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(28), dp(40), dp(28), dp(40));
        root.setOnClickListener(v -> finish());

        label = Ui.text(this, "", 12, Ui.SOFT, "sans-serif-medium");
        label.setLetterSpacing(0.16f);
        root.addView(label);

        chrono = new Chronometer(this);
        chrono.setTextColor(Ui.TEXT);
        chrono.setTextSize(96);
        chrono.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        chrono.setFontFeatureSettings("tnum");
        chrono.setIncludeFontPadding(false);
        chrono.setCountDown(true);
        chrono.setPadding(0, dp(10), 0, 0);
        root.addView(chrono);
        big = Ui.text(this, "", 96, Ui.TEXT, "sans-serif-thin");
        big.setIncludeFontPadding(false);
        big.setPadding(0, dp(10), 0, 0);
        root.addView(big);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressDrawable(getDrawable(R.drawable.progress_mono));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        bp.topMargin = dp(18);
        root.addView(bar, bp);

        title = Ui.text(this, "", 34, Ui.TEXT, "sans-serif-medium");
        title.setPadding(0, dp(26), 0, 0);
        root.addView(title);
        room = Ui.text(this, "", 44, Ui.BG, "sans-serif-medium");
        room.setBackground(Ui.round(this, Ui.TEXT, 18, 0));
        room.setPadding(dp(18), dp(4), dp(18), dp(6));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(14);
        root.addView(room, rp);

        LinearLayout next = Ui.vertical(this);
        next.setPadding(0, dp(40), 0, 0);
        nextH = Ui.text(this, "", 11, Ui.MUTED, "sans-serif-medium");
        nextH.setLetterSpacing(0.16f);
        next.addView(nextH);
        LinearLayout row = Ui.horizontal(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);
        nextS = Ui.text(this, "", 22, Ui.SOFT, null);
        nextS.setLayoutParams(Ui.weight(1));
        row.addView(nextS);
        nextR = Ui.text(this, "", 22, Ui.TEXT, "sans-serif-medium");
        nextR.setBackground(Ui.round(this, Ui.RAISED, 14, Ui.LINE));
        nextR.setPadding(dp(12), dp(4), dp(12), dp(4));
        row.addView(nextR);
        next.addView(row);
        root.addView(next);

        foot = Ui.text(this, "", 13, Ui.MUTED, null);
        foot.setPadding(0, dp(28), 0, 0);
        root.addView(foot);

        TextView hint = Ui.text(this, "Нажми, чтобы закрыть", 11, Ui.FAINT, null);
        hint.setPadding(0, dp(36), 0, 0);
        root.addView(hint);

        setContentView(root);
    }

    /** @return через сколько обновить снова (до звонка или до следующей минуты). */
    private long refresh() {
        Schedule.load(this);
        ZonedDateTime now = ZonedDateTime.now();
        long nowMs = System.currentTimeMillis();
        Schedule.Status s = Schedule.compute(now);
        Updater.Display d = Updater.describe(s, nowMs);

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
        bar.setVisibility(d.progress >= 0 ? View.VISIBLE : View.GONE);
        if (d.progress >= 0) bar.setProgress(d.progress);

        title.setText(d.title);
        room.setText(d.room);
        room.setVisibility(d.room.isEmpty() ? View.GONE : View.VISIBLE);
        nextH.setText(d.nextHead);
        nextS.setText(d.nextSubject);
        nextR.setText(d.nextRoom);
        nextR.setVisibility(d.nextRoom.isEmpty() ? View.GONE : View.VISIBLE);
        ((View) nextH.getParent()).setVisibility(d.nextSubject.isEmpty() ? View.GONE : View.VISIBLE);
        foot.setText(d.foot);
        foot.setVisibility(d.foot.isEmpty() ? View.GONE : View.VISIBLE);

        long toMinute = 60_000 - nowMs % 60_000 + 300;
        long toTarget = d.timer ? d.target - nowMs + 300 : Long.MAX_VALUE;
        return Math.max(1_000, Math.min(toMinute, toTarget));
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (chrono == null) return;
        lastTarget = -1;
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
