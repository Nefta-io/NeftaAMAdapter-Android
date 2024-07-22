package com.nefta.sdk;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

class WebController extends ViewGroup {

    private Publisher _publisher;
    private int _width;
    private int _height;
    WebView _webView;
    FrameLayout.LayoutParams _layoutParams;
    Creative.OnCreativeShow _onShow;

    @SuppressLint("ClickableViewAccessibility")
    public WebController(Creative creative) {
        super(creative._placement._publisher._activity);

        _publisher = creative._placement._publisher;

        _webView = new WebView(NeftaPlugin._context);
        _webView.setFocusable(true);
        _webView.setFocusableInTouchMode(true);
        _webView.setScrollContainer(false);
        _webView.setVerticalScrollBarEnabled(false);
        _webView.setHorizontalScrollBarEnabled(false);
        _webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        _webView.setWebViewClient(new CallbackWebViewClient(creative._placement));
        _webView.resumeTimers();

        WebSettings webSettings = _webView.getSettings();
        webSettings.setSupportZoom(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);
        webSettings.setLoadWithOverviewMode(false);
        webSettings.setUseWideViewPort(false);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setGeolocationEnabled(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setMediaPlaybackRequiresUserGesture(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);

        setBackgroundColor(Color.TRANSPARENT);

        _width = creative._renderWidth;
        _height = creative._renderHeight;

        _layoutParams = new FrameLayout.LayoutParams(_width, _height);
        addView(_webView, new FrameLayout.LayoutParams(_width, _height));

        _publisher.ParentView(this, _layoutParams);

        _webView.setInitialScale(creative._scale);

        if (creative._redirectClickUrl != null && creative._redirectClickUrl.length() > 0) {
            _webView.setOnTouchListener(new View.OnTouchListener() {
                private boolean _isPointerDown;
                private float _x;
                private float _y;

                @Override
                public boolean onTouch(View view, MotionEvent event) {
                    int action = event.getAction();
                    if (action == MotionEvent.ACTION_DOWN) {
                        _x = event.getX();
                        _y = event.getY();
                        _isPointerDown = true;
                    } else if (_isPointerDown && action == MotionEvent.ACTION_UP) {
                        float dx = event.getX() - _x;
                        float dy = event.getY() - _y;
                        float d = (float) Math.sqrt(dx * dx + dy * dy);

                        if (d / _publisher._info._scale < 5) {
                            _publisher.OnClick(creative._placement, null);
                        }
                        _isPointerDown = false;
                    }
                    return true;
                }
            });
        }

        if (creative._isLink) {
            _webView.loadUrl(creative._adMarkup);
        } else {
            String html = creative._adMarkup.trim();
            if (!html.startsWith("<html>")) {
                html = "<html><head></head><body style=\"margin:0\">"+ html +"</body></html>";
            }
            _webView.loadData(html, "text/html", "UTF-8");
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        boolean isExternalMove = _layoutParams.leftMargin != -9000;
        if (changed) {
            int availableWidth = r - l;
            int availableHeight = b - t;
            int lOffset = (int)((availableWidth - _width) * 0.5f + 0.5f);
            int tOffset = (int)((availableHeight - _height) * 0.5f + 0.5f);
            _webView.layout(lOffset, tOffset, _width, _height);
        }

        if (isExternalMove) {
            if (_onShow != null) {
                _onShow.invoke();
                _onShow = null;
            }
        }
    }

    public void OnPause(boolean paused) {
        if (paused) {
            _webView.onPause();
            if (_webView.getVisibility() == View.VISIBLE) {
                _webView.pauseTimers();
            }
        } else {
            _webView.onResume();
            _webView.resumeTimers();
        }
    }

    public void Hide(boolean hide) {
        setVisibility(hide ? View.GONE : View.VISIBLE);
    }

    public void Dispose() {
        _publisher._handler.post(new Runnable() {
            @Override
            public void run() {
                setVisibility(View.GONE);
                _webView.loadUrl("about:blank");
                _webView.clearCache(true);
                _webView.freeMemory();
                _webView.pauseTimers();
                _webView = null;
            }
        });
        _publisher = null;
    }
}
