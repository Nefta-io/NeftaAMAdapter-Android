package com.google.ads.mediation.nefta;

import com.nefta.sdk.Placement;

abstract class NeftaRequest {
    NeftaAdapter _adapter;
    String _placementId;
    Placement _placement;
    public int _state;

    public abstract void OnLoadFail(String error);
    public abstract void OnLoad(Placement placement);
    public abstract void OnShow();
    public abstract void OnClick();
    public abstract void OnRewarded();
    public abstract void OnClose();
}