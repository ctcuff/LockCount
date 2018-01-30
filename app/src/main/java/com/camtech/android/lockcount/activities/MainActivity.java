package com.camtech.android.lockcount.activities;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.camtech.android.lockcount.data.TimeConstants;
import com.camtech.android.lockcount.receivers.AutoInsertReceiver;
import com.camtech.android.lockcount.receivers.ButtonReceiver;
import com.camtech.android.lockcount.services.LockService;
import com.camtech.android.lockcount.R;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;

import java.util.Calendar;

import com.ontbee.legacyforks.cn.pedant.SweetAlert.SweetAlertDialog;

import es.dmoral.toasty.Toasty;

/**
 * This controls the main UI of the app.
 * The alarm for {@link LockService} is started here.
 */

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final String BROADCAST_TAG = "AutoInsertReceiver";

    TextView counterTextView;
    TextView empty_counter;
    TextView counterValueTextView;
    FloatingActionButton fab;
    boolean startWithAlarm;
    boolean canShowTutorial;
    boolean canAutoInsertData;
    SharedPreferences.Editor editor;
    SharedPreferences numberOfUnlocksPref;
    SharedPreferences alarmMillisecondPref;
    SharedPreferences preferences;
    SharedPreferences tutorialPref;
    AlarmManager alarm;
    int numberOfUnlocks;
    int sequenceStep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fab = findViewById(R.id.fab);

        PreferenceManager.setDefaultValues(this, R.xml.pref_main, false);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        numberOfUnlocksPref = getSharedPreferences(getString(R.string.key_num_unlocks), MODE_PRIVATE);
        numberOfUnlocks = numberOfUnlocksPref.getInt(getString(R.string.key_num_unlocks), 0);
        canAutoInsertData = preferences.getBoolean(getString(R.string.pref_key_auto_log_data), true);

        // This only shows on a fresh install or upon an app reset
        showTutorial();

        IntentFilter updateUi = new IntentFilter();
        updateUi.addAction(ButtonReceiver.BROADCAST_LOCKS_RESET); // Listen for a reset
        updateUi.addAction(LockService.UPDATE_UI); // Makes sure the UI updates on device unlock

        // Receiver that updates unlock count on screen
        registerReceiver(displayLocksReceiver, updateUi);
        // Receiver that updates the isServiceRunning pref boolean
        registerReceiver(updateServiceStatus, new IntentFilter(LockService.UPDATE_SERVICE_STATUS));

        displayLockCounter(); // Make sure the number of unlocks updates when the app opens
        updateStatus(); // Called to make sure the FAB updates

        // This only opens when the reset button on the notification was clicked
        if (getIntent().getIntExtra(LockService.NOTIFICATION_TAG, 0) == 200 && numberOfUnlocks != 0) {
            showDeleteConfirmation();
        }

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!isServiceRunning(LockService.class)) {
                    startWithAlarm = preferences.getBoolean(getString(R.string.pref_key_refresh_switch), false);
                    long alarmInMilliseconds = alarmMillisecondPref.getLong(getString(R.string.key_alarm_millisecond), 0);
                    // Start service with the alarm
                    // No need to start it if the time specified is less that 1 minute
                    if (startWithAlarm && alarmInMilliseconds >= 60000) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTimeInMillis(System.currentTimeMillis());
                        Intent intent = new Intent(MainActivity.this, LockService.class);
                        PendingIntent pIntent = PendingIntent.getService(MainActivity.this, 0, intent, 0);
                        alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        // Sets the alarm to repeat.
                        // The interval is what the user specified in the settings
                        alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), alarmInMilliseconds, pIntent);
                        Log.i(TAG, "Starting service with a refresh alarm of " + alarmInMilliseconds + "ms");
                        startService(intent);

                    } else {
                        // Start service without alarm
                        Log.i(TAG, "Starting service without refresh alarm");
                        startService(new Intent(MainActivity.this, LockService.class));
                    }
                } else {
                    stopService(new Intent(MainActivity.this, LockService.class));
                }

            }
        });
        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (numberOfUnlocks != 0) {
                    showDeleteConfirmation();
                }
                return true;
            }
        });

        if (canAutoInsertData) {
            Log.i(TAG, "Setting alarm...");

            Intent alarmIntent = new Intent(MainActivity.this, AutoInsertReceiver.class);
            alarmIntent.putExtra(BROADCAST_TAG, 101);
            PendingIntent alarmPendingIntent = PendingIntent.getBroadcast(this, 101, alarmIntent, 0);

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            // Sets the alarm to start at this time
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
            Log.i(TAG, "Refresh alarm not enabled...");
        }

    }

    // Updates unlock count on screen
    private BroadcastReceiver displayLocksReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            displayLockCounter();
        }
    };

    // Makes sure the FAB updates when the service starts/stops
    private BroadcastReceiver
            updateServiceStatus = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateStatus();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        numberOfUnlocksPref = getSharedPreferences(getString(R.string.key_num_unlocks), MODE_PRIVATE);
        numberOfUnlocks = numberOfUnlocksPref.getInt(getString(R.string.key_num_unlocks), 0);
        alarmMillisecondPref = getSharedPreferences(getString(R.string.key_alarm_millisecond), 0);

        displayLockCounter();

    }

    private void displayLockCounter() {
        numberOfUnlocksPref = getSharedPreferences(getString(R.string.key_num_unlocks), MODE_PRIVATE);
        numberOfUnlocks = numberOfUnlocksPref.getInt(getString(R.string.key_num_unlocks), 0);

        counterTextView = findViewById(R.id.counter_tv);
        counterValueTextView = findViewById(R.id.counter_value);
        empty_counter = findViewById(R.id.zero_counter);
        if (numberOfUnlocks == 0) {
            counterTextView.setVisibility(View.INVISIBLE);
            counterValueTextView.setVisibility(View.INVISIBLE);
            empty_counter.setVisibility(View.VISIBLE);
        } else {
            counterTextView.setVisibility(View.VISIBLE);
            empty_counter.setVisibility(View.INVISIBLE);
            counterValueTextView.setVisibility(View.VISIBLE);
            counterValueTextView.setText(getString(R.string.number_of_unlocks, numberOfUnlocks));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(displayLocksReceiver);
        unregisterReceiver(updateServiceStatus);
    }

    private void showDeleteConfirmation() {
        new SweetAlertDialog(MainActivity.this, SweetAlertDialog.WARNING_TYPE)
                .setTitleText("Reset")
                .setContentText("Are you sure you want to reset the counter?")
                .setConfirmText("OK")
                .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog dialog) {
                        editor = numberOfUnlocksPref.edit();
                        editor.putInt(getString(R.string.key_num_unlocks), 0);
                        editor.apply();
                        // Make sure the notification knows to update
                        sendBroadcast(new Intent(ButtonReceiver.BROADCAST_LOCKS_RESET));
                        Toasty.success(MainActivity.this, "Unlocks reset", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                }).setCancelText("CANCEL").show();
    }

    private void updateStatus() {
        if (isServiceRunning(LockService.class)) {
            fab.setImageResource(R.drawable.ic_fab_pause);
        } else {
            fab.setImageResource(R.drawable.ic_fab_play);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings_main:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.settings_view_logs:
                startActivity(new Intent(this, LockDataView.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
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

    private void showTutorial() {
        tutorialPref = getSharedPreferences(getString(R.string.key_show_main_tutorial), MODE_PRIVATE);
        canShowTutorial = tutorialPref.getBoolean(getString(R.string.key_show_main_tutorial), true);
        if (canShowTutorial) {
            sequenceStep = 0;
            final int TITLE_TEXT_SIZE = 20;
            final int DESC_TEXT_SIZE = 15;
            new TapTargetSequence(this)
                    .targets(
                            TapTarget.forView(findViewById(R.id.fab), "Start", "Press this button to start counting")
                                    .titleTextSize(TITLE_TEXT_SIZE)
                                    .descriptionTextSize(DESC_TEXT_SIZE)
                                    .cancelable(false)
                                    .tintTarget(false),
                            TapTarget.forView(findViewById(R.id.fab), "Stop", "Press it again to stop")
                                    .titleTextSize(TITLE_TEXT_SIZE)
                                    .descriptionTextSize(DESC_TEXT_SIZE)
                                    .cancelable(false)
                                    .tintTarget(false),
                            TapTarget.forView(findViewById(R.id.fab), "Reset", "Hold it down to reset the counter")
                                    .titleTextSize(TITLE_TEXT_SIZE)
                                    .descriptionTextSize(DESC_TEXT_SIZE)
                                    .cancelable(false)
                                    .tintTarget(false)
                    ).listener(new TapTargetSequence.Listener() {
                @Override
                public void onSequenceFinish() {
                    final SweetAlertDialog sDialog = new SweetAlertDialog(MainActivity.this, SweetAlertDialog.CUSTOM_IMAGE_TYPE);
                    sDialog.setCancelable(false);
                    sDialog.setTitleText("View History");
                    sDialog.setContentText("Press this button to view your unlock history");
                    sDialog.setCustomImage(R.drawable.ic_dialog_lock_open);
                    sDialog.setConfirmText("GOT IT").setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                        @Override
                        public void onClick(SweetAlertDialog sweetAlertDialog) {
                            sweetAlertDialog.dismiss();
                            startActivity(new Intent(MainActivity.this, LockDataView.class));
                        }
                    });
                    sDialog.show();
                }

                @Override
                public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                    sequenceStep++;
                    if (sequenceStep == 1) {
                        fab.setImageResource(R.drawable.ic_fab_pause);
                    } else if (sequenceStep == 2) {
                        fab.setImageResource(R.drawable.ic_fab_play);
                    }
                }

                @Override
                public void onSequenceCanceled(TapTarget lastTarget) {
                }
            }).start();
        }
    }
}
