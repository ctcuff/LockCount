package com.camtech.android.lockcount.data;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;


public class LockContract {

    public static final String CONTENT_AUTHORITY = "com.camtech.android.lockcount";
    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);
    public static final  String PATH_LOCKS = "locks";

    private LockContract(){}

    public static final class LockEntry implements BaseColumns {

        public static final String CONTENT_LIST_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_LOCKS;

        public static final String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/" + CONTENT_AUTHORITY + "/" + PATH_LOCKS;

        public static final Uri CONTENT_URI = Uri.withAppendedPath(BASE_CONTENT_URI, PATH_LOCKS);

        public static final String TABLE_NAME = "lockdata";

        // The ID is used to keep track of the order of each entry.
        // This way, sorting by ID can be the same as sorting by date
        // allowing the date to be stored as a string
        public static final String COLUMN_ID = BaseColumns._ID;
        // Keeps track of the number of unlocks
        public static final String COLUMN_NUMBER_OF_UNLOCKS = "numlocks";
        // Example: Sat, December, 2, 2017 (used to show the date in the history)
        public static final String COLUMN_DATE_LONG = "date";
        // Example: 12/2 (used as labels for the graph)
        public static final String COLUMN_DATE_SHORT = "formattedDate";
    }
}
