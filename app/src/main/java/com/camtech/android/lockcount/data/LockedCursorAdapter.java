package com.camtech.android.lockcount.data;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.camtech.android.lockcount.R;
import com.camtech.android.lockcount.activities.LockDataView;

/**
 * Custom adapter used to display the data in {@link LockDataView}
 */

public class LockedCursorAdapter extends CursorAdapter {

    public LockedCursorAdapter(Context context, Cursor c) {
        super(context, c, 0);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.list_item, parent, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        TextView numUnlocksTextView = view.findViewById(R.id.number_of_unlocks);
        TextView dateTextView = view.findViewById(R.id.date);

        int numUnlocksColumnIndex = cursor.getColumnIndex(LockContract.LockEntry.COLUMN_NUMBER_OF_UNLOCKS);
        int dateColumnIndex = cursor.getColumnIndex(LockContract.LockEntry.COLUMN_DATE_LONG);

        int numUnlocks = cursor.getInt(numUnlocksColumnIndex);
        String date = cursor.getString(dateColumnIndex);

        numUnlocksTextView.setText(context.getString(R.string.number_of_unlocks_cursor, numUnlocks));
        dateTextView.setText(date);
    }
}
