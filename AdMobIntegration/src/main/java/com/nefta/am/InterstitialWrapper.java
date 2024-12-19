package com.nefta.am;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

class InterstitialWrapper extends FullScreenContentCallback implements OnPaidEventListener {
    private final String TAG = "INTERSTITIAL";
    private Activity _activity;
    private Button _loadButton;
    private Button _showButton;
    InterstitialAd _interstitial;

    public InterstitialWrapper(Activity activity, Button loadButton, Button showButton) {
        _activity = activity;
        _loadButton = loadButton;
        _showButton = showButton;

        _loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Load();
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _interstitial.show(_activity);
                _showButton.setEnabled(false);
            }
        });
        _showButton.setEnabled(false);
    }

    private void Load() {
        InterstitialAd.load(_activity, "ca-app-pub-1193175835908241/2233679380", new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    Log.i(TAG, "onAdLoaded "+ interstitialAd.getAdUnitId());
                    _interstitial = interstitialAd;
                    _interstitial.setFullScreenContentCallback(InterstitialWrapper.this);
                    _interstitial.setOnPaidEventListener(InterstitialWrapper.this);

                    _showButton.setEnabled(true);
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    Log.i(TAG, "onAdLoadFailed " + loadAdError);

                    _loadButton.setEnabled(true);
                }
            });

        _loadButton.setEnabled(false);
    }

    @Override
    public void onAdFailedToShowFullScreenContent(AdError adError) {
       Log.i(TAG, "onAdFailedToShowFullScreenContent "+ adError);
    }

    @Override
    public void onAdImpression() {
        Log.i(TAG, "onAdImpression");
    }

    @Override
    public void onAdShowedFullScreenContent() {
        Log.i(TAG, "onAdShowedFullScreenContent");
    }

    @Override
    public void onAdClicked() {
        Log.i(TAG, "onAdClicked");
    }

    @Override
    public void onAdDismissedFullScreenContent() {
        Log.i(TAG, "onAdDismissedFullScreenContent");

        _loadButton.setEnabled(true);
    }

    @Override
    public void onPaidEvent(@NonNull AdValue adValue) {
        Log.i(TAG, "onPaidEvent "+ adValue);
    }
}

