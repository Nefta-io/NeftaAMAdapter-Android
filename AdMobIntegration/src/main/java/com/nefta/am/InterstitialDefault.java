package com.nefta.am;

import android.os.Handler;
import android.os.Looper;

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

public class InterstitialDefault extends FullScreenContentCallback implements OnPaidEventListener, Interstitial {

    private InterstitialUi _ui;
    private AdRequest _request;
    private InterstitialAd _interstitial;
    private InterstitialAdLoadCallback _loadCallbacks;
    private Handler _handler;

    public void Init(InterstitialUi ui) {
        _ui = ui;
        _handler = new Handler(Looper.getMainLooper());

        _loadCallbacks = new InterstitialAdLoadCallback() {

            @Override
            public void onAdLoaded(@NonNull InterstitialAd ad) {
                _interstitial = ad;
                NeftaAdapter.OnExternalMediationRequestLoaded(_interstitial, _request);

                Log("Loaded " + AdUnitA);

                ad.setFullScreenContentCallback(InterstitialDefault.this);
                ad.setOnPaidEventListener(InterstitialDefault.this);

                _request = null;

                _ui.SetAvailability(true);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                NeftaAdapter.OnExternalMediationRequestFailed(_request, error);

                Log("onAdFailedToLoad " + AdUnitA + ": " + error);

                _request = null;
                _interstitial = null;

                _handler.postDelayed(() -> {
                    if (_ui.IsAutoLoad) {
                        Load();
                    }
                }, 5000);
            }
        };
    }

    public void Load() {
        _request = new AdRequest.Builder().build();
        NeftaAdapter.OnExternalMediationRequest(NeftaAdapter.AdType.Interstitial, _request, AdUnitA);
        Log("Loading " + AdUnitA);
        InterstitialAd.load(_ui.Activity, AdUnitA, _request, _loadCallbacks);
    }

    public void Show() {
        Log("Showing " + AdUnitA);
        _interstitial.show(_ui.Activity);

        _ui.SetAvailability(false);
    }

    @Override
    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
        Log("onAdFailedToShowFullScreenContent "+ adError);

        _interstitial = null;

        if (_ui.IsAutoLoad) {
            Load();
        }
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
    public void onPaidEvent(@NonNull AdValue adValue) {
        NeftaAdapter.OnExternalMediationImpression(_interstitial, adValue);

        Log("onPaidEvent "+ adValue.getValueMicros());
    }

    @Override
    public void onAdClicked() {
        NeftaAdapter.OnExternalMediationClick(_interstitial);

        Log("onAdClicked");
    }

    @Override
    public void onAdDismissedFullScreenContent() {
        Log("onAdDismissedFullScreenContent");

        _interstitial = null;

        if (_ui.IsAutoLoad) {
            Load();
        }
    }

    private void Log(String log) {
        _ui.Log(log);
    }
}
