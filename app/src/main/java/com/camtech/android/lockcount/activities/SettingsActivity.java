package com.camtech.android.lockcount.activities;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.camtech.android.lockcount.BuildConfig;
import com.camtech.android.lockcount.R;
import com.camtech.android.lockcount.data.TimeConstants;
import com.camtech.android.lockcount.receivers.AutoInsertReceiver;
import com.camtech.android.lockcount.services.LockService;
import com.shawnlin.numberpicker.NumberPicker;

import java.util.Calendar;

import es.dmoral.toasty.Toasty;

/**
 * All of the apps settings and preferences are controlled here.
 */

public class SettingsActivity extends AppCompatPreferenceActivity {

    private static final String TAG = SettingsActivity.class.getSimpleName();

    static SettingsActivity instance;
    public static SharedPreferences alarmHourPref;
    public static SharedPreferences alarmMinutePref;
    public static SharedPreferences alarmMillisecondPref;
    public static final int ALARM_HOUR_DEFAULT = 0;
    public static final int ALARM_MINUTE_DEFAULT = 0;
    public static final int ALARM_MILLISECOND_DEFAULT = 0;

    public static int alarmHour;
    public static int alarmMinute;
    public static long alarmInMilliseconds;
    final static String BROADCAST_TAG = "AutoInsertReceiver";

    public static SwitchPreference refreshSwitch;

    private SettingsActivity() {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle("Settings");
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MainPreferenceFragment()).commit();

        alarmHourPref = getSharedPreferences(getString(R.string.key_alarm_hour), MODE_PRIVATE);
        alarmMinutePref = getSharedPreferences(getString(R.string.key_alarm_minute), MODE_PRIVATE);
        alarmMillisecondPref = getSharedPreferences(getString(R.string.key_alarm_millisecond), MODE_PRIVATE);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    public static class MainPreferenceFragment extends PreferenceFragment {
        SharedPreferences.Editor editor;
        PendingIntent alarmPendingIntent;

        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_main);

            // Set up the intent to start the AutoInsert alarm when the setting is toggled
            Intent alarmIntent = new Intent(getActivity(), AutoInsertReceiver.class);
            alarmIntent.putExtra(BROADCAST_TAG, 101);
            alarmPendingIntent = PendingIntent.getBroadcast(getActivity(), 101, alarmIntent, 0);

            alarmHour = alarmHourPref.getInt(getString(R.string.key_alarm_hour), ALARM_HOUR_DEFAULT);
            alarmMinute = alarmMinutePref.getInt(getString(R.string.key_alarm_minute), ALARM_MINUTE_DEFAULT);

            Preference appVersion = findPreference(getString(R.string.pref_key_app_version));
            appVersion.setSummary(BuildConfig.VERSION_NAME);

            final Preference refreshPref = findPreference(getString(R.string.pref_key_refresh));
            refreshPref.setSummary(getString(R.string.summary_refresh, alarmHour, alarmMinute));
            refreshPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showRefreshDialog();
                    return true;
                }
            });

            refreshSwitch = (SwitchPreference) findPreference(getString(R.string.pref_key_refresh_switch));
            refreshSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue.toString().equals("true")) {
                        refreshPref.setEnabled(true);
                        saveBoolean(getString(R.string.pref_key_refresh_switch), true);
                        if (alarmInMilliseconds >= 60000 && instance.isServiceRunning(LockService.class)) {
                            // No need to show this if the refresh interval is 0 or if the service isn't running
                            Toasty.info(getActivity(), "Please restart the service for this to take effect", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        refreshPref.setEnabled(false);
                        saveBoolean(getString(R.string.pref_key_refresh_switch), false);
                    }
                    return true;
                }
            });

            if (refreshSwitch.isChecked()) {
                refreshPref.setEnabled(true);
            } else {
                refreshPref.setEnabled(false);
            }

            SwitchPreference autoLogData = (SwitchPreference) findPreference(getString(R.string.pref_key_auto_log_data));
            autoLogData.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue.toString().equals("true")) {
                        // Set the alarm
                        Log.i(TAG, "Setting alarm...");

                        saveBoolean(getString(R.string.pref_key_auto_log_data), true);

                        AlarmManager alarmManager = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);

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
                        // Cancel the alarm
                        saveBoolean(getString(R.string.pref_key_auto_log_data), false);

                        AlarmManager manager = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
                        if (manager != null) {
                            manager.cancel(alarmPendingIntent);
                        }
                        if (getActivity().getIntent().getBooleanExtra(LockDataView.TAG, false)) {
                            // Only show this toast if the settings were opened through LockDataView
                            Toasty.info(getActivity(), "Press the + button to add data", Toast.LENGTH_SHORT).show();
                        }
                        Log.i(TAG, "Canceling alarm...");
                    }
                    return true;
                }
            });


            SwitchPreference autoStart = (SwitchPreference) findPreference(getString(R.string.pref_key_auto_start));
            autoStart.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue.toString().equals("true")) {
                        saveBoolean(getString(R.string.pref_key_auto_start), true);
                    } else {
                        saveBoolean(getString(R.string.pref_key_auto_start), true);
                    }
                    return true;
                }
            });

            final Preference notificationPriority = findPreference(getString(R.string.pref_key_notification_priority));
            notificationPriority.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue.toString().equals("true")) {
                        saveBoolean(getString(R.string.pref_key_notification_priority), true);
                        if (instance.isServiceRunning(LockService.class)) {
                            LockService.mNotificationManager.cancelAll();
                            LockService.mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
                            LockService.mNotificationManager.notify(LockService.NOTIFICATION_ID, LockService.mBuilder.build());
                        }
                    } else {
                        saveBoolean(getString(R.string.pref_key_notification_priority), false);
                        if (instance.isServiceRunning(LockService.class)) {
                            LockService.mNotificationManager.cancelAll();
                            LockService.mBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
                            LockService.mNotificationManager.notify(LockService.NOTIFICATION_ID, LockService.mBuilder.build());
                        }
                    }
                    return true;
                }
            });

            SwitchPreference showNotification = (SwitchPreference) findPreference(getString(R.string.pref_key_notification));
            showNotification.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue.toString().equals("true")) {
                        notificationPriority.setEnabled(true);
                        saveBoolean(getString(R.string.pref_key_notification), true);
                        if (instance.isServiceRunning(LockService.class)) {
                            LockService.mNotificationManager.notify(LockService.NOTIFICATION_ID, LockService.mBuilder.build());
                        }
                    } else {
                        notificationPriority.setEnabled(false);
                        saveBoolean(getString(R.string.pref_key_notification), false);
                        if (instance.isServiceRunning(LockService.class)) {
                            LockService.mNotificationManager.cancelAll();
                        }
                    }
                    return true;
                }
            });

            if (showNotification.isChecked()) {
                notificationPriority.setEnabled(true);
            } else {
                notificationPriority.setEnabled(false);
            }

            Preference sendFeedback = findPreference(getString(R.string.pref_key_send_feedback));
            sendFeedback.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    sendFeedback(getActivity());
                    return true;
                }
            });

            Preference moreApps = findPreference(getString(R.string.pref_key_more_apps));
            moreApps.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    try {
                        // Open in the Google Play if the user has it installed
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://dev?id=4843808087449577366")));
                    } catch (ActivityNotFoundException e) {
                        // Open in browser if the user doesn't have Google Play installed
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/dev?id=4843808087449577366")));
                    }
                    return true;
                }
            });

            Preference showTutorial = findPreference(getString(R.string.pref_key_show_tutorial));
            showTutorial.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    SharedPreferences mainTutorialPref =
                            getActivity().getSharedPreferences(getString(R.string.key_show_main_tutorial), MODE_PRIVATE);

                    editor = mainTutorialPref.edit();
                    editor.putBoolean(getString(R.string.key_show_main_tutorial), true).apply();

                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return true;
                }
            });

        }

        private void showRefreshDialog() {
            final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            final LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(LAYOUT_INFLATER_SERVICE);
            final View viewLayout = inflater.inflate(R.layout.refresh_dialog,
                    (ViewGroup) getActivity().findViewById(R.id.dialog_layout));

            builder.setView(viewLayout);

            final NumberPicker numberPickerHours = viewLayout.findViewById(R.id.number_picker_hours);
            final NumberPicker numberPickerMinutes = viewLayout.findViewById(R.id.number_picker_minutes);
            numberPickerHours.setValue(alarmHourPref.getInt(getString(R.string.key_alarm_hour), ALARM_HOUR_DEFAULT));
            numberPickerMinutes.setValue(alarmMinutePref.getInt(getString(R.string.key_alarm_minute), ALARM_MINUTE_DEFAULT));

            builder.setTitle("Refresh Interval");
            builder.setMessage("Enter the refresh interval in hours and minutes");
            builder.setPositiveButton("OK",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            editor = alarmHourPref.edit();
                            editor.putInt(getString(R.string.key_alarm_hour), numberPickerHours.getValue()).apply();

                            editor = alarmMinutePref.edit();
                            editor.putInt(getString(R.string.key_alarm_minute), numberPickerMinutes.getValue()).apply();

                            // 1 Hour = 3,600,000 Milliseconds
                            alarmHour = alarmHourPref.getInt(getString(R.string.key_alarm_hour), ALARM_HOUR_DEFAULT);
                            // 1 Minute = 60,000 Milliseconds
                            alarmMinute = alarmMinutePref.getInt(getString(R.string.key_alarm_minute), ALARM_MINUTE_DEFAULT);

                            editor = alarmMillisecondPref.edit();
                            editor.putLong(getString(R.string.key_alarm_millisecond), (long) ((alarmHour * 3600000) + (alarmMinute * 60000)));
                            editor.apply();

                            alarmInMilliseconds = alarmMillisecondPref.getLong(getString(R.string.key_alarm_millisecond), ALARM_MILLISECOND_DEFAULT);

                            Preference refreshPref = findPreference(getString(R.string.pref_key_refresh));
                            refreshPref.setSummary(getString(R.string.summary_refresh, alarmHour, alarmMinute));

                            // No need to show this toast if the refresh interval is 0 or if the service isn't running
                            if (alarmInMilliseconds >= 60000 && instance.isServiceRunning(LockService.class)) {
                                Toasty.info(getActivity(), "Please restart the service for this to take effect", Toast.LENGTH_LONG).show();
                            }
                        }

                    });
            builder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.create().show();
        }


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

    public static SharedPreferences getPref(String key) {
        return instance.getSharedPreferences(key, Context.MODE_PRIVATE);
    }

    public static void saveBoolean(String key, boolean value) {
        SharedPreferences.Editor editor = getPref(key).edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    public static void sendFeedback(Context context) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n\n-----------------------------")
                .append("\nPlease don't remove this information:")
                .append("\nDevice OS: Android version: ").append(Build.VERSION.RELEASE)
                .append("\nApp version: ").append(BuildConfig.VERSION_NAME)
                .append("\nDevice brand: ").append(Build.BRAND)
                .append("\nDevice model: ").append(Build.MODEL)
                .append("\nDevice manufacturer: ").append(Build.MANUFACTURER)
                .append("\n-----------------------------");
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // Only email apps
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"dev.ctcuff@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "LockCount Query");
        intent.putExtra(Intent.EXTRA_TEXT, builder.toString());
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.choose_email_client)));
    }
}