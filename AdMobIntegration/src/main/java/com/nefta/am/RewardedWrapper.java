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
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.nefta.sdk.Insight;
import com.nefta.sdk.NeftaPlugin;

import java.util.HashMap;

public class RewardedWrapper extends FullScreenContentCallback implements OnPaidEventListener, OnUserEarnedRewardListener {
    private final String DefaultAdUnitId = "ca-app-pub-1193175835908241/8841990779";
    private final String TAG = "REWARDED_VIDEO";
    private final String AdUnitIdInsightName = "recommended_interstitial_ad_unit_id";
    private final String FloorPriceInsightName = "calculated_user_floor_price_interstitial";

    private RewardedAd _rewardedAd;
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

        Log.i(TAG, "OnBehaviourInsights for Rewarded: "+ _recommendedAdUnitId +", calculated bid floor: "+ _calculatedBidFloor);

        if (_isLoadRequested) {
            Load();
        }
    }

    private void Load() {
        _isLoadRequested = false;

        _loadedAdUnitId = _recommendedAdUnitId != null && !_recommendedAdUnitId.isEmpty() ? _recommendedAdUnitId : DefaultAdUnitId;

        Log.i(TAG, "Loading Rewarded "+ _loadedAdUnitId);

        RewardedAd.load(_activity, _loadedAdUnitId, new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    NeftaAdapter.OnExternalMediationRequestFailed(NeftaAdapter.AdType.Rewarded, _recommendedAdUnitId, _calculatedBidFloor, _loadedAdUnitId, loadAdError);

                    Log.i(TAG, "onAdFailedToLoad "+ loadAdError.getMessage());

                    _loadButton.setEnabled(true);

                    _handler.postDelayed(RewardedWrapper.this::GetInsightsAndLoad, 5000);
                }

                @Override
                public void onAdLoaded(@NonNull RewardedAd ad) {
                    NeftaAdapter.OnExternalMediationRequestLoaded(NeftaAdapter.AdType.Rewarded, _recommendedAdUnitId, _calculatedBidFloor, ad);

                    Log.i(TAG, "onAdLoaded "+ ad.getAdUnitId());

                    _rewardedAd = ad;
                    _rewardedAd.setFullScreenContentCallback(RewardedWrapper.this);
                    _rewardedAd.setOnPaidEventListener(RewardedWrapper.this);

                    _showButton.setEnabled(true);
                }
            });
        _loadButton.setEnabled(false);
    }

    @Override
    public void onPaidEvent(@NonNull AdValue adValue) {
        NeftaAdapter.OnExternalMediationImpression(NeftaAdapter.AdType.Rewarded, _loadedAdUnitId, adValue);

        Log.i(TAG, "onPaidEvent "+ adValue);
    }

    public RewardedWrapper(Activity activity, Button loadButton, Button showButton) {
        _activity = activity;
        _loadButton = loadButton;
        _showButton = showButton;

        _handler = new Handler(Looper.getMainLooper());

        _loadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GetInsightsAndLoad();
            }
        });
        _showButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _rewardedAd.show(_activity, RewardedWrapper.this);
                _showButton.setEnabled(false);
            }
        });
        _showButton.setEnabled(false);
    }

    @Override
    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
        Log.i(TAG, "onAdFailedToShowFullScreenContent");
        _rewardedAd = null;
    }

    @Override
    public void onAdClicked() {
        Log.i(TAG, "onAdClicked");
    }

    @Override
    public void onAdDismissedFullScreenContent() {
        Log.i(TAG, "onAdDismissedFullScreenContent");
        _rewardedAd = null;
    }

    @Override
    public void onAdImpression() {
        Log.i(TAG, "onAdImpression");
    }

    @Override
    public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
        Log.i(TAG, "onAdImpression " + rewardItem);
    }

    @Override
    public void onAdShowedFullScreenContent() {
        Log.i(TAG, "onAdShowedFullScreenContent");

        _loadButton.setEnabled(true);
    }
}