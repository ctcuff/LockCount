package com.camtech.android.lockcount.receivers;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.camtech.android.lockcount.activities.SettingsActivity;
import com.camtech.android.lockcount.services.LockService;

/**
 * Handles button clicks from the notification.
 * See: {@link LockService#onStartCommand(Intent, int, int)}
 */

public class ButtonReceiver extends BroadcastReceiver {

    public static final int STOP = 100;
    public static final int RESET = 200;
    public static final int SETTINGS = 300;
    public static final String BROADCAST_LOCKS_RESET = "updateNumberOfLocks";

    private static final String TAG = ButtonReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        int requestCode = intent.getIntExtra(LockService.NOTIFICATION_TAG, 0);
        switch (requestCode) {
            case STOP:
                // The stop button on the notification was clicked
                Log.i(TAG, "Broadcast received... stopping service");
                context.stopService(new Intent(context, LockService.class));

                break;
            case RESET:
                // The reset button on the notification was clicked
                Log.i(TAG, "Broadcast received... resetting counter");
                // Make sure the MainActivity knows to update the UI
                context.sendBroadcast(new Intent(BROADCAST_LOCKS_RESET));
                break;
            case SETTINGS:
                Log.i(TAG, "Broadcast received... opening SettingsActivity");
                // If the app was closed and the settings button was clicked,
                // pressing the app button should return the user to what they
                // were doing previously
                Intent mainActivityIntent = new Intent(context, SettingsActivity.class);
                mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Intent closeNotificationBar = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                context.sendBroadcast(closeNotificationBar);
                context.startActivity(mainActivityIntent);
                break;
            default:
                // Thrown if an unknown intent ID was sent here
                throw new IllegalArgumentException("Unknown intent with ID: " + intent.getIntExtra(LockService.NOTIFICATION_TAG, 0));
        }

    }
}
