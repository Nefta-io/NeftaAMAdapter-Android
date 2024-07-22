package com.google.ads.mediation.nefta;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.mediation.MediationInterstitialListener;
import com.nefta.sdk.Placement;

class InterstitialNeftaRequest extends NeftaRequest {
    public MediationInterstitialListener _listener;
    public InterstitialNeftaRequest(String placementId, NeftaAdapter adapter, MediationInterstitialListener listener) {
        _placementId = placementId;
        _adapter = adapter;
        _listener = listener;
    }

    @Override
    public void OnLoadFail(String error) {
        _listener.onAdFailedToLoad(_adapter, new AdError(11, error, "OnLoadFail"));
    }

    @Override
    public void OnLoad(Placement placement) {
        _placement = placement;
        _listener.onAdLoaded(_adapter);
    }

    @Override
    public void OnShow() {
        _listener.onAdOpened(_adapter);
    }

    @Override
    public void OnClick() {
        _listener.onAdClicked(_adapter);
    }

    @Override
    public void OnRewarded() {
    }

    @Override
    public void OnClose() {
        _listener.onAdClosed(_adapter);
    }
}