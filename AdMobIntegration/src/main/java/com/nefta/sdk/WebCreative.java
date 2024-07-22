package com.nefta.sdk;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;

class WebCreative extends Creative {
    WebController _webController;

    WebCreative(Placement placement, int width, int height, String adMarkup, Boolean isLink) {
        super(placement);
        _width = width;
        _height = height;
        _adMarkup = adMarkup;
        _isLink = isLink;
    }

    WebCreative(Placement placement, BidResponse bid) {
        super(placement, bid);
    }

    @Override
    void Load() {
        _placement._publisher.CalculateSize(this);

        Handler handler = _placement._publisher._handler;
        if (Looper.myLooper() == handler.getLooper()) {
            LoadMain();
        } else {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    LoadMain();
                }
            });
        }
    }

    @Override
    void Show() {
        if (_placement._type == Placement.Types.Banner) {
            _placement._publisher.ShowBannerCreative(_placement, this, _webController, _webController._layoutParams);
        } else {
            ViewGroup parent = (ViewGroup) _webController.getParent();
            if (parent != null) {
                parent.removeView(_webController);
            }

            _webController._layoutParams.gravity = Gravity.NO_GRAVITY;
            _webController._layoutParams.width = _renderWidth;
            _webController._layoutParams.height = _renderHeight;
            _webController._layoutParams.setMargins(_rect.left, _rect.top, _rect.right, _rect.bottom);

            _placement.ShowFullscreen(this);
        }
        super.Show();
    }

    @Override
    void GracefulShow(String placementId) {
        if (_webController != null) {
            _firePlacementIdShow = placementId;

            _webController._onShow = this::OnCreativeShow;
            Show();
        }
    }

    @Override
    void OnPause(boolean paused) {
        _webController.OnPause(paused);
    }

    @Override
    void Hide(boolean hide) {
        _webController.Hide(hide);
    }

    @Override
    void Dispose() {
        if (_webController != null) {
            _webController.Dispose();
            _webController = null;
        }

        _placement = null;
    }

    private void LoadMain() {
        _webController = new WebController(this);
    }
}
