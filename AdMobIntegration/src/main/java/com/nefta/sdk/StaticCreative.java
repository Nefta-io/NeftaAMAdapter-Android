package com.nefta.sdk;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

class StaticCreative extends Creative {

    private AsyncTask<String, Void, Bitmap> _loadTask;

    private View.OnLayoutChangeListener _onLayoutChange;

    Bitmap _image;
    ImageView _imageView;
    FrameLayout.LayoutParams _imageLayout;

    StaticCreative(Placement placement, BidResponse bid) {
        super(placement, bid);
    }

    StaticCreative(Placement placement, int width, int height, String url) {
        super(placement);
        _width = width;
        _height = height;
        _adMarkup = url;
    }

    @Override
    void Load() {
        _placement._publisher.CalculateSize(this);

        _loadTask = new ImageLoadTask().execute(_adMarkup);
    }

    private class ImageLoadTask extends AsyncTask<String, Void, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(RestHelper._connectTimeoutMs);
                connection.setReadTimeout(RestHelper._readTimeoutMs);
                connection.connect();
                InputStream input = connection.getInputStream();
                _image = BitmapFactory.decodeStream(input);
                input.close();
                _placement.OnCreativeLoadingEnd(null);
                return _image;
            } catch (IOException e) {
                _placement.OnCreativeLoadingEnd(e.getMessage());
                return null;
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    void Show() {
        if (_placement._type == Placement.Types.Banner) {
            if (_imageView == null) {
                _imageView = new ImageView(_placement._publisher._activity);
            }
            _imageView.setImageBitmap(_image);
            _imageView.setOnTouchListener(new View.OnTouchListener() {
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

                        if (d / _placement._publisher._info._scale < 5) {
                            _placement._publisher.OnClick(_placement, null);
                        }
                        _isPointerDown = false;
                    }
                    return true;
                }
            });
            _imageLayout = new FrameLayout.LayoutParams(10, 10);
            _placement._publisher.ShowBannerCreative(_placement, this, _imageView, _imageLayout);
        } else {
            _placement.ShowFullscreen(this);
        }
        super.Show();
    }

    @Override
    void GracefulShow(String placementId) {
        _firePlacementIdShow = placementId;

        _onLayoutChange = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (v.getVisibility() == View.VISIBLE) {
                    OnCreativeShow();
                }
            }
        };

        _imageView = new ImageView(_placement._publisher._activity);
        _imageView.addOnLayoutChangeListener(_onLayoutChange);
        Show();
    }

    @Override
    void Hide(boolean hide) {
        _imageView.setVisibility(hide ? View.GONE : View.VISIBLE);
    }

    @Override
    void Dispose() {
        if (_loadTask != null) {
            _loadTask.cancel(true);
            _loadTask = null;
        }

        if (_onLayoutChange != null) {
            _imageView.removeOnLayoutChangeListener(_onLayoutChange);
        }

        if (_imageView != null) {
            _imageView.setImageDrawable(null);
            _imageView = null;
            _imageLayout = null;
        }

        _placement = null;

        if (_image != null) {
            _image.recycle();
            _image = null;
        }
    }
}
