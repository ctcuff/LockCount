package com.camtech.android.lockcount.receivers;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.camtech.android.lockcount.R;
import com.camtech.android.lockcount.data.DBHelper;
import com.camtech.android.lockcount.data.LockContract;
import com.camtech.android.lockcount.data.LockContract.LockEntry;
import com.camtech.android.lockcount.services.LockService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Receiver to insert data into the database
 * at a set time automatically. The default insertion
 * time is 11:55 PM (23:55). This triggers every 24 hours
 * (every 86,400,000 ms).
 */

public class AutoInsertReceiver extends BroadcastReceiver {

    final String BROADCAST_TAG = "AutoInsertReceiver";
    final String TAG = AutoInsertReceiver.class.getSimpleName();
    String lastDateInDatabase;
    SharedPreferences.Editor editor;

    @Override
    public void onReceive(Context context, Intent intent) {
        int requestCode = intent.getIntExtra(BROADCAST_TAG, 0);
        Log.i(TAG, "Broadcast received...");
        switch (requestCode) {
            case 101:
                // We check the long date to make sure duplicate dates aren't added
                String[] dateColumn = {LockEntry.COLUMN_DATE_LONG, LockEntry.COLUMN_DATE_SHORT};

                Date date = Calendar.getInstance().getTime();

                SimpleDateFormat longDate = new SimpleDateFormat("EEE, MMMM d, yyyy", context.getResources().getConfiguration().locale);
                SimpleDateFormat shortDate = new SimpleDateFormat("M/d", context.getResources().getConfiguration().locale);

                String currentDateLong = longDate.format(date);
                String currentDateShort = shortDate.format(date);

                SharedPreferences numberOfUnlocksPref = context.getSharedPreferences(context.getString(R.string.key_num_unlocks), Context.MODE_PRIVATE);
                int numUnlocksToday = numberOfUnlocksPref.getInt(context.getString(R.string.key_num_unlocks), 0);
                Cursor cursor = null;
                DBHelper dbHelper = null;

                try {
                    dbHelper = new DBHelper(context);
                    cursor = dbHelper.getReadableDatabase().query(
                            LockEntry.TABLE_NAME,
                            dateColumn,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
                    // Extract the last saved date in the db
                    if (cursor.moveToLast()) {
                        cursor.moveToLast();
                        int dateColumnIndex = cursor.getColumnIndex(LockEntry.COLUMN_DATE_LONG);
                        lastDateInDatabase = cursor.getString(dateColumnIndex);
                    }

                    Log.i(TAG, "----------------------------------------------");
                    Log.i(TAG, "Last date in database: " + lastDateInDatabase);
                    Log.i(TAG, "Date added: " + currentDateLong);
                    Log.i(TAG, "Number of unlocks added: " + numUnlocksToday);
                    Log.i(TAG, "----------------------------------------------");

                    // Don't add data if the date hasn't changed or if the number of unlocks is zero
                    if (!currentDateLong.equalsIgnoreCase(lastDateInDatabase) && numUnlocksToday >= 1) {
                        ContentValues values = new ContentValues();
                        values.put(LockEntry.COLUMN_NUMBER_OF_UNLOCKS, numUnlocksToday);
                        values.put(LockEntry.COLUMN_DATE_LONG, currentDateLong);
                        values.put(LockEntry.COLUMN_DATE_SHORT, currentDateShort);
                        Uri newUri = context.getContentResolver().insert(LockContract.LockEntry.CONTENT_URI, values);
                        Log.i(TAG, "Data inserted successfully: " + String.valueOf(newUri));
                        Log.i(TAG, "Data added. Total unlocks today: " + numUnlocksToday);

                        // Reset the number of unlocks
                        editor = numberOfUnlocksPref.edit();
                        editor.putInt(context.getString(R.string.key_num_unlocks), 0);
                        editor.apply();

                        // Make sure the notification knows that the count was reset
                        context.sendBroadcast(new Intent(ButtonReceiver.BROADCAST_LOCKS_RESET));
                        Log.i(TAG, "Unlock count reset");
                    } else {
                        Log.i(TAG, "Data not added");
                    }
                } catch (Exception e) {
                    Log.i(TAG, "Error inserting data", e);
                } finally {
                    if (dbHelper != null) {
                        dbHelper.close();
                    }
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown intent with ID: " + intent.getIntExtra(LockService.NOTIFICATION_TAG, 0));
        }
    }
}