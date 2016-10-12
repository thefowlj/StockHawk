package com.sam_chordas.android.stockhawk.custom;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * Created by jonfowler on 10/11/16.
 */

public class RobotoTextView extends TextView {
    public RobotoTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public RobotoTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RobotoTextView(Context context) {
        super(context);
        init();
    }

    private void init() {
        Typeface tf = Typeface.createFromAsset(getContext().getAssets(),
                "fonts/Roboto-Light.ttf");
        setTypeface(tf);
    }
}
