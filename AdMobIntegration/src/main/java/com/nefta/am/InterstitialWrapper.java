package com.nefta.am;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.google.ads.mediation.nefta.NeftaAdapter;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

class InterstitialWrapper extends FullScreenContentCallback implements OnPaidEventListener {
    private final String DefaultAdUnitId = "ca-app-pub-1193175835908241/2233679380";
    private final String TAG = "INTERSTITIAL";

    private InterstitialAd _interstitial;
    private AdInsight _usedInsight;
    private boolean _isLoading;

    private MainActivity _activity;
    private Button _loadButton;
    private Button _showButton;
    private Handler _handler;

    private void GetInsightsAndLoad() {
        NeftaPlugin._instance.GetInsights(Insights.INTERSTITIAL, this::Load, 5);
    }

    private void Load(Insights insights) {
        String selectedAdUnitId = DefaultAdUnitId;
        _usedInsight = insights._interstitial;
        if (_usedInsight != null && _usedInsight._adUnit != null) {
            selectedAdUnitId = _usedInsight._adUnit;
        }
        final String adUnitToLoad = selectedAdUnitId;

        Log("Loading Interstitial "+ adUnitToLoad);
        InterstitialAd.load(_activity, adUnitToLoad, new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                    NeftaAdapter.OnExternalMediationRequestFailed(NeftaAdapter.AdType.Interstitial, adUnitToLoad, _usedInsight, adError);

                    Log("onAdLoadFailed " + adError);

                    _handler.postDelayed(() -> {
                        if (_isLoading) {
                            GetInsightsAndLoad();
                        }
                    }, 5000);
                }

                @Override
                public void onAdLoaded(@NonNull InterstitialAd ad) {
                    NeftaAdapter.OnExternalMediationRequestLoaded(ad, _usedInsight);

                    Log("onAdLoaded "+ adUnitToLoad);

                    _interstitial = ad;
                    _interstitial.setFullScreenContentCallback(InterstitialWrapper.this);
                    _interstitial.setOnPaidEventListener(InterstitialWrapper.this);

                    SetLoadingButton(false);
                    _loadButton.setEnabled(false);
                    _showButton.setEnabled(true);
                }
            });
    }

    @Override
    public void onPaidEvent(@NonNull AdValue adValue) {
        NeftaAdapter.OnExternalMediationImpression(_interstitial, adValue);

        Log("onPaidEvent "+ adValue);
    }

    public InterstitialWrapper(MainActivity activity, Button loadButton, Button showButton) {
        _activity = activity;
        _loadButton = loadButton;
        _showButton = showButton;

        _handler = new Handler(Looper.getMainLooper());

        _loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (_isLoading) {
                    SetLoadingButton(false);
                } else {
                    Log("GetInsightsAndLoad...");
                    GetInsightsAndLoad();
                    SetLoadingButton(true);
                }
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _interstitial.show(_activity);
                _loadButton.setEnabled(true);
                _showButton.setEnabled(false);
            }
        });
        _showButton.setEnabled(false);
    }

    @Override
    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
       Log("onAdFailedToShowFullScreenContent "+ adError);
    }

    @Override
    public void onAdImpression() {
        Log("onAdImpression");
    }

    @Override
    public void onAdShowedFullScreenContent() {
        Log("onAdShowedFullScreenContent");
    }

    @Override
    public void onAdClicked() {
        Log("onAdClicked");
    }

    @Override
    public void onAdDismissedFullScreenContent() {
        Log("onAdDismissedFullScreenContent");
    }

    private void Log(String log) {
        _activity.Log("Interstitial " + log);
    }

    private void SetLoadingButton(boolean isLoading) {
        _isLoading = isLoading;
        if (isLoading) {
            _loadButton.setText("Cancel");
        } else {
            _loadButton.setText("Load Interstitial");
        }
    }
}

