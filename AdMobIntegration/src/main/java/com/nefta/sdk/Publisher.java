package com.nefta.sdk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public class Publisher {

    private static final String _loadedVideoPref = "nefta.videos";

    static class PlacementRequest {
        public Placement.Modes _mode;
        public Placement.Position _position;
        public HashMap<String, Object> _parameters;

        public PlacementRequest() {
            _mode = Placement.Modes.Manual;
            _position = Placement.Position.TOP;
        }

        public PlacementRequest(Placement.Modes mode, Placement.Position position) {
            _mode = mode;
            _position = position;
        }
    }

    private Bidder _bidder;
    private boolean _placementsLoaded;
    private final HashMap<String, PlacementRequest> _idRequestsBeforeInit;
    private HashMap<String, Placement> _placements;
    private boolean _showHiddenOnClose;

    private ViewGroup _rootView;
    private final ArrayList<String> _loadedVideos;
    private Placement _currentPlacement;
    int _closeMargin = 5;
    int _closeSize;
    int _closeObstruction;
    Creative _pendingRenderCreative;

    final Rect _offsets = new Rect();
    final Rect _safeArea = new Rect();
    int _progressBarHeight;

    Activity _activity;
    final NeftaPlugin.Info _info;
    private final NeftaPlugin.State _state;

    final NeftaPlugin _plugin;
    Handler _handler;
    final RestHelper _restHelper;
    boolean _isEnabled;
    boolean _isMuted;

    ExecutorService GetExecutor() {
        return _plugin._executor;
    }

    public Publisher(NeftaPlugin plugin, NeftaPlugin.Info info, NeftaPlugin.State state, RestHelper restHelper) {
        _plugin = plugin;
        _info = info;
        _state = state;
        _restHelper = restHelper;
        _loadedVideos = new ArrayList<>();
        _idRequestsBeforeInit = new HashMap<>();
    }

    public void Init() {
        _bidder = new Bidder();
        _bidder.Init(_info, _state, _restHelper, this::OnBid);

        _placements = new HashMap<>();

        String loadedVideos = _plugin._preferences.getString(_loadedVideoPref, null);
        if (loadedVideos != null && loadedVideos.length() > 0) {
            String[] videoHashes = loadedVideos.split(",");
            for (String videoHash : videoHashes) {
                VideoCreative.TryDeleteVideo(NeftaPlugin._context, videoHash);
            }
        }
        _plugin._preferencesEditor.putString(_loadedVideoPref, null);

        _handler = new Handler(Looper.getMainLooper());

        _closeSize = ScreenToRenderSize(_info._scale, 32);
        _closeMargin = ScreenToRenderSize(_info._scale, 5);
        _closeObstruction = _closeSize + _closeMargin;
        _progressBarHeight = ScreenToRenderSize(_info._scale, 5);
    }

    boolean IsInitCallRequired() {
        return _isEnabled && !_placementsLoaded;
    }

    void PrepareRenderer(Activity activity) {
        _activity = activity;
        _handler.post(new Runnable() {
            @Override
            public void run() {
                _rootView = (ViewGroup) activity.findViewById(android.R.id.content).getRootView();

                UpdateOffsets();
            }
        });
    }

    void Enable(boolean enable) {
        _isEnabled = enable;

        if (!_isEnabled) {
            Close();
        }
    }

    void EnableBanner(String id, boolean enable) {
        if (!_placementsLoaded) {
            if(_idRequestsBeforeInit.containsKey(id)) {
                _idRequestsBeforeInit.get(id)._mode = Placement.Modes.Continuous;
            } else {
                _idRequestsBeforeInit.put(id, new PlacementRequest(Placement.Modes.Continuous, Placement.Position.TOP));
            }
            return;
        }

        Placement placement = _placements.get(id);
        if (placement == null) {
            return;
        }

        _handler.post(new Runnable() {
            @Override
            public void run() {
                NeftaPlugin.NLogI("EnableBanner: " + id + ": " + enable);
                if (enable) {
                    SetPlacementMode(id, Placement.Modes.Continuous);
                    if (placement._isHidden) {
                        HidePlacement(placement, false);
                    }
                } else {
                    if (placement.IsShowing()) {
                        HidePlacement(placement, true);
                    }
                }
            }
        });
    }

    private void HidePlacement(Placement placement, boolean hide) {
        placement.Hide(hide);
    }

    void SetPlacementPosition(final String id, Placement.Position position) {
        if (!_placementsLoaded) {
            if(_idRequestsBeforeInit.containsKey(id)) {
                _idRequestsBeforeInit.get(id)._position = position;
            } else {
                _idRequestsBeforeInit.put(id, new PlacementRequest(Placement.Modes.Manual, position));
            }
            return;
        }
        Placement placement = _placements.get(id);
        if (placement != null) {
            placement._position = position;
        }
    }

    void SetPlacementMode(final String id, Placement.Modes mode) {
        if (!_placementsLoaded) {
            if (_idRequestsBeforeInit.containsKey(id)) {
                _idRequestsBeforeInit.get(id)._mode = mode;
            } else {
                _idRequestsBeforeInit.put(id, new PlacementRequest(mode, Placement.Position.TOP));
            }
            return;
        }

        Placement placement = _placements.get(id);
        if (placement == null) {
            return;
        }
        NeftaPlugin.NLogI("SetPlacementMode: " + id + ": " + mode);
        placement._mode = mode;

        if (mode == Placement.Modes.ScheduledBid) {
            if (placement._availableBid == null) {
                Bid(id);
            }
        } else if (mode == Placement.Modes.ScheduledLoad || mode == Placement.Modes.Continuous) {
            if (mode == Placement.Modes.Continuous && placement._type == Placement.Types.Banner) {
                if (placement.CanShow()) {
                    Show(id);
                }
            }
            Load(id);
        }
    }

    void SetCustomParameter(final String id, final String key, final Object value) {
        if (!_placementsLoaded) {
            PlacementRequest request;
            if (_idRequestsBeforeInit.containsKey(id)) {
                request = _idRequestsBeforeInit.get(id);
            } else {
                request = new PlacementRequest(Placement.Modes.Manual, Placement.Position.TOP);
                _idRequestsBeforeInit.put(id, request);
            }
            if (request._parameters == null) {
                request._parameters = new HashMap<String, Object>();
            }
            request._parameters.put(key, value);
            return;
        }
        Placement placement = _placements.get(id);
        if (placement != null) {
            if (placement._customParameters == null) {
                placement._customParameters = new HashMap<String, Object>();
            }
            placement._customParameters.put(key, value);
        }
    }

    void Bid(final String id) {
        if (!_placementsLoaded) {
            NeftaPlugin.NLogI("Bid delayed ('" + id + ")");
            if (_idRequestsBeforeInit.containsKey(id)) {
                _idRequestsBeforeInit.get(id)._mode = Placement.Modes.ScheduledBid;
            } else {
                _idRequestsBeforeInit.put(id, new PlacementRequest(Placement.Modes.ScheduledBid, Placement.Position.TOP));
            }
            return;
        }
        Placement placement = _placements.get(id);
        if (placement == null) {
            NeftaPlugin.NLogW("Bid invalid placement (" + id + ")");
            return;
        }

        if (placement.IsBidding()) {
            NeftaPlugin.NLogW("Bid already in progress " + placement._type + "(" + id + ")");
            return;
        }

        NeftaPlugin.NLogI("Bid " + placement._type + "(" + id + ")");

        _plugin.UpdateResolution();
        placement.OnBidStart();
        _bidder.Bid(placement);
    }

    void Load(final String id) {
        if (!_placementsLoaded) {
            if (_idRequestsBeforeInit.containsKey(id)) {
                PlacementRequest request = _idRequestsBeforeInit.get(id);
                request._mode = Placement.Modes.ScheduledLoad;
            } else {
                _idRequestsBeforeInit.put(id, new PlacementRequest(Placement.Modes.ScheduledLoad, Placement.Position.TOP));
            }
            return;
        }

        Placement placement = _placements.get(id);
        if (placement == null) {
            return;
        }

        if (placement._bufferBid != null) {
            if (!placement.IsLoading()) {
                OnLoadSuccessCallback(placement);
            }
            return;
        } else if (placement._availableBid == null) {
            if (placement._mode == Placement.Modes.Manual) {
                placement._mode = Placement.Modes.ScheduledLoad;
            }
            if (!placement.IsBidding()) {
                Bid(id);
            }
            return;
        }

        placement.Load();
        if (_plugin._callbackInterface != null) {
            _plugin._callbackInterface.IOnLoadStart(placement._id);
        }
        if (_plugin.OnLoadStart != null) {
            _handler.post(new Runnable() {
                @Override
                public void run() {
                    _plugin.OnLoadStart.Invoke(placement);
                }
            });
        }
    }

    boolean IsReady(final Placement.Types type) {
        for (Map.Entry<String, Placement> p : _placements.entrySet()) {
            Placement placement = p.getValue();
            if (placement._type == type && placement._bufferBid != null && !placement.IsLoading()) {
                return true;
            }
        }
        return false;
    }

    boolean IsReady(final String placementId) {
        Placement placement = _placements.get(placementId);
        if (placement != null) {
            return placement._bufferBid != null && !placement.IsLoading();
        }
        return false;
    }

    void Show(Placement.Types type) {
        for (Map.Entry<String, Placement> p : _placements.entrySet()) {
            Placement placement = p.getValue();
            if (placement._type == type && placement.CanShow()) {
                Show(placement._id);
                return;
            }
        }
    }

    void Show(final String id) {
        if (Looper.myLooper() == _handler.getLooper()) {
            ShowMain(id);
            return;
        }
        _handler.post(new Runnable() {
            @Override
            public void run() {
                ShowMain(id);
            }
        });
    }

    void ShowMain(final String id) {
        Placement placement = null;
        _showHiddenOnClose = false;
        for (Map.Entry<String, Placement> p : _placements.entrySet()) {
            String pKey = p.getKey();
            Placement pValue = p.getValue();
            if (pKey.equals(id)) {
                placement = pValue;
            } else if (pValue.IsShowing()) {
                if (pValue._type == Placement.Types.Banner) {
                    pValue.Hide(true);
                    _showHiddenOnClose = true;
                } else {
                    CloseMain(pKey, false);
                }
            }
        }

        if (placement == null) {
            return;
        }

        if (placement._isHidden) {
            HidePlacement(placement, false);
        }
        if (!placement.CanShow()) {
            long showTime = 0;
            if (placement._showTime > 0) {
                showTime = System.currentTimeMillis() - placement._showTime;
            }
            NeftaPlugin.NLogW("Placement " + placement._type + "(" + id + ") could not be shown; available bid: " + placement._availableBid + ", buffered bid: " + placement._bufferBid + ", show time: " + showTime);
            return;
        }

        _currentPlacement = placement;
        _currentPlacement.Show();

        if (_plugin._callbackInterface != null) {
            _plugin._callbackInterface.IOnShow(placement._id);
        }
        if (_plugin.OnShow != null) {
            _plugin.OnShow.Invoke(placement);
        }

        if (placement._mode == Placement.Modes.Continuous) {
            Bid(id);
        }
    }

    void ShowBannerCreative(Placement placement, Creative creative, View view, FrameLayout.LayoutParams layoutParams) {
        view.setVisibility(View.VISIBLE);

        layoutParams.width = creative._renderWidth;
        layoutParams.height = creative._renderHeight;
        layoutParams.gravity = Gravity.CENTER;
        layoutParams.setMargins(0, 0, 0, 0);
        ViewGroup parent = (ViewGroup) view.getParent();

        if (placement._isManualPosition) {
            if (parent == _rootView) {
                parent.removeView(view);
            }
            ViewGroup.LayoutParams currentParams = view.getLayoutParams();
            if (currentParams instanceof RelativeLayout.LayoutParams) {
                currentParams.width = creative._renderWidth;
                currentParams.height = creative._renderHeight;
                view.setLayoutParams(currentParams);
            } else {
                view.setLayoutParams(layoutParams);
            }
        } else {
            if (parent == null) {
                _rootView.addView(view);
                parent = _rootView;
            }

            if (parent == _rootView) {
                int top = 0;
                int bottom = 0;
                layoutParams.gravity = Gravity.CENTER;
                if (placement._position == Placement.Position.TOP) {
                    top = _offsets.top;
                    layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                } else if (placement._position == Placement.Position.BOTTOM) {
                    bottom = _offsets.bottom;
                    layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                }

                layoutParams.setMargins(0, top, 0, bottom);
                view.setLayoutParams(layoutParams);
            }
        }
        view.bringToFront();
    }

    void ParentView(View view, FrameLayout.LayoutParams layout) {
        layout.setMargins(-9000, -9000, 0, 0);
        _rootView.addView(view, layout);
    }

    void OnUpdate() {
        if (!_isEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<String, Placement> p : _placements.entrySet()) {
            Placement placement = p.getValue();

            placement.OnUpdate(now);
        }
    }

    void CalculateSize(Creative creative) {
        if (creative._placement._type == Placement.Types.Banner) {
            creative._scale = (int)(_info._scale * 100 + 0.5);
            creative._renderWidth = (int)(creative._scale * 0.01 * creative._width);
            creative._renderHeight = (int)(creative._scale * 0.01 * creative._height);
            return;
        }

        if (_currentPlacement != null && _currentPlacement._rendererActivity != null && _currentPlacement._renderedCreative != null) {
            Creative renderedCreative = _currentPlacement._renderedCreative;

            creative._rootWidth = renderedCreative._rootWidth;
            creative._rootHeight = renderedCreative._rootHeight;
            creative._orientation = renderedCreative._orientation;
            creative._safeArea = renderedCreative._safeArea;
        } else {
            creative._rootWidth = _plugin._info._width;
            creative._rootHeight = _plugin._info._height;
            WindowManager windowManager = (WindowManager)NeftaPlugin._context.getSystemService(Context.WINDOW_SERVICE);
            creative._orientation = windowManager.getDefaultDisplay().getRotation();
            creative._safeArea = new Rect(_safeArea.left, _safeArea.top, _safeArea.right, _safeArea.bottom);
        }

        int left = creative._safeArea.left;
        int top = creative._safeArea.top;
        int right = creative._safeArea.right;
        int bottom = creative._safeArea.bottom;

        if (creative instanceof VideoCreative) {
            bottom += _progressBarHeight;
        }
        int availableWidth = creative._rootWidth - left - right;
        int availableHeight = creative._rootHeight - top - bottom;

        float availableScale = (float) availableWidth / availableHeight;
        float creativeScale = (float) creative._width / creative._height;

        boolean creativeIsVerticallyConstrained = availableScale > creativeScale;
        float scale;
        if (creativeIsVerticallyConstrained) {
            scale = (float)availableHeight / creative._height;
            int renderWidth = (int) (creative._width * scale);

            int horizontalPadding = availableWidth - renderWidth;
            if (horizontalPadding < _closeObstruction + _closeObstruction) {
                availableWidth -= _closeObstruction;
                right += _closeObstruction;
                if (horizontalPadding < _closeObstruction) {
                    scale = (float) availableWidth / creative._width;
                }
            }
        } else {
            scale = (float)availableWidth / creative._width;
            int renderHeight = (int) (creative._height * scale);

            int verticalPadding = availableHeight - renderHeight;
            if (verticalPadding < _closeObstruction + _closeObstruction) {
                availableHeight -= _closeObstruction;
                top += _closeObstruction;
                if (verticalPadding < _closeObstruction) {
                    scale = (float) availableHeight / creative._height;
                }
            }
        }

        creative._scale = (int)(scale * 100 + 0.5);

        creative._renderWidth = (int)(creative._scale * 0.01 * creative._width);
        creative._renderHeight = (int)(creative._scale * 0.01 * creative._height);

        int widthPadding = (int)((availableWidth - creative._renderWidth) * 0.5f);
        int heightPadding = (int)((availableHeight - creative._renderHeight) * 0.5f);
        creative._rect = new Rect(left + widthPadding, top + heightPadding, right + widthPadding, bottom + heightPadding);
    }

    void ShowFullscreen(Creative creative) {
        _pendingRenderCreative = creative;
        Class<?> activityClass = RendererPortrait.class;
        if (creative._orientation == Surface.ROTATION_90) {
            activityClass = RendererLandscape.class;
        } else if (creative._orientation == Surface.ROTATION_180) {
            activityClass = RendererReversePortrait.class;
        } else if (creative._orientation == Surface.ROTATION_270) {
            activityClass = RendererReverseLandscape.class;
        }
        Intent adIntent = new Intent(_activity, activityClass);
        adIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        _activity.startActivity(adIntent);
        _activity.overridePendingTransition(0, 0);
    }

    void Close() {
        _handler.post(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<String, Placement> p : _placements.entrySet()) {
                    if (p.getValue()._renderedBid != null) {
                        CloseMain(p.getKey(), false);
                    }
                }
            }
        });
    }

    void Close(final String id) {
        if (Looper.myLooper() == _handler.getLooper()) {
            CloseMain(id, true);
            return;
        }
        _handler.post(new Runnable() {
            @Override
            public void run() {
                CloseMain(id, true);
            }
        });
    }

    void CloseMain(String id, boolean showHidden) {
        Placement placement = _placements.get(id);
        if (placement == null) {
            return;
        }

        placement.Close();

        if (_currentPlacement == placement) {
            _currentPlacement = null;
        }

        if (_plugin._callbackInterface != null) {
            _plugin._callbackInterface.IOnClose(placement._id);
        }
        if (_plugin.OnClose != null) {
            _plugin.OnClose.Invoke(placement);
        }

        if (showHidden && _showHiddenOnClose) {
            for (Map.Entry<String, Placement> p : _placements.entrySet()) {
                placement = p.getValue();
                if (placement._isHidden) {
                    HidePlacement(placement, false);
                    _currentPlacement = placement;
                    return;
                }
            }
        }
    }

    void OnPause(final boolean paused) {
        if (_currentPlacement != null && _pendingRenderCreative == null) {
            _currentPlacement.OnPause(paused);
        }
    }

    public PlacementView GetViewForPlacement(Placement placement, boolean show) {
        if (show) {
            if (placement._bufferedCreative != null) {
                placement._bufferedCreative.GracefulShow(placement._id);

                if (placement._bufferedCreative instanceof StaticCreative) {
                    StaticCreative staticCreative = (StaticCreative) placement._bufferedCreative;
                    return new PlacementView(staticCreative._imageView, staticCreative._imageLayout);
                } else if (placement._bufferedCreative instanceof WebCreative) {
                    WebController webController = ((WebCreative) placement._bufferedCreative)._webController;
                    return new PlacementView(webController, webController._layoutParams);
                }
            }
        } else {
            if (placement._renderedCreative != null) {
                if (placement._renderedCreative instanceof StaticCreative) {
                    StaticCreative staticCreative = (StaticCreative) placement._bufferedCreative;
                    return new PlacementView(staticCreative._imageView, staticCreative._imageLayout);
                } else if (placement._renderedCreative instanceof WebCreative) {
                    WebController webController = ((WebCreative) placement._renderedCreative)._webController;
                    return new PlacementView(webController, webController._layoutParams);
                }
            }
        }
        return null;
    }

    void OnClick(final Placement placement, final String customRedirectUrl) {
        placement.OnClick(customRedirectUrl);

        if (_plugin._callbackInterface != null) {
            _plugin._callbackInterface.IOnClick(placement._id);
        }
        if (_plugin.OnClick != null) {
            _plugin.OnClick.Invoke(placement);
        }
    }

    public void ParseAdUnits(final String response, JSONObject responseJson) throws JSONException {
        _placements.clear();

        if (responseJson == null) {
            responseJson = new JSONObject(response);
        }
        JSONArray adUnits = responseJson.getJSONArray("ad_units");
        for (int i = 0; i < adUnits.length(); i++) {
            final JSONObject adUnit = adUnits.getJSONObject(i);
            String type = adUnit.getString("type");
            Placement placement = null;
            final String id = adUnit.getString("id");
            int width = adUnit.has("width") && !adUnit.isNull("width") ? adUnit.getInt("width") : 1;
            int height = adUnit.has("height") && !adUnit.isNull("height") ? adUnit.getInt("height") : 1;
            if ("banner".equals(type)) {
                if (width <= 1) {
                    width = 320;
                }
                if (height <= 1) {
                    height = 50;
                }
                placement = new Placement(id, width, height, Placement.Position.TOP, Placement.Types.Banner, this);
            } else if ("interstitial".equals(type)) {
                if (width <= 1) {
                    width = 320;
                }
                if (height <= 1) {
                    height = 480;
                }
                placement = new Placement(id, width, height, Placement.Position.CENTER, Placement.Types.Interstitial, this);
            } else if ("rewarded_video".equals(type)) {
                placement = new Placement(id, width, height, Placement.Position.CENTER, Placement.Types.RewardedVideo, this);
            } else {
                NeftaPlugin.NLogW("Unrecognized adUnit type: " + type);
                continue;
            }
            _placements.put(id, placement);
        }

        NeftaPlugin.NLogI("Placements loaded: "+ _placements.size());
        _placementsLoaded = true;

        if (_plugin._callbackInterface != null) {
            _plugin._callbackInterface.IOnReady(response);
        }
        if (_plugin.OnReady != null) {
            _handler.post(new Runnable() {
                @Override
                public void run() {
                    _plugin.OnReady.Invoke(_placements);
                }
            });
        }

        for (Map.Entry<String, Placement> p : _placements.entrySet()) {
            String id = p.getKey();
            Placement placement = p.getValue();

            placement._mode = Placement.Modes.Manual;
            placement._position = Placement.Position.TOP;
            if (_idRequestsBeforeInit.containsKey(id)) {
                PlacementRequest request = _idRequestsBeforeInit.get(id);
                placement._mode = request._mode;
                placement._position = request._position;
                placement._customParameters = request._parameters;
                _idRequestsBeforeInit.remove(id);
            }

            if (placement._mode == Placement.Modes.ScheduledBid) {
                if (!placement.IsBidding()) {
                    Bid(placement._id);
                }
            } else if (placement._mode == Placement.Modes.ScheduledLoad || placement._mode == Placement.Modes.Continuous) {
                if (!placement.IsLoading()) {
                    Load(placement._id);
                }
            }
        }
    }

    private void OnBid(final Placement placement, final BidResponse bidResponse) {
        _handler.post(new Runnable() {
            @Override
            public void run() {
                UpdateOffsets();

                float bidPrice = -1;
                if (bidResponse != null) {
                    bidPrice = bidResponse._price;
                }
                placement.OnBid(bidResponse);

                if (_plugin._callbackInterface != null) {
                    _plugin._callbackInterface.IOnBid(placement._id, bidPrice);
                }

                if (_plugin.OnBid != null) {
                    _plugin.OnBid.Invoke(placement, bidResponse);
                }

                if (placement._mode == Placement.Modes.ScheduledLoad || placement._mode == Placement.Modes.Continuous) {
                    if (bidResponse != null) {
                        Load(placement._id);
                    }
                }
            }
        });
    }

    void OnLoadingEnd(Placement placement, final String error) {
        if (error != null) {
            placement.OnLoadFail(error);
            if (_plugin._callbackInterface != null) {
                _plugin._callbackInterface.IOnLoadFail(placement._id, error);
            }
            if (_plugin.OnLoadFail != null) {
                _handler.post(new Runnable() {
                    @Override
                    public void run() {
                        _plugin.OnLoadFail.Invoke(placement, error);
                    }
                });
            }

            if (placement._mode == Placement.Modes.Continuous) {
                Bid(placement._id);
            }
            return;
        }

        placement.OnLoad();

        OnLoadSuccessCallback(placement);
    }

    private void OnLoadSuccessCallback(Placement placement) {
        int w = 0;
        int h = 0;
        if (placement._bufferedCreative != null) {
            w = placement._bufferedCreative._renderWidth;
            h = placement._bufferedCreative._renderHeight;
        }
        final int fw = w;
        final int fh = h;
        if (_plugin._callbackInterface != null) {
            _plugin._callbackInterface.IOnLoad(placement._id, fw, fh);
        }
        if (_plugin.OnLoad != null) {
            _handler.post(new Runnable() {
                @Override
                public void run() {
                    _plugin.OnLoad.Invoke(placement, fw, fh);
                }
            });
        }
    }

    void UpdateCachedVideoPrefs(boolean add, String hashCode) {
        if (add) {
            _loadedVideos.add(hashCode);
        } else {
            for (int i = 0; i < _loadedVideos.size(); i++) {
                if (_loadedVideos.get(i) == hashCode) {
                    _loadedVideos.remove(i);
                    break;
                }
            }
        }

        UpdateCachedVideoPrefs();
    }

    private void UpdateCachedVideoPrefs() {
        StringBuilder loadedVideos = new StringBuilder();
        for (int l = 0; l < _loadedVideos.size(); l++) {
            if (l > 0) {
                loadedVideos.append(',');
            }
            loadedVideos.append(_loadedVideos.get(l));
        }
        String ls = loadedVideos.toString();
        NeftaPlugin.NLogI("Updated cached videos:"+ ls);
        _plugin._preferencesEditor.putString(_loadedVideoPref, ls);
        _plugin._preferencesEditor.apply();
    }

    int ScreenToRenderSize(float scale, int size) {
        return (int) (size * scale + 0.5f);
    }

    private void UpdateOffsets() {
        _offsets.left = 0;
        _offsets.top = 0;
        _offsets.right = 0;
        _offsets.bottom = 0;

        _safeArea.left = 0;
        _safeArea.top = 0;
        _safeArea.right = 0;
        _safeArea.bottom = 0;

        if (_activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }

        Window window = _activity.getWindow();
        View decorView = window.getDecorView();
        WindowInsets windowInsets = decorView.getRootWindowInsets();
        if (windowInsets == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets insets = windowInsets.getInsets(
                    WindowInsets.Type.displayCutout() |
                            WindowInsets.Type.statusBars() |
                            WindowInsets.Type.navigationBars());
            _offsets.top = insets.top;
            _offsets.left = insets.left;
            _offsets.right = insets.right;
            _offsets.bottom = insets.bottom;

            DisplayCutout cutout = _activity.getWindowManager().getDefaultDisplay().getCutout();
            if (cutout != null) {
                _safeArea.left = cutout.getSafeInsetLeft();
                _safeArea.top = cutout.getSafeInsetTop();
                _safeArea.right = cutout.getSafeInsetRight();
                _safeArea.bottom = cutout.getSafeInsetBottom();
            }
        } else {
            DisplayCutout cutout = windowInsets.getDisplayCutout();
            if (cutout != null) {
                _offsets.top = cutout.getSafeInsetTop();
                _offsets.left = cutout.getSafeInsetLeft();
                _offsets.right = cutout.getSafeInsetRight();
                _offsets.bottom = cutout.getSafeInsetBottom();
            }
        }
        NeftaPlugin.NLogD("Update offsets: "+ _offsets + " safeArea: + " + _safeArea);
    }
}
