package com.nefta.am;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;

class BannerWrapper extends AdListener implements OnPaidEventListener {
    private final String TAG = "BANNER";
    private Activity _activity;
    private ViewGroup _bannerGroup;
    private Button _loadAndShowButton;
    private Button _closeButton;
    private AdView _adView;

    public BannerWrapper(Activity activity, ViewGroup bannerGroup, Button loadAndShowButton, Button closeButton) {
        _activity = activity;
        _bannerGroup = bannerGroup;
        _loadAndShowButton = loadAndShowButton;
        _closeButton = closeButton;
        _loadAndShowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _adView = new AdView(_activity);
                _adView.setAdSize(AdSize.BANNER);
                _adView.setAdUnitId("ca-app-pub-1193175835908241/3238310303");
                _adView.setAdListener(BannerWrapper.this);
                _adView.setOnPaidEventListener(BannerWrapper.this);
                _adView.loadAd(new AdRequest.Builder().build());

                _bannerGroup.removeAllViews();
                _bannerGroup.addView(_adView);

                _closeButton.setEnabled(true);
            }
        });
        _closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _bannerGroup.removeView(_adView);

                _adView.destroy();
                _adView = null;

                _closeButton.setEnabled(false);
            }
        });

        _closeButton.setEnabled(false);
    }

    @Override
    public void onAdFailedToLoad(LoadAdError adError) {
        Log.i(TAG, "onAdLoadFailed "+ adError);
    }

    @Override
    public void onAdLoaded() {
        Log.i(TAG, "onAdLoaded");
    }

    @Override
    public void onAdOpened() {
        Log.i(TAG, "onAdOpened");
    }

    @Override
    public void onAdImpression() {
        Log.i(TAG, "onAdImpression");
    }

    @Override
    public void onAdClicked() {
        Log.i(TAG, "onAdClicked");
    }

    @Override
    public void onAdClosed() {
        Log.i(TAG, "onAdClosed");
    }

    @Override
    public void onPaidEvent(@NonNull AdValue adValue) {
        Log.i(TAG, "onPaidEvent "+ adValue);
    }
}

