package app.urok7b;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Оформление виджетов и уведомления: готовые темы, акцент, прозрачность, углы и что показывать.
 * Предпросмотр — это настоящие виджеты и уведомление, собранные тем же кодом.
 * Сам экран приложения не меняется.
 */
public class StyleActivity extends Activity {

    private static final int PICK_PHOTO = 7;
    private Theme t;
    private LinearLayout preview, presets, accents, corners, photoCard;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    // Настоящие виджеты и уведомление обновляются не на каждое касание, а когда рука остановилась:
    // раньше каждое движение пересобирало их все, и экран дёргался
    private final Runnable apply = () -> Updater.update(this);
    private long drawnAt;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        t = Theme.load(this);
        LinearLayout root = Ui.screen(this, "Оформление",
                "Виджеты и уведомление. Экран приложения остаётся прежним.");

        preview = Ui.vertical(this);
        preview.setLayoutParams(Ui.mw(this, 18));
        root.addView(preview);

        root.addView(Ui.label(this, "Тема"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        presets = Ui.horizontal(this);
        hs.addView(presets);
        root.addView(hs);

        root.addView(Ui.label(this, "Акцент: таймер, прогресс, кабинет"));
        accents = Ui.horizontal(this);
        root.addView(accents);

        root.addView(Ui.label(this, "Фон виджетов"));
        LinearLayout bgCard = Ui.card(this);
        TextView opLabel = Ui.text(this, "", 14, Ui.SOFT, null);
        bgCard.addView(opLabel);
        SeekBar op = new SeekBar(this);
        op.setMax(70);
        op.setProgress(t.opacity - 30);
        opLabel.setText("Непрозрачность " + t.opacity + "%");
        op.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                opLabel.setText("Непрозрачность " + (p + 30) + "%");
                // предпросмотр идёт за пальцем, но не чаще десяти раз в секунду
                if (!user || System.currentTimeMillis() - drawnAt < 100) return;
                drawnAt = System.currentTimeMillis();
                t.opacity = p + 30;
                renderPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                t.opacity = s.getProgress() + 30;
                changed();
            }
        });
        op.setLayoutParams(Ui.mw(this, 6));
        bgCard.addView(op);
        TextView cl = Ui.text(this, "Углы", 14, Ui.SOFT, null);
        cl.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 8));
        bgCard.addView(cl);
        corners = Ui.horizontal(this);
        bgCard.addView(corners);
        TextView opNote = Ui.text(this, "Прозрачность видна у виджетов на рабочем столе. Уведомление система всегда рисует непрозрачным.", 12, Ui.MUTED, null);
        opNote.setPadding(0, Ui.dp(this, 12), 0, 0);
        bgCard.addView(opNote);
        root.addView(bgCard);

        root.addView(Ui.label(this, "Свой фон: фото"));
        photoCard = Ui.card(this);
        root.addView(photoCard);

        root.addView(Ui.label(this, "Что показывать"));
        LinearLayout show = Ui.card(this);
        show.addView(Ui.switchRow(this, "Секунды в таймере",
                "Если выключить, таймер будет «24 мин», а батарея будет тратиться ещё меньше", t.seconds,
                (b, v) -> { t.seconds = v; changed(); }));
        show.addView(Ui.switchRow(this, "Полоска прогресса", null, t.progress,
                (b, v) -> { t.progress = v; changed(); }));
        show.addView(Ui.switchRow(this, "Следующий урок", null, t.next,
                (b, v) -> { t.next = v; changed(); }));
        show.addView(Ui.switchRow(this, "Точки уроков дня", null, t.dots,
                (b, v) -> { t.dots = v; changed(); }));
        show.addView(Ui.switchRow(this, "Цвет урока", "Таймер и кабинет в цвете предмета: алгебра синяя, физика фиолетовая", t.subjectColor,
                (b, v) -> { t.subjectColor = v; changed(); }));
        root.addView(show);

        root.addView(Ui.label(this, "Код темы"));
        root.addView(Ui.button(this, "Скопировать код темы", false, v -> {
            ClipboardManager cm = getSystemService(ClipboardManager.class);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Тема", t.encode()));
            Toast.makeText(this, "Скопировано: " + t.encode(), Toast.LENGTH_SHORT).show();
        }));
        root.addView(Ui.button(this, "Поделиться темой", false, v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Моя тема для «Звонка» (Оформление → Вставить код):\n" + t.encode());
            startActivity(Intent.createChooser(i, "Поделиться темой"));
        }));
        root.addView(Ui.button(this, "Вставить код темы (с сайта или от друга)", false, v -> importCode()));
        root.addView(Ui.button(this, "Сбросить", false, v -> {
            t = new Theme();
            changed();
            recreate();
        }));

        refreshPickers();
        renderPhoto();
        renderPreview();
    }

    private void changed() {
        t.save(this);
        ui.removeCallbacks(apply);
        ui.postDelayed(apply, 350);
        refreshPickers();
        renderPreview();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(apply);
        Updater.update(this); // уходим с экрана: всё применено сразу
    }

    /** Фото из галереи через системное окно выбора: приложению не нужен доступ ко всем фото. */
    private void renderPhoto() {
        photoCard.removeAllViews();
        boolean has = Backdrop.file(this).exists();
        photoCard.addView(Ui.linkRow(this, has ? "Выбрать другое фото" : "Выбрать фото", "Останется только на этом телефоне", null, v -> {
            try {
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), PICK_PHOTO);
            } catch (Exception e) {
                Toast.makeText(this, "Не нашлось приложения, чтобы выбрать фото", Toast.LENGTH_SHORT).show();
            }
        }));
        if (!has) return;
        String[][] where = {{Backdrop.KEY_WIDGET, "В виджетах"}, {Backdrop.KEY_NOTIF, "В уведомлении"}, {Backdrop.KEY_APP, "В приложении"}};
        for (String[] w : where) {
            photoCard.addView(Ui.switchRow(this, w[1], null, Updater.prefs(this).getBoolean(w[0], false), (b, on) -> {
                Updater.prefs(this).edit().putBoolean(w[0], on).apply();
                Ui.version++; // главный экран пересоберётся с новым фоном
                changed();
            }));
        }
        photoCard.addView(Ui.linkRow(this, "Убрать фото", null, null, v -> {
            Backdrop.clear(this);
            Ui.version++;
            changed();
            renderPhoto();
        }));
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_PHOTO || result != RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri uri = data.getData();
        new Thread(() -> {
            boolean ok = Backdrop.save(getApplicationContext(), uri);
            ui.post(() -> {
                if (isFinishing()) return;
                if (!ok) {
                    Toast.makeText(this, "Не получилось открыть эту картинку", Toast.LENGTH_SHORT).show();
                    return;
                }
                android.content.SharedPreferences p = Updater.prefs(this);
                if (!p.contains(Backdrop.KEY_WIDGET)) p.edit().putBoolean(Backdrop.KEY_WIDGET, true).apply(); // первое фото сразу видно в виджетах
                Ui.version++;
                changed();
                renderPhoto();
            });
        }).start();
    }

    private void refreshPickers() {
        presets.removeAllViews();
        for (String[] p : Theme.PRESETS) {
            int bg = Theme.color(p[1], 0), ac = Theme.color(p[2], 0);
            boolean sel = (t.bg & 0xFFFFFF) == (bg & 0xFFFFFF) && (t.accent & 0xFFFFFF) == (ac & 0xFFFFFF);
            LinearLayout item = Ui.vertical(this);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, 0, Ui.dp(this, 14), 0);
            View sw = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(bg);
            g.setStroke(Ui.dp(this, sel ? 3 : 1), sel ? Ui.TEXT : Ui.FAINT);
            sw.setBackground(g);
            sw.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
            TextView dot = Ui.text(this, "●", 12, ac, null);
            FrameLayout f = new FrameLayout(this);
            f.addView(sw);
            dot.setLayoutParams(new FrameLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));
            dot.setGravity(Gravity.CENTER);
            f.addView(dot);
            item.addView(f);
            TextView name = Ui.text(this, p[0], 11, sel ? Ui.TEXT : Ui.MUTED, null);
            name.setPadding(0, Ui.dp(this, 6), 0, 0);
            item.addView(name);
            item.setOnClickListener(v -> {
                t.bg = bg;
                t.accent = ac;
                changed();
            });
            presets.addView(item);
        }

        accents.removeAllViews();
        for (String a : Theme.ACCENTS) {
            int c = Theme.color(a, 0);
            boolean sel = (t.accent & 0xFFFFFF) == (c & 0xFFFFFF);
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            g.setStroke(Ui.dp(this, sel ? 3 : 1), sel ? Ui.TEXT : Ui.FAINT);
            v.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 32), 1f);
            lp.rightMargin = Ui.dp(this, 6);
            v.setLayoutParams(lp);
            v.setOnClickListener(x -> {
                t.accent = c;
                changed();
            });
            accents.addView(v);
        }

        corners.removeAllViews();
        int[] r = {12, 26, 36};
        String[] n = {"Острые", "Обычные", "Круглые"};
        for (int i = 0; i < r.length; i++) {
            int val = r[i];
            corners.addView(Ui.pill(this, n[i], Math.abs(t.radius - val) < 6, v -> {
                t.radius = val;
                changed();
            }));
        }
    }

    /** Пример: идёт третий урок, чтобы было видно всё сразу. */
    private Updater.Display demo() {
        Updater.Display d = new Updater.Display();
        d.kind = Schedule.Kind.LESSON;
        d.live = true;
        d.timer = true;
        d.target = System.currentTimeMillis() + 24 * 60_000L + 16_000L;
        d.label = "УРОК 3 ИЗ 7";
        d.title = "Физика";
        d.room = "48/49";
        d.sideHead = "ЗВОНОК";
        d.sideValue = d.sideShort = "10:30";
        d.statHead = "ДОМОЙ В";
        d.statValue = "14:30";
        d.progress = 46;
        d.dotTotal = 7;
        d.dotDone = 2;
        d.dotCurrent = 3;
        d.nextHead = "ДАЛЬШЕ · 10:50";
        d.nextSubject = "Программирование";
        d.nextRoom = "56/57";
        d.compactTitle = "Физика · каб. 48/49";
        d.compactText = "Звонок 10:30 → Программирование · 56/57";
        d.miniLabel = "УРОК 3/7";
        d.miniRoom = "56/57";
        d.miniSub = "Программирование";
        return d;
    }

    private void renderPreview() {
        preview.removeAllViews();
        Updater.Display d = demo();
        Theme shown = t.forDisplay(d); // как в настоящих виджетах: при «цвете урока» акцент в цвет предмета

        // обои-подложка, чтобы было видно прозрачность
        LinearLayout wall = Ui.vertical(this);
        GradientDrawable wg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#3B3B58"), Color.parseColor("#1E3A3A"), Color.parseColor("#4A2E2E")});
        wg.setCornerRadius(Ui.dp(this, 24));
        wall.setBackground(wg);
        wall.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));

        FrameLayout notif = new FrameLayout(this);
        notif.setBackground(Ui.round(this, t.bg | 0xFF000000, 20, 0)); // уведомление непрозрачное и в жизни
        notif.setClipToOutline(true);
        notif.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        add(notif, Updater.notifBig(this, d, shown), ViewGroup.LayoutParams.WRAP_CONTENT);
        wall.addView(notif);

        LinearLayout widgets = Ui.horizontal(this);
        widgets.setGravity(Gravity.TOP);
        widgets.setLayoutParams(Ui.mw(this, 14));
        FrameLayout w2 = new FrameLayout(this);
        w2.setLayoutParams(new LinearLayout.LayoutParams(0, Ui.dp(this, 160), 1f));
        add(w2, LessonWidget.build(this, d, shown), ViewGroup.LayoutParams.MATCH_PARENT);
        FrameLayout w1 = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 76), 1f);
        lp.leftMargin = Ui.dp(this, 12);
        w1.setLayoutParams(lp);
        add(w1, MiniWidget.build(this, d, shown), ViewGroup.LayoutParams.MATCH_PARENT);
        widgets.addView(w2);
        widgets.addView(w1);
        wall.addView(widgets);
        FrameLayout rooms = new FrameLayout(this);
        rooms.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 150)));
        ((LinearLayout.LayoutParams) rooms.getLayoutParams()).topMargin = Ui.dp(this, 12);
        add(rooms, RoomsWidget.build(this, java.time.ZonedDateTime.now(), shown), ViewGroup.LayoutParams.MATCH_PARENT);
        wall.addView(rooms);
        preview.addView(wall);

        TextView note = Ui.text(this, "Так будут выглядеть уведомление и виджеты: 2×2, 2×1 и «Кабинеты» (в нём кабинеты на сегодня).", 12, Ui.MUTED, null);
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, 0);
        preview.addView(note);
    }

    private void add(FrameLayout host, RemoteViews rv, int height) {
        try {
            View v = rv.apply(this, host);
            v.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
            host.addView(v);
        } catch (RuntimeException e) {
            host.addView(Ui.text(this, "Предпросмотр недоступен", 12, Ui.MUTED, null));
        }
    }

    private void importCode() {
        EditText in = Ui.field(this, "", "ZV1;bg=000000;ac=FFFFFF...");
        new AlertDialog.Builder(this, Ui.dialogTheme())
                .setTitle("Код темы")
                .setView(in)
                .setPositiveButton("Применить", (d, w) -> {
                    String s = in.getText().toString();
                    int p = s.indexOf("ZV1");
                    Theme n = p < 0 ? null : Theme.decode(s.substring(p).split("\\s")[0]);
                    if (n == null) {
                        Toast.makeText(this, "Не похоже на код темы", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    t = n;
                    changed();
                    recreate();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }
}
