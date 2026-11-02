package app.urok7b;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Настройки: расписание, уведомление, обновления, проверка телефона, «поделиться». */
public class SettingsActivity extends Activity {

    static final String SITE = "https://danick-dack.github.io/Zvonok/";
    static final String APK_URL = "https://github.com/Danick-Dack/Zvonok/releases/latest/download/Zvonok-165.apk";

    private LinearLayout root;

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        root = Ui.screen(this, "Настройки", Schedule.school() + " · " + Schedule.term());

        LinearLayout sched = section("РАСПИСАНИЕ");
        sched.addView(Ui.linkRow(this, "Класс", Schedule.isCustom() ? "Сейчас включено своё расписание" : null,
                Updater.prefs(this).getString(Schedule.KEY_CLASS, Schedule.DEFAULT_CLASS), v -> Ui.pickClass(this, this::build)));
        sched.addView(Ui.linkRow(this, "Своё расписание", "Любые предметы, кабинеты и звонки",
                Schedule.isCustom() ? "вкл" : null, v -> startActivity(new Intent(this, EditorActivity.class))));
        sched.addView(Ui.linkRow(this, "Оформление", "Цвета виджетов и уведомления", null,
                v -> startActivity(new Intent(this, StyleActivity.class))));

        LinearLayout notif = section("УВЕДОМЛЕНИЕ");
        notif.addView(flag("Уведомление на экране блокировки", "Чёрное, не смахивается", Updater.KEY_NOTIF, true));
        notif.addView(flag("Только в учебное время", "После уроков и в выходные уведомления нет", Updater.KEY_SCHOOL_ONLY, false));
        notif.addView(Ui.switchRow(this, "Экономный таймер", "Без секунд: «24 мин» вместо 24:16",
                !Theme.load(this).seconds, (b, on) -> {
                    Theme t = Theme.load(this);
                    t.seconds = !on;
                    t.save(this);
                    Updater.update(this);
                }));
        notif.addView(flag("Каникулы", "Всё на паузе: ни уведомления, ни будильников", Updater.KEY_VACATION, false));

        LinearLayout upd = section("ОБНОВЛЕНИЯ");
        boolean avail = Updates.available(this);
        upd.addView(Ui.linkRow(this, avail ? "Доступна версия " + Updates.latestText(this) : "Проверить обновления",
                "Установлена " + Updates.currentName(this) + " (сборка " + Updates.currentBuild(this) + ")",
                avail ? "Скачать" : null, v -> {
                    if (Updates.available(this)) download();
                    else {
                        Toast.makeText(this, "Проверяю…", Toast.LENGTH_SHORT).show();
                        Updates.check(this, ok -> {
                            Toast.makeText(this, !ok ? "Нет связи с GitHub, попробуй позже"
                                    : Updates.available(this) ? "Есть обновление!" : "У тебя последняя версия",
                                    Toast.LENGTH_SHORT).show();
                            build();
                        });
                    }
                }));
        upd.addView(flag("Проверять автоматически", "Раз в сутки, только когда приложение открыто", Updates.KEY_AUTO, true));

        LinearLayout checks = section("ПРОВЕРКА ТЕЛЕФОНА");
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean notifOk = nm != null && nm.areNotificationsEnabled();
        if (notifOk) {
            NotificationChannel ch = nm.getNotificationChannel(Updater.CHANNEL);
            notifOk = ch == null || ch.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        checks.addView(check("Показ уведомления", "Разрешение и экран блокировки", notifOk, () -> {
            if (!open(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID, Updater.CHANNEL)))
                open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }));
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = getSystemService(AlarmManager.class);
            checks.addView(check("Точное время звонков", "Таймер переключается ровно по звонку",
                    am == null || am.canScheduleExactAlarms(),
                    () -> open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg()))));
        }
        PowerManager pm = getSystemService(PowerManager.class);
        checks.addView(check("Без ограничений батареи", "Чтобы система не закрывала уведомление. Расход не растёт",
                pm != null && pm.isIgnoringBatteryOptimizations(getPackageName()), () -> {
                    if (!open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg())))
                        open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                }));
        checks.addView(check("Автозапуск (Xiaomi)", "Чтобы уведомление вернулось после перезагрузки", null, () -> {
            if (!open(new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"))))
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg()));
        }));

        LinearLayout share = section("ПОДЕЛИТЬСЯ");
        share.addView(Ui.linkRow(this, "Отправить приложение другу", "APK по Bluetooth, Quick Share, Telegram — без интернета",
                null, v -> {
                    Intent i = new Intent(Intent.ACTION_SEND).setType(ApkProvider.MIME)
                            .putExtra(Intent.EXTRA_STREAM, ApkProvider.URI)
                            .putExtra(Intent.EXTRA_TEXT, "«Звонок» — расписание лицея №165. Сайт: " + SITE)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    i.setClipData(ClipData.newRawUri("Звонок", ApkProvider.URI));
                    open(Intent.createChooser(i, "Отправить приложение"));
                }));
        share.addView(Ui.linkRow(this, "Ссылка на сайт", "Все классы, установка для Android и iPhone", null,
                v -> open(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                        "«Звонок» — расписание всех классов лицея №165 с таймером до звонка: " + SITE), "Поделиться"))));

        TextView foot = Ui.text(this, "Приватность: в сети приложение делает только одно — раз в сутки спрашивает GitHub, "
                + "вышла ли новая версия (можно выключить выше). Нет рекламы, аккаунтов и аналитики, данные не покидают телефон.\n\n"
                + "Батарея: пока экран выключен, приложение ничего не делает и телефон не будит.", 12, Ui.MUTED, null);
        foot.setLineSpacing(0, 1.2f);
        foot.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), Ui.dp(this, 4), 0);
        root.addView(foot);
    }

    private LinearLayout section(String title) {
        root.addView(Ui.caps(this, title));
        LinearLayout card = Ui.card(this);
        root.addView(card);
        return card;
    }

    private View flag(String title, String sub, String key, boolean def) {
        return Ui.switchRow(this, title, sub, Updater.prefs(this).getBoolean(key, def), (b, on) -> {
            Updater.prefs(this).edit().putBoolean(key, on).apply();
            Updater.update(this);
        });
    }

    /** ok: true — готово, false — нужно включить, null — проверить нельзя. */
    private View check(String title, String sub, Boolean ok, Runnable fix) {
        LinearLayout r = Ui.horizontal(this);
        r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        LinearLayout t = Ui.twoLines(this, title, sub);
        t.setLayoutParams(Ui.weight(1));
        r.addView(t);
        TextView st = Ui.text(this, ok == null ? "Открыть" : ok ? "✓ Готово" : "Включить", 12,
                ok == null ? Ui.TEXT : ok ? Ui.MUTED : Ui.BG, "sans-serif-medium");
        if (ok == null) st.setBackground(Ui.round(this, 0, 12, Color.parseColor("#3A3A3A")));
        else if (!ok) st.setBackground(Ui.round(this, Ui.TEXT, 12, 0));
        st.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        r.addView(st);
        r.setOnClickListener(v -> fix.run());
        return r;
    }

    private void download() {
        if (open(new Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL))))
            Toast.makeText(this, "Когда файл скачается — открой его и нажми «Обновить»", Toast.LENGTH_LONG).show();
    }

    private Uri pkg() {
        return Uri.parse("package:" + getPackageName());
    }

    private boolean open(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
