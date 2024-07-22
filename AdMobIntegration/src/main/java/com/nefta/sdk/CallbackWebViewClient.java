package com.nefta.sdk;

import android.graphics.Bitmap;
import android.os.Build;
import android.webkit.HttpAuthHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

class CallbackWebViewClient extends WebViewClient {

    private Placement _placement;

    CallbackWebViewClient(Placement placement) {
        _placement = placement;
    }

    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        if (_placement == null) {
            return;
        }

        String error = errorCode + " " + description + " " + failingUrl;

        _placement.OnCreativeLoadingEnd(error);
        _placement = null;
    }


    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        if (url.endsWith("ico")) {
            return new WebResourceResponse("image/ico", null, null);
        }

        return super.shouldInterceptRequest(view, url);
    }

    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        if (_placement == null) {
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        if (!request.isForMainFrame()) {
            return;
        }

        String error = String.valueOf(errorResponse.getStatusCode());
        _placement.OnCreativeLoadingEnd(error);
        _placement = null;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {

    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (_placement == null || url.equals("about:blank")) {
            return;
        }

        _placement.OnCreativeLoadingEnd(null);
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {
        handler.cancel();
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        NeftaPlugin.NLogI("Click on custom url: "+ url);
        _placement._publisher.OnClick(_placement, url);
        return true;
    }
}