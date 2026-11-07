package app.urok7b;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Все разрешения, которые нужны «Звонку», в одном месте: для «Проверки телефона» в настройках
 * и для подсказки при запуске. У каждого пункта понятное «зачем» и переход прямо к нужному переключателю.
 */
final class Perms {

    static final int REQ_NOTIF = 41;

    static final class Item {
        final String key, title, why;
        final Boolean ok;          // true — готово, false — нужно включить, null — проверить нельзя
        final Runnable fix;

        Item(String key, String title, String why, Boolean ok, Runnable fix) {
            this.key = key;
            this.title = title;
            this.why = why;
            this.ok = ok;
            this.fix = fix;
        }
    }

    private Perms() {}

    static String brand() {
        return (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);
    }

    static boolean isXiaomi() {
        String b = brand();
        return b.contains("xiaomi") || b.contains("redmi") || b.contains("poco");
    }

    static boolean isVivo() {
        String b = brand();
        return b.contains("vivo") || b.contains("iqoo");
    }

    static boolean isOppo() {
        String b = brand();
        return b.contains("oppo") || b.contains("realme") || b.contains("oneplus");
    }

    static boolean isSamsung() {
        return brand().contains("samsung");
    }

    static boolean isTranssion() {
        String b = brand();
        return b.contains("tecno") || b.contains("infinix") || b.contains("itel");
    }

    static boolean isHuawei() {
        String b = brand();
        return b.contains("huawei") || b.contains("honor");
    }

    /** Есть ли на этом телефоне отдельное разрешение «показ на экране блокировки». */
    static boolean hasLockPermission() {
        return isXiaomi() || isVivo() || isOppo() || isHuawei();
    }

    /** Состояние скрытого разрешения MIUI (10020 — экран блокировки, 10008 — автозапуск). null — не узнать. */
    static Boolean miuiOp(Context c, int op) {
        if (!isXiaomi()) return null;
        try {
            AppOpsManager ops = c.getSystemService(AppOpsManager.class);
            java.lang.reflect.Method m = AppOpsManager.class.getMethod("checkOpNoThrow", int.class, int.class, String.class);
            int r = (int) m.invoke(ops, op, Process.myUid(), c.getPackageName());
            return r == AppOpsManager.MODE_ALLOWED;
        } catch (Throwable e) {
            return null;
        }
    }

    static List<Item> list(Activity a) {
        List<Item> out = new ArrayList<>();
        NotificationManager nm = a.getSystemService(NotificationManager.class);
        if (nm != null) Updater.ensureChannel(nm);

        // 1. уведомления вообще
        boolean appOn = nm != null && nm.areNotificationsEnabled();
        out.add(new Item("notif", "Уведомления",
                "Без этого не будет уведомления с таймером до звонка и кабинетом",
                appOn, () -> askNotifications(a)));

        // 2. канал «Текущий урок»
        NotificationChannel live = nm == null ? null : nm.getNotificationChannel(Updater.CHANNEL);
        if (appOn && live != null) {
            out.add(new Item("ch_live", "Уведомление «Текущий урок»",
                    "Сам таймер в шторке. Если этот пункт выключен, уведомление не появится",
                    live.getImportance() != NotificationManager.IMPORTANCE_NONE, () -> openChannel(a, Updater.CHANNEL)));
            // 3. видно ли его на экране блокировки
            out.add(new Item("ch_lock", "Уведомление на экране блокировки",
                    "Чтобы таймер был виден, не разблокируя телефон",
                    live.getImportance() != NotificationManager.IMPORTANCE_NONE
                            && live.getLockscreenVisibility() != Notification.VISIBILITY_SECRET,
                    () -> openChannel(a, Updater.CHANNEL)));
        }
        // 4. канал вибрации (только если она включена)
        if (appOn && Updater.prefs(a).getBoolean(Updater.KEY_BUZZ, false) && nm != null) {
            NotificationChannel buzz = nm.getNotificationChannel(Updater.CHANNEL_BUZZ);
            out.add(new Item("ch_buzz", "Вибрация перед уроком",
                    "Напоминание за 2 минуты до звонка. Без этого вибрации не будет",
                    buzz == null || buzz.getImportance() != NotificationManager.IMPORTANCE_NONE,
                    () -> openChannel(a, Updater.CHANNEL_BUZZ)));
        }

        // 5. точные будильники
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = a.getSystemService(AlarmManager.class);
            out.add(new Item("alarm", "Точное время звонков",
                    "Без этого таймер может переключаться с опозданием",
                    am == null || am.canScheduleExactAlarms(),
                    () -> open(a, new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(a)))));
        }

        // 6. батарея
        PowerManager pm = a.getSystemService(PowerManager.class);
        out.add(new Item("battery", "Без ограничений батареи",
                "Без этого система может закрыть уведомление. Расход не растёт",
                pm != null && pm.isIgnoringBatteryOptimizations(a.getPackageName()), () -> {
            if (!open(a, new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg(a))))
                open(a, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }));

        // 7. показ поверх экрана блокировки (MIUI, Vivo, Oppo/Realme, Huawei/Honor)
        if (hasLockPermission()) {
            out.add(new Item("lock", "Показ на экране блокировки",
                    isXiaomi() ? "Чтобы по нажатию на уведомление открывался большой таймер без разблокировки. "
                            + "Открой «Другие разрешения» → «Экран блокировки» → «Разрешить»"
                            : "Чтобы по нажатию на уведомление открывался большой таймер без разблокировки. "
                            + "Найди в разрешениях «Экран блокировки» или «Показ на заблокированном экране» и разреши",
                    miuiOp(a, 10020), () -> openOtherPerms(a)));
        }

        // 8. автозапуск
        if (isXiaomi() || isVivo() || isOppo() || isHuawei() || isTranssion()) {
            out.add(new Item("autostart", "Автозапуск",
                    "Чтобы уведомление вернулось после перезагрузки телефона",
                    miuiOp(a, 10008), () -> openAutostart(a)));
        }

        // 9. Samsung усыпляет «неиспользуемые» приложения, даже если батарея без ограничений
        if (isSamsung()) {
            out.add(new Item("samsung_sleep", "Не усыплять «Звонок»",
                    "Обслуживание устройства → Батарея → Ограничения фонового использования: «Звонка» не должно быть среди спящих",
                    null, () -> {
                if (!open(a, new Intent().setComponent(new ComponentName("com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"))))
                    open(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(a)));
            }));
        }

        // 10. Android 16: таймер урока в строке состояния (на Samsung это Now Bar, на Xiaomi Super Island). Не обязательно
        if (Build.VERSION.SDK_INT >= 36 && nm != null && Updater.prefs(a).getBoolean(Updater.KEY_LIVE, true)) {
            out.add(new Item("live", "Таймер в строке состояния",
                    "Урок и минуты до звонка вверху экрана и на экране блокировки (Samsung: Now Bar, Xiaomi: Super Island). По желанию",
                    promoted(nm), () -> {
                if (!open(a, new Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName())))
                    open(a, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
            }));
        }
        return out;
    }

    /** Можно ли показывать таймер в строке состояния (Android 16); null, если не узнать. */
    static Boolean promoted(NotificationManager nm) {
        try {
            return (Boolean) NotificationManager.class.getMethod("canPostPromotedNotifications").invoke(nm);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Чего не хватает: выключенные пункты и непроверяемые, которые ещё ни разу не открывали (таймер в строке состояния по желанию). */
    static List<Item> missing(Activity a) {
        List<Item> out = new ArrayList<>();
        boolean timer = Updater.notifEnabled(a), buzz = Updater.prefs(a).getBoolean(Updater.KEY_BUZZ, false);
        for (Item i : list(a)) {
            if ("live".equals(i.key)) continue;
            // уведомление с таймером выключено в «Ещё»: его разрешения не требуем (вибрации нужны только уведомления вообще)
            if (!timer && ("ch_live".equals(i.key) || "ch_lock".equals(i.key) || "notif".equals(i.key) && !buzz)) continue;
            if (Boolean.FALSE.equals(i.ok) || (i.ok == null && !Updater.prefs(a).getBoolean("perm_seen_" + i.key, false)))
                out.add(i);
        }
        return out;
    }

    static void markSeen(Context c, Item i) {
        Updater.prefs(c).edit().putBoolean("perm_seen_" + i.key, true).apply();
    }

    static void askNotifications(Activity a) {
        if (Build.VERSION.SDK_INT >= 33
                && a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !Updater.prefs(a).getBoolean("notif_asked_twice", false)) {
            // системное окно «Разрешить уведомления?» показывается максимум дважды, потом только настройки
            boolean again = Updater.prefs(a).getBoolean("notif_asked", false);
            Updater.prefs(a).edit().putBoolean("notif_asked", true).putBoolean("notif_asked_twice", again).apply();
            a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        open(a, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
    }

    static void openChannel(Activity a, String channel) {
        if (!open(a, new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID, channel)))
            open(a, new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.getPackageName()));
    }

    /** «Другие разрешения» MIUI или экран разрешений производителя; если не вышло — карточка приложения. */
    static void openOtherPerms(Activity a) {
        String p = a.getPackageName();
        if (isXiaomi()) {
            if (open(a, new Intent("miui.intent.action.APP_PERM_EDITOR").setClassName("com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity").putExtra("extra_pkgname", p))) return;
            if (open(a, new Intent("miui.intent.action.APP_PERM_EDITOR").setClassName("com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity").putExtra("extra_pkgname", p))) return;
        } else if (isVivo()) {
            if (open(a, new Intent().setClassName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity").putExtra("packagename", p))) return;
            if (open(a, new Intent().setClassName("com.iqoo.secure",
                    "com.iqoo.secure.safecenter.PermissionManageDetailActivity").putExtra("packagename", p))) return;
        } else if (isOppo()) {
            if (open(a, new Intent().setClassName("com.coloros.securitypermission",
                    "com.coloros.securitypermission.permission.PermissionAppAllPermissionActivity").putExtra("packageName", p))) return;
        } else if (isHuawei()) {
            if (open(a, new Intent().setClassName("com.huawei.systemmanager",
                    "com.huawei.permissionmanager.ui.MainActivity"))) return;
        }
        open(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(a)));
    }

    static void openAutostart(Activity a) {
        ComponentName[] tries = {
                new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                new ComponentName("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
                new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                new ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity"),
        };
        for (ComponentName c : tries) if (open(a, new Intent().setComponent(c))) return;
        open(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(a)));
    }

    static Uri pkg(Context c) {
        return Uri.parse("package:" + c.getPackageName());
    }

    static boolean open(Activity a, Intent i) {
        try {
            a.startActivity(i);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
