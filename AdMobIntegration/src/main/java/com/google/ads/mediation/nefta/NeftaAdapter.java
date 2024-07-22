package com.google.ads.mediation.nefta;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.mediation.Adapter;
import com.google.android.gms.ads.mediation.InitializationCompleteCallback;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.MediationBannerAdapter;
import com.google.android.gms.ads.mediation.MediationBannerListener;
import com.google.android.gms.ads.mediation.MediationConfiguration;
import com.google.android.gms.ads.mediation.MediationInterstitialAdapter;
import com.google.android.gms.ads.mediation.MediationInterstitialListener;
import com.google.android.gms.ads.mediation.MediationRewardedAd;
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback;
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration;
import com.google.android.gms.ads.mediation.VersionInfo;
import com.nefta.sdk.NeftaPlugin;
import com.nefta.sdk.Placement;

import java.util.ArrayList;
import java.util.List;

@Keep
public class NeftaAdapter extends Adapter implements MediationBannerAdapter, MediationInterstitialAdapter, MediationRewardedAd {

    private static final String SAMPLE_AD_UNIT_KEY = "parameter";
    private NeftaRequest _request;

    public static NeftaPlugin _plugin;

    static ArrayList<NeftaRequest> _listeners;
    static AdBannerNeftaRequest _lastBanner;

    @Override
    public void initialize(Context context, InitializationCompleteCallback initializationCompleteCallback, List<MediationConfiguration> mediationConfigurations) {
        if (_plugin != null) {
            initializationCompleteCallback.onInitializationSucceeded();
            return;
        }

        if (context == null) {
            initializationCompleteCallback.onInitializationFailed("Initialization Failed: Context is null.");
            return;
        }

        String appId = mediationConfigurations.get(0).getServerParameters().getString("app_id");
        if (!TrySetPlugin(NeftaPlugin.Init(context, appId))) {
            initializationCompleteCallback.onInitializationFailed("Initialization Failed: Missing app_id");
            return;
        }

        initializationCompleteCallback.onInitializationSucceeded();
    }

    private boolean TrySetPlugin(NeftaPlugin plugin) {
        if (_plugin != null) {
            return true;
        }
        if (plugin == null) {
            return false;
        }
        _plugin = plugin;
        _listeners = new ArrayList<NeftaRequest>();
        _plugin.OnLoadFail = NeftaAdapter::OnLoadFail;
        _plugin.OnLoad = NeftaAdapter::OnLoad;
        _plugin.OnShow = NeftaAdapter::OnShow;
        _plugin.OnClick = NeftaAdapter::OnClick;
        _plugin.OnReward = NeftaAdapter::OnReward;
        _plugin.OnClose = NeftaAdapter::OnClose;
        _plugin.EnableAds(true);
        return true;
    }

    @NonNull
    @Override
    public VersionInfo getVersionInfo() {
        return new VersionInfo(1, 1, 1);
    }

    @NonNull
    @Override
    public VersionInfo getSDKVersionInfo() {
        String[] splits = NeftaPlugin.Version.split("\\.");
        int major = Integer.parseInt(splits[0]);
        int minor = Integer.parseInt(splits[1]);
        int micro = Integer.parseInt(splits[2]);
        return new VersionInfo(major, minor, micro);
    }

    @Override
    public void onDestroy() {
        Log.i("NeftaPluginAM", "OnDestroy: " + _request + ": " + _request._placement._type);
        boolean isLastBanner = _lastBanner == _request;
        if (_request._placement._type != Placement.Types.Banner || isLastBanner) {
            _plugin.Close(_request._placement._id);
            if (isLastBanner) {
                _lastBanner = null;
            }
        } else {
            _request.OnClose();
        }
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onResume() {
    }

    @Override
    public void requestBannerAd(Context context, MediationBannerListener listener, Bundle serverParameters, AdSize adSize, MediationAdRequest mediationAdRequest, Bundle mediationExtras) {
        if (!TrySetPlugin(NeftaPlugin._instance)) {
            listener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_INTERNAL_ERROR);
            return;
        }

        if (serverParameters.containsKey(SAMPLE_AD_UNIT_KEY)) {
            _plugin.PrepareRenderer((Activity) context);

            String placementId = serverParameters.getString(SAMPLE_AD_UNIT_KEY);
            _request = new AdBannerNeftaRequest(placementId, this, listener);
            _listeners.add(_request);

            _plugin.Load(placementId);
        } else {
            listener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_INVALID_REQUEST);
        }
    }

    @Override
    @NonNull
    public View getBannerView() {
        return NeftaPlugin._instance.GetViewForPlacement(_request._placement, false)._view;
    }

    @Override
    public void requestInterstitialAd(Context context, MediationInterstitialListener listener, Bundle serverParameters, MediationAdRequest mediationAdRequest, Bundle mediationExtras) {
        if (!TrySetPlugin(NeftaPlugin._instance)) {
            listener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_INTERNAL_ERROR);
            return;
        }

        if (serverParameters.containsKey(SAMPLE_AD_UNIT_KEY)) {
            _plugin.PrepareRenderer((Activity) context);

            String placementId = serverParameters.getString(SAMPLE_AD_UNIT_KEY);
            _request = new InterstitialNeftaRequest(placementId, this, listener);
            _listeners.add(_request);

            _plugin.Load(placementId);
        } else {
            listener.onAdFailedToLoad(this, AdRequest.ERROR_CODE_INVALID_REQUEST);
        }
    }

    @Override
    public void showInterstitial() {
        if (_plugin.IsReady(_request._placementId)) {
            _plugin.Show(_request._placementId);
        }
    }

    @Override
    public void showAd(@NonNull Context context) {
        _plugin.PrepareRenderer((Activity) context);

        if (_plugin.IsReady(_request._placementId)) {
            _plugin.Show(_request._placementId);
        }
    }

    @Override
    public void loadRewardedAd(MediationRewardedAdConfiguration mediationRewardedAdConfiguration, MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback> mediationAdLoadCallback) {
        Bundle serverParameters = mediationRewardedAdConfiguration.getServerParameters();
        if (serverParameters.containsKey(SAMPLE_AD_UNIT_KEY)) {
            String placementId = serverParameters.getString(SAMPLE_AD_UNIT_KEY);

            _request = new RewardedVideoNeftaRequest(placementId, this, mediationAdLoadCallback);
            _listeners.add(_request);

            _plugin.Load(placementId);
        } else {
            mediationAdLoadCallback.onFailure(new AdError(10, "Missing placementId", "loadRewardedAd"));
        }
    }

    private static void OnLoadFail(Placement placement, String error) {
        for (int i = 0; i < _listeners.size(); i++) {
            NeftaRequest r = _listeners.get(i);
            if (r._placementId.equals(placement._id) && r._state == 0) {
                r.OnLoadFail(error);
                _listeners.remove(i);
                return;
            }
        }
    }

    private static void OnLoad(Placement placement, int width, int height) {
        for (int i = 0; i < _listeners.size(); i++) {
            NeftaRequest r = _listeners.get(i);
            if (r._placementId.equals(placement._id) && r._state == 0) {
                r._state = 1;
                r.OnLoad(placement);
                return;
            }
        }
    }

    private static void OnShow(Placement placement) {
        for (int i = 0; i < _listeners.size(); i++) {
            NeftaRequest r = _listeners.get(i);
            if (r._placementId.equals(placement._id) && r._state == 1) {
                r._state = 2;
                r.OnShow();
                return;
            }
        }
    }

    private static void OnClick(Placement placement) {
        for (int i = 0; i < _listeners.size(); i++) {
            NeftaRequest r = _listeners.get(i);
            if (r._placementId.equals(placement._id) && r._state == 2) {
                r.OnClick();
                return;
            }
        }
    }

    private static void OnReward(Placement placement) {
        for (int i = 0; i < _listeners.size(); i++) {
            NeftaRequest r = _listeners.get(i);
            if (r._placementId.equals(placement._id) && r._state == 2) {
                r.OnRewarded();
            }
        }
    }

    private static void OnClose(Placement placement) {
        for (int i = 0; i < _listeners.size(); i++) {
            NeftaRequest r = _listeners.get(i);
            if (r._placementId.equals(placement._id) && r._state == 2) {
                r.OnClose();
                _listeners.remove(i);
                return;
            }
        }
    }
}