package com.nefta.sdk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import com.google.ads.mediation.nefta.BuildConfig;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class NeftaEvents {
    static final String _version = "1.0";
    static final String _savedEventBatchesPref = "nefta.savedEventBatches";
    static final String _skipBatchingTillPref = "nefta.skipBatchingTill";
    static final String _batchSizePref = "nefta.batchSize";
    static final String _sendDelayAfterTriggerPref = "nefta.sendDelayAfterTrigger";
    static final String _skipBatchingTillBeName = "enable_batching_after_event_count";
    static final String _batchSizeBeName = "event_batch_size_trigger";
    static final String _sendDelayAfterTriggerBeName = "event_batch_time_trigger";

    static final int _defaultSkipBatchingTill = 20;
    static final int _defaultBatchSize = 10;
    static final int _defaultSendDelayAfterTrigger = 5;

    private Map<ProgressionType, Map<ProgressionStatus, String>> _progressionNames;
    private final Executor _executor;
    private final Context _context;
    private final NeftaPlugin.Info _info;
    private final NeftaPlugin.State _state;
    private final RestHelper _restHelper;
    private final SharedPreferences.Editor _preferencesEditor;
    private final ArrayList<String> _savedBatchNames;
    private final StringBuilder _sb;
    private final ArrayList<String> _events = new ArrayList<>();
    private int _sendDelay;
    private int _timeSinceHeartbeat;

    private int _skipBatchingTill = -1;
    private int _batchSize = -1;
    private int _sendDelayAfterTrigger = -1;

    public NeftaEvents(Context context, NeftaPlugin.Info info, NeftaPlugin.State state, SharedPreferences preferences, SharedPreferences.Editor preferencesEditor, RestHelper restHelper) {
        _progressionNames = new HashMap<ProgressionType, Map<ProgressionStatus, String>>() {{
            put(ProgressionType.Achievement, new HashMap<ProgressionStatus, String>() {{
                put(ProgressionStatus.Start, "achievement_start");
                put(ProgressionStatus.Complete, "achievement_complete");
                put(ProgressionStatus.Fail, "achievement_fail");
            }});
            put(ProgressionType.GameplayUnit, new HashMap<ProgressionStatus, String>() {{
                put(ProgressionStatus.Start, "gameplay_unit_start");
                put(ProgressionStatus.Complete, "gameplay_unit_complete");
                put(ProgressionStatus.Fail, "gameplay_unit_fail");
            }});
            put(ProgressionType.ItemLevel, new HashMap<ProgressionStatus, String>() {{
                put(ProgressionStatus.Start, "item_level_start");
                put(ProgressionStatus.Complete, "item_level_complete");
                put(ProgressionStatus.Fail, "item_level_fail");
            }});
            put(ProgressionType.Unlock, new HashMap<ProgressionStatus, String>() {{
                put(ProgressionStatus.Start, "unlock_start");
                put(ProgressionStatus.Complete, "unlock_complete");
                put(ProgressionStatus.Fail, "unlock_fail");
            }});
            put(ProgressionType.PlayerLevel, new HashMap<ProgressionStatus, String>() {{
                put(ProgressionStatus.Start, "player_level_start");
                put(ProgressionStatus.Complete, "player_level_complete");
                put(ProgressionStatus.Fail, "player_level_fail");
            }});
            put(ProgressionType.Task, new HashMap<ProgressionStatus, String>() {{
                put(ProgressionStatus.Start, "task_start");
                put(ProgressionStatus.Complete, "task_complete");
                put(ProgressionStatus.Fail, "task_fail");
            }});
            put(ProgressionType.Other, new HashMap<ProgressionStatus, String>() {{
                put(ProgressionStatus.Start, "other_start");
                put(ProgressionStatus.Complete, "other_complete");
                put(ProgressionStatus.Fail, "other_fail");
            }});
        }};

        _executor = Executors.newSingleThreadExecutor();
        _context = context;
        _info = info;
        _state = state;
        _preferencesEditor = preferencesEditor;
        _restHelper = restHelper;
        _sb = new StringBuilder(512);
        _savedBatchNames = new ArrayList<>();

        _skipBatchingTill = preferences.getInt(_skipBatchingTillPref, _defaultSkipBatchingTill);
        _batchSize = preferences.getInt(_batchSizePref, _defaultBatchSize);
        _sendDelayAfterTrigger = preferences.getInt(_sendDelayAfterTriggerPref, _defaultSendDelayAfterTrigger);

        String savedBatches = preferences.getString(_savedEventBatchesPref, null);
        if (savedBatches != null && savedBatches.length() > 0) {
            String[] batches = savedBatches.split(",");
            _savedBatchNames.addAll(Arrays.asList(batches));
        }
    }

    void SetCustomConfig(JSONObject config) {
        boolean isChange = false;
        int skipBatchingTill = config.optInt(_skipBatchingTillBeName);
        if (skipBatchingTill != _skipBatchingTill) {
            isChange = true;
            _skipBatchingTill = skipBatchingTill;
        }
        int batchSize = config.optInt(_batchSizeBeName);
        if (batchSize != _batchSize) {
            isChange = true;
            _batchSize = batchSize;
        }
        int sendDelayAfterTrigger = config.optInt(_sendDelayAfterTriggerBeName);
        if (sendDelayAfterTrigger != _sendDelayAfterTrigger) {
            isChange = true;
            _sendDelayAfterTrigger = sendDelayAfterTrigger;
        }

        if (isChange) {
            _preferencesEditor.putInt(_skipBatchingTillPref, _skipBatchingTill);
            _preferencesEditor.putInt(_batchSizePref, _batchSize);
            _preferencesEditor.putInt(_sendDelayAfterTriggerPref, _sendDelayAfterTrigger);

            NeftaPlugin.NLogI("Event new config: skipBatchingTill: " + _skipBatchingTill + " batchSize: " + _batchSize + " delayAfterTrigger: " + _sendDelayAfterTrigger);
            TrySendEvents(false);
        }
    }

    private String CanSend() {
        if (_state._nuid == null || _state._nuid.length() == 0) {
            return "no behaviour id";
        }
        String networkError = _restHelper.IsNetworkAvailable();
        if (networkError != null) {
            return networkError;
        }
        return null;
    }

    public void CreateBaseEvent(final String type, final String eventCategory, final String eventSubCategory, final String itemName, final Long value, final String customPayload, boolean log) {
        synchronized (_events) {
            _sb.setLength(0);
            _sb.append("{\"event_type\":\"");
            _sb.append(type);
            if (eventCategory != null) {
                _sb.append("\",\"event_category\":\"");
                _sb.append(eventCategory);
            }
            if (eventSubCategory != null) {
                _sb.append("\",\"event_sub_category\":\"");
                _sb.append(eventSubCategory);
            }
            if (itemName != null) {
                _sb.append("\",\"item_name\":");
                _sb.append(JSONObject.quote(itemName));
            } else {
                _sb.append("\"");
            }
            if (customPayload != null) {
                _sb.append(",\"custom_publisher_payload\":");
                _sb.append(JSONObject.quote(customPayload));
            }
            if (value != null) {
                _sb.append(",\"value\":");
                _sb.append(value);
            }

            RecordInternal(log);
        }
        TrySendEvents(false);
    }

    public void Record(final String recordedEvent) {
        synchronized (_events) {
            _sb.setLength(0);
            _sb.append(recordedEvent);
            _sb.setLength(recordedEvent.length() - 1);
            RecordInternal(true);
        }
        TrySendEvents(false);
    }

    public enum ProgressionType {
        Achievement,
        GameplayUnit,
        ItemLevel,
        Unlock,
        PlayerLevel,
        Task,
        Other;
    }

    public enum ProgressionStatus {
        Start,
        Complete,
        Fail;
    }

    public enum ProgressionSource {
        Undefined(null),
        CoreContent("core_content"),
        OptionalContent("optional_content"),
        Boss("boss"),
        Social("social"),
        SpecialEvent("special_event"),
        Other("other");

        public final String _value;
        ProgressionSource(final String value) {
            _value = value;
        }
    }

    /**
     * Record a progression event
     *
     * @param status Defines the progression outcome.
     * @param type Defines the type of progression.
     * @param source Defines content type of progression.
     */
    public void AddProgressionEvent(@NonNull ProgressionStatus status, @NonNull ProgressionType type, @NonNull ProgressionSource source) {
        AddProgressionEvent(status, type, source, null, null);
    }

    /**
     * Record a progression event in full detail
     *
     * @param status Defines the progression outcome.
     * @param type Defines the type of progression.
     * @param source Defines content type of progression.
     * @param name Defines the specific named content of progression.
     * @param value Quantifiable progression step or number.
     */
    public void AddProgressionEvent(@NonNull ProgressionStatus status, @NonNull ProgressionType type, @NonNull ProgressionSource source, final String name, Long value) {
        AddProgressionEvent(status, type, source, name, value, null);
    }

    /**
     * Record a progression event in full detail with custom data
     *
     * @param status Defines the progression outcome.
     * @param type Defines the type of progression.
     * @param source Defines content type of progression.
     * @param name Defines the specific named content of progression.
     * @param value Quantifiable progression step or number.
     * @param customPayload Any other custom data.
     */
    public void AddProgressionEvent(@NonNull ProgressionStatus status, @NonNull ProgressionType type, @NonNull ProgressionSource source, final String name, Long value, String customPayload) {
        CreateBaseEvent("progression", _progressionNames.get(type).get(status), source._value, name, value, customPayload, true);
    }

    public enum ResourceCategory {
        SoftCurrency("soft_currency"),
        PremiumCurrency("premium_currency"),
        Resource("resource"),
        Consumable("consumable"),
        CosmeticItem("cosmetic_item"),
        CoreItem("core_item"),
        Chest("chest"),
        Experience("experience"),
        Other("other");
        public final String _value;
        ResourceCategory(final String value) {
            _value = value;
        }
    }

    public enum ReceiveMethod {
        Undefined(null),
        LevelEnd("level_end"),
        Reward("reward"),
        Loot("loot"),
        Shop("shop"),
        Iap("iap"),
        Create("create"),
        Other("other");
        public final String _value;
        ReceiveMethod(final String value) {
            _value = value;
        }
    }

    /**
     * Record an event when the player receives any valuable
     *
     * @param category Defines the category of valuable the player obtained.
     * @param method Defines the source of obtained valuable.
     */
    public void AddReceiveEvent(ResourceCategory category, ReceiveMethod method) {
        AddReceiveEvent(category, method, null, null);
    }

    /**
     * Record an event when the player receives any valuable in full detail
     *
     * @param category Defines the category of valuable the player obtained.
     * @param method Defines the source of obtained valuable.
     * @param name The name of received valuable.
     * @param quantity Received quantity.
     */
    public void AddReceiveEvent(ResourceCategory category, ReceiveMethod method, final String name, Long quantity) {
        AddReceiveEvent(category, method, name, quantity, null);
    }

    /**
     * Record an event when the player receives any valuable in full detail with custom data
     *
     * @param category Defines the category of valuable the player obtained.
     * @param method Defines the source of obtained valuable.
     * @param name The name of received valuable.
     * @param quantity Received quantity.
     * @param customPayload Any other custom data.
     */
    public void AddReceiveEvent(ResourceCategory category, ReceiveMethod method, final String name, Long quantity, final String customPayload) {
        CreateBaseEvent("receive", category._value, method._value, name, quantity, customPayload, true);
    }

    public enum SpendMethod {
        Undefined(null),
        Boost("boost"),
        Continuity("continuity"),
        Create("create"),
        Unlock("unlock"),
        Upgrade("upgrade"),
        Shop("shop"),
        Other("other");
        public final String _value;
        SpendMethod(final String value) {
            _value = value;
        }
    }

    /**
     * Record an event when the player spends any valuable
     *
     * @param category Defines the category of valuable the player spend.
     * @param method Defines the source of spending.
     */
    public void AddSpendEvent(ResourceCategory category, SpendMethod method) {
        AddSpendEvent(category, method, null, null);
    }

    /**
     * Record an event when the player spends any valuable in full detail
     *
     * @param category Defines the category of valuable the player spend.
     * @param method Defines the source of spending.
     * @param name The name of spent valuable.
     * @param quantity Spend quantity.
     */
    public void AddSpendEvent(ResourceCategory category, SpendMethod method, final String name, Long quantity) {
        AddSpendEvent(category, method, null, quantity, null);
    }

    /**
     * Record an event when the player spends any valuable in full detail with custom data
     *
     * @param category Defines the category of valuable the player spend.
     * @param method Defines the source of spending.
     * @param name The name of spent valuable.
     * @param quantity Spend quantity.
     * @param customPayload Any other custom data.
     */
    public void AddSpendEvent(ResourceCategory category, SpendMethod method, final String name, Long quantity, final String customPayload) {
        CreateBaseEvent("spend", category._value, method._value, name, quantity, customPayload, true);
    }

    public enum SessionCategory {
        SessionStart("session_start"),
        AccountConnected("account_connected"),
        AccountUpgraded("account_upgraded"),
        Heartbeat("heartbeat");
        public final String _value;
        SessionCategory(final String value) {
            _value = value;
        }
    }

    /**
     * Record an event when the player session changes
     *
     * @param category Defines category of session change.
     */
    public void AddSessionEvent(SessionCategory category) {
        AddSessionEvent(category, null, null, null, true);
    }

    /**
     * Send an event when the player session changes in full detail with custom data
     *
     * @param category Defines category of session change.
     * @param name Any name associated with the session change.
     * @param value Any value associated with the session change.
     * @param customPayload Any other custom data.
     * @param log Log the event to adb.
     */
    public void AddSessionEvent(SessionCategory category, final String name, final Long value, final String customPayload, boolean log) {
        CreateBaseEvent("session", category._value, null, name, value, customPayload, log);
    }

    private void RecordInternal(boolean log) {
        _sb.append(",\"sequence_number\":");
        _sb.append(_state._sequenceNumber);
        _sb.append(",\"session_id\":");
        _sb.append(_state._sessionId);
        _sb.append(",\"event_time\":");
        _sb.append(System.currentTimeMillis());
        _sb.append(",\"test\":");
        _sb.append(BuildConfig.DEBUG ? "true" : "false");
        _sb.append("}");

        _state._sequenceNumber++;
        _preferencesEditor.putInt(NeftaPlugin._sequenceNumberPref, _state._sequenceNumber);
        _preferencesEditor.apply();

        String eventS = _sb.toString();

        _events.add(eventS);
        if (log) {
            NeftaPlugin.NLogI("Recorded event: " + eventS);
        }
    }

    void OnUpdate() {
        if (_sendDelay > 0) {
            _sendDelay--;
            if (_sendDelay <= 0) {
                TrySendEvents(true);
            }
        }

        if (_state._heartbeat_frequency != 0) {
            _timeSinceHeartbeat++;
            if (_timeSinceHeartbeat >= _state._heartbeat_frequency) {
                _timeSinceHeartbeat = 0;

                AddSessionEvent(SessionCategory.Heartbeat, null, (long)_state._heartbeat_frequency, null, false);
            }
        }
    }

    void OnResume() {
        if (_state._sessionDuration == 0) {
            _timeSinceHeartbeat = 0;
        }

        if (_savedBatchNames.size() > 0) {
            TrySendEvents(true);
        }
    }

    void OnPause() {
        if (_events.isEmpty()) {
            return;
        }

        final String batchName = String.valueOf(_state._sequenceNumber);
        _savedBatchNames.add(batchName);
        StringBuilder batchNameBuilder = new StringBuilder();
        for (int i = 0; i < _savedBatchNames.size(); i++) {
            if (i > 0) {
                batchNameBuilder.append(',');
            }
            batchNameBuilder.append(_savedBatchNames.get(i));
        }
        String names = batchNameBuilder.toString();
        _preferencesEditor.putString(_savedEventBatchesPref, names);
        _preferencesEditor.apply();
        NeftaPlugin.NLogI( "Updating saved event batches: "+ names);

        _executor.execute(new Runnable() {
            public void run() {
                try {
                    String eventsPath = GetEventsFilePath();
                    File eventsDirectory = new File(eventsPath);
                    if (!eventsDirectory.exists()) {
                        eventsDirectory.mkdir();
                    }
                    FileOutputStream outputStream = new FileOutputStream(eventsPath + "/" + batchName);

                    String batch = null;
                    synchronized (_events) {
                        batch = GetCurrentBatch();
                    }

                    byte[] batchByes = batch.getBytes("UTF-8");
                    outputStream.write(batchByes);
                    outputStream.close();
                } catch (IOException e) {
                    NeftaPlugin.NLogW("Error saving event: " + e.getMessage());
                }
            }
        });
    }

    private String GetEventsFilePath() {
        return _context.getApplicationInfo().dataDir +"/Events";
    }

    private void TrySendEvents(boolean force) {
        if (!force) {
            if (_skipBatchingTill > 0 && _skipBatchingTill < _state._sequenceNumber && _events.size() < _batchSize) {
                _sendDelay = _sendDelayAfterTrigger;
                return;
            }
        }

        String networkError = CanSend();
        if (networkError != null) {
            NeftaPlugin.NLogI( "Event sending canceled: " + networkError);
            return;
        }

        _sendDelay = 0;

        if (_savedBatchNames.size() > 0) {
            final String[] names = new String[_savedBatchNames.size()];
            for (int i = 0; i < _savedBatchNames.size(); i++) {
                names[i] = _savedBatchNames.get(i);
            }
            _executor.execute(new Runnable() {
                public void run() {
                    NeftaPlugin.NLogI( "Attempting to send "+ names.length);
                    for (int i = 0; i < names.length; i++) {
                        File file = new File(GetEventsFilePath() + "/" + names[i]);
                        if (!file.exists()) {
                            continue;
                        }
                        try {
                            FileInputStream inputStream = new FileInputStream(file);
                            byte[] batch = new byte[(int) file.length()];
                            inputStream.read(batch);

                            SendBatch(batch);
                            inputStream.close();
                            file.delete();
                        } catch (IOException e) {
                            NeftaPlugin.NLogW( "Error sending events: "+  e.getMessage());
                        }
                    }
                }
            });
            _savedBatchNames.clear();
            _preferencesEditor.putString(_savedEventBatchesPref, "");
            _preferencesEditor.apply();
        } else {
            String batch = null;
            synchronized (_events) {
                if (_events.size() == 0) {
                    return;
                }
                batch = GetCurrentBatch();
            }
            final String finalBatch = batch;
            _executor.execute(new Runnable() {
                public void run() {
                    try {
                        byte[] batchRaw = finalBatch.getBytes("UTF-8");
                        SendBatch(batchRaw);
                    } catch (UnsupportedEncodingException e) {
                        NeftaPlugin.NLogW("Error encoding events");
                    }
                }
            });
        }
    }

    String GetCurrentBatch() {
        _sb.setLength(0);
        _sb.append("{\"sdk_version\":\"");
        _sb.append(NeftaPlugin.Version);
        _sb.append("\",\"device_language\":");
        _sb.append(JSONObject.quote(_info._language));
        _sb.append(",\"device_country\":");
        _sb.append(JSONObject.quote(_info._country));
        _sb.append(",\"platform\":\"android\",\"device_type\":\"mobile\",\"device_make\":");
        _sb.append(JSONObject.quote(Build.MANUFACTURER));
        _sb.append(",\"device_model\":");
        _sb.append(JSONObject.quote(Build.MODEL));
        _sb.append(",\"hardware_version\":");
        _sb.append(JSONObject.quote(Build.HARDWARE));
        _sb.append(",\"device_os\":\"Android\",\"device_os_version\":\"");
        _sb.append(Build.VERSION.RELEASE);
        _sb.append("\",\"screen_size_width\":");
        _sb.append(_info._width);
        _sb.append(",\"screen_size_height\":");
        _sb.append(_info._height);
        _sb.append(",\"device_ppi\":");
        _sb.append(_info._dpi);
        _sb.append(",\"app_id\":\"");
        _sb.append(_info._appId);
        _sb.append("\",\"app_version\":\"");
        _sb.append(_info._bundleVersion);
        _sb.append("\",\"event_version\":\"");
        _sb.append(_version);
        _sb.append("\",\"device_pixel_ratio\":");
        _sb.append(_info._scale);
        _sb.append(",\"device_utc_offset\":");
        _sb.append(_info._utfOffset);
        if (_state._isDebugEvent) {
            _sb.append(",\"overrides\":{\"enable_debug_events\":true}");
        }
        _sb.append(",\"events\":[");
        for (int i = 0; i < _events.size(); i++) {
            if (i > 0) {
                _sb.append((char)44);
            }
            _sb.append(_events.get(i));
        }
        _events.clear();

        return _sb.toString();
    }

    private void SendBatch(byte[] batch) throws UnsupportedEncodingException {
        String postfix = "],\"event_sent_time\":"+ System.currentTimeMillis() +",\"nuid\":\""+ _state._nuid + "\"}";
        byte[] postfixBytes = postfix.getBytes("UTF-8");

        int batchLengthWithoutClosingBracket = batch.length;
        byte[] body = Arrays.copyOf(batch, batchLengthWithoutClosingBracket + postfixBytes.length);
        System.arraycopy(postfixBytes, 0, body, batchLengthWithoutClosingBracket, postfixBytes.length);

        _restHelper.MakePostRequest(_info._restEventUrl, body, this::OnEventsSend);
    }

    private void OnEventsSend(String responseReader, Object responseObject, int responseCode) {
        TrySendEvents(true);
    }
}
