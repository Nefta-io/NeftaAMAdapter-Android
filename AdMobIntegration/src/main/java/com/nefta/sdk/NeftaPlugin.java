package com.nefta.sdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NeftaPlugin implements DefaultLifecycleObserver {
    private static final String TAG = "NeftaPlugin";
    static final String _sequenceNumberPref = "nefta.sequenceNumber";
    private static final String _sessionNumberPref = "nefta.sessionNumber";
    private static final String _pauseTimePref = "nefta.pauseTime";
    private static final String _sessionDurationPref = "nefta.sessionDuration";
    private static final String _nuidPref = "nefta.user_id";
    private static final String _isDebugEventPref = "nefta.isDebugEvent";
    private static final String _heartbeatFrequencyPref = "nefta.heartbeatFrequency";

    public static final String Version = "3.3.1";

    static class Info {
        public String _appId;
        public String _restUrl;
        public String _restEventUrl;
        public String _userAgent;
        public String _publisherUserId;
        public String _language;
        public String _country;
        public int _width;
        public int _height;
        public int _dpi;
        public float _scale;
        public boolean _isTablet;
        public String _bundleId;
        public String _bundleVersion;
        public int _utfOffset;
    }

    static class State {
        public String _nuid;
        public int _sequenceNumber;
        public int _sessionId;
        public long _resumeTime;
        public long _pauseTime;
        public long _sessionDuration;
        public boolean _isDebugEvent;
        public int _heartbeat_frequency;
    }

    public interface IOnReady {
        void Invoke(HashMap<String, Placement> placements);
    }

    public interface IOnBid {
        void Invoke(Placement placement, BidResponse bidResponse);
    }

    public interface IOnLoadFail {
        void Invoke(Placement placement, String error);
    }

    public interface IOnLoad {
        void Invoke(Placement placement, int width, int height);
    }

    public interface IOnChange {
        void Invoke(Placement placement);
    }

    public interface IOnLog {
        void Invoke(String log);
    }

    private boolean _isInitInProgress;
    private long _timeSinceLastInitCall;
    private String _cachedInitResponse;

    Info _info;
    State _state;

    SharedPreferences _preferences;
    SharedPreferences.Editor _preferencesEditor;
    Publisher _publisher;
    static Context _context;
    private final Handler _handler;
    ExecutorService _executor;
    RestHelper _restHelper;
    public CallbackInterface _callbackInterface;

    public IOnReady OnReady;
    public IOnBid OnBid;
    public IOnChange OnLoadStart;
    public IOnLoadFail OnLoadFail;
    public IOnLoad OnLoad;
    public IOnChange OnShow;
    public IOnChange OnClick;
    public IOnChange OnClose;
    public IOnChange OnReward;
    public static IOnLog OnLog;

    public static NeftaPlugin _instance;

    public static NeftaEvents Events;

    @SuppressLint("HardwareIds")
    private NeftaPlugin(Context context, final String appId) {
        _info = new Info();
        _state = new State();
        _handler = new Handler(Looper.getMainLooper());

        _context = context;

        if (appId == null || appId.length() == 0) {
            throw new RuntimeException("NeftaPlugin can not be initialized with null or empty appId");
        }

        _info._restUrl = "https://rtb.nefta.app";
        _info._restEventUrl = "https://ef.nefta.app/event/game";
        _info._bundleId = _context.getPackageName();
        _info._bundleVersion = "0";
        String preferenceName = "Nefta";
        try {
            PackageInfo packageInfo = _context.getPackageManager().getPackageInfo(_info._bundleId, 0);
            _info._bundleVersion = packageInfo.versionName;
            preferenceName = packageInfo.packageName + ".v2.playerprefs";
        } catch (PackageManager.NameNotFoundException e) {
            NeftaPlugin.NLogW("Error getting info: " + e.getMessage());
        }

        _preferences = _context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
        _preferencesEditor = _preferences.edit();

        _state._sequenceNumber = _preferences.getInt(_sequenceNumberPref, 0);
        _state._sessionId = _preferences.getInt(_sessionNumberPref, 0);
        _state._pauseTime = _preferences.getLong(_pauseTimePref, 0);
        _state._sessionDuration = _preferences.getLong(_sessionDurationPref, 0);
        _state._resumeTime = -1;
        _state._nuid = _preferences.getString(_nuidPref, null);

        _state._isDebugEvent = _preferences.getBoolean(_isDebugEventPref, false);
        _state._heartbeat_frequency = _preferences.getInt(_heartbeatFrequencyPref, 0);

        _info._appId = appId;
        Locale locale = Locale.getDefault();
        _info._language = locale.getLanguage();
        _info._country = locale.getCountry();
        _info._utfOffset = TimeZone.getDefault().getOffset((new Date()).getTime()) / 1000;

        DisplayMetrics displayMetrics = UpdateResolution();
        _info._dpi = displayMetrics.densityDpi;
        _info._scale = displayMetrics.density;
        double diagonalInInches = Math.sqrt(Math.pow((double)_info._width / displayMetrics.xdpi, 2) + Math.pow((double)_info._height / displayMetrics.ydpi, 2));
        _info._isTablet = diagonalInInches >= 6.5 && (_context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;

        _executor = Executors.newCachedThreadPool();

        _restHelper = new RestHelper(_context, _executor);
        Events = new NeftaEvents(_context, _info, _state, _preferences, _preferencesEditor, _restHelper);

        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (_state._resumeTime == -1) {
                    return;
                }

                boolean isInitCallRequired = _state._nuid == null || _state._nuid.length() == 0 || (_publisher != null && _publisher.IsInitCallRequired());

                if (isInitCallRequired && !_isInitInProgress && _timeSinceLastInitCall + 5000 <= System.currentTimeMillis()) {
                    String networkError = _restHelper.IsNetworkAvailable();
                    if (networkError != null) {
                        NLogI("Init call canceled: " + networkError);
                    } else {
                        InitConfiguration();
                    }
                }
                if (_publisher != null) {
                    _publisher.OnUpdate();
                }
                Events.OnUpdate();
            }
        };
        timer.scheduleAtFixedRate(task, 1000, 1000);

        NeftaPlugin.NLogI("Core initialized " + _state._nuid + " n: " + _state._sequenceNumber + " s: " + _state._sessionId);

        if (Looper.myLooper() == Looper.getMainLooper()) {
            InitMain();
        } else {
            _handler.post(() -> InitMain());
        }
    }

    private void InitMain() {
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
        onResume(null);
    }

    public static NeftaPlugin Init(Context context, final String appId) {
        if (_instance == null) {
            _instance = new NeftaPlugin(context, appId);
        }

        return _instance;
    }

    static void NLogI(final String log) {
        if (OnLog != null) {
            OnLog.Invoke(log);
        }
        Log.i(NeftaPlugin.TAG, Thread.currentThread().getName() + " " + log);
    }

    static void NLogW(final String log) {
        if (OnLog != null) {
            OnLog.Invoke(log);
        }
        Log.w(NeftaPlugin.TAG, Thread.currentThread().getName() + " " + log);
    }

    static void NLogD(final String log) {
        if (OnLog != null) {
            OnLog.Invoke(log);
        }
        Log.d(NeftaPlugin.TAG, Thread.currentThread().getName() + " " + log);
    }

    public void EnableAds(boolean enable) {
        NLogI("EnableAds: "+ enable);
        if (_publisher == null) {
            if (!enable) {
                return;
            }
            _publisher = new Publisher(this, _info, _state, _restHelper);
            _publisher.Init();
        }

        _publisher.Enable(enable);

        if (enable && _cachedInitResponse != null) {
            try {
                _publisher.ParseAdUnits(_cachedInitResponse, null);
                _cachedInitResponse = null;
            } catch (Exception e) {
                NLogW("Error parsing ad units: "+ e.getMessage());
            }
        }
    }

    public void Record(final String gameEvent) {
        Events.Record(gameEvent);
    }

    public void SetPublisherUserId(final String publisherUserId) {
        _info._publisherUserId = publisherUserId;
    }

    public void SetListener(CallbackInterface callbackInterface) {
        _callbackInterface = callbackInterface;
    }

    public void PrepareRenderer(Activity activity) {
        _publisher.PrepareRenderer(activity);
    }

    public void EnableBanner(String placementId, boolean enable) {
        _publisher.EnableBanner(placementId, enable);
    }

    public void SetPlacementPosition(final String placementId, Placement.Position position) {
        _publisher.SetPlacementPosition(placementId, position);
    }
    public void SetPlacementPosition(final String placementId, int position) {
        _publisher.SetPlacementPosition(placementId, Placement.Position.FromInt(position));
    }

    public void SetPlacementMode(final String placementId, Placement.Modes mode) {
        _publisher.SetPlacementMode(placementId, mode);
    }
    public void SetPlacementMode(final String placementId, int mode) {
        _publisher.SetPlacementMode(placementId, Placement.Modes.FromInt(mode));
    }

    public void SetCustomParameter(final String placementId, final String key, final Object value) {
        _publisher.SetCustomParameter(placementId, key, value);
    }

    public void Bid(final String id) {
        _publisher.Bid(id);
    }
    public void Load(final String id) { _publisher.Load(id); }
    public boolean IsReady(final String placementId) { return _publisher.IsReady(placementId); }
    public void Show(final String id) {_publisher.Show(id); }

    public void Close() { _publisher.Close(); }
    public void Close(final String id) { _publisher.Close(id); }
    public void Mute(boolean mute) {
        _publisher._isMuted = mute;
    }
    public String GetNuid(boolean present) {
        NLogI("Nuid: " + _state._nuid);

        if (present) {
            ClipboardManager clipboard = (ClipboardManager) _context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("nuid", _state._nuid);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(_context, _state._nuid, Toast.LENGTH_SHORT).show();
        }
        return _state._nuid;
    }

    @Override public void onCreate(LifecycleOwner owner) {}
    @Override public void onStart(LifecycleOwner owner) {}
    @Override public void onStop(LifecycleOwner owner) {}
    @Override public void onDestroy(LifecycleOwner owner) {}

    @Override
    public void onResume(LifecycleOwner owner) {
        if (_state._resumeTime != -1) {
            return;
        }
        _state._resumeTime = System.currentTimeMillis();
        if (_state._resumeTime < _state._pauseTime) {
            _state._pauseTime = _state._resumeTime;
        }
        long pauseDuration = _state._resumeTime - _state._pauseTime;

        NLogI("OnResume " + _state._pauseTime +" "+ _state._resumeTime +" "+ pauseDuration);
        if (pauseDuration > 30 * 60 * 1000) {
            boolean isFirstRun = _state._sequenceNumber == 0;

            Long previousSessionDurationInSeconds = null;
            if (isFirstRun) {

            } else {
                _state._sessionId++;
                _preferencesEditor.putInt(_sessionNumberPref, _state._sessionId);
                previousSessionDurationInSeconds = _state._sessionDuration / 1000;
            }

            Events.AddSessionEvent(NeftaEvents.SessionCategory.SessionStart, null, previousSessionDurationInSeconds, null, true);
            _state._sessionDuration = 0;
        }

        Events.OnResume();

        if (_publisher != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                _publisher.OnPause(false);
            } else {
                _handler.post(new Runnable() {
                    @Override
                    public void run() {
                        _publisher.OnPause(false);
                    }
                });
            }
        }
    }

    @Override
    public void onPause(LifecycleOwner owner) {
        if (_state._resumeTime == -1) {
            return;
        }
        NLogI("OnPause " + _state._resumeTime);

        _state._pauseTime = System.currentTimeMillis();
        _state._sessionDuration += _state._pauseTime - _state._resumeTime;
        _state._resumeTime = -1;

        Events.OnPause();
        _preferencesEditor.putLong(_pauseTimePref, _state._pauseTime);
        _preferencesEditor.putLong(_sessionDurationPref, _state._sessionDuration);
        _preferencesEditor.apply();

        if (_publisher != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                _publisher.OnPause(true);
            } else {
                _handler.post(new Runnable() {
                    @Override
                    public void run() {
                        _publisher.OnPause(true);
                    }
                });
            }
        }
    }

    public PlacementView GetViewForPlacement(Placement placement, boolean show) {
        return _publisher.GetViewForPlacement(placement, show);
    }

    DisplayMetrics UpdateResolution() {
        WindowManager windowManager = (WindowManager) _context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        Point point = new Point();
        windowManager.getDefaultDisplay().getRealSize(point);
        _info._width = point.x;
        _info._height = point.y;

        return displayMetrics;
    }

    static JSONObject GetDeviceData(Info info) throws JSONException {
        JSONObject device = new JSONObject();
        device.put("ua", info._userAgent);
        int deviceType = 1; // mobile/tablet
        if (info._isTablet) {
            deviceType = 5; // tablet
        } else {
            deviceType = 4; // phone
        }
        device.put("devicetype", deviceType);
        device.put("make", android.os.Build.MANUFACTURER);
        device.put("model", Build.MODEL);
        device.put("os", "Android");
        device.put("osv", Build.VERSION.RELEASE);
        device.put("hwv", Build.HARDWARE);
        device.put("w", info._width);
        device.put("h", info._height);
        device.put("ppi", info._dpi);
        device.put("pxratio", info._scale);
        device.put("language", info._language);
        JSONObject geo = new JSONObject();
        geo.put("country", info._country);
        geo.put("utcoffset", info._utfOffset);
        device.put("geo", geo);
        return device;
    }

    private void InitConfiguration() {
        JSONObject requestObject = new JSONObject();
        try {
            if (_info._userAgent == null) {
                _info._userAgent = WebSettings.getDefaultUserAgent(_context);
            }

            requestObject.put("app_id", _info._appId);
            if (_state._nuid != null && _state._nuid.length() > 0) {
                requestObject.put("nuid", _state._nuid);
            }
            requestObject.put("bundle_id", _info._bundleId);
            requestObject.put("sdk_version", Version);
            requestObject.put("session_id", _state._sessionId);
            requestObject.put("app_version", _info._bundleVersion);
            requestObject.put("device_time", System.currentTimeMillis());
            requestObject.put("device", NeftaPlugin.GetDeviceData(_info));
        } catch (JSONException e) {
            NLogW("Error creating init request: " + e.getMessage());
        }
        _isInitInProgress = true;
        _restHelper.MakePostRequest(_info._restUrl + "/sdk/init", requestObject, null, this::ParseInitResponse);
    }

    private void ParseInitResponse(final String response, Object returnObject, final int responseCode) {
        _isInitInProgress = false;
        _timeSinceLastInitCall = System.currentTimeMillis();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            return;
        }

        try {
            JSONObject responseJson = new JSONObject(response);

            JSONObject eventConfig = responseJson.optJSONObject("event_batching");
            if (eventConfig != null) {
                Events.SetCustomConfig(eventConfig);
            }

            boolean isDebugEvent = false;
            JSONObject overrideConfig = responseJson.optJSONObject("overrides");
            if (overrideConfig != null) {
                isDebugEvent = overrideConfig.optBoolean("enable_debug_events");
            }
            if (isDebugEvent != _state._isDebugEvent) {
                _state._isDebugEvent = isDebugEvent;
                _preferencesEditor.putBoolean(NeftaPlugin._isDebugEventPref, _state._isDebugEvent);
            }

            JSONObject timeouts = responseJson.optJSONObject("timeouts");
            if (timeouts != null) {
                int connectInSeconds = timeouts.optInt("connect", 0);
                if (connectInSeconds > 0) {
                    RestHelper._connectTimeoutMs = connectInSeconds * 1000;
                }
                int readInSeconds = timeouts.optInt("read", 0);
                if (readInSeconds > 0) {
                    RestHelper._readTimeoutMs = readInSeconds * 1000;
                }
            }
            int bannerRefreshRateInSeconds = responseJson.optInt("banner_refresh_rate", 0);
            if (bannerRefreshRateInSeconds > 0) {
                Placement._placementContinuousDurationInMs = bannerRefreshRateInSeconds * 1000L;
            }

            int heartbeatFrequency = responseJson.optInt("heartbeat_frequency_sec", 0);
            if (heartbeatFrequency != _state._heartbeat_frequency) {
                _state._heartbeat_frequency = heartbeatFrequency;
                _preferencesEditor.putInt(NeftaPlugin._heartbeatFrequencyPref, _state._heartbeat_frequency);
            }

            final String newNuid = responseJson.optString("nuid");
            if (newNuid.length() > 0 && !newNuid.equals(_state._nuid)) {
                _state._nuid = newNuid;
                _preferencesEditor.putString(NeftaPlugin._nuidPref, _state._nuid);
                _preferencesEditor.apply();
                NLogI("Changing nuid to: " + newNuid);
            }

            if (_publisher != null && _publisher._isEnabled) {
                _publisher.ParseAdUnits(response, responseJson);
            } else {
                _cachedInitResponse = response;
            }
        } catch (JSONException e) {
            NLogI("Error parsing init response: " + e.getMessage());
        }
    }

    public void SetOverride(String override) {
        NLogI("SetOverrideUrl: "+ override);
        _info._restUrl = override;
        _info._restEventUrl = override + "/event/game";
    }
}