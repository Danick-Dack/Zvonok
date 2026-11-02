package app.urok7b;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Только системные события: перезагрузка, обновление приложения, смена времени и даты.
 * Чужие приложения не могут подсунуть сюда ничего полезного: любое событие лишь пересчитывает экран.
 */
public class SystemReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        switch (intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
            case Intent.ACTION_MY_PACKAGE_REPLACED:
            case Intent.ACTION_TIME_CHANGED:
            case Intent.ACTION_TIMEZONE_CHANGED:
            case Intent.ACTION_DATE_CHANGED:
            case "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED":
                Updater.update(ctx, true);
                break;
            default:
                break;
        }
    }
}
