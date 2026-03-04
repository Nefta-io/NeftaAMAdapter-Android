package com.nefta.am;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;
import com.google.android.gms.ads.ResponseInfo;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

public class SInterstitialAd extends InterstitialAd {

    private final String _adUnitId;
    private double _cost;
    private FullScreenContentCallback _fullScreenCallback;
    private OnPaidEventListener _onPaidEventHandler;
    public boolean _hasFill;

    private SInterstitialAd(String adUnitId, double cost) {
        _adUnitId = adUnitId;
        _cost = cost;
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

    @NonNull
    @Override
    public String getAdUnitId() {
        return _adUnitId;
    }

    @Override
    public void setPlacementId(long var1) { }

    @Override
    public long getPlacementId() { return 0; }

    public static void load(@NonNull Context context, @NonNull String adUnitId, @NonNull AdRequest adRequest, @NonNull InterstitialAdLoadCallback loadCallback) {
        boolean hasFloor = adRequest.getNetworkExtrasBundle(AdMobAdapter.class) != null;
        String status = adUnitId + " loading " + (hasFloor ? " as Optimized" : "as Default");
        if (InterstitialSim.Instance._trackA._request == adRequest) {
            InterstitialSim.Instance.ToggleTrackA(true, true);
            RewardedSim.Instance._aStatus.setText(status);
        } else {
            InterstitialSim.Instance.ToggleTrackB(true, true);
            RewardedSim.Instance._bStatus.setText(status);
        }
        new Thread(() -> {
            try {
                InterstitialSim.Track r = InterstitialSim.Instance._trackA._request == adRequest ? InterstitialSim.Instance._trackA : InterstitialSim.Instance._trackB;
                r._loadSelection = 0;
                while (r._loadSelection == 0) {
                    Thread.sleep(1000);
                }
                if (r._loadSelection == 1) {
                    SInterstitialAd s = new SInterstitialAd(adUnitId, 0.002);
                    loadCallback.onAdLoaded(s);
                } else if (r._loadSelection == 2) {
                    SInterstitialAd s = new SInterstitialAd(adUnitId, 0.001);
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
    public void setOnPaidEventListener(@Nullable OnPaidEventListener onPaidEventListener) {
        _onPaidEventHandler = onPaidEventListener;
    }

    @Override
    public void show(@NonNull Activity activity) {
        SimulatorAd.Instance.Show("Interstitial",
                () -> {
                    _fullScreenCallback.onAdShowedFullScreenContent();
                    _onPaidEventHandler.onPaidEvent(AdValue.zza(AdValue.PrecisionType.PRECISE, "USD", (long)(_cost * 1000000)));
                },
                () -> {
                    _fullScreenCallback.onAdClicked();
                },
                null,
                () -> {
                    _fullScreenCallback.onAdDismissedFullScreenContent();
                });
    }
}