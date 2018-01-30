package com.camtech.android.lockcount.activities;

import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.camtech.android.lockcount.R;
import com.camtech.android.lockcount.data.DBHelper;
import com.camtech.android.lockcount.data.LockContract.LockEntry;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IValueFormatter;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.ArrayList;

/**
 * Everything to do with controlling the graph happens here.
 * Note that this activity is only started from
 * {@link LockDataView#onConfigurationChanged(Configuration)}
 * upon device rotation to landscape
 */

public class GraphActivity extends AppCompatActivity {

    private final String TAG = GraphActivity.class.getSimpleName();
    BarChart chart;
    ImageView emptyGraphImage;
    TextView emptyGraphText;
    private Toast mToast = null;
    final float TEXT_SIZE = 14f;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        if (Build.VERSION.SDK_INT < 16) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        // Make the status bar transparent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION, WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        Cursor cursor = null;
        DBHelper dbHelper = null;

        emptyGraphImage = findViewById(R.id.graph_icon);
        emptyGraphImage.setVisibility(View.GONE);
        emptyGraphText = findViewById(R.id.graph_text);
        emptyGraphText.setVisibility(View.GONE);


        chart = findViewById(R.id.chart);

        // We only care about displaying the number of unlocks and
        // the short date (12/4 for example).
        // The number of unlocks is shown above each bar,
        // the date is shown underneath each bar.
        String[] dateColumn = {LockEntry.COLUMN_NUMBER_OF_UNLOCKS, LockEntry.COLUMN_DATE_SHORT};

        // Get the sort order from LockDataView
        String orderBy = getIntent().getStringExtra(LockDataView.SORT_ORDER);
        try {
            dbHelper = new DBHelper(this);
            cursor = dbHelper.getReadableDatabase().query(
                    LockEntry.TABLE_NAME,
                    dateColumn,
                    null,
                    null,
                    null,
                    null,
                    orderBy,
                    null);
            ArrayList<BarEntry> entries = new ArrayList<>();
            // These arrays should be as large as the number of rows in the database
            String dateInDB[] = new String[cursor.getCount()];
            int[] numUnlocksInDB = new int[cursor.getCount()];
            int i = 0;
            if (cursor.moveToFirst()) {

                cursor.moveToFirst();

                while (!cursor.isAfterLast()) {
                    // Iterate through the database to extract every unlock number and date
                    dateInDB[i] = cursor.getString(cursor.getColumnIndex(LockEntry.COLUMN_DATE_SHORT));
                    numUnlocksInDB[i] = cursor.getInt(cursor.getColumnIndex(LockEntry.COLUMN_NUMBER_OF_UNLOCKS));
                    entries.add(new BarEntry(i, numUnlocksInDB[i]));
                    i++;
                    cursor.moveToNext();
                }
                // Create the bar graph
                BarDataSet barDataSet = new BarDataSet(entries, "Number of unlocks");
                barDataSet.setColor(getResources().getColor(R.color.colorPrimary));

                BarData barData = new BarData(barDataSet);
                barData.setBarWidth(0.50f);
                barData.setValueTextSize(TEXT_SIZE);
                barData.setValueFormatter(new IValueFormatter() {
                    @Override
                    public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
                        // Convert float to int then to a string. This way we get 20 instead of 20.00
                        return "" + ((int) value);
                    }
                });

                XAxis xAxis = chart.getXAxis();
                xAxis.setGranularity(1f);
                xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
                xAxis.setTextSize(TEXT_SIZE);
                xAxis.setCenterAxisLabels(false);
                // Use the dates from the database as x-axis labels
                xAxis.setValueFormatter(new IndexAxisValueFormatter(dateInDB));

                //--Don't wanna display any y axis--//
                YAxis yAxisRight = chart.getAxisRight();
                yAxisRight.setEnabled(false);
                YAxis yAxisLeft = chart.getAxisLeft();
                yAxisLeft.setEnabled(false);
                //-----------------------------------//

                Description desc = new Description();
                desc.setText(" ");

                chart.setData(barData);
                chart.getAxisLeft().setDrawGridLines(false);
                chart.getXAxis().setDrawGridLines(false);
                chart.getXAxis().setDrawAxisLine(true);
                chart.zoom(1.5f, 0f, 0, 0);
                chart.setScaleYEnabled(false); // Disable vertical zoom
                chart.setDoubleTapToZoomEnabled(false);
                chart.setDescription(desc);
            } else {
                // If there's no data to graph, show
                // the empty graph image.
                emptyGraphImage.setVisibility(View.VISIBLE);
                emptyGraphText.setVisibility(View.VISIBLE);
                chart.setVisibility(View.GONE);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (dbHelper != null) {
                dbHelper.close();
            }
        }
    }

    /**
     * Since this activity can only be displayed in landscape,
     * if the device is rotated to portrait, {@link LockDataView}
     * should be started
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent multiple toasts
        if (mToast != null) mToast.cancel();
        mToast = Toast.makeText(this, "Rotate your device to go back", Toast.LENGTH_SHORT);
        mToast.show();
    }
}