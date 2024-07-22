package com.nefta.sdk;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import com.google.ads.mediation.nefta.R;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class RendererActivity extends Activity implements SurfaceHolder.Callback {

    interface IOnVideoSurfaceReady {
        void Invoke(SurfaceHolder surfaceHolder);
    }

    private final int _violet = Color.rgb(153, 0, 255);

    private Publisher _publisher;
    private Placement _placement;
    private WebCreative _webCreative;
    private StaticCreative _staticCreative;
    private VideoCreative _videoCreative;
    boolean _hasFocus;

    ViewGroup _fullScreenView;
    private FrameLayout.LayoutParams _closeZoneLayout;
    private ImageView _imageView;
    private FrameLayout.LayoutParams _imageLayout;
    private ImageView _xImage;
    private TextView _countdownText;
    private ProgressBar _countdownBar;
    private SurfaceView _videoView;
    private FrameLayout.LayoutParams _videoViewLayout;
    private NProgressBar _progressBar;
    private View _closeConfirmation;
    private RendererActivity.IOnVideoSurfaceReady _onVideoSurfaceReady;
    private RendererActivity.IOnVideoSurfaceReady _onVideoSurfaceDestroyed;
    private ObjectAnimator _closeAnimator;
    private Runnable _updater;
    private long _lastUpdateTime;
    private int _watchTimeMs;
    private int _displayedCountdown;
    private int _minWatchTimeMs;
    private boolean _isWaitTime;
    private boolean childHandlesInput;

    public boolean _isCloseConfirmationShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        _publisher = NeftaPlugin._instance._publisher;
        Creative creative = _publisher._pendingRenderCreative;

        NeftaPlugin.NLogI("RenderActivity onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_renderer);

        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(flags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(layoutParams);
        }

        _fullScreenView = findViewById(R.id.root);
        _fullScreenView.setOnTouchListener(new View.OnTouchListener() {
            private boolean _isPointerDown;
            private float _x;
            private float _y;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (childHandlesInput) {
                    return false;
                }
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
                        _publisher.OnClick(_placement, null);
                    }
                    _isPointerDown = false;
                }
                return true;
            }
        });

        _closeZoneLayout = (FrameLayout.LayoutParams) findViewById(R.id.closeZone).getLayoutParams();

        _xImage = (ImageView) findViewById(R.id.closeImage);
        _xImage.setOnClickListener(this::OnXClick);

        _countdownBar = findViewById(R.id.countdownBar);
        _countdownText = (TextView) findViewById(R.id.countdownText);

        _closeConfirmation = findViewById(R.id.closeConfirmation);
        _closeConfirmation.setTranslationY(130 * _publisher._info._scale);
        _isCloseConfirmationShown = false;
        View _resumeButton = findViewById(R.id.resumeButton);
        _resumeButton.setOnClickListener(this::OnResumeVideoClick);
        View _closeButton = findViewById(R.id.closeButton);
        _closeButton.setOnClickListener(this::OnCloseVideoClick);

        _hasFocus = true;

        _placement = creative._placement;
        _placement._rendererActivity = this;
        SetCreative(creative);
        _publisher._pendingRenderCreative = null;
    }

    public void SetCreative(Creative creative) {
        Clear();

        _minWatchTimeMs = creative._minWatchTime * 1000;
        childHandlesInput = creative._redirectClickUrl == null || creative._redirectClickUrl.length() == 0;

        if (creative instanceof WebCreative) {
            _webCreative = (WebCreative) creative;
            _fullScreenView.addView(_webCreative._webController, _webCreative._webController._layoutParams);
            _webCreative._webController.setVisibility(View.VISIBLE);
        } else if (creative instanceof StaticCreative) {
            _staticCreative = (StaticCreative) creative;
            if (_imageView == null) {
                _imageView = new ImageView(this);
                _imageLayout = new FrameLayout.LayoutParams(creative._renderWidth, creative._renderHeight, Gravity.NO_GRAVITY);
                _fullScreenView.addView(_imageView, _imageLayout);
            } else {
                _imageLayout.width = creative._renderWidth;
                _imageLayout.height = creative._renderHeight;
                _imageView.setVisibility(View.VISIBLE);
            }
            _imageLayout.setMargins(creative._rect.left, creative._rect.top, creative._rect.right, creative._rect.bottom);
            _imageView.setImageBitmap(_staticCreative._image);
        } else {
            _videoCreative = (VideoCreative) creative;
            _onVideoSurfaceReady = _videoCreative::OnVideoSurfaceReady;
            _onVideoSurfaceDestroyed = _videoCreative::OnVideoSurfaceDestroyed;

            FrameLayout.LayoutParams progressBarLayout = null;
            if (_videoView == null) {
                _videoView = new SurfaceView(this);
                _videoViewLayout = new FrameLayout.LayoutParams(creative._renderWidth, creative._renderHeight, Gravity.NO_GRAVITY);
                _fullScreenView.addView(_videoView, _videoViewLayout);

                int progressBarWidth = creative._rootWidth - creative._safeArea.left - creative._safeArea.right;
                _progressBar = new NProgressBar(this, Color.rgb(40, 0, 66), _violet);

                progressBarLayout = new FrameLayout.LayoutParams(progressBarWidth, _publisher._progressBarHeight, Gravity.BOTTOM | Gravity.START);
                _fullScreenView.addView(_progressBar, progressBarLayout);
            } else {
                _videoViewLayout.width = creative._renderWidth;
                _videoViewLayout.height = creative._renderHeight;
                progressBarLayout = (FrameLayout.LayoutParams)_progressBar.getLayoutParams();
                _progressBar.setVisibility(View.VISIBLE);
            }
            _videoView.getHolder().addCallback(this);
            _videoViewLayout.setMargins(creative._rect.left, creative._rect.top, creative._rect.right, creative._rect.bottom);
            progressBarLayout.setMargins(creative._safeArea.left, 0, creative._safeArea.right, creative._safeArea.bottom);

            _watchTimeMs = 0;
            _displayedCountdown = -1;

            _progressBar.SetProgress(0);
        }
        _closeZoneLayout.setMargins(0, creative._safeArea.top + _publisher._closeMargin, creative._safeArea.right + _publisher._closeMargin, 0);

        _lastUpdateTime = -1;
        _updater = new Runnable() {
            @Override
            public void run() {
                if (_videoCreative != null) {
                    float progress = _videoCreative.OnUpdate();
                    if (progress >= 0) {
                        _progressBar.SetProgress(progress);
                    }
                }

                if (_hasFocus && !_isCloseConfirmationShown) {
                    long now = System.currentTimeMillis();
                    if (_lastUpdateTime != -1) {
                        _watchTimeMs += (int) (now - _lastUpdateTime);
                    }
                    _lastUpdateTime = now;
                } else {
                    _lastUpdateTime = -1;
                }
                UpdateXButton(false);

                _placement._publisher._handler.postDelayed(this, 16);
            }
        };
        _placement._publisher._handler.postDelayed(_updater, 16);

        UpdateXButton(true);
    }

    private void UpdateXButton(boolean force) {
        boolean isWaitTime = _minWatchTimeMs > 0 && _watchTimeMs < _minWatchTimeMs;
        if (isWaitTime) {
            int countdown = (int) Math.ceil((_minWatchTimeMs - _watchTimeMs) / 1000f);
            if (countdown != _displayedCountdown || force) {
                _displayedCountdown = countdown;
                _countdownText.setText(String.valueOf(_displayedCountdown));
            }
            int progress = (int) (_watchTimeMs * 100 / (float)_minWatchTimeMs);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                _countdownBar.setProgress(progress, true);
            } else {
                _countdownBar.setProgress(progress);
            }
        }

        if (isWaitTime != _isWaitTime || force) {
            _isWaitTime = isWaitTime;

            _countdownText.setVisibility(isWaitTime ? View.VISIBLE : View.GONE);
            _countdownBar.setVisibility(isWaitTime ? View.VISIBLE : View.GONE);
            _xImage.setVisibility(!isWaitTime ? View.VISIBLE : View.GONE);
        }
    }

    void Clear() {
        _placement._publisher._handler.removeCallbacks(_updater);
        _updater = null;

        if (_webCreative != null) {
            _fullScreenView.removeView(_webCreative._webController);
            _webCreative.Dispose();
            _webCreative = null;
        } else if (_staticCreative != null) {
            _imageView.setVisibility(View.GONE);
            _imageView.setImageBitmap(null);
            _staticCreative.Dispose();
            _staticCreative = null;
        } else if (_videoCreative != null) {
            _videoView.setVisibility(View.GONE);
            _progressBar.setVisibility(View.GONE);
            _onVideoSurfaceReady = null;
            _onVideoSurfaceDestroyed = null;
            _videoCreative.Dispose();
            _videoCreative = null;
        }
    }

    void Finish() {
        Clear();
        _placement = null;
        _publisher = null;

        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onBackPressed() {
        if (_isCloseConfirmationShown) {
            if (_closeAnimator == null || !_closeAnimator.isRunning()) {
                Animate(false);
            }
        } else if (_videoCreative != null && _videoCreative._isRewardAvailable) {
            if (_closeAnimator == null || !_closeAnimator.isRunning()) {
                Animate(true);
            }
        } else {
            _placement.NextCreative();
        }
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        if (_onVideoSurfaceReady != null) {
            _onVideoSurfaceReady.Invoke(surfaceHolder);
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        if (_onVideoSurfaceDestroyed != null) {
            _onVideoSurfaceDestroyed.Invoke(surfaceHolder);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        UpdateFocus(true);
    }

    @Override
    protected void onPause() {
        super.onPause();

        UpdateFocus(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (_placement != null) {
            _publisher.CloseMain(_placement._id, true);
            _placement = null;
        }
        _publisher = null;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        UpdateFocus(hasFocus);
    }

    private void UpdateFocus(boolean hasFocus) {
        if (hasFocus == _hasFocus) {
            return;
        }

        _hasFocus = hasFocus;
        if (!_isCloseConfirmationShown) {
            if (_webCreative != null) {
                _webCreative.OnPause(!hasFocus);
            } else if (_videoCreative != null) {
                _videoCreative.OnPause(!hasFocus);
            }
        }
    }

    private void OnXClick(View view) {
        if (_isCloseConfirmationShown) {
            return;
        }
        if (_videoCreative != null && _videoCreative._isRewardAvailable) {
            if (_closeAnimator == null || !_closeAnimator.isRunning()) {
                Animate(true);
            }
        } else {
            _placement.NextCreative();
        }
    }

    private void OnResumeVideoClick(View view) {
        Animate(false);
    }

    private void OnCloseVideoClick(View view) {
        _isCloseConfirmationShown = false;
        _closeConfirmation.setTranslationY(130 * _publisher._info._scale);

        if (_videoCreative != null && _videoCreative._trackingEventsClose != null) {
            for(String url : _videoCreative._trackingEventsClose) {
                _placement._publisher._restHelper.MakeGetRequest(url);
            }
            _videoCreative._trackingEventsClose = null;
        }

        _placement.NextCreative();
    }

    private void Animate(boolean show) {
        float idlePosition = 130 * _publisher._info._scale;
        float shownPosition = -25 * _publisher._info._scale;

        _isCloseConfirmationShown = show;
        if (show) {
            _closeAnimator = ObjectAnimator.ofFloat(_closeConfirmation, View.TRANSLATION_Y, idlePosition, shownPosition);
            _closeAnimator.setDuration(200);
            _closeAnimator.start();

            if (_videoCreative != null) {
                _videoCreative.OnPause(true);
                if (_videoCreative._trackingEventsPause != null) {
                    for (String url : _videoCreative._trackingEventsPause) {
                        _placement._publisher._restHelper.MakeGetRequest(url);
                    }
                }
            }
        } else {
            _closeConfirmation.bringToFront();
            _closeAnimator = ObjectAnimator.ofFloat(_closeConfirmation, View.TRANSLATION_Y, shownPosition, idlePosition);
            _closeAnimator.setDuration(200);
            _closeAnimator.start();

            if (_videoCreative != null) {
                _videoCreative.OnPause(false);
                if (_videoCreative._trackingEventsResume != null) {
                    for (String url : _videoCreative._trackingEventsResume) {
                        _placement._publisher._restHelper.MakeGetRequest(url);
                    }
                }
            }
        }
    }
}