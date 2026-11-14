package app.urok7b;

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

/**
 * Вкладка «Ещё» из трёх коротких разделов: «Главное» (класс, лицей, игры, поделиться), «Настройки» (оформление,
 * уведомление и звук) и «О „Звонке“» (что нового, написать нам, версия и разрешения). Вопросы и ответы, плакат и
 * исходный код живут на сайте: в приложении только то, чем пользуются каждый день.
 */
final class MoreTab {

    /** «Звонку» столько дней: первая версия вышла 1 ноября 2026 (как на сайте). */
    static final LocalDate BORN = LocalDate.of(2026, 11, 1);
    private static final String[] PANES = {"Главное", "Настройки", "О «Звонке»"};
    /** Открытый раздел. Общий на процесс: смена темы пересоздаёт экран, а раздел остаётся тем же. */
    private static int pane;
    private TextView age;

    private static String ageText() {
        long n = Math.max(1, ChronoUnit.DAYS.between(BORN, LocalDate.now()));
        return "«Звонку» " + Schedule.word((int) n, "день", "дня", "дней") + ". Первая версия вышла 1 ноября 2026. За помощь спасибо Кириллу и Руслану";
    }

    /** Счётчик дней «Звонка»: экран вызывает при каждой смене ключа, в том числе в полночь. */
    void updateAge() {
        if (age != null) age.setText(ageText());
    }

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
        build(view.getScrollY());
    }

    private void build(int y) {
        root.removeAllViews();
        age = null;
        root.addView(Ui.heading(Ui.text(a, "Ещё", 28, Ui.TEXT, "sans-serif-medium")));
        root.addView(Ui.text(a, Schedule.school() + " · " + Schedule.term(), 14, Ui.MUTED, null));
        root.addView(Ui.segments(a, PANES, pane, k -> { pane = k; build(0); }), Ui.mw(a, 16));
        if (pane == 0) main();
        else if (pane == 1) settings();
        else about();
        view.post(() -> view.scrollTo(0, y));
    }

    /** «Главное»: то, за чем сюда приходят чаще всего. */
    private void main() {
        LinearLayout cls = group("Класс и расписание");
        cls.addView(Ui.linkRow(a, "Класс", Schedule.isCustom() ? "Сейчас включено своё расписание" : null,
                Updater.prefs(a).getString(Schedule.KEY_CLASS, Schedule.DEFAULT_CLASS), v -> Ui.pickClass(a, () -> { build(); a.rebuild(); })));
        cls.addView(Ui.linkRow(a, "Своё расписание", "Любые предметы, кабинеты и звонки", Schedule.isCustom() ? "вкл" : null,
                v -> a.startActivity(new Intent(a, EditorActivity.class))));
        org.json.JSONObject extras = Plus.set();
        int picked = (extras.optJSONArray("shor") == null ? 0 : extras.optJSONArray("shor").length())
                + (extras.optJSONArray("clubs") == null ? 0 : extras.optJSONArray("clubs").length());
        cls.addView(Ui.linkRow(a, "Кружки и ШОР", "Таймер до начала и конца занятий после уроков", picked > 0 ? String.valueOf(picked) : null,
                v -> a.startActivity(new Intent(a, Pages.class).putExtra(Pages.EXTRA_PAGE, Pages.CLUBS))));
        if (!Schedule.isCustom()) {
            String slug = Schedule.title().replace("А", "A").replace("Б", "B").replace("В", "V").replace("Г", "G");
            cls.addView(Ui.linkRow(a, "Уроки в календаре", "Каникулы и замены учтены, календарь обновляется сам", null,
                    v -> open(Uri.parse("https://calendar.google.com/calendar/render?cid=" + Uri.encode("webcal://danick-dack.github.io/Zvonok/cal/" + slug + ".ics")))));
        }

        LinearLayout lyc = group("Лицей");
        lyc.addView(Ui.linkRow(a, "Олимпиады", "Алматы и онлайн, 7–11 классы", null,
                v -> a.startActivity(new Intent(a, Pages.class).putExtra(Pages.EXTRA_PAGE, Pages.OLYMP))));
        lyc.addView(Ui.linkRow(a, "Гимн лицея", "Текст, чтобы не подглядывать у соседа", null,
                v -> a.startActivity(new Intent(a, Pages.class).putExtra(Pages.EXTRA_PAGE, Pages.ANTHEM))));

        LinearLayout games = group("На перемене");
        games.addView(Ui.linkRow(a, "Слова", "Слово из 5 букв, одно на весь лицей в день", null, v -> Words.show(a)));
        games.addView(Ui.linkRow(a, "Змейка", "Без интернета, рекорд остаётся на телефоне", null, v -> Snake.show(a)));

        String c = Schedule.isCustom() ? "" : Schedule.title();
        LinearLayout share = group("Одноклассникам");
        share.addView(Ui.linkRow(a, "Поделиться", c.isEmpty() ? "QR-код и ссылка на сайт «Звонка»" : "QR-код и ссылка сразу на расписание " + c, null, v -> share(c)));
    }

    /** «Настройки»: как выглядит приложение и что оно присылает. */
    private void settings() {
        LinearLayout look = group("Оформление");
        look.addView(caption("Тема"));
        String[] keys = {"auto", "light", "dark"};
        int cur = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(Ui.scheme(a))) cur = i;
        look.addView(Ui.segments(a, new String[]{"Как в системе", "Светлая", "Тёмная"}, cur,
                k -> { Updater.prefs(a).edit().putString(Ui.KEY_SCHEME, keys[k]).apply(); a.recreate(); }), Ui.mw(a, 8));
        look.addView(caption("Главный цвет"));
        Ui.Flow colors = new Ui.Flow(a, 12);
        for (String[] acc : Ui.ACCENTS) {
            boolean on = acc[0].equals(Ui.accentKey(a));
            View dot = new View(a);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFF000000 | Integer.parseInt(acc[Ui.DARK ? 3 : 2], 16));
            // обводка видна на любом фоне: чёрный круг не сливается с тёмной темой
            g.setStroke(a.dp(on ? 3 : 1), on ? Ui.TEXT : (Ui.MUTED & 0x00FFFFFF) | 0x99000000);
            dot.setBackground(g);
            dot.setSelected(on);
            dot.setContentDescription("Цвет: " + acc[1] + (on ? ", выбран" : ""));
            dot.setOnClickListener(v -> { Ui.tick(v); Updater.prefs(a).edit().putString(Ui.KEY_ACCENT, acc[0]).apply(); a.recreate(); });
            colors.addView(dot, new android.view.ViewGroup.LayoutParams(a.dp(44), a.dp(44)));
        }
        look.addView(colors, Ui.mw(a, 8));
        look.addView(caption("Иконка приложения"));
        Ui.Flow icons = new Ui.Flow(a, 8);
        for (android.content.pm.ActivityInfo i : Fun.icons(a)) {
            boolean on = Fun.iconOn(a, i);
            android.widget.ImageView v = new android.widget.ImageView(a);
            v.setImageDrawable(i.loadIcon(a.getPackageManager()));
            v.setPadding(a.dp(4), a.dp(4), a.dp(4), a.dp(4));
            if (on) v.setBackground(Ui.round(a, 0, 16, Ui.TEXT));
            v.setContentDescription("Иконка: " + i.metaData.getString("icon_name") + (on ? ", выбрана" : ""));
            v.setOnClickListener(x -> {
                Ui.tick(x);
                Fun.setIcon(a, i);
                build();
                Toast.makeText(a, "Иконка сменится через пару секунд. Если значок пропал с рабочего стола, верни его из списка приложений",
                        Toast.LENGTH_LONG).show();
            });
            icons.addView(v, new android.view.ViewGroup.LayoutParams(a.dp(56), a.dp(56)));
        }
        look.addView(icons, Ui.mw(a, 8));
        look.addView(Ui.switchRow(a, "Цвет предметов", "Алгебра синяя, физика фиолетовая, химия бирюзовая", !Ui.PLAIN,
                (b, on) -> { Updater.prefs(a).edit().putBoolean(Ui.KEY_PLAIN, !on).apply(); a.recreate(); }), Ui.mw(a, 8));
        look.addView(Ui.switchRow(a, "Праздничные темы", "В праздники и каникулы значок и цвет дня", Updater.prefs(a).getBoolean(Plus.KEY_FEST, true), (b, on) -> {
            Updater.prefs(a).edit().putBoolean(Plus.KEY_FEST, on).apply();
            a.rebuild();
        }));
        look.addView(Ui.linkRow(a, "Виджеты и уведомление", "Цвета, прозрачность, свой фон, цвет урока", null,
                v -> a.startActivity(new Intent(a, StyleActivity.class))));

        LinearLayout notif = group("Уведомление и звук");
        notif.addView(flag("Таймер на экране блокировки", "Уведомление с уроком и кабинетом, не смахивается", Updater.KEY_NOTIF, true));
        if (Build.VERSION.SDK_INT >= 36) {
            notif.addView(flag("Таймер в строке состояния", "Урок и минуты до звонка видны вверху экрана", Updater.KEY_LIVE, true));
        }
        notif.addView(flag("Только в учебное время", "После уроков и в выходные уведомления нет", Updater.KEY_SCHOOL_ONLY, false));
        notif.addView(Ui.switchRow(a, "Экономный таймер", "Без секунд: «24 мин» вместо 24:16", !Theme.load(a).seconds, (b, on) -> {
            Theme t = Theme.load(a);
            t.seconds = !on;
            t.save(a);
            Updater.update(a);
        }));
        notif.addView(flag("Вибрация перед звонком", "За 3 минуты до звонка на урок и на перемену", Updater.KEY_BUZZ, false));
        notif.addView(flag("Новости лицея", "Уведомление о новом посте в канале", Feed.KEY_NOTIFY, true));
        notif.addView(Ui.switchRow(a, "Звуки", "На уроках и переменах тикают часы, на звонке звенит звонок", Fun.soundOn(a),
                (b, on) -> Updater.prefs(a).edit().putBoolean(Fun.KEY_SOUND, on).apply()));
    }

    /** «О „Звонке“»: связь с нами и состояние самого приложения. */
    private void about() {
        LinearLayout top = group(null);
        top.addView(Ui.linkRow(a, "Что нового", "Все обновления сайта, Android и iPhone", null, v -> a.startActivity(new Intent(a, Changelog.class))));
        top.addView(Ui.linkRow(a, "Написать нам", "Идея, ошибка или вопрос. Анонимно", null, v -> a.startActivity(new Intent(a, Feedback.class))));
        top.addView(Ui.linkRow(a, "Сайт «Звонка»", "Вопросы и ответы, плакат для класса, iPhone", null, v -> web(Updates.SITE)));

        LinearLayout app = group("Приложение");
        app.addView(Ui.linkRow(a, "Версия " + Updates.currentName(a), "Сборка " + Updates.currentBuild(a) + ". Новые версии обязательные, приложение находит их само", null, v -> {
            Toast.makeText(a, "Проверяю…", Toast.LENGTH_SHORT).show();
            Updates.check(a, ok -> {
                if (Updates.available(a)) a.startActivity(new Intent(a, Setup.class));
                else Toast.makeText(a, ok ? "У тебя последняя версия" : "Нет связи, попробуй позже", Toast.LENGTH_SHORT).show();
            });
        }));
        app.addView(Ui.linkRow(a, "Разрешения", "Обязательные: без них таймер отстаёт или пропадает", null,
                v -> a.startActivity(new Intent(a, Setup.class).putExtra(Setup.EXTRA_REVIEW, true))));
        if (Build.VERSION.SDK_INT >= 31) {
            app.addView(Ui.linkRow(a, "Ссылки «Звонка» в приложении", "QR-коды классов откроются здесь, а не в браузере",
                    null, v -> open(new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:" + a.getPackageName())))));
        }

        age = note(ageText());
        age.setPadding(a.dp(4), a.dp(20), a.dp(4), 0);
        root.addView(age);
        TextView privacy = note("Аккаунтов и рекламы нет. В интернет приложение ходит только на сайт «Звонка» и GitHub: за новой версией, "
                + "заменами и новостями лицея. Пока экран выключен, оно ничего не делает и телефон не будит");
        privacy.setTextSize(13);
        privacy.setPadding(a.dp(4), a.dp(8), a.dp(4), 0);
        root.addView(privacy);
    }

    /** Поделиться с классом: QR-код и ссылка сразу на класс. APK приложение не раздаёт: ставить только с официального сайта. */
    private void share(String cls) {
        String url = Updates.SITE + (cls.isEmpty() ? "" : "#" + slug(cls));
        LinearLayout box = Ui.vertical(a);
        box.setPadding(a.dp(24), a.dp(16), a.dp(24), 0);
        Qr qr = Qr.of(a, cls);
        if (qr != null) {
            qr.setContentDescription("QR-код: " + url);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(a.dp(200), a.dp(200));
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            box.addView(qr, lp);
        }
        TextView n = note(cls.isEmpty() ? "Наведи камеру телефона на QR-код: откроется сайт «Звонка»"
                : "Наведи камеру телефона на QR-код: сразу откроется расписание " + cls);
        n.setGravity(Gravity.CENTER);
        n.setPadding(0, a.dp(12), 0, 0);
        box.addView(n);
        String msg = "«Звонок»: расписание всех классов лицея №165. Сколько до звонка, какой урок дальше и в каком кабинете. "
                + "Работает без интернета.\n" + (cls.isEmpty() ? "" : "Сразу на " + cls + ": ") + url;
        new android.app.AlertDialog.Builder(a, Ui.dialogTheme())
                .setTitle(cls.isEmpty() ? "Поделиться «Звонком»" : "Расписание " + cls)
                .setView(box)
                .setPositiveButton("Отправить ссылку", (d, w) -> open(Intent.createChooser(
                        new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Поделиться")))
                .setNegativeButton("Закрыть", null)
                .show();
    }

    static String slug(String cls) {
        return cls.replace("А", "a").replace("Б", "b").replace("В", "v").replace("Г", "g").toLowerCase(java.util.Locale.ROOT);
    }

    /** Раздел: спокойная подпись и карточка под ней. Цветных значков у разделов нет: цвет остаётся урокам и таймеру. */
    private LinearLayout group(String title) {
        LinearLayout card = Ui.card(a);
        if (title != null) {
            root.addView(Ui.heading(Ui.label(a, title)));
            root.addView(card);
        } else {
            root.addView(card, Ui.mw(a, 24));
        }
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

    private boolean open(Uri u) {
        return open(new Intent(Intent.ACTION_VIEW, u));
    }

    private void web(String url) {
        Ui.web(a, url);
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
