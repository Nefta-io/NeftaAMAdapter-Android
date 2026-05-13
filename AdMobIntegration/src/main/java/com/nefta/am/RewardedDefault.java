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
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class RewardedDefault extends FullScreenContentCallback implements OnPaidEventListener, OnUserEarnedRewardListener, Rewarded {

    private RewardedUi _ui;
    private AdRequest _request;
    private RewardedAd _rewarded;
    private RewardedAdLoadCallback _loadCallbacks;
    private Handler _handler;

    public void Init(RewardedUi ui) {
        _ui = ui;
        _handler = new Handler(Looper.getMainLooper());

        _loadCallbacks = new RewardedAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                _rewarded = ad;
                NeftaAdapter.OnExternalMediationRequestLoaded(_rewarded, _request);

                Log("onAdLoaded " + AdUnitA);

                ad.setFullScreenContentCallback(RewardedDefault.this);
                ad.setOnPaidEventListener(RewardedDefault.this);

                _request = null;

                _ui.SetAvailability(true);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                NeftaAdapter.OnExternalMediationRequestFailed(_request, error);

                Log("onAdFailedToLoad " + AdUnitA + ": " + error);

                _request = null;
                _rewarded = null;

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
        NeftaAdapter.OnExternalMediationRequest(NeftaAdapter.AdType.Rewarded, _request, AdUnitA);
        Log("Loading " + AdUnitA);
        RewardedAd.load(_ui.Activity, AdUnitA, _request, _loadCallbacks);
    }

    public void Show() {
        Log("Showing " + AdUnitA);
        _rewarded.show(_ui.Activity, this);

        _ui.SetAvailability(false);
    }

    @Override
    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
        Log("onAdFailedToShowFullScreenContent");

        _rewarded = null;

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
        NeftaAdapter.OnExternalMediationImpression(_rewarded, adValue);

        Log("onPaidEvent "+ adValue.getValueMicros());
    }

    @Override
    public void onAdClicked() {
        NeftaAdapter.OnExternalMediationClick(_rewarded);

        Log("onAdClicked");
    }

    @Override
    public void onAdDismissedFullScreenContent() {
        Log("onAdDismissedFullScreenContent");

        _rewarded = null;

        if (_ui.IsAutoLoad) {
            Load();
        }
    }

    @Override
    public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
        _ui.Log("onUserEarnedReward " + rewardItem);
    }

    private void Log(String log) {
        _ui.Log(log);
    }
}
