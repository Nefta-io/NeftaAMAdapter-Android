package com.nefta.sdk;

import android.graphics.Rect;

import java.util.ArrayList;

public abstract class Creative {

    interface OnCreativeShow {
        void invoke();
    }

    protected Placement _placement;
    protected String _firePlacementIdShow;

    ArrayList<String> _trackingEventsStart = new ArrayList<>();
    ArrayList<String> _trackingClickUrls = new ArrayList<>();
    String _redirectClickUrl;
    public int _width;
    public int _height;
    public int _scale;
    public int _renderWidth;
    public int _renderHeight;
    int _rootWidth;
    int _rootHeight;
    Rect _rect;
    int _orientation;
    Rect _safeArea;
    String _adMarkup;
    boolean _isLink;
    int _minWatchTime;
    boolean _isLoaded;

    Creative(Placement placement) {
        _placement = placement;
    }

    Creative(Placement placement, BidResponse bid) {
        _placement = placement;
        _width = bid._width;
        _height = bid._height;

        _adMarkup = bid._adMarkup;
        _isLink = bid._adMarkupType == BidResponse.AdMarkupTypes.HtmlLink || bid._adMarkupType == BidResponse.AdMarkupTypes.ImageLink;
        _minWatchTime = bid._minWatchTime;

        _redirectClickUrl = bid._redirectClickUrl;
    }

    void Load() {
    }

    void Show() {
        if (_trackingEventsStart != null) {
            for (String url : _trackingEventsStart) {
                _placement._publisher._restHelper.MakeGetRequest(url);
            }
            _trackingEventsStart = null;
        }
    }

    void GracefulShow(String placementId) {
    }

    void OnPause(boolean paused) {
    }

    void Hide(boolean hide) {
    }

    void Dispose() {
    }

    protected void OnCreativeShow() {
        if (_placement != null && _firePlacementIdShow != null) {
            _placement._publisher.ShowMain(_firePlacementIdShow);
            _firePlacementIdShow = null;
        }
    }
}
