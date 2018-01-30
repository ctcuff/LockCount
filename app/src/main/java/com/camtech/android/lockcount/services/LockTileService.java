package com.camtech.android.lockcount.services;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import com.camtech.android.lockcount.R;

import java.util.Calendar;

/**
 * Controls the Quick Tile on devices running
 * Android Nougat (API 24) and up.
 */
@TargetApi(Build.VERSION_CODES.N)
public class LockTileService extends TileService {

    public static Tile tile;
    public static int newState;
    public static final String PREFERENCES_KEY = "tilePref";
    public static final String SERVICE_STATUS_FLAG = "serviceStatus";
    private static final String TAG = LockTileService.class.getSimpleName();

    @Override
    public void onClick() {
        updateTile();
    }

    @Override
    public void onTileAdded() {
    }


    @Override
    public void onTileRemoved() {

    }

    @Override
    public void onStopListening() {
        Log.i(TAG, "onStopListening...");
    }

    @Override
    public void onStartListening() {
        Log.i(TAG, "onStartListening...");
        tile = this.getQsTile();
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFERENCES_KEY, MODE_PRIVATE);
        if (!isServiceRunning(LockService.class)) {
            prefs.edit().putBoolean(SERVICE_STATUS_FLAG, false).apply();
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_lock_outline_black_24dp));
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
        } else {
            prefs.edit().putBoolean(SERVICE_STATUS_FLAG, true).apply();
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_lock_open_black_24dp));
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }
    }

    // Changes the appearance of the tile.
    public void updateTile() {
        tile = this.getQsTile();
        boolean isActive = getServiceStatus();
        boolean startWithAlarm;
        SharedPreferences alarmMillisecondPref = getSharedPreferences(getString(R.string.key_alarm_millisecond), 0);

        PreferenceManager.setDefaultValues(this, R.xml.pref_main, false);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        String newLabel;

        // Change the tile to match the service status.
        if (isActive) {
            newLabel = getString(R.string.tile_label);
            newState = Tile.STATE_ACTIVE;
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_lock_outline_black_24dp));

            if (!isServiceRunning(LockService.class)) {
                startWithAlarm = preferences.getBoolean(getString(R.string.pref_key_refresh_switch), false);
                long alarmInMilliseconds = alarmMillisecondPref.getLong(getString(R.string.key_alarm_millisecond), 0);
                // Start service with the alarm
                if (startWithAlarm && alarmInMilliseconds >= 60000) {
                    Calendar cal = Calendar.getInstance();
                    Intent intent = new Intent(this, LockService.class);
                    PendingIntent pIntent = PendingIntent.getService(this, 0, intent, 0);
                    AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    if (alarm != null) {
                        alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), alarmInMilliseconds, pIntent);
                    }
                    Log.i(TAG, "Starting service with an alarm of " + alarmInMilliseconds + "ms");
                    startService(new Intent(this, LockService.class));
                } else {
                    // Start service without alarm
                    Log.i(TAG, "Starting service without alarm");
                    startService(new Intent(this, LockService.class));
                }
            }

        } else {
            newLabel = getString(R.string.tile_label);
            newState = Tile.STATE_INACTIVE;
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_lock_open_black_24dp));
            if (isServiceRunning(LockService.class)) {
                stopService(new Intent(this, LockService.class));
            }
        }

        // Change the UI of the tile.
        tile.setLabel(newLabel);
        tile.setState(newState);

        // Need to call updateTile for the tile to pick up changes.
        tile.updateTile();
        Log.i(TAG, String.valueOf(isServiceRunning(LockService.class)));
    }


    // Access storage to see how many times the tile has been tapped.
    public boolean getServiceStatus() {

        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFERENCES_KEY, MODE_PRIVATE);

        boolean isActive = prefs.getBoolean(SERVICE_STATUS_FLAG, false);
        isActive = !isActive;

        prefs.edit().putBoolean(SERVICE_STATUS_FLAG, isActive).apply();

        return isActive;
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

}
