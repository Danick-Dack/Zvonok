package app.urok7b;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * Пасхалка, как на сайте: десять нажатий на «Звонок» открывают змейку, скоротать перемену.
 * Цвета змейки как у предметов, рекорд хранится на телефоне. Игра идёт, только пока окно открыто.
 */
final class Snake extends View {

    private static final int N = 15;
    private static final int[] COLORS = {0xFF007AFF, 0xFFAF52DE, 0xFFFFCC00, 0xFFFF3B30, 0xFF32ADE6, 0xFF00C7BE, 0xFF34C759, 0xFFFF9500};
    private final ArrayDeque<int[]> body = new ArrayDeque<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final TextView score;
    private int[] dir = {1, 0}, want = {1, 0}, food;
    private int points;
    private boolean over, running;
    private float sx, sy;
    private final Runnable step = new Runnable() {
        @Override public void run() {
            move();
            if (running && !over) handler.postDelayed(this, 140);
        }
    };

    private Snake(Context c, TextView score) {
        super(c);
        this.score = score;
        setContentDescription("Змейка. Управление кнопками со стрелками под полем");
        start();
    }

    static void show(Activity a) {
        LinearLayout box = Ui.vertical(a);
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 8), Ui.dp(a, 20), Ui.dp(a, 4));
        TextView score = Ui.text(a, "", 14, Ui.SOFT, null);
        score.setGravity(Gravity.CENTER);
        score.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Snake game = new Snake(a, score);
        android.util.DisplayMetrics m = a.getResources().getDisplayMetrics();
        // экран набок: поле по высоте, чтобы под ним поместились счёт и кнопки
        int side = Math.min(m.heightPixels > m.widthPixels ? m.widthPixels - Ui.dp(a, 88) : m.heightPixels - Ui.dp(a, 200), Ui.dp(a, 360));
        box.addView(game, new LinearLayout.LayoutParams(side, side));
        box.addView(score, Ui.mw(a, 10));
        LinearLayout pad = Ui.horizontal(a);
        pad.setGravity(Gravity.CENTER);
        String[][] keys = {{"←", "-1", "0", "Влево"}, {"↑", "0", "-1", "Вверх"}, {"↓", "0", "1", "Вниз"}, {"→", "1", "0", "Вправо"}};
        for (String[] k : keys) {
            TextView b = Ui.text(a, k[0], 22, Ui.TEXT, "sans-serif-medium");
            b.setGravity(Gravity.CENTER);
            b.setBackground(Ui.round(a, Ui.RAISED, 14, Ui.LINE));
            b.setContentDescription(k[3]);
            b.setOnClickListener(v -> { Ui.tick(v); game.turn(Integer.parseInt(k[1]), Integer.parseInt(k[2])); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 56), Ui.dp(a, 48));
            lp.setMargins(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
            pad.addView(b, lp);
        }
        box.addView(pad, Ui.mw(a, 10));
        AlertDialog d = new AlertDialog.Builder(a, Ui.dialogTheme()).setTitle("Змейка на перемене").setView(box)
                .setNegativeButton("Закрыть", null).create();
        d.setOnDismissListener(x -> game.stop());
        d.show();
    }

    private void start() {
        body.clear();
        for (int x = 7; x >= 5; x--) body.addLast(new int[]{x, 7});
        dir = new int[]{1, 0};
        want = dir;
        points = 0;
        over = false;
        place();
        running = true;
        handler.removeCallbacks(step);
        handler.postDelayed(step, 140);
        draw();
    }

    private void stop() {
        running = false;
        handler.removeCallbacks(step);
    }

    private int best() {
        return Updater.prefs(getContext()).getInt("snake", 0);
    }

    private void place() {
        do food = new int[]{random.nextInt(N), random.nextInt(N)}; while (hits(food));
    }

    private boolean hits(int[] p) {
        for (int[] b : body) if (b[0] == p[0] && b[1] == p[1]) return true;
        return false;
    }

    void turn(int x, int y) {
        if (x != -dir[0] || y != -dir[1]) want = new int[]{x, y};
    }

    private void move() {
        dir = want;
        int[] head = {(body.peekFirst()[0] + dir[0] + N) % N, (body.peekFirst()[1] + dir[1] + N) % N};
        if (hits(head)) {
            over = true;
            if (points > best()) Updater.prefs(getContext()).edit().putInt("snake", points).apply();
            draw();
            return;
        }
        body.addFirst(head);
        if (head[0] == food[0] && head[1] == food[1]) {
            points++;
            place();
        } else {
            body.removeLast();
        }
        draw();
    }

    private void draw() {
        score.setText(over ? "Звонок! Перемена кончилась. Счёт " + points + (points > 0 && points >= best() ? ", это рекорд" : ", рекорд " + best())
                + ". Нажми на поле, чтобы сыграть ещё" : "Счёт " + points + " · рекорд " + best());
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float z = getWidth() / (float) N, r = z / 4;
        paint.setColor(Ui.RAISED);
        c.drawRoundRect(0, 0, getWidth(), getHeight(), z / 2, z / 2, paint);
        int i = 0;
        for (int[] b : body) {
            paint.setColor(COLORS[i++ / 3 % COLORS.length]);
            c.drawRoundRect(b[0] * z + 1, b[1] * z + 1, (b[0] + 1) * z - 1, (b[1] + 1) * z - 1, r, r, paint);
        }
        paint.setColor(Ui.ACCENT);
        c.drawCircle(food[0] * z + z / 2, food[1] * z + z / 2, z / 2 - 3, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            sx = e.getX();
            sy = e.getY();
            getParent().requestDisallowInterceptTouchEvent(true);
        } else if (e.getAction() == MotionEvent.ACTION_UP) {
            float dx = e.getX() - sx, dy = e.getY() - sy;
            if (Math.max(Math.abs(dx), Math.abs(dy)) > Ui.dp(getContext(), 20)) {
                if (Math.abs(dx) > Math.abs(dy)) turn((int) Math.signum(dx), 0);
                else turn(0, (int) Math.signum(dy));
            } else if (over) {
                start();
            }
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
