package com.sam_chordas.android.stockhawk.service;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.HistoricalQuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by sam_chordas on 9/30/15.
 * The GCMTask service is primarily for periodic tasks. However, OnRunTask can be called directly
 * and is used for the initialization and adding task as well.
 */
public class StockTaskService extends GcmTaskService {
    private String LOG_TAG = StockTaskService.class.getSimpleName();

    private OkHttpClient client = new OkHttpClient();
    private Context mContext;
    private StringBuilder mStoredSymbols = new StringBuilder();
    private boolean isUpdate;

    private static final long MS_IN_30_DAYS = 2592000000L;
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private static final String START_DATE_QUERY = " and startDate=\"";
    private static final String END_DATE_QUERY = "\" and endDate=\"";

    public StockTaskService() {
    }

    public StockTaskService(Context context) {
        mContext = context;
    }

    String fetchData(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    @Override
    public int onRunTask(TaskParams params) {
        Cursor initQueryCursor;
        if (mContext == null) {
            mContext = this;
        }
        StringBuilder urlStringBuilder = new StringBuilder();
        try {
            // Base URL for the Yahoo query
            urlStringBuilder.append(Utils.YAHOO_BASE_URL);
            urlStringBuilder.append(URLEncoder.encode(Utils.YAHOO_SYMBOL_QUERY, Utils.UTF8));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (params.getTag().equals(StockIntentService.INIT) ||
                params.getTag().equals(StockIntentService.PERIODIC)) {
            isUpdate = true;
            initQueryCursor = mContext.getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                    new String[]{"Distinct " + QuoteColumns.SYMBOL}, null,
                    null, null);
            if (initQueryCursor.getCount() == 0 || initQueryCursor == null) {
                // Init task. Populates DB with quotes for the symbols seen below
                try {
                    urlStringBuilder.append(
                            URLEncoder.encode(Utils.DEFAULT_STOCK_SYMBOLS, Utils.UTF8));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else if (initQueryCursor != null) {
                DatabaseUtils.dumpCursor(initQueryCursor);
                initQueryCursor.moveToFirst();
                for (int i = 0; i < initQueryCursor.getCount(); i++) {
                    mStoredSymbols.append("\"" +
                            initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol")) + "\",");
                    initQueryCursor.moveToNext();
                }
                mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");
                try {
                    urlStringBuilder.append(URLEncoder.encode(mStoredSymbols.toString(), Utils.UTF8));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        } else if (params.getTag().equals(StockIntentService.ADD)) {
            isUpdate = false;
            // get symbol from params.getExtra and build query
            String stockInput = params.getExtras().getString(StockIntentService.SYMBOL);
            try {
                if(stockSymbolExists(stockInput) == false) {
                    final String message = getString(R.string.invalid_stock, stockInput);
                    Handler h = new Handler(mContext.getMainLooper());

                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(mContext, message,Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    try {
                        urlStringBuilder.append(URLEncoder.encode("\"" + stockInput + "\")",
                                Utils.UTF8));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // finalize the URL for the API query.
        urlStringBuilder.append(Utils.YAHOO_QUERY_SUFFIX);

        String urlString;
        String getResponse;
        int result = GcmNetworkManager.RESULT_FAILURE;

        if (urlStringBuilder != null) {
            urlString = urlStringBuilder.toString();
            Log.d(LOG_TAG, "urlString: " + urlString);
            try {
                getResponse = fetchData(urlString);
                result = GcmNetworkManager.RESULT_SUCCESS;
                try {
                    ContentValues contentValues = new ContentValues();
                    // update ISCURRENT to 0 (false) so new data is current
                    if (isUpdate) {
                        contentValues.put(QuoteColumns.ISCURRENT, 0);
                        mContext.getContentResolver().update(QuoteProvider.Quotes.CONTENT_URI, contentValues,
                                null, null);
                    }
                    mContext.getContentResolver().applyBatch(QuoteProvider.AUTHORITY,
                            Utils.quoteJsonToContentVals(getResponse));
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(LOG_TAG, "Error applying batch insert", e);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //start historical data pull
        Cursor symbolQueryCursor = mContext.getContentResolver()
                .query(QuoteProvider.Quotes.CONTENT_URI,
                        new String[] {"Distinct " + QuoteColumns.SYMBOL},
                        null,
                        null,
                        null);



        if(symbolQueryCursor != null) {
            DatabaseUtils.dumpCursor(symbolQueryCursor);

            Calendar calendar = Calendar.getInstance();
            Date date = new Date();

            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
            String endDate = dateFormat.format(date);
            date.setTime(System.currentTimeMillis() - MS_IN_30_DAYS);
            String startDate = dateFormat.format(date);
            //String startDate = "2016-10-01";
            //String endDate = "2016-10-08";
            Log.d(LOG_TAG, "Start Date: " + startDate);
            Log.d(LOG_TAG, "End Date: " + endDate);

            while(symbolQueryCursor.moveToNext()) {
                String symbol = symbolQueryCursor.getString(symbolQueryCursor.getColumnIndex(QuoteColumns.SYMBOL));
                //String symbol = symbolQueryCursor.getString(symbolQueryCursor.getColumnIndex(QuoteColumns.SYMBOL));
                StringBuilder historicalUrlStringBuilder = new StringBuilder();
                try {
                    historicalUrlStringBuilder.append(Utils.YAHOO_BASE_URL);
                    historicalUrlStringBuilder.append(URLEncoder.encode(Utils.YAHOO_SYMBOL_QUERY, Utils.UTF8));
                    historicalUrlStringBuilder.append(URLEncoder.encode("\""+symbol+"\"", Utils.UTF8));
                    historicalUrlStringBuilder.append(URLEncoder.encode(START_DATE_QUERY +
                            startDate + END_DATE_QUERY + endDate + "\"", Utils.UTF8));
                } catch(UnsupportedEncodingException e) {
                    Log.d(LOG_TAG, "Encoding error ", e);
                }

                historicalUrlStringBuilder.append(Utils.YAHOO_QUERY_SUFFIX);

                String historicalUrlString;
                String historicalResponse;

                if(historicalUrlStringBuilder != null) {
                    historicalUrlString = historicalUrlStringBuilder.toString();
                    Log.d(LOG_TAG, "histoURL: " + historicalUrlString);
                    try {
                        historicalResponse = fetchData(historicalUrlString);

                        ContentResolver resolver = mContext.getContentResolver();
                        resolver.delete(QuoteProvider.HistoricalQuotes.CONTENT_URI,
                                HistoricalQuoteColumns.SYMBOL + " = \"" + symbol + "\"", null);
                        resolver.applyBatch(QuoteProvider.AUTHORITY,
                                Utils.historicalQuoteJsonToContentVals(historicalResponse));
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    } catch (OperationApplicationException e) {
                        e.printStackTrace();
                    } catch(SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return result;
    }

    private boolean stockSymbolExists(String stockSymbol) throws IOException {
        StringBuilder tempBuilder = new StringBuilder();
        try {
            tempBuilder.append(Utils.YAHOO_BASE_URL);
            tempBuilder.append(URLEncoder.encode(Utils.YAHOO_SYMBOL_QUERY, Utils.UTF8));
            tempBuilder.append(URLEncoder.encode("\"" + stockSymbol + "\")", Utils.UTF8));
            tempBuilder.append(Utils.YAHOO_QUERY_SUFFIX);
        } catch(UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        String urlString = tempBuilder.toString();
        String jsonData = fetchData(urlString);
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            jsonObject = jsonObject.getJSONObject("query");
            if(jsonObject == null) {
                return false;
            } else {
                JSONObject results  = jsonObject.getJSONObject("results").getJSONObject("quote");
                if (results == null) {
                    return false;
                } else {
                    String stuff = results.getString("Ask");
                    Log.d(LOG_TAG, stuff);
                    return !stuff.equals("null");
                }
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return false;
    }
}
