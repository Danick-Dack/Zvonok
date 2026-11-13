package app.urok7b;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Свой фон: фото из галереи под виджетами, уведомлением и экраном приложения. Фото один раз ужимается и лежит
 * в памяти приложения (разрешение на галерею не нужно: файл выбирает сам человек в системном окне).
 * Здесь же фон виджета картинкой, когда нужна прозрачность: она записана прямо в пикселях и не зависит от
 * того, как лаунчер обращается с прозрачностью картинок.
 * Обычный непрозрачный фон без фото остаётся ресурсом: он ничего не стоит при каждом обновлении.
 */
final class Backdrop {

    static final String KEY_WIDGET = "bg_widget", KEY_NOTIF = "bg_notif", KEY_APP = "bg_app";
    private static final int SIDE = 1080;  // длинная сторона сохранённого фото
    private static final int DIM = 0x8C;   // фото под слоем цвета темы (55 %), чтобы текст читался
    private static final java.util.Map<String, Bitmap> CACHE = new java.util.HashMap<>(); // по одной на размер, не больше восьми

    private Backdrop() {}

    static File file(Context c) {
        return new File(c.getFilesDir(), "bg.jpg");
    }

    static boolean on(Context c, String key) {
        return Updater.prefs(c).getBoolean(key, false) && file(c).exists();
    }

    /** Сохранить выбранное фото уменьшенным. @return false, если это не картинка. */
    static boolean save(Context c, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;
            o.inSampleSize = Math.max(1, Math.max(o.outWidth, o.outHeight) / SIDE);
            o.inJustDecodeBounds = false;
            Bitmap b;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, o);
            }
            if (b == null) return false;
            File tmp = new File(file(c).getPath() + ".part");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                b.compress(Bitmap.CompressFormat.JPEG, 85, out); // пересохранение: в файле только пиксели, без метаданных снимка
            }
            b.recycle();
            synchronized (Backdrop.class) { CACHE.clear(); }
            return tmp.renameTo(file(c)) || (file(c).delete() && tmp.renameTo(file(c)));
        } catch (Exception | OutOfMemoryError e) {
            return false;
        }
    }

    static void clear(Context c) {
        file(c).delete();
        synchronized (Backdrop.class) { CACHE.clear(); }
    }

    /** Фото, уменьшенное примерно до нужной стороны (null, если его нет или оно не читается). */
    private static Bitmap photo(Context c, int side) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file(c).getPath(), o);
            o.inSampleSize = Math.max(1, Math.min(o.outWidth, o.outHeight) / Math.max(1, side));
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(file(c).getPath(), o);
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * Картинка фона w×h: фото по центру под слоем цвета темы или сплошной цвет; углы скруглены, прозрачность
     * (alpha 0–255) записана в пикселях. Готовые картинки запоминаются: виджеты перерисовываются часто, фон редко.
     */
    static synchronized Bitmap make(Context c, int color, int w, int h, float radius, boolean withPhoto, int alpha) {
        String key = color + "|" + w + "|" + h + "|" + radius + "|" + alpha + "|" + (withPhoto ? file(c).lastModified() : 0);
        Bitmap ready = CACHE.get(key);
        if (ready != null) return ready;
        if (CACHE.size() >= 8) CACHE.clear();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF box = new RectF(0, 0, w, h);
        Bitmap src = withPhoto ? photo(c, Math.max(w, h)) : null;
        if (src != null) {
            float k = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
            Matrix m = new Matrix();
            m.setScale(k, k);
            m.postTranslate((w - src.getWidth() * k) / 2, (h - src.getHeight() * k) / 2);
            BitmapShader sh = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            sh.setLocalMatrix(m);
            p.setShader(sh);
            p.setAlpha(alpha);
            cv.drawRoundRect(box, radius, radius, p);
            p.setShader(null);
            p.setColor((color & 0x00FFFFFF) | (DIM * alpha / 255 << 24));
        } else {
            p.setColor((color & 0x00FFFFFF) | (alpha << 24));
        }
        cv.drawRoundRect(box, radius, radius, p);
        CACHE.put(key, out);
        return out;
    }

    /**
     * Фон виджета. Непрозрачный без фото: скруглённая форма из ресурсов, перекрашенная в цвет темы.
     * С прозрачностью или фото: готовая картинка по размеру виджета (ширина к высоте = aspect).
     */
    static void widget(Context c, RemoteViews rv, int id, Theme t, float aspect) {
        boolean withPhoto = on(c, KEY_WIDGET);
        if (!withPhoto && t.opacity >= 100) {
            rv.setImageViewResource(id, t.shapeRes());
            rv.setInt(id, "setColorFilter", t.bg | 0xFF000000);
            rv.setInt(id, "setImageAlpha", 255);
            return;
        }
        int h = 220, w = Math.round(h * aspect);
        float dp = h / 150f; // виджет в два ряда высотой около 150 dp
        rv.setInt(id, "setColorFilter", 0);
        rv.setInt(id, "setImageAlpha", 255);
        rv.setImageViewBitmap(id, make(c, t.bg, w, h, t.radius * dp, withPhoto, Math.round(t.opacity * 2.55f)));
    }

    /** Фото в уведомлении: карточка под содержимым. Без фото остаётся цвет темы (система красит уведомление сама). */
    static void notif(Context c, RemoteViews rv, int id, int rootId, Theme t, boolean big) {
        boolean withPhoto = on(c, KEY_NOTIF);
        rv.setViewVisibility(id, withPhoto ? View.VISIBLE : View.GONE);
        rv.setInt(rootId, "setBackgroundColor", withPhoto ? 0 : t.bg);
        if (withPhoto) rv.setImageViewBitmap(id, make(c, t.bg, 360, big ? 200 : 72, 22, true, 255));
    }

    /** Фон экрана приложения: фото под плотным слоем цвета экрана. null: обычный фон. */
    static Drawable app(Context c, int bg) {
        if (!on(c, KEY_APP)) return null;
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        // в треть экрана: под плотным слоем цвета резкость не нужна, а памяти в девять раз меньше
        BitmapDrawable d = new BitmapDrawable(c.getResources(), make(c, bg, Math.max(1, dm.widthPixels / 3), Math.max(1, dm.heightPixels / 3), 0, true, 255));
        return new LayerDrawable(new Drawable[]{d, new ColorDrawable((bg & 0x00FFFFFF) | 0x73000000)});
    }
}
