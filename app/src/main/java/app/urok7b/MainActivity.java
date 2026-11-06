package app.urok7b;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Главный экран «Звонка»: вкладки «Сейчас», «Неделя», «Кабинеты» и «Ещё», как на сайте.
 * Пока экран открыт, он просыпается раз в минуту (секунды таймера рисует сама система), в фоне не работает.
 */
public class MainActivity extends Activity {

    static final String EXTRA_TAB = "tab";   // now | week | rooms | more (ярлыки, виджеты)
    static final String EXTRA_WEEK = "week"; // старый ярлык «Неделя»
    static final String EXTRA_FIND = "find"; // сразу к поиску кабинета
    static final int NOW = 0, WEEK = 1, ROOMS = 2, MORE = 3;
    private static final String[] TAB_KEYS = {"now", "week", "rooms", "more"};
    private static final String[] TAB_NAMES = {"Сейчас", "Неделя", "Кабинеты", "Ещё"};
    private static final int[] TAB_ICONS = {R.drawable.ic_now, R.drawable.ic_week, R.drawable.ic_door, R.drawable.ic_more};

    /** Что происходит сейчас: одно вычисление на тик для всех вкладок. */
    static final class Tick {
        final ZonedDateTime now = ZonedDateTime.now();
        final long ms = now.toInstant().toEpochMilli();
        final int min = now.getHour() * 60 + now.getMinute();
        final Schedule.Status s = Schedule.compute(now);
        final Updater.Display d = Updater.describe(s, ms);
    }

    private int builtVersion, tab = NOW;
    private final ScrollView[] pages = new ScrollView[4];
    private final LinearLayout[] tabs = new LinearLayout[4];
    private NowTab nowTab;
    private WeekTab weekTab;
    private RoomsTab roomsTab;
    private MoreTab moreTab;
    private FrameLayout host;
    private boolean askedClass, linkHandled;
    private String lastKey = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh(false);
            handler.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000 + 300);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.apply(this);
        builtVersion = Ui.version;
        Schedule.load(this);

        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);
        host = new FrameLayout(this);
        root.addView(host, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(Ui.divider(this));
        LinearLayout bar = Ui.horizontal(this);
        bar.setBackgroundColor(Ui.BG);
        bar.setPadding(0, dp(4), 0, dp(4));
        for (int i = 0; i < 4; i++) {
            final int k = i;
            LinearLayout t = Ui.vertical(this);
            t.setGravity(Gravity.CENTER);
            t.setMinimumHeight(dp(56));
            t.addView(Ui.icon(this, TAB_ICONS[i], Ui.MUTED, 24));
            TextView name = Ui.text(this, TAB_NAMES[i], 12, Ui.MUTED, "sans-serif-medium");
            name.setGravity(Gravity.CENTER);
            t.addView(name);
            t.setContentDescription(TAB_NAMES[i] + ", вкладка " + (i + 1) + " из 4");
            t.setOnClickListener(v -> {
                Ui.tick(v);
                if (tab == k) pages[k].smoothScrollTo(0, 0); // повторное нажатие: наверх, как в системных приложениях
                else show(k);
            });
            tabs[i] = t;
            bar.addView(t, Ui.weight(1));
        }
        root.addView(bar);
        setContentView(root);
        if (state != null) tab = state.getInt(EXTRA_TAB, NOW);
        show(tab);
        handleIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(EXTRA_TAB, tab);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        linkHandled = false;
        handleIntent(intent);
    }

    /** Ярлыки, виджеты и ссылки на класс: …/Zvonok/#10a или …/Zvonok/10a (так устроены QR-коды). */
    private void handleIntent(Intent i) {
        if (i == null) return;
        String t = i.getStringExtra(EXTRA_TAB);
        String mode = i.getStringExtra("mode"); // ярлыки из версии 4.0
        if ("true".equals(String.valueOf(i.getExtras() == null ? null : i.getExtras().get(EXTRA_WEEK)))) t = "week";
        if (mode != null) t = "rooms";
        for (int k = 0; k < 4; k++) if (TAB_KEYS[k].equals(t)) show(k);
        if ("find".equals(mode) || i.getBooleanExtra(EXTRA_FIND, false)) {
            show(ROOMS);
            roomsTab.focusSearch();
        }
        i.removeExtra(EXTRA_TAB);
        i.removeExtra("mode");
        i.removeExtra(EXTRA_WEEK);
        i.removeExtra(EXTRA_FIND);
        String cls = classFromLink(i.getData());
        if (cls == null || linkHandled) return;
        linkHandled = true;
        askedClass = true;
        boolean first = !Updater.prefs(this).contains(Schedule.KEY_CLASS);
        if (first || cls.equals(Updater.prefs(this).getString(Schedule.KEY_CLASS, ""))) {
            setClass(cls);
            return;
        }
        new android.app.AlertDialog.Builder(this, Ui.dialogTheme())
                .setTitle("Расписание " + cls)
                .setMessage("Это ссылка на класс " + cls + ". Показывать " + cls + " вместо " + Schedule.title() + "?")
                .setPositiveButton("Да, мой класс " + cls, (d, w) -> setClass(cls))
                .setNegativeButton("Нет", null)
                .show();
    }

    private static final Pattern LINK = Pattern.compile("^(7|8|9|10|11)([abvgабвг])$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** «10a», «10а», «10А» → «10А»; всё остальное null. */
    static String classFromLink(Uri u) {
        if (u == null) return null;
        for (String part : new String[]{u.getFragment(), u.getLastPathSegment()}) {
            Matcher m = LINK.matcher(part == null ? "" : part.trim());
            if (!m.matches()) continue;
            String c = m.group(1) + "АБВГ".charAt("abvgабвг".indexOf(m.group(2).toLowerCase(java.util.Locale.ROOT)) % 4);
            return Schedule.classes().contains(c) ? c : null;
        }
        return null;
    }

    void setClass(String cls) {
        Updater.prefs(this).edit().putString(Schedule.KEY_CLASS, cls).putBoolean(Schedule.KEY_USE_CUSTOM, false).apply();
        Schedule.invalidate();
        Updater.update(this);
        rebuild();
        android.widget.Toast.makeText(this, "Класс " + cls, android.widget.Toast.LENGTH_SHORT).show();
    }

    /** Показать вкладку; содержимое строится при первом открытии. */
    void show(int k) {
        tab = k;
        if (pages[k] == null) {
            if (k == NOW) pages[k] = (nowTab = new NowTab(this)).view;
            else if (k == WEEK) pages[k] = (weekTab = new WeekTab(this)).view;
            else if (k == ROOMS) pages[k] = (roomsTab = new RoomsTab(this)).view;
            else pages[k] = (moreTab = new MoreTab(this)).view;
            host.addView(pages[k]);
        }
        for (int i = 0; i < 4; i++) {
            if (pages[i] != null) pages[i].setVisibility(i == k ? View.VISIBLE : View.GONE);
            boolean on = i == k;
            tabs[i].setSelected(on);
            ((android.widget.ImageView) tabs[i].getChildAt(0)).setColorFilter(on ? Ui.ACCENT : Ui.MUTED);
            ((TextView) tabs[i].getChildAt(1)).setTextColor(on ? Ui.ACCENT : Ui.MUTED);
        }
        lastKey = "";
        refresh(true);
    }

    @Override
    public void onBackPressed() {
        if (tab != NOW) show(NOW); // «Назад» с другой вкладки ведёт на «Сейчас», а не закрывает приложение
        else super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.palette(this);
        if (Ui.version != builtVersion) {
            recreate();
            return;
        }
        Updater.update(this);
        if (!Updater.prefs(this).contains(Schedule.KEY_CLASS) && !askedClass) {
            askedClass = true;
            // запоминаем сразу: если окно закроют без выбора, больше не спрашиваем (класс меняется кнопкой сверху)
            Updater.prefs(this).edit().putString(Schedule.KEY_CLASS, Schedule.DEFAULT_CLASS).apply();
            NowTab.markNewsSeen(this); // новичку «что нового» незачем
            Ui.pickClass(this, () -> { rebuild(); setup(); });
        } else if (!setup()) {
            Updates.askOnce(this, () -> Updates.check(this, ok -> rebuild()));
        }
        if (Updates.due(this)) Updates.check(this, ok -> rebuild());
        else if (Updates.changesDue(this)) Updates.checkChanges(this, this::rebuild);
        if (moreTab != null) moreTab.build();
        rebuild();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        if (nowTab != null) nowTab.pause();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        Updater.update(this);
    }

    /** Поменялось что-то большое (класс, замены, обновление): всё перерисовать. */
    void rebuild() {
        lastKey = "";
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void refresh(boolean force) {
        Schedule.load(this);
        Tick t = new Tick();
        // ключ меняется на звонках, в полночь и раз в 5 % урока (полоска прогресса), а не каждую минуту
        String key = tab + "|" + t.d.sig + "|" + t.now.toLocalDate() + "|" + t.d.progress / 5 + "|" + Schedule.title();
        boolean changed = force || !key.equals(lastKey);
        if (changed && !lastKey.isEmpty() && t.d.sig != null && !lastKey.contains(t.d.sig)) announce(t);
        lastKey = key;
        if (tab == NOW) nowTab.render(t, changed);
        else if (changed && tab == WEEK) weekTab.render(t);
        else if (changed && tab == ROOMS) roomsTab.render(t);
    }

    /** Для экранного диктора: только смена урока, а не каждая минута (как на сайте). */
    private void announce(Tick t) {
        AccessibilityManager am = getSystemService(AccessibilityManager.class);
        if (am == null || !am.isEnabled()) return;
        host.announceForAccessibility(NowTab.spoken(t));
    }

    /** Обязательная настройка телефона, пока чего-то не хватает. true: экран настройки открыт. */
    private boolean setup() {
        if (Perms.missing(this).isEmpty()) return false;
        startActivity(new Intent(this, Setup.class));
        return true;
    }

    int dp(float v) {
        return Ui.dp(this, v);
    }
}
