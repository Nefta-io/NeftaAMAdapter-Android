package com.nefta.am;

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
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

class BannerWrapper extends AdListener implements OnPaidEventListener {
    private final String DefaultAdUnitId = "ca-app-pub-1193175835908241/3238310303";

    private AdView _adView;
    private AdInsight _usedInsight;

    private MainActivity _activity;
    private ViewGroup _bannerGroup;
    private Button _loadAndShowButton;
    private Button _closeButton;

    private void GetInsightsAndLoad() {
        NeftaPlugin._instance.GetInsights(Insights.BANNER, this::Load, 5);
    }

    private void Load(Insights insights) {
        String selectedAdUnitId = DefaultAdUnitId;
        _usedInsight = insights._banner;
        if (_usedInsight != null && _usedInsight._adUnit != null) {
            selectedAdUnitId = _usedInsight._adUnit;
        }

        Log("Loading banner: "+ selectedAdUnitId);
        _adView = new AdView(_activity);
        _adView.setAdSize(AdSize.BANNER);
        _adView.setAdUnitId(selectedAdUnitId);
        _adView.setAdListener(BannerWrapper.this);
        _adView.setOnPaidEventListener(BannerWrapper.this);
        _adView.loadAd(new AdRequest.Builder().build());

        _bannerGroup.removeAllViews();
        _bannerGroup.addView(_adView);
    }

    @Override
    public void onAdFailedToLoad(@NonNull LoadAdError adError) {
        NeftaAdapter.OnExternalMediationRequestFailed(NeftaAdapter.AdType.Banner, _adView.getAdUnitId(), _usedInsight, adError);

        Log("onAdLoadFailed "+ adError);

        _loadAndShowButton.setEnabled(true);
        _closeButton.setEnabled(false);

        //_handler.postDelayed(this::GetInsightsAndLoad, 5000);
    }

    @Override
    public void onAdLoaded() {
        NeftaAdapter.OnExternalMediationRequestLoaded(_adView, _usedInsight);

        Log("onAdLoaded");

        _closeButton.setEnabled(true);
    }

    @Override
    public void onPaidEvent(@NonNull AdValue adValue) {
        NeftaAdapter.OnExternalMediationImpression(_adView, adValue);

        Log("onPaidEvent "+ adValue);
    }

    public BannerWrapper(MainActivity activity, ViewGroup bannerGroup, Button loadAndShowButton, Button closeButton) {
        _activity = activity;
        _bannerGroup = bannerGroup;
        _loadAndShowButton = loadAndShowButton;
        _closeButton = closeButton;

        _loadAndShowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log("GetInsightsAndLoad...");
                GetInsightsAndLoad();
                loadAndShowButton.setEnabled(false);
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
        Log("onAdOpened");
    }

    @Override
    public void onAdImpression() {
        Log("onAdImpression");
    }

    @Override
    public void onAdClicked() {
        Log("onAdClicked");
    }

    @Override
    public void onAdClosed() {
        Log("onAdClosed");
    }

    private void Log(String log) {
        _activity.Log("Rewarded " + log);
    }
}

