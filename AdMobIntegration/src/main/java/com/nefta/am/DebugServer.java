package com.nefta.am;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.ads.MobileAds;
import com.nefta.sdk.Insights;
import com.nefta.sdk.NeftaEvents;
import com.nefta.sdk.NeftaPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DebugServer {
    private final String TAG = "DS";
    private final int _broadcastPort = 12010;

    private class LogEntry {
        public final long _time;
        public final String _line;

        public LogEntry(String line) {
            _time = System.currentTimeMillis();
            _line = line;
        }
    }

    private Activity _activity;
    private String _name;
    private String _bundleId;
    private String _version;
    private final Handler _mainHandler;
    private final HandlerThread _backgroundThread;
    private final Handler _backgroundHandler;
    private Thread _broadcastThread;
    private InetAddress _broadcastAddress;
    private int _localPort;
    private DatagramSocket _broadcastSocket;
    private Runnable _broadcastRunnable;
    private SimpleDateFormat _dateTimeFormat;
    private final List<LogEntry> _logLines;
    private ExecutorService _executor;
    private boolean _isSimulator;
    private static DebugServer _instance;

    public static void Init(Activity activity, Intent intent) {
        if (_instance == null) {
            _instance = new DebugServer(activity, intent);
        }

        TextView title = activity.findViewById(R.id.title);
        title.setText("AM Integration "+ MobileAds.getVersion());
        title.setOnClickListener(v -> _instance.ToggleUI(activity, !_instance._isSimulator));
        _instance.ToggleUI(activity, BuildConfig.IS_SIMULATOR);
    }

    private void ToggleUI(Activity activity, boolean isSimulator) {
        _isSimulator = isSimulator;

        activity.findViewById(R.id.interstitialSim).setVisibility(_isSimulator ? View.VISIBLE : View.GONE);
        activity.findViewById(R.id.rewardedSim).setVisibility(_isSimulator ? View.VISIBLE : View.GONE);

        activity.findViewById(R.id.interstitial).setVisibility(_isSimulator ? View.GONE : View.VISIBLE);
        activity.findViewById(R.id.rewarded).setVisibility(_isSimulator ? View.GONE : View.VISIBLE);
    }

    private DebugServer(Activity activity, Intent intent) {
        _instance = this;

        _activity = activity;
        _mainHandler = new Handler(Looper.getMainLooper());

        _backgroundThread = new HandlerThread("NetworkThread");
        _backgroundThread.start();
        _backgroundHandler = new Handler(_backgroundThread.getLooper());

        _name = Build.MANUFACTURER + " " + Build.MODEL;
        _bundleId = activity.getPackageName();

        try {
            PackageManager pm = _activity.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(_activity.getPackageName(), 0);

            _version = packageInfo.versionName + "." + packageInfo.versionCode;
        } catch (Exception ignored) {

        }

        if (intent != null && intent.getExtras() != null) {
            String override = intent.getStringExtra("override");
            if (override != null && override.length() > 2) {
                NeftaPlugin.SetOverride(override);
            }
        }

        _logLines = new ArrayList<>();

        _dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        _dateTimeFormat.setTimeZone(TimeZone.getDefault());

        NeftaPlugin._instance = null;
        NeftaPlugin.SetDebugTime(0);
        NeftaPlugin.OnLog = (String log) -> {
            _logLines.add(new LogEntry(log));
        };

        startBroadcastServer();
    }

    public void Destroy() {
        StopBroadcastServer();
        _activity = null;
        _instance = null;
    }

    private void startBroadcastServer() {
        _broadcastRunnable = new Runnable() {
            @Override
            public void run() {
                SendState(_broadcastAddress, _broadcastPort, "master");
                _backgroundHandler.postDelayed(this, 5000);
            }
        };

        try {
            _broadcastSocket = new DatagramSocket();
            _broadcastSocket.setBroadcast(true);
            String ip = GetBroadcastIp();
            _broadcastAddress = InetAddress.getByName(ip);
            _localPort = _broadcastSocket.getLocalPort();
            Log.i(TAG, "Broadcast server started: " + ip +":"+ _localPort);
            _backgroundHandler.post(_broadcastRunnable);
        } catch (Exception e) {
            Log.i(TAG, "Exc: " + e.getMessage());
        }

        _broadcastThread = new Thread(() -> {
            byte[] buffer = new byte[10240];

            while (_broadcastThread != null) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    _broadcastSocket.receive(packet);
                } catch (SocketException e) {
                    Log.i(TAG, "Socket Exc: "+ e.getMessage());
                    return;
                } catch (Exception e) {
                    Log.i(TAG, "Exc: "+ e.getMessage());
                }
                String message = new String(packet.getData(), 0, packet.getLength());

                InetAddress address = packet.getAddress();
                int port = packet.getPort();

                Log.i(TAG, "Received broadcast: "+ message);

                String[] segments = message.split("\\|");
                String sourceName = segments[0];
                if (_name.equals(sourceName)) {
                    continue;
                }
                String control = segments[3];
                String pId;
                String aId;
                int aIdH;
                switch (control) {
                    case "get_logs":
                        long lastLogTime = Long.parseLong(segments[4]);
                        for (int i = 0; i < _logLines.size(); i++) {
                            LogEntry entry = _logLines.get(i);
                            if (lastLogTime > entry._time) {
                                continue;
                            }
                            SendUdp(address, port, sourceName, "log|"+ entry._time +"|"+ entry._line);
                        }
                        break;
                    case "set_time_offset":
                        String offsetString = segments[4];
                        NeftaPlugin.SetDebugTime(Long.parseLong(offsetString));
                        SendUdp(address, port, sourceName, "return|set_time_offset");
                        break;
                    case "state":
                        SendState(address, port, sourceName);
                        break;
                    case "add_event": {
                        String name = null;
                        long value = 0;
                        String customPayload = null;

                        if ("progression".equals(segments[4])) {
                            NeftaEvents.ProgressionStatus status = ToProgressionStatus(segments[5]);
                            NeftaEvents.ProgressionType type = ToProgressionType(segments[6]);
                            NeftaEvents.ProgressionSource source = ToProgressionSource(segments[7]);
                            if (segments.length > 8) {
                                name = segments[8];
                            }
                            if (segments.length > 9) {
                                value = Long.parseLong(segments[9]);
                            }
                            if (segments.length > 10) {
                                customPayload = segments[10];
                            }
                            NeftaPlugin.Events.AddProgressionEvent(status, type, source, name, value, customPayload);
                        } else if ("receive".equals(segments[4])) {
                            NeftaEvents.ResourceCategory category = ToResourceCategory(segments[5]);
                            NeftaEvents.ReceiveMethod method = ToReceiveMethod(segments[6]);
                            if (segments.length > 7) {
                                name = segments[7];
                            }
                            if (segments.length > 8) {
                                value = Long.parseLong(segments[8]);
                            }
                            if (segments.length > 9) {
                                customPayload = segments[9];
                            }
                            NeftaPlugin.Events.AddReceiveEvent(category, method, name, value, customPayload);
                        } else if ("spend".equals(segments[4])) {
                            NeftaEvents.ResourceCategory category = ToResourceCategory(segments[5]);
                            NeftaEvents.SpendMethod method = ToSpendMethod(segments[6]);
                            if (segments.length > 7) {
                                name = segments[7];
                            }
                            if (segments.length > 8) {
                                value = Long.parseLong(segments[8]);
                            }
                            if (segments.length > 9) {
                                customPayload = segments[9];
                            }
                            NeftaPlugin.Events.AddSpendEvent(category, method, name, value, customPayload);
                        } else if ("revenue".equals(segments[4])) {
                            name = segments[5];
                            value = (long) (Double.parseDouble(segments[6]) * 1000000);
                            String currency = segments[7];
                            if (segments.length > 8) {
                                customPayload = segments[8];
                            }
                            NeftaPlugin.Events.AddPurchaseEvent(name, value, currency, customPayload);
                        }
                        SendUdp(address, port, sourceName, "return|add_event");
                        break;
                    }
                    case "add_unity_event": {
                        int type = Integer.parseInt(segments[4]);
                        int category = Integer.parseInt(segments[5]);
                        int subCategory = Integer.parseInt(segments[6]);
                        String name = segments[7];
                        long value = Long.parseLong(segments[8]);
                        String customPayload = null;
                        if (segments.length > 9) {
                            customPayload = segments[9];
                        }
                        int repeat = 1;
                        if (segments.length > 10) {
                            repeat = Integer.parseInt(segments[10]);
                        }

                        if (_executor == null) {
                            _executor = Executors.newFixedThreadPool(10);
                        }

                        for (int i = 0; i < repeat; i++) {
                            final String cp = customPayload;
                            _executor.submit(() -> {
                                NeftaPlugin._instance.Record(type, category, subCategory, name, value, cp);
                            });
                        }

                        SendUdp(address, port, sourceName, "return|add_unity_event");
                        break;
                    }
                    case "add_external_mediation_request": {
                        String provider = segments[4];
                        String id = segments[5];
                        String id2 = segments[6];
                        double revenue = Double.parseDouble(segments[7]);
                        String precision = segments[8];
                        int status = Integer.parseInt(segments[9]);
                        String providerStatus = null;
                        if (segments.length > 10) {
                            providerStatus = segments[10];
                        }
                        String networkStatus = null;
                        if (segments.length > 11) {
                            networkStatus = segments[11];
                        }
                        NeftaPlugin.OnExternalMediationResponse(provider, id, id2, revenue, precision, status, providerStatus, networkStatus, null);
                        SendUdp(address, port, sourceName, "return|add_external_mediation_request");
                        break;
                    }
                    case "add_external_mediation_impression": {
                        boolean isClick = Boolean.parseBoolean(segments[4]);
                        String provider = segments[5];
                        String id = segments[6];
                        String id2 = segments[7];
                        double revenue = Double.parseDouble(segments[8]);
                        String precision = segments[9];
                        NeftaPlugin.OnExternalMediationImpression(isClick, provider, new JSONObject(), id, id2);
                        SendUdp(address, port, sourceName, "return|add_external_mediation_impression");
                        break;
                    }
                    case "get_insights":
                        int insightFlags = Integer.parseInt(segments[4]);
                        int callbackIndex = Integer.parseInt(segments[5]);

                        NeftaPlugin._instance.GetInsights(insightFlags, null, (insights) -> {
                            ForwardInsights(callbackIndex, insights);
                        });

                        SendUdp(address, port, sourceName, "return|get_insights");
                        break;
                    case "set_override":
                        String app_id = segments[4];
                        String rest_url = segments[5];
                        if (rest_url.isEmpty() || rest_url.equals("null")) {
                            rest_url = null;
                        }

                        NeftaPlugin._instance._info._appId = app_id;
                        //NeftaPlugin.SetOverride(rest_url);
                        if (segments.length > 6 && segments[6].length() > 0) {
                            NeftaPlugin._instance._state._nuid = segments[6];
                        }

                        SendUdp(address, port, sourceName, "return|set_override");
                        break;
                    case "create_file":
                        String path = segments[4];
                        String content = segments[5];

                        File f = new File(_activity.getApplicationInfo().dataDir, path);
                        try {
                            File parentDir = f.getParentFile();
                            if (parentDir != null && !parentDir.exists()) {
                                if (parentDir.mkdirs()) {
                                    Log.i(TAG, "Parent directories created successfully");
                                }
                            }

                            FileOutputStream outputStream = new FileOutputStream(f);
                            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
                            outputStream.close();

                            Log.i(TAG, "Wrote '"+ content + "' to "+ f.getAbsolutePath());

                            SendUdp(address, port, sourceName, "return|create_file");
                        } catch (Exception exception) {
                            Log.i(TAG, "Error writing to '"+ f.getAbsolutePath() + ": "+ exception.getMessage());
                        }
                        break;
                    case "crash":
                        throw new RuntimeException("Simulated crash");
                    default:
                        Log.i(TAG, "Unrecognized command: " + control + " m:" + message);
                        break;
                }
            }

        });
        _broadcastThread.start();
    }

    private void StopBroadcastServer() {
        if (_broadcastRunnable != null) {
            _backgroundHandler.removeCallbacks(_broadcastRunnable);
            _broadcastRunnable = null;
        }

        if (_broadcastThread != null) {
            _broadcastThread.interrupt();
            _broadcastThread = null;
        }

        _broadcastSocket.close();
        _broadcastSocket = null;
    }

    private void SendUdp(InetAddress address, int port, String to, String message) {
        try {
            byte[] buffer = (_name +"|"+ to +"|"+ message).getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
            _broadcastSocket.send(packet);
        } catch (Exception e) {
            Log.i(TAG, "Exc: "+ e.getMessage());
        }
    }

    private void SendState(InetAddress address, int port, String to) {
        String stateString = null;
        try {
            JSONObject json = new JSONObject();
            JSONArray adUnits = new JSONArray();
            //json.put("rest_url", NeftaPlugin._efUrl);
            json.put("ad_units", adUnits);

            if (NeftaPlugin._instance != null) {
                json.put("app_id", NeftaPlugin._instance._info._appId);
                json.put("nuid", NeftaPlugin._instance._state._nuid);
            }

            stateString = json.toString();
        } catch (Exception ignored) {

        }

        String message = "state|android|"+ _localPort +"|"+ _bundleId  +"|"+ _version +"|"+ _logLines.size() +"|"+ stateString;
        SendUdp(address, port, to, message);
    }

    private void ForwardInsights(int index, Insights insights) {
        String message = "return|insights|"+ index + "|{";
        boolean hasFields = false;
        if (insights._churn != null) {
            message += "\"churn\":{\"d1_probability\":"+ insights._churn._d1_probability + "}";
            hasFields = true;
        }
        if (insights._banner != null) {
            if (hasFields) {
                message += ",";
            }
            message += "\"interstitial\":{\"ad_unit\":\""+ insights._banner._adUnit +"\",\"floor_price\":"+ insights._banner._floorPrice +"}";
            hasFields = true;
        }
        if (insights._interstitial!= null) {
            if (hasFields) {
                message += ",";
            }
            message += "\"interstitial\":{\"ad_unit\":\""+ insights._interstitial._adUnit +"\",\"floor_price\":"+ insights._interstitial._floorPrice +"}";
            hasFields = true;
        }
        if (insights._rewarded != null) {
            if (hasFields) {
                message += ",";
            }
            message += "\"interstitial\":{\"ad_unit\":\""+ insights._rewarded._adUnit +"\",\"floor_price\":"+ insights._rewarded._floorPrice +"}";
        }
        message += "}";
        SendUdp(_broadcastAddress, _broadcastPort, "master", message);
    }

    private NeftaEvents.ProgressionStatus ToProgressionStatus(String name) {
        switch (name) {
            case "start":
                return NeftaEvents.ProgressionStatus.Start;
            case "complete":
                return NeftaEvents.ProgressionStatus.Complete;
            default:
                return NeftaEvents.ProgressionStatus.Fail;
        }
    }

    private NeftaEvents.ProgressionType ToProgressionType(String name) {
        switch (name) {
            case "achievement":
                return NeftaEvents.ProgressionType.Achievement;
            case "gameplay_unit":
                return NeftaEvents.ProgressionType.GameplayUnit;
            case "item_level":
                return NeftaEvents.ProgressionType.ItemLevel;
            case "unlock":
                return NeftaEvents.ProgressionType.Unlock;
            case "player_level":
                return NeftaEvents.ProgressionType.PlayerLevel;
            case "task":
                return NeftaEvents.ProgressionType.Task;
            default:
                return NeftaEvents.ProgressionType.Other;
        }
    }

    private NeftaEvents.ProgressionSource ToProgressionSource(String name) {
        for (NeftaEvents.ProgressionSource c : NeftaEvents.ProgressionSource.values()) {
            if (name.equals(c._name)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Non existing ProgressionSource: " + name);
    }

    private NeftaEvents.ResourceCategory ToResourceCategory(String name) {
        for (NeftaEvents.ResourceCategory c : NeftaEvents.ResourceCategory.values()) {
            if (c._name.equals(name)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Non existing ResourceCategory: " + name);
    }

    private NeftaEvents.ReceiveMethod ToReceiveMethod(String name) {
        for (NeftaEvents.ReceiveMethod m : NeftaEvents.ReceiveMethod.values()) {
            if (name.equals(m._name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Non existing ReceiveMethod: " + name);
    }

    private NeftaEvents.SpendMethod ToSpendMethod(String name) {
        for (NeftaEvents.SpendMethod m : NeftaEvents.SpendMethod.values()) {
            if (name.equals(m._name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Non existing SpendMethod: " + name);
    }

    private String GetBroadcastIp() {
        String ip = null;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                for (InetAddress inetAddress : Collections.list(addresses)) {
                    if (!inetAddress.isLoopbackAddress() && inetAddress.isSiteLocalAddress()) {
                        ip = inetAddress.getHostAddress();

                        ip = ip.substring(0, ip.lastIndexOf(".") + 1) + "255";
                    }
                }
            }
        } catch (Exception e) {
            Log.i(TAG, "Error getting ip address: " + e.getMessage());
        }
        return ip;
    }
}