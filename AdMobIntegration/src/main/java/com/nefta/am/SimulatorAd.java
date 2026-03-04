package com.nefta.am;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SimulatorAd extends FrameLayout {
    interface Callback {
        void run();
    }
    private TextView _title;

    private Callback _onClick;
    private Callback _onClose;

    public static SimulatorAd Instance;

    public SimulatorAd(@NonNull Context context) {
        super(context);
    }

    public SimulatorAd(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SimulatorAd(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Instance = this;

        _title = findViewById(R.id.ad_title);
        findViewById(R.id.ad_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setVisibility(GONE);
                _onClose.run();
            }
        });

        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _onClick.run();
            }
        });

        setVisibility(GONE);
    }

    public void Show(String title, Callback onShow, Callback onClick, Callback onReward, Callback onClose) {
        _title.setText(title);

        _onClick = onClick;
        _onClose = onClose;

        postDelayed(onShow::run, 100);

        if (onReward != null) {
            postDelayed(onReward::run, 3000);
        }

        setVisibility(VISIBLE);
    }
}