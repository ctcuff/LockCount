package com.camtech.android.lockcount.activities;

import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.camtech.android.lockcount.R;
import com.camtech.android.lockcount.data.DBHelper;
import com.camtech.android.lockcount.data.LockContract.LockEntry;
import com.camtech.android.lockcount.data.LockedCursorAdapter;
import com.camtech.android.lockcount.data.TimeConstants;
import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.ontbee.legacyforks.cn.pedant.SweetAlert.SweetAlertDialog;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

import es.dmoral.toasty.Toasty;

/**
 * Displays unlock data. This doesn't handle displaying
 * any graph data. Graphing is done through {@link GraphActivity}
 */

public class LockDataView extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String TAG = LockDataView.class.getSimpleName();
    public static final String SORT_ORDER = "sortOrder";
    SharedPreferences tutorialPref;
    SharedPreferences numberOfUnlocksPref;
    SharedPreferences preferences;

    SharedPreferences.Editor editor;
    boolean canShowTutorial;
    boolean isAutoInsertEnabled;
    int numberOfUnlocks;
    int numUnlocksInDatabase;
    String currentDate;
    String currentDateFormatted;
    String lastDateInDatabase;
    AlertDialog alertDialog;

    ListView dataListView;
    FloatingActionButton fab;
    LockedCursorAdapter cursorAdapter;
    private static final int LOADER_ID = 0;
    private Toast mToast = null;

    String orderBy = null;

    final String LOCKS_ASCENDING = LockEntry.COLUMN_NUMBER_OF_UNLOCKS + " ASC";
    final String LOCKS_DESCENDING = LockEntry.COLUMN_NUMBER_OF_UNLOCKS + " DESC";
    final String DATE_OLDEST = LockEntry._ID + " ASC";
    final String DATE_MOST_RECENT = LockEntry._ID + " DESC";

    /**
     * Don't forget to change itemSelected if this value has changed
     * See {@link #showSortDialog()#itemSelected}
     */
    final String DEFAULT_SORT_ORDER = DATE_MOST_RECENT;

    final String[] DEFAULT_PROJECTION = {
            LockEntry._ID,
            LockEntry.COLUMN_NUMBER_OF_UNLOCKS,
            LockEntry.COLUMN_DATE_LONG,
            LockEntry.COLUMN_DATE_SHORT};

    @Override

    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate: ");
        setContentView(R.layout.layout_data);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        setTitle("History");

        fab = findViewById(R.id.fab_data);
        dataListView = findViewById(R.id._list);
        View emptyView = findViewById(R.id.empty_view);

        cursorAdapter = new LockedCursorAdapter(this, null);
        dataListView.setAdapter(cursorAdapter);
        dataListView.setEmptyView(emptyView);

        PreferenceManager.setDefaultValues(this, R.xml.pref_main, false);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        isAutoInsertEnabled = preferences.getBoolean(getString(R.string.pref_key_auto_log_data), true);
        // Used to make sure the tutorial is only shown once
        tutorialPref = getSharedPreferences(getString(R.string.key_show_main_tutorial), MODE_PRIVATE);
        canShowTutorial = tutorialPref.getBoolean(getString(R.string.key_show_main_tutorial), true);

        if (canShowTutorial) {
            showTutorial();
        }
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                insertData();
            }
        });
        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Cursor cursor = getContentResolver().query(
                        LockEntry.CONTENT_URI,
                        null,
                        null,
                        null,
                        null);
                try {
                    // No point in showing the dialog if the cursor is empty
                    if ((cursor != null ? cursor.getCount() : 0) < 1 || cursor == null) {
                        return true;
                    } else {
                        deleteAllEntries();
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                return true;
            }
        });

        dataListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, final long id) {
                new SweetAlertDialog(LockDataView.this, SweetAlertDialog.WARNING_TYPE)
                        .setTitleText("Delete Entry")
                        .setContentText("Are you sure you want to delete this entry? " +
                                "This action can't be undone")
                        .setCancelText("NO")
                        .setConfirmText("YES")
                        .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                            @Override
                            public void onClick(SweetAlertDialog sweetAlertDialog) {
                                getContentResolver().delete(Uri.withAppendedPath(LockEntry.CONTENT_URI, String.valueOf(id)), null, null);
                                Toasty.success(getBaseContext(), "Entry deleted", Toast.LENGTH_SHORT).show();
                                sweetAlertDialog.dismiss();
                            }
                        }).show();
                return true;
            }
        });

        getSupportLoaderManager().initLoader(LOADER_ID, null, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Only show the FAB button if the preference to insert
        // data automatically is disabled
        isAutoInsertEnabled = preferences.getBoolean(getString(R.string.pref_key_auto_log_data), true);
        if (isAutoInsertEnabled) fab.hide();
        else fab.show();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_history, menu);
        return true;
    }

    private void showTutorial() {
        SweetAlertDialog sDialog = new SweetAlertDialog(LockDataView.this, SweetAlertDialog.CUSTOM_IMAGE_TYPE);
        sDialog.setCancelable(false);
        sDialog.setTitleText("Unlock History");
        sDialog.setCustomImage(R.drawable.ic_dialog_lock_open);
        sDialog.setContentText(getString(R.string.unlock_history_tutorial_1));
        sDialog.setConfirmText("NEXT");
        sDialog.setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
            @Override
            public void onClick(SweetAlertDialog sweetAlertDialog) {
                sweetAlertDialog.dismiss();
                SweetAlertDialog sDialog = new SweetAlertDialog(LockDataView.this, SweetAlertDialog.CUSTOM_IMAGE_TYPE);
                sDialog.setCancelable(false);
                sDialog.setTitleText("Unlock History");
                sDialog.setContentText(getString(R.string.unlock_history_tutorial_2));
                sDialog.setCustomImage(R.drawable.ic_dialog_lock_open);
                sDialog.setConfirmText("NEXT");
                sDialog.setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sweetAlertDialog) {
                        sweetAlertDialog.dismiss();
                        showTapTarget();
                    }
                }).show();
            }
        }).show();
    }

    private void showTapTarget() {
        fab.show();
        final int TITLE_TEXT_SIZE = 20;
        final int DESC_TEXT_SIZE = 15;
        new TapTargetSequence(this)
                .targets(
                        TapTarget.forView(findViewById(R.id.fab_data), "Manual Add",
                                "Press this to add to your history")
                                .titleTextSize(TITLE_TEXT_SIZE)
                                .descriptionTextSize(DESC_TEXT_SIZE)
                                .cancelable(false)
                                .tintTarget(false),
                        TapTarget.forView(findViewById(R.id.fab_data), "Delete All Entries",
                                "Hold it down to delete all entries. Careful, this can't be undone!")
                                .titleTextSize(TITLE_TEXT_SIZE)
                                .descriptionTextSize(DESC_TEXT_SIZE)
                                .cancelable(false)
                                .tintTarget(false)
                ).listener(new TapTargetSequence.Listener() {
            @Override
            public void onSequenceFinish() {
                fab.hide();
                SweetAlertDialog sDialog = new SweetAlertDialog(LockDataView.this, SweetAlertDialog.CUSTOM_IMAGE_TYPE);
                sDialog.setCancelable(false);
                sDialog.setCustomImage(R.drawable.ic_dialog_rotate);
                sDialog.setTitleText("Graph View");
                sDialog.setContentText(getString(R.string.unlock_history_tutorial_3));
                sDialog.setConfirmText("GOT IT").setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sweetAlertDialog) {
                        // Make sure the tutorial isn't shown again
                        editor = tutorialPref.edit();
                        editor.putBoolean(getString(R.string.key_show_main_tutorial), false).apply();
                        sweetAlertDialog.dismiss();
                        finish();
                    }
                }).show();
            }

            @Override
            public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
            }

            @Override
            public void onSequenceCanceled(TapTarget lastTarget) {
            }
        }).start();

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sort_order:
                // Prevent multiple alert dialogs from opening if clicked more than once
                if (alertDialog == null) showSortDialog();
                return true;
            case R.id.settings_history:
                // Tell the SettingsActivity that it was opened through LockDataView.
                // This is to show the Toasty.info message when the 'Automatically add
                // data setting' is toggled.
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.putExtra(TAG, true);
                startActivity(intent);
                return true;
            case android.R.id.home:
                finish();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This is where the {@link GraphActivity} is started
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Intent intent = new Intent(this, GraphActivity.class);
            // Pass on the sort order to the graph
            intent.putExtra(SORT_ORDER, orderBy);
            Log.i(TAG, "onConfigurationChanged: sortOrder: " + orderBy);
            startActivity(intent);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Load the default sort order if no order is specified
        if (orderBy == null) orderBy = DEFAULT_SORT_ORDER;

        return new CursorLoader(this, LockEntry.CONTENT_URI, DEFAULT_PROJECTION, null, null, orderBy);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        cursorAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        cursorAdapter.swapCursor(null);
    }

    private void insertData() {
        // We only care about checking the long date and number of unlocks.
        // The short date could be check as well but only one is checked for convenience.
        String[] columns = {LockEntry.COLUMN_NUMBER_OF_UNLOCKS, LockEntry.COLUMN_DATE_LONG};

        Cursor c = null;
        DBHelper dbHelper = null;
        try {
            // Query the database to extract the last saved data and number of unlocks
            dbHelper = new DBHelper(this);
            c = dbHelper.getReadableDatabase().query(
                    LockEntry.TABLE_NAME,
                    columns,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            if (c.moveToLast()) {
                Log.i(TAG, "MOVING CURSOR TO LAST");
                int dateColumnIndex = c.getColumnIndex(LockEntry.COLUMN_DATE_LONG);
                int numLockColumnIndex = c.getColumnIndex(LockEntry.COLUMN_NUMBER_OF_UNLOCKS);
                lastDateInDatabase = c.getString(dateColumnIndex);
                numUnlocksInDatabase = c.getInt(numLockColumnIndex);

                Log.i(TAG, "Number of locks in database: " + numUnlocksInDatabase);
                Log.i(TAG, "Last date in database: " + lastDateInDatabase);
            } else {
                Log.i(TAG, "CURSOR NOT MOVED");
                lastDateInDatabase = null;
                numUnlocksInDatabase = 0;
                Log.i(TAG, "Number of locks in database: " + numUnlocksInDatabase);
                Log.i(TAG, "Last date in database: " + lastDateInDatabase);
            }
        } finally {
            if (c != null) {
                c.close();
            }
            if (dbHelper != null) {
                dbHelper.close();
            }
        }

        numberOfUnlocksPref = getSharedPreferences(getString(R.string.key_num_unlocks), MODE_PRIVATE);
        numberOfUnlocks = numberOfUnlocksPref.getInt(getString(R.string.key_num_unlocks), 0);

        //Initializes this Date instance to the current time.
        Date now = Calendar.getInstance().getTime();

        // Convert current time to String using specified formats
        // Formatted as: Wednesday, December 13, 2017
        SimpleDateFormat longDate = new SimpleDateFormat("EEE, MMMM d, yyyy", getResources().getConfiguration().locale);
        // Formatted as: 12/13
        SimpleDateFormat shortDate = new SimpleDateFormat("M/d", getResources().getConfiguration().locale);
        currentDate = longDate.format(now);
        currentDateFormatted = shortDate.format(now);

        // No need to add to the database if the number of unlocks is 0
        if (numberOfUnlocks != 0) {
            final ContentValues values = new ContentValues();
            // Again, no need to add to the db if the number or date hasn't changed.
            // If the number is different and the date is different,
            // then the number can be inserted.
            if (numberOfUnlocks != numUnlocksInDatabase && !currentDate.equalsIgnoreCase(lastDateInDatabase)) {
                Log.i(TAG, "-");
                Log.i(TAG, "Current date: " + currentDate);
                Log.i(TAG, "Number of unlocks today: " + numberOfUnlocks);
                values.put(LockEntry.COLUMN_NUMBER_OF_UNLOCKS, numberOfUnlocks);
                values.put(LockEntry.COLUMN_DATE_LONG, currentDate);
                values.put(LockEntry.COLUMN_DATE_SHORT, currentDateFormatted);
                Uri newUri = getContentResolver().insert(LockEntry.CONTENT_URI, values);
                Log.i(TAG, String.valueOf(newUri));
                Log.i(TAG, "-");
            } else if (numberOfUnlocks == numUnlocksInDatabase && currentDate.equalsIgnoreCase(lastDateInDatabase)) {
                // The number of unlocks hasn't changed and the date hasn't
                // changed so only add the data if the user wants to.
                new SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                        .setTitleText("Duplicate Entry")
                        .setContentText("You've already added this number, do you want to add it again?")
                        .setCancelText("CANCEL")
                        .setConfirmText("YES").setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sweetAlertDialog) {
                        Log.i(TAG, "-");
                        Log.i(TAG, "Current date: " + currentDate);
                        Log.i(TAG, "Number of unlocks today: " + numberOfUnlocks);
                        values.put(LockEntry.COLUMN_NUMBER_OF_UNLOCKS, numberOfUnlocks);
                        values.put(LockEntry.COLUMN_DATE_LONG, currentDate);
                        values.put(LockEntry.COLUMN_DATE_SHORT, currentDateFormatted);
                        Uri newUri = getContentResolver().insert(LockEntry.CONTENT_URI, values);
                        Log.i(TAG, String.valueOf(newUri));
                        Log.i(TAG, "-");
                        sweetAlertDialog.dismiss();
                    }
                }).show();

            } else if (numberOfUnlocks != numUnlocksInDatabase && currentDate.equalsIgnoreCase(lastDateInDatabase)) {
                // The number of unlocks has changed but the date hasn't so don't auto add the data.
                // Even though it doesn't really make sense to add the same date multiple times,
                // again, the user should be able to choose
                new SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                        .setTitleText("Duplicate Date")
                        .setContentText("You've already added to your history today, are you sure" +
                                " you want to add again?")
                        .setCancelText("CANCEL")
                        .setConfirmText("YES").setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sweetAlertDialog) {
                        Log.i(TAG, "-");
                        Log.i(TAG, "Current date: " + currentDate);
                        Log.i(TAG, "Number of unlocks today: " + numberOfUnlocks);
                        values.put(LockEntry.COLUMN_NUMBER_OF_UNLOCKS, numberOfUnlocks);
                        values.put(LockEntry.COLUMN_DATE_LONG, currentDate);
                        values.put(LockEntry.COLUMN_DATE_SHORT, currentDateFormatted);
                        Uri newUri = getContentResolver().insert(LockEntry.CONTENT_URI, values);
                        Log.i(TAG, String.valueOf(newUri));
                        Log.i(TAG, "-");
                        sweetAlertDialog.dismiss();
                    }
                }).show();
            }
        } else {
            // No zeroes should be inserted
            if (mToast != null) mToast.cancel();
            mToast = Toast.makeText(this, "You need at least 1 unlock to add data", Toast.LENGTH_SHORT);
            mToast.show();
        }
    }

    int itemSelected = 2; // Used to keep track of what radio button was selected

    private void showSortDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] items = {"Unlocks ascending", "Unlocks descending", "Date (most recent)", "Date (oldest)"};
        builder.setTitle(Html.fromHtml("<b>Sort by</b>"));
        builder.setCancelable(false);
        builder.setSingleChoiceItems(items, itemSelected, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        itemSelected = 0;
                        orderBy = LOCKS_ASCENDING;
                        // Make sure the UI updates
                        new CursorLoader(getBaseContext(), LockEntry.CONTENT_URI, DEFAULT_PROJECTION, null, null, orderBy);
                        getSupportLoaderManager().restartLoader(LOADER_ID, null, LockDataView.this);
                        break;
                    case 1:
                        itemSelected = 1;
                        orderBy = LOCKS_DESCENDING;
                        new CursorLoader(getBaseContext(), LockEntry.CONTENT_URI, DEFAULT_PROJECTION, null, null, orderBy);
                        getSupportLoaderManager().restartLoader(LOADER_ID, null, LockDataView.this);
                        break;
                    case 2:
                        itemSelected = 2;
                        orderBy = DATE_MOST_RECENT;
                        new CursorLoader(getBaseContext(), LockEntry.CONTENT_URI, DEFAULT_PROJECTION, null, null, orderBy);
                        getSupportLoaderManager().restartLoader(LOADER_ID, null, LockDataView.this);
                        break;
                    case 3:
                        itemSelected = 3;
                        orderBy = DATE_OLDEST;
                        new CursorLoader(getBaseContext(), LockEntry.CONTENT_URI, DEFAULT_PROJECTION, null, null, orderBy);
                        getSupportLoaderManager().restartLoader(LOADER_ID, null, LockDataView.this);
                }
                dialog.dismiss();
                alertDialog = null; // Makes sure multiple dialogs can't be opened
            }
        });
        builder.setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                alertDialog = null;
            }
        });
        alertDialog = builder.create();
        if (!alertDialog.isShowing()) alertDialog.show();
    }

    private void deleteAllEntries() {
        new SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                .setTitleText("Delete History")
                .setContentText("Are you sure you want to delete your history? This can't be undone.")
                .setCancelText("CANCEL")
                .setConfirmText("YES").setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
            @Override
            public void onClick(SweetAlertDialog sweetAlertDialog) {
                sweetAlertDialog.dismiss();
                Toasty.success(LockDataView.this, "History cleared", Toast.LENGTH_SHORT).show();
                int rowsDeleted = getContentResolver().delete(LockEntry.CONTENT_URI, null, null);
                Log.i(TAG, "Rows deleted: " + rowsDeleted);
            }
        }).show();

    }

    /**
     * Used to insert fake data. Useful for testing the
     * graph or adding data to the database in bulk in one go.
     */
    private void insertDummyData() {
        Random random = new Random();
        ContentValues values = new ContentValues();

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat longDate = new SimpleDateFormat("EEE, MMMM d, yyyy", getResources().getConfiguration().locale);
        SimpleDateFormat shortDate = new SimpleDateFormat("M/d", getResources().getConfiguration().locale);

        int numberOfEntries = 500; // The number of entries to be inserted
        int maxValue = 500; // The max value of an entry
        // A loop to insert dates into the database.
        // If you decide to insert 31 entries, you might
        // want to set your device to the first date
        // of a month with 31 days, because it'll look pretty
        for (int i = 1; i <= numberOfEntries; i++) {
            Date now = cal.getTime();
            currentDate = longDate.format(now);
            currentDateFormatted = shortDate.format(now);
            // Insert random numbers making sure no zeroes get added
            values.put(LockEntry.COLUMN_NUMBER_OF_UNLOCKS, random.nextInt(maxValue) + 1);
            values.put(LockEntry.COLUMN_DATE_LONG, currentDate);
            values.put(LockEntry.COLUMN_DATE_SHORT, currentDateFormatted);
            Uri newUri = getContentResolver().insert(LockEntry.CONTENT_URI, values);

            // Increment the date
            // Comment out this line if you only want to use a single date
            // cal.add(Calendar.DATE, 1);

            Log.i(TAG, "Uri " + i + ":" + " " + String.valueOf(newUri));
        }
    }
}