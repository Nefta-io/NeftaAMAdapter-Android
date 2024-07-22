package com.google.ads.mediation.nefta;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.mediation.MediationBannerListener;
import com.nefta.sdk.NeftaPlugin;
import com.nefta.sdk.Placement;

class AdBannerNeftaRequest extends NeftaRequest {
    public MediationBannerListener _listener;

    public AdBannerNeftaRequest(String placementId, NeftaAdapter adapter, MediationBannerListener listener) {
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
        _placement._isManualPosition = true;
        NeftaPlugin._instance.Show(placement._id);
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
        _adapter = null;
        _placement = null;
    }
}
