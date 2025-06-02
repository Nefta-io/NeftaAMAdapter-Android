package com.nefta.am;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.google.ads.mediation.nefta.NeftaAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;
import com.nefta.sdk.Insight;
import com.nefta.sdk.NeftaPlugin;

import java.util.HashMap;

class BannerWrapper extends AdListener implements OnPaidEventListener {
    private final String DefaultAdUnitId = "ca-app-pub-1193175835908241/3238310303";
    private final String TAG = "BANNER";
    private final String AdUnitIdInsightName = "recommended_banner_ad_unit_id";
    private final String FloorPriceInsightName = "calculated_user_floor_price_banner";

    private String _recommendedAdUnitId;
    private double _calculatedBidFloor;
    private boolean _isLoadRequested;
    private String _loadedAdUnitId;

    private Activity _activity;
    private ViewGroup _bannerGroup;
    private Button _loadAndShowButton;
    private Button _closeButton;
    private Handler _handler;

    private AdView _adView;

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

        Log.i(TAG, "OnBehaviourInsights for Banner: "+ _recommendedAdUnitId +", calculated bid floor: "+ _calculatedBidFloor);

        if (_isLoadRequested) {
            Load();
        }
    }

    private void Load() {
        _isLoadRequested = false;

        _loadedAdUnitId = _recommendedAdUnitId != null && !_recommendedAdUnitId.isEmpty() ? _recommendedAdUnitId :DefaultAdUnitId;

        Log.i(TAG, "Loading Banner "+ _loadedAdUnitId);

        _adView = new AdView(_activity);
        _adView.setAdSize(AdSize.BANNER);
        _adView.setAdUnitId(_loadedAdUnitId);
        _adView.setAdListener(BannerWrapper.this);
        _adView.setOnPaidEventListener(BannerWrapper.this);
        _adView.loadAd(new AdRequest.Builder().build());

        _bannerGroup.removeAllViews();
        _bannerGroup.addView(_adView);

        _loadAndShowButton.setEnabled(false);
    }

    @Override
    public void onAdFailedToLoad(@NonNull LoadAdError adError) {
        NeftaAdapter.OnExternalMediationRequestFailed(NeftaAdapter.AdType.Banner, _recommendedAdUnitId, _calculatedBidFloor, _loadedAdUnitId, adError);

        Log.i(TAG, "onAdLoadFailed "+ adError);

        _loadAndShowButton.setEnabled(true);
        _closeButton.setEnabled(false);

        //_handler.postDelayed(this::GetInsightsAndLoad, 5000);
    }

    @Override
    public void onAdLoaded() {
        NeftaAdapter.OnExternalMediationRequestLoaded(NeftaAdapter.AdType.Banner, _recommendedAdUnitId, _calculatedBidFloor, _adView);

        Log.i(TAG, "onAdLoaded");

        _closeButton.setEnabled(true);
    }

    @Override
    public void onPaidEvent(@NonNull AdValue adValue) {
        NeftaAdapter.OnExternalMediationImpression(NeftaAdapter.AdType.Banner, _loadedAdUnitId, adValue);

        Log.i(TAG, "onPaidEvent "+ adValue);
    }

    public BannerWrapper(Activity activity, ViewGroup bannerGroup, Button loadAndShowButton, Button closeButton) {
        _activity = activity;
        _bannerGroup = bannerGroup;
        _loadAndShowButton = loadAndShowButton;
        _closeButton = closeButton;

        _handler = new Handler(Looper.getMainLooper());

        _loadAndShowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GetInsightsAndLoad();

                Log.i(TAG, "Loading ...");
            }
        });
        _closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _bannerGroup.removeView(_adView);

                _adView.destroy();
                _adView = null;

                _loadAndShowButton.setEnabled(true);
                _closeButton.setEnabled(false);
            }
        });

        _closeButton.setEnabled(false);
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
}

