package com.camtech.android.lockcount.data;

import com.camtech.android.lockcount.receivers.AutoInsertReceiver;

/**
 * Used to store default values for setting the
 * time of the {@link AutoInsertReceiver} alarm
 */

public final class TimeConstants {

    // Prevent the class from being instantiated
    private TimeConstants() {}

    public static final int INTERVAL = 86400000;  // Same as 24 hours
    public static final int HOUR = 23;            // Triggers the alarm to start at 11:55 PM
    public static final int MINUTE = 55;

}
