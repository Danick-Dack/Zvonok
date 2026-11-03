package app.urok7b;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Кольцо-таймер: тёмная дорожка с рисками, белая дуга прогресса и светящаяся точка на её конце. */
final class RingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float stroke;
    private float value = -1; // 0..1, -1 — без прогресса
    private ValueAnimator anim;

    RingView(Context c) {
        super(c);
        stroke = Ui.dp(c, 12);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(Color.parseColor("#18181B"));
        tick.setStrokeWidth(Ui.dp(c, 1));
        tick.setColor(Color.parseColor("#2A2A2E"));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(Color.WHITE);
        knob.setColor(Color.WHITE);
        knob.setShadowLayer(Ui.dp(c, 8), 0, 0, 0x99FFFFFF); // свечение (на старых Android может не рисоваться)
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
        // 60 рисок внутри дорожки, каждая пятая длиннее
        float cx = box.centerX(), cy = box.centerY(), rIn = box.width() / 2 - stroke / 2 - Ui.dp(getContext(), 3);
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6 - 90);
            float len = Ui.dp(getContext(), i % 5 == 0 ? 6 : 3);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + rIn * cos, cy + rIn * sin, cx + (rIn - len) * cos, cy + (rIn - len) * sin, tick);
        }
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
