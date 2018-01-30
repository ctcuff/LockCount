package com.camtech.android.lockcount.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.camtech.android.lockcount.data.LockContract.LockEntry;

/**
 * The database is created here
 * */

public class DBHelper extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "locks.db";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        String SQL_CREATE_TABLE = "CREATE TABLE " + LockEntry.TABLE_NAME + " ("
                + LockEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + LockEntry.COLUMN_NUMBER_OF_UNLOCKS + " INTEGER NOT NULL DEFAULT 0, "
                + LockEntry.COLUMN_DATE_LONG + " TEXT, "
                + LockEntry.COLUMN_DATE_SHORT + " TEXT);";

        db.execSQL(SQL_CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {


    }
}
