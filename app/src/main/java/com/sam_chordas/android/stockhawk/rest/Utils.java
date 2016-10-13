package com.sam_chordas.android.stockhawk.rest;

import android.content.ContentProviderOperation;
import android.util.Log;

import com.sam_chordas.android.stockhawk.data.HistoricalQuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by sam_chordas on 10/8/15.
 */
public class Utils {

    private static String LOG_TAG = Utils.class.getSimpleName();

    public static boolean showPercent = true;

    public static final String ROBOTO_LIGHT_FONT = "fonts/Roboto-Light.ttf";
    public static final String UTF8 = "UTF-8";
    public static final String YAHOO_BASE_URL = "https://query.yahooapis.com/v1/public/yql?q=";
    public static final String YAHOO_SYMBOL_QUERY =
            "select * from yahoo.finance.quotes where symbol in (";
    public static final String YAHOO_HIST_SYMBOL_QUERY =
            "select * from yahoo.finance.historicaldata where symbol = ";
    public static final String DEFAULT_STOCK_SYMBOLS = "\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")";
    public static final String YAHOO_QUERY_SUFFIX =
            "&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
            + "org%2Falltableswithkeys&callback=";

    public static class JSONKeys {
        public static final String QUERY = "query";
        public static final String COUNT = "count";
        public static final String RESULTS = "results";
        public static final String QUOTE = "quote";

        public static final String QUOTE_SYMBOL ="symbol";
        public static final String QUOTE_CHANGE = "Change";
        public static final String QUOTE_BID = "Bid";
        public static final String QUOTE_PERCENT_CHANGE = "ChangeinPercent";
        public static final String QUOTE_DAYS_RANGE = "DaysRange";
        public static final String QUOTE_YEAR_RANGE = "YearRange";
        public static final String QUOTE_OPEN = "Open";
        public static final String QUOTE_PREVIOUS_CLOSE = "PreviousClose";
        public static final String QUOTE_COMPANY_NAME = "Name";
        public static final String QUOTE_ASK = "Ask";


        public static final String HIST_SYMBOL = "Symbol";
        public static final String HIST_CLOSE = "Close";
        public static final String HIST_DATE = "Date";
    }

    public static ArrayList quoteJsonToContentVals(String JSON) {
        ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();
        JSONObject jsonObject = null;
        JSONArray resultsArray = null;
        try {
            jsonObject = new JSONObject(JSON);
            if (jsonObject != null && jsonObject.length() != 0) {
                jsonObject = jsonObject.getJSONObject(JSONKeys.QUERY);
                int count = Integer.parseInt(jsonObject.getString(JSONKeys.COUNT));
                if (count == 1) {
                    jsonObject = jsonObject.getJSONObject(JSONKeys.RESULTS)
                            .getJSONObject(JSONKeys.QUOTE);
                    batchOperations.add(buildBatchOperation(jsonObject));
                } else {
                    resultsArray = jsonObject.getJSONObject(JSONKeys.RESULTS).getJSONArray(JSONKeys.QUOTE);

                    if (resultsArray != null && resultsArray.length() != 0) {
                        for (int i = 0; i < resultsArray.length(); i++) {
                            jsonObject = resultsArray.getJSONObject(i);
                            batchOperations.add(buildBatchOperation(jsonObject));
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(LOG_TAG, "String to JSON failed: " + e);
        }
        return batchOperations;
    }

    public static ArrayList historicalQuoteJsonToContentVals(String JSON) {
        ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();
        JSONObject jsonObject = null;
        JSONArray resultsArray = null;
        try {
            jsonObject = new JSONObject(JSON);
            if(jsonObject != null && jsonObject.length() != 0) {
                jsonObject = jsonObject.getJSONObject(JSONKeys.QUERY);
                resultsArray = jsonObject.getJSONObject(JSONKeys.RESULTS).getJSONArray(JSONKeys.QUOTE);
                if (resultsArray != null && resultsArray.length() != 0) {
                    for (int i = 0; i < resultsArray.length(); i++) {
                        jsonObject = resultsArray.getJSONObject(i);
                        batchOperations.add(buildHistoricalBatchOperation(jsonObject));
                    }
                }
            }
        } catch(JSONException e) {
            e.printStackTrace();
        }
        return batchOperations;
    }

    public static String truncateBidPrice(String bidPrice) {
        bidPrice = String.format("%.2f", Float.parseFloat(bidPrice));
        return bidPrice;
    }

    public static String truncateChange(String change, boolean isPercentChange) {
        String weight = change.substring(0, 1);
        String ampersand = "";
        if (isPercentChange) {
            ampersand = change.substring(change.length() - 1, change.length());
            change = change.substring(0, change.length() - 1);
        }
        change = change.substring(1, change.length());
        double round = (double) Math.round(Double.parseDouble(change) * 100) / 100;
        change = String.format("%.2f", round);
        StringBuffer changeBuffer = new StringBuffer(change);
        changeBuffer.insert(0, weight);
        changeBuffer.append(ampersand);
        change = changeBuffer.toString();
        return change;
    }

    public static ContentProviderOperation buildHistoricalBatchOperation(JSONObject jsonObject) {
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(
                QuoteProvider.HistoricalQuotes.CONTENT_URI);
        try {
            builder.withValue(HistoricalQuoteColumns.SYMBOL, jsonObject.getString(JSONKeys.HIST_SYMBOL));
            builder.withValue(HistoricalQuoteColumns.CLOSEPRICE, jsonObject.getString(JSONKeys.HIST_CLOSE));
            builder.withValue(HistoricalQuoteColumns.DATE, jsonObject.getString(JSONKeys.HIST_DATE));
        } catch(JSONException e) {
            e.printStackTrace();
        }
        return builder.build();
    }

    public static ContentProviderOperation buildBatchOperation(JSONObject jsonObject) {
        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(
                QuoteProvider.Quotes.CONTENT_URI);
        try {
            String change = jsonObject.getString(JSONKeys.QUOTE_CHANGE);
            builder.withValue(QuoteColumns.SYMBOL,
                    jsonObject.getString(JSONKeys.QUOTE_SYMBOL));
            builder.withValue(QuoteColumns.BIDPRICE,
                    truncateBidPrice(jsonObject.getString(JSONKeys.QUOTE_BID)));
            builder.withValue(QuoteColumns.PERCENT_CHANGE, truncateChange(
                    jsonObject.getString(JSONKeys.QUOTE_PERCENT_CHANGE), true));
            builder.withValue(QuoteColumns.CHANGE, truncateChange(change, false));
            builder.withValue(QuoteColumns.ISCURRENT, 1);
            if (change.charAt(0) == '-') {
                builder.withValue(QuoteColumns.ISUP, 0);
            } else {
                builder.withValue(QuoteColumns.ISUP, 1);
            }
            builder.withValue(QuoteColumns.DAYS_RANGE,
                    jsonObject.getString(JSONKeys.QUOTE_DAYS_RANGE));
            builder.withValue(QuoteColumns.YEAR_RANGE,
                    jsonObject.getString(JSONKeys.QUOTE_YEAR_RANGE));
            builder.withValue(QuoteColumns.OPEN,
                    jsonObject.getString(JSONKeys.QUOTE_OPEN));
            builder.withValue(QuoteColumns.PREVIOUS_CLOSE,
                    jsonObject.getString(JSONKeys.QUOTE_PREVIOUS_CLOSE));
            builder.withValue(QuoteColumns.COMPANY_NAME,
                    jsonObject.getString(JSONKeys.QUOTE_COMPANY_NAME));

        } catch (JSONException e) {
            e.printStackTrace();
        }
        return builder.build();
    }
}
