package app.urok7b;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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
        notif.addView(flag("Вибрация перед уроком", "Тихо вибрирует за 2 минуты до звонка на урок", Updater.KEY_BUZZ, false));
        notif.addView(flag("Каникулы", "Всё на паузе: ни уведомления, ни будильников. Каникулы из расписания приложение знает и само", Updater.KEY_VACATION, false));

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
        upd.addView(flag("Проверять автоматически", "Новая версия раз в сутки, замены в расписании раз в 3 часа, когда приложение открыто. Нужен интернет", Updates.KEY_AUTO, false));

        LinearLayout checks = section("ПРОВЕРКА ТЕЛЕФОНА");
        for (Perms.Item i : Perms.list(this))
            checks.addView(check(i.title, i.why, i.ok, () -> { Perms.markSeen(this, i); i.fix.run(); }));

        LinearLayout share = section("ПОДЕЛИТЬСЯ");
        share.addView(Ui.linkRow(this, "Отправить приложение другу", "Скинуть APK по Bluetooth, Quick Share или в Telegram, можно без интернета",
                null, v -> {
                    Intent i = new Intent(Intent.ACTION_SEND).setType(ApkProvider.MIME)
                            .putExtra(Intent.EXTRA_STREAM, ApkProvider.URI)
                            .putExtra(Intent.EXTRA_TEXT, "Это «Звонок», расписание лицея №165. Сайт: " + SITE)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    i.setClipData(ClipData.newRawUri("Звонок", ApkProvider.URI));
                    open(Intent.createChooser(i, "Отправить приложение"));
                }));
        share.addView(Ui.linkRow(this, "Ссылка на сайт", "Все классы, установка для Android и iPhone", null,
                v -> open(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
                        "Смотри, это «Звонок». Там расписание всех классов лицея №165 и таймер до звонка: " + SITE), "Поделиться"))));

        TextView foot = Ui.text(this, "Про приватность: в интернет приложение выходит, только если ты разрешил, и только чтобы узнать "
                + "про новую версию и замены в расписании. Рекламы, аккаунтов и аналитики нет, твои данные остаются на телефоне.\n\n"
                + "Про батарею: пока экран выключен, приложение ничего не делает и телефон не будит.", 12, Ui.MUTED, null);
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
            Toast.makeText(this, "Когда файл скачается, открой его и нажми «Обновить»", Toast.LENGTH_LONG).show();
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
