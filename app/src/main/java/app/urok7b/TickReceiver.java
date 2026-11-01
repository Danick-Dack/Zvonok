package app.urok7b;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Плановые тики в школьное время, возврат смахнутого уведомления,
 * а также перезагрузка, обновление приложения и смена времени.
 */
public class TickReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        // плановый тик может ничего не перерисовывать, если экран выключен; всё остальное — полное обновление
        boolean force = intent == null || !Updater.ACTION_TICK.equals(intent.getAction());
        Updater.update(ctx, force);
    }
}
