package com.google.ads.mediation.nefta;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationRewardedAd;
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.nefta.sdk.NeftaPlugin;
import com.nefta.sdk.Placement;

@Keep
class RewardedVideoNeftaRequest extends NeftaRequest implements MediationRewardedAd {
    private MediationRewardedAdCallback _callback;
    public MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback> _listener;

    public RewardedVideoNeftaRequest(String placementId, NeftaAdapter adapter, MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback> listener) {
        _placementId = placementId;
        _adapter = adapter;
        _listener = listener;
    }

    @Override
    public void OnLoadFail(String error) {
        _listener.onFailure(new AdError(11, error, "OnLoadFail"));
    }

    @Override
    public void OnLoad(Placement placement) {
        _placement = placement;
        _callback = _listener.onSuccess(this);
    }

    @Override
    public void OnShow() {
        _callback.onAdOpened();
        _callback.onVideoStart();
    }

    @Override
    public void OnClick() {
        _callback.reportAdClicked();
    }

    @Override
    public void OnRewarded() {
        _callback.onVideoComplete();
        _callback.onUserEarnedReward(new RewardItem() {
            @Override
            public int getAmount() {
                return 1;
            }

            @NonNull
            @Override
            public String getType() {
                return "rewardedVideo";
            }
        });
    }

    @Override
    public void OnClose() {
        _callback.onAdClosed();
    }

    @Override
    public void showAd(@NonNull Context context) {
        NeftaPlugin._instance.PrepareRenderer((Activity) context);
        NeftaPlugin._instance.Show(_placementId);
    }
}