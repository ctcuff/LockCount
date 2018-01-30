package com.camtech.android.lockcount.services;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.camtech.android.lockcount.R;
import com.camtech.android.lockcount.activities.MainActivity;
import com.camtech.android.lockcount.data.TimeConstants;
import com.camtech.android.lockcount.receivers.AutoInsertReceiver;
import com.camtech.android.lockcount.receivers.ButtonReceiver;

import java.util.Calendar;

/**
 * This service counts the number of unlocks
 */
public class LockService extends Service {

    public static final String TAG = LockService.class.getSimpleName();
    public static final String NOTIFICATION_TAG = "LockedNotification";
    public static final String UPDATE_UI = "updateUi";
    public static final String UPDATE_SERVICE_STATUS = "updateServiceStatus";
    private static final String BROADCAST_TAG = "AutoInsertReceiver";
    public static int numberOfUnlocks;
    Intent locksIntent, updateUi, updateServiceStatus;
    public static NotificationManager mNotificationManager;
    public static NotificationCompat.Builder mBuilder;
    public static int NOTIFICATION_ID = 0;
    public static boolean showInStatusBar;

    SharedPreferences.Editor editor;
    SharedPreferences numberOfUnlocksPref;
    SharedPreferences preferences;

    public static String notificationTextDefault;
    boolean notificationEnabled;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate...");
        // To listen for when the user resets the number of unlocks
        registerReceiver(resetLocks, new IntentFilter(ButtonReceiver.BROADCAST_LOCKS_RESET));
        // To listen for device unlocks
        registerReceiver(deviceLockStatus, new IntentFilter(Intent.ACTION_USER_PRESENT));

        locksIntent = new Intent(ButtonReceiver.BROADCAST_LOCKS_RESET);
        updateUi = new Intent(UPDATE_UI);
        updateServiceStatus = new Intent(UPDATE_SERVICE_STATUS);

        PreferenceManager.setDefaultValues(this, R.xml.pref_main, false);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean startedOnBoot = preferences.getBoolean(getString(R.string.pref_key_auto_start), false);
        boolean canStartAlarm = preferences.getBoolean(getString(R.string.pref_key_auto_log_data), true);


        if (canStartAlarm) {
            // Starts the alarm when the service starts.
            // It's started here for code convenience and to ensure the
            // alarm fires on (or close to) the right time each day.
            Log.i(TAG, "Setting AutoInsert alarm for " + TimeConstants.HOUR + ":" + TimeConstants.MINUTE);

            Intent alarmIntent = new Intent(this, AutoInsertReceiver.class);
            alarmIntent.putExtra(BROADCAST_TAG, 101);
            PendingIntent alarmPendingIntent = PendingIntent.getBroadcast(this, 101, alarmIntent, 0);

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            // Sets the alarm to start at 11:55 PM or 23:55
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR_OF_DAY, TimeConstants.HOUR);
            calendar.set(Calendar.MINUTE, TimeConstants.MINUTE);

            if (alarmManager != null) {
                alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        TimeConstants.INTERVAL,
                        alarmPendingIntent);
            }
        } else {
            Log.i(TAG, "AutoInsert alarm not enabled...");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand...");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Make sure the tile listens for updates
            LockTileService.requestListeningState(this, new ComponentName(this, LockTileService.class));
        }

        notificationEnabled = preferences.getBoolean(getString(R.string.pref_key_notification), true);

        // This broadcast makes sure the FAB updates in the MainActivity
        sendBroadcast(updateServiceStatus);

        numberOfUnlocksPref = getSharedPreferences(getString(R.string.key_num_unlocks), MODE_PRIVATE);
        numberOfUnlocks = numberOfUnlocksPref.getInt(getString(R.string.key_num_unlocks), 0);
        notificationTextDefault = getString(R.string.notification_title);
        showInStatusBar = preferences.getBoolean(getString(R.string.pref_key_notification_priority), true);

        // Intent for the stop button
        Intent stopButtonIntent = new Intent(this, ButtonReceiver.class);
        stopButtonIntent.putExtra(NOTIFICATION_TAG, 100);
        PendingIntent stopService = PendingIntent.getBroadcast(this, 100, stopButtonIntent, 0);

        // Adds the intent to open the MainActivity reset dialog when the
        // reset button is clicked
        Intent resetButtonIntent = new Intent(this, MainActivity.class);
        resetButtonIntent.putExtra(NOTIFICATION_TAG, 200);
        TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(this);
        taskStackBuilder.addParentStack(MainActivity.class);
        taskStackBuilder.addNextIntent(resetButtonIntent);
        PendingIntent resetCounter = taskStackBuilder.getPendingIntent(200, PendingIntent.FLAG_UPDATE_CURRENT);

        // Intent for the settings button
        Intent settingsIntent = new Intent(this, ButtonReceiver.class);
        settingsIntent.putExtra(NOTIFICATION_TAG, 300);
        PendingIntent openSettings = PendingIntent.getBroadcast(this, 300, settingsIntent, 0);

        mBuilder = new NotificationCompat.Builder(this);
        mBuilder.addAction(R.drawable.ic_notification_close, "STOP", stopService);
        mBuilder.addAction(R.drawable.ic_notification_reset, "RESET", resetCounter);
        mBuilder.addAction(R.drawable.ic_notification_settings, "SETTINGS", openSettings);

        mBuilder.setOngoing(true); //The notification can't be swiped away
        mBuilder.setSmallIcon(R.drawable.ic_notification_lock);

        // Because grammar is important
        if (numberOfUnlocks == 0) {
            mBuilder.setContentText(getString(R.string.notification_title));
        } else if (numberOfUnlocks == 1) {
            mBuilder.setContentText(getString(R.string.notification_unlocks_singular, numberOfUnlocks));
        } else {
            mBuilder.setContentText(getString(R.string.notification_unlocks_plural, numberOfUnlocks));
        }
        if (showInStatusBar) {
            // The notification will be shown on the lock screen and in the status bar
            mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
        } else {
            // The notification won't be shown on the lock screen or status bar
            mBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
        }
        mBuilder.setContentTitle("LockCount");
        mBuilder.setColor(getResources().getColor(R.color.colorPrimaryDark));
        mBuilder.setShowWhen(false); // Don't show the time when the notification appeared

        //Intent to open the MainActivity when the notification is clicked
        Intent resultIntent = new Intent(this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // Only show the notification if the setting is enabled
        if (notificationEnabled) {
            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy...");
        unregisterReceiver(resetLocks);
        unregisterReceiver(deviceLockStatus);

        mNotificationManager.cancelAll();

        // Since it doesn't make since to keep the alarm when the
        // service is no longer running.
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, LockService.class);
        PendingIntent pIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        if (alarm != null) alarm.cancel(pIntent);
        // Make sure the MainActivity knows that the service has stopped
        // and to update the floating action button
        sendBroadcast(updateServiceStatus);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Make sure the tile knows to update
            LockTileService.requestListeningState(this, new ComponentName(this, LockTileService.class));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Listens for device lock/unlock
    private BroadcastReceiver deviceLockStatus = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null) {
                boolean isPhoneLocked = keyguardManager.inKeyguardRestrictedInputMode();
                if (!isPhoneLocked) {
                    numberOfUnlocks = numberOfUnlocksPref.getInt(getString(R.string.key_num_unlocks), 0);
                    updateUnlockCount();
                    Log.i(TAG, "Number of unlocks: " + numberOfUnlocks);
                }
            }
        }
    };


    private void updateUnlockCount() {
        numberOfUnlocks++;
        editor = numberOfUnlocksPref.edit();
        editor.putInt(getString(R.string.key_num_unlocks), numberOfUnlocks);
        editor.apply();

        // Again, because grammar is important...
        if (numberOfUnlocks == 1) {
            mBuilder.setContentText(getString(R.string.notification_unlocks_singular, numberOfUnlocks));
        } else {
            mBuilder.setContentText(getString(R.string.notification_unlocks_plural, numberOfUnlocks));
        }
        mBuilder.setColor(getResources().getColor(R.color.colorPrimaryDark));
        if (notificationEnabled) {
            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        }

        updateUi.putExtra("number", String.valueOf(numberOfUnlocks));
        sendBroadcast(updateUi);
    }

    // Used to make sure the text in the notification updates
    private BroadcastReceiver resetLocks = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "resetLocks triggered");
            // Reset the number of unlocks to 0
            SharedPreferences numberOfLocksPref = context.getSharedPreferences(context.getString(R.string.key_num_unlocks), Context.MODE_PRIVATE);
            SharedPreferences.Editor editor;
            editor = numberOfLocksPref.edit();
            editor.putInt(context.getString(R.string.key_num_unlocks), 0);
            editor.apply();

            numberOfUnlocks = numberOfLocksPref.getInt(getString(R.string.key_num_unlocks), 0);
            updateUi.putExtra("number", String.valueOf(numberOfUnlocks));
            sendBroadcast(updateUi); // Make sure the UI in the MainActivity updates

            LockService.mBuilder.setContentText(getString(R.string.notification_title));
            if (notificationEnabled) {
                LockService.mNotificationManager.notify(LockService.NOTIFICATION_ID, LockService.mBuilder.build());
            }
        }
    };
}
