package com.nefta.am;

import android.os.Handler;
import android.os.Looper;
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
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.nefta.sdk.AdInsight;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaPlugin;

public class RewardedWrapper extends FullScreenContentCallback implements OnPaidEventListener, OnUserEarnedRewardListener {
    private final String DefaultAdUnitId = "ca-app-pub-1193175835908241/8841990779";

    private RewardedAd _rewardedAd;
    private AdInsight _usedInsight;
    private boolean _isLoading;

    private MainActivity _activity;
    private Button _loadButton;
    private Button _showButton;
    private Handler _handler;

    private void GetInsightsAndLoad() {
        NeftaPlugin._instance.GetInsights(Insights.REWARDED, this::Load, 5);
    }

    private void Load(Insights insights) {
        String selectedAdUnitId = DefaultAdUnitId;
        _usedInsight = insights._rewarded;
        if (_usedInsight != null && _usedInsight._adUnit != null) {
            selectedAdUnitId = _usedInsight._adUnit;
        }
        final String adUnitToLoad = selectedAdUnitId;

        Log("Loading Rewarded "+ adUnitToLoad);

        RewardedAd.load(_activity, adUnitToLoad, new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    NeftaAdapter.OnExternalMediationRequestFailed(NeftaAdapter.AdType.Rewarded, adUnitToLoad, _usedInsight, loadAdError);

                    Log("onAdFailedToLoad "+ loadAdError.getMessage());

                    _handler.postDelayed(() -> {
                        if (_isLoading) {
                            GetInsightsAndLoad();
                        }
                    }, 5000);
                }

                @Override
                public void onAdLoaded(@NonNull RewardedAd ad) {
                    NeftaAdapter.OnExternalMediationRequestLoaded(ad, _usedInsight);

                    Log("onAdLoaded "+ ad.getAdUnitId());

                    _rewardedAd = ad;
                    _rewardedAd.setFullScreenContentCallback(RewardedWrapper.this);
                    _rewardedAd.setOnPaidEventListener(RewardedWrapper.this);

                    SetLoadingButton(false);
                    _loadButton.setEnabled(false);
                    _showButton.setEnabled(true);
                }
            });
    }

    @Override
    public void onPaidEvent(@NonNull AdValue adValue) {
        NeftaAdapter.OnExternalMediationImpression(_rewardedAd, adValue);

        Log("onPaidEvent "+ adValue);
    }

    public RewardedWrapper(MainActivity activity, Button loadButton, Button showButton) {
        _activity = activity;
        _loadButton = loadButton;
        _showButton = showButton;

        _handler = new Handler(Looper.getMainLooper());

        _loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log("GetInsightsAndLoad...");
                GetInsightsAndLoad();
                _loadButton.setEnabled(false);
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _rewardedAd.show(_activity, RewardedWrapper.this);
                _loadButton.setEnabled(true);
                _showButton.setEnabled(false);
            }
        });
        _showButton.setEnabled(false);
    }

    @Override
    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
        Log("onAdFailedToShowFullScreenContent");
        _rewardedAd = null;
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

    @Override
    public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
        Log("onUserEarnedReward " + rewardItem);
    }

    private void Log(String log) {
        _activity.Log("Rewarded " + log);
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