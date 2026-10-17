package org.pocketworkstation.pckeyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;

final class CompatUtils {
    private CompatUtils() {}

    static void registerReceiver(Context context, BroadcastReceiver receiver,
            IntentFilter filter, boolean exported) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            int flags = exported ? Context.RECEIVER_EXPORTED : Context.RECEIVER_NOT_EXPORTED;
            context.registerReceiver(receiver, filter, flags);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }

    static int immutablePendingIntentFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        }
        return android.app.PendingIntent.FLAG_UPDATE_CURRENT;
    }
}
