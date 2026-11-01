package app.urok7b;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Служба, которая только держит уведомление: такое уведомление нельзя смахнуть,
 * и его можно покрасить в чёрный. Сама служба ничего не считает и процессор не нагружает:
 * обновления по-прежнему приходят от будильников.
 */
public class LessonService extends Service {

    private static volatile boolean running;

    static void ensureRunning(Context ctx) {
        if (running) return;
        try {
            ctx.startForegroundService(new Intent(ctx, LessonService.class));
        } catch (Exception e) {
            // запуск из фона запрещён — уведомление всё равно показано обычным способом
        }
    }

    static void stop(Context ctx) {
        if (running) ctx.stopService(new Intent(ctx, LessonService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = Updater.buildNow(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(Updater.NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(Updater.NOTIF_ID, n);
            }
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        if (!Updater.notifEnabled(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
