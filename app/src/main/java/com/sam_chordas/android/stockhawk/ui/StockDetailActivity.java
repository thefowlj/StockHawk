package com.sam_chordas.android.stockhawk.ui;

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.CursorAdapter;
import android.widget.TextView;

import com.db.chart.model.LineSet;
import com.db.chart.model.Point;
import com.db.chart.view.ChartView;
import com.db.chart.view.LineChartView;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.HistoricalQuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;

import java.util.ArrayList;

public class StockDetailActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String LOG_TAG = StockDetailActivity.class.getSimpleName();

    private static final int LOADER_HIST_ID = 0;
    private static final int LOADER_QUOTE_ID = 1;

    private static final String[] HIST_QUOTE_PROJECTION = {
            HistoricalQuoteColumns._ID,
            HistoricalQuoteColumns.SYMBOL,
            HistoricalQuoteColumns.CLOSEPRICE,
            HistoricalQuoteColumns.DATE
    };

    private static final String HIST_QUOTE_SELECTION = HistoricalQuoteColumns.SYMBOL + " = ?";
    private static final String HIST_QUOTE_SORT_ORDER = HistoricalQuoteColumns.DATE + " ASC";

    private static final int COL_HIST_QUOTE_ID = 0;
    private static final int COL_HIST_QUOTE_SYMBOL = 1;
    private static final int COL_HIST_QUOTE_CLOSEPRICE = 2;
    private static final int COL_HIST_QUOTE_DATE = 3;

    private static final String[] QUOTE_PROJECTION = {
            QuoteColumns._ID,
            QuoteColumns.DAYS_RANGE,
            QuoteColumns.YEAR_RANGE,
            QuoteColumns.OPEN,
            QuoteColumns.PREVIOUS_CLOSE
    };

    private static final int COL_QUOTE_ID = 0;
    private static final int COL_QUOTE_DAYS_RANGE = 1;
    private static final int COL_QUOTE_YEAR_RANGE = 2;
    private static final int COL_QUOTE_OPEN = 3;
    private static final int COL_QUOTE_PREVIOUS_CLOSE = 4;


    private String mStock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_detail);

        mStock = getIntent().getStringExtra(MyStocksActivity.STOCK_SYMBOL_TAG);
        Bundle bundle = new Bundle();
        bundle.putString(MyStocksActivity.STOCK_SYMBOL_TAG, mStock);

        setTitle(mStock + " Details");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        getLoaderManager().initLoader(LOADER_HIST_ID, bundle, this);
        getLoaderManager().initLoader(LOADER_QUOTE_ID, bundle, this);

    }

    protected void onResume() {
        super.onResume();

    }

    /**
     * Instantiate and return a new Loader for the given ID.
     *
     * @param id   The ID whose loader is to be created.
     * @param args Any arguments supplied by the caller.
     * @return Return a new Loader instance that is ready to start loading.
     */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] selectionArgs = {args.getString(MyStocksActivity.STOCK_SYMBOL_TAG)};

        CursorLoader loader = null;

        if (id == LOADER_HIST_ID) {
            loader = new CursorLoader(this,
                    QuoteProvider.HistoricalQuotes.CONTENT_URI,
                    HIST_QUOTE_PROJECTION,
                    HIST_QUOTE_SELECTION,
                    selectionArgs,
                    HIST_QUOTE_SORT_ORDER);
        } else if (id == LOADER_QUOTE_ID) {
            loader = new CursorLoader(this,
                    QuoteProvider.Quotes.CONTENT_URI,
                    QUOTE_PROJECTION,
                    HIST_QUOTE_SELECTION,
                    selectionArgs,
                    null);
        }

        return loader;
    }

    /**
     * Called when a previously created loader has finished its load.  Note
     * that normally an application is <em>not</em> allowed to commit fragment
     * transactions while in this call, since it can happen after an
     * activity's state is saved.  See {@link android.app.FragmentManager#beginTransaction()
     * FragmentManager.openTransaction()} for further discussion on this.
     * <p>
     * <p>This function is guaranteed to be called prior to the release of
     * the last data that was supplied for this Loader.  At this point
     * you should remove all use of the old data (since it will be released
     * soon), but should not do your own release of the data since its Loader
     * owns it and will take care of that.  The Loader will take care of
     * management of its data so you don't have to.  In particular:
     * <p>
     * <ul>
     * <li> <p>The Loader will monitor for changes to the data, and report
     * them to you through new calls here.  You should not monitor the
     * data yourself.  For example, if the data is a {@link Cursor}
     * and you place it in a {@link CursorAdapter}, use
     * the {@link CursorAdapter#CursorAdapter(Context,
     * Cursor, int)} constructor <em>without</em> passing
     * in either {@link CursorAdapter#FLAG_AUTO_REQUERY}
     * or {@link CursorAdapter#FLAG_REGISTER_CONTENT_OBSERVER}
     * (that is, use 0 for the flags argument).  This prevents the CursorAdapter
     * from doing its own observing of the Cursor, which is not needed since
     * when a change happens you will get a new Cursor throw another call
     * here.
     * <li> The Loader will release the data once it knows the application
     * is no longer using it.  For example, if the data is
     * a {@link Cursor} from a {@link CursorLoader},
     * you should not call close() on it yourself.  If the Cursor is being placed in a
     * {@link CursorAdapter}, you should use the
     * {@link CursorAdapter#swapCursor(Cursor)}
     * method so that the old Cursor is not closed.
     * </ul>
     *
     * @param loader The Loader that has finished.
     * @param data   The data generated by the Loader.
     */
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data != null && loader.getId() == LOADER_HIST_ID) {
            ArrayList<Float> dataArrayList = new ArrayList<>();
            ArrayList<String> dateArrayList = new ArrayList<>();

            LineSet dataset = new LineSet();
            dataset.setColor(getResources().getColor(R.color.material_blue_500));
            dataset.setDotsColor(Color.WHITE);
            int x = 0;

            if (data.moveToFirst()) {
                int count = data.getCount();
                int interval = Math.round(count / 5);
                do {
                    //TODO: do stuff with the data
                    //dataArrayList.add(Float.valueOf(data.getString(COL_HIST_QUOTE_CLOSEPRICE)));
                    //dateArrayList.add(data.getString(COL_HIST_QUOTE_DATE));

                    String label = (x % interval == 0) ? data.getString(COL_HIST_QUOTE_DATE) : "";

                    Point p = new Point(label,
                            Float.valueOf(data.getString(COL_HIST_QUOTE_CLOSEPRICE)));
                    p.setRadius(10);
                    p.setColor(getResources().getColor(R.color.white));
                    //p.setCoordinates(x, Float.valueOf(data.getString(COL_HIST_QUOTE_CLOSEPRICE)));
                    dataset.addPoint(p);
                    x++;

                    Log.d(StockDetailActivity.class.getSimpleName(), "Closing Price: " +
                            data.getString(COL_HIST_QUOTE_CLOSEPRICE));
                } while (data.moveToNext());

                Paint gridPaint = new Paint();
                gridPaint.setColor(Color.WHITE);
                gridPaint.setAntiAlias(true);
                gridPaint.setStyle(Paint.Style.STROKE);

                float maxValue = dataset.getMax().getValue();
                float minValue = dataset.getMin().getValue();
                float dValue = maxValue - minValue;
                int yInterval = Math.round(dValue / 5);
                yInterval = yInterval <= 0 ? 1 : yInterval;

                int yMax = Math.round(maxValue + yInterval);
                int yMin = Math.round(minValue - yInterval);
                yMin = yMin < 0 ? 0 : yMin;
                dValue = yMax - yMin;
                while (dValue % yInterval != 0) {
                    yMax++;
                    dValue = yMax - yMin;
                }

                Log.d(LOG_TAG, "yMin: " + yMin);
                Log.d(LOG_TAG, "yMax: " + yMax);
                Log.d(LOG_TAG, "yInterval: " + yInterval);

                LineChartView chart = (LineChartView) findViewById(R.id.linechart);
                chart.addData(dataset);
                chart.setGrid(ChartView.GridType.FULL, gridPaint);
                chart.setAxisColor(Color.WHITE);
                chart.setLabelsColor(Color.WHITE);
                chart.setAxisBorderValues(yMin, yMax, yInterval);
                chart.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/Roboto-Light.ttf"));
                chart.show();
            }

        } else if (data != null && loader.getId() == LOADER_QUOTE_ID) {
            if (data.moveToFirst()) {
                TextView tv = (TextView) findViewById(R.id.days_range_textview);
                tv.setText(data.getString(COL_QUOTE_DAYS_RANGE));

                tv = (TextView) findViewById(R.id.year_range_textview);
                tv.setText(data.getString(COL_QUOTE_YEAR_RANGE));
                Log.d(LOG_TAG, "year_range: " + data.getString(COL_QUOTE_YEAR_RANGE));

                tv = (TextView) findViewById(R.id.open_textview);
                tv.setText(data.getString(COL_QUOTE_OPEN));

                tv = (TextView) findViewById(R.id.previous_close_textview);
                tv.setText(data.getString(COL_QUOTE_PREVIOUS_CLOSE));
            }
        }
    }

    /**
     * Called when a previously created loader is being reset, and thus
     * making its data unavailable.  The application should at this point
     * remove any references it has to the Loader's data.
     *
     * @param loader The Loader that is being reset.
     */
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }
}
