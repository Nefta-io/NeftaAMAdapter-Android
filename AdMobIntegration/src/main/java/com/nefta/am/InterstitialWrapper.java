package com.nefta.am;

import android.app.Activity;
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
import com.nefta.sdk.Insight;
import com.nefta.sdk.NeftaPlugin;

import java.util.HashMap;

class InterstitialWrapper extends FullScreenContentCallback implements OnPaidEventListener {
    private final String DefaultAdUnitId = "ca-app-pub-1193175835908241/2233679380";
    private final String TAG = "INTERSTITIAL";
    private final String AdUnitIdInsightName = "recommended_interstitial_ad_unit_id";
    private final String FloorPriceInsightName = "calculated_user_floor_price_interstitial";

    private InterstitialAd _interstitial;
    private String _recommendedAdUnitId;
    private double _calculatedBidFloor;
    private boolean _isLoadRequested;
    private String _loadedAdUnitId;

    private Activity _activity;
    private Button _loadButton;
    private Button _showButton;
    private Handler _handler;

    private void GetInsightsAndLoad() {
        _isLoadRequested = true;

        NeftaPlugin._instance.GetBehaviourInsight(new String[] { AdUnitIdInsightName, FloorPriceInsightName }, this::OnBehaviourInsight);

        _handler.postDelayed(() -> {
            if (_isLoadRequested) {
                _recommendedAdUnitId = null;
                _calculatedBidFloor = 0;
                Load();
            }
        }, 5000);
    }

    private void OnBehaviourInsight(HashMap<String, Insight> insights) {
        _recommendedAdUnitId = null;
        _calculatedBidFloor = 0;
        if (insights.containsKey(AdUnitIdInsightName)) {
            _recommendedAdUnitId = insights.get(AdUnitIdInsightName)._string;
        }
        if (insights.containsKey(FloorPriceInsightName)) {
            _calculatedBidFloor = insights.get(FloorPriceInsightName)._float;
        }

        Log.i(TAG, "OnBehaviourInsights for Interstitial: "+ _recommendedAdUnitId +", calculated bid floor: "+ _calculatedBidFloor);

        if (_isLoadRequested) {
            Load();
        }
    }

    private void Load() {
        _isLoadRequested = false;

        _loadedAdUnitId = _recommendedAdUnitId != null ? _recommendedAdUnitId : DefaultAdUnitId;
        Log.i(TAG, "Loading Interstitial "+ _loadedAdUnitId);

        InterstitialAd.load(_activity, _loadedAdUnitId, new AdRequest.Builder().build(),
            new InterstitialAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                    NeftaAdapter.OnExternalMediationRequestFailed(NeftaAdapter.AdType.Interstitial, _recommendedAdUnitId, _calculatedBidFloor, _loadedAdUnitId, adError);

                    Log.i(TAG, "onAdLoadFailed " + adError);

                    _loadButton.setEnabled(true);

                    _handler.postDelayed(InterstitialWrapper.this::GetInsightsAndLoad, 5000);
                }

                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    NeftaAdapter.OnExternalMediationRequestLoaded(NeftaAdapter.AdType.Interstitial, _recommendedAdUnitId, _calculatedBidFloor, interstitialAd);

                    Log.i(TAG, "onAdLoaded "+ interstitialAd.getAdUnitId());

                    _interstitial = interstitialAd;
                    _interstitial.setFullScreenContentCallback(InterstitialWrapper.this);
                    _interstitial.setOnPaidEventListener(InterstitialWrapper.this);

                    _showButton.setEnabled(true);
                }
            });

        _loadButton.setEnabled(false);
    }

    @Override
    public void onPaidEvent(@NonNull AdValue adValue) {
        NeftaAdapter.OnExternalMediationImpression(NeftaAdapter.AdType.Interstitial, _loadedAdUnitId, adValue);

        Log.i(TAG, "onPaidEvent "+ adValue);
    }

    public InterstitialWrapper(Activity activity, Button loadButton, Button showButton) {
        _activity = activity;
        _loadButton = loadButton;
        _showButton = showButton;

        _handler = new Handler(Looper.getMainLooper());

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

    @Override
    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
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
}

