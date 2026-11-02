package app.urok7b;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Кольцо-таймер: тёмная дорожка, белая дуга прогресса и точка на её конце. */
final class RingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float stroke;
    private float value = -1; // 0..1, -1 — без прогресса
    private ValueAnimator anim;

    RingView(Context c) {
        super(c);
        stroke = Ui.dp(c, 9);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(Color.parseColor("#1A1A1C"));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(Color.WHITE);
        knob.setColor(Color.WHITE);
    }

    /** Плавно перейти к новому значению (0..1) или убрать дугу (-1). */
    void setProgress(float v) {
        if (anim != null) anim.cancel();
        if (v < 0 || value < 0 || Math.abs(v - value) > 0.5f) {
            value = v;
            invalidate();
            return;
        }
        anim = ValueAnimator.ofFloat(value, v).setDuration(700);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            value = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float pad = stroke / 2 + Ui.dp(getContext(), 4);
        box.set(pad, pad, getWidth() - pad, getHeight() - pad);
        c.drawOval(box, track);
        if (value < 0) return;
        float sweep = Math.max(0.5f, value * 360f);
        c.drawArc(box, -90, sweep, false, arc);
        double a = Math.toRadians(sweep - 90);
        float r = box.width() / 2;
        c.drawCircle(box.centerX() + (float) (r * Math.cos(a)), box.centerY() + (float) (r * Math.sin(a)),
                stroke * 0.85f, knob);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
