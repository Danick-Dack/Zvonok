package app.urok7b;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/**
 * Служба держит уведомление: уведомление службы нельзя смахнуть, «Очистить всё» его не трогает,
 * и его можно покрасить в чёрный. Сама служба ничего не считает и процессор не нагружает:
 * она просто ждёт включения экрана, чтобы сразу показать свежие данные.
 * Пока экран выключен, приложение не делает ничего.
 */
public class LessonService extends Service {

    private static volatile boolean running;
    private BroadcastReceiver screenOn;

    static boolean isRunning() {
        return running;
    }

    static void ensureRunning(Context ctx) {
        if (running) return;
        try {
            ctx.startForegroundService(new Intent(ctx, LessonService.class));
        } catch (RuntimeException e) {
            // запуск из фона запрещён — уведомление всё равно показано обычным способом,
            // служба поднимется при следующем открытии приложения или перезагрузке
        }
    }

    static void stop(Context ctx) {
        if (running) ctx.stopService(new Intent(ctx, LessonService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        screenOn = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                Updater.update(c, true);
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(screenOn, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(screenOn, f);
            }
        } catch (RuntimeException e) {
            screenOn = null;
        }
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
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Updater.notifEnabled(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean wasRunning = running;
        running = true;
        // служба поднялась после убийства процессом MIUI — перезапустить будильники
        if (!wasRunning) Updater.update(this, true);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // смахнули приложение из «Недавних» — уведомление должно остаться
        Updater.update(this, true);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        if (screenOn != null) {
            try {
                unregisterReceiver(screenOn);
            } catch (RuntimeException ignored) {
            }
            screenOn = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
