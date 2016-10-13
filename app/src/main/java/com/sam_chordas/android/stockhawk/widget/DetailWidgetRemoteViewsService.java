package com.sam_chordas.android.stockhawk.widget;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Binder;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.ui.MyStocksActivity;

/**
 * Created by jonfowler on 10/11/16.
 */

public class DetailWidgetRemoteViewsService extends RemoteViewsService {

    public static final String LOG_TAG = DetailWidgetRemoteViewsService.class.getSimpleName();

    private static final String[] QUOTE_COLUMNS = {
            QuoteColumns._ID,
            QuoteColumns.SYMBOL,
            QuoteColumns.BIDPRICE,
            QuoteColumns.PERCENT_CHANGE,
            QuoteColumns.CHANGE,
            QuoteColumns.ISUP,
            QuoteColumns.ISCURRENT,
            QuoteColumns.COMPANY_NAME
    };

    private static int COL_QUOTE_ID = 0;
    private static int COL_QUOTE_SYMBOL = 1;
    private static int COL_QUOTE_BIDPRICE = 2;
    private static int COL_QUOTE_PERCENT_CHANGE = 3;
    private static int COL_QUOTE_CHANGE = 4;
    private static int COL_QUOTE_ISUP = 5;
    private static int COL_QUOTE_ISCURRENT = 6;
    private static int COL_QUOTE_COMPANY_NAME = 7;

    /**
     * To be implemented by the derived service to generate appropriate factories for
     * the data.
     *
     * @param intent
     */
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private Cursor data = null;

            @Override
            public void onCreate() {
                //Blah! Nothing to see here.
            }

            @Override
            public void onDataSetChanged() {
                if (data != null) {
                    data.close();
                }
                // This method is called by the app hosting the widget (e.g., the launcher)
                // However, our ContentProvider is not exported so it doesn't have access to the
                // data. Therefore we need to clear (and finally restore) the calling identity so
                // that calls use our process and permission
                final long identityToken = Binder.clearCallingIdentity();
                data = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                        QUOTE_COLUMNS,
                        QuoteColumns.ISCURRENT + " = ?",
                        new String[]{"1"},
                        null);
                Binder.restoreCallingIdentity(identityToken);
            }

            @Override
            public void onDestroy() {
                if (data != null) {
                    data.close();
                    data = null;
                }
            }

            @Override
            public int getCount() {
                return data == null ? 0 : data.getCount();
            }

            @Override
            public RemoteViews getViewAt(int position) {
                if (position == AdapterView.INVALID_POSITION ||
                        data == null ||
                        !data.moveToPosition(position)) {
                    return null;
                }

                RemoteViews views = new RemoteViews(getPackageName(), R.layout.list_item_quote);
                if (data.moveToPosition(position)) {
                    String symbol = data.getString(COL_QUOTE_SYMBOL);
                    String bidPrice = data.getString(COL_QUOTE_BIDPRICE);
                    String percentChange = data.getString(COL_QUOTE_PERCENT_CHANGE);
                    String change = data.getString(COL_QUOTE_CHANGE);
                    boolean isUp = data.getInt(COL_QUOTE_ISUP) == 1;
                    String companyName = data.getString(COL_QUOTE_COMPANY_NAME);

                    views.setTextViewText(R.id.stock_symbol, symbol);
                    views.setTextViewText(R.id.bid_price, bidPrice);

                    views.setTextViewText(R.id.change, Utils.showPercent ? percentChange : change);
                    views.setInt(R.id.change, "setBackgroundResource",
                            isUp ? R.drawable.percent_change_pill_green :
                                    R.drawable.percent_change_pill_red);

                    Context context = getApplicationContext();
                    String contentDescription = context.getString(R.string.cd_list_item,
                            companyName,
                            isUp ? context.getString(R.string.up) : context.getString(R.string.down),
                            Utils.showPercent ? percentChange :
                                    change + context.getString(R.string.points),
                            bidPrice);
                    views.setContentDescription(R.id.list_item, contentDescription);

                    final Intent fillInIntent = new Intent();
                    fillInIntent.putExtra(MyStocksActivity.STOCK_SYMBOL_TAG, symbol);
                    views.setOnClickFillInIntent(R.id.list_item, fillInIntent);
                }

                return views;
            }

            @Override
            public RemoteViews getLoadingView() {
                return new RemoteViews(getPackageName(), R.layout.list_item_quote);
            }

            @Override
            public int getViewTypeCount() {
                return 1;
            }

            @Override
            public long getItemId(int position) {
                if (data.moveToPosition(position)) {
                    return data.getLong(COL_QUOTE_ID);
                }
                return position;
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }
        };
    }
}
