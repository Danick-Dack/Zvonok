package app.urok7b;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Внутренний приёмник (не экспортирован): плановые тики будильника и возврат смахнутого уведомления.
 * Другие приложения отправить сюда ничего не могут.
 */
public class TickReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent != null && Updater.ACTION_BUZZ.equals(intent.getAction())) {
            Updater.buzz(ctx);
            Updater.update(ctx, false); // заодно завести напоминание на следующий урок
            return;
        }
        // плановый тик может ничего не перерисовывать, если экран выключен; возврат всегда полное обновление
        boolean force = intent == null || !Updater.ACTION_TICK.equals(intent.getAction());
        Updater.update(ctx, force);
    }
}
