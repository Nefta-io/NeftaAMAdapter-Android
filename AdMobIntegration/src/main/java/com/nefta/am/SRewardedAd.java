package com.nefta.am;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.rewarded.OnAdMetadataChangedListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions;
import com.nefta.debug.Callback;
import com.nefta.debug.NDebug;

public class SRewardedAd extends RewardedAd {
    private String _adUnitId;
    private double _cost;
    private FullScreenContentCallback _fullScreenCallback;
    private OnPaidEventListener _onPaidEventHandler;
    public boolean _hasFill;

    private SRewardedAd(String adUnitId, double cost) {

        _adUnitId = adUnitId;
        _cost = cost;
    }

    @NonNull
    @Override
    public Bundle getAdMetadata() {
        return null;
    }

    @Nullable
    @Override
    public FullScreenContentCallback getFullScreenContentCallback() {
        return null;
    }

    @Nullable
    @Override
    public OnPaidEventListener getOnPaidEventListener() {
        return null;
    }

    @NonNull
    @Override
    public ResponseInfo getResponseInfo() {
        return null;
    }

    @Nullable
    @Override
    public OnAdMetadataChangedListener getOnAdMetadataChangedListener() {
        return null;
    }

    @NonNull
    @Override
    public RewardItem getRewardItem() {
        return null;
    }

    @NonNull
    @Override
    public String getAdUnitId() {
        return _adUnitId;
    }

    @Override
    public void setPlacementId(long var1) { }

    @Override
    public long getPlacementId() { return 0; }

    public static void load(@NonNull Context context, @NonNull String adUnitId, @NonNull AdRequest adRequest, @NonNull RewardedAdLoadCallback loadCallback) {
        boolean hasFloor = adRequest.getNetworkExtrasBundle(AdMobAdapter.class) != null;
        String status = adUnitId + " loading " + (hasFloor ? " as Optimized" : "as Default");
        if (RewardedSim.Instance._trackA._request == adRequest) {
            RewardedSim.Instance.ToggleTrackA(true, true);
            RewardedSim.Instance._aStatus.setText(status);
        } else {
            RewardedSim.Instance.ToggleTrackB(true, true);
            RewardedSim.Instance._aStatus.setText(status);
        }
        new Thread(() -> {
            try {
                RewardedSim.Track r = RewardedSim.Instance._trackA._request == adRequest ? RewardedSim.Instance._trackA : RewardedSim.Instance._trackB;
                r._loadSelection = 0;
                while (r._loadSelection == 0) {
                    Thread.sleep(1000);
                }
                if (r._loadSelection == 1) {
                    SRewardedAd s = new SRewardedAd(adUnitId, 0.002);
                    loadCallback.onAdLoaded(s);
                } else if (r._loadSelection == 2) {
                    SRewardedAd s = new SRewardedAd(adUnitId, 0.001);
                    loadCallback.onAdLoaded(s);
                } else if (r._loadSelection == 3) {
                    LoadAdError error = new LoadAdError(AdRequest.ERROR_CODE_NO_FILL, "no fill", "sim", null, null);
                    loadCallback.onAdFailedToLoad(error);
                } else if (r._loadSelection == 4) {
                    LoadAdError error = new LoadAdError(AdRequest.ERROR_CODE_INTERNAL_ERROR, "other",  "sim",null, null);
                    loadCallback.onAdFailedToLoad(error);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @Override
    public void setFullScreenContentCallback(@Nullable FullScreenContentCallback fullScreenContentCallback) {
        _fullScreenCallback = fullScreenContentCallback;
    }

    @Override
    public void setImmersiveMode(boolean b) {

    }

    @Override
    public void setOnAdMetadataChangedListener(@Nullable OnAdMetadataChangedListener onAdMetadataChangedListener) {

    }

    @Override
    public void setOnPaidEventListener(@Nullable OnPaidEventListener onPaidEventListener) {
        _onPaidEventHandler = onPaidEventListener;
    }

    @Override
    public void setServerSideVerificationOptions(@Nullable ServerSideVerificationOptions serverSideVerificationOptions) {

    }

    @Override
    public void show(@NonNull Activity activity, @NonNull OnUserEarnedRewardListener onUserEarnedRewardListener) {
        NDebug.Open("Rewarded",
                activity,
                new Callback() {
                    @Override
                    public void onShow() {
                        _fullScreenCallback.onAdShowedFullScreenContent();
                        _onPaidEventHandler.onPaidEvent(AdValue.zza(AdValue.PrecisionType.PRECISE, "USD", (long)(_cost * 1000000)));
                    }

                    @Override
                    public void onClick() {
                        _fullScreenCallback.onAdClicked();
                    }

                    @Override
                    public void onReward() {
                        onUserEarnedRewardListener.onUserEarnedReward(new RewardItem() {
                            @Override
                            public int getAmount() {
                                return 1;
                            }

                            @NonNull
                            @Override
                            public String getType() {
                                return "sim reward";
                            }
                        });
                    }

                    @Override
                    public void onClose() {
                        _fullScreenCallback.onAdDismissedFullScreenContent();
                    }
                }
        );
    }
}