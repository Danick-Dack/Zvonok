package app.urok7b;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Вкладка «Ещё»: класс, оформление, уведомление, поделиться, календарь, проверка телефона, о проекте. */
final class MoreTab {

    /** «Звонку» столько дней: первая версия вышла 1 ноября 2026 (как на сайте). */
    static final LocalDate BORN = LocalDate.of(2026, 11, 1);
    private static final String[][] FAQ = {
            {"Это официальное приложение лицея?", "Нет. «Звонок» сделал ученик лицея для своих. Расписание взято из утверждённого "
                    + "расписания четверти. Если где-то ошибка, напиши нам, исправим."},
            {"Что «Звонок» знает обо мне?", "Ничего, кроме класса и настроек, и они лежат у тебя на телефоне. Ни аккаунтов, ни рекламы, "
                    + "ни аналитики. В интернет приложение ходит только за новой версией и заменами, если ты это разрешишь, "
                    + "и когда ты сам отправляешь письмо в «Написать нам» (через сервис Web3Forms)."},
            {"Сколько тратит батареи и места?", "Меньше мегабайта и около 1 % зарядки в день. Пока экран выключен, приложение спит, "
                    + "а таймер тикает силами самой системы. В каникулы оно отдыхает вместе с тобой."},
            {"Уведомление пропало или не обновляется", "Телефон закрыл приложение ради батареи. Открой «Проверка телефона» выше "
                    + "и включи всё, что там предлагают."},
            {"Как обновить приложение?", "Приложение само скажет, что вышла новая версия: скачай и установи поверх. "
                    + "Расписание и замены обновляются сами, если разрешить проверку обновлений."},
            {"Работает без интернета?", "Да. Расписание внутри, а замены подтянутся, когда появится сеть."},
    };

    final ScrollView view;
    private final MainActivity a;
    private final LinearLayout root;

    MoreTab(MainActivity a) {
        this.a = a;
        view = new ScrollView(a);
        view.setVerticalScrollBarEnabled(false);
        root = Ui.vertical(a);
        root.setPadding(a.dp(18), a.dp(20), a.dp(18), a.dp(36));
        view.addView(root);
        build();
    }

    /** Пересобрать (после возврата из настроек телефона, смены класса и т. п.), не теряя прокрутку. */
    void build() {
        int y = view.getScrollY();
        root.removeAllViews();
        root.addView(Ui.heading(Ui.text(a, "Ещё", 28, Ui.TEXT, "sans-serif-medium")));
        root.addView(Ui.text(a, Schedule.school() + " · " + Schedule.term(), 14, Ui.MUTED, null));

        LinearLayout cls = section("КЛАСС И РАСПИСАНИЕ");
        cls.addView(Ui.linkRow(a, "Класс", Schedule.isCustom() ? "Сейчас включено своё расписание" : null,
                Updater.prefs(a).getString(Schedule.KEY_CLASS, Schedule.DEFAULT_CLASS), v -> Ui.pickClass(a, () -> { build(); a.rebuild(); })));
        cls.addView(Ui.linkRow(a, "Своё расписание", "Любые предметы, кабинеты и звонки", Schedule.isCustom() ? "вкл" : null,
                v -> a.startActivity(new Intent(a, EditorActivity.class))));

        LinearLayout look = section("ОФОРМЛЕНИЕ");
        look.addView(caption("Тема"));
        String[] keys = {"auto", "light", "dark"};
        int cur = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(Ui.scheme(a))) cur = i;
        look.addView(Ui.segments(a, new String[]{"Как в системе", "Светлая", "Тёмная"}, cur,
                k -> { Updater.prefs(a).edit().putString(Ui.KEY_SCHEME, keys[k]).apply(); a.recreate(); }), Ui.mw(a, 8));
        look.addView(caption("Главный цвет"));
        Ui.Flow colors = new Ui.Flow(a, 10);
        for (String[] acc : Ui.ACCENTS) {
            boolean on = acc[0].equals(Ui.accentKey(a));
            View dot = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFF000000 | Integer.parseInt(acc[Ui.DARK ? 3 : 2], 16));
            g.setStroke(a.dp(on ? 3 : 1), on ? Ui.TEXT : Ui.LINE);
            dot.setBackground(g);
            dot.setSelected(on);
            dot.setContentDescription("Цвет: " + acc[1] + (on ? ", выбран" : ""));
            dot.setOnClickListener(v -> { Ui.tick(v); Updater.prefs(a).edit().putString(Ui.KEY_ACCENT, acc[0]).apply(); a.recreate(); });
            colors.addView(dot, new android.view.ViewGroup.LayoutParams(a.dp(44), a.dp(44)));
        }
        look.addView(colors, Ui.mw(a, 8));
        look.addView(Ui.switchRow(a, "Цвет предметов", "Алгебра синяя, физика фиолетовая, химия бирюзовая", !Ui.PLAIN,
                (b, on) -> { Updater.prefs(a).edit().putBoolean(Ui.KEY_PLAIN, !on).apply(); a.recreate(); }));
        look.addView(Ui.linkRow(a, "Виджеты и уведомление", "Цвета, прозрачность, цвет урока", null,
                v -> a.startActivity(new Intent(a, StyleActivity.class))));

        LinearLayout notif = section("УВЕДОМЛЕНИЕ");
        notif.addView(flag("Уведомление на экране блокировки", "С таймером, не смахивается", Updater.KEY_NOTIF, true));
        if (Build.VERSION.SDK_INT >= 36) {
            notif.addView(flag("Таймер в строке состояния", "Android 16: урок и минуты до звонка видны вверху экрана и на экране блокировки",
                    Updater.KEY_LIVE, true));
        }
        notif.addView(flag("Только в учебное время", "После уроков и в выходные уведомления нет", Updater.KEY_SCHOOL_ONLY, false));
        notif.addView(Ui.switchRow(a, "Экономный таймер", "Без секунд: «24 мин» вместо 24:16", !Theme.load(a).seconds, (b, on) -> {
            Theme t = Theme.load(a);
            t.seconds = !on;
            t.save(a);
            Updater.update(a);
        }));
        notif.addView(flag("Вибрация перед уроком", "Тихо вибрирует за 2 минуты до звонка на урок", Updater.KEY_BUZZ, false));

        shareSection();

        LinearLayout cal = section("КАЛЕНДАРЬ");
        if (Schedule.isCustom()) {
            cal.addView(note("Своё расписание можно добавить в календарь на сайте: «Конструктор» → «Скачать для календаря»."));
        } else {
            String slug = Schedule.title().replace("А", "A").replace("Б", "B").replace("В", "V").replace("Г", "G");
            cal.addView(Ui.linkRow(a, "Уроки " + Schedule.title() + " в календаре", "Каникулы и замены учтены, календарь обновляется сам", null,
                    v -> open(Uri.parse("https://calendar.google.com/calendar/render?cid=" + Uri.encode("webcal://danick-dack.github.io/Zvonok/cal/" + slug + ".ics")))));
        }

        LinearLayout checks = section("ПРОВЕРКА ТЕЛЕФОНА");
        for (Perms.Item i : Perms.list(a))
            checks.addView(check(i.title, i.why, i.ok, () -> { Perms.markSeen(a, i); i.fix.run(); }));
        if (Build.VERSION.SDK_INT >= 31) {
            checks.addView(Ui.linkRow(a, "Ссылки «Звонка» в приложении", "Ссылки и QR-коды классов откроются здесь, а не в браузере",
                    null, v -> open(new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:" + a.getPackageName())))));
        }

        LinearLayout upd = section("ОБНОВЛЕНИЯ");
        boolean avail = Updates.available(a);
        upd.addView(Ui.linkRow(a, avail ? "Доступна версия " + Updates.latestText(a) : "Проверить обновления",
                "Установлена " + Updates.currentName(a) + " (сборка " + Updates.currentBuild(a) + ")", avail ? "Скачать" : null, v -> {
                    if (Updates.available(a)) {
                        if (open(Uri.parse(Updates.APK_URL)))
                            Toast.makeText(a, "Когда файл скачается, открой его и нажми «Обновить»", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(a, "Проверяю…", Toast.LENGTH_SHORT).show();
                    Updates.check(a, ok -> {
                        Toast.makeText(a, !ok ? "Нет связи с GitHub, попробуй позже"
                                : Updates.available(a) ? "Есть обновление!" : "У тебя последняя версия", Toast.LENGTH_SHORT).show();
                        build();
                        a.rebuild();
                    });
                }));
        upd.addView(flag("Проверять автоматически", "Новая версия раз в сутки, замены в расписании раз в 3 часа, когда приложение открыто. Нужен интернет",
                Updates.KEY_AUTO, false));

        LinearLayout about = section("О «ЗВОНКЕ»");
        about.addView(Ui.linkRow(a, "Что нового", "Все обновления сайта, Android и iPhone", null, v -> a.startActivity(new Intent(a, Changelog.class))));
        about.addView(Ui.linkRow(a, "Написать нам", "Идея, ошибка или вопрос. Анонимно", null, v -> a.startActivity(new Intent(a, Feedback.class))));
        about.addView(Ui.linkRow(a, "Сайт «Звонка»", "Все классы, установка для iPhone", null, v -> open(Uri.parse(Updates.SITE))));
        about.addView(Ui.linkRow(a, "Исходный код", "github.com/Danick-Dack/Zvonok", null, v -> open(Uri.parse("https://github.com/Danick-Dack/Zvonok"))));
        long n = Math.max(1, ChronoUnit.DAYS.between(BORN, LocalDate.now()));
        TextView age = note("«Звонку» " + Schedule.word((int) n, "день", "дня", "дней") + ". Первая версия вышла 1 ноября 2026.");
        age.setPadding(0, a.dp(12), 0, a.dp(4));
        about.addView(age);

        root.addView(Ui.caps(a, "ВОПРОСЫ"));
        LinearLayout faq = Ui.card(a);
        root.addView(faq);
        for (String[] qa : FAQ) faq.addView(question(qa[0], qa[1]));

        TextView privacy = Ui.text(a, "Про приватность: в интернет приложение выходит, только если ты разрешил, и только чтобы узнать "
                + "про новую версию и замены в расписании, или когда ты сам отправляешь нам письмо. Рекламы, аккаунтов и аналитики нет.\n\n"
                + "Про батарею: пока экран выключен, приложение ничего не делает и телефон не будит.", 13, Ui.MUTED, null);
        privacy.setLineSpacing(0, 1.2f);
        privacy.setPadding(a.dp(4), a.dp(22), a.dp(4), 0);
        root.addView(privacy);
        view.post(() -> view.scrollTo(0, y));
    }

    /** Поделиться с классом: QR-код и ссылка сразу на класс, приложение без интернета, плакат. */
    private void shareSection() {
        LinearLayout share = section("ПОДЕЛИТЬСЯ С КЛАССОМ");
        String cls = Schedule.isCustom() ? "" : Schedule.title();
        String url = Updates.SITE + (cls.isEmpty() ? "" : "#" + slug(cls));
        Qr qr = Qr.of(a, cls);
        if (qr != null) {
            qr.setContentDescription("QR-код: " + url);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(176), a.dp(176));
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.topMargin = a.dp(8);
            share.addView(qr, lp);
        }
        TextView n = note(cls.isEmpty() ? "Ссылка на сайт. Наведи камеру телефона на QR-код."
                : "Ссылка и QR-код сразу откроют расписание " + cls + ".");
        n.setGravity(Gravity.CENTER);
        n.setPadding(0, a.dp(10), 0, a.dp(6));
        share.addView(n);
        String msg = "«Звонок»: расписание всех классов лицея №165. Сколько до звонка, какой урок дальше и в каком кабинете. "
                + "Работает без интернета.\n" + (cls.isEmpty() ? "" : "Сразу на " + cls + ": ") + url;
        share.addView(Ui.linkRow(a, "Отправить ссылку", cls.isEmpty() ? "На сайт «Звонка»" : "Сразу на расписание " + cls, null,
                v -> open(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Поделиться"))));
        share.addView(Ui.linkRow(a, "Отправить приложение", "По Bluetooth, Quick Share или в Telegram, можно без интернета", null, v -> {
            Intent i = new Intent(Intent.ACTION_SEND).setType(ApkProvider.MIME)
                    .putExtra(Intent.EXTRA_STREAM, ApkProvider.URI)
                    .putExtra(Intent.EXTRA_TEXT, "Это «Звонок», расписание лицея №165. Сайт: " + Updates.SITE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri("Звонок", ApkProvider.URI));
            open(Intent.createChooser(i, "Отправить приложение"));
        }));
        share.addView(Ui.linkRow(a, "Плакат для класса", "Лист A4 с QR-кодом, откроется на сайте", null,
                v -> open(Uri.parse(Updates.SITE + "poster.html" + (cls.isEmpty() ? "" : "#" + slug(cls))))));
    }

    static String slug(String cls) {
        return cls.replace("А", "a").replace("Б", "b").replace("В", "v").replace("Г", "g").toLowerCase(java.util.Locale.ROOT);
    }

    private LinearLayout section(String title) {
        root.addView(Ui.heading(Ui.caps(a, title)));
        LinearLayout card = Ui.card(a);
        root.addView(card);
        return card;
    }

    private TextView caption(String s) {
        TextView t = Ui.text(a, s, 14, Ui.SOFT, "sans-serif-medium");
        t.setPadding(0, a.dp(10), 0, 0);
        return t;
    }

    private TextView note(String s) {
        TextView t = Ui.text(a, s, 14, Ui.MUTED, null);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private View flag(String title, String sub, String key, boolean def) {
        return Ui.switchRow(a, title, sub, Updater.prefs(a).getBoolean(key, def), (b, on) -> {
            Updater.prefs(a).edit().putBoolean(key, on).apply();
            Updater.update(a);
        });
    }

    /** Вопрос раскрывается нажатием, как на сайте. */
    private View question(String q, String answer) {
        LinearLayout box = Ui.vertical(a);
        TextView h = Ui.text(a, q + "  ›", 15, Ui.TEXT, "sans-serif-medium");
        h.setPadding(0, a.dp(12), 0, a.dp(12));
        TextView ans = note(answer);
        ans.setPadding(0, 0, 0, a.dp(12));
        ans.setVisibility(View.GONE);
        h.setContentDescription(q + ". Свёрнуто");
        h.setOnClickListener(v -> {
            boolean open = ans.getVisibility() != View.VISIBLE;
            ans.setVisibility(open ? View.VISIBLE : View.GONE);
            h.setText(q + (open ? "  ⌄" : "  ›"));
            h.setContentDescription(q + (open ? ". Развёрнуто" : ". Свёрнуто"));
        });
        box.addView(h);
        box.addView(ans);
        return box;
    }

    /** ok: true готово, false нужно включить, null проверить нельзя. */
    private View check(String title, String sub, Boolean ok, Runnable fix) {
        LinearLayout r = Ui.horizontal(a);
        r.setPadding(0, a.dp(12), 0, a.dp(12));
        LinearLayout t = Ui.twoLines(a, title, sub);
        t.setLayoutParams(Ui.weight(1));
        r.addView(t);
        TextView st = Ui.text(a, ok == null ? "Открыть" : ok ? "✓ Готово" : "Включить", 13,
                ok == null ? Ui.TEXT : ok ? Ui.MUTED : Ui.ON_ACCENT, "sans-serif-medium");
        if (ok == null) st.setBackground(Ui.round(a, 0, 12, Ui.LINE));
        else if (!ok) st.setBackground(Ui.round(a, Ui.ACCENT, 12, 0));
        st.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8));
        r.addView(st);
        r.setOnClickListener(v -> fix.run());
        return r;
    }

    private boolean open(Uri u) {
        return open(new Intent(Intent.ACTION_VIEW, u));
    }

    private boolean open(Intent i) {
        try {
            a.startActivity(i);
            return true;
        } catch (RuntimeException e) {
            Toast.makeText(a, "Не нашлось приложения, чтобы это открыть", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
