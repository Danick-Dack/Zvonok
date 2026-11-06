package app.urok7b;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import org.json.JSONObject;

/**
 * QR-код класса: те же коды, что на сайте и плакате. Их готовит tools/build_site.py в assets/qr.json
 * (строка «размер:биты в hex»), поэтому кодировщика QR в приложении нет.
 */
final class Qr extends android.view.View {

    private final int size;
    private final boolean[] dark;
    private final Paint paint = new Paint();

    private Qr(Context c, String code) {
        super(c);
        String[] p = code.split(":");
        size = Integer.parseInt(p[0]);
        dark = new boolean[size * size];
        for (int i = 0; i < dark.length; i++) dark[i] = (Character.digit(p[1].charAt(i / 4), 16) >> (3 - i % 4) & 1) == 1;
    }

    /** QR-код ссылки на класс («» — на весь сайт); null, если кодов в APK нет. */
    static Qr of(Context c, String cls) {
        try {
            JSONObject all = new JSONObject(Ui.asset(c, "qr.json"));
            return new Qr(c, all.has(cls) ? all.getString(cls) : all.getString(""));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        // белое поле с отступом в 2 модуля: так код читается камерой и в тёмной теме
        float m = Math.min(getWidth(), getHeight()) / (float) (size + 4);
        paint.setColor(Color.WHITE);
        c.drawRoundRect(0, 0, m * (size + 4), m * (size + 4), m, m, paint);
        paint.setColor(Color.BLACK);
        for (int i = 0; i < dark.length; i++) {
            if (!dark[i]) continue;
            float x = (i % size + 2) * m, y = (i / size + 2) * m;
            c.drawRect(x, y, x + m + 0.5f, y + m + 0.5f, paint);
        }
    }
}
