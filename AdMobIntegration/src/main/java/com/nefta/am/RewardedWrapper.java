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
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class RewardedWrapper extends FullScreenContentCallback implements OnPaidEventListener, OnUserEarnedRewardListener {
    private final String TAG = "REWARDED_VIDEO";
    private Activity _activity;
    private Button _loadButton;
    private Button _showButton;
    RewardedAd _rewardedAd;

    public RewardedWrapper(Activity activity, Button loadButton, Button showButton) {
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
                _rewardedAd.show(_activity, RewardedWrapper.this);
                _showButton.setEnabled(false);
            }
        });
        _showButton.setEnabled(false);
    }

    private void Load() {
        RewardedAd.load(_activity, "ca-app-pub-1193175835908241/8841990779", new AdRequest.Builder().build(),
            new RewardedAdLoadCallback() {
                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    Log.i(TAG, "onAdFailedToLoad "+ loadAdError.getMessage());
                    _rewardedAd = null;

                    _loadButton.setEnabled(true);
                }

                @Override
                public void onAdLoaded(@NonNull RewardedAd ad) {
                    _rewardedAd = ad;
                    _rewardedAd.setFullScreenContentCallback(RewardedWrapper.this);
                    _rewardedAd.setOnPaidEventListener(RewardedWrapper.this);
                    Log.i(TAG, "onAdLoaded "+ ad.getAdUnitId());
                    _showButton.setEnabled(true);
                }
            });
        _loadButton.setEnabled(false);
    }

    @Override
    public void onAdFailedToShowFullScreenContent(AdError adError) {
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

    @Override
    public void onPaidEvent(@NonNull AdValue adValue) {
        Log.i(TAG, "onPaidEvent "+ adValue);
    }
}