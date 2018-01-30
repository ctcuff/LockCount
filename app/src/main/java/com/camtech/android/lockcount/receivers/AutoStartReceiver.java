package com.camtech.android.lockcount.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.camtech.android.lockcount.R;
import com.camtech.android.lockcount.services.LockService;

/**
 * This automatically starts the {@link LockService} when the device
 * reboots/powers on, but only if the setting is enabled
 */

public class AutoStartReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        PreferenceManager.setDefaultValues(context, R.xml.pref_main, false);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        boolean allowAutoStart = preferences.getBoolean(context.getString(R.string.pref_key_auto_start), false);

        if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED) && allowAutoStart) {
            // Start the service on device boot if allowed
            context.startService(new Intent(context, LockService.class));
        }

    }
}
